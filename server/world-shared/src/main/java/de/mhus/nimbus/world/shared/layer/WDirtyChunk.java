package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Dirty chunk entity - regeneration queue.
 * Tracks chunks that need to be regenerated from layers.
 */
@Document(collection = "w_dirty_chunks")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_chunk_idx", def = "{ 'worldId': 1, 'chunkKey': 1 }", unique = true),
        @CompoundIndex(name = "world_timestamp_idx", def = "{ 'worldId': 1, 'timestamp': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WDirtyChunk implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /**
     * Chunk key identifier (format: "cx:cz").
     */
    @Indexed
    private String chunkKey;

    /**
     * Timestamp when marked dirty.
     */
    private Instant timestamp;

    /**
     * Reason why chunk was marked dirty (for debugging/analytics).
     * Examples: "layer_updated", "layer_deleted", "regeneration_failed_retry"
     */
    private String reason;

    /**
     * Update the timestamp to current time.
     */
    public void touch() {
        timestamp = Instant.now();
    }
}
