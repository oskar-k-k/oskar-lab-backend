package dev.oskar_lab.backend.workout;

import jakarta.persistence.*;
import java.util.UUID;

/** Reusable exercise catalog entry; prescriptions belong to plans. */
@Entity
@Table(name = "workout_exercises")
public class Exercise {
    @Id public UUID id;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 120) public String nameDe;

    /** Reads the identifier safely even when this entity is a lazy JPA reference. */
    public UUID getId() { return id; }
}
