package dev.oskar_lab.backend.workout;

import jakarta.persistence.*;
import java.util.*;

/** A reusable session, owned by a platform account or a read-only starter template. */
@Entity
@Table(name = "workout_plans")
public class WorkoutPlan {
    @Id public UUID id = UUID.randomUUID();
    public UUID ownerId;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 2000) public String notes = "";
    @Version @org.hibernate.annotations.ColumnDefault("0") public long version;
    @Column(nullable = false) @org.hibernate.annotations.ColumnDefault("CURRENT_TIMESTAMP")
    public java.time.Instant updatedAt = java.time.Instant.now();
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    public List<PlanExercise> exercises = new ArrayList<>();
}
