package dev.oskar_lab.backend.travel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** Persists trip details after the platform access boundary has been checked. */
public interface TravelTripRepository extends JpaRepository<TravelTrip, UUID> {}
