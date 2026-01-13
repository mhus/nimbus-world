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
    blockTypeId: 'w/1', // ITEM blockType with group prefix (w/1)
    offsets: item.offset ?? [0, 0, 0],
    modifiers: {
        0: {
            visibility: {
                shape: 28, // Shape.ITEM (Y-axis billboard)
                textures: {
                    0: item.texture
                },
                scalingX: item.scaleX ?? 0.5,
                scalingY: item.scaleY ?? 0.5,
            },
            physics: {
                interactive: true,
            }
        }
    },
    metadata: {
      id: item.id
    },
  };
}

// Deprecated implementation:
// /**
//  * Converts ItemModifier to BlockModifier for rendering.
//  *
//  * ItemRenderer expects BlockModifier structure with visibility properties.
//  * This method creates a minimal BlockModifier from the simplified ItemModifier.
//  *
//  * @param itemModifier Simplified item modifier (storage format)
//  * @returns BlockModifier with visibility properties (rendering format)
//  */
// private convertItemModifierToBlockModifier(itemModifier: any): any {
//     return {
//         visibility: {
//             shape: 28, // Shape.ITEM (Y-axis billboard)
//
//             // Convert simple texture string to textures map
//             textures: {
//                 0: itemModifier.texture, // TextureKey.ALL
//             },
//
//             // Copy scaling properties
//             scalingX: itemModifier.scaleX ?? 0.5,
//             scalingY: itemModifier.scaleY ?? 0.5,
//
//             // Copy offset (pivot point adjustment)
//             offsets: itemModifier.offset || [0, 0, 0],
//
//             // Copy optional color tint
//             color: itemModifier.color,
//         },
//         // Items don't need: wind, physics, illumination, effects, audio
//     };
// }
