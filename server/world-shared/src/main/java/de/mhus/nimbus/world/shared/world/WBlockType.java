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

    public WBlockType appendWorldPrefix() {
        if (publicData == null) return this;
        publicData.setId(WorldCollection.appendPrefix(worldId, publicData.getId()));
        return this;
    }

    public  WBlockType removeWorldPrefix() {
        if (publicData == null) return this;
        publicData.setId(WorldCollection.removePrefix(publicData.getId()));
        return this;
    }

}
