package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemType;
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
 * MongoDB Entity for ItemType templates.
 * Wraps generated ItemType DTO in 'publicData' field.
 * ItemTypes are templates that define how items look and behave.
 */
@Document(collection = "w_itemtypes")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "itemType_idx", def = "{ 'itemType': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WItemType implements Identifiable {

    @Id
    private String id;

    /**
     * Item type identifier (e.g., "sword", "axe", "potion").
     * Unique across all item types.
     */
    @Indexed(unique = true)
    private String itemType;

    /**
     * Public data containing the generated ItemType DTO.
     * This is what gets serialized and sent to clients.
     */
    private ItemType publicData;

    /**
     * Optional world identifier for scoped item types.
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
}
