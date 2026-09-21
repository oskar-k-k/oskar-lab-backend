package dev.oskar_lab.backend.workout;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Immutable exercise performance snapshot, independent of later prescription edits. */
@Entity
@Table(name = "workout_logs")
public class WorkoutLog {
    @Id public UUID id;
    @Column(nullable = false) public UUID ownerId;
    @Column(nullable = false) public UUID exerciseId;
    @Column(nullable = false) public UUID planId;
    public long planVersion;
    public int position;
    @Column(nullable = false, length = 120) public String planName;
    @Column(nullable = false, length = 12) public String trackingMode;
    @Column(nullable = false) public Instant recordedAt;
    @ElementCollection
    @CollectionTable(name = "workout_log_sets", joinColumns = @JoinColumn(name = "log_id"))
    @OrderBy("setNumber ASC")
    public List<PerformanceSet> sets = new ArrayList<>();

    /** One actual set; value is repetitions or seconds and weight is kilograms. */
    @Embeddable
    public static class PerformanceSet {
        public int setNumber;
        @Column(name = "actual_value", nullable = false) public int value;
        @Column(precision = 8, scale = 2) public BigDecimal weight;
    }
}
