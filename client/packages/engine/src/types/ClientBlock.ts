/**
 * ClientBlock - Client-side block instance
 *
 * Contains the block instance with resolved client-side types and caches.
 * This type is NOT transmitted over network - it's only used client-side.
 *
 * Per specification (network-model-2.0.md):
 * - Server sends BlockData (serialized) over network
 * - Client deserializes BlockData into Block objects
 * - ClientBlock wraps Block with resolved references and rendering caches
 */

import type {Block, BlockType, BlockModifier, Item, ItemBlockRef} from '@nimbus/shared';
import type { ClientBlockType } from './ClientBlockType';
import type { Mesh } from '@babylonjs/core';

/**
 * Client-side block instance with caches and resolved references
 *
 * This wraps a Block instance (deserialized from BlockData) with:
 * - Resolved references (BlockType, BlockModifier from registry)
 * - Client-optimized rendering data (ClientBlockType)
 * - Visibility and dirty flags
 */
export interface ClientBlock {
  /**
   * Original block instance (deserialized from BlockData)
   * Contains: x, y, z, blockTypeId, status, modifierIndex, metadata
   */
  block: Block;

  /** Chunk coordinates */
  chunk: { cx: number; cz: number };

  // Cached references (resolved from IDs)

  /**
   * Cached BlockType reference
   * Resolved from block.blockTypeId via registry
   *
   * Note: Items use BlockType ID 5 (fixed ITEM type)
   */
  blockType: BlockType;


  /**
   * Cached current BlockModifier
   * Resolved from block.modifiers[block.status] or blockType.modifiers[block.status]
   * For items: Generated from block.itemModifier by ChunkService
   */
  currentModifier: BlockModifier;

  /**
   * Current status (string representation for debugging)
   * @example "OPEN", "CLOSED", "WINTER"
   */
  statusName?: string;

  // Additional client-side caches

  /**
   * Is block currently visible (culling, distance, etc.)
   */
  isVisible?: boolean;

  /**
   * Last update timestamp (for change detection)
   */
  lastUpdate?: number;

  /**
   * Dirty flag (needs re-render)
   */
  isDirty?: boolean;

  /**
   * Optional reference to the rendered mesh for this block
   * Can be used for direct highlighting or manipulation
   * Note: In chunked rendering, blocks are merged into chunk meshes,
   * so this reference is only available for individually rendered blocks
   */
  mesh?: Mesh;

  /**
   * Complete Item data for items
   * Contains id, itemType, position, name, modifier (with actionScript), parameters
   * Only present for items (ChunkService converts Item to Block+ClientBlock for rendering)
   *
   * The actionScript is in item.modifier.actionScript (ScriptActionDefinition)
   */
  itemBlockRef?: ItemBlockRef;
}
