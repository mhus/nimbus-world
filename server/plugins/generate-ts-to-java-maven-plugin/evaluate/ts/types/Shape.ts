/**
 * Shape - Defines the visual shape/geometry of a block
 *
 * NEVER CHANGE EXISTING VALUES - only add new ones at the end.
 */

export enum Shape {
  /** Not visible but can have modifiers (effects, etc.) */
  INVISIBLE = 0,

  /** Standard cube */
  CUBE = 1,

  /** Cross shape (two intersecting planes) */
  CROSS = 2,

  /** Hash/grid pattern */
  HASH = 3,

  /** Custom 3D model */
  MODEL = 4,

  /** Glass cube */
  GLASS = 5,

  /** Flat glass pane */
  GLASS_FLAT = 6,

  /** Flat surface */
  FLAT = 7,

  /** Sphere */
  SPHERE = 8,

  /** Cylinder/pillar */
  CYLINDER = 9,

  /** Rounded cube */
  ROUND_CUBE = 10,

  /** Steps */
  STEPS = 11,

  /** Stairs */
  STAIR = 12,

  /** Billboard (always faces camera) */
  BILLBOARD = 13,

  /** Sprite */
  SPRITE = 14,

  /** Flame effect */
  FLAME = 15,

  /** Ocean water (flat) */
  OCEAN = 16,

  /** Ocean coast variation */
  OCEAN_COAST = 17,

  /** Ocean maelstrom variation */
  OCEAN_MAELSTROM = 18,

  /** River water (flat, directional) */
  RIVER = 19,

  /** River waterfall */
  RIVER_WATERFALL = 20,

  /** River waterfall with whirlpool */
  RIVER_WATERFALL_WHIRLPOOL = 21,

  /** Water cube */
  WATER = 22,

  /** Lava */
  LAVA = 23,

  /** Fog */
  FOG = 24,

  /** Thin instances (grass, leaves - high performance with Y-axis billboard) */
  THIN_INSTANCES = 25,

  /** Wall (hollow cube with inner faces) */
  WALL = 26,

  /** Flipbox (animated sprite sheet, horizontal face only) */
  FLIPBOX = 27,

  /** Item billboard (Y-axis locked, optimized for pickable items) */
  ITEM = 28,
}

/**
 * Human-readable shape names
 */
export const ShapeNames: Record<Shape, string> = {
  [Shape.INVISIBLE]: 'invisible',
  [Shape.CUBE]: 'cube',
  [Shape.CROSS]: 'cross',
  [Shape.HASH]: 'hash',
  [Shape.MODEL]: 'model',
  [Shape.GLASS]: 'glass',
  [Shape.GLASS_FLAT]: 'glass_flat',
  [Shape.FLAT]: 'flat',
  [Shape.SPHERE]: 'sphere',
  [Shape.CYLINDER]: 'cylinder',
  [Shape.ROUND_CUBE]: 'round_cube',
  [Shape.STEPS]: 'steps',
  [Shape.STAIR]: 'stair',
  [Shape.BILLBOARD]: 'billboard',
  [Shape.SPRITE]: 'sprite',
  [Shape.FLAME]: 'flame',
  [Shape.FLIPBOX]: 'flipbox',
  [Shape.OCEAN]: 'ocean',
  [Shape.OCEAN_COAST]: 'ocean_coast',
  [Shape.OCEAN_MAELSTROM]: 'ocean_maelstrom',
  [Shape.RIVER]: 'river',
  [Shape.RIVER_WATERFALL]: 'river_waterfall',
  [Shape.RIVER_WATERFALL_WHIRLPOOL]: 'river_waterfall_whirlpool',
  [Shape.WATER]: 'water',
  [Shape.LAVA]: 'lava',
  [Shape.FOG]: 'fog',
  [Shape.THIN_INSTANCES]: 'thin_instances',
  [Shape.WALL]: 'wall',
  [Shape.ITEM]: 'item',
};
