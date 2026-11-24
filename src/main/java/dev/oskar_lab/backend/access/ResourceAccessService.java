package dev.oskar_lab.backend.access;

import dev.oskar_lab.backend.auth.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Reusable owner/viewer authorization. Products remain responsible for their own domain rules. */
@Service
@Transactional
public class ResourceAccessService {
    private final AccessResourceRepository resources;
    private final ResourceViewerRepository viewers;
    private final AccountRepository accounts;

    public ResourceAccessService(AccessResourceRepository resources, ResourceViewerRepository viewers, AccountRepository accounts) {
        this.resources = resources;
        this.viewers = viewers;
        this.accounts = accounts;
    }

    /** Requires a complete platform account; never accepts an email as caller identity. */
    @Transactional(readOnly = true)
    public Account requireAccount(UUID user) {
        Account account = accounts.findById(user).orElseThrow(() -> new AuthFailure(401, "unauthorized"));
        if (account.username == null || account.termsAcceptedAt == null) throw new AuthFailure(403, "forbidden");
        return account;
    }

    /** Creates a private resource owned by the authenticated account. */
    public AccessResource create(UUID user, String type) {
        requireAccount(user);
        AccessResource resource = new AccessResource();
        resource.ownerId = user;
        resource.resourceType = type;
        return resources.saveAndFlush(resource);
    }

    /** Returns owned and verified-email-shared resources, in bounded pages. */
    @Transactional(readOnly = true)
    public List<AccessResource> visible(UUID user, String type, int page) {
        if (page < 0 || page > 10000) throw new AuthFailure(400, "invalidPage");
        Account account = requireAccount(user);
        return resources.visible(type, user, verifiedEmail(account), PageRequest.of(page, 20));
    }

    /** Denies unknown and inaccessible IDs identically before loading private product data. */
    @Transactional(readOnly = true)
    public AccessResource requireRead(UUID user, String type, UUID id) {
        Account account = requireAccount(user);
        AccessResource resource = resources.findByIdAndResourceType(id, type).orElseThrow(() -> new AuthFailure(404, "notFound"));
        if (!resource.ownerId.equals(user) && !viewers.existsByResourceIdAndEmail(id, verifiedEmail(account)))
            throw new AuthFailure(404, "notFound");
        return resource;
    }

    /** Serializes owner mutations, including grant/revoke and product deletions. */
    public AccessResource requireOwner(UUID user, String type, UUID id) {
        AccessResource resource = requireRead(user, type, id);
        if (!resource.ownerId.equals(user)) throw new AuthFailure(403, "forbidden");
        return resources.lock(id, type).orElseThrow(() -> new AuthFailure(404, "notFound"));
    }

    /** Lists sharing addresses only to the resource owner. */
    public List<ResourceViewer> viewers(UUID user, String type, UUID id) {
        requireOwner(user, type, id);
        return viewers.findByResourceIdOrderByCreatedAtAscIdAsc(id);
    }

    /** Adds a read-only invitation without checking or revealing whether an account exists. */
    public ResourceViewer invite(UUID user, String type, UUID id, String input) {
        requireOwner(user, type, id);
        String email = input.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new AuthFailure(400, "invalidEmail");
        if (viewers.existsByResourceIdAndEmail(id, email)) throw new AuthFailure(409, "conflict");
        if (viewers.countByResourceId(id) >= 100) throw new AuthFailure(409, "viewerLimit");
        ResourceViewer viewer = new ResourceViewer();
        viewer.resourceId = id;
        viewer.email = email;
        return viewers.saveAndFlush(viewer);
    }

    /** Revokes future reads immediately; does not send email or expose a public invitation URL. */
    public void revoke(UUID user, String type, UUID id, UUID viewerId) {
        requireOwner(user, type, id);
        viewers.delete(viewers.findByIdAndResourceId(viewerId, id).orElseThrow(() -> new AuthFailure(404, "notFound")));
    }

    /** Removes the access boundary after its product data has been deleted in the same transaction. */
    public void delete(UUID user, String type, UUID id) {
        AccessResource resource = requireOwner(user, type, id);
        viewers.deleteByResourceId(id);
        resources.delete(resource);
    }

    private String verifiedEmail(Account account) {
        return account.email.equals(account.verifiedEmail) ? account.verifiedEmail : "";
    }
}
