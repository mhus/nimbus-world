/**
 * StatusEffect - Active status effect on player
 *
 * Status effects are temporary modifiers applied to the player.
 * They reference items from the server (ItemData) which contain:
 * - Visual representation (icon/texture)
 * - Description
 * - Duration
 * - Pose (optional)
 * - Parameters (custom effect data)
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
   * Item ID that defines this effect
   * References an ItemData on the server
   */
  itemId: string;

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
