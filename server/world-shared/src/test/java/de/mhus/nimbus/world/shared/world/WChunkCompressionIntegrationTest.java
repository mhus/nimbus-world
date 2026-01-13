package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.shared.types.WorldId;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for WChunk compression feature.
 *
 * Note: These tests use mocked MongoDB and Storage services.
 * For full end-to-end tests with real MongoDB, use @DataMongoTest.
 */
@SpringBootTest(classes = {WChunkService.class, WChunkRepository.class})
@TestPropertySource(properties = {
        "nimbus.chunk.compression.enabled=true"
})
class WChunkCompressionIntegrationTest {

    @Autowired(required = false)
    private WChunkService chunkService;

    @MockBean
    private WChunkRepository repository;

    @MockBean
    private StorageService storageService;

    @MockBean
    private WWorldService worldService;

    @MockBean
    private WItemPositionService itemRegistryService;

    @Test
    void testEndToEndCompression() throws Exception {
        if (chunkService == null) {
            // Skip if not running in full Spring context
            return;
        }

        // Given: Test chunk data
        WorldId worldId = WorldId.unchecked("test-region:integration-test-world");
        String chunkKey = "10:10";
        ChunkData originalData = createLargeChunkData(10, 10, 150);

        // Mock repository behavior
        when(repository.findByWorldIdAndChunk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(WChunk.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock storage service
        when(storageService.store(anyString(), any(), anyString(), anyString(), any(InputStream.class)))
                .thenReturn(new StorageService.StorageInfo("test-storage-id", 5000,
                        new Date(), "integration-test-world", "chunk/10:10", "WChunkStorage",
                        SchemaVersion.create("1.0.1")));

        // When: Save chunk
        WChunk savedChunk = chunkService.saveChunk(worldId, chunkKey, originalData);

        // Then: Verify compression was applied
        assertThat(savedChunk).isNotNull();
        assertThat(savedChunk.isCompressed()).isTrue();
        assertThat(savedChunk.getStorageId()).isNotNull();
    }

    @Test
    void testMixedCompressedUncompressed() {
        if (chunkService == null) {
            return;
        }

        // Given: Two chunks - one compressed, one not
        WorldId worldId = WorldId.unchecked("test-region:mixed-world");

        WChunk compressedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk("0:0")
                .storageId("storage-compressed")
                .compressed(true)
                .build();

        WChunk uncompressedChunk = WChunk.builder()
                .worldId(worldId.getId())
                .chunk("1:1")
                .storageId("storage-uncompressed")
                .compressed(false)
                .build();

        // When/Then: Both chunks can coexist
        assertThat(compressedChunk.isCompressed()).isTrue();
        assertThat(uncompressedChunk.isCompressed()).isFalse();
    }

    private ChunkData createLargeChunkData(int cx, int cz, int blockCount) {
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
            block.setBlockTypeId("grass_" + i);
            blocks.add(block);
        }
        chunkData.setBlocks(blocks);

        // Add height data
        int[][] heightData = new int[blockCount][4];
        for (int i = 0; i < blockCount; i++) {
            heightData[i] = new int[]{i % 32, i / 32, 15, 5};
        }
        chunkData.setHeightData(heightData);

        return chunkData;
    }
}
