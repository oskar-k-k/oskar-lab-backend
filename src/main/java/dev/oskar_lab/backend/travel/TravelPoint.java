package dev.oskar_lab.backend.travel;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** A dated place with explicit local-time semantics and a validated zone offset. */
@Entity
@Table(name = "travel_points", uniqueConstraints = @UniqueConstraint(columnNames = {"entry_id", "role"}))
public class TravelPoint {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entry_id") public TravelEntry entry;
    @Column(nullable = false) public int position;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public PointRole role;
    @Column(name = "local_date", nullable = false) public LocalDate date;
    @Column(name = "local_time") public LocalTime time;
    @Column(nullable = false, length = 64) public String timeZone;
    @Column(length = 9) public String utcOffset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) public TimeMeaning timeMeaning;
    @Column(nullable = false, length = 200) public String placeName;
    @Column(nullable = false, length = 1000) public String address = "";
    @Column(length = 16000) public String mapEmbedUrl;
    public LocalTime timelineTime;
    @Column(length = 9) public String timelineUtcOffset;
}
