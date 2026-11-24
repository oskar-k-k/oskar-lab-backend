package dev.oskar_lab.backend;

import dev.oskar_lab.backend.storage.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileLifecycleTests {
    @Autowired FileStorageService storage;
    @Autowired FileDeletionRepository deletions;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean OrbitStorageClient orbit;

    @Test void queuesRolledBackUploadsAndRetriesFailedCloudDeletion() {
        UUID id = UUID.randomUUID();
        when(orbit.upload(anyString(), anyString(), anyString(), any(byte[].class)))
            .thenReturn(new OrbitStorageClient.StoredFile(id, "application/pdf", 8));
        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            storage.upload("thailand", "example.pdf", "application/pdf", new byte[8]);
            transaction.setRollbackOnly();
        });
        assertTrue(deletions.existsByProjectAndOrbitFileId("thailand", id));
        doThrow(new StorageFailure(503, "storageUnavailable")).when(orbit).delete("thailand", id);
        var cleanup = new FileCleanup(deletions, orbit);
        cleanup.reconcile();
        assertTrue(deletions.existsByProjectAndOrbitFileId("thailand", id));
        doNothing().when(orbit).delete("thailand", id);
        cleanup.reconcile();
        assertFalse(deletions.existsByProjectAndOrbitFileId("thailand", id));
    }

    @Test void neverDeletesCloudFilesForRolledBackDomainDeletions() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(transaction -> {
            storage.scheduleDelete("thailand", id);
            transaction.setRollbackOnly();
        });
        assertFalse(deletions.existsByProjectAndOrbitFileId("thailand", id));
        verify(orbit, never()).delete("thailand", id);
    }
}
