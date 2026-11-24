package dev.oskar_lab.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Operator-only bridge endpoint. Disabled by default and never exposed by a browser adapter. */
@RestController
@RequestMapping("/internal/storage/migrations")
public class StorageMigrationController {
    private final LegacyFileMigration migration;
    private final String bridge;
    private final boolean enabled;
    public StorageMigrationController(LegacyFileMigration migration, @Value("${auth.bridge-secret:}") String bridge,
                                      @Value("${orbit.migration-enabled:false}") boolean enabled) {
        this.migration = migration; this.bridge = bridge; this.enabled = enabled;
    }
    /** Copies at most twenty legacy documents and reports counts only. */
    @PostMapping("/travel")
    public ResponseEntity<Map<String, Long>> migrate(@RequestHeader(value = "X-Auth-Bridge", defaultValue = "") String key) {
        if (!enabled || bridge.length() < 32 || !MessageDigest.isEqual(bridge.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new StorageFailure(403, "forbidden");
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(migration.migrate());
    }
}
