package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** One booking or activity groups its points; unrelated activities may occur during a stay. */
@Entity
@Table(name = "travel_entries")
public class TravelEntry {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID tripId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public EntryType type;
    @Column(nullable = false, length = 200) public String title;
    @Column(nullable = false, length = 4000) public String notes = "";
    @Column(nullable = false, length = 120) public String reference = "";
    @Column(nullable = false, length = 160) public String bookingSource = "";
    @Column(nullable = false) public LocalDate sortDate;
    @Column(nullable = false) public LocalTime sortTime;
    @Column(nullable = false) public Instant updatedAt = Instant.now();
    @Version public long version;
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<TravelPoint> points = new ArrayList<>();
}
