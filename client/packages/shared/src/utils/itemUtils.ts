/**
 * Item Utilities - Helper functions for Item-to-Block conversion
 *
 * Items are stored as Item objects but transmitted and rendered as Blocks.
 * This module provides conversion utilities used by both client and server.
 */

import type { Block } from '../types/Block';
import {ItemBlockRef} from "../types";

/**
 * Converts an Item to a Block for network transmission or rendering
 *
 * Items are stored with position, id, name, etc. directly.
 * For network transmission and rendering, they need to be converted to Blocks
 * with BlockType 1 (ITEM type) and metadata containing the item information.
 *
 * @param item Item to convert
 * @returns Block representation of the item
 */
export function itemToBlock(item: ItemBlockRef): Block {
  return {
    position: item.position,
    blockTypeId: 'n:1', // ITEM blockType with group prefix (n:1)
    offsets: item.offset ?? [0, 0, 0],
    modifiers: {
        'default': {
            visibility: {
                shape: 28, // Shape.ITEM (Y-axis billboard)
                textures: {
                    0: item.texture
                },
                scalingX: item.scaleX ?? 0.5,
                scalingY: item.scaleY ?? 0.5,
            },
        }
    },
    metadata: {
      id: item.name,
      interactive: true,
    },
  };
}

