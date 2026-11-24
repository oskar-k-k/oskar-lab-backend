package dev.oskar_lab.backend.access;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A normalized email grant, usable only by an account with a verified matching address. */
@Entity
@Table(name = "platform_resource_viewers", uniqueConstraints = @UniqueConstraint(columnNames = {"resource_id", "email"}))
public class ResourceViewer {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID resourceId;
    @Column(nullable = false, length = 254) public String email;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
