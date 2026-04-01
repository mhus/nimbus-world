package de.mhus.nimbus.world.shared.world;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for spell word definitions stored in WAnything collection "spellWords".
 * Each spell word has a category and properties that flow into crafted items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpellWordDefinition {

    /**
     * Category of the spell word: "element", "form", or "modifier".
     */
    private String category;

    /**
     * Icon identifier for UI display.
     */
    private String icon;

    /**
     * Concrete properties that flow into crafted items (e.g. damageType, baseDamage, burnChance).
     * Values are scaled with the player's word level during crafting.
     */
    private Map<String, Object> properties;

}
