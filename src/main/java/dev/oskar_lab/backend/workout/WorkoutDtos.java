package dev.oskar_lab.backend.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** JSON contracts shared by the workout API; all durations are seconds. */
public final class WorkoutDtos {
    private WorkoutDtos() {}
    public record ExerciseDto(UUID id, String name, String nameDe) {}
    public record Entry(
            @NotNull UUID exerciseId,
            @Min(1) @Max(100) int setsMin, @Min(1) @Max(100) int setsMax,
            @NotNull @Pattern(regexp = "reps|seconds|unspecified") String mode,
            @Min(1) @Max(86400) Integer targetMin, @Min(1) @Max(86400) Integer targetMax,
            @Min(0) @Max(86400) Integer restMin, @Min(0) @Max(86400) Integer restMax,
            @NotNull @Size(max = 32) String superset, @NotNull @Size(max = 1000) String notes) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties("template")
    public record SavePlan(
            @NotNull UUID id, @Min(0) long version,
            @NotBlank @Size(max = 120) String name, @NotNull @Size(max = 2000) String notes,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Entry> exercises) {}
    public record Plan(UUID id, long version, String name, String notes, boolean template, List<Entry> exercises) {}
    public record PlanPage(List<Plan> plans, boolean hasMore) {}
}
