package dev.oskar_lab.backend;

import dev.oskar_lab.backend.travel.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class TravelRulesTests {
    @Test void keepsPlannedStayTimesIndependentAndValidatesTheirZones() {
        var start = new Point(PointRole.CHECK_IN, LocalDate.of(2030,12,1), LocalTime.of(14,0), "Asia/Bangkok", null,
            TimeMeaning.NOT_BEFORE, "Example stay", "", null, LocalTime.of(17,0), null);
        var end = point(PointRole.CHECK_OUT, "2030-12-03", "11:00", "Asia/Bangkok", null, TimeMeaning.NOT_AFTER);
        var normalized = TravelRules.normalize(EntryType.HOTEL, List.of(start,end));
        assertEquals(LocalTime.of(14,0), normalized.getFirst().time());
        assertEquals(LocalTime.of(17,0), normalized.getFirst().timelineTime());
        assertEquals("+07:00", normalized.getFirst().timelineUtcOffset());
        assertNull(TravelRules.durationMinutes(normalized));
        var gap = new Point(PointRole.CHECK_IN, LocalDate.of(2026,3,29), null, "Europe/Berlin", null,
            TimeMeaning.UNSPECIFIED, "Example stay", "", null, LocalTime.of(2,30), null);
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.HOTEL, List.of(gap,end)));
        var activity = new Point(PointRole.START, start.date(), null, "Asia/Bangkok", null,
            TimeMeaning.UNSPECIFIED, "Example", "", null, LocalTime.NOON, null);
        assertEquals(LocalTime.NOON, TravelRules.normalize(EntryType.ACTIVITY, List.of(activity)).getFirst().timelineTime());
    }
    private Point point(PointRole role, String date, String time, String zone, String offset, TimeMeaning meaning) {
        return new Point(role, LocalDate.parse(date), time == null ? null : LocalTime.parse(time), zone, offset, meaning, "Example place", "Example address", null);
    }

    @Test void computesFlightDurationAcrossTimeZonesAndMidnight() {
        var points = TravelRules.normalize(EntryType.FLIGHT, List.of(
            point(PointRole.DEPARTURE, "2030-12-01", "20:00", "Europe/Berlin", null, TimeMeaning.EXACT),
            point(PointRole.ARRIVAL, "2030-12-02", "12:00", "Asia/Bangkok", null, TimeMeaning.EXACT)));
        assertEquals("+01:00", points.getFirst().utcOffset());
        assertEquals("+07:00", points.getLast().utcOffset());
        assertEquals(600L, TravelRules.durationMinutes(points));
    }

    @Test void handlesDateLineCrossingsUsingInstantsInsteadOfLocalDateOrder() {
        var points = TravelRules.normalize(EntryType.FLIGHT, List.of(
            point(PointRole.DEPARTURE, "2030-06-02", "10:00", "Pacific/Auckland", null, TimeMeaning.EXACT),
            point(PointRole.ARRIVAL, "2030-06-01", "23:00", "Pacific/Honolulu", null, TimeMeaning.EXACT)));
        assertEquals(660L, TravelRules.durationMinutes(points));
    }

    @Test void preservesHotelWindowsAndUnknownHours() {
        var points = TravelRules.normalize(EntryType.HOTEL, List.of(
            point(PointRole.CHECK_IN, "2030-12-02", "15:00", "Asia/Bangkok", null, TimeMeaning.NOT_BEFORE),
            point(PointRole.CHECK_OUT, "2030-12-05", "11:00", "Asia/Bangkok", null, TimeMeaning.NOT_AFTER)));
        assertNull(TravelRules.durationMinutes(points));
        var unknown = TravelRules.normalize(EntryType.ACTIVITY, List.of(point(PointRole.START, "2030-12-03", null, "Asia/Bangkok", null, TimeMeaning.UNSPECIFIED)));
        assertNull(unknown.getFirst().time());
        assertNull(unknown.getFirst().utcOffset());
    }

    @Test void rejectsInvalidRolesZonesAndImpossibleTimeRanges() {
        var departure = point(PointRole.DEPARTURE, "2030-12-02", "10:00", "UTC", null, TimeMeaning.EXACT);
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.FLIGHT, List.of(departure)));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.FLIGHT, List.of(departure, departure)));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.FLIGHT, List.of(departure,
            point(PointRole.ARRIVAL, "2030-12-02", "09:00", "UTC", null, TimeMeaning.EXACT))));
        assertThrows(DateTimeException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-12-02", "10:00", "Unknown/Place", null, TimeMeaning.EXACT))));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-12-02", null, "UTC", null, TimeMeaning.EXACT))));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-12-02", "10:00", "UTC", null, TimeMeaning.NOT_AFTER))));
    }

    @Test void rejectsDstGapsAndRequiresAnExplicitOffsetForOverlaps() {
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-03-31", "02:30", "Europe/Berlin", null, TimeMeaning.EXACT))));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-10-27", "02:30", "Europe/Berlin", null, TimeMeaning.EXACT))));
        var points = TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-10-27", "02:30", "Europe/Berlin", "+01:00", TimeMeaning.EXACT)));
        assertEquals("+01:00", points.getFirst().utcOffset());
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY,
            List.of(point(PointRole.START, "2030-10-27", "02:30", "Europe/Berlin", "+03:00", TimeMeaning.EXACT))));
    }
    @Test void acceptsOnlyKeyFreeGoogleShareEmbeds() {
        String valid = "https://www.google.com/maps/embed?pb=!1m18!example";
        assertEquals(valid, TravelRules.normalizeMapEmbedUrl(valid));
        assertNull(TravelRules.normalizeMapEmbedUrl(" "));
        for (String invalid : List.of("javascript:alert(1)", "https://evil.invalid/maps/embed?pb=x",
            "https://www.google.com.evil.invalid/maps/embed?pb=x", "https://www.google.com/maps/embed/v1/place?key=x&q=y",
            "https://user@www.google.com/maps/embed?pb=x", "https://www.google.com/maps/embed?pb=x&key=secret",
            "https://www.google.com/maps/embed?pb=x&pb=y", "https://www.google.com/maps/embed?hl=de")) {
            assertThrows(IllegalArgumentException.class, () -> TravelRules.normalizeMapEmbedUrl(invalid));
        }
    }

    @Test void plansAirportPresenceOnThePreviousDayWithoutChangingTheFlight() {
        var departure = new Point(PointRole.DEPARTURE, LocalDate.of(2030,12,2), LocalTime.of(1,0), "Europe/Berlin", null,
            TimeMeaning.EXACT, "Example airport", "", null, LocalTime.of(22,0), null, LocalDate.of(2030,12,1));
        var arrival = point(PointRole.ARRIVAL, "2030-12-02", "13:00", "Asia/Bangkok", null, TimeMeaning.EXACT);
        var normalized = TravelRules.normalize(EntryType.FLIGHT, List.of(departure, arrival));
        assertEquals(LocalDate.of(2030,12,1), normalized.getFirst().timelineDate());
        assertEquals(LocalTime.of(1,0), normalized.getFirst().time());
        assertEquals(360L, TravelRules.durationMinutes(normalized));
        var reversed = new Point(PointRole.DEPARTURE, departure.date(), departure.time(), departure.timeZone(), null,
            TimeMeaning.EXACT, "Example", "", null, LocalTime.of(15,0), null, departure.date());
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.FLIGHT, List.of(reversed, arrival)));
    }

    @Test void validatesThePlannedDateZoneAndCompletePlanForEveryRole() {
        var gap = new Point(PointRole.START, LocalDate.of(2030,3,30), LocalTime.NOON, "Europe/Berlin", null,
            TimeMeaning.EXACT, "Example", "", null, LocalTime.of(2,30), null, LocalDate.of(2030,3,31));
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY, List.of(gap)));
        var overlap = new Point(PointRole.START, LocalDate.of(2030,10,26), LocalTime.NOON, "Europe/Berlin", null,
            TimeMeaning.EXACT, "Example", "", null, LocalTime.of(2,30), "+01:00", LocalDate.of(2030,10,27));
        assertEquals("+01:00", TravelRules.normalize(EntryType.ACTIVITY, List.of(overlap)).getFirst().timelineUtcOffset());
        var missingTime = new Point(PointRole.START, gap.date(), gap.time(), gap.timeZone(), null,
            TimeMeaning.EXACT, "Example", "", null, null, null, gap.date());
        assertThrows(IllegalArgumentException.class, () -> TravelRules.normalize(EntryType.ACTIVITY, List.of(missingTime)));
    }
}
