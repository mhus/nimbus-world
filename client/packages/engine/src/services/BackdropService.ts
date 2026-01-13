/**
 * BackdropService - Manages backdrop rendering from chunk data
 *
 * Backdrops are pseudo-walls rendered at chunk edges based on chunk data.
 * They prevent sun from shining into tunnels and provide far-away rendering with alpha fading.
 *
 * Key features:
 * - Renders backdrops from chunk data (not dynamically calculated)
 * - Each backdrop side has its own mesh
 * - Reacts to chunk load/unload events
 * - Tracks and disposes unused backdrops
 * - Manages separate material cache via BackdropMaterialManager
 */

import { Mesh, VertexData, MeshBuilder, Color3, Vector3, type Scene } from '@babylonjs/core';
import {
  getLogger,
  ExceptionHandler,
  type Backdrop,
} from '@nimbus/shared';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import type { AppContext } from '../AppContext';
import type { ChunkService } from './ChunkService';
import { BackdropMaterialManager } from './BackdropMaterialManager';
import { getChunkKey } from '../utils/ChunkUtils';

const logger = getLogger('BackdropService');

/**
 * Backdrop mesh key format: "{direction}{cx},{cz}"
 * Examples: "n3,4" (north backdrop for chunk 3,4), "e0,-1", "s2,5", "w-1,3"
 */
type BackdropKey = string;

/**
 * BackdropService - Manages backdrop rendering from chunk data
 */
export class BackdropService {
  private scene: Scene;
  private chunkService: ChunkService;
  private materialManager: BackdropMaterialManager;

  /** Backdrop meshes by key (format: "cx,cz:direction") */
  private backdropMeshes = new Map<BackdropKey, Mesh>();

  /** Prevent concurrent updates */
  private isUpdating = false;

  /** Flag to trigger another update after current one finishes */
  private needsAnotherUpdate = false;

  /** Backdrop type cache (loaded from server) */
  private backdropTypeCache = new Map<string, Backdrop>();

  /** Loading promises for deduplication */
  private loadingBackdropTypes = new Map<string, Promise<Backdrop | undefined>>();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.chunkService = appContext.services.chunk as ChunkService;
    this.materialManager = new BackdropMaterialManager(scene, appContext);

    this.setupEventListeners();

    logger.debug('BackdropService initialized');

