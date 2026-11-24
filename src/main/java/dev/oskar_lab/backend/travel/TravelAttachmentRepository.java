package dev.oskar_lab.backend.travel;

import org.springframework.data.jpa.repository.*;
import java.util.*;

/** Attachment IDs are resolved only inside an already authorized entry. */
public interface TravelAttachmentRepository extends JpaRepository<TravelAttachment, UUID> {
    Optional<TravelAttachment> findByIdAndEntryId(UUID id, UUID entryId);
    List<TravelAttachment> findByEntryIdOrderByIdAsc(UUID entryId);
    @Query("select count(a) from TravelAttachment a where a.entryId in (select e.id from TravelEntry e where e.tripId = :trip)")
    long countForTrip(UUID trip);
}
