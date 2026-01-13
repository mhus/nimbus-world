package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemBlockRef;
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
 * MongoDB Entity for Item positions in the world.
 * Stores ItemBlockRef data for items placed in chunks.
 * Each item position is associated with a specific chunk for efficient spatial queries.
 */
@Document(collection = "w_item_positions")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_itemId_idx", def = "{ 'worldId': 1, 'itemId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WItemPosition implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this item exists.
     */
    @Indexed
    private String worldId;

    /**
     * Item identifier (unique within world).
     */
    private String itemId;

    /**
     * Chunk key in format "cx:cz" (e.g., "0:0", "-1:2").
     * Indexed for efficient chunk-based queries.
     */
    @Indexed
    private String chunk;

    /**
     * Public data containing the ItemBlockRef DTO.
     * This is what gets serialized and sent to clients with chunk data.
     */
    private ItemBlockRef publicData;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Initialize timestamps for new item position.
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
}
