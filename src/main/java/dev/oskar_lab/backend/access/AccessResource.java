package dev.oskar_lab.backend.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Platform ownership boundary; each product chooses a stable resource type. */
@Entity
@Table(name = "platform_resources")
public class AccessResource {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false, length = 64) public String resourceType;
    @Column(nullable = false) public UUID ownerId;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Version public long version;
}
