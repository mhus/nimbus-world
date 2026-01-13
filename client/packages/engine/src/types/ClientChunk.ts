/**
 * ClientChunk - Client-side chunk representation
 *
 * Wraps ChunkDataTransferObject from server with additional
 * client-side state for rendering and management.
 */

import type {ChunkDataTransferObject, Backdrop, Vector3} from '@nimbus/shared';
import type { ClientBlock } from './ClientBlock';
import type { DisposableResources } from '../rendering/DisposableResources';

/**
 * Height data for chunk column
 *
 * Describes vertical boundaries and special levels for a single column (x, z) in a chunk.
 *
 * @field x - World X coordinate within chunk (0 to chunkSize-1)
 * @field z - World Z coordinate within chunk (0 to chunkSize-1)
 * @field maxHeight - Maximum Y boundary:
 *   - Usually world.stop.y (e.g. 1000)
 *   - Exception: If blocks exceed world.stop.y, set to (highestBlock + 10) for headroom
 *   - Can be overridden by server via chunkData.h
 * @field minHeight - Minimum Y boundary (lowest block Y position, or world.start.y if no blocks)
 * @field groundLevel - Y position of lowest solid block (ground surface)
 * @field waterHeight - Y position of highest water block (water surface), undefined if no water
 */
export type ClientHeightData = readonly [
  x: number,
  z: number,
  maxHeight: number,
  minHeight: number,
  groundLevel: number,
  waterHeight?: number
];

/**
 * Client-side chunk data with processed blocks
 */
export interface ClientChunkData {
  /** Original transfer object from server */
  transfer: ChunkDataTransferObject;

  /** Map of block position key(x,y,z) -> ClientBlock (with merged modifiers) */
  data: Map<string, ClientBlock>;
  /** Map of height position key(x,z) -> ClientHeightData */
  hightData: Map<string, ClientHeightData>;

  statusData: Map<string, number>;

  /** Backdrop data for chunk edges (with defaults applied) */
  backdrop?: {
    n?: Array<Backdrop>;
    e?: Array<Backdrop>;
    s?: Array<Backdrop>;
    w?: Array<Backdrop>;
  };

  /** Disposable rendering resources (meshes, sprites, etc.) created for this chunk */
  resourcesToDispose?: DisposableResources;

  /** Map of block position key -> permanent audio Sound (for per-block audio management) */
  permanentAudioSounds?: Map<string, any>;
}

/**
 * Client-side chunk with rendering state
 */
export class ClientChunk {
  /** Chunk data with processed blocks */
  data: ClientChunkData;

  /** Whether chunk has been rendered */
  isRendered: boolean;

  /** Whether chunk has been loaded */
  isLoaded: boolean;

  chunkSize: number;

    /** Last time chunk was accessed (for LRU) */
  lastAccessTime: number;

    constructor(data: ClientChunkData, chunkSize : number) {
        this.data = data;
        this.isRendered = false;
        this.isLoaded = false;
        this.chunkSize = chunkSize;
        this.lastAccessTime = Date.now();
    }

  /** Optional reference to Babylon.js mesh */
  renderMesh?: any;

  /**
   * Get height data for column (x, z) within chunk
   * @param posX block world x coordinate
   * @param posZ block world z coordinate
   * @returns ClientHeightData or undefined if not found
   */
  getHeightData(posX: number, posZ: number): ClientHeightData | undefined {
    // Convert world coordinates to local chunk coordinates
    const chunkCx = this.data.transfer.cx;
    const chunkCz = this.data.transfer.cz;

    const worldX = Math.floor(posX);
    const worldZ = Math.floor(posZ);

    const localX = worldX - (chunkCx * this.chunkSize);
    const localZ = worldZ - (chunkCz * this.chunkSize);

    return this.data?.hightData?.get(`${localX},${localZ}`);
  }

  getHeightDataForPosition(position: Vector3) {
    return this.getHeightData(position.x, position.z);
  }

}
