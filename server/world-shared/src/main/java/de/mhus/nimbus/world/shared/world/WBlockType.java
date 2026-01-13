package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.BlockType;
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
 * MongoDB Entity for BlockType templates.
 * Wraps generated BlockType DTO in 'publicData' field.
 * BlockTypes are templates that define how blocks look and behave.
 */
@Document(collection = "w_blocktypes")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "blockId_idx", def = "{ 'blockId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WBlockType implements Identifiable {

    @Id
    private String id;

    /**
     * External block identifier (e.g., "core:stone", "w:123").
     * Unique across all block types.
     */
    @Indexed(unique = true)
    private String blockId;

    /**
     * Public data containing the generated BlockType DTO.
     * This is what gets serialized and sent to clients.
     */
    private BlockType publicData;

    /**
     * Optional world identifier for scoped block types.
     */
    @Indexed
    private String worldId;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Initialize timestamps for new entity.
     */
    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Get publicData with full blockId including prefix.
     * Reconstructs the full ID from worldId and blockId.
     * <p>
     * Example: blockId="wfr", worldId="r:..." -> returns BlockType with id="r:wfr"
     *
     * @return BlockType with corrected full ID
     */
    public BlockType getPublicDataWithFullId() {
        if (publicData == null) {
            return null;
        }

        // Determine prefix from worldId using WorldCollection
        var wid = de.mhus.nimbus.shared.types.WorldId.of(worldId).orElse(null);
        if (wid != null) {
            // Use WorldCollection to determine the correct prefix
            var collection = WorldCollection.of(wid, blockId);
            String fullId = collection.typeString() + ":" + blockId;
            publicData.setId(fullId);
        } else {
            // Fallback: use blockId as-is if worldId is invalid
            publicData.setId(blockId);
        }

        return publicData;
    }
}
