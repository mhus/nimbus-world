package de.mhus.nimbus.world.shared.world;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for crafting recipe definitions stored in WAnything collection "craftingRecipes".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CraftingRecipeDefinition {

    /**
     * Crafting category / station type (e.g. "smithing", "weaving", "alchemy", "writing", "woodworking").
     */
    private String category;

    /**
     * Minimum crafting level required for this recipe.
     */
    private int minLevel;

    /**
     * Whether spell words can be applied to this recipe.
     */
    private boolean allowSpells;

    /**
     * Optional list of allowed spell word names. If empty or null, all words are allowed.
     */
    private List<String> allowedSpellWords;

    /**
     * Required materials (itemId -> amount).
     */
    private Map<String, Integer> materials;

    /**
     * Result item ID (base item, without spell suffix).
     */
    private String resultItemId;

    /**
     * Result amount.
     */
    @Builder.Default
    private int resultAmount = 1;

    /**
     * Base success chance (0.0 - 1.0), scaled with crafting level.
     */
    @Builder.Default
    private double successChance = 1.0;

    /**
     * Crafting XP reward on success.
     */
    private int xpReward;

    /**
     * Spell word XP reward per word on success.
     */
    private int spellWordXpReward;

}
