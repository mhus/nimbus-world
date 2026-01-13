package de.mhus.nimbus.world.shared.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.shared.types.WorldId;

import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WChunkServiceCompressionTest {

    @Mock
    private WChunkRepository repository;

    @Mock
    private StorageService storageService;

    @Mock
    private WWorldService worldService;

    @Mock
    private WItemPositionService itemRegistryService;

    @Spy
    @InjectMocks
    private WChunkService chunkService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Set compression enabled by default
        ReflectionTestUtils.setField(chunkService, "compressionEnabled", true);
    }

    @Test
    void testSaveAndLoadCompressedChunk() throws Exception {
        // Given: ChunkData with multiple blocks
        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "0:0";
        ChunkData chunkData = createTestChunkData(0, 0, 100);

        WChunk savedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-123")
                .compressed(true)
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedChunk));
        when(repository.save(any(WChunk.class))).thenReturn(savedChunk);

        // Mock storage service - capture compressed data
        ByteArrayOutputStream capturedStream = new ByteArrayOutputStream();
        when(storageService.store(anyString(), any(), anyString(), anyString(), any(InputStream.class)))
                .thenAnswer(invocation -> {
                    InputStream inputStream = invocation.getArgument(4);
                    inputStream.transferTo(capturedStream);
                    return new StorageService.StorageInfo("storage-123", capturedStream.size(),
                            new Date(), "test-world", "chunk/0:0", "WChunkStorage",
                            SchemaVersion.create("1.0.1"));
                });

        // When: Save chunk
        WChunk result = chunkService.saveChunk(worldId, chunkKey, chunkData);

        // Then: Verify chunk is marked as compressed
        assertThat(result.isCompressed()).isTrue();

        // Verify data was compressed (should be smaller than JSON)
        String originalJson = objectMapper.writeValueAsString(chunkData);
        assertThat(capturedStream.size()).isLessThan(originalJson.length());

        // Verify we can decompress it back
        byte[] compressedData = capturedStream.toByteArray();
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressedData))) {
            ChunkData decompressed = objectMapper.readValue(gzipIn, ChunkData.class);
            assertThat(decompressed.getCx()).isEqualTo(chunkData.getCx());
            assertThat(decompressed.getCz()).isEqualTo(chunkData.getCz());
            assertThat(decompressed.getBlocks()).hasSize(chunkData.getBlocks().size());
        }
    }

    @Test
    void testSaveAndLoadUncompressedChunk() throws Exception {
        // Given: Compression disabled
        ReflectionTestUtils.setField(chunkService, "compressionEnabled", false);

        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "1:1";
        ChunkData chunkData = createTestChunkData(1, 1, 50);

        WChunk savedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-456")
                .compressed(false)
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedChunk));
        when(repository.save(any(WChunk.class))).thenReturn(savedChunk);

        // Mock storage service
        ByteArrayOutputStream capturedStream = new ByteArrayOutputStream();
        when(storageService.store(anyString(), any(), anyString(), anyString(), any(InputStream.class)))
                .thenAnswer(invocation -> {
                    InputStream inputStream = invocation.getArgument(4);
                    inputStream.transferTo(capturedStream);
                    return new StorageService.StorageInfo("storage-456", capturedStream.size(),
                            new Date(), "test-world", "chunk/1:1", "WChunkStorage",
                            SchemaVersion.create("1.0.1"));
                });

        // When: Save chunk
        WChunk result = chunkService.saveChunk(worldId, chunkKey, chunkData);

        // Then: Verify chunk is NOT compressed
        assertThat(result.isCompressed()).isFalse();

        // Verify data is plain JSON (not compressed)
        String originalJson = objectMapper.writeValueAsString(chunkData);
        assertThat(capturedStream.size()).isEqualTo(originalJson.getBytes("UTF-8").length);
    }

    @Test
    void testCompressionFlag() {
        // Given: Chunk data
        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "2:2";
        ChunkData chunkData = createTestChunkData(2, 2, 10);

        WChunk savedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-789")
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.empty());
        when(repository.save(any(WChunk.class))).thenAnswer(invocation -> {
            WChunk chunk = invocation.getArgument(0);
            assertThat(chunk.isCompressed()).isTrue();  // Should be set during save
            return chunk;
        });

        // Mock storage service
        when(storageService.store(anyString(), any(), anyString(), anyString(), any(InputStream.class)))
                .thenReturn(new StorageService.StorageInfo("storage-789", 1000,
                        new Date(), "test-world", "chunk/2:2", "WChunkStorage",
                        SchemaVersion.create("1.0.1")));

        // When: Save chunk
        chunkService.saveChunk(worldId, chunkKey, chunkData);

        // Then: Verify save was called (assertion in mock answer)
        verify(repository, times(1)).save(any(WChunk.class));
    }

    @Test
    void testBackwardCompatibility() throws Exception {
        // Given: Old chunk without compression (compressed=false)
        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "3:3";
        ChunkData chunkData = createTestChunkData(3, 3, 20);

        WChunk oldChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-old")
                .compressed(false)  // Old chunk
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.of(oldChunk));

        // Mock storage service - return uncompressed JSON
        String json = objectMapper.writeValueAsString(chunkData);
        ByteArrayInputStream jsonStream = new ByteArrayInputStream(json.getBytes("UTF-8"));
        when(storageService.load(eq("storage-old")))
                .thenReturn(jsonStream);

        // When: Load chunk via getStream
        InputStream stream = chunkService.getStream(worldId, chunkKey);

        // Then: Should get uncompressed stream (no GZIPInputStream wrapper)
        ChunkData loaded = objectMapper.readValue(stream, ChunkData.class);
        assertThat(loaded.getCx()).isEqualTo(chunkData.getCx());
        assertThat(loaded.getCz()).isEqualTo(chunkData.getCz());
        assertThat(loaded.getBlocks()).hasSize(chunkData.getBlocks().size());
    }

    @Test
    void testCompressionRatio() throws Exception {
        // Given: Large chunk with many blocks
        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "4:4";
        ChunkData chunkData = createTestChunkData(4, 4, 200);

        WChunk savedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-big")
                .compressed(true)
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.empty());
        when(repository.save(any(WChunk.class))).thenReturn(savedChunk);

        // Mock storage service - capture data
        ByteArrayOutputStream capturedStream = new ByteArrayOutputStream();
        when(storageService.store(anyString(), any(), anyString(), anyString(), any(InputStream.class)))
                .thenAnswer(invocation -> {
                    InputStream inputStream = invocation.getArgument(4);
                    inputStream.transferTo(capturedStream);
                    return new StorageService.StorageInfo("storage-big", capturedStream.size(),
                            new Date(), "test-world", "chunk/4:4", "WChunkStorage",
                            SchemaVersion.create("1.0.1"));
                });

        // When: Save chunk
        chunkService.saveChunk(worldId, chunkKey, chunkData);

        // Then: Verify compression ratio (expect >50% reduction)
        String originalJson = objectMapper.writeValueAsString(chunkData);
        int originalSize = originalJson.getBytes("UTF-8").length;
        int compressedSize = capturedStream.size();

        double compressionRatio = (double) compressedSize / originalSize;
        assertThat(compressionRatio).isLessThan(0.5);  // At least 50% reduction
    }

    @Test
    void testGetCompressedStream() throws Exception {
        // Given: Compressed chunk in storage
        WorldId worldId = WorldId.unchecked("test-region:test-world");
        String chunkKey = "5:5";
        ChunkData chunkData = createTestChunkData(5, 5, 30);

        // Compress data manually
        String json = objectMapper.writeValueAsString(chunkData);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(json.getBytes("UTF-8"));
        }

        WChunk chunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk(chunkKey)
                .storageId("storage-compressed")
                .compressed(true)
                .build();

        // Mock repository
        when(repository.findByWorldIdAndChunk(eq(worldId.getId()), eq(chunkKey)))
                .thenReturn(Optional.of(chunk));

        // Mock storage service - return compressed stream
        when(storageService.load(eq("storage-compressed")))
                .thenReturn(new ByteArrayInputStream(compressed.toByteArray()));

        // When: Get compressed stream (no decompression)
        InputStream rawStream = chunkService.getCompressedStream(worldId, chunkKey);

        // Then: Should get raw compressed data
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        rawStream.transferTo(output);

        // Manually decompress to verify it's valid compressed data
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            ChunkData decompressed = objectMapper.readValue(gzipIn, ChunkData.class);
            assertThat(decompressed.getCx()).isEqualTo(5);
            assertThat(decompressed.getCz()).isEqualTo(5);
        }
    }

    private ChunkData createTestChunkData(int cx, int cz, int blockCount) {
        ChunkData chunkData = new ChunkData();
        chunkData.setCx(cx);
        chunkData.setCz(cz);
        chunkData.setSize((byte) 32);

        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            Block block = new Block();
            Vector3Int position = new Vector3Int();
            position.setX(cx * 32 + (i % 32));
            position.setY(i / 32);
            position.setZ(cz * 32 + (i / 32));
            block.setPosition(position);
            block.setBlockTypeId("stone");
            blocks.add(block);
        }
        chunkData.setBlocks(blocks);

        // Add height data
        int[][] heightData = new int[blockCount][4];
        for (int i = 0; i < blockCount; i++) {
            heightData[i] = new int[]{i % 32, i / 32, 10, 0};
        }
        chunkData.setHeightData(heightData);

        return chunkData;
    }
}
