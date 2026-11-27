package dev.oskar_lab.backend.travel;

import dev.oskar_lab.backend.access.ResourceAccessService;
import dev.oskar_lab.backend.auth.AuthFailure;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.UUID;
import static dev.oskar_lab.backend.travel.SavedPlaceDtos.*;

/** Owns authorized saved-link writes; links remain plain data until the user opens them. */
@Service @Transactional
public class SavedPlaceService {
    private final ResourceAccessService access;
    private final TravelSavedPlaceRepository places;
    public SavedPlaceService(ResourceAccessService access, TravelSavedPlaceRepository places) {
        this.access = access; this.places = places;
    }
    /** Reads twenty inspirations at a time under existing trip read permissions. */
    @Transactional(readOnly = true)
    public SavedPlacePage list(UUID user, UUID tripId, int page) {
        access.requireRead(user, TravelService.RESOURCE_TYPE, tripId);
        if (page < 0 || page > 10000) throw new AuthFailure(400, "invalidPage");
        var result = places.findByTripIdOrderByCreatedAtDescIdDesc(tripId, PageRequest.of(page, 20));
        return new SavedPlacePage(result.stream().map(this::dto).toList(), result.size() == 20);
    }
    /** Creates a non-timeline place for the owner without contacting its external URL. */
    public SavedPlace create(UUID user, UUID tripId, SavePlace input) {
        access.requireOwner(user, TravelService.RESOURCE_TYPE, tripId);
        if (input.version() != 0) throw new AuthFailure(400, "invalidTravel");
        if (places.countByTripId(tripId) >= 500) throw new AuthFailure(409, "savedPlaceLimit");
        var place = new TravelSavedPlace(); place.tripId = tripId;
        apply(place, input); return dto(places.saveAndFlush(place));
    }
    /** Uses last-read versions to protect another device's newer saved-place edit. */
    public SavedPlace update(UUID user, UUID tripId, UUID id, SavePlace input) {
        var place = require(user, tripId, id);
        if (place.version != input.version()) throw new AuthFailure(409, "conflict");
        apply(place, input); return dto(places.saveAndFlush(place));
    }
    /** Removes only an owner's matching trip row at the expected version. */
    public void delete(UUID user, UUID tripId, UUID id, long version) {
        var place = require(user, tripId, id);
        if (place.version != version) throw new AuthFailure(409, "conflict");
        places.delete(place); places.flush();
    }
    private TravelSavedPlace require(UUID user, UUID tripId, UUID id) {
        access.requireOwner(user, TravelService.RESOURCE_TYPE, tripId);
        return places.findByIdAndTripId(id, tripId).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }
    private void apply(TravelSavedPlace place, SavePlace input) {
        String url = input.url() == null ? "" : input.url().strip();
        if (!url.isEmpty()) {
            try {
                URI parsed = URI.create(url);
                if (!("https".equalsIgnoreCase(parsed.getScheme()) || "http".equalsIgnoreCase(parsed.getScheme())) ||
                    parsed.getHost() == null || parsed.getUserInfo() != null || url.chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException error) { throw new AuthFailure(400, "invalidTravel"); }
        }
        place.title = input.title().strip(); place.url = url;
        place.notes = input.notes() == null ? "" : input.notes().strip();
    }
    private SavedPlace dto(TravelSavedPlace place) {
        return new SavedPlace(place.id, place.tripId, place.title, place.url, place.notes, place.version);
    }
}
