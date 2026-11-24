package dev.oskar_lab.backend.access;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import java.util.*;

/** Queries visible resources without listing other accounts or their invitations. */
public interface AccessResourceRepository extends JpaRepository<AccessResource, UUID> {
    Optional<AccessResource> findByIdAndResourceType(UUID id, String resourceType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessResource r where r.id = :id and r.resourceType = :type")
    Optional<AccessResource> lock(UUID id, String type);

    @Query("""
        select r from AccessResource r where r.resourceType = :type and
        (r.ownerId = :user or exists (select v.id from ResourceViewer v
            where v.resourceId = r.id and v.email = :email)) order by r.createdAt desc, r.id
        """)
    List<AccessResource> visible(String type, UUID user, String email, Pageable page);
}
