package dev.oskar_lab.backend.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Persistent exercise catalog. */
public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findAllByOrderByNameAsc();
}
