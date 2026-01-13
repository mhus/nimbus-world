/**
 * BlockModifier - Visual and behavioral properties for blocks
 *
 * All parameters are optional to minimize data transmission and storage.
 * Parameter names may be shortened or replaced with numbers to reduce data size.
 *
 * Rendering order: Offsets → Scale → Rotation
 */

import type { Shape } from './Shape';
import type { Vector3 } from './Vector3';
import type { FaceVisibility } from './Block';

/**
 * Texture mapping keys
 */
export enum TextureKey {
  ALL = 0,
  TOP = 1,
  BOTTOM = 2,
  LEFT = 3,
  RIGHT = 4,
  FRONT = 5,
  BACK = 6,
  SIDE = 7,
  DIFFUSE = 8,
  DISTORTION = 9,
  OPACITY = 10,
  WALL = 11,
  // Inside textures (for rendering interior faces of blocks)
  INSIDE_ALL = 20,
  INSIDE_TOP = 21,
  INSIDE_BOTTOM = 22,
  INSIDE_LEFT = 23,
  INSIDE_RIGHT = 24,
  INSIDE_FRONT = 25,
  INSIDE_BACK = 26,
  INSIDE_SIDE = 27,
  // 100+ reserved for shape-specific textures
}

/**
 * Human-readable texture key names
 */
export const TextureKeyNames: Record<TextureKey, string> = {
  [TextureKey.ALL]: 'all',
  [TextureKey.TOP]: 'top',
  [TextureKey.BOTTOM]: 'bottom',
  [TextureKey.LEFT]: 'left',
  [TextureKey.RIGHT]: 'right',
  [TextureKey.FRONT]: 'front',
  [TextureKey.BACK]: 'back',
  [TextureKey.SIDE]: 'side',
  [TextureKey.DIFFUSE]: 'diffuse',
  [TextureKey.DISTORTION]: 'distortion',
  [TextureKey.OPACITY]: 'opacity',
  [TextureKey.WALL]: 'wall',
  [TextureKey.INSIDE_ALL]: 'inside_all',
  [TextureKey.INSIDE_TOP]: 'inside_top',
  [TextureKey.INSIDE_BOTTOM]: 'inside_bottom',
  [TextureKey.INSIDE_LEFT]: 'inside_left',
  [TextureKey.INSIDE_RIGHT]: 'inside_right',
  [TextureKey.INSIDE_FRONT]: 'inside_front',
  [TextureKey.INSIDE_BACK]: 'inside_back',
  [TextureKey.INSIDE_SIDE]: 'inside_side',
};

/**
 * Texture sampling mode
 */
export enum SamplingMode {
  NEAREST = 0,
  LINEAR = 1,
  MIPMAP = 2,
}

/**
 * Texture transparency mode
 *
 * Combines Babylon.js Material.transparencyMode (rendering method)
 * with Texture alpha source (hasAlpha vs getAlphaFromRGB):
 *
 * - NONE: Opaque, no transparency
 * - ALPHA_TEST: Sharp edges, alpha from texture alpha channel (hasAlpha)
 * - ALPHA_BLEND: Smooth transparency, alpha from texture alpha channel (hasAlpha)
 * - ALPHA_TEST_FROM_RGB: Sharp edges, alpha derived from RGB brightness (getAlphaFromRGB)
 * - ALPHA_BLEND_FROM_RGB: Smooth transparency, alpha derived from RGB brightness (getAlphaFromRGB)
 * - ALPHA_TESTANDBLEND: Combined test+blend, alpha from texture alpha channel (hasAlpha)
 * - ALPHA_TESTANDBLEND_FROM_RGB: Combined test+blend, alpha derived from RGB brightness (getAlphaFromRGB)
 */
export enum TransparencyMode {
  NONE = 0,
  ALPHA_TEST = 1,
  ALPHA_BLEND = 2,
  ALPHA_TEST_FROM_RGB = 3,
  ALPHA_BLEND_FROM_RGB = 4,
  ALPHA_TESTANDBLEND = 5,
  ALPHA_TESTANDBLEND_FROM_RGB = 6,
}

