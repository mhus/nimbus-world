/**
 * PhysicsUtils - Pure utility functions for physics
 *
 * Contains stateless helper functions for physics calculations and checks.
 */

import { getLogger, Direction, DirectionHelper } from '@nimbus/shared';
import type { Vector3 } from '@babylonjs/core';
import type { PhysicsEntity } from './types';
import type { ChunkService } from '../ChunkService';
import type { AppContext } from '../../AppContext';

const logger = getLogger('PhysicsUtils');

/**
 * Get movement direction from delta values
 */
export function getMovementDirection(dx: number, dz: number): Direction {
  // Determine which axis has larger movement
  const absDx = Math.abs(dx);
  const absDz = Math.abs(dz);

  if (absDx > absDz) {
    // X-axis dominant
    return dx > 0 ? Direction.EAST : Direction.WEST;
  } else {
    // Z-axis dominant (or equal, prefer Z)
    // Note: In this coordinate system, positive Z = North, negative Z = South
    return dz > 0 ? Direction.NORTH : Direction.SOUTH;
  }
}

/**
 * Invert direction (for exit checks)
 * NORTH ↔ SOUTH, EAST ↔ WEST
 */
export function invertDirection(direction: Direction): Direction {
  switch (direction) {
    case Direction.NORTH:
      return Direction.SOUTH;
    case Direction.SOUTH:
      return Direction.NORTH;
    case Direction.EAST:
      return Direction.WEST;
    case Direction.WEST:
      return Direction.EAST;
    default:
      return direction;
  }
}

/**
 * Check if we can enter a block from a specific direction
 *
 * For solid blocks with passableFrom:
 * - If entrySide is in passableFrom → allow entry (one-way gate)
 * - If entrySide not in passableFrom → block entry
 *
 * For non-solid blocks with passableFrom (WALLS):
 * - passableFrom defines which sides are WALLS (barriers)
 * - If entrySide is in passableFrom → blocked (wall exists on that side)
 * - If entrySide not in passableFrom → allow (no wall on that side)
 *
 * @param passableFrom Direction bitfield from block (which sides are walls for non-solid, or passable for solid)
 * @param entrySide Which side of the block we're entering from
 * @param isSolid Whether the block is solid
 * @returns true if entry is allowed, false if blocked
 */
export function canEnterFrom(
  passableFrom: number | undefined,
  entrySide: Direction,
  isSolid: boolean
): boolean {
  // No passableFrom set - use default behavior
  if (passableFrom === undefined || passableFrom === 0) {
    return !isSolid; // Solid blocks block, non-solid blocks allow
  }

  // For solid blocks: passableFrom = sides you CAN enter (one-way gates)
  if (isSolid) {
    return DirectionHelper.hasDirection(passableFrom, entrySide);
  }

  // For non-solid blocks (WALLS): passableFrom = sides with WALLS (cannot enter)
  // Inverted logic: if side is in passableFrom → it's a wall → blocked
  return !DirectionHelper.hasDirection(passableFrom, entrySide);
}

/**
 * Check if we can leave a block towards a specific direction
 *
 * For solid blocks with passableFrom:
 * - Player inside can exit through any non-solid neighbor (one-way behavior)
 *
 * For non-solid blocks with passableFrom (WALLS):
 * - passableFrom defines which sides are WALLS (barriers)
 * - If exitDir is in passableFrom → blocked (wall exists on that side)
 * - If exitDir not in passableFrom → allow (no wall on that side)
 *
 * @param passableFrom Direction bitfield from block (which sides are walls for non-solid, or passable for solid)
 * @param exitDir Which direction we're moving towards (which side we're exiting through)
 * @param isSolid Whether the block is solid
 * @returns true if exit is allowed, false if blocked
 */
export function canLeaveTo(
  passableFrom: number | undefined,
  exitDir: Direction,
  isSolid: boolean
): boolean {
  // No passableFrom set - always allow exit
  if (passableFrom === undefined || passableFrom === 0) {
    return true;
  }

  // For solid blocks: always allow exit (one-way behavior)
  if (isSolid) {
    return true; // Can always exit solid blocks (from inside)
  }

  // For non-solid blocks (WALLS): passableFrom = sides with WALLS (cannot exit)
  // Inverted logic: if side is in passableFrom → it's a wall → blocked
  return !DirectionHelper.hasDirection(passableFrom, exitDir);
}

/**
 * Check if chunk at position is loaded
 */
export function isChunkLoaded(
  worldX: number,
  worldZ: number,
  chunkService: ChunkService | undefined,
  chunkSize: number
): boolean {
  if (!chunkService) {
    return true; // If no chunk service, allow movement
  }

  const chunkX = Math.floor(worldX / chunkSize);
  const chunkZ = Math.floor(worldZ / chunkSize);

  const chunk = chunkService.getChunk(chunkX, chunkZ);
  return chunk !== undefined;
}

/**
 * Clamp entity to loaded chunk boundaries
 * Prevents player from moving into unloaded chunks
 */
export function clampToLoadedChunks(
  entity: PhysicsEntity,
  oldX: number,
  oldZ: number,
  chunkService: ChunkService | undefined,
  chunkSize: number
): void {
  if (!chunkService) {
    return;
  }

  // Check if new position is in a loaded chunk
  if (!isChunkLoaded(entity.position.x, entity.position.z, chunkService, chunkSize)) {
    // Not loaded - revert position
    entity.position.x = oldX;
    entity.position.z = oldZ;
    entity.velocity.x = 0;
    entity.velocity.z = 0;

    logger.debug('Movement blocked - chunk not loaded', {
      entityId: entity.entityId,
      attemptedX: entity.position.x,
      attemptedZ: entity.position.z,
    });
  }
}

