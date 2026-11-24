package dev.oskar_lab.backend.access;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

/** Stores private sharing lists independently of product data. */
public interface ResourceViewerRepository extends JpaRepository<ResourceViewer, UUID> {
    boolean existsByResourceIdAndEmail(UUID resourceId, String email);
    List<ResourceViewer> findByResourceIdOrderByCreatedAtAscIdAsc(UUID resourceId);
    Optional<ResourceViewer> findByIdAndResourceId(UUID id, UUID resourceId);
    long countByResourceId(UUID resourceId);
    void deleteByResourceId(UUID resourceId);
}