/**
 * Texture wrap mode
 * Controls texture coordinate wrapping behavior at boundaries
 */
export enum WrapMode {
  /** Clamp coordinates to [0, 1] range */
  CLAMP = 0,
  /** Repeat texture infinitely (tiling) */
  REPEAT = 1,
  /** Mirror texture at each repeat */
  MIRROR = 2,
}

/**
 * Direction flags (bitfield)
 */
export enum Direction {
  NORTH = 1 << 0,  // 0b000001 = 1
  SOUTH = 1 << 1,  // 0b000010 = 2
  EAST = 1 << 2,   // 0b000100 = 4
  WEST = 1 << 3,   // 0b001000 = 8
  UP = 1 << 4,     // 0b010000 = 16
  DOWN = 1 << 5,   // 0b100000 = 32
}

/**
 * Helper utilities for Direction bitfield operations
 */
export namespace DirectionHelper {
  /**
   * Check if a direction is set in bitfield
   * @param value Direction bitfield value
   * @param dir Direction to check
   * @returns True if direction is set
   */
  export function hasDirection(value: number, dir: Direction): boolean {
    return (value & dir) !== 0;
  }

  /**
   * Add direction to bitfield
   * @param value Current bitfield value
   * @param dir Direction to add
   * @returns Updated bitfield value
   */
  export function addDirection(value: number, dir: Direction): number {
    return value | dir;
  }

  /**
   * Remove direction from bitfield
   * @param value Current bitfield value
   * @param dir Direction to remove
   * @returns Updated bitfield value
   */
  export function removeDirection(value: number, dir: Direction): number {
    return value & ~dir;
  }

  /**
   * Toggle direction in bitfield
   * @param value Current bitfield value
   * @param dir Direction to toggle
   * @returns Updated bitfield value
   */
  export function toggleDirection(value: number, dir: Direction): number {
    return value ^ dir;
  }

  /**
   * Get all directions in bitfield
   * @param value Direction bitfield value
   * @returns Array of direction names
   */
  export function getDirections(value: number): string[] {
    const directions: string[] = [];
    if (hasDirection(value, Direction.NORTH)) directions.push('north');
    if (hasDirection(value, Direction.SOUTH)) directions.push('south');
    if (hasDirection(value, Direction.EAST)) directions.push('east');
    if (hasDirection(value, Direction.WEST)) directions.push('west');
    if (hasDirection(value, Direction.UP)) directions.push('up');
    if (hasDirection(value, Direction.DOWN)) directions.push('down');
    return directions;
  }

  /**
   * Count number of directions set
   * @param value Direction bitfield value
   * @returns Number of directions (0-6)
   */
  export function countDirections(value: number): number {
    let count = 0;
    let bits = value & 0b00111111; // Mask to 6 bits
    while (bits) {
      count += bits & 1;
      bits >>= 1;
    }
    return count;
  }

  /**
   * Create direction bitfield from direction names
   * @param directions Array of direction names
   * @returns Direction bitfield value
   */
  export function fromDirections(directions: string[]): number {
    let value = 0;
    directions.forEach((dir) => {
      switch (dir.toLowerCase()) {
        case 'north':
          value = addDirection(value, Direction.NORTH);
          break;
        case 'south':
          value = addDirection(value, Direction.SOUTH);
          break;
        case 'east':
          value = addDirection(value, Direction.EAST);
          break;
        case 'west':
          value = addDirection(value, Direction.WEST);
          break;
        case 'up':
          value = addDirection(value, Direction.UP);
          break;
        case 'down':
          value = addDirection(value, Direction.DOWN);
          break;
      }
    });
    return value;
  }

