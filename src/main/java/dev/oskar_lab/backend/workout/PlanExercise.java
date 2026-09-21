package dev.oskar_lab.backend.workout;

import jakarta.persistence.*;
import java.util.UUID;

/** Ordered prescription with optional ranges; null targets explicitly mean unspecified. */
@Entity
@Table(name = "workout_plan_exercises")
public class PlanExercise {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "plan_id") public WorkoutPlan plan;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "exercise_id") public Exercise exercise;
    public int position;
    public int setsMin;
    public int setsMax;
    @Column(nullable = false, length = 12) public String mode;
    @org.hibernate.annotations.ColumnDefault("'reps'")
    @Column(nullable = false, length = 12) public String trackingMode = "reps";
    public Integer targetMin;
    public Integer targetMax;
    public Integer restMin;
    public Integer restMax;
    @Column(nullable = false, length = 32) public String superset = "";
    @Column(nullable = false, length = 1000) public String notes = "";
}
