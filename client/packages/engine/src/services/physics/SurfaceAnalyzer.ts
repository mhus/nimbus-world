/**
 * SurfaceAnalyzer - Surface and slope analysis
 *
 * Analyzes block surfaces for:
 * - Surface type (flat, slope, climbing)
 * - Surface height (interpolated from corner heights)
 * - Slope vectors
 * - Walkability
 */

import { getLogger } from '@nimbus/shared';
import type { SurfaceState, PhysicsEntity } from './types';
import type { ClientBlock } from '../../types/ClientBlock';
import type { ChunkService } from '../ChunkService';
import type { PlayerEntity } from '../../types/PlayerEntity';

const logger = getLogger('SurfaceAnalyzer');

/**
 * Type guard to check if entity is a PlayerEntity
 */
function isPlayerEntity(entity: PhysicsEntity): entity is PlayerEntity {
  return 'playerInfo' in entity;
}

/**
 * SurfaceAnalyzer - Analyzes surfaces and slopes
 */
export class SurfaceAnalyzer {
  constructor(private chunkService: ChunkService) {}

  /**
   * Get corner heights for a block from auto-derived offsets.
   *
   * Auto-derives corner heights from:
   * - Block.offsets (Y values of top 4 corners)
   * - VisibilityModifier.offsets (fallback)
   *
   * Only works if shape == CUBE (shape === 1).
   *
   * @param block ClientBlock to get corner heights from
   * @returns Corner heights array [NW, NE, SE, SW] or undefined
   */
  getCornerHeights(block: ClientBlock): [number, number, number, number] | undefined {
    let cornerHeights: [number, number, number, number] | undefined;

    // Auto-derive from offsets (only if shape == CUBE)
    if (block.currentModifier.visibility?.shape === 1) { // Shape.CUBE = 1
      // Try Block.offsets first
      // Minimum length must be 24 for all 8 corners (3 values per corner)
      if (block.block.offsets && block.block.offsets.length >= 23) {
        // Extract Y-offsets from top 4 corners
        // Offsets order: [bottom 4 corners (0-11), top 4 corners (12-23)]
        // Top corners: [4]=SW(12-14), [5]=SE(15-17), [6]=NW(18-20), [7]=NE(21-23)
        // cornerHeights order: [NW, NE, SE, SW]
        const yNW = block.block.offsets[19] ?? 0; // Corner 6 Y
        const yNE = block.block.offsets[22] ?? 0; // Corner 7 Y
        const ySE = block.block.offsets[16] ?? 0; // Corner 5 Y
        const ySW = block.block.offsets[13] ?? 0; // Corner 4 Y
        cornerHeights = [yNW, yNE, ySE, ySW];
      }
      // Try VisibilityModifier.offsets
      else if (
        block.currentModifier.visibility?.offsets &&
        block.currentModifier.visibility.offsets.length >= 23
      ) {
        const offsets = block.currentModifier.visibility.offsets;
        const yNW = offsets[19] ?? 0;
        const yNE = offsets[22] ?? 0;
        const ySE = offsets[16] ?? 0;
        const ySW = offsets[13] ?? 0;
        cornerHeights = [yNW, yNE, ySE, ySW];
      }
    }

    return cornerHeights;
  }

  /**
   * Calculate interpolated surface height at a position within a block.
   * Uses bilinear interpolation between the four corner heights.
   *
   * @param block ClientBlock with potential cornerHeights
   * @param worldX World X position (can be fractional)
   * @param worldZ World Z position (can be fractional)
   * @returns Interpolated Y position of the block surface at this location
   */
  getBlockSurfaceHeight(block: ClientBlock, worldX: number, worldZ: number): number {
    const cornerHeights = this.getCornerHeights(block);

    // No corner heights found - use standard block top
    if (!cornerHeights) {
      return block.block.position.y + 1.0;
    }

    // Calculate local coordinates within the block (0.0 to 1.0)
    const localX = worldX - block.block.position.x;
    const localZ = worldZ - block.block.position.z;

    // Bilinear interpolation between 4 corners
    // Corner indices: [0]=NW, [1]=NE, [2]=SE, [3]=SW
    const heightNW = cornerHeights[0]; // North-West
    const heightNE = cornerHeights[1]; // North-East
    const heightSE = cornerHeights[2]; // South-East
    const heightSW = cornerHeights[3]; // South-West

    // Interpolate along North edge (Z = 0) between NW and NE
    const heightNorth = heightNW + (heightNE - heightNW) * localX;

    // Interpolate along South edge (Z = 1) between SW and SE
    const heightSouth = heightSW + (heightSE - heightSW) * localX;

    // Interpolate between North and South edges
    const interpolatedHeight = heightNorth + (heightSouth - heightNorth) * localZ;

    return block.block.position.y + 1.0 + interpolatedHeight;
  }

  /**
   * Calculate slope vector from corner heights.
   * Returns normalized slope in X and Z directions.
   *
   * @param cornerHeights Array of 4 corner heights [NW, NE, SE, SW]
   * @returns Vector2 with slope in X and Z directions (range: -1 to 1 per axis)
   */
  calculateSlope(cornerHeights: [number, number, number, number]): { x: number; z: number } {
    // Calculate average slope in X direction (West to East)
    // Compare West side (NW, SW) to East side (NE, SE)
    const westHeight = (cornerHeights[0] + cornerHeights[3]) / 2; // Average of NW and SW
    const eastHeight = (cornerHeights[1] + cornerHeights[2]) / 2; // Average of NE and SE
    const slopeX = eastHeight - westHeight; // Positive = rising to East

    // Calculate average slope in Z direction (North to South)
    // Compare North side (NW, NE) to South side (SW, SE)
    const northHeight = (cornerHeights[0] + cornerHeights[1]) / 2; // Average of NW and NE
    const southHeight = (cornerHeights[3] + cornerHeights[2]) / 2; // Average of SW and SE
    const slopeZ = southHeight - northHeight; // Positive = rising to South

    return { x: slopeX, z: slopeZ };
  }

  /**
   * Check if block is semi-solid (has corner heights)
   */
  isSemiSolid(block: ClientBlock): boolean {
    return this.getCornerHeights(block) !== undefined;
  }
}
