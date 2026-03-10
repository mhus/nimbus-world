/**
 * ClientChunk - Client-side chunk representation
 *
 * Contains processed block data from the server enriched with
 * client-side state for rendering and management.
 */

import {Backdrop, Vector3, AreaData, HeightData} from '@nimbus/shared';
import type { ClientBlock } from './ClientBlock';
import type { DisposableResources } from '../rendering/DisposableResources';

/**
 * Client-side chunk with processed blocks and rendering state
 */
export class ClientChunk {
  /** Chunk X coordinate */
  cx: number;

  /** Chunk Z coordinate */
  cz: number;

  /** Map of block position key(x,y,z) -> ClientBlock (with merged modifiers) */
  blocks: Map<string, ClientBlock>;

  /** Record of height position key(x,z) -> HeightData */
  heightData: Record<string, HeightData>;

  /** Block status overrides by position key */
  statusData: Map<string, string>;

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

  /** Area data with parameters */
  areaData?: Array<AreaData>;

  /** Indicates that the chunk is denied access */
  deny?: boolean;

  /** Whether chunk has been rendered */
  isRendered: boolean;

  /** Whether chunk has been loaded */
  isLoaded: boolean;

  chunkSize: number;

  /** Last time chunk was accessed (for LRU) */
  lastAccessTime: number;

  /** Optional reference to Babylon.js mesh */
  renderMesh?: any;

  constructor(
    cx: number,
    cz: number,
    blocks: Map<string, ClientBlock>,
    heightData: Record<string, HeightData>,
    statusData: Map<string, string>,
    chunkSize: number,
  ) {
    this.cx = cx;
    this.cz = cz;
    this.blocks = blocks;
    this.heightData = heightData;
    this.statusData = statusData;
    this.chunkSize = chunkSize;
    this.isRendered = false;
    this.isLoaded = false;
    this.lastAccessTime = Date.now();
  }

  /**
   * Get height data for column (x, z) within chunk
   * @param posX block world x coordinate
   * @param posZ block world z coordinate
   * @returns HeightData or undefined if not found
   */
  getHeightData(posX: number, posZ: number): HeightData | undefined {
    const worldX = Math.floor(posX);
    const worldZ = Math.floor(posZ);
    return this.heightData?.[`${worldX},${worldZ}`];
  }

  getHeightDataForPosition(position: Vector3) {
    return this.getHeightData(position.x, position.z);
  }
}
