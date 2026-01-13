package de.mhus.nimbus.shared.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChunkedOutputStream.
 */
@ExtendWith(MockitoExtension.class)
class ChunkedOutputStreamTest {

    @Mock
    private StorageDataRepository repository;

    private static final String TEST_UUID = "test-uuid";
    private static final String TEST_PATH = "test/path";
    private static final String TEST_WORLD = "test-world";
    private static final String TEST_SCHEMA = "test-schema";
    private static final String TEST_SCHEMA_VERSION = "1.0";
    private static final int CHUNK_SIZE = 1024; // 1KB for testing
    private Date testDate;

    @BeforeEach
    void setUp() {
        testDate = new Date();
    }

    @Test
    void testWriteSingleChunk() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        byte[] data = "Hello World".getBytes();
        stream.write(data);
        stream.close();

        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(1)).save(captor.capture());

        StorageData saved = captor.getValue();
        assertThat(saved.getUuid()).isEqualTo(TEST_UUID);
        assertThat(saved.getPath()).isEqualTo(TEST_PATH);
        assertThat(saved.getIndex()).isEqualTo(0);
        assertThat(saved.getData()).isEqualTo(data);
        assertThat(saved.isFinal()).isTrue();
        assertThat(saved.getSize()).isEqualTo(data.length);
        assertThat(saved.getCreatedAt()).isEqualTo(testDate);
    }

    @Test
    void testWriteMultipleChunks() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        // Write 2.5 chunks worth of data
        byte[] chunk1 = new byte[CHUNK_SIZE];
        byte[] chunk2 = new byte[CHUNK_SIZE];
        byte[] chunk3 = new byte[CHUNK_SIZE / 2];

        for (int i = 0; i < CHUNK_SIZE; i++) {
            chunk1[i] = (byte) 'A';
            chunk2[i] = (byte) 'B';
        }
        for (int i = 0; i < CHUNK_SIZE / 2; i++) {
            chunk3[i] = (byte) 'C';
        }

        stream.write(chunk1);
        stream.write(chunk2);
        stream.write(chunk3);
        stream.close();

        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(3)).save(captor.capture());

        List<StorageData> chunks = captor.getAllValues();
        assertThat(chunks).hasSize(3);

        // First chunk
        assertThat(chunks.get(0).getIndex()).isEqualTo(0);
        assertThat(chunks.get(0).getData()).hasSize(CHUNK_SIZE);
        assertThat(chunks.get(0).isFinal()).isFalse();
        assertThat(chunks.get(0).getSize()).isEqualTo(0);

        // Second chunk
        assertThat(chunks.get(1).getIndex()).isEqualTo(1);
        assertThat(chunks.get(1).getData()).hasSize(CHUNK_SIZE);
        assertThat(chunks.get(1).isFinal()).isFalse();
        assertThat(chunks.get(1).getSize()).isEqualTo(0);

        // Final chunk
        assertThat(chunks.get(2).getIndex()).isEqualTo(2);
        assertThat(chunks.get(2).getData()).hasSize(CHUNK_SIZE / 2);
        assertThat(chunks.get(2).isFinal()).isTrue();
        assertThat(chunks.get(2).getSize()).isEqualTo(CHUNK_SIZE * 2 + CHUNK_SIZE / 2);
    }

    @Test
    void testWriteSingleBytes() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        // Write bytes one at a time
        String text = "Test";
        for (byte b : text.getBytes()) {
            stream.write(b);
        }
        stream.close();

        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(1)).save(captor.capture());

        StorageData saved = captor.getValue();
        assertThat(saved.getData()).isEqualTo(text.getBytes());
        assertThat(saved.isFinal()).isTrue();
        assertThat(saved.getSize()).isEqualTo(text.length());
    }

    @Test
    void testWriteExactlyOneChunk() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        byte[] data = new byte[CHUNK_SIZE];
        for (int i = 0; i < CHUNK_SIZE; i++) {
            data[i] = (byte) 'X';
        }

        stream.write(data);
        stream.close();

        // When buffer is exactly full, chunk is saved with isFinal=false,
        // then on close() it's updated to isFinal=true
        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(2)).save(captor.capture());

        List<StorageData> savedChunks = captor.getAllValues();
        assertThat(savedChunks).hasSize(2);

        // First save: isFinal=false
        assertThat(savedChunks.get(0).getData()).hasSize(CHUNK_SIZE);
        assertThat(savedChunks.get(0).isFinal()).isFalse();

        // Second save: same chunk updated with isFinal=true
        assertThat(savedChunks.get(1).getData()).hasSize(CHUNK_SIZE);
        assertThat(savedChunks.get(1).isFinal()).isTrue();
        assertThat(savedChunks.get(1).getSize()).isEqualTo(CHUNK_SIZE);
    }

    @Test
    void testWriteEmptyStream() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        stream.close();

        // Should save one empty chunk marked as final
        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(1)).save(captor.capture());

        StorageData saved = captor.getValue();
        assertThat(saved.getData()).isEmpty();
        assertThat(saved.isFinal()).isTrue();
        assertThat(saved.getSize()).isEqualTo(0);
    }

    @Test
    void testWriteWithOffset() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        byte[] data = "0123456789".getBytes();
        stream.write(data, 2, 5); // Write "23456"
        stream.close();

        ArgumentCaptor<StorageData> captor = ArgumentCaptor.forClass(StorageData.class);
        verify(repository, times(1)).save(captor.capture());

        StorageData saved = captor.getValue();
        assertThat(new String(saved.getData())).isEqualTo("23456");
        assertThat(saved.getSize()).isEqualTo(5);
    }

    @Test
    void testWriteAfterClose() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        stream.close();

        assertThatThrownBy(() -> stream.write(42))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void testWriteNullArray() {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        assertThatThrownBy(() -> stream.write(null, 0, 10))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("null");
    }

    @Test
    void testWriteInvalidOffsetLength() {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        byte[] data = new byte[10];

        assertThatThrownBy(() -> stream.write(data, -1, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThatThrownBy(() -> stream.write(data, 0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThatThrownBy(() -> stream.write(data, 0, 20))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void testRepositoryException() {
        when(repository.save(any(StorageData.class)))
                .thenThrow(new RuntimeException("MongoDB error"));

        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        byte[] data = "test".getBytes();

        assertThatThrownBy(() -> {
            stream.write(data);
            stream.close();
        }).isInstanceOf(IOException.class)
          .hasMessageContaining("Failed to save chunk");
    }

    @Test
    void testGetTotalBytesWritten() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        assertThat(stream.getTotalBytesWritten()).isEqualTo(0);

        stream.write("Hello".getBytes());
        assertThat(stream.getTotalBytesWritten()).isEqualTo(5);

        stream.write(" World".getBytes());
        assertThat(stream.getTotalBytesWritten()).isEqualTo(11);

        stream.close();
    }

    @Test
    void testDoubleClose() throws IOException {
        ChunkedOutputStream stream = new ChunkedOutputStream(
                repository, TEST_UUID, TEST_SCHEMA, TEST_SCHEMA_VERSION, TEST_WORLD, TEST_PATH, CHUNK_SIZE, testDate);

        stream.write("test".getBytes());
        stream.close();
        stream.close(); // Should not throw or save again

        verify(repository, times(1)).save(any(StorageData.class));
    }
}
