/**
 * Backdrop - Dynamic pseudo walls at chunk boundaries
 *
 * Backdrops are rendered dynamically at the edges of the visible/loaded chunk area.
 * They are NOT stored in chunk data, but calculated based on which chunks are loaded.
 *
 * Used for:
 * - Preventing sun from shining into tunnels
 * - Far-away rendering with alpha fading at the edge of the visible world
 */

/**
 * Backdrop configuration
 *
 * Defines visual properties for a backdrop segment
 */
export interface Backdrop {
  /** Type of backdrop rendering */
  type?: 'none' | 'texture' | 'solid' | 'fog' | 'fadeout';

  /** Backdrop ID - loads backdrop type from server (e.g., "fog1", "stone") */
  id?: string;

  /** Left position - local X/Z coordinate (0-16), default 0 */
  left?: number; // javaType: byte

  /** Width of the backdrop segment (0-16), default 16 */
  width?: number; // javaType: byte

  /** Base Y coordinate (bottom) - if not provided, use groundLevel at this edge */
  yBase?: number; // javaType: int

  /** Height of the backdrop - relative to yBase, default 60 */
  height?: number; // javaType: int

  /** Depth of the backdrop in blocks - if 0 or undefined: plane, if > 0: box/cube */
  depth?: number; // javaType: int

  /** Texture path (e.g., "textures/backdrop/hills.png") */
  texture?: string;

  /** Noise texture path for organic fog appearance (e.g., "textures/noise/perlin.png") */
  noiseTexture?: string;

  /** Color tint (hex string like "#808080") */
  color?: string;

  /** Alpha transparency (0-1) */
  alpha?: number;

  /** Alpha blending mode */
  alphaMode?: number; // javaType: byte
}

/**
 * Backdrop direction enum
 */
export enum BackdropDirection {
  NORTH = 'north',
  EAST = 'east',
  SOUTH = 'south',
  WEST = 'west',
}

/**
 * Backdrop position - identifies where a backdrop should be rendered
 * No Usage ?
 */
export interface BackdropPosition {
  /** Chunk X coordinate where backdrop is needed */
  cx: number; // javaType: int

  /** Chunk Z coordinate where backdrop is needed */
  cz: number; // javaType: int

  /** Which direction(s) need backdrops at this position */
  directions: BackdropDirection[];
}
