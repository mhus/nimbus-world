/**
 * BlockContext - Block analysis structures for physics
 *
 * Defines all block-related context structures used by the physics system
 * to analyze the environment around entities.
 */

import type { ClientBlock } from '../../../types/ClientBlock';

/**
 * Block info with position and client block
 */
export interface BlockInfo {
  x: number;
  y: number;
  z: number;
  block: ClientBlock | null;
}

/**
 * Block context around player - Complete schema as per requirements
 * Contains all relevant blocks categorized by their relationship to player position
 */
export interface PlayerBlockContext {
  /** Blocks that player currently occupies (player's body space) */
  currentBlocks: {
    blocks: BlockInfo[];
    allNonSolid: boolean; // All blocks are non-solid or semi-solid
    hasSolid: boolean;
    passableFrom: number | undefined; // OR combined from all blocks
  };

  /** Blocks that player is entering (when moving over block boundary) */
  enteringBlocks: {
    blocks: BlockInfo[];
    allPassable: boolean; // All blocks allow entry
    hasSolid: boolean;
  };

  /** Blocks directly in front of player (in movement direction) */
  frontBlocks: {
    blocks: BlockInfo[];
    allPassable: boolean;
    hasSolid: boolean;
  };

  /** Blocks at player's feet (bottom of player, within footprint) */
  footBlocks: {
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasAutoRotationY: boolean;
    hasAutoMove: boolean;
    autoJump: number;
    autoOrientationY: number | undefined;
    autoMove: { x: number; y: number; z: number };
  };

  /** Blocks in front of player's feet (for climbing/slope detection) */
  footFrontBlocks: {
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasClimbable: boolean;
    maxClimbHeight: number; // Maximum height difference for auto-climb
    cornerHeights?: [number, number, number, number]; // For slopes
  };

  /** Blocks directly under player (for ground detection and gravity) */
  groundBlocks: {
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasGround: boolean;
    groundY: number; // -1 if no ground
    resistance: number; // Ground friction/resistance
    hasAutoMove: boolean;
    hasAutoRotationY: boolean;
    autoJump: number;
    autoMove: { x: number; y: number; z: number };
    autoOrientationY: number | undefined;
  };

  /** Blocks one level above ground, at feet level (for slope detection) */
  groundFootBlocks: {
    blocks: BlockInfo[];
    isSemiSolid: boolean; // Has cornerHeights or autoCornerHeights
    maxHeight: number; // Maximum corner height for slope sliding
    cornerHeights?: [number, number, number, number];
  };

  /** Blocks above player's head (for ceiling collision) */
  headBlocks: {
    blocks: BlockInfo[];
    hasSolid: boolean;
    maxY: number; // Maximum Y player can reach (block bottom)
  };
}
