package dev.oskar_lab.backend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.slf4j.LoggerFactory;

/** Retries committed deletion intents without holding database transactions during network I/O. */
@Configuration @EnableScheduling
@ConditionalOnProperty(name = "orbit.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class FileCleanup {
    private final FileDeletionRepository deletions;
    private final OrbitStorageClient orbit;
    public FileCleanup(FileDeletionRepository deletions, OrbitStorageClient orbit) { this.deletions = deletions; this.orbit = orbit; }

    /** Removes confirmed cloud objects; unavailable storage leaves the intent for a later attempt. */
    @Scheduled(fixedDelayString = "${orbit.cleanup.delay-ms:30000}", initialDelayString = "${orbit.cleanup.delay-ms:30000}")
    public void reconcile() {
        for (var pending : deletions.findTop20ByOrderByIdAsc()) {
            try { orbit.delete(pending.project, pending.orbitFileId); deletions.deleteById(pending.id); }
            catch (StorageFailure error) { LoggerFactory.getLogger(FileCleanup.class).warn("OrbitStorage deletion remains pending: {}", error.getMessage()); }
        }
    }
}
