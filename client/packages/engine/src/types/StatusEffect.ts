/**
 * StatusEffect - Active status effect on player
 *
 * Status effects are temporary modifiers applied to the player.
 * They use a texture path for visual representation.
 */

/**
 * Active status effect
 */
export interface StatusEffect {
  /**
   * Unique identifier for this effect instance
   * Generated when effect is applied
   */
  id: string;

  /**
   * Texture path for the effect icon (e.g. "n:/textures/items/potion_heal.png")
   */
  texture: string;

  /**
   * Display title for tooltip (optional)
   */
  title?: string;

  /**
   * Timestamp when effect was applied (ms)
   */
  appliedAt: number;

  /**
   * Duration in milliseconds (optional)
   * If specified, effect is automatically removed after this time
   * If not specified, effect persists until manually removed
   */
  duration?: number;

  /**
   * Timestamp when effect expires (ms)
   * Calculated as appliedAt + duration
   */
  expiresAt?: number;
}
