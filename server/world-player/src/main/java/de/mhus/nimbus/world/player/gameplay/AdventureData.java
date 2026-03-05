package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * Adventure gameplay state container.
 * Extends EntityCombatData with adventure-specific fields (hunger, thirst, adrenaline, air,
 * combat idle timer, backpack, shortcuts, skills caches).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdventureData extends EntityCombatData {

    /** Seconds since last combat action (for adrenaline decay) */
    private double combatIdleTimer;

    /** Whether player is currently underwater (transient, not persisted) */
    private transient boolean underwater;

    /** Cached backpack data including itemIds and wearingItemIds (transient, not persisted) */
    private transient PlayerBackpack cachedBackpack;

    /** Cached shortcuts from PlayerInfo (transient, not persisted) */
    private transient Map<String, ShortcutDefinition> cachedShortcuts;

    /** Cached items by itemId - contains all loaded WItems from backpack, wearings, and shortcuts (transient, not persisted) */
    private transient Map<String, WItem> cachedItems;

    /** Cached skills from RCharacter (transient, not persisted) */
    private transient Map<String, Integer> cachedSkills;

    /** Timestamp until which collecting is blocked (transient, not persisted) */
    private transient long nextCollectAllowed = 0;

    /** Timestamp until which attacking is blocked by attack speed cooldown (transient, not persisted) */
    private transient long nextAttackAllowed = 0;

    /**
     * Initialize with default vital values and combat stats.
     * Calls super.initBaseDefaults() for health/stamina/mana + all combat stats,
     * then adds adventure-specific vitals: hunger, thirst, adrenaline, air.
     */
    public void initDefaults() {
        initBaseDefaults();

        // Override order for adventure-specific layout
        getVital("health").setOrder(0);
        getVital("stamina").setOrder(3);
        getVital("mana").setOrder(4);

        // Adventure-specific vitals
        var hunger = VitalValue.of("hunger",  100, 0.1,  "#CC8800", "Hunger", 1, 0.5);
        hunger.setCurrent(0);
        hunger.setOptions("p");
        getVitals().put("hunger", hunger);

        var thirst = VitalValue.of("thirst", 100, 0.15, "#4488FF", "Thirst", 2, 0.5);
        thirst.setCurrent(0);
        thirst.setOptions("p");
        getVitals().put("thirst", thirst);

        getVitals().put("adrenaline", VitalValue.of("adrenaline", 100, 0, "#FF8800", "Adrenaline", 5, 0.1));
        getVitals().put("air",        VitalValue.of("air",        100, 0, "#88CCFF", "Air",        6));
    }

    // --- Convenience accessors for backward compatibility ---

    public double getHealth() { return getVitalCurrent("health"); }
    public double getHunger() { return getVitalCurrent("hunger"); }
    public double getThirst() { return getVitalCurrent("thirst"); }
    public double getStamina() { return getVitalCurrent("stamina"); }
    public double getMana() { return getVitalCurrent("mana"); }
    public double getAdrenaline() { return getVitalCurrent("adrenaline"); }

    public double getMaxHealth() { return getVitalEffectiveMax("health"); }
    public double getMaxHunger() { return getVitalEffectiveMax("hunger"); }
    public double getMaxThirst() { return getVitalEffectiveMax("thirst"); }
    public double getMaxStamina() { return getVitalEffectiveMax("stamina"); }
    public double getMaxMana() { return getVitalEffectiveMax("mana"); }

    private double getVitalCurrent(String type) {
        var v = getVitals().get(type);
        return v != null ? v.getCurrent() : 0;
    }

    private double getVitalEffectiveMax(String type) {
        var v = getVitals().get(type);
        return v != null ? v.getEffectiveMax() : 0;
    }

    private String lastVitalisData;

}
