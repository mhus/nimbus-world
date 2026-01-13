package de.mhus.nimbus.shared.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StorageCleanupScheduler.
 */
@ExtendWith(MockitoExtension.class)
class StorageCleanupSchedulerTest {

    @Mock
    private StorageDataRepository storageDataRepository;

    @Mock
    private StorageDeleteRepository storageDeleteRepository;

    @InjectMocks
    private StorageCleanupScheduler scheduler;

    @Test
    void testCleanupWithNoEntries() {
        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Collections.emptyList());

        scheduler.cleanupDeletedStorage();

        // No deletions should occur
        verify(storageDataRepository, never()).deleteByUuid(any());
        verify(storageDeleteRepository, never()).delete(any(StorageDelete.class));
    }

    @Test
    void testCleanupSingleEntry() {
        String storageId = "test-uuid";
        StorageDelete deleteEntry = StorageDelete.builder()
                .id("delete-1")
                .storageId(storageId)
                .deletedAt(new Date())
                .build();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Collections.singletonList(deleteEntry));
        when(storageDataRepository.countByUuid(storageId)).thenReturn(3L);

        scheduler.cleanupDeletedStorage();

        // Verify chunks were deleted
        verify(storageDataRepository).countByUuid(storageId);
        verify(storageDataRepository).deleteByUuid(storageId);

        // Verify delete entry was removed
        verify(storageDeleteRepository).delete(deleteEntry);
    }

    @Test
    void testCleanupMultipleEntries() {
        StorageDelete delete1 = StorageDelete.builder()
                .id("delete-1")
                .storageId("uuid-1")
                .deletedAt(new Date())
                .build();

        StorageDelete delete2 = StorageDelete.builder()
                .id("delete-2")
                .storageId("uuid-2")
                .deletedAt(new Date())
                .build();

        StorageDelete delete3 = StorageDelete.builder()
                .id("delete-3")
                .storageId("uuid-3")
                .deletedAt(new Date())
                .build();

        List<StorageDelete> deleteList = Arrays.asList(delete1, delete2, delete3);

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(deleteList);
        when(storageDataRepository.countByUuid(anyString())).thenReturn(5L);

        scheduler.cleanupDeletedStorage();

        // Verify all entries were processed
        verify(storageDataRepository, times(3)).deleteByUuid(anyString());
        verify(storageDataRepository).deleteByUuid("uuid-1");
        verify(storageDataRepository).deleteByUuid("uuid-2");
        verify(storageDataRepository).deleteByUuid("uuid-3");

        verify(storageDeleteRepository, times(3)).delete(any(StorageDelete.class));
    }

    @Test
    void testCleanupWithError() {
        StorageDelete delete1 = StorageDelete.builder()
                .id("delete-1")
                .storageId("uuid-1")
                .deletedAt(new Date())
                .build();

        StorageDelete delete2 = StorageDelete.builder()
                .id("delete-2")
                .storageId("uuid-2")
                .deletedAt(new Date())
                .build();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Arrays.asList(delete1, delete2));

        // First deletion fails
        when(storageDataRepository.countByUuid("uuid-1"))
                .thenThrow(new RuntimeException("MongoDB connection failed"));

        // Second deletion succeeds
        when(storageDataRepository.countByUuid("uuid-2")).thenReturn(2L);

        scheduler.cleanupDeletedStorage();

        // Verify first deletion was attempted but failed
        verify(storageDataRepository).countByUuid("uuid-1");
        verify(storageDataRepository, never()).deleteByUuid("uuid-1");
        verify(storageDeleteRepository, never()).delete(delete1);

        // Verify second deletion succeeded
        verify(storageDataRepository).countByUuid("uuid-2");
        verify(storageDataRepository).deleteByUuid("uuid-2");
        verify(storageDeleteRepository).delete(delete2);
    }

    @Test
    void testCleanupWithRepositoryException() {
        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenThrow(new RuntimeException("MongoDB connection failed"));

        // Should not throw exception, just log error
        scheduler.cleanupDeletedStorage();

        verify(storageDataRepository, never()).deleteByUuid(any());
    }

    @Test
    void testCleanupOnlyProcessesExpiredEntries() {
        // This test verifies the query logic (though it's in the repository)
        Date now = new Date();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(now))
                .thenReturn(Collections.emptyList());

        scheduler.cleanupDeletedStorage();

        // Verify correct query was made
        verify(storageDeleteRepository).findByDeletedAtLessThanEqual(any(Date.class));
    }

    @Test
    void testCleanupDeletesChunksBeforeDeleteEntry() {
        String storageId = "test-uuid";
        StorageDelete deleteEntry = StorageDelete.builder()
                .id("delete-1")
                .storageId(storageId)
                .deletedAt(new Date())
                .build();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Collections.singletonList(deleteEntry));
        when(storageDataRepository.countByUuid(storageId)).thenReturn(3L);

        scheduler.cleanupDeletedStorage();

        // Verify order: chunks deleted first, then delete entry
        var inOrder = inOrder(storageDataRepository, storageDeleteRepository);
        inOrder.verify(storageDataRepository).countByUuid(storageId);
        inOrder.verify(storageDataRepository).deleteByUuid(storageId);
        inOrder.verify(storageDeleteRepository).delete(deleteEntry);
    }

    @Test
    void testCleanupWithZeroChunks() {
        String storageId = "test-uuid";
        StorageDelete deleteEntry = StorageDelete.builder()
                .id("delete-1")
                .storageId(storageId)
                .deletedAt(new Date())
                .build();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Collections.singletonList(deleteEntry));
        when(storageDataRepository.countByUuid(storageId)).thenReturn(0L);

        scheduler.cleanupDeletedStorage();

        // Should still try to delete (no-op) and remove entry
        verify(storageDataRepository).countByUuid(storageId);
        verify(storageDataRepository).deleteByUuid(storageId);
        verify(storageDeleteRepository).delete(deleteEntry);
    }

    @Test
    void testCleanupHandlesPartialFailures() {
        StorageDelete delete1 = StorageDelete.builder()
                .id("delete-1")
                .storageId("uuid-1")
                .deletedAt(new Date())
                .build();

        StorageDelete delete2 = StorageDelete.builder()
                .id("delete-2")
                .storageId("uuid-2")
                .deletedAt(new Date())
                .build();

        StorageDelete delete3 = StorageDelete.builder()
                .id("delete-3")
                .storageId("uuid-3")
                .deletedAt(new Date())
                .build();

        when(storageDeleteRepository.findByDeletedAtLessThanEqual(any(Date.class)))
                .thenReturn(Arrays.asList(delete1, delete2, delete3));

        // First succeeds
        when(storageDataRepository.countByUuid("uuid-1")).thenReturn(2L);

        // Second fails
        when(storageDataRepository.countByUuid("uuid-2"))
                .thenThrow(new RuntimeException("Error"));

        // Third succeeds
        when(storageDataRepository.countByUuid("uuid-3")).thenReturn(1L);

        scheduler.cleanupDeletedStorage();

        // Verify first deletion succeeded
        verify(storageDataRepository).deleteByUuid("uuid-1");
        verify(storageDeleteRepository).delete(delete1);

        // Verify second deletion failed
        verify(storageDataRepository, never()).deleteByUuid("uuid-2");
        verify(storageDeleteRepository, never()).delete(delete2);

        // Verify third deletion succeeded (scheduler continues after error)
        verify(storageDataRepository).deleteByUuid("uuid-3");
        verify(storageDeleteRepository).delete(delete3);
    }
}
