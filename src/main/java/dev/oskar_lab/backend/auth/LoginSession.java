package dev.oskar_lab.backend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Stores only the digest of a revocable, opaque session credential. */
@Entity
@Table(name = "platform_sessions")
public class LoginSession {
    @Id @Column(length = 64) public String digest;
    @Column(nullable = false) public UUID accountId;
    @Column(nullable = false) public Instant expiresAt;
}
