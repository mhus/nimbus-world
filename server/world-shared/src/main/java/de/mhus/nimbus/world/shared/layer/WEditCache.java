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
 * Edit cache entity - temporary block storage for editing.
 * Stores edited blocks before they are committed to layers.
 * Each block is stored individually for efficient editing.
 */
@Document(collection = "w_edit_cache")
@ActualSchemaVersion("1.1.0")
@CompoundIndexes({
        @CompoundIndex(name = "worldId_chunk_idx", def = "{ 'worldId': 1, 'chunk': 1 }"),
        @CompoundIndex(name = "worldId_layerDataId_idx", def = "{ 'worldId': 1, 'layerDataId': 1 }"),
        @CompoundIndex(name = "unique_block_position_idx", def = "{ 'worldId': 1, 'layerDataId': 1, 'modelName': 1, 'x': 1, 'y': 1, 'z': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WEditCache implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier.
     */
    @Indexed
    private String worldId;

    /**
     * X coordinate in world coordinates.
     */
    private int x;

    /**
     * Y coordinate in world coordinates.
     */
    private int y;

    /**
     * Z coordinate in world coordinates.
     */
    private int z;

    /**
     * Chunk identifier (format: "cx:cz").
     * World coordinates, not layer-local coordinates.
     */
    private String chunk;

    /**
     * Layer data identifier - references WLayer.layerDataId.
     */
    @Indexed
    private String layerDataId;

    /**
     * Model name - references WLayerModel.name.
     * Only set for MODEL type layers, null for GROUND layers.
     */
    private String modelName;

    /**
     * Block data with layer-specific properties.
     */
    private LayerBlock block;

    /**
     * Timestamp when block was created in cache.
     */
    private Instant createdAt;

    /**
     * Timestamp when block was last modified.
     */
    private Instant modifiedAt;

    /**
     * Initialize timestamps for new cache entry.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        modifiedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        modifiedAt = Instant.now();
    }
}
