package dev.oskar_lab.backend.travel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** Loads packing aggregates by their authorized parent trip. */
public interface TravelPackingRepository extends JpaRepository<TravelPackingList, UUID> {}
