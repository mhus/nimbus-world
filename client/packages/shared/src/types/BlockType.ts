/**
 * BlockType - Definition/Template for a block type
 *
 * This is the registry definition that defines what a block type is.
 * BlockType instances in the world only store the BlockType ID.
 */

import type { BlockModifier } from './BlockModifier';

/**
 * Block status values
 * 0 = default status
 * 1-9 = standard states (open, closed, locked, destroyed, etc.)
 * 10-17 = seasonal states
 * 100+ = custom world-specific states
 */
export enum BlockStatus {
  DEFAULT = 0,
  OPEN = 1,
  CLOSED = 2,
  LOCKED = 3,
  DESTROYED = 5,

  // Seasonal states
  WINTER = 10,
  SPRING = 11,
  SUMMER = 12,
  AUTUMN = 13,

  // Custom states start at 100
  CUSTOM_START = 100,
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
  initialStatus?: number; // javaType: int

  /**
   * Detailed description of the block type to be used by AI systems.
   */
  description?: string;

  /**
   * Modifiers map: status → BlockModifier
   *
   * Defines visual and behavioral properties for each status.
   * Status 0 (DEFAULT) should always be present.
   *
   * @example
   * modifiers: {
   *   0: { visibility: { shape: Shape.CUBE }, ... },  // default
   *   1: { visibility: { shape: Shape.CUBE }, ... },  // open
   *   2: { visibility: { shape: Shape.CUBE }, ... }   // closed
   * }
   */
  modifiers: Record<number, BlockModifier>; // javaType: java.util.Map<Integer,BlockModifier>
}