  /**
   * Check if any horizontal direction is set
   * @param value Direction bitfield value
   * @returns True if north, south, east, or west is set
   */
  export function hasHorizontalDirection(value: number): boolean {
    return (
      hasDirection(value, Direction.NORTH) ||
      hasDirection(value, Direction.SOUTH) ||
      hasDirection(value, Direction.EAST) ||
      hasDirection(value, Direction.WEST)
    );
  }

  /**
   * Check if any vertical direction is set
   * @param value Direction bitfield value
   * @returns True if up or down is set
   */
  export function hasVerticalDirection(value: number): boolean {
    return hasDirection(value, Direction.UP) || hasDirection(value, Direction.DOWN);
  }

  /**
   * Get opposite direction
   * @param dir Direction
   * @returns Opposite direction
   */
  export function getOpposite(dir: Direction): Direction {
    switch (dir) {
      case Direction.NORTH:
        return Direction.SOUTH;
      case Direction.SOUTH:
        return Direction.NORTH;
      case Direction.EAST:
        return Direction.WEST;
      case Direction.WEST:
        return Direction.EAST;
      case Direction.UP:
        return Direction.DOWN;
      case Direction.DOWN:
        return Direction.UP;
      default:
        return dir;
    }
  }

  /**
   * Convert to string representation for debugging
   * @param value Direction bitfield value
   * @returns String representation
   */
  export function toString(value: number): string {
    const directions = getDirections(value);
    return `Direction(${directions.join(' | ')})`;
  }
}


/**
 * UV mapping coordinates
 *
 * This interface serves two purposes:
 * 1. **Atlas Extraction**: x, y, w, h define which region of a source image to extract
 * 2. **Mesh Transformation**: us, vs, uo, vo, wu, wv, uc, vc define how the texture is displayed on the mesh
 *
 * @example Atlas extraction from tileset
 * ```typescript
 * uvMapping: { x: 32, y: 48, w: 16, h: 16 }
 * ```
 *
 * @example Texture tiling and offset on mesh
 * ```typescript
 * uvMapping: {
 *   x: 0, y: 0, w: 16, h: 16,      // Extract full 16x16 texture
 *   uScale: 2.0, vScale: 2.0,      // Repeat 2x2 on mesh
 *   uOffset: 0.25, vOffset: 0.0    // Offset by 25% horizontally
 * }
 * ```
 */
export interface UVMapping {
  // ========================================
  // Atlas Extraction (Source Image Region)
  // ========================================

  /** X position in source image (pixels) */
  readonly x: number;

  /** Y position in source image (pixels) */
  readonly y: number;

  /** Width of extracted region (pixels) */
  readonly w: number;

  /** Height of extracted region (pixels) */
  readonly h: number;

  // ========================================
  // Mesh UV Transformation (Display)
  // Maps to Babylon.js Texture properties
  // ========================================

  /** Texture tiling in U direction (Babylon.js: texture.uScale)
   * @default 1.0
   * @example 2.0 = repeat texture 2 times horizontally
   */
  readonly uScale?: number;

  /** Texture tiling in V direction (Babylon.js: texture.vScale)
   * @default 1.0
   * @example 2.0 = repeat texture 2 times vertically
   */
  readonly vScale?: number;

  /** Texture offset in U direction (Babylon.js: texture.uOffset)
   * @default 0.0
   * @range [0.0, 1.0]
   * @example 0.25 = shift texture 25% to the right
   */
  readonly uOffset?: number;

  /** Texture offset in V direction (Babylon.js: texture.vOffset)
   * @default 0.0
   * @range [0.0, 1.0]
   * @example 0.5 = shift texture 50% down
   */
  readonly vOffset?: number;

  /** Wrap mode for U coordinate (Babylon.js: texture.wrapU)
   * @default 1 (REPEAT)
   * @see WrapMode
   */
  readonly wrapU?: number;

  /** Wrap mode for V coordinate (Babylon.js: texture.wrapV)
   * @default 1 (REPEAT)
   * @see WrapMode
   */
  readonly wrapV?: number;

