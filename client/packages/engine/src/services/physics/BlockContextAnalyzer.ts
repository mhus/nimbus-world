/**
 * BlockContextAnalyzer - Analyzes blocks around entities
 *
 * Builds comprehensive block context with 8 categories:
 * - currentBlocks: Player's body space
 * - enteringBlocks: Blocks player is entering
 * - frontBlocks: Blocks in front
 * - footBlocks: Blocks at feet
 * - footFrontBlocks: Blocks in front of feet
 * - groundBlocks: Blocks under player
 * - groundFootBlocks: Blocks at feet level (for slopes)
 * - headBlocks: Blocks above head
 */

import { getLogger, Direction, DirectionHelper } from '@nimbus/shared';
import type { Vector3 } from '@babylonjs/core';
import type { PlayerBlockContext, BlockInfo, PhysicsEntity } from './types';
import type { ClientBlock } from '../../types/ClientBlock';
import type { ChunkService } from '../ChunkService';
import { SurfaceAnalyzer } from './SurfaceAnalyzer';

const logger = getLogger('BlockContextAnalyzer');

/**
 * Entity dimensions
 */
interface EntityDimensions {
  height: number;
  width: number;
  footprint: number;
}

/**
 * BlockContextAnalyzer - Analyzes surrounding blocks
 */
export class BlockContextAnalyzer {
  private surfaceAnalyzer: SurfaceAnalyzer;
  private contextCache: Map<string, { context: PlayerBlockContext; timestamp: number }> = new Map();
  private readonly cacheTimeout = 100; // ms

  constructor(private chunkService: ChunkService) {
    this.surfaceAnalyzer = new SurfaceAnalyzer(chunkService);
  }

  /**
   * Get block context for entity
   * Uses caching to avoid redundant calculations
   */
  getContext(entity: PhysicsEntity, dimensions: EntityDimensions): PlayerBlockContext {
    // Build cache key including block position
    const blockX = Math.floor(entity.position.x);
    const blockY = Math.floor(entity.position.y);
    const blockZ = Math.floor(entity.position.z);
    const cacheKey = `${entity.entityId}_${blockX}_${blockY}_${blockZ}`;

    // Check cache
    const cached = this.contextCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < this.cacheTimeout) {
      return cached.context;
    }

    // Build new context
    const context = this.buildContext(entity, dimensions);

    // Cache result
    this.contextCache.set(cacheKey, {
      context,
      timestamp: Date.now(),
    });

