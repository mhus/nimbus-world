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
@ActualSchemaVersion("1.2.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_name_idx", def = "{ 'worldId': 1, 'name': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WBlockType implements Identifiable {

    @Id
    private String id;

    /**
     * Unique technical name for this block type (e.g., "stone", "oak_planks").
     * Unique per world (compound index with worldId).
     */
    private String name;

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

    /**
     * Default client-side parameters for blocks of this type.
     * These are sent to the client when blocks are placed.
     */
    private java.util.Map<String, String> defaultClient;

    /**
     * Default server-side parameters for blocks of this type.
     * These are stored as serverInfo when blocks are placed.
     */
    private java.util.Map<String, String> defaultServer;

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
        touchUpdate();
    }

    /**
     * Update modification timestamp.
     */
    public void touchUpdate() {
        updatedAt = Instant.now();
        if (publicData != null)
            publicData.setName(getName());
    }

    public WBlockType appendWorldPrefix() {
        if (publicData == null) return this;
        publicData.setName(WorldCollection.appendPrefix(worldId, publicData.getName()));
        return this;
    }

    public WBlockType removeWorldPrefix() {
        if (publicData == null) return this;
        setName(WorldCollection.removePrefix(getName())); // for secure
        if (publicData != null)
            publicData.setName(WorldCollection.removePrefix(publicData.getName()));
        return this;
    }

}
