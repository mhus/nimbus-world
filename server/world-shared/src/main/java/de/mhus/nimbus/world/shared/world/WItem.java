package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Item;
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

/**
 * MongoDB Entity for Items (inventory/template).
 * Items are reusable and can appear in different worlds without position.
 * For placed items with position, see WItemPosition.
 */
@Document(collection = "w_items")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_itemId_idx", def = "{ 'worldId': 1, 'itemId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WItem implements Identifiable {

    @Id
    @TypeScript(ignore = true)
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
     * Public data containing the Item DTO.
     * This includes itemType, name, description, modifier, and parameters.
     */
    @TypeScript(import_ = "Item", importPath = "../../types")
    private Item publicData;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Soft delete flag.
     */
    @Indexed
    @Builder.Default
    private boolean enabled = true;

    /**
     * Initialize timestamps for new item.
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
