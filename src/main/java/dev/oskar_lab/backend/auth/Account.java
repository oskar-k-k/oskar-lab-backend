package dev.oskar_lab.backend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Persistent platform identity, independent of any app or login provider. */
@Entity
@Table(name = "platform_accounts")
public class Account {
    @Id public UUID id = UUID.randomUUID();
    @Column(unique = true, length = 32) public String username;
    @Column(unique = true, length = 32) public String usernameKey;
    @Column(nullable = false, unique = true, length = 254) public String email;
    @Column(length = 256) public String passwordHash;
    @Column(unique = true) public String googleSubject;
    public Instant termsAcceptedAt;
    public String termsVersion;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
