/**
 * ClientBlockType - Optimized block type for client-side rendering
 *
 * Important properties extracted from BlockType for fast access.
 * Values are dynamically built from BlockType, Block, and status.
 * Updated when status changes.
 *
 * No optional values unless explicitly meaningful.
 */

import type { BlockType, Shape, Vector3 } from '@nimbus/shared';

/**
 * Facing direction
 */
export enum Facing {
  NORTH = 0,
  EAST = 1,
  SOUTH = 2,
  WEST = 3,
  UP = 4,
  DOWN = 5,
}

/**
 * Client-side optimized BlockType
 */
export interface ClientBlockType {
  /** Original BlockType reference */
  type: BlockType;

  /** Arbitrary attributes map */
  attributes: Record<string, any>;

  /** Resolved shape */
  shape: Shape;

  /** Asset paths */
  assets: string[];

  /** Asset textures */
  assetTextures: any[]; // TODO: Define texture type

  /** Geometry offsets (shape-dependent, see Offsets type) */
  offsets: number[];

  /** Scaling XYZ */
  scalingXYZ: Vector3;

  /** Rotation XY (Z rotation less common) */
  rotationXY: { x: number; y: number };

  /** Facing direction */
  facing: Facing;

  /** Tint color */
  color?: string;

  // Additional fast-access properties can be added as needed
}
