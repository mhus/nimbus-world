package de.mhus.nimbus.shared.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChunkedInputStream.
 */
@ExtendWith(MockitoExtension.class)
class ChunkedInputStreamTest {

    @Mock
    private StorageDataRepository repository;

    private static final String TEST_UUID = "test-uuid";

    @BeforeEach
    void setUp() {
        // Setup is done in individual tests
    }

    @Test
    void testReadSingleChunk() throws IOException {
        // Setup: Single chunk with "Hello World"
        byte[] data = "Hello World".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        // Read all data
        byte[] result = stream.readAllBytes();
        assertThat(result).isEqualTo(data);

        // EOF
        assertThat(stream.read()).isEqualTo(-1);

        stream.close();
    }

    @Test
    void testReadMultipleChunks() throws IOException {
        // Setup: 3 chunks
        byte[] data1 = "Chunk1".getBytes();
        byte[] data2 = "Chunk2".getBytes();
        byte[] data3 = "Chunk3".getBytes();

        StorageData chunk0 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data1)
                .isFinal(false)
                .createdAt(new Date())
                .build();

        StorageData chunk1 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(1)
                .data(data2)
                .isFinal(false)
                .createdAt(new Date())
                .build();

        StorageData chunk2 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(2)
                .data(data3)
                .isFinal(true)
                .size(data1.length + data2.length + data3.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk0);
        when(repository.findByUuidAndIndex(TEST_UUID, 1)).thenReturn(chunk1);
        when(repository.findByUuidAndIndex(TEST_UUID, 2)).thenReturn(chunk2);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        // Read all data
        byte[] result = stream.readAllBytes();
        String expected = "Chunk1Chunk2Chunk3";
        assertThat(new String(result)).isEqualTo(expected);

        stream.close();
    }

    @Test
    void testReadSingleBytes() throws IOException {
        byte[] data = "ABC".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        assertThat(stream.read()).isEqualTo((int) 'A');
        assertThat(stream.read()).isEqualTo((int) 'B');
        assertThat(stream.read()).isEqualTo((int) 'C');
        assertThat(stream.read()).isEqualTo(-1); // EOF

        stream.close();
    }

    @Test
    void testReadBulk() throws IOException {
        byte[] data = "0123456789".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        byte[] buffer = new byte[5];
        int bytesRead = stream.read(buffer, 0, 5);

        assertThat(bytesRead).isEqualTo(5);
        assertThat(new String(buffer)).isEqualTo("01234");

        stream.close();
    }

    @Test
    void testReadAcrossChunkBoundaries() throws IOException {
        // Setup: 2 chunks with 5 bytes each
        byte[] data1 = "12345".getBytes();
        byte[] data2 = "67890".getBytes();

        StorageData chunk0 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data1)
                .isFinal(false)
                .createdAt(new Date())
                .build();

        StorageData chunk1 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(1)
                .data(data2)
                .isFinal(true)
                .size(10)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk0);
        when(repository.findByUuidAndIndex(TEST_UUID, 1)).thenReturn(chunk1);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        // Read 8 bytes, crossing chunk boundary
        byte[] buffer = new byte[8];
        int bytesRead = stream.read(buffer, 0, 8);

        assertThat(bytesRead).isEqualTo(8);
        assertThat(new String(buffer)).isEqualTo("12345678");

        stream.close();
    }

    @Test
    void testReadEmptyChunk() throws IOException {
        byte[] data = new byte[0];
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(0)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        assertThat(stream.read()).isEqualTo(-1); // EOF immediately

        stream.close();
    }

    @Test
    void testNoChunksFound() {
        when(repository.findByUuidAndIndex(eq(TEST_UUID), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> new ChunkedInputStream(repository, TEST_UUID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No chunks found");
    }

    @Test
    void testMissingChunkInSequence() throws IOException {
        // Setup: Chunk 0 exists, but chunk 1 is missing (not final, so more expected)
        byte[] data = "test".getBytes();
        StorageData chunk0 = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(false)  // Not final, so stream expects more chunks
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk0);
        when(repository.findByUuidAndIndex(TEST_UUID, 1)).thenReturn(null);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        // Read all data from chunk 0 successfully
        byte[] buffer = new byte[data.length];
        int bytesRead = stream.read(buffer);
        assertThat(bytesRead).isEqualTo(data.length);
        assertThat(buffer).isEqualTo(data);

        // Try to read more - chunk 1 is missing, but since it returns null (not exception),
        // stream treats it as EOF
        int nextByte = stream.read();
        assertThat(nextByte).isEqualTo(-1); // EOF

        stream.close();
    }

    @Test
    void testWrongChunkIndex() {
        // Setup: Chunk with wrong index
        byte[] data = "test".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(5) // Expected 0
                .data(data)
                .isFinal(false)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        assertThatThrownBy(() -> new ChunkedInputStream(repository, TEST_UUID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected index 0 but got 5");
    }

    @Test
    void testReadAfterClose() throws IOException {
        byte[] data = "test".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);
        stream.close();

        assertThatThrownBy(() -> stream.read())
                .isInstanceOf(IOException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void testReadNullBuffer() throws IOException {
        byte[] data = "test".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        assertThatThrownBy(() -> stream.read(null, 0, 10))
                .isInstanceOf(NullPointerException.class);

        stream.close();
    }

    @Test
    void testReadInvalidOffsetLength() throws IOException {
        byte[] data = "test".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        byte[] buffer = new byte[10];

        assertThatThrownBy(() -> stream.read(buffer, -1, 5))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThatThrownBy(() -> stream.read(buffer, 0, -1))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThatThrownBy(() -> stream.read(buffer, 0, 20))
                .isInstanceOf(IndexOutOfBoundsException.class);

        stream.close();
    }

    @Test
    void testAvailable() throws IOException {
        byte[] data = "Hello".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);

        assertThat(stream.available()).isEqualTo(5);

        stream.read(); // Read one byte
        assertThat(stream.available()).isEqualTo(4);

        stream.close();
    }

    @Test
    void testDoubleClose() throws IOException {
        byte[] data = "test".getBytes();
        StorageData chunk = StorageData.builder()
                .uuid(TEST_UUID)
                .index(0)
                .data(data)
                .isFinal(true)
                .size(data.length)
                .createdAt(new Date())
                .build();

        when(repository.findByUuidAndIndex(TEST_UUID, 0)).thenReturn(chunk);

        ChunkedInputStream stream = new ChunkedInputStream(repository, TEST_UUID);
        stream.close();
        stream.close(); // Should not throw
    }
}
