package dev.oskar_lab.backend.workout;

import dev.oskar_lab.backend.auth.AuthFailure;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static dev.oskar_lab.backend.workout.TrackingDtos.*;

/** Stores immutable, owner-scoped set results with safe retries after lost responses. */
@Service
@Transactional
public class TrackingService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final WorkoutLogRepository logs;
    private final WorkoutPlanRepository plans;
    public TrackingService(WorkoutLogRepository logs, WorkoutPlanRepository plans) {
        this.logs = logs; this.plans = plans;
    }

    /** A repeated ID returns the original entry only when its complete payload matches. */
    public Log save(UUID owner, SaveLog input) {
        var previous = logs.findById(input.id());
        if (previous.isPresent()) {
            WorkoutLog old = previous.get();
            if (!owner.equals(old.ownerId) || !input.planId().equals(old.planId) || input.planVersion() != old.planVersion ||
                    input.position() != old.position || !input.exerciseId().equals(old.exerciseId) ||
                    !input.trackingMode().equals(old.trackingMode) || !sameSets(old, input)) throw new AuthFailure(409, "conflict");
            return dto(old);
        }
        WorkoutPlan plan = plans.findById(input.planId()).filter(p -> p.ownerId == null || owner.equals(p.ownerId))
                .orElseThrow(() -> new AuthFailure(404, "notFound"));
        if (plan.version != input.planVersion() || input.position() >= plan.exercises.size()) throw new AuthFailure(409, "conflict");
        PlanExercise prescription = plan.exercises.get(input.position());
        if (!prescription.exercise.getId().equals(input.exerciseId()) || !prescription.trackingMode.equals(input.trackingMode()))
            throw new AuthFailure(409, "conflict");
        int last = 0;
        for (SetResult set : input.sets()) {
            if (set.setNumber() <= last || ("weighted".equals(input.trackingMode()) != (set.weight() != null)))
                throw new AuthFailure(400, "invalidTracking");
            last = set.setNumber();
        }
        WorkoutLog log = new WorkoutLog();
        log.id = input.id(); log.ownerId = owner; log.planId = plan.id; log.planVersion = plan.version;
        log.position = input.position(); log.exerciseId = input.exerciseId(); log.planName = plan.name;
        log.trackingMode = input.trackingMode(); log.recordedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        for (SetResult set : input.sets()) {
            var row = new WorkoutLog.PerformanceSet();
            row.setNumber = set.setNumber(); row.value = set.value(); row.weight = set.weight() == null ? null : set.weight().setScale(2);
            log.sets.add(row);
        }
        entityManager.persist(log);
        entityManager.flush();
        return dto(log);
    }

    @Transactional(readOnly = true)
    public History history(UUID owner, UUID exercise, int page) {
        var result = logs.findByOwnerIdAndExerciseIdOrderByRecordedAtDescIdDesc(owner, exercise, PageRequest.of(page, 20));
        return new History(result.stream().map(this::dto).toList(), result.size() == 20);
    }

    private boolean sameSets(WorkoutLog log, SaveLog input) {
        if (log.sets.size() != input.sets().size()) return false;
        for (int i = 0; i < log.sets.size(); i++) {
            var a = log.sets.get(i); var b = input.sets().get(i);
            if (a.setNumber != b.setNumber() || a.value != b.value() ||
                    (a.weight == null ? b.weight() != null : b.weight() == null || a.weight.compareTo(b.weight()) != 0)) return false;
        }
        return true;
    }
    private Log dto(WorkoutLog log) {
        return new Log(log.id, log.exerciseId, log.planName, log.trackingMode, log.recordedAt,
                log.sets.stream().map(s -> new SetResult(s.setNumber, s.value, s.weight)).toList());
    }
}
