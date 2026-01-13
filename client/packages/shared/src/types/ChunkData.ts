/**
 * ChunkData - Internal chunk representation
 *
 * This is the internal representation of a chunk for both client and server.
 * For network transfer, see ChunkDataTransferObject.
 *
 * Chunks are always columns (XZ coordinates, full Y height).
 */

import type { Block } from './Block';
import type { ItemBlockRef } from './ItemBlockRef';
import type { Backdrop } from './Backdrop';

/**
 * Height data for chunk column
 * Array of 4 values describing height information for a specific XZ position
 */
export type HeightData = readonly [
  x: number, // javaType: int
  z: number, // javaType: int
  maxHeight: number, // javaType: int
  groundLevel: number, // javaType: int
  waterLevel?: number // javaType: int
];

/**
 * Block status data for chunk if not default. Block address + status.
 * Array of 4 values: x, y, z, status
 */
export type Status = [
    x: number, // javaType: int
    y: number, // javaType: int
    z: number, // javaType: int
    s: number // javaType: int
];

/**
 * Chunk size (blocks per side)
 * Typically 16 or 32, defined by world settings
 */
export type ChunkSize = number;

/**
 * Chunk data - internal representation
 *
 * Stores block data efficiently as a flat typed array.
 * Block index: localX + localZ * chunkSize + localY * chunkSize * chunkSize
 */
export interface ChunkData {
  /**
   * Chunk X coordinate (in chunk space)
   */
  cx: number; // javaType: int

  /**
   * Chunk Z coordinate (in chunk space)
   */
  cz: number; // javaType: int

  /**
   * Chunk size (blocks per side, typically 16 or 32)
   * Deprecated: use world settings instead
   */
  size: number; // javaType: byte

  /**
   * Sparse block data - array of Block instances
   * Only stores non-air blocks (blockTypeId !== 0)
   * Each Block contains world coordinates (x, y, z)
   */
  blocks: Block[];

  /**
   * Item data - array of Item instances
   * Items are managed separately from blocks with their own structure
   * Items can only exist at AIR positions or replace existing items
   * Each item has a unique ID, position, itemType, and modifiers
   */
  i?: ItemBlockRef[];

  /**
   * Height data per XZ position (optional)
   * Flat array of height values
   */
  heightData?: HeightData[]; // javaType: int[][]

  status?: Status[]; // javaType: int[][]

  /**
   * Backdrop data for chunk edges (optional)
   * Defines backdrop walls at chunk boundaries
   */
  backdrop?: {
    /** North side backdrop items (negative Z direction) */
    n?: Array<Backdrop>;
    /** East side backdrop items (positive X direction) */
    e?: Array<Backdrop>;
    /** South side backdrop items (positive Z direction) */
    s?: Array<Backdrop>;
    /** West side backdrop items (negative X direction) */
    w?: Array<Backdrop>;
  };

  /**
   * Binary compressed chunk data (optional)
   * Used for efficient storage or transfer of chunk information
   * The format and compression method are implementation-specific
   */
  c?: Uint8Array; // javaType: byte[]
}
