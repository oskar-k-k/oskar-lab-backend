package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A manually saved place or inspiration link within one private trip. */
@Entity @Table(name = "travel_saved_places")
public class TravelSavedPlace {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID tripId;
    @Column(nullable = false, length = 160) public String title;
    @Column(nullable = false, length = 2048) public String url = "";
    @Column(nullable = false, length = 1000) public String notes = "";
    @Column(nullable = false) public Instant createdAt = Instant.now();
    @Version public long version;
}
