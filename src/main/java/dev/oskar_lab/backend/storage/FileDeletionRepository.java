package dev.oskar_lab.backend.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Bounds cleanup work and keeps failed cloud deletions available for the next retry. */
public interface FileDeletionRepository extends JpaRepository<PendingFileDeletion, UUID> {
    List<PendingFileDeletion> findTop20ByOrderByIdAsc();
    boolean existsByProjectAndOrbitFileId(String project, UUID orbitFileId);
}
