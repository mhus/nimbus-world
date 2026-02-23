/**
 * BlockType - Definition/Template for a block type
 *
 * This is the registry definition that defines what a block type is.
 * BlockType instances in the world only store the BlockType ID.
 */

import type { BlockModifier } from './BlockModifier';

/**
 * Block status values
 */
export enum BlockStatus {
  DEFAULT = 'default',
  OPEN = 'open',
  CLOSED = 'closed',
  LOCKED = 'locked',
  DESTROYED = 'destroyed',

  // Seasonal states
  WINTER = 'winter',
  SPRING = 'spring',
  SUMMER = 'summer',
  AUTUMN = 'autumn',

}

export enum BlockTypeType {
    AIR = 0,
    GROUND = 1,
    WATER = 2,
    PLANT = 3,
    PLANT_PART = 4,
    STRUCTURE = 5,
    DECORATION = 6,
    UTILITY = 7,
    LAVA = 8,
    WINDOW = 9,
    DOOR = 10,
    WALL = 11,
    ROOF = 12,
    PATH = 13,
    FENCE = 14,
    STAIRS = 15,
    RAMP = 16,
    BRIDGE = 17,
    LIGHT = 18,
    BLOCK = 19,
    OTHER = 99,
}

/**
 * BlockType definition
 */
export interface BlockType {
  /**
   * Unique block type ID
   */
  id: string;

  type?: BlockTypeType;

  title?: string;

  /**
   * Initial status for new block instances
   * @default 0 (BlockStatus.DEFAULT)
   */
  initialStatus?: string; // javaType: String

  /**
   * Detailed description of the block type to be used by AI systems.
   */
  description?: string;

  /**
   * Modifiers map: status → BlockModifier
   *
   * Defines visual and behavioral properties for each status.
   * Status 'default' (legacy: 0) must always be present.
   *
   * @example
   * modifiers: {
   *   default: { visibility: { shape: Shape.CUBE }, ... },  // default
   *   open: { visibility: { shape: Shape.CUBE }, ... },  // open
   *   closed: { visibility: { shape: Shape.CUBE }, ... }   // closed
   * }
   */
  modifiers: Record<string, BlockModifier>; // javaType: java.util.Map<String,BlockModifier>
}
