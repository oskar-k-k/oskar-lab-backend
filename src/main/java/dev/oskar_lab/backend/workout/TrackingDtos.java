package dev.oskar_lab.backend.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

/** Validated tracking payloads; actual values need not meet the planned target. */
public final class TrackingDtos {
    private TrackingDtos() {}
    public record SetResult(@Min(1) @Max(100) int setNumber, @NotNull @Min(0) @Max(86400) Integer value,
            @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2) BigDecimal weight) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"planId", "planVersion", "position"})
    public record SaveLog(@NotNull UUID id, @NotNull UUID exerciseId,
            @Schema(allowableValues = {"reps", "seconds", "weighted"}) @NotNull @Pattern(regexp = "reps|seconds|weighted") String trackingMode,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid SetResult> sets) {}
    public record Log(UUID id, UUID exerciseId, @Schema(allowableValues = {"reps", "seconds", "weighted"}) String trackingMode, Instant recordedAt, List<SetResult> sets) {}
    public record History(List<Log> entries, boolean hasMore) {}
}
