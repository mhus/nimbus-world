package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ItemRef;
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
import java.util.List;

/**
 * MongoDB Entity for Chests - storage for items.
 * Chests can be user-specific or general, always region-specific.
 * They use the same service and UI but different types determine their access scope.
 */
@Document(collection = "w_chests")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "region_name_idx", def = "{ 'regionId': 1, 'name': 1 }", unique = true),
        @CompoundIndex(name = "world_name_idx", def = "{ 'worldId': 1, 'name': 1 }"),
        @CompoundIndex(name = "region_user_idx", def = "{ 'regionId': 1, 'userId': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WChest implements Identifiable {

    @Id
    @TypeScript(ignore = true)
    private String id;

    /**
     * Region identifier - always set, separate from worldId.
     */
    @Indexed
    private String regionId;

    /**
     * World identifier - optional.
     */
    @Indexed
    @TypeScript(optional = true)
    private String worldId;

    /**
     * Internal character/identifier (e.g., UUID).
     */
    private String name;

    /**
     * Display name - optional.
     */
    @TypeScript(optional = true)
    private String title;

    /**
     * Description of the chest.
     */
    @TypeScript(optional = true)
    private String description;

    /**
     * User identifier - optional, set for user-specific chests.
     */
    @Indexed
    @TypeScript(optional = true)
    private String userId;

    /**
     * Type of chest determining access scope.
     */
    @Indexed
    @TypeScript(follow = true)
    private ChestType type;

    /**
     * Items stored in this chest.
     */
    @Builder.Default
    @TypeScript(import_ = "ItemRef", importPath = "../../types/ItemRef")
    private List<ItemRef> items = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Initialize timestamps for new chest.
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
     * Chest type enum defining access scope.
     */
    public enum ChestType {
        /**
         * Region-wide chest, accessible to all users in the region.
         */
        REGION,

        /**
         * World-specific chest, accessible in a specific world.
         */
        WORLD,

        /**
         * User-specific chest, only accessible to the owner.
         */
        USER
    }
}
