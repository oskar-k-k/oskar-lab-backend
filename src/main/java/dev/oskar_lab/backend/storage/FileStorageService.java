package dev.oskar_lab.backend.storage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.util.UUID;

/** Central file transport for every app. Domain services must authorize before calling it. */
@Service
public class FileStorageService {
    private final OrbitStorageClient orbit;
    private final FileDeletionRepository deletions;
    private final TransactionTemplate independent;

    public FileStorageService(OrbitStorageClient orbit, FileDeletionRepository deletions, PlatformTransactionManager manager) {
        this.orbit = orbit; this.deletions = deletions;
        independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Uploads to the app's project; a rolled-back database write queues cleanup of the uploaded object. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OrbitStorageClient.StoredFile upload(String project, String filename, String type, byte[] bytes) {
        var stored = orbit.upload(project, filename, type, bytes);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) independent.executeWithoutResult(transaction -> queue(project, stored.id()));
            }
        });
        return stored;
    }

    /** Returns verified bytes only after the caller has checked resource permissions. */
    public byte[] download(String project, UUID fileId, long size, String sha256) { return orbit.download(project, fileId, size, sha256); }

    /** Queues cloud deletion in the caller's transaction; rollback cannot delete a still-visible document. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleDelete(String project, UUID fileId) { queue(project, fileId); }

    private void queue(String project, UUID fileId) {
        if (deletions.existsByProjectAndOrbitFileId(project, fileId)) return;
        var pending = new PendingFileDeletion(); pending.project = project; pending.orbitFileId = fileId;
        deletions.saveAndFlush(pending);
    }
}
