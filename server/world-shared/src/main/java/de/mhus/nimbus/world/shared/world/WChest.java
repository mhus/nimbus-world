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
        @CompoundIndex(name = "region_player_idx", def = "{ 'regionId': 1, 'playerId': 1 }"),
        @CompoundIndex(name = "world_player_idx", def = "{ 'worldId': 1, 'playerId': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@GenerateTypeScript("entities")
public class WChest implements Identifiable, CowEntity {

    @Id
    @TypeScript(ignore = true)
    private String id;

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
     * Player identifier - optional, set for player-specific chests. Format: @userId:characterId
     */
    @Indexed
    @TypeScript(optional = true)
    private String playerId;

    /**
     * Type of chest determining access scope.
     */
    @Indexed
    @TypeScript(follow = true)
    private ChestType type;

    /**
     * Optional PIN code for accessing the chest, if required.
     */
    private String pin;

    /**
     * Maximum number of items allowed in the chest.
     */
    private int capacity;

    /**
     * Optional key identifier for chests that require a key, referencing an itemId of a key item.
     */
    private String keyId;

    /**
     * Optional lock picking difficulty level for the chest, if it can be lockpicked instead of using a key.
     * Higher values indicate more difficult locks. If 0 or not set, lockpicking is not possible.
     */
    @Builder.Default
    private int lockPickingDifficulty = 0;


    /**
     * Items stored in this chest.
     */
    @Builder.Default
    @TypeScript(import_ = "ItemRef", importPath = "../../types/ItemRef")
    private List<ItemRef> items = new ArrayList<>();

    /**
     * Soft delete flag. Used as tombstone marker in COW instances.
     */
    @Indexed
    @Builder.Default
    @TypeScript(ignore = true)
    private boolean enabled = true;

    private Instant createdAt;
    private Instant updatedAt;

    @Override
    @org.springframework.data.annotation.Transient
    public String getCowId() {
        return name;
    }

    @Override
    @org.springframework.data.annotation.Transient
    public boolean isCowEnabled() {
        return enabled;
    }

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
         * Player-specific chest, only accessible to the owner.
         */
        PLAYER,

        /**
         * Player bank chest, requires playerId. Stores the player's bank items.
         */
        BANK,

        /**
         * Player transfer chest, requires playerId. Used for item transfers between players or systems.
         */
        TRANSFER,

        /**
         * Merchant's visible shop chest, linked to a WTrader.
         */
        MERCHANT,

        /**
         * Merchant's hidden pool chest for inventory cycling, linked to a WTrader.
         */
        MERCHANT_POOL
    }
}
