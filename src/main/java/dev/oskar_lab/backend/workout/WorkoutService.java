package dev.oskar_lab.backend.workout;

import dev.oskar_lab.backend.auth.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static dev.oskar_lab.backend.workout.WorkoutDtos.*;

/** Owns account isolation, prescription validation and atomic session updates. */
@Service
@Transactional
public class WorkoutService {
    private final WorkoutPlanRepository plans;
    private final ExerciseRepository exercises;
    private final AccountRepository accounts;

    public WorkoutService(WorkoutPlanRepository plans, ExerciseRepository exercises, AccountRepository accounts) {
        this.plans = plans;
        this.exercises = exercises;
        this.accounts = accounts;
    }

    /** Rejects identities that have not completed central onboarding. */
    @Transactional(readOnly = true)
    public void requireAccount(UUID owner) {
        Account account = accounts.findById(owner).orElseThrow(() -> new AuthFailure(401, "unauthorized"));
        if (account.username == null || account.termsAcceptedAt == null) throw new AuthFailure(403, "unauthorized");
    }

    @Transactional(readOnly = true)
    public List<ExerciseDto> catalog() {
        return exercises.findAllByOrderByNameAsc().stream().map(e -> new ExerciseDto(e.id, e.name, e.nameDe)).toList();
    }

    @Transactional(readOnly = true)
    public List<Plan> templates() { return plans.findByOwnerIdIsNullOrderByNameAsc().stream().map(this::dto).toList(); }

    @Transactional(readOnly = true)
    public PlanPage list(UUID owner, int page) {
        var result = plans.findByOwnerIdOrderByNameAscIdAsc(owner, PageRequest.of(page, 20));
        return new PlanPage(result.stream().map(this::dto).toList(), result.size() == 20);
    }

    @Transactional(readOnly = true)
    public Plan get(UUID owner, UUID id) { return dto(owned(owner, id)); }

    /** Client-generated IDs make a create retry safe after a lost response. */
    public Plan save(UUID owner, SavePlan input, boolean create) {
        validate(input);
        WorkoutPlan plan;
        if (create) {
            var existing = plans.findById(input.id());
            if (existing.isPresent()) {
                if (!owner.equals(existing.get().ownerId)) throw new AuthFailure(409, "conflict");
                Plan saved = dto(existing.get());
                if (!saved.name().equals(input.name().strip()) || !saved.notes().equals(input.notes()) ||
                        !saved.exercises().equals(input.exercises())) throw new AuthFailure(409, "conflict");
                return saved;
            }
            plan = new WorkoutPlan();
            plan.id = input.id();
            plan.ownerId = owner;
        } else {
            plan = owned(owner, input.id());
            if (plan.version != input.version()) throw new AuthFailure(409, "conflict");
        }
        Map<UUID, Exercise> catalog = new HashMap<>();
        exercises.findAllById(input.exercises().stream().map(Entry::exerciseId).toList()).forEach(e -> catalog.put(e.id, e));
        if (input.exercises().stream().anyMatch(e -> !catalog.containsKey(e.exerciseId()))) throw new AuthFailure(400, "invalidPlan");
        plan.name = input.name().strip();
        plan.notes = input.notes();
        plan.exercises.clear();
        for (Entry entry : input.exercises()) {
            PlanExercise row = new PlanExercise();
            row.plan = plan;
            row.exercise = catalog.get(entry.exerciseId());
            row.position = plan.exercises.size();
            row.setsMin = entry.setsMin(); row.setsMax = entry.setsMax(); row.mode = entry.mode();
            row.targetMin = entry.targetMin(); row.targetMax = entry.targetMax();
            row.restMin = entry.restMin(); row.restMax = entry.restMax();
            row.superset = entry.superset().strip(); row.notes = entry.notes();
            plan.exercises.add(row);
        }
        // A child-only edit must also participate in optimistic locking.
        plan.updatedAt = java.time.Instant.now();
        return dto(plans.saveAndFlush(plan));
    }

    private WorkoutPlan owned(UUID owner, UUID id) {
        return plans.findByIdAndOwnerId(id, owner).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }

    private void validate(SavePlan input) {
        for (Entry row : input.exercises()) {
            boolean unspecified = "unspecified".equals(row.mode());
            if (row.setsMin() > row.setsMax() || !range(row.restMin(), row.restMax()) ||
                    (unspecified ? row.targetMin() != null || row.targetMax() != null :
                            row.targetMin() == null || !range(row.targetMin(), row.targetMax()))) {
                throw new AuthFailure(400, "invalidPlan");
            }
        }
    }

    private boolean range(Integer min, Integer max) { return min == null ? max == null : max != null && min <= max; }

    private Plan dto(WorkoutPlan plan) {
        return new Plan(plan.id, plan.version, plan.name, plan.notes, plan.ownerId == null,
                plan.exercises.stream().map(e -> new Entry(e.exercise.getId(), e.setsMin, e.setsMax, e.mode,
                        e.targetMin, e.targetMax, e.restMin, e.restMax, e.superset, e.notes)).toList());
    }
}
