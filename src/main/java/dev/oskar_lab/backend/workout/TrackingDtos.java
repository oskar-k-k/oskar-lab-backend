package dev.oskar_lab.backend.workout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Validated tracking payloads; actual values need not meet the planned target. */
public final class TrackingDtos {
    private TrackingDtos() {}
    public record SetResult(@Min(1) @Max(100) int setNumber, @Min(0) @Max(86400) int value,
            @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2) BigDecimal weight) {}
    public record SaveLog(@NotNull UUID id, @NotNull UUID planId, @Min(0) long planVersion,
            @Min(0) @Max(99) int position, @NotNull UUID exerciseId,
            @NotNull @Pattern(regexp = "reps|seconds|weighted") String trackingMode,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid SetResult> sets) {}
    public record Log(UUID id, UUID exerciseId, String planName, String trackingMode, Instant recordedAt, List<SetResult> sets) {}
    public record History(List<Log> entries, boolean hasMore) {}
}
