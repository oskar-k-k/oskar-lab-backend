package dev.oskar_lab.backend.travel;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/** Loads document bytes only for an explicitly authorized download. */
public interface TravelAttachmentContentRepository extends JpaRepository<TravelAttachmentContent, UUID> {}