  /** Rotation center for U axis (Babylon.js: texture.uRotationCenter)
   * @default 0.5
   * @range [0.0, 1.0]
   * @example 0.5 = center, 0.0 = left edge, 1.0 = right edge
   */
  readonly uRotationCenter?: number;

  /** Rotation center for V axis (Babylon.js: texture.vRotationCenter)
   * @default 0.5
   * @range [0.0, 1.0]
   * @example 0.5 = center, 0.0 = top edge, 1.0 = bottom edge
   */
  readonly vRotationCenter?: number;

  /** Rotation angle around W axis in radians (Babylon.js: texture.wAng)
   * @default 0
   * @example Math.PI / 4 = 45° rotation
   * @example Math.PI / 2 = 90° rotation
   */
  readonly wAng?: number;

  /** Rotation angle around U axis in radians (Babylon.js: texture.uAng)
   * @default 0
   */
  readonly uAng?: number;

  /** Rotation angle around V axis in radians (Babylon.js: texture.vAng)
   * @default 0
   */
  readonly vAng?: number;
}

/**
 * Texture definition
 */
export interface TextureDefinition {
  /** Path to texture file */
  path: string;

  /** UV mapping coordinates */
  uvMapping?: UVMapping;

  /** Sampling mode */
  samplingMode?: SamplingMode;

  /** Transparency mode */
  transparencyMode?: TransparencyMode;

  /** Opacity (0.0 - 1.0) */
  opacity?: number;

  /** Effect type (NONE, FLIPBOX, WIND)
   * Default is the effect from the VisibilityModifier
   * If set, overrides the VisibilityModifier.effect for this texture
   */
  effect?: BlockEffect;

  /** Effect Parameters
   * Format depends on effect type:
   * - FLIPBOX: "frameCount,delayMs" (e.g., "4,100")
   * Default is the effectParameters from the VisibilityModifier
   * If set, overrides the VisibilityModifier.effectParameters for this texture
   */
  effectParameters?: string;

  /** Tint color */
  color?: string;

  /**  Back Face Culling */
  backFaceCulling?: boolean;

}

/**
 * Block effect types
 */
export enum BlockEffect {
  /** No effect */
  NONE = 0,

  /** Wind effect */
  WIND = 2,
}


/**
 * Visibility properties
 */
export interface VisibilityModifier {
  /** Shape type */
  shape?: Shape;

  /** Effect type (NONE, FLIPBOX, WIND)
   * Applies to all textures by default
   * Can be overridden per texture in TextureDefinition.effect
   */
  effect?: BlockEffect;

  /** Effect-specific parameters
   * Format depends on effect type:
   * - FLIPBOX: "frameCount,delayMs" (e.g., "4,100")
   * Applies to all textures by default
   * Can be overridden per texture in TextureDefinition.effectParameters
   */
  effectParameters?: string;

  /**
   * Corner offsets (8 corners × 3 axes = 24 values)
   * Values are numbers (supports both integers and floats)
   */
  offsets?: number[];

  /** Scaling factor X */
  scalingX?: number;

  /** Scaling factor Y */
  scalingY?: number;

  /** Scaling factor Z */
  scalingZ?: number;

  /** Rotation X (degrees) */
  rotationX?: number;

  /** Rotation Y (degrees) */
  rotationY?: number;

  /** Path to model file (for shape=MODEL) */
  path?: string;

  /** Texture definitions (key = TextureKey) */
  textures?: Record<number, TextureDefinition | string>;

  /**
   * Face visibility flags (1 byte bitfield)
   * Determines which faces are visible or if it's auto-calculated
   * Priority: VisibilityModifier.faceVisibility > Block.faceVisibility > default (all visible)
   */
  faceVisibility?: FaceVisibility;
}

/**
 * Wind properties
 */
export interface WindModifier {
  /** How much the block is affected by wind (leaf-like behavior) */
  leafiness?: number;

  /** Stability/rigidity */
  stability?: number;

  /** Upper lever arm */
  leverUp?: number;

