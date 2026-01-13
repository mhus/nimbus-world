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
import java.util.stream.Stream;

/**
 * Model layer entity - entity-oriented storage.
 * Entire block structure stored in MongoDB document.
 * Blocks have relative positions from mount point.
 */
@Document(collection = "w_layer_model")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "layerData_idx", def = "{ 'layerDataId': 1 }"),
        @CompoundIndex(name = "world_layerData_idx", def = "{ 'worldId': 1, 'layerDataId': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WLayerModel implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    @Indexed
    private String worldId;

    /**
     * A name is not optional, it's unique
     */
    private String name;

    /**
     * A title if needed or empty if not
     */
    @TypeScript(optional = true)
    private String title;

    /**
     * References WLayer.layerDataId (1:1 relationship).
     */
    @Indexed
    private String layerDataId;

    /**
     * mount point X coordinate.
     */
    private int mountX;

    /**
     * mount point Y coordinate.
     */
    private int mountY;

    /**
     * mount point Z coordinate.
     */
    private int mountZ;

    /**
     * Rotation in 90 degree steps.
     * 0 = no rotation, 1 = 90 degrees, 2 = 180 degrees, 3 = 270 degrees.
     */
    @Builder.Default
    private int rotation = 0;

    /**
     * Reference to another layer model.
     * If this is set first the referenced model will be rendered and then this model on top.
     * Currently: The referenced model can not reference another model. No cascading.
     * e.g. r:
     */
    @TypeScript(optional = true)
    private String referenceModelId;

    /**
     * Layer overlay order.
     * Lower values are rendered first (bottom), higher values on top.
     */
    @Builder.Default
    private int order = 100;

    /**
     * Layer blocks with relative positions from mount point.
     * Position (0,0,0) = mount point.
     * Position (-1,2,3) = 1 left, 2 up, 3 forward from mount point.
     */
    @Builder.Default
    @TypeScript(type = "any[]") // for now use any
    private List<LayerBlock> content = new ArrayList<>();

    /**
     * Group mapping: group name -> group ID.
     * Allows named access to groups defined in the layer.
     * Example: {"walls": 1, "roof": 2, "floor": 3}
     */
    @Builder.Default
    private Map<String, Integer> groups = new HashMap<>();

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

    /**
     * Get block positions as a stream.
     * Returns world coordinates (mount point + relative position).
     * Streams directly from block data without creating intermediate collections.
     *
     * @return Stream of block positions as int arrays [x, y, z, groupId, x-org, y-org, z-org]
     */
    public Stream<int[]> getBlockPositions() {
        if (content == null || content.isEmpty()) {
            return Stream.empty();
        }

        return content.stream()
                .filter(layerBlock -> layerBlock.getBlock() != null)
                .filter(layerBlock -> layerBlock.getBlock().getPosition() != null)
                .map(layerBlock -> {
                    de.mhus.nimbus.generated.types.Vector3Int relativePos = layerBlock.getBlock().getPosition();
                    return new int[]{
                            mountX + relativePos.getX(),
                            mountY + relativePos.getY(),
                            mountZ + relativePos.getZ(),
                            layerBlock.getGroup(),
                            relativePos.getX(),
                            relativePos.getY(),
                            relativePos.getZ(),
                    };
                });
    }
}
