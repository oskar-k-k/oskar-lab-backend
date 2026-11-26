package dev.oskar_lab.backend.travel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** JSON-only travel contracts. Unknown local times remain null rather than becoming midnight. */
public final class TravelDtos {
    private TravelDtos() {}

    @Schema(name = "TravelSaveTrip")
    public record SaveTrip(@NotBlank @Size(max = 160) String title, @Size(max = 4000) String notes,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotNull @PositiveOrZero Long version) {}

    @Schema(name = "TravelTrip")
    public record Trip(UUID id, UUID ownerId, String title, String notes, LocalDate startDate, LocalDate endDate,
                       long version, boolean canEdit) {}

    @Schema(name = "TravelTripPage")
    public record TripPage(List<Trip> trips, boolean hasMore) {}

    @Schema(name = "TravelPoint")
    public record Point(@NotNull PointRole role, @NotNull LocalDate date, @Schema(nullable = true) LocalTime time,
        @NotBlank @Size(max = 64) String timeZone, @Schema(nullable = true) @Size(max = 9) String utcOffset,
        @NotNull TimeMeaning timeMeaning, @NotBlank @Size(max = 200) String placeName, @Size(max = 1000) String address,
        @Schema(nullable = true) @Size(max = 16000) String mapEmbedUrl) {}

    @Schema(name = "TravelSaveEntry")
    public record SaveEntry(@NotNull EntryType type, @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String notes, @Size(max = 120) String reference,
        @NotNull @Size(min = 1, max = 2) List<@NotNull @Valid Point> points,
        @NotNull @PositiveOrZero Long version, @Size(max = 160) String bookingSource) {}

    @Schema(name = "TravelEntry")
    public record Entry(UUID id, UUID tripId, EntryType type, String title, String notes, String reference,
                        List<Point> points, @Schema(nullable = true) Long durationMinutes, long version, String bookingSource) {}

    @Schema(name = "TravelEntryPage")
    public record EntryPage(List<Entry> entries, boolean hasMore) {}

    @Schema(name = "TravelInviteViewer")
    public record InviteViewer(@NotBlank @Size(max = 254) String email) {}

    @Schema(name = "TravelViewer")
    public record Viewer(UUID id, String email) {}

    @Schema(name = "TravelAttachment")
    public record Attachment(UUID id, UUID entryId, @Schema(nullable = true) PointRole pointRole,
                             String filename, String mediaType, long size) {}
}
