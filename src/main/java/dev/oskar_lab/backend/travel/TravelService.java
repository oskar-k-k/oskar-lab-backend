package dev.oskar_lab.backend.travel;

import dev.oskar_lab.backend.access.*;
import dev.oskar_lab.backend.auth.AuthFailure;
import dev.oskar_lab.backend.storage.FileStorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;

/** Owns travel invariants, resource authorization and atomic edits/deletion of private bookings. */
@Service
@Transactional
public class TravelService {
    public static final String RESOURCE_TYPE = "travel.trip";
    private final ResourceAccessService access;
    private final TravelTripRepository trips;
    private final TravelEntryRepository entries;
    private final TravelAttachmentRepository attachments;
    private final TravelAttachmentContentRepository contents;
    private final FileStorageService storage;

    public TravelService(ResourceAccessService access, TravelTripRepository trips, TravelEntryRepository entries,
                         TravelAttachmentRepository attachments, TravelAttachmentContentRepository contents, FileStorageService storage) {
        this.access = access;
        this.trips = trips;
        this.entries = entries;
        this.attachments = attachments;
        this.contents = contents;
        this.storage = storage;
    }

    /** Lists only visible trips, twenty at a time; unverified email addresses cannot claim shares. */
    @Transactional(readOnly = true)
    public TripPage list(UUID user, int page) {
        var resources = access.visible(user, RESOURCE_TYPE, page);
        return new TripPage(resources.stream().map(r -> tripDto(trips.findById(r.id).orElseThrow(() -> new AuthFailure(404, "notFound")), r, user)).toList(), resources.size() == 20);
    }

    /** Creates a private trip and its platform ownership record in one transaction. */
    public Trip create(UUID user, SaveTrip input) {
        validateTrip(input);
        if (input.version() != 0) throw new AuthFailure(400, "invalidTravel");
        AccessResource resource = access.create(user, RESOURCE_TYPE);
        TravelTrip trip = new TravelTrip();
        trip.id = resource.id;
        applyTrip(trip, input);
        return tripDto(trips.saveAndFlush(trip), resource, user);
    }

    /** Reads a trip after checking its resource boundary. */
    @Transactional(readOnly = true)
    public Trip get(UUID user, UUID tripId) {
        AccessResource resource = access.requireRead(user, RESOURCE_TYPE, tripId);
        return tripDto(trip(tripId), resource, user);
    }

    /** Rejects stale owner edits with 409 instead of overwriting newer content. */
    public Trip update(UUID user, UUID tripId, SaveTrip input) {
        AccessResource resource = access.requireOwner(user, RESOURCE_TYPE, tripId);
        TravelTrip trip = trip(tripId);
        checkVersion(trip.version, input.version());
        validateTrip(input);
        applyTrip(trip, input);
        return tripDto(trips.saveAndFlush(trip), resource, user);
    }

    /** Deletes trip metadata and sharing atomically; cloud bytes are removed through a durable cleanup queue. */
    public void delete(UUID user, UUID tripId, long version) {
        access.requireOwner(user, RESOURCE_TYPE, tripId);
        TravelTrip trip = trip(tripId);
        checkVersion(trip.version, version);
        for (TravelEntry entry : entries.findByTripId(tripId)) deleteEntryRows(entry);
        entries.flush();
        trips.delete(trip);
        trips.flush();
        access.delete(user, RESOURCE_TYPE, tripId);
    }

    /** Returns grouped bookings; clients may flatten points to interleave activities and hotel endpoints. */
    @Transactional(readOnly = true)
    public EntryPage entries(UUID user, UUID tripId, int page) {
        access.requireRead(user, RESOURCE_TYPE, tripId);
        if (page < 0 || page > 10000) throw new AuthFailure(400, "invalidPage");
        var result = entries.findByTripIdOrderBySortDateAscSortTimeAscIdAsc(tripId, PageRequest.of(page, 20));
        return new EntryPage(result.stream().map(this::entryDto).toList(), result.size() == 20);
    }

    /** Creates a typed booking with one or two validated points. */
    public Entry createEntry(UUID user, UUID tripId, SaveEntry input) {
        access.requireOwner(user, RESOURCE_TYPE, tripId);
        if (input.version() != 0) throw new AuthFailure(400, "invalidTravel");
        if (entries.countByTripId(tripId) >= 1000) throw new AuthFailure(409, "entryLimit");
        TravelEntry entry = new TravelEntry();
        entry.tripId = tripId;
        applyEntry(entry, input);
        return entryDto(entries.saveAndFlush(entry));
    }

    /** Reads one booking inside the specified trip; IDs from other trips are never accepted. */
    @Transactional(readOnly = true)
    public Entry getEntry(UUID user, UUID tripId, UUID entryId) {
        return entryDto(requireEntry(user, tripId, entryId, false));
    }