  /** Lower lever arm */
  leverDown?: number;
}

/**
 * Illumination properties
 */
export interface IlluminationModifier {
  /** Light color */
  color?: string;

  /** Light strength */
  strength?: number;
}

/**
 * Physics properties
 */
export interface PhysicsModifier {
  /** Solid collision */
  solid?: boolean;

  /** Movement resistance - speed reduction if on(solid)/in(!solid) the block, default: 0 */
  resistance?: number;

  /** Climbable with resistance factor - if move forward will move upward with resistance of this - 0 or undefined means disabled, default; 0 */
  climbable?: number;

  /** Auto climb one (this) block, default: false */
  autoClimbable?: boolean;

  /** Auto-move velocity when standing on(solid)/in(!solid) block - moves the entity with speed, default: 0 */
  autoMove?: Vector3;

  /** Auto-orientation Y axis when standing on(solid)/in(!solid) block - rotates the entity to this Y angle, default: undefined (disabled) */
  autoOrientationY?: number;

  /** Interactive block - player can send a interact command for this block to the server, default: false */
  interactive?: boolean;

  /** Send collision event to server when player collides with this block, default: false */
  collisionEvent?: boolean;

  /** Passable from specific directions - Controls one-way passage through blocks
   * - If block is solid: Acts as one-way block - can enter from specified directions, block normal movement in other directions
   * - If block is not solid: Acts as wall on edges - can walk through but not exit where passableFrom is not set
   * Default: none (0) */
  passableFrom?: Direction; // javaType: Integer

  /** Auto-jump when walking into the block - like fence gate, default: false */
  autoJump?: boolean;

  /**
   * Corner heights for sloped/ramped blocks - defines Y-offset adjustments for the four top corners.
   * Array of 4 numbers representing height adjustments (relative to block Y position).
   *
   * Corner order (counter-clockwise, top view):
   * [0] = North-West (-X, -Z)
   * [1] = North-East (+X, -Z)
   * [2] = South-East (+X, +Z)
   * [3] = South-West (-X, +Z)
   *
   * Values:
   * - 0.0 = standard height (top of block)
   * - negative = lower than standard
   * - positive = higher than standard
   *
   * If undefined or not exactly 4 values: treated as [0, 0, 0, 0] (flat surface)
   *
   * Effects:
   * - Player stands on interpolated surface height
   * - Enables sliding on slopes (influenced by resistance)
   * - Automatic slope detection for movement
   *
   * Priority: Block.cornerHeights > PhysicsModifier.cornerHeights > auto-derived from offsets (if shape == CUBE)
   *
   * Example: Ramp rising from North to South
   * cornerHeights: [-0.5, -0.5, 0.0, 0.0]
   */
  cornerHeights?: [number, number, number, number];
}

/**
 * Sky effect properties
 */
export interface SkyEffect {
  /** Sky intensity */
  intensity?: number;

  /** Sky color */
  color?: string;

  /** Wind/weather effect */
  wind?: any; // TODO: Define weather structure
}

/**
 * Effects properties
 */
export interface EffectsModifier {
  /** Force ego/first-person view */
  forceEgoView?: boolean;

  /** Sky effect */
  sky?: SkyEffect;
}

/**
 * Audio type enumeration
 */
export enum AudioType {
  /** Step sounds when walking on block */
  STEPS = 'steps',
  /** Permanent ambient sound that plays continuously when block is visible */
  PERMANENT = 'permanent',
  /** Collision sound when player collides with block */
  COLLISION = 'collision',
}

/**
 * Audio definition
 */
export interface AudioDefinition {
  /** Audio type - AudioType enum for blocks, or custom string for entities */
  type: AudioType | string;

  /** Path to audio file */
  path: string;

  /** Volume (0.0 - 1.0) */
  volume: number;

  /** Loop audio playback */
  loop?: boolean;

  /** Whether audio is enabled */
  enabled: boolean;

  /** Maximum hearing distance in blocks (default: 15) */
  maxDistance?: number;
}

