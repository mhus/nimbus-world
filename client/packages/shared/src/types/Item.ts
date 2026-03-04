/**
 * Item - Item definition in the world
 *
 * Items are Y-axis billboards with simple properties.
 * All modifier properties are now directly on the Item (flattened from ItemModifier).
 *
 * Items are stored with position, title, and id directly (no Block wrapper).
 * ChunkService converts Items to Blocks for rendering only.
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

/** This is a marker interface for full Item with all properties loaded from ItemType */
export interface FullItem extends Item {

}

/**
 * Item definition in the world
 *
 * Contains all properties directly (no separate ItemModifier).
 */
export interface Item {
  /**
   * Unique item name (technical identifier)
   * Generated on server, used for tracking and updates
   */
  name: string;

  /**
   * Item type identifier (e.g., 'sword', 'wand', 'potion')
   */
  itemType: string;

  /**
   * Item category type (e.g., 'weapon', 'tool', 'food', 'potion', 'armor', 'material')
   */
  type?: string;

  /**
   * Optional display title
   */
  title?: string;

  /**
   * Optional description
   */
  description?: string;

  // --- Rendering properties (formerly in ItemModifier) ---

  /**
   * Texture path for the item (e.g., 'items/sword.png')
   */
  texture?: string;

  /**
   * X-axis scaling (width)
   * Default: 0.5 (half block width)
   */
  scaleX?: number;

  /**
   * Y-axis scaling (height multiplier)
   * Default: 0.5 (half block height)
   */
  scaleY?: number;

  /**
   * Pivot offset [x, y, z]
   * Shifts the item's center point relative to block position
   * Default: [0, 0, 0]
   */
  offset?: [number, number, number];

  /**
   * Optional tint color (hex format, e.g., '#ff0000')
   */
  color?: string;

  /**
   * Animation pose when item is used
   * Examples: 'attack', 'use', 'drink', 'cast'
   */
  pose?: string;

  /**
   * Scrawl script executed when item is used
   */
  onUseEffect?: ScriptActionDefinition;

  /**
   * Whether this item blocks other shortcuts while active
   * Default: false
   */
  exclusive?: boolean;

  /**
   * Action script executed on specific triggers
   */
  actionScript?: ScriptActionDefinition;

  /**
   * Targeting mode for visual effects (pose, onUseEffect)
   * Default: 'ALL'
   */
  actionTargeting?: ActionTargetingMode; // javaType: String

  /**
   * Generic item with amount, can be stacked in inventory.
   */
  generic?: boolean;

  /**
   * Optional parameters
   * Custom key-value pairs for item-specific data.
   */
  parameters?: Record<string, string>;
}
