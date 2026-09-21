package dev.oskar_lab.backend.workout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Queries always scope personal sessions to their owner. */
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, UUID> {
    List<WorkoutPlan> findByOwnerIdOrderByNameAscIdAsc(UUID ownerId, Pageable page);
    List<WorkoutPlan> findByOwnerIdIsNullOrderByNameAsc();
    Optional<WorkoutPlan> findByIdAndOwnerId(UUID id, UUID ownerId);
}