/**
 * Audio properties - Array of audio definitions
 */
export type AudioModifier = AudioDefinition[];

/**
 * BlockModifier - Complete modifier definition
 */
export interface BlockModifier {
  /** Visibility properties */
  visibility?: VisibilityModifier;

  /** Wind properties */
  wind?: WindModifier;

  /** Illumination properties */
  illumination?: IlluminationModifier;

  /** Physics properties */
  physics?: PhysicsModifier;

  /** Effects */
  effects?: EffectsModifier;

  /** Audio properties */
  audio?: AudioModifier;
}

/**
 * Texture helper functions for efficient texture access
 */
export namespace TextureHelper {
  /**
   * Convert TextureKey enum to numeric index
   * @param key TextureKey enum value
   * @returns Numeric index
   */
  export function textureKeyToIndex(key: TextureKey): number {
    return key as number;
  }

  /**
   * Convert numeric index to TextureKey enum
   * @param index Numeric index
   * @returns TextureKey enum value
   */
  export function indexToTextureKey(index: number): TextureKey {
    return index as TextureKey;
  }

  /**
   * Get texture from visibility modifier
   * @param visibility Visibility modifier
   * @param key Texture key
   * @returns Texture definition, string, or undefined
   */
  export function getTexture(
    visibility: VisibilityModifier,
    key: TextureKey
  ): TextureDefinition | string | undefined {
    if (!visibility.textures) {
      return undefined;
    }
    const index = textureKeyToIndex(key);
    return visibility.textures[index];
  }

  /**
   * Set texture in visibility modifier
   * @param visibility Visibility modifier
   * @param key Texture key
   * @param texture Texture definition or path string
   */
  export function setTexture(
    visibility: VisibilityModifier,
    key: TextureKey,
    texture: TextureDefinition | string
  ): void {
    if (!visibility.textures) {
      visibility.textures = {};
    }
    const index = textureKeyToIndex(key);
    visibility.textures[index] = texture;
  }

  /**
   * Remove texture from visibility modifier
   * @param visibility Visibility modifier
   * @param key Texture key
   */
  export function removeTexture(
    visibility: VisibilityModifier,
    key: TextureKey
  ): void {
    if (!visibility.textures) {
      return;
    }
    const index = textureKeyToIndex(key);
    delete visibility.textures[index];
  }

  /**
   * Check if texture exists
   * @param visibility Visibility modifier
   * @param key Texture key
   * @returns True if texture exists
   */
  export function hasTexture(
    visibility: VisibilityModifier,
    key: TextureKey
  ): boolean {
    if (!visibility.textures) {
      return false;
    }
    const index = textureKeyToIndex(key);
    return visibility.textures[index] !== undefined;
  }

  /**
   * Get all texture keys that have textures defined
   * @param visibility Visibility modifier
   * @returns Array of TextureKey values
   */
  export function getDefinedTextureKeys(
    visibility: VisibilityModifier
  ): TextureKey[] {
    if (!visibility.textures) {
      return [];
    }
    return Object.keys(visibility.textures)
      .map((key) => parseInt(key, 10))
      .filter((key) => !isNaN(key))
      .map((key) => indexToTextureKey(key));
  }

  /**
   * Get texture count
   * @param visibility Visibility modifier
   * @returns Number of defined textures
   */
  export function getTextureCount(visibility: VisibilityModifier): number {
    if (!visibility.textures) {
      return 0;
    }
    return Object.keys(visibility.textures).length;
  }

  /**
   * Clone textures map
   * @param visibility Visibility modifier
   * @returns Cloned textures map
   */
  export function cloneTextures(
    visibility: VisibilityModifier
  ): Record<number, TextureDefinition | string> | undefined {
    if (!visibility.textures) {
      return undefined;
    }
    const cloned: Record<number, TextureDefinition | string> = {};
    for (const [key, texture] of Object.entries(visibility.textures)) {
      const index = parseInt(key, 10);
      // If texture is a string, just copy it; if it's an object, spread it
      cloned[index] = typeof texture === 'string' ? texture : { ...texture };
    }
    return cloned;
  }

