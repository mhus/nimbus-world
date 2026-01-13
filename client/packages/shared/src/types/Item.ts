/**
 * Item - Item instance in the world
 *
 * Items reference an ItemType for default properties and can override them.
 * The ItemType provides texture, scaling, pose, and onUseEffect defaults.
 * Individual items can customize these via modifier.
 *
 * Items are stored with position, name, and id directly (no Block wrapper).
 * ChunkService converts Items to Blocks for rendering only.
 */

import type { ItemModifier } from './ItemModifier';

/** This is a marker interface for full Item with all properties loaded from ItemType */
export interface FullItem extends Item {

}

/**
 * Item instance in the world
 *
 * References an ItemType and optionally overrides its properties.
 * The position, name, and id are stored directly (not in a Block wrapper).
 */
export interface Item {
  /**
   * Unique item identifier
   * Generated on server, used for tracking and updates
   */
  id: string;

  /**
   * Item type identifier (e.g., 'sword', 'wand', 'potion')
   * References an ItemType definition loaded from files/itemtypes/{type}.json
   */
  itemType: string;

  /**
   * Position in world coordinates
   * Direct position, not wrapped in a Block
   * Use ItemBlockRef position instead.
   */
  // position: Vector3;

  /**
   * Optional display name
   * Overrides the ItemType name for this specific item instance
   */
  name?: string;

  /**
   * Optional description override
   * Overrides the ItemType description for this specific item instance
   */
  description?: string;

  /**
   * Optional modifier overrides
   *
   * Allows individual items to override ItemType.modifier properties.
   * Merged with ItemType.modifier to create final rendering modifier.
   *
   * Example:
   * ```json
   * {
   *   "modifier": {
   *     "texture": "items/enchanted_sword.png",
   *     "color": "#ff00ff",
   *     "scaleX": 0.7
   *   }
   * }
   * ```
   */
  modifier?: Partial<ItemModifier>;

  /**
   * Optional parameters
   *
   * Custom key-value pairs for item-specific data (server-side only).
   * Examples: durability, enchantments, customData
   */
  parameters?: Record<string, any>;
}
