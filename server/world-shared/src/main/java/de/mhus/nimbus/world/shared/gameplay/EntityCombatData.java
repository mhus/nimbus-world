package de.mhus.nimbus.world.shared.gameplay;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared combat data container for any entity (players and NPCs).
 * Holds vital values, combat stats, and active effects.
 *
 * <p>Players extend this via AdventureData with additional adventure-specific fields.
 * NPCs use this directly, initialized from WEntity.server properties.</p>
 */
@Data
public class EntityCombatData extends GameplayData {

    /** All vital values keyed by type (health, stamina, mana, etc.) */
    private Map<String, VitalValue> vitals = new LinkedHashMap<>();

    /** All combat stats keyed by type (physical.damage, physical.defense, etc.) */
    private Map<String, CombatStat> combatStats = new LinkedHashMap<>();

    /** Active effects (buffs, debuffs, DoTs) */
    private List<ActiveEffect> activeEffects = new ArrayList<>();

    /** Timestamp of last tick for delta calculation */
    private long lastTickTimestamp;

    /** Passive stats from equipment + skills, recalculated on inventory/skill change (transient) */
    private transient PassiveStats passiveStats;

    /** Combat strategy when attacked (FLEE, ATTACK_FLEE, ATTACK_REPEAT) */
    private CombatStrategy combatStrategy = CombatStrategy.FLEE;

    /** Weapon item ID used for attacks (default: fist) */
    private String weaponItemId = CombatConstants.FIST_ITEM_ID;

    /**
     * Get a vital value by type, or null if not found.
     */
    public VitalValue getVital(String type) {
        return vitals.get(type);
    }

    /**
     * Get a combat stat by type, or null if not found.
     */
    public CombatStat getCombatStat(String type) {
        return combatStats.get(type);
    }

    /**
     * Add an active effect. Instant effects (.current) are applied immediately.
     */
    public void addEffect(ActiveEffect effect) {
        if (effect.isInstant()) {
            applyInstantEffect(effect);
        } else {
            activeEffects.add(effect);
        }
    }

    /**
     * Remove all effects from a specific source.
     */
    public void removeEffectsBySource(String source) {
        activeEffects.removeIf(e -> source.equals(e.getSource()));
    }

    /**
     * Apply an instant effect (e.g., "health.current:50" = heal 50 HP immediately).
     */
    private void applyInstantEffect(ActiveEffect effect) {
        String vitalType = effect.getStatGroup();
        VitalValue vital = vitals.get(vitalType);
        if (vital != null) {
            vital.setCurrent(vital.getCurrent() + effect.getValue() * effect.getStacks());
            vital.clamp();
        }
    }

    /**
     * Initialize with base combat defaults: health, stamina, mana + all combat stats.
     * Suitable for NPCs that only need the basic combat vitals (no hunger/thirst/adrenaline/air).
     */
    public void initBaseDefaults() {
        vitals.put("health",  VitalValue.of("health",  100, 0.5, "#FF4444", "Health",  0));
        vitals.put("stamina", VitalValue.of("stamina", 100, 2.0, "#44CC44", "Stamina", 1));
        vitals.put("mana",    VitalValue.of("mana",    100, 1.0, "#AA44FF", "Mana",    2));

        combatStats.put("physical.damage",   CombatStat.of("physical.damage",   5));
        combatStats.put("physical.accuracy",  CombatStat.of("physical.accuracy",  0.7));
        combatStats.put("physical.defense",   CombatStat.of("physical.defense",   0));
        combatStats.put("physical.evasion",   CombatStat.of("physical.evasion",   0.1));
        combatStats.put("magical.damage",     CombatStat.of("magical.damage",     0));
        combatStats.put("magical.accuracy",   CombatStat.of("magical.accuracy",   0.7));
        combatStats.put("magical.defense",    CombatStat.of("magical.defense",    0));
        combatStats.put("magical.evasion",    CombatStat.of("magical.evasion",    0.05));
        combatStats.put("attackSpeed",        CombatStat.of("attackSpeed",        1.0));
        combatStats.put("critChance",         CombatStat.of("critChance",         0.05));
        combatStats.put("critMultiplier",     CombatStat.of("critMultiplier",     1.5));

        lastTickTimestamp = System.currentTimeMillis();
    }