    return context;
  }

  /**
   * Invalidate cache for entity
   */
  invalidateCache(entityId: string): void {
    this.contextCache.delete(entityId);
  }

  /**
   * Build complete block context
   */
  private buildContext(entity: PhysicsEntity, dimensions: EntityDimensions): PlayerBlockContext {
    const pos = entity.position;
    const lastPos = entity.lastBlockPos;
    const yaw = entity.rotation.y;

    // Get footprint positions (4 corners based on footprint radius)
    // Use default footprint of 0.3 if not provided
    const footprint = dimensions.footprint ?? 0.3;

    if (dimensions.footprint === null || dimensions.footprint === undefined) {
      logger.warn('Footprint is null/undefined, using default 0.3', {
        entityId: entity.entityId,
        dimensions: dimensions
      });
    }

    const footprintPositions = this.getFootprintPositions(pos, footprint);

    // Get front direction from yaw
    const frontDir = this.getFrontDirection(yaw);
    const frontOffset = this.getDirectionOffset(frontDir);

    // Calculate Y levels
    const feetY = Math.floor(pos.y);
    const groundY = feetY - 1;
    const headY = feetY + Math.ceil(dimensions.height);

    // Detect block boundary crossing for entering blocks
    const currentBlockPos = { x: Math.floor(pos.x), y: feetY, z: Math.floor(pos.z) };
    const lastBlockPos = { x: Math.floor(lastPos.x), y: Math.floor(lastPos.y), z: Math.floor(lastPos.z) };
    const crossingBoundary =
      currentBlockPos.x !== lastBlockPos.x ||
      currentBlockPos.y !== lastBlockPos.y ||
      currentBlockPos.z !== lastBlockPos.z;

    // Build all 8 block categories
    const currentBlocks = this.analyzeCurrentBlocks(footprintPositions, feetY, dimensions.height);
    const enteringBlocks = this.analyzeEnteringBlocks(
      footprintPositions,
      feetY,
      dimensions.height,
      currentBlockPos,
      lastBlockPos,
      crossingBoundary
    );
    const frontBlocks = this.analyzeFrontBlocks(
      footprintPositions,
      feetY,
      dimensions.height,
      frontOffset
    );
    const footBlocks = this.analyzeFootBlocks(footprintPositions, feetY);
    const footFrontBlocks = this.analyzeFootFrontBlocks(
      footprintPositions,
      feetY,
      frontOffset
    );
    const groundBlocks = this.analyzeGroundBlocks(footprintPositions, groundY);
    const groundFootBlocks = this.analyzeGroundFootBlocks(footprintPositions, feetY);
    const headBlocks = this.analyzeHeadBlocks(footprintPositions, headY);

    return {
      currentBlocks,
      enteringBlocks,
      frontBlocks,
      footBlocks,
      footFrontBlocks,
      groundBlocks,
      groundFootBlocks,
      headBlocks,
    };
  }

  /**
   * Get footprint corner positions
   */
  private getFootprintPositions(
    pos: Vector3,
    footprint: number
  ): Array<{ x: number; z: number }> {
    const positions: Array<{ x: number; z: number }> = [];

    // Sample at 4 corners of footprint
    const offsets = [
      { x: -footprint, z: -footprint }, // NW
      { x: footprint, z: -footprint }, // NE
      { x: footprint, z: footprint }, // SE
      { x: -footprint, z: footprint }, // SW
    ];

    for (const offset of offsets) {
      positions.push({
        x: Math.floor(pos.x + offset.x),
        z: Math.floor(pos.z + offset.z),
      });
    }

    // Remove duplicates
    const unique = new Map<string, { x: number; z: number }>();
    for (const p of positions) {
      const key = `${p.x},${p.z}`;
      if (!unique.has(key)) {
        unique.set(key, p);
      }
    }

    return Array.from(unique.values());
  }

  /**
   * Get front direction from yaw angle
   */
  private getFrontDirection(yaw: number): Direction {
    // Convert yaw to Direction
    // Yaw 0 = North (-Z), 90 = East (+X), 180 = South (+Z), 270 = West (-X)
    const degrees = ((yaw * 180) / Math.PI + 360) % 360;

    if (degrees < 45 || degrees >= 315) return Direction.NORTH;
    if (degrees < 135) return Direction.EAST;
    if (degrees < 225) return Direction.SOUTH;
    return Direction.WEST;
  }

  /**
   * Get offset for direction
   */
  private getDirectionOffset(dir: Direction): { x: number; z: number } {
    switch (dir) {
      case Direction.NORTH:
        return { x: 0, z: -1 };
      case Direction.EAST:
        return { x: 1, z: 0 };
      case Direction.SOUTH:
        return { x: 0, z: 1 };
      case Direction.WEST:
        return { x: -1, z: 0 };
      default:
        return { x: 0, z: 0 };
    }
  }

  /**
   * Get block at position
   */
  private getBlock(x: number, y: number, z: number): BlockInfo {
    const block = this.chunkService.getBlockAt(x, y, z);
    return { x, y, z, block: block || null };
  }

  /**
   * Analyze current blocks (player body space)
   */
  private analyzeCurrentBlocks(
    footprint: Array<{ x: number; z: number }>,
    feetY: number,
    height: number
  ) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;
    let passableFrom: number | undefined = undefined;

    // Check all Y levels from feet to head
    const numLevels = Math.ceil(height);
    for (let dy = 0; dy < numLevels; dy++) {
      for (const pos of footprint) {
        const blockInfo = this.getBlock(pos.x, feetY + dy, pos.z);
        if (blockInfo.block) {
          blocks.push(blockInfo);

          const physics = blockInfo.block.currentModifier.physics;
          if (physics?.solid) {
            hasSolid = true;
          }
          if (physics?.passableFrom !== undefined) {
            passableFrom = passableFrom
              ? passableFrom | physics.passableFrom
              : physics.passableFrom;
          }
        }
      }
    }

    const allNonSolid = !hasSolid;

    return { blocks, allNonSolid, hasSolid, passableFrom };
  }

  /**
   * Analyze entering blocks (when moving over boundary)
   */
  private analyzeEnteringBlocks(
    footprint: Array<{ x: number; z: number }>,
    feetY: number,
    height: number,
    currentBlockPos: { x: number; y: number; z: number },
    lastBlockPos: { x: number; y: number; z: number },
    crossingBoundary: boolean
  ) {
    // If not crossing boundary, no entering blocks
    if (!crossingBoundary) {
      return {
        blocks: [],
        allPassable: true,
        hasSolid: false,
      };
    }

    const blocks: BlockInfo[] = [];
    let hasSolid = false;

    // Determine which direction we crossed
    const deltaX = currentBlockPos.x - lastBlockPos.x;
    const deltaY = currentBlockPos.y - lastBlockPos.y;
    const deltaZ = currentBlockPos.z - lastBlockPos.z;

    // Check new block(s) we entered in all Y levels
    const numLevels = Math.ceil(height);
    for (let dy = 0; dy < numLevels; dy++) {
      // If crossed in X direction
      if (deltaX !== 0) {
        for (const pos of footprint) {
          if (Math.floor(pos.x) === currentBlockPos.x) {
            const blockInfo = this.getBlock(pos.x, feetY + dy, pos.z);
            if (blockInfo.block) {
              blocks.push(blockInfo);
              if (blockInfo.block.currentModifier.physics?.solid) {
                hasSolid = true;
              }
            }
          }
        }
      }

      // If crossed in Z direction
      if (deltaZ !== 0) {
        for (const pos of footprint) {
          if (Math.floor(pos.z) === currentBlockPos.z) {
            const blockInfo = this.getBlock(pos.x, feetY + dy, pos.z);
            if (blockInfo.block) {
              blocks.push(blockInfo);
              if (blockInfo.block.currentModifier.physics?.solid) {
                hasSolid = true;
              }
            }
          }
        }
      }

      // If crossed in Y direction
      if (deltaY !== 0) {
        for (const pos of footprint) {
          const blockInfo = this.getBlock(pos.x, feetY + dy, pos.z);
          if (blockInfo.block) {
            blocks.push(blockInfo);
            if (blockInfo.block.currentModifier.physics?.solid) {
              hasSolid = true;
            }
          }
        }
      }
    }

    const allPassable = !hasSolid;

    return { blocks, allPassable, hasSolid };
  }

  /**
   * Analyze front blocks
   */
  private analyzeFrontBlocks(
    footprint: Array<{ x: number; z: number }>,
    feetY: number,
    height: number,
    frontOffset: { x: number; z: number }
  ) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;

    const numLevels = Math.ceil(height);
    for (let dy = 0; dy < numLevels; dy++) {
      for (const pos of footprint) {
        const blockInfo = this.getBlock(pos.x + frontOffset.x, feetY + dy, pos.z + frontOffset.z);
        if (blockInfo.block) {
          blocks.push(blockInfo);
          if (blockInfo.block.currentModifier.physics?.solid) {
            hasSolid = true;
          }
        }
      }
    }

    const allPassable = !hasSolid;

    return { blocks, allPassable, hasSolid };
  }

  /**
   * Analyze foot blocks (at player's feet)
   */
  private analyzeFootBlocks(footprint: Array<{ x: number; z: number }>, feetY: number) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;
    let hasAutoRotationY = false;
    let hasAutoMove = false;
    let autoJump = 0;
    let autoOrientationY: number | undefined = undefined;
    const autoMove = { x: 0, y: 0, z: 0 };

    for (const pos of footprint) {
      const blockInfo = this.getBlock(pos.x, feetY, pos.z);
      if (blockInfo.block) {
        blocks.push(blockInfo);

        const physics = blockInfo.block.currentModifier.physics;
        if (physics?.solid) hasSolid = true;
        if (physics?.autoOrientationY !== undefined) {
          hasAutoRotationY = true;
          autoOrientationY = physics.autoOrientationY;
        }
        if (physics?.autoMove) {
          hasAutoMove = true;
          autoMove.x = Math.max(autoMove.x, Math.abs(physics.autoMove.x)) * Math.sign(physics.autoMove.x);
          autoMove.y = Math.max(autoMove.y, Math.abs(physics.autoMove.y)) * Math.sign(physics.autoMove.y);
          autoMove.z = Math.max(autoMove.z, Math.abs(physics.autoMove.z)) * Math.sign(physics.autoMove.z);
        }
        if (physics?.autoJump && physics?.autoJump > 0) autoJump = physics?.autoJump;
      }
    }

    return {
      blocks,
      hasSolid,
      hasAutoRotationY,
      hasAutoMove,
      autoJump,
      autoOrientationY,
      autoMove,
    };
  }

  /**
   * Analyze foot front blocks (for climbing/slopes)
   */
  private analyzeFootFrontBlocks(
    footprint: Array<{ x: number; z: number }>,
    feetY: number,
    frontOffset: { x: number; z: number }
  ) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;
    let hasClimbable = false;
    let maxClimbHeight = 0;
    let cornerHeights: [number, number, number, number] | undefined;

    for (const pos of footprint) {
      const blockInfo = this.getBlock(pos.x + frontOffset.x, feetY, pos.z + frontOffset.z);
      if (blockInfo.block) {
        blocks.push(blockInfo);

        const physics = blockInfo.block.currentModifier.physics;
        if (physics?.solid) hasSolid = true;
        if (physics?.climbable) hasClimbable = true;

        // Get corner heights for slope detection
        const heights = this.surfaceAnalyzer.getCornerHeights(blockInfo.block);
        if (heights) {
          cornerHeights = heights;
          maxClimbHeight = Math.max(...heights);
        }
      }
    }

    return {
      blocks,
      hasSolid,
      hasClimbable,
      maxClimbHeight,
      cornerHeights,
    };
  }

  /**
   * Analyze ground blocks (under player)
   */
  private analyzeGroundBlocks(footprint: Array<{ x: number; z: number }>, groundY: number) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;
    let hasGround = false;
    let groundYPos = -1;
    let resistance = 0;
    let hasAutoMove = false;
    let hasAutoRotationY = false;
    let autoJump = 0;
    let autoOrientationY: number | undefined = undefined;
    const autoMove = { x: 0, y: 0, z: 0 };

    for (const pos of footprint) {
      const blockInfo = this.getBlock(pos.x, groundY, pos.z);
      if (blockInfo.block) {
        blocks.push(blockInfo);

        const physics = blockInfo.block.currentModifier.physics;
        if (physics?.solid) {
          hasSolid = true;
          hasGround = true;
          groundYPos = Math.max(groundYPos, groundY);
        }
        if (physics?.resistance !== undefined) {
          resistance = Math.max(resistance, physics.resistance);
        }
        if (physics?.autoMove) {
          hasAutoMove = true;
          autoMove.x = Math.max(autoMove.x, Math.abs(physics.autoMove.x)) * Math.sign(physics.autoMove.x);
          autoMove.y = Math.max(autoMove.y, Math.abs(physics.autoMove.y)) * Math.sign(physics.autoMove.y);
          autoMove.z = Math.max(autoMove.z, Math.abs(physics.autoMove.z)) * Math.sign(physics.autoMove.z);
        }
        if (physics?.autoOrientationY !== undefined) {
          hasAutoRotationY = true;
          autoOrientationY = physics.autoOrientationY;
        }
        if (physics?.autoJump && physics?.autoJump > 0) autoJump = physics?.autoJump;
      }
    }

    return {
      blocks,
      hasSolid,
      hasGround,
      groundY: groundYPos,
      resistance,
      hasAutoMove,
      hasAutoRotationY,
      autoJump,
      autoMove,
      autoOrientationY,
    };
  }

  /**
   * Analyze ground foot blocks (for slope detection)
   */
  private analyzeGroundFootBlocks(footprint: Array<{ x: number; z: number }>, feetY: number) {
    const blocks: BlockInfo[] = [];
    let isSemiSolid = false;
    let maxHeight = 0;
    let cornerHeights: [number, number, number, number] | undefined;

    for (const pos of footprint) {
      const blockInfo = this.getBlock(pos.x, feetY, pos.z);
      if (blockInfo.block) {
        blocks.push(blockInfo);

        const heights = this.surfaceAnalyzer.getCornerHeights(blockInfo.block);
        if (heights) {
          isSemiSolid = true;
          cornerHeights = heights;
          maxHeight = Math.max(maxHeight, ...heights);
        }
      }
    }

    return {
      blocks,
      isSemiSolid,
      maxHeight,
      cornerHeights,
    };
  }

  /**
   * Analyze head blocks (ceiling)
   */
  private analyzeHeadBlocks(footprint: Array<{ x: number; z: number }>, headY: number) {
    const blocks: BlockInfo[] = [];
    let hasSolid = false;
    let maxY = Number.MAX_SAFE_INTEGER;

    for (const pos of footprint) {
      const blockInfo = this.getBlock(pos.x, headY, pos.z);
      if (blockInfo.block) {
        blocks.push(blockInfo);

        if (blockInfo.block.currentModifier.physics?.solid) {
          hasSolid = true;
          maxY = Math.min(maxY, headY);
        }
      }
    }

    if (maxY === Number.MAX_SAFE_INTEGER) {
      maxY = headY + 100; // No ceiling
    }

    return {
      blocks,
      hasSolid,
      maxY,
    };
  }
}
