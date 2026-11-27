package dev.oskar_lab.backend.travel;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Bounds every saved-place read and scopes individual IDs to their parent trip. */
public interface TravelSavedPlaceRepository extends JpaRepository<TravelSavedPlace, UUID> {
    List<TravelSavedPlace> findByTripIdOrderByCreatedAtDescIdDesc(UUID tripId, Pageable page);
    Optional<TravelSavedPlace> findByIdAndTripId(UUID id, UUID tripId);
    long countByTripId(UUID tripId);
    void deleteByTripId(UUID tripId);
}
