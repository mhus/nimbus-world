package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AccessLevel;
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
 * MongoDB Entity for NPC traders/service providers.
 * Linked to a WEntity via entityId. Holds commerce, training, and service data.
 */
@Document(collection = "w_traders")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_entityId_idx", def = "{ 'worldId': 1, 'entityId': 1 }", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WTrader implements Identifiable {

    @Id
    private String id;

    @Indexed
    private String worldId;

    /**
     * Links to WEntity.entityId — the NPC this trader data belongs to.
     */
    private String entityId;

    @Builder.Default
    private TraderType traderType = TraderType.MERCHANT;

    /**
     * Item categories this trader handles (portfolio).
     * Values correspond to Item.type (weapon, armor, tool, food, potion, material, consumable).
     */
    @Builder.Default
    private List<String> categories = new ArrayList<>();

    /**
     * Fixed personality price modifier. Positive = more expensive, negative = cheaper.
     */
    private double personalityModifier;

    /**
     * Available silver for buying/selling.
     */
    private long silverAmount;

    /**
     * WChest.name for the visible shop chest.
     */
    private String chestId;

    /**
     * WChest.name for the hidden pool chest.
     */
    private String poolChestId;

    /**
     * Fixed or logic-controlled quest item IDs (not part of normal pool cycling).
     */
    @Builder.Default
    private List<String> questItems = new ArrayList<>();

    /**
     * Maximum number of items displayed to the player when opening the shop.
     */
    @Builder.Default
    private int maxDisplayItems = 12;

    /**
     * Exchange rate for Gold to Silver conversion.
     */
    @Builder.Default
    private double goldExchangeRate = 10.0;

    // --- Trainer fields ---

    /**
     * Skills this trainer can train (e.g. "smithing.iron", "sword.steel").
     */
    @Builder.Default
    private List<String> trainableSkills = new ArrayList<>();

    /**
     * Maximum skill points this trainer can train per skill.
     */
    private int maxSkillPoints;

    /**
     * Cost in silver per skill point.
     */
    private double costPerSkillPoint;

    // --- Service fields ---

    /**
     * Types of repair this NPC offers (e.g. "weapon", "armor", "magic").
     */
    @Builder.Default
    private List<String> repairTypes = new ArrayList<>();

    /**
     * Cost in silver per repair point.
     */
    private double repairCostPerPoint;

    // --- Pool sync ---

    /**
     * Last time the pool was synced with the shop chest.
     */
    private Instant lastPoolSync;

    /**
     * Minimum interval in seconds between pool syncs.
     */
    @Builder.Default
    private int poolSyncIntervalSeconds = 3600;

    private Instant createdAt;
    private Instant updatedAt;

    @Indexed
    @Builder.Default
    private boolean enabled = true;

    public void touchCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public void touchUpdate() {
        updatedAt = Instant.now();
    }
}
