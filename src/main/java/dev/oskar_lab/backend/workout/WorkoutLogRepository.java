package dev.oskar_lab.backend.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Retrieves only an owner's exercise history, newest first with stable tie ordering. */
public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, UUID> {
    List<WorkoutLog> findByOwnerIdAndExerciseIdOrderByRecordedAtDescIdDesc(UUID ownerId, UUID exerciseId, Pageable page);
}
