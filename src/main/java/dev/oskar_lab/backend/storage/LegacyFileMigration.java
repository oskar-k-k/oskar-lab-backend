package dev.oskar_lab.backend.storage;

import dev.oskar_lab.backend.travel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** One-way verified migration; failed copies leave legacy bytes intact and queue uploaded-object cleanup. */
@Service
public class LegacyFileMigration {
    private final TravelAttachmentRepository attachments;
    private final TravelAttachmentContentRepository contents;
    private final FileStorageService storage;
    public LegacyFileMigration(TravelAttachmentRepository attachments, TravelAttachmentContentRepository contents, FileStorageService storage) {
        this.attachments = attachments; this.contents = contents; this.storage = storage;
    }

    /** Migrates a bounded batch and removes old bytea rows only after a verified cloud round trip. */
    @Transactional
    public Map<String, Long> migrate() {
        long migrated = 0;
        for (var attachment : attachments.findTop20ByOrbitFileIdIsNullOrderByIdAsc()) {
            var content = contents.findById(attachment.id).orElseThrow(() -> new StorageFailure(503, "legacyContentMissing"));
            String hash = OrbitStorageClient.hash(content.bytes);
            var uploaded = storage.upload("thailand", attachment.filename, attachment.mediaType, content.bytes);
            storage.download("thailand", uploaded.id(), attachment.size, hash);
            attachment.orbitFileId = uploaded.id(); attachment.sha256 = hash;
            attachments.saveAndFlush(attachment);
            contents.delete(content);
            migrated++;
        }
        contents.flush();
        return Map.of("migrated", migrated, "remaining", contents.count());
    }
}
