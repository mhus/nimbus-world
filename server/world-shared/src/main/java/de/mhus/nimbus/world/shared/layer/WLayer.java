package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.shared.annotations.GenerateTypeScript;
import de.mhus.nimbus.shared.annotations.TypeScript;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Layer entity - main registry for all layers.
 * References specific layer data (LayerTerrain or LayerModel).
 */
@Document(collection = "w_layers")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_name_idx", def = "{ 'worldId': 1, 'name': 1 }", unique = true),
        @CompoundIndex(name = "world_order_idx", def = "{ 'worldId': 1, 'order': 1 }"),
        @CompoundIndex(name = "world_enabled_idx", def = "{ 'worldId': 1, 'enabled': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WLayer implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    @Indexed
    private String worldId;

    private String name;

    @TypeScript(optional = true)
    private String title;

    @TypeScript(follow = true)
    private LayerType layerType;

    /**
     * Reference to LayerTerrain or LayerModel collection.
     */
    private String layerDataId;

    /**
     * If true, this layer affects all chunks in the world.
     * If false, only chunks in affectedChunks list are affected.
     */
    @Builder.Default
    private boolean allChunks = true;

    /**
     * List of chunk keys (format: "cx:cz") affected by this layer.
     * Only used if allChunks is false.
     */
    @Builder.Default
    private List<String> affectedChunks = new ArrayList<>();

    /**
     * Layer overlay order.
     * Lower values are rendered first (bottom), higher values on top.
     */
    @Builder.Default
    private int order = 100;

    /**
     * Layer enabled flag (soft delete).
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Group mapping: group name -> group ID.
     * Allows named access to groups defined in the layer.
     * This is for Ground Layers, Model Layers use a mapping in WLayerModel.
     * Example: {"blackRiver": 1}
     */
    @Builder.Default
    private Map<String, Integer> groups = new HashMap<>();

    /**
     * Base ground layer flag.
     * If true, this layer is the base ground layer for the world.
     */
    @Builder.Default
    private boolean baseGround = false;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Set creation and update timestamps.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update the update timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
