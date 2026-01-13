/**
 * ItemType - Template definition for items
 *
 * Provides default properties for item categories (sword, wand, potion, etc.)
 * Individual items can override these defaults via ItemData.modifierOverrides.
 *
 * Similar to BlockType but for items. Stored in server files/itemtypes/*.json
 */

import type { ItemModifier } from './ItemModifier';

export interface ItemType {
  /**
   * Unique item type identifier (e.g., 'sword', 'wand', 'potion')
   * Used to reference this ItemType from Item
   */
  type: string;

  /**
   * Display name for this item type
   * Example: "Sword", "Magic Wand", "Health Potion"
   */
  name: string;

  /**
   * Optional description
   * Provides context about this item type
   */
  description?: string;

  /**
   * Default item modifier
   * Contains texture, scaling, pose, onUseEffect, exclusive flag, etc.
   * Individual items can override these via Item.modifier
   */
  modifier: ItemModifier;

  /**
   * Optional parameters
   *
   * Custom key-value pairs for item-specific data (server-side only).
   * Examples: durability, enchantments, customData
   */
  parameters?: Record<string, any>;

}
