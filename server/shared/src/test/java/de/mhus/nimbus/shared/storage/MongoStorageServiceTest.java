package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.types.SchemaVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MongoStorageService.
 */
@ExtendWith(MockitoExtension.class)
class MongoStorageServiceTest {

    @Mock
    private StorageDataRepository storageDataRepository;

    @Mock
    private StorageDeleteRepository storageDeleteRepository;

    @InjectMocks
    private MongoStorageService service;

    private static final int CHUNK_SIZE = 1024; // 1KB for testing

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chunkSize", CHUNK_SIZE);
    }

    @Test
    void testStoreSmallFile() {
        String testPath = "test/file.txt";
        byte[] testData = "Hello World".getBytes();
        InputStream stream = new ByteArrayInputStream(testData);

        StorageService.StorageInfo result = service.store("test", SchemaVersion.create("1.0"), "w1", testPath, stream);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotBlank(); // UUID generated
        assertThat(result.size()).isEqualTo(testData.length);
        assertThat(result.path()).isEqualTo(testPath);
        assertThat(result.createdAt()).isNotNull();

        // Verify one chunk was saved (small file)
        verify(storageDataRepository, times(1)).save(any(StorageData.class));

        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(storageDataRepository).save(captor.capture());

        StorageData saved = captor.getValue();
        assertThat(saved.getUuid()).isEqualTo(result.id());
        assertThat(saved.getPath()).isEqualTo(testPath);
        assertThat(saved.getIndex()).isEqualTo(0);
        assertThat(saved.isFinal()).isTrue();
        assertThat(saved.getSize()).isEqualTo(testData.length);
    }

    @Test
    void testStoreLargeFile() {
        String testPath = "test/large.bin";
        byte[] testData = new byte[CHUNK_SIZE * 2 + 100]; // 2.1 chunks
        for (int i = 0; i < testData.length; i++) {
            testData[i] = (byte) (i % 256);
        }
        InputStream stream = new ByteArrayInputStream(testData);

        StorageService.StorageInfo result = service.store("test", SchemaVersion.create("1.0"), "w1", testPath, stream);

        assertThat(result).isNotNull();
        assertThat(result.size()).isEqualTo(testData.length);

        // Verify 3 chunks were saved
        verify(storageDataRepository, times(3)).save(any(StorageData.class));
    }

    @Test
    void testStoreNullStream() {
        String testPath = "test/file.txt";

        StorageService.StorageInfo result = service.store("test", SchemaVersion.create("1.0"), "w1", testPath, null);

        assertThat(result).isNull();
        verify(storageDataRepository, never()).save(any(StorageData.class));
    }

    @Test
    void testLoad() {
        String storageId = "test-uuid";

        // Mock repository to return chunks
        byte[] data = "Test Data".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(storageId)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .schema("test")
                .schemaVersion("1.0")
                .build();

        when(storageDataRepository.findByUuidAndIndex(storageId, 0)).thenReturn(chunk);

        InputStream result = service.load(storageId);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(ChunkedInputStream.class);
    }

    @Test
    void testLoadNullStorageId() {
        InputStream result = service.load(null);

        assertThat(result).isNull();
        verify(storageDataRepository, never()).findByUuidAndIndex(any(), anyInt());
    }

    @Test
    void testLoadEmptyStorageId() {
        InputStream result = service.load("");

        assertThat(result).isNull();
        verify(storageDataRepository, never()).findByUuidAndIndex(any(), anyInt());
    }

    @Test
    void testLoadNonExistent() {
        String storageId = "non-existent-uuid";

        when(storageDataRepository.findByUuidAndIndex(storageId, 0)).thenReturn(null);

        InputStream result = service.load(storageId);

        assertThat(result).isNull();
    }

    @Test
    void testDelete() {
        String storageId = "test-uuid";

        service.delete(storageId);

        // Verify StorageDelete entry was created
        ArgumentCaptor<StorageDelete> captor = ArgumentCaptor.forClass(StorageDelete.class);
        verify(storageDeleteRepository).save(captor.capture());

        StorageDelete deleteEntry = captor.getValue();
        assertThat(deleteEntry.getStorageId()).isEqualTo(storageId);
        assertThat(deleteEntry.getDeletedAt()).isAfter(new Date());

        // Should be scheduled for ~5 minutes in future
        long delay = deleteEntry.getDeletedAt().getTime() - System.currentTimeMillis();
        assertThat(delay).isBetween(4 * 60 * 1000L, 6 * 60 * 1000L); // 4-6 minutes tolerance
    }

    @Test
    void testDeleteNullStorageId() {
        service.delete(null);

        verify(storageDeleteRepository, never()).save(any(StorageDelete.class));
    }

    @Test
    void testDeleteEmptyStorageId() {
        service.delete("");

        verify(storageDeleteRepository, never()).save(any(StorageDelete.class));
    }

    @Test
    void testUpdate() {
        String oldStorageId = "old-uuid";
        String testPath = "test/updated.txt";
        byte[] newData = "Updated Data".getBytes();
        InputStream stream = new ByteArrayInputStream(newData);

        // Mock old final chunk
        StorageData oldChunk = StorageData.builder()
                .uuid(oldStorageId)
                .path(testPath)
                .worldId("w1")
                .index(0)
                .isFinal(true)
                .size(100)
                .createdAt(new Date())
                .schema("test")
                .schemaVersion("1.0")
                .build();

        when(storageDataRepository.findByUuidAndIsFinalTrue(oldStorageId)).thenReturn(oldChunk);

        StorageService.StorageInfo result = service.update(null, null, oldStorageId, stream);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotEqualTo(oldStorageId); // New UUID generated
        assertThat(result.path()).isEqualTo(testPath);
        assertThat(result.size()).isEqualTo(newData.length);

        // Verify new version was stored
        verify(storageDataRepository, atLeastOnce()).save(any(StorageData.class));

        // Verify old version was scheduled for deletion
        verify(storageDeleteRepository).save(any(StorageDelete.class));
    }

    @Test
    void testUpdateNullStorageId() {
        InputStream stream = new ByteArrayInputStream("test".getBytes());

        StorageService.StorageInfo result = service.update(null, null, null, stream);

        assertThat(result).isNull();
        verify(storageDataRepository, never()).save(any(StorageData.class));
    }

    @Test
    void testUpdateNullStream() {
        String storageId = "test-uuid";

        StorageService.StorageInfo result = service.update(null, null, storageId, null);

        assertThat(result).isNull();
        verify(storageDataRepository, never()).save(any(StorageData.class));
    }

    @Test
    void testInfo() {
        String storageId = "test-uuid";
        String testPath = "test/file.txt";
        long testSize = 12345;
        Date testDate = new Date();

        StorageData finalChunk = StorageData.builder()
                .uuid(storageId)
                .path(testPath)
                .worldId("w1")
                .index(5)
                .isFinal(true)
                .size(testSize)
                .createdAt(testDate)
                .schema("test")
                .schemaVersion("1.0")
                .build();

        when(storageDataRepository.findByUuidAndIsFinalTrue(storageId)).thenReturn(finalChunk);

        StorageService.StorageInfo result = service.info(storageId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(storageId);
        assertThat(result.path()).isEqualTo(testPath);
        assertThat(result.size()).isEqualTo(testSize);
        assertThat(result.createdAt()).isEqualTo(testDate);
    }

    @Test
    void testInfoNullStorageId() {
        StorageService.StorageInfo result = service.info(null);

        assertThat(result).isNull();
        verify(storageDataRepository, never()).findByUuidAndIsFinalTrue(any());
    }

    @Test
    void testInfoEmptyStorageId() {
        StorageService.StorageInfo result = service.info("");

        assertThat(result).isNull();
        verify(storageDataRepository, never()).findByUuidAndIsFinalTrue(any());
    }

    @Test
    void testInfoNonExistent() {
        String storageId = "non-existent-uuid";

        when(storageDataRepository.findByUuidAndIsFinalTrue(storageId)).thenReturn(null);

        StorageService.StorageInfo result = service.info(storageId);

        assertThat(result).isNull();
    }

    @Test
    void testStoreWithRepositoryException() {
        String testPath = "test/file.txt";
        byte[] testData = "test".getBytes();
        InputStream stream = new ByteArrayInputStream(testData);

        when(storageDataRepository.save(any(StorageData.class)))
                .thenThrow(new RuntimeException("MongoDB connection failed"));

        assertThatThrownBy(() -> service.store("test", SchemaVersion.create("1.0"), "w1", testPath, stream))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to store file");
    }

    @Test
    void testUpdateWithNonExistentOldVersion() {
        String oldStorageId = "non-existent-uuid";
        InputStream stream = new ByteArrayInputStream("new data".getBytes());

        when(storageDataRepository.findByUuidAndIsFinalTrue(oldStorageId)).thenReturn(null);

        assertThatThrownBy(() -> service.update(null, null, oldStorageId, stream))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");

        // Verify no new version was stored due to exception
        verify(storageDataRepository, never()).save(any(StorageData.class));

        // Verify no deletion was scheduled due to exception
        verify(storageDeleteRepository, never()).save(any(StorageDelete.class));
    }
}
