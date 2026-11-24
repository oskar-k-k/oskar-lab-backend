package dev.oskar_lab.backend.travel;

/** Stable booking kinds and point semantics shared by persistence and the API contract. */
public final class TravelTypes {
    private TravelTypes() {}

    /** Determines allowed points and lets clients choose an appropriate presentation. */
    public enum EntryType { FLIGHT, BOAT, TRAIN, BUS, TRANSFER, HOTEL, APARTMENT, ACTIVITY, RESTAURANT, TOUR }
    /** Connects endpoints through their parent entry without inferring relationships from dates. */
    public enum PointRole { DEPARTURE, ARRIVAL, CHECK_IN, CHECK_OUT, START, END }
    /** Distinguishes a scheduled time from a check-in window, deadline or unknown time. */
    public enum TimeMeaning { EXACT, NOT_BEFORE, NOT_AFTER, UNSPECIFIED }
}
