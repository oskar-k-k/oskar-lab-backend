package dev.oskar_lab.backend.travel;

import java.time.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.oskar_lab.backend.travel.TravelDtos.*;
import static dev.oskar_lab.backend.travel.TravelTypes.*;

/** Validates booking structure and local times without guessing offsets or unknown hours. */
public final class TravelRules {
    private TravelRules() {}

    /** Normalizes valid points; daylight-saving overlaps require an explicit matching offset. */
    public static List<Point> normalize(EntryType type, List<Point> points) {
        List<PointRole> expected = switch (type) {
            case FLIGHT, BOAT, TRAIN, BUS, TRANSFER -> List.of(PointRole.DEPARTURE, PointRole.ARRIVAL);
            case HOTEL, APARTMENT -> List.of(PointRole.CHECK_IN, PointRole.CHECK_OUT);
            default -> points.size() == 1 ? List.of(PointRole.START) : List.of(PointRole.START, PointRole.END);
        };
        if (!points.stream().map(Point::role).toList().equals(expected)) throw new IllegalArgumentException();
        List<Point> normalized = points.stream().map(TravelRules::normalizePoint).toList();
        if (normalized.size() == 2) {
            Point start = normalized.getFirst();
            Point end = normalized.getLast();
            if (start.time() != null && end.time() != null) {
                if (instant(end).isBefore(instant(start))) throw new IllegalArgumentException();
            } else if (!isTransport(type) && end.date().isBefore(start.date())) {
                throw new IllegalArgumentException();
            }
        }
        return normalized;
    }

    /** Derives elapsed minutes only when both endpoints represent exact, known instants. */
    public static Long durationMinutes(List<Point> points) {
        if (points.size() != 2 || points.stream().anyMatch(p -> p.timeMeaning() != TimeMeaning.EXACT || p.time() == null)) return null;
        return Duration.between(instant(points.getFirst()), instant(points.getLast())).toMinutes();
    }

    /** Bounds dates to a supported travel range without relying on database conversion errors. */
    public static void validateDate(LocalDate date) {
        if (date.getYear() < 1900 || date.getYear() > 2200) throw new IllegalArgumentException();
    }

    private static Point normalizePoint(Point point) {
        validateDate(point.date());
        ZoneId zone = ZoneId.of(point.timeZone());
        String offset = null;
        if (point.time() == null) {
            if (point.timeMeaning() != TimeMeaning.UNSPECIFIED || point.utcOffset() != null) throw new IllegalArgumentException();
        } else {
            if (point.timeMeaning() == TimeMeaning.UNSPECIFIED) throw new IllegalArgumentException();
            if (point.timeMeaning() == TimeMeaning.NOT_BEFORE && point.role() != PointRole.CHECK_IN) throw new IllegalArgumentException();
            if (point.timeMeaning() == TimeMeaning.NOT_AFTER && point.role() != PointRole.CHECK_OUT) throw new IllegalArgumentException();
            List<ZoneOffset> offsets = zone.getRules().getValidOffsets(LocalDateTime.of(point.date(), point.time()));
            ZoneOffset selected = point.utcOffset() == null ? (offsets.size() == 1 ? offsets.getFirst() : null) : ZoneOffset.of(point.utcOffset());
            if (selected == null || !offsets.contains(selected)) throw new IllegalArgumentException();
            offset = selected.toString();
        }
        return new Point(point.role(), point.date(), point.time(), zone.getId(), offset,
            point.timeMeaning(), point.placeName().strip(), point.address() == null ? "" : point.address().strip(), normalizeMapEmbedUrl(point.mapEmbedUrl()));
    }

    /** Allows only key-free Google Maps share embeds, never arbitrary frames or API-key URLs. */
    public static String normalizeMapEmbedUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        URI uri = URI.create(normalized);
        if (normalized.length() > 16000 || !"https".equals(uri.getScheme()) ||
            !"www.google.com".equals(uri.getHost()) || uri.getRawUserInfo() != null ||
            uri.getPort() != -1 || uri.getFragment() != null || !"/maps/embed".equals(uri.getRawPath()) ||
            uri.getRawQuery() == null) throw new IllegalArgumentException();
        boolean hasPlace = false;
        Set<String> seen = new HashSet<>();
        for (String parameter : uri.getRawQuery().split("&")) {
            String[] pair = parameter.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (!Set.of("pb", "hl").contains(key) || !seen.add(key) || pair.length != 2 || pair[1].isBlank())
                throw new IllegalArgumentException();
            if (key.equals("pb")) hasPlace = true;
        }
        if (!hasPlace) throw new IllegalArgumentException();
        return normalized;
    }

    private static Instant instant(Point point) {
        return LocalDateTime.of(point.date(), point.time()).toInstant(ZoneOffset.of(point.utcOffset()));
    }

    private static boolean isTransport(EntryType type) {
        return switch (type) {case FLIGHT, BOAT, TRAIN, BUS, TRANSFER -> true; default -> false;};
    }
}
