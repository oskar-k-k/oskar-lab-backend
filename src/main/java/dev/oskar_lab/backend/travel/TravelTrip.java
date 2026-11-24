package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/** Travel data uses the same ID as its platform-owned resource. */
@Entity
@Table(name = "travel_trips")
public class TravelTrip {
    @Id public UUID id;
    @Column(nullable = false, length = 160) public String title;
    @Column(nullable = false, length = 4000) public String notes = "";
    @Column(nullable = false) public LocalDate startDate;
    @Column(nullable = false) public LocalDate endDate;
    @Version public long version;
}
