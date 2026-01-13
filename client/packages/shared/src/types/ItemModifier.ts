/**
 * ItemModifier - Minimal item definition for rendering and behavior
 *
 * Items are Y-axis billboards with simple properties.
 * This is the storage representation. ChunkService converts this to
 * BlockModifier for rendering.
 *
 * Billboard mode (BILLBOARDMODE_Y) and transparency (ALPHA_TEST) are
 * enforced by ItemRenderer and not configurable.
 */

import type { ScriptActionDefinition } from '../scrawl/ScriptActionDefinition';

/**
 * Targeting mode for action effects
 *
 * - 'ENTITY': Only execute when entity is targeted
 * - 'BLOCK': Only execute when block is targeted
 * - 'BOTH': Execute when entity OR block is targeted
 * - 'GROUND': Always execute with ground position from camera ray
 * - 'ALL': Always execute (entity, block, or ground position)
 */
export type ActionTargetingMode = 'ENTITY' | 'BLOCK' | 'BOTH' | 'GROUND' | 'ALL';

export interface ItemModifier {
  /**
   * Texture path for the item (e.g., 'items/sword.png')
   * This is the only required field.
   */
  texture: string;

  /**
   * X-axis scaling (width)
   * Default: 0.5 (half block width)
   */
  scaleX?: number;

  /**
   * Y-axis scaling (height multiplier)
   * Default: 0.5 (half block height)
   * Final height = (texture height / texture width) * scaleY
   */
  scaleY?: number;

  /**
   * Pivot offset [x, y, z]
   * Shifts the item's center point relative to block position
   * Default: [0, 0, 0]
   *
   * Example: [0, -0.2, 0] lowers the item by 0.2 units
   */
  offset?: [number, number, number];

  /**
   * Optional tint color (hex format, e.g., '#ff0000')
   * Applied as a color overlay on the texture
   */
  color?: string;

  /**
   * Animation pose when item is used
   * Examples: 'attack', 'use', 'drink', 'cast'
   *
   * Pose is activated via ModifierService when the item shortcut is triggered.
   */
  pose?: string;

  /**
   * Scrawl script executed when item is used
   * This is the main interaction logic for the item.
   *
   * Example:
   * ```json
   * {
   *   "script": {
   *     "id": "fireball",
   *     "root": {
   *       "kind": "Play",
   *       "effectId": "projectile",
   *       "source": "$source",
   *       "target": "$target"
   *     }
   *   }
   * }
   * ```
   */
  onUseEffect?: ScriptActionDefinition;

  /**
   * Whether this item blocks other shortcuts while active
   * If true, no other shortcuts can be activated while this item is being used
   * Default: false
   */
  exclusive?: boolean;

  /**
   * Action script executed on specific triggers
   * This is separate from onUseEffect and can be triggered by other events
   * For example: automatic effects, passive abilities, environmental triggers
   */
  actionScript?: ScriptActionDefinition;

  /**
   * Targeting mode for visual effects (pose, onUseEffect)
   *
   * Controls when visual effects execute and what target they use:
   * - 'ENTITY': Only execute when entity is targeted
   * - 'BLOCK': Only execute when block is targeted
   * - 'BOTH': Execute when entity OR block is targeted
   * - 'GROUND': Always execute with ground position from camera ray
   * - 'ALL': Always execute (entity, block, or ground position)
   *
   * Default: 'ALL'
   *
   * Note: Server interactions always use 'BOTH' mode (entity OR block required).
   * This field only affects client-side visual effects and pose animations.
   */
  actionTargeting?: ActionTargetingMode; // javaType: String

  /**
   * This item is a generic. This means it has an amount on the item instance
   * and can be stacked in inventory. This kind of items have usually no special
   * modifiers.
   */
  generic?: boolean;

}
