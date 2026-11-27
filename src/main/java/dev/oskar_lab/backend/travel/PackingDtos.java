package dev.oskar_lab.backend.travel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

/** Bounded, ordered packing-list contract; IDs remain stable across edits and checks. */
public final class PackingDtos {
    private PackingDtos() {}
    public record PackingItem(@NotNull UUID id, @NotBlank @Size(max = 200) String name, @NotNull Boolean checked) {}
    public record PackingGroup(@NotNull UUID id, @NotBlank @Size(max = 100) String name,
                               @NotNull @Size(max = 100) List<@NotNull @Valid PackingItem> items) {}
    public record PackingPerson(@NotNull UUID id, @NotBlank @Size(max = 80) String name,
                                @NotNull @Size(max = 40) List<@NotNull @Valid PackingGroup> groups) {}
    public record PackingList(@PositiveOrZero long version,
                              @NotNull @Size(max = 12) List<@NotNull @Valid PackingPerson> people) {}
}