    // Initial update in case chunks are already loaded
    setTimeout(() => this.updateBackdrops(), 1000);
  }

  /**
   * Setup chunk event listeners
   */
  private setupEventListeners(): void {
    this.chunkService.on('chunk:loaded', () => {
      this.updateBackdrops();
    });

    this.chunkService.on('chunk:unloaded', () => {
      this.updateBackdrops();
    });

    logger.debug('Chunk event listeners registered');
  }

  /**
   * Update backdrops based on currently loaded chunks
   *
   * This is called whenever chunks are loaded or unloaded.
   * For each chunk, check if neighbors exist. If not, draw backdrop on that side.
   */
  private async updateBackdrops(): Promise<void> {
    // If update is in progress, mark that another update is needed
    if (this.isUpdating) {
      this.needsAnotherUpdate = true;
      return;
    }

    this.isUpdating = true;
    this.needsAnotherUpdate = false;

    try {
      const allChunks = this.chunkService.getAllChunks();

      logger.debug('Updating backdrops', { loadedChunks: allChunks.length });

      // Build set of loaded chunk coordinates for fast lookup
      const loadedCoords = new Set<string>();
      for (const chunk of allChunks) {
        const cx = chunk.data.transfer.cx;
        const cz = chunk.data.transfer.cz;
        loadedCoords.add(getChunkKey(cx, cz));
      }

      // Collect all needed backdrops by checking each chunk's neighbors
      const neededBackdrops = new Set<BackdropKey>();

      // Check each loaded chunk for missing neighbors
      for (const chunk of allChunks) {
        const cx = chunk.data.transfer.cx;
        const cz = chunk.data.transfer.cz;

        // Check North neighbor (cz+1)
        const hasNorth = loadedCoords.has(getChunkKey(cx, cz + 1));
        if (!hasNorth) {
          neededBackdrops.add(this.getBackdropKey(cx, cz, 'n'));
        }

        // Check South neighbor (cz-1)
        const hasSouth = loadedCoords.has(getChunkKey(cx, cz - 1));
        if (!hasSouth) {
          neededBackdrops.add(this.getBackdropKey(cx, cz, 's'));
        }

        // Check East neighbor (cx+1)
        const hasEast = loadedCoords.has(getChunkKey(cx + 1, cz));
        if (!hasEast) {
          neededBackdrops.add(this.getBackdropKey(cx, cz, 'e'));
        }

        // Check West neighbor (cx-1)
        const hasWest = loadedCoords.has(getChunkKey(cx - 1, cz));
        if (!hasWest) {
          neededBackdrops.add(this.getBackdropKey(cx, cz, 'w'));
        }
      }

      logger.debug('Collected needed backdrops', {
        count: neededBackdrops.size,
        currentlyRendered: this.backdropMeshes.size
      });

      // Dispose backdrops that are no longer needed
      this.disposeUnneededBackdrops(neededBackdrops);

      // Create new backdrops where needed
      await this.createNeededBackdrops(allChunks, neededBackdrops);

      logger.debug('Backdrops updated successfully', {
        renderedBackdrops: this.backdropMeshes.size
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'BackdropService.updateBackdrops');
    } finally {
      this.isUpdating = false;

      // If another update was requested while we were updating, run it now
      if (this.needsAnotherUpdate) {
        this.needsAnotherUpdate = false;
        setTimeout(() => this.updateBackdrops(), 0);
      }
    }
  }

  /**
   * Generate backdrop key from chunk coordinates and direction
   * Format: "{direction}{cx},{cz}" (e.g., "n3,4", "s0,-1", "e2,5", "w-1,3")
   */
  private getBackdropKey(cx: number, cz: number, direction: string): BackdropKey {
    return `${direction}${cx},${cz}`;
  }


  /**
   * Dispose backdrops that are no longer needed
   */
  private disposeUnneededBackdrops(neededBackdrops: Set<BackdropKey>): void {
    const toDispose: BackdropKey[] = [];

    // Find backdrops to dispose
    for (const [key, mesh] of this.backdropMeshes) {
      if (!neededBackdrops.has(key)) {
        toDispose.push(key);
      }
    }

    if (toDispose.length > 0) {
      logger.debug('Disposing unneeded backdrops', {
        count: toDispose.length,
        keys: toDispose
      });

      // Dispose them
      for (const key of toDispose) {
        const mesh = this.backdropMeshes.get(key);
        if (mesh) {
          mesh.dispose();
          this.backdropMeshes.delete(key);
        }
      }
    }
  }

  /**
   * Create backdrops where needed
   */
  private async createNeededBackdrops(
    allChunks: any[],
    neededBackdrops: Set<BackdropKey>
  ): Promise<void> {
    // Build a map of chunks by coordinate for fast lookup
    const chunkMap = new Map<string, any>();
    for (const chunk of allChunks) {
      const cx = chunk.data.transfer.cx;
      const cz = chunk.data.transfer.cz;
      chunkMap.set(getChunkKey(cx, cz), chunk);
    }

    // Create each needed backdrop
    for (const key of neededBackdrops) {
      // Skip if already exists
      if (this.backdropMeshes.has(key)) {
        continue;
      }

      // Parse key to get chunk coordinates and direction
      // Format: "n3,4" -> direction='n', cx=3, cz=4
      const direction = key[0];
      const coords = key.substring(1);
      const [cx, cz] = coords.split(',').map(Number);

      // Get the chunk
      const chunk = chunkMap.get(getChunkKey(cx, cz));
      if (!chunk || !chunk.data.backdrop) {
        continue;
      }

      // Get backdrop data for this direction
      const backdrop = chunk.data.backdrop;
      let backdropData: Array<Backdrop> | undefined;

      switch (direction) {
        case 'n':
          backdropData = backdrop.n;
          break;
        case 's':
          backdropData = backdrop.s;
          break;
        case 'e':
          backdropData = backdrop.e;
          break;
        case 'w':
          backdropData = backdrop.w;
          break;
      }

      if (!backdropData || backdropData.length === 0) {
        continue;
      }

      // Create the backdrop
      await this.createBackdrop(cx, cz, direction, backdropData);
    }
  }

  /**
   * Create backdrop mesh for a specific chunk side
   */
  private async createBackdrop(
    cx: number,
    cz: number,
    direction: string,
    backdrops: Array<Backdrop>
  ): Promise<void> {
    try {
      const key = this.getBackdropKey(cx, cz, direction);

      // Use first backdrop (TODO: support multiple backdrops per side)
      let backdropConfig = backdrops[0];

      // If backdrop has an ID, load type from server and merge with inline config
      if (backdropConfig.id) {
        logger.debug('Loading backdrop type from server', {
          id: backdropConfig.id,
          key
        });

        const loadedType = await this.loadBackdropType(backdropConfig.id);
        if (loadedType) {
          // Merge: server defaults + inline overrides
          backdropConfig = { ...loadedType, ...backdropConfig };
          logger.debug('Backdrop type loaded and merged', {
            id: backdropConfig.id,
            merged: backdropConfig
          });
        } else {
          logger.warn('Failed to load backdrop type, using inline config', {
            id: backdropConfig.id
          });
        }
      }

      // Check if type is 'none' - skip rendering
      if (backdropConfig.type === 'none') {
        logger.debug('Backdrop type is "none", skipping rendering', { key });
        return;
      }

      // Create mesh
      const mesh = this.createBackdropMesh(cx, cz, direction, backdropConfig);

      if (mesh) {
        // Get material
        const material = await this.materialManager.getBackdropMaterial(backdropConfig);
        mesh.material = material;

        // Rendering settings - render backdrop between environment and world
        mesh.renderingGroupId = RENDERING_GROUPS.BACKDROP;
        mesh.name = `backdrop_${key}`;

        // Store mesh
        this.backdropMeshes.set(key, mesh);

        logger.debug('Backdrop created', { key });
      } else {
        logger.warn('Failed to create backdrop mesh', { key });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'BackdropService.createBackdrop', {
        cx,
        cz,
        direction,
      });
    }
  }

  /**
   * Load backdrop type from server
   */
  private async loadBackdropType(id: string): Promise<Backdrop | undefined> {
    try {
      // Check cache
      if (this.backdropTypeCache.has(id)) {
        logger.debug('Backdrop type found in cache', { id });
        return this.backdropTypeCache.get(id);
      }

      // Check if already loading
      if (this.loadingBackdropTypes.has(id)) {
        logger.debug('Backdrop type already loading, waiting', { id });
        return await this.loadingBackdropTypes.get(id);
      }

      // Load from server
      const loadPromise = this.fetchBackdropType(id);
      this.loadingBackdropTypes.set(id, loadPromise);

      try {
        const result = await loadPromise;
        return result;
      } finally {
        this.loadingBackdropTypes.delete(id);
      }
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'BackdropService.loadBackdropType',
        { id }
      );
    }
  }

  /**
   * Fetch backdrop type from server REST API
   */
  private async fetchBackdropType(id: string): Promise<Backdrop | undefined> {
    try {
      const networkService = this.chunkService['appContext'].services.network;
      if (!networkService) {
        throw new Error('NetworkService not available');
      }

      const url = networkService.getBackdropUrl(id);

      logger.debug('Fetching backdrop type from server', { id, url });

      const response = await fetch(url, {
        credentials: 'include',
      });

      if (response.status === 404) {
        logger.warn('Backdrop type not found on server', { id, url });
        return undefined;
      }

      if (!response.ok) {
        throw new Error(
          `Failed to fetch backdrop type: ${response.status} ${response.statusText}`
        );
      }

      const backdropType: Backdrop = await response.json();

      // Cache
      this.backdropTypeCache.set(id, backdropType);

      logger.debug('Backdrop type fetched successfully', {
        id,
        backdropType
      });

      return backdropType;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'BackdropService.fetchBackdropType',
        { id }
      );
    }
  }

  /**
   * Create a single backdrop mesh for one direction
   */
  private createBackdropMesh(
    cx: number,
    cz: number,
    direction: string,
    config: Backdrop
  ): Mesh | null {
    try {
      // Get chunk size from world info
      const chunkSize = this.chunkService['appContext'].worldInfo?.chunkSize || 16;

      // Calculate world coordinates of chunk origin
      const worldX = cx * chunkSize;
      const worldZ = cz * chunkSize;

      // Get local coordinates (0-16 within chunk)
      const left = config.left ?? 0;
      const width = config.width ?? chunkSize;
      const right = left + width;

      // Calculate Y coordinates
      let yBase: number;
      if (config.yBase !== undefined) {
        yBase = config.yBase;
      } else {
        // Calculate groundLevel at this edge
        yBase = this.calculateGroundLevelAtEdge(cx, cz, direction, left, right);
      }

      const height = config.height ?? 60;
      const yUp = yBase + height;

      // Create mesh based on direction
      // left and width are local coordinates (0-16)
      let x1: number, z1: number, x2: number, z2: number;

      switch (direction) {
        case 'n':
          // North edge (positive Z direction, upper edge of chunk)
          // z = worldZ + chunkSize (e.g., chunk 1,1 -> z=32)
          // x goes from left to (left + width)
          x1 = worldX + left;
          z1 = worldZ + chunkSize;
          x2 = worldX + right;
          z2 = worldZ + chunkSize;
          break;

        case 's':
          // South edge (negative Z direction, lower edge of chunk)
          // z = worldZ (e.g., chunk 1,1 -> z=16)
          // x goes from left to (left + width)
          x1 = worldX + left;
          z1 = worldZ;
          x2 = worldX + right;
          z2 = worldZ;
          break;

        case 'e':
          // East edge (positive X direction, right edge of chunk)
          // x = worldX + chunkSize (e.g., chunk 1,1 -> x=32)
          // z goes from left to (left + width)
          x1 = worldX + chunkSize;
          z1 = worldZ + left;
          x2 = worldX + chunkSize;
          z2 = worldZ + right;
          break;

        case 'w':
          // West edge (negative X direction, left edge of chunk)
          // x = worldX (e.g., chunk 1,1 -> x=16)
          // z goes from left to (left + width)
          x1 = worldX;
          z1 = worldZ + left;
          x2 = worldX;
          z2 = worldZ + right;
          break;

        default:
          logger.warn('Unknown backdrop direction', { direction });
          return null;
      }

      // Get depth (0 = plane, > 0 = box)
      const depth = config.depth ?? 0;

      // Create mesh (plane or box depending on depth)
      const mesh = this.createBackdropGeometry(
        x1, z1, x2, z2, yBase, yUp,
        direction, depth
      );

      return mesh;
    } catch (error) {
      ExceptionHandler.handle(error, 'BackdropService.createBackdropMesh', {
        cx,
        cz,
        direction,
      });
      return null;
    }
  }

  /**
   * Calculate ground level at the edge of a chunk
   */
  private calculateGroundLevelAtEdge(
    cx: number,
    cz: number,
    direction: string,
    left: number,
    right: number
  ): number {
    try {
      const chunk = this.chunkService.getChunk(cx, cz);
      if (!chunk || !chunk.data.hightData) {
        return 0; // Default if no height data
      }

      const heightData = chunk.data.hightData;
      let minGroundLevel = Infinity;

      // Sample ground level along the edge
      const samples = Math.max(3, Math.ceil(right - left)); // At least 3 samples
      for (let i = 0; i <= samples; i++) {
        const t = i / samples;
        const localCoord = Math.floor(left + (right - left) * t);

        let x: number, z: number;
        switch (direction) {
          case 'n':
          case 's':
            x = localCoord;
            z = direction === 'n' ? 0 : 15;
            break;
          case 'e':
          case 'w':
            x = direction === 'w' ? 0 : 15;
            z = localCoord;
            break;
          default:
            continue;
        }

        const key = `${x},${z}`;
        const height = heightData.get(key);
        if (height) {
          const groundLevel = height[4]; // groundLevel is at index 4
          if (groundLevel < minGroundLevel) {
            minGroundLevel = groundLevel;
          }
        }
      }

      const result = minGroundLevel === Infinity ? 0 : minGroundLevel;
      logger.debug('Calculated ground level at edge', {
        chunk: { cx, cz },
        direction,
        groundLevel: result
      });

      return result;
    } catch (error) {
      logger.warn('Failed to calculate ground level, using 0', {
        cx, cz, direction, error
      });
      return 0;
    }
  }

  /**
   * Create backdrop geometry (plane or box depending on depth)
   */
  private createBackdropGeometry(
    x1: number,
    z1: number,
    x2: number,
    z2: number,
    yStart: number,
    yEnd: number,
    direction: string,
    depth: number
  ): Mesh {
    if (depth === 0) {
      // Create plane (thin wall)
      return this.createVerticalPlane(x1, z1, x2, z2, yStart, yEnd);
    } else {
      // Create box (volumetric fog)
      return this.createBackdropBox(x1, z1, x2, z2, yStart, yEnd, direction, depth);
    }
  }

  /**
   * Create a vertical plane mesh
   */
  private createVerticalPlane(
    x1: number,
    z1: number,
    x2: number,
    z2: number,
    yStart: number,
    yEnd: number
  ): Mesh {
    const mesh = new Mesh('backdrop_plane', this.scene);

    // Create quad vertices in ABSOLUTE WORLD COORDINATES
    const positions = [
      x1, yStart, z1,  // Bottom left
      x2, yStart, z2,  // Bottom right
      x2, yEnd, z2,    // Top right
      x1, yEnd, z1     // Top left
    ];

    // Reverse winding order
    const indices = [0, 2, 1, 0, 3, 2];

    // UVs
    const uvs = [0, 0, 1, 0, 1, 1, 0, 1];

    // Calculate normals
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.sqrt(dx * dx + dz * dz);
    const nx = -dz / len;
    const nz = dx / len;
    const normals = [nx, 0, nz, nx, 0, nz, nx, 0, nz, nx, 0, nz];

    // Apply vertex data
    const vertexData = new VertexData();
    vertexData.positions = positions;
    vertexData.indices = indices;
    vertexData.uvs = uvs;
    vertexData.normals = normals;

    vertexData.applyToMesh(mesh);

    mesh.isPickable = false;

    return mesh;
  }

  /**
   * Create a backdrop box (for volumetric fog)
   */
  private createBackdropBox(
    x1: number,
    z1: number,
    x2: number,
    z2: number,
    yStart: number,
    yEnd: number,
    direction: string,
    depth: number
  ): Mesh {
    // Calculate box dimensions based on direction
    let xMin: number, xMax: number, zMin: number, zMax: number;

    switch (direction) {
      case 'n':
        // North: extend in negative Z direction
        xMin = x1;
        xMax = x2;
        zMin = z1 - depth;
        zMax = z1;
        break;

      case 's':
        // South: extend in positive Z direction
        xMin = x1;
        xMax = x2;
        zMin = z1;
        zMax = z1 + depth;
        break;

      case 'e':
        // East: extend in negative X direction
        xMin = x1 - depth;
        xMax = x1;
        zMin = z1;
        zMax = z2;
        break;

      case 'w':
        // West: extend in positive X direction
        xMin = x1;
        xMax = x1 + depth;
        zMin = z1;
        zMax = z2;
        break;

      default:
        return this.createVerticalPlane(x1, z1, x2, z2, yStart, yEnd);
    }

    const width = Math.abs(xMax - xMin);
    const height = yEnd - yStart;
    const depthSize = Math.abs(zMax - zMin);

    const centerX = (xMin + xMax) / 2;
    const centerY = (yStart + yEnd) / 2;
    const centerZ = (zMin + zMax) / 2;

    logger.debug('Creating backdrop box', {
      direction,
      dimensions: { width, height, depth: depthSize },
      center: { x: centerX, y: centerY, z: centerZ },
      bounds: { xMin, xMax, yStart, yEnd, zMin, zMax }
    });

    // Create box using MeshBuilder
    const box = MeshBuilder.CreateBox(
      'backdrop_box',
      {
        width: width,
        height: height,
        depth: depthSize
      },
      this.scene
    );

    // Position box at center
    box.position.set(centerX, centerY, centerZ);
    box.isPickable = false;

    return box;
  }


  /**
   * Get statistics
   */
  getStats(): {
    backdropCount: number;
    materialCacheSize: number;
  } {
    return {
      backdropCount: this.backdropMeshes.size,
      materialCacheSize: this.materialManager.getStats().cachedMaterials,
    };
  }

  /**
   * Dispose all backdrops and cleanup resources
   */
  dispose(): void {
    logger.debug('Disposing BackdropService', {
      backdropCount: this.backdropMeshes.size,
    });

    // Dispose all meshes
    for (const mesh of this.backdropMeshes.values()) {
      mesh.dispose();
    }

    this.backdropMeshes.clear();

    // Dispose materials
    this.materialManager.dispose();

    logger.debug('BackdropService disposed');
  }
}
