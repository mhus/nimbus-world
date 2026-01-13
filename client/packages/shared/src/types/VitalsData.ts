/**
 * VitalsData - Player vital statistics (health, hunger, mana, etc.)
 *
 * Vitals are player attributes that change over time through:
 * - Regeneration (regenRate)
 * - Degeneration (degenRate)
 * - Temporary extensions (extended)
 * - Actions (taking damage, eating, drinking, etc.)
 */

/**
 * Vital statistic data
 */
export interface VitalsData {
  /**
   * Vital type identifier
   * Examples: 'health', 'hunger', 'thirst', 'stamina', 'mana', 'oxygen'
   */
  type: string;

  /**
   * Current value
   * Range: 0 to (max + extended)
   */
  current: number; // javaType: int

  /**
   * Maximum base value
   * This is the permanent maximum
   */
  max: number; // javaType: int

  /**
   * Extended maximum (temporary bonus)
   * Optional - adds to max temporarily
   * Example: Buff adds +20 max health temporarily
   * Default: 0
   */
  extended?: number; // javaType: int

  /**
   * Timestamp when extension expires (milliseconds)
   * Optional - when this time is reached, extended is set to 0
   * If not specified, extension is permanent until manually changed
   */
  extendExpiry?: number; // javaType: long

  /**
   * Regeneration rate per second
   * Positive values = automatic increase over time
   * Example: 1.0 = +1 per second
   * Default: 0
   */
  regenRate: number;

  /**
   * Degeneration rate per second
   * Positive values = automatic decrease over time
   * Example: 0.5 = -0.5 per second (hunger decreases over time)
   * Default: 0
   */
  degenRate: number;

  /**
   * Color for UI display
   * Hex color code (e.g., '#ff0000' for red health bar)
   */
  color: string;

  /**
   * Display name for UI
   * Human-readable name shown in tooltips
   * Example: 'Health', 'Hunger', 'Mana'
   */
  name: string;

  /**
   * Display order in UI
   * Lower numbers appear first (top)
   * Example: health=0, hunger=1, stamina=2
   */
  order: number; // javaType: int
}