/**
 * Clamp entity position to world boundaries
 */
export function clampToWorldBounds(entity: PhysicsEntity, appContext: AppContext): void {
  const worldInfo = appContext.worldInfo;
  if (!worldInfo || !worldInfo.start || !worldInfo.stop) {
    return;
  }

  const start = worldInfo.start;
  const stop = worldInfo.stop;
  let clamped = false;

  // Clamp X
  if (entity.position.x < start.x) {
    entity.position.x = start.x;
    entity.velocity.x = 0;
    clamped = true;
  } else if (entity.position.x > stop.x) {
    entity.position.x = stop.x;
    entity.velocity.x = 0;
    clamped = true;
  }

  // Clamp Y
  if (entity.position.y < start.y) {
    entity.position.y = start.y;
    entity.velocity.y = 0;
    clamped = true;
  } else if (entity.position.y > stop.y) {
    entity.position.y = stop.y;
    entity.velocity.y = 0;
    clamped = true;
  }

  // Clamp Z
  if (entity.position.z < start.z) {
    entity.position.z = start.z;
    entity.velocity.z = 0;
    clamped = true;
  } else if (entity.position.z > stop.z) {
    entity.position.z = stop.z;
    entity.velocity.z = 0;
    clamped = true;
  }

  if (clamped) {
    logger.debug('Entity clamped to world bounds', {
      entityId: entity.entityId,
      position: { x: entity.position.x, y: entity.position.y, z: entity.position.z },
      bounds: { start, stop },
    });
  }
}

/**
 * Check if block at position is solid
 */
export function isBlockSolid(
  x: number,
  y: number,
  z: number,
  chunkService: ChunkService | undefined
): boolean {
  if (!chunkService) {
    return false;
  }

  // Floor coordinates to get block position
  const blockX = Math.floor(x);
  const blockY = Math.floor(y);
  const blockZ = Math.floor(z);

  const clientBlock = chunkService.getBlockAt(blockX, blockY, blockZ);
  if (!clientBlock) {
    return false; // No block = not solid
  }

  // Direct access to pre-merged modifier (much faster!)
  return clientBlock.currentModifier.physics?.solid === true;
}

/**
 * Check underwater state for entity
 *
 * Updates entity.inWater flag based on heightData from chunks.
 * Also handles world height boundaries (min/max Y clamping).
 *
 * @param entity Entity to check
 * @param chunkService Chunk service for accessing height data
 * @param appContext App context for camera service notification
 * @returns true if state changed, false otherwise
 */
export function checkUnderwaterState(
  entity: PhysicsEntity,
  chunkService: ChunkService | undefined,
  appContext: AppContext,
  eyeHeight: number
): boolean {
  if (!chunkService) {
    return false;
  }

  const chunkSize = appContext.worldInfo?.chunkSize || 16;
  const chunkX = Math.floor(entity.position.x / chunkSize);
  const chunkZ = Math.floor(entity.position.z / chunkSize);
  const chunk = chunkService.getChunk(chunkX, chunkZ);

  if (!chunk) {
    return false;
  }

  // Calculate local coordinates within chunk (handle negative positions correctly)
  const localX = ((entity.position.x % chunkSize) + chunkSize) % chunkSize;
  const localZ = ((entity.position.z % chunkSize) + chunkSize) % chunkSize;
  const heightKey = `${Math.floor(localX)},${Math.floor(localZ)}`;
  const heightData = chunk.data.hightData.get(heightKey);

  const wasInWater = entity.inWater;

  if (heightData && heightData[5] !== undefined) {
    const [x, z, maxHeight, minHeight, groundLevel, waterHeight] = heightData;

    // Player is underwater when eyes are below water surface
    const waterSurfaceY = waterHeight + 1.0; // Water block top face
    const eyeY = entity.position.y + eyeHeight;
    entity.inWater = eyeY <= waterSurfaceY;

    // Clamp to min/max height boundaries
    if (entity.position.y < minHeight) {
      entity.position.y = minHeight;
      entity.velocity.y = 0;
    } else if (entity.position.y > maxHeight) {
      entity.position.y = maxHeight;
      entity.velocity.y = 0;
    }
  } else {
    // No waterHeight data - definitely not underwater
    entity.inWater = false;
  }

  // Notify CameraService on state change
  if (wasInWater !== entity.inWater) {
    const cameraService = appContext.services.camera;
    if (cameraService) {
      cameraService.setUnderwater(entity.inWater);
      logger.debug('💧 Water state changed', {
        entityId: entity.entityId,
        inWater: entity.inWater,
      });
    }
    return true;
  }

  return false;
}

/**
 * Check if entity has moved to a different block position
 * Returns true if floor(position) has changed since last check
 */
export function hasBlockPositionChanged(entity: PhysicsEntity): boolean {
  const currentBlockX = Math.floor(entity.position.x);
  const currentBlockY = Math.floor(entity.position.y);
  const currentBlockZ = Math.floor(entity.position.z);

  const changed =
    currentBlockX !== Math.floor(entity.lastBlockPos.x) ||
    currentBlockY !== Math.floor(entity.lastBlockPos.y) ||
    currentBlockZ !== Math.floor(entity.lastBlockPos.z);

  if (changed) {
    entity.lastBlockPos.x = currentBlockX;
    entity.lastBlockPos.y = currentBlockY;
    entity.lastBlockPos.z = currentBlockZ;
  }

  return changed;
}
