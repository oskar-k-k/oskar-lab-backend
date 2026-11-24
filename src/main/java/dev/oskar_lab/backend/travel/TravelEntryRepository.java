package dev.oskar_lab.backend.travel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Scopes every entry lookup to its trip; page order is the first point's local date/time. */
public interface TravelEntryRepository extends JpaRepository<TravelEntry, UUID> {
    Optional<TravelEntry> findByIdAndTripId(UUID id, UUID tripId);
    List<TravelEntry> findByTripIdOrderBySortDateAscSortTimeAscIdAsc(UUID tripId, Pageable page);
    List<TravelEntry> findByTripId(UUID tripId);
    long countByTripId(UUID tripId);
}
