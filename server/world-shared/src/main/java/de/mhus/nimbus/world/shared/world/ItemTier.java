package de.mhus.nimbus.world.shared.world;

/**
 * Material/quality tier for items. Determines weapon/armor level,
 * crafting cost multiplier, and required skill level.
 * Higher tiers = better stats, higher cost, higher skill requirement.
 */
public enum ItemTier {
    NONE,
    LEATHER,
    IRON,
    STEEL,
    SILVER,
    GOLD,
    MYTHRIL,
    ADAMANT,
    ORICHALCUM
}
