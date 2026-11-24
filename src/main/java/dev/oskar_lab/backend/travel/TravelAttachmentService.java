package dev.oskar_lab.backend.travel;

import dev.oskar_lab.backend.auth.AuthFailure;
import dev.oskar_lab.backend.storage.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** Stores document references in PostgreSQL and private bytes exclusively in the app's OrbitStorage project. */
@Service
@Transactional
public class TravelAttachmentService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final TravelService travel;
    private final TravelAttachmentRepository attachments;
    private final FileStorageService storage;

    public TravelAttachmentService(TravelService travel, TravelAttachmentRepository attachments, FileStorageService storage) {
        this.travel = travel;
        this.attachments = attachments;
        this.storage = storage;
    }

    /** Lists at most twenty attachments without loading their bytes. */
    @Transactional(readOnly = true)
    public List<Attachment> list(UUID user, UUID trip, UUID entry) {
        travel.requireEntry(user, trip, entry, false);
        return attachments.findByEntryIdOrderByIdAsc(entry).stream().map(this::dto).toList();
    }

    /** Stores a validated upload on a booking or one of its points, with bounded size/count. */
    public Attachment upload(UUID user, UUID trip, UUID entry, PointRole role, String filename, String mediaType, byte[] bytes) {
        TravelEntry booking = travel.requireEntry(user, trip, entry, true);
        if (role != null && booking.points.stream().noneMatch(p -> p.role == role)) throw new AuthFailure(400, "invalidTravel");
        if (bytes.length > MAX_BYTES) throw new AuthFailure(413, "fileTooLarge");
        if (filename == null || filename.isBlank() || filename.length() > 200 ||
            filename.chars().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\')) throw new AuthFailure(400, "invalidAttachment");
        if (!hasSignature(mediaType, bytes)) throw new AuthFailure(415, "unsupportedAttachment");
        if (attachments.findByEntryIdOrderByIdAsc(entry).size() >= 20 || attachments.countForTrip(trip) >= 100)
            throw new AuthFailure(409, "attachmentLimit");
        TravelAttachment attachment = new TravelAttachment();
        attachment.entryId = entry; attachment.pointRole = role; attachment.filename = filename.strip();
        attachment.mediaType = mediaType; attachment.size = bytes.length;
        var stored = storage.upload(attachment.storageProject, filename, mediaType, bytes);
        attachment.orbitFileId = stored.id();
        attachment.sha256 = OrbitStorageClient.hash(bytes);
        attachments.saveAndFlush(attachment);
        return dto(attachment);
    }

    /** Authorizes both parent IDs before loading binary content. */
    @Transactional(readOnly = true)
    public Download download(UUID user, UUID trip, UUID entry, UUID id) {
        travel.requireEntry(user, trip, entry, false);
        TravelAttachment attachment = attachment(entry, id);
        if (attachment.orbitFileId == null) throw new StorageFailure(503, "storageMigrationRequired");
        return new Download(dto(attachment), storage.download(attachment.storageProject, attachment.orbitFileId, attachment.size, attachment.sha256));
    }

    /** Deletes metadata and bytes together; viewers cannot remove documents. */
    public void delete(UUID user, UUID trip, UUID entry, UUID id) {
        travel.requireEntry(user, trip, entry, true);
        TravelAttachment attachment = attachment(entry, id);
        if (attachment.orbitFileId != null) storage.scheduleDelete(attachment.storageProject, attachment.orbitFileId);
        attachments.delete(attachment);
    }

    /** Internal download payload; controllers must serve this as an attachment, never as JSON. */
    public record Download(Attachment metadata, byte[] bytes) {}

    private TravelAttachment attachment(UUID entry, UUID id) {
        return attachments.findByIdAndEntryId(id, entry).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }
    private Attachment dto(TravelAttachment attachment) {
        return new Attachment(attachment.id, attachment.entryId, attachment.pointRole, attachment.filename, attachment.mediaType, attachment.size);
    }
    private boolean hasSignature(String type, byte[] bytes) {
        if (type == null) return false;
        byte[] prefix = switch (type) {
            case "application/pdf" -> new byte[]{'%', 'P', 'D', 'F', '-'};
            case "image/png" -> new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10};
            case "image/jpeg" -> new byte[]{(byte)255, (byte)216, (byte)255};
            default -> new byte[0];
        };
        return prefix.length > 0 && bytes.length > prefix.length && Arrays.equals(prefix, Arrays.copyOf(bytes, prefix.length));
    }
}
