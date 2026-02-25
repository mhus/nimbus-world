package de.mhus.nimbus.world.generator.fauna;

/**
 * Defines how animals are grouped together.
 * <ul>
 *   <li>HERD: All animals in a group share the same gender</li>
 *   <li>HAREM: One male leader, all others female</li>
 *   <li>MIXED: Each animal gets a random gender</li>
 *   <li>LONER: No group formation, each animal placed individually with random gender</li>
 * </ul>
 */
public enum FaunaGroupType {
    HERD, HAREM, MIXED, LONER
}
