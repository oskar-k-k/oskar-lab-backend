package dev.oskar_lab.backend.travel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.*;

/** Private inspiration links are separate from dated bookings and never fetched by the server. */
public final class SavedPlaceDtos {
    private SavedPlaceDtos() {}
    @Schema(name = "TravelSavePlace")
    public record SavePlace(@NotBlank @Size(max = 160) String title, @Size(max = 2048) String url,
                            @Size(max = 1000) String notes, @NotNull @PositiveOrZero Long version) {}
    @Schema(name = "TravelSavedPlace")
    public record SavedPlace(UUID id, UUID tripId, String title, String url, String notes, long version) {}
    @Schema(name = "TravelSavedPlacePage")
    public record SavedPlacePage(List<SavedPlace> places, boolean hasMore) {}
}