  /**
   * Get texture by name (for convenience)
   * @param visibility Visibility modifier
   * @param name Texture name ('top', 'bottom', etc.)
   * @returns Texture definition, string, or undefined
   */
  export function getTextureByName(
    visibility: VisibilityModifier,
    name: string
  ): TextureDefinition | string | undefined {
    const key = getTextureKeyByName(name);
    if (key === undefined) {
      return undefined;
    }
    return getTexture(visibility, key);
  }

  /**
   * Set texture by name (for convenience)
   * @param visibility Visibility modifier
   * @param name Texture name ('top', 'bottom', etc.)
   * @param texture Texture definition or path string
   */
  export function setTextureByName(
    visibility: VisibilityModifier,
    name: string,
    texture: TextureDefinition | string
  ): void {
    const key = getTextureKeyByName(name);
    if (key !== undefined) {
      setTexture(visibility, key, texture);
    }
  }

  /**
   * Get TextureKey by name
   * @param name Texture name
   * @returns TextureKey or undefined
   */
  export function getTextureKeyByName(name: string): TextureKey | undefined {
    const normalized = name.toLowerCase();
    for (const [key, keyName] of Object.entries(TextureKeyNames)) {
      if (keyName === normalized) {
        return parseInt(key, 10) as TextureKey;
      }
    }
    return undefined;
  }

  /**
   * Get name for TextureKey
   * @param key TextureKey
   * @returns Texture name
   */
  export function getTextureName(key: TextureKey): string {
    return TextureKeyNames[key] ?? 'unknown';
  }

  /**
   * Create simple texture definition
   * @param path Texture path
   * @returns Simple texture definition
   */
  export function createTexture(path: string): TextureDefinition {
    return { path };
  }

  /**
   * Create texture definition with UV mapping
   * @param path Texture path
   * @param uvMapping UV coordinates
   * @returns Texture definition with UV mapping
   */
  export function createTextureWithUV(
    path: string,
    uvMapping: UVMapping
  ): TextureDefinition {
    return { path, uvMapping };
  }

  /**
   * Normalize texture to TextureDefinition
   * Converts string path to TextureDefinition object
   * @param texture Texture definition or path string
   * @returns TextureDefinition object
   */
  export function normalizeTexture(texture: TextureDefinition | string): TextureDefinition {
    return typeof texture === 'string' ? { path: texture } : texture;
  }

  /**
   * Check if texture is a string (simple path)
   * @param texture Texture definition or path string
   * @returns True if texture is a string
   */
  export function isStringTexture(texture: TextureDefinition | string): texture is string {
    return typeof texture === 'string';
  }

  /**
   * Set all cube face textures to same texture
   * @param visibility Visibility modifier
   * @param texture Texture definition or path string
   */
  export function setAllFaces(
    visibility: VisibilityModifier,
    texture: TextureDefinition | string
  ): void {
    setTexture(visibility, TextureKey.TOP, texture);
    setTexture(visibility, TextureKey.BOTTOM, texture);
    setTexture(visibility, TextureKey.LEFT, texture);
    setTexture(visibility, TextureKey.RIGHT, texture);
    setTexture(visibility, TextureKey.FRONT, texture);
    setTexture(visibility, TextureKey.BACK, texture);
  }

  /**
   * Set side faces (left, right, front, back) to same texture
   * @param visibility Visibility modifier
   * @param texture Texture definition or path string
   */
  export function setSideFaces(
    visibility: VisibilityModifier,
    texture: TextureDefinition | string
  ): void {
    setTexture(visibility, TextureKey.LEFT, texture);
    setTexture(visibility, TextureKey.RIGHT, texture);
    setTexture(visibility, TextureKey.FRONT, texture);
    setTexture(visibility, TextureKey.BACK, texture);
  }
}