    /** Updates booking points together; attachments cannot silently lose their point association. */
    public Entry updateEntry(UUID user, UUID tripId, UUID entryId, SaveEntry input) {
        TravelEntry entry = requireEntry(user, tripId, entryId, true);
        checkVersion(entry.version, input.version());
        var roles = input.points().stream().map(Point::role).toList();
        if (attachments.findByEntryIdOrderByIdAsc(entryId).stream().anyMatch(a -> a.pointRole != null && !roles.contains(a.pointRole)))
            throw new AuthFailure(409, "attachmentPointInUse");
        applyEntry(entry, input);
        return entryDto(entries.saveAndFlush(entry));
    }

    /** Deletes a booking and its documents after checking ownership and version. */
    public void deleteEntry(UUID user, UUID tripId, UUID entryId, long version) {
        TravelEntry entry = requireEntry(user, tripId, entryId, true);
        checkVersion(entry.version, version);
        deleteEntryRows(entry);
    }

    /** Internal attachment boundary: authorize before resolving entry or document IDs. */
    public TravelEntry requireEntry(UUID user, UUID tripId, UUID entryId, boolean write) {
        if (write) access.requireOwner(user, RESOURCE_TYPE, tripId);
        else access.requireRead(user, RESOURCE_TYPE, tripId);
        return entries.findByIdAndTripId(entryId, tripId).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }

    private void applyEntry(TravelEntry entry, SaveEntry input) {
        List<Point> normalized;
        try { normalized = TravelRules.normalize(input.type(), input.points()); }
        catch (DateTimeException | IllegalArgumentException error) { throw new AuthFailure(400, "invalidTravel"); }
        entry.type = input.type();
        entry.title = input.title().strip();
        entry.notes = text(input.notes());
        entry.reference = text(input.reference());
        entry.sortDate = normalized.getFirst().date();
        entry.sortTime = normalized.getFirst().time() == null ? LocalTime.of(23, 59, 59) : normalized.getFirst().time();
        entry.updatedAt = Instant.now();
        var roles = normalized.stream().map(Point::role).toList();
        entry.points.removeIf(point -> !roles.contains(point.role));
        for (int index = 0; index < normalized.size(); index++) {
            Point point = normalized.get(index);
            TravelPoint row = entry.points.stream().filter(p -> p.role == point.role()).findFirst().orElse(null);
            if (row == null) { row = new TravelPoint(); row.entry = entry; entry.points.add(row); }
            row.position = index;
            row.role = point.role(); row.date = point.date(); row.time = point.time(); row.timeZone = point.timeZone();
            row.utcOffset = point.utcOffset(); row.timeMeaning = point.timeMeaning(); row.placeName = point.placeName(); row.address = point.address();
        }
    }

    private Entry entryDto(TravelEntry entry) {
        var points = entry.points.stream().sorted(Comparator.comparingInt(p -> p.position)).map(p -> new Point(p.role, p.date, p.time,
            p.timeZone, p.utcOffset, p.timeMeaning, p.placeName, p.address)).toList();
        return new Entry(entry.id, entry.tripId, entry.type, entry.title, entry.notes, entry.reference,
            points, TravelRules.durationMinutes(points), entry.version);
    }

    private void deleteEntryRows(TravelEntry entry) {
        for (TravelAttachment attachment : attachments.findByEntryIdOrderByIdAsc(entry.id)) {
            if (attachment.orbitFileId != null) storage.scheduleDelete(attachment.storageProject, attachment.orbitFileId);
            contents.deleteById(attachment.id);
            contents.flush();
            attachments.delete(attachment);
        }
        attachments.flush();
        entries.delete(entry);
    }

    private TravelTrip trip(UUID id) { return trips.findById(id).orElseThrow(() -> new AuthFailure(404, "notFound")); }
    private Trip tripDto(TravelTrip trip, AccessResource resource, UUID user) {
        return new Trip(trip.id, resource.ownerId, trip.title, trip.notes, trip.startDate, trip.endDate, trip.version, resource.ownerId.equals(user));
    }
    private void applyTrip(TravelTrip trip, SaveTrip input) {
        trip.title = input.title().strip(); trip.notes = text(input.notes()); trip.startDate = input.startDate(); trip.endDate = input.endDate();
    }
    private void validateTrip(SaveTrip input) {
        try { TravelRules.validateDate(input.startDate()); TravelRules.validateDate(input.endDate()); }
        catch (IllegalArgumentException error) { throw new AuthFailure(400, "invalidTravel"); }
        if (input.endDate().isBefore(input.startDate())) throw new AuthFailure(400, "invalidTravel");
    }
    private void checkVersion(long current, long expected) { if (current != expected) throw new AuthFailure(409, "conflict"); }
    private String text(String value) { return value == null ? "" : value.strip(); }
}