    /**
     * Create EntityCombatData from WEntity.server properties.
     * Properties format (underscores instead of dots for MongoDB compatibility):
     * <ul>
     *   <li>vital_health=100, vital_health_regen=0.5</li>
     *   <li>vital_mana=50, vital_stamina=80</li>
     *   <li>combat_physical_damage=10, combat_physical_accuracy=0.7</li>
     *   <li>combat_attackSpeed=1.2, combat_critChance=0.05</li>
     *   <li>combat_range=c (c=close, f=far, cf=both)</li>
     * </ul>
     *
     * @param server WEntity.server properties map
     * @return Initialized EntityCombatData, or null if no combat properties found
     */
    public static EntityCombatData fromEntityProperties(Map<String, String> server) {
        if (server == null) return null;

        boolean hasCombatProps = server.keySet().stream()
                .anyMatch(k -> k.startsWith("vital_") || k.startsWith("combat_"));
        if (!hasCombatProps) return null;

        EntityCombatData data = new EntityCombatData();
        data.initBaseDefaults();

        // Override vitals from properties (underscore format: vital_health, vital_health_regen)
        overrideVital(data, server, "health", "#FF4444", "Health", 0);
        overrideVital(data, server, "stamina", "#44CC44", "Stamina", 1);
        overrideVital(data, server, "mana", "#AA44FF", "Mana", 2);

        // Override combat stats from properties (underscore format: combat_physical_damage)
        overrideCombatStat(data, server, "physical.damage", "physical_damage");
        overrideCombatStat(data, server, "physical.accuracy", "physical_accuracy");
        overrideCombatStat(data, server, "physical.defense", "physical_defense");
        overrideCombatStat(data, server, "physical.evasion", "physical_evasion");
        overrideCombatStat(data, server, "magical.damage", "magical_damage");
        overrideCombatStat(data, server, "magical.accuracy", "magical_accuracy");
        overrideCombatStat(data, server, "magical.defense", "magical_defense");
        overrideCombatStat(data, server, "magical.evasion", "magical_evasion");
        overrideCombatStat(data, server, "attackSpeed", "attackSpeed");
        overrideCombatStat(data, server, "critChance", "critChance");
        overrideCombatStat(data, server, "critMultiplier", "critMultiplier");

        data.setLastTickTimestamp(System.currentTimeMillis());

        // Parse combat strategy (default: FLEE)
        String strategyVal = server.get("combat_strategy");
        data.setCombatStrategy(CombatStrategy.fromString(strategyVal));

        // Weapon item ID (default: fist)
        String weaponId = server.get("combat_weapon");
        if (weaponId != null && !weaponId.isBlank()) {
            data.setWeaponItemId(weaponId.trim());
        }

        return data;
    }

    /**
     * Apply weapon effects to this entity's combat stats.
     * Parses the weapon's "effects" string (same format as item effects:
     * "physical.damage:10,physical.accuracy:0.8") and adds the values
     * to the corresponding base combat stats.
     *
     * @param effectsDef Comma-separated effect definitions from WItem.server.effects
     */
    public void applyWeaponEffects(String effectsDef) {
        if (effectsDef == null || effectsDef.isBlank()) return;
        for (String effectStr : effectsDef.split(",")) {
            String trimmed = effectStr.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(":");
            if (parts.length < 2) continue;
            String statName = parts[0].trim();
            double value;
            try {
                value = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            CombatStat stat = combatStats.get(statName);
            if (stat != null) {
                stat.setBase(stat.getBase() + value);
                stat.resetBuffs();
                stat.recalculate();
            } else {
                CombatStat newStat = CombatStat.of(statName, value);
                combatStats.put(statName, newStat);
            }
        }
    }

    private static void overrideVital(EntityCombatData data, Map<String, String> server,
                                       String vitalName, String color, String displayName, int order) {
        String baseKey = "vital_" + vitalName;
        String baseVal = server.get(baseKey);
        if (baseVal != null) {
            double base = parseDouble(baseVal, 100);
            double regen = parseDouble(server.get(baseKey + "_regen"), 0);
            VitalValue vital = VitalValue.of(vitalName, base, regen, color, displayName, order);
            data.getVitals().put(vitalName, vital);
        } else {
            // Check if regen is overridden even without base
            String regenVal = server.get(baseKey + "_regen");
            if (regenVal != null) {
                VitalValue vital = data.getVital(vitalName);
                if (vital != null) {
                    vital.setBaseRegenRate(parseDouble(regenVal, vital.getBaseRegenRate()));
                    vital.resetBuffs();
                    vital.recalculate();
                }
            }
        }
    }

    /**
     * @param statName  Runtime stat name (with dots, e.g. "physical.damage")
     * @param propSuffix Property suffix in server map (with underscores, e.g. "physical_damage")
     */
    private static void overrideCombatStat(EntityCombatData data, Map<String, String> server,
                                            String statName, String propSuffix) {
        String key = "combat_" + propSuffix;
        String val = server.get(key);
        if (val != null) {
            CombatStat stat = CombatStat.of(statName, parseDouble(val, 0));
            data.getCombatStats().put(statName, stat);
        }
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
