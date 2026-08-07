package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import de.mhus.nimbus.shared.types.StorageEntity;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Speicherung eines Welt-Chunks.
 * Uniqueness: (worldId, chunk).
 * Inline wird ein JSON String des ChunkData gespeichert (content). Ist der JSON zu groß,
 * wird er extern über storageId referenziert und content bleibt null.
 */
@Document(collection = "w_chunks")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_chunk_idx", def = "{ 'worldId': 1, 'chunk': 1 }"),
        @CompoundIndex(name = "world_chunk_epoches_idx", def = "{ 'worldId': 1, 'chunk': 1, 'epoches': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WChunk implements Identifiable, EpochEntity, StorageEntity {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /** Chunk Identifier 'cx:cz'. */
    @Indexed
    private String chunk;

    /** Hex Identifier 'q;r' */
    private String hex;

    private String storageId;

    @Builder.Default
    private boolean compressed = false;

    private int blockCount;

    private int chunkSize;

    /**
     * Server-side metadata for blocks in this chunk.
     * Key: Block coordinate "x,y,z"
     * Value: Server metadata map from BlockMetadata.server
     */
    private Map<String, Map<String, String>> infoServer;

    /**
     * Epoch assignment for this chunk version.
     * This chunk data is valid for all listed epochs.
     * Multiple WChunk documents for the same worldId+chunk may exist with different epoches.
     */
    @Builder.Default
    private List<Integer> epoches = List.of();

    private Instant createdAt;
    private Instant updatedAt;

    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void touchUpdate() { updatedAt = Instant.now(); }

    /**
     * Get server metadata for a specific block position.
     *
     * @param x Block x coordinate
     * @param y Block y coordinate
     * @param z Block z coordinate
     * @return Server metadata map for the block, or null if not found
     */
    public Map<String, String> getServerInfoForBlock(int x, int y, int z) {
        if (infoServer == null) {
            return null;
        }

        String coordinate = x + "," + y + "," + z;
        return infoServer.get(coordinate);
    }
}
