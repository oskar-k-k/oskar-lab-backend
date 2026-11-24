package dev.oskar_lab.backend.storage;

import jakarta.persistence.*;
import java.util.UUID;

/** Durable deletion intent, committed atomically with removal of the owning domain record. */
@Entity @Table(name = "platform_file_deletions", uniqueConstraints = @UniqueConstraint(columnNames = {"project", "orbit_file_id"}))
public class PendingFileDeletion {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false, length = 64) public String project;
    @Column(nullable = false) public UUID orbitFileId;
}
