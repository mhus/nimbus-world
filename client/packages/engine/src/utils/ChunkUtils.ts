/**
 * ChunkUtils - Coordinate conversion and chunk utilities
 *
 * Provides utility functions for converting between world coordinates
 * and chunk coordinates, and other chunk-related calculations.
 */

import type { ChunkCoordinate } from '@nimbus/shared';

/**
 * Convert world coordinates to chunk coordinates
 *
 * @param worldX - World X coordinate
 * @param worldZ - World Z coordinate
 * @param chunkSize - Size of chunks (typically 16 or 32)
 * @returns Chunk coordinates
 */
export function worldToChunk(
  worldX: number,
  worldZ: number,
  chunkSize: number
): ChunkCoordinate {
  return {
    cx: Math.floor(worldX / chunkSize),
    cz: Math.floor(worldZ / chunkSize),
  };
}

/**
 * Convert chunk coordinates to world coordinates (chunk origin)
 *
 * @param cx - Chunk X coordinate
 * @param cz - Chunk Z coordinate
 * @param chunkSize - Size of chunks
 * @returns World coordinates of chunk origin
 */
export function chunkToWorld(
  cx: number,
  cz: number,
  chunkSize: number
): { x: number; z: number } {
  return {
    x: cx * chunkSize,
    z: cz * chunkSize,
  };
}

/**
 * Get chunk key for Map storage
 *
 * @param cx - Chunk X coordinate
 * @param cz - Chunk Z coordinate
 * @returns String key for chunk
 */
export function getChunkKey(cx: number, cz: number): string {
  return `${cx},${cz}`;
}

// /**
//  * Get browser-specific render distance
//  *
//  * Safari needs higher render distance for acceptable performance.
//  *
//  * @returns Recommended render distance in chunks
//  */
// export function getBrowserSpecificRenderDistance(): number {
//   if (typeof navigator === 'undefined') {
//     return 1; // Default for tests
//   }
//
//   const ua = navigator.userAgent.toLowerCase();
//
//   // Safari (but not Chrome which also contains 'safari' in UA)
//   if (ua.includes('safari') && !ua.includes('chrome')) {
//     return 3;
//   }
//
//   // Chrome, Firefox, Edge, etc.
//   return 1;
// }
//
// /**
//  * Get browser-specific unload distance
//  *
//  * Chunks beyond this distance from player will be unloaded.
//  *
//  * @returns Unload distance in chunks (renderDistance + 1)
//  */
// export function getBrowserSpecificUnloadDistance(): number {
//   return getBrowserSpecificRenderDistance() + 1;
// }
