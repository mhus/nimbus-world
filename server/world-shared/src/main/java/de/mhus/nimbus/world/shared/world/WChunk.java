package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
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
        @CompoundIndex(name = "world_chunk_idx", def = "{ 'worldId': 1, 'chunk': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WChunk implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /** Chunk Identifier (z.B. Koordinaten serialisiert). */
    @Indexed
    private String chunk;

    @Indexed
    private String storageId;

    @Builder.Default
    private boolean compressed = false;

    /**
     * Server-side metadata for blocks in this chunk.
     * Key: Block coordinate "x,y,z"
     * Value: Server metadata map from BlockMetadata.server
     */
    private Map<String, Map<String, String>> infoServer;

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
