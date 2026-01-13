/**
 * ThinInstancesService - Manages thin instance rendering for grass-like objects
 *
 * Uses Babylon.js Thin Instances for extreme performance with Y-axis billboard shader.
 * Supports GPU-based wind animation and per-block instance configuration.
 */

import { Mesh, MeshBuilder, Scene, Matrix, Vector3, StandardMaterial, Texture, type IDisposable } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ShaderService } from './ShaderService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('ThinInstancesService');

/**
 * Disposable wrapper for a single thin instance mesh
 * Allows DisposableResources to manage individual mesh lifecycle
 */
class ThinInstanceMeshDisposable implements IDisposable {
  constructor(
    private service: ThinInstancesService,
    private mesh: Mesh
  ) {}

  dispose(): void {
    this.service.disposeSingleMesh(this.mesh);
  }
}

/**
 * Configuration for a thin instances group
 */
interface ThinInstanceConfig {
  texturePath: string;
  instanceCount: number;
  blockPosition: { x: number; y: number; z: number };
  offset?: { x: number; y: number; z: number }; // Optional offset for positioning
  scaling?: { x: number; y: number; z: number }; // Optional scaling factors
  wind?: {
    leafiness: number;
    stability: number;
    leverUp: number;
    leverDown: number;
  }; // Optional wind parameters
}

/**
 * Thin instances group data
 */
interface ThinInstanceGroup {
  mesh: Mesh;
  matricesData: Float32Array;
  instanceCount: number;
  chunkKey: string;
}

export class ThinInstancesService {
  private scene: Scene;
  private appContext: AppContext;
  private shaderService?: ShaderService;

  // Map: chunkKey -> ThinInstanceGroup[]
  private instanceGroups: Map<string, ThinInstanceGroup[]> = new Map();

  // Base mesh template (will be cloned for each texture)
  private baseMesh?: Mesh;

  // Material cache: texturePath -> Material
  private materialCache: Map<string, StandardMaterial> = new Map();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    this.createBaseMesh();

    logger.debug('ThinInstancesService initialized');
  }

  /**
   * Set shader service
   */
  setShaderService(shaderService: ShaderService): void {
    this.shaderService = shaderService;
    logger.debug('ShaderService set');
  }

  /**
   * Create base mesh template (vertical quad)
   * Note: This is no longer used, kept for backwards compatibility
   */
  private createBaseMesh(): void {
    // Create a simple vertical quad
    const mesh = MeshBuilder.CreateGround('thinInstanceBase', { width: 1, height: 2 }, this.scene);
    mesh.rotation.x = Math.PI * 0.5; // Rotate to vertical
    mesh.bakeCurrentTransformIntoVertices(); // Bake rotation into vertices
    mesh.isVisible = false; // Template is invisible

    this.baseMesh = mesh;

    logger.debug('Base mesh template created');
  }

  /**
   * Create a mesh with the correct aspect ratio for a texture
   */
  private createMeshForAspectRatio(aspectRatio: number, name: string): Mesh {
    const height = 2;
    const width = height * aspectRatio;

    const mesh = MeshBuilder.CreateGround(name, { width, height }, this.scene);

    // Rotate to vertical
    mesh.rotation.x = Math.PI * 0.5;

    // Shift mesh up so it stands on the ground (pivot at bottom, not center)
    // After rotation, the mesh needs to be shifted up by height/2
    mesh.position.y = height / 2;

    // Bake rotation and position into vertices
    mesh.bakeCurrentTransformIntoVertices();

    // Flip UV coordinates vertically to correct texture orientation
    const uvs = mesh.getVerticesData('uv');
    if (uvs) {
      for (let i = 1; i < uvs.length; i += 2) {
        uvs[i] = 1.0 - uvs[i]; // Invert V coordinate
      }
      mesh.setVerticesData('uv', uvs);
    }

    // Set rendering group for world content
    mesh.renderingGroupId = RENDERING_GROUPS.WORLD;

    return mesh;
  }

  /**
   * Create thin instances for a block
   *
   * @param config Instance configuration
   * @param chunkKey Parent chunk key
   * @returns Created mesh and disposable for cleanup
   */
  async createInstances(config: ThinInstanceConfig, chunkKey: string): Promise<{ mesh: Mesh; disposable: IDisposable }> {
    // Get or create material first (to load texture and get dimensions)
    const materialResult = await this.getMaterial(config.texturePath);
    const material = materialResult.material;
    const aspectRatio = materialResult.aspectRatio;

    // Create mesh with correct aspect ratio for this texture
    const mesh = this.createMeshForAspectRatio(
      aspectRatio,
      `thinInstances_${config.blockPosition.x}_${config.blockPosition.y}_${config.blockPosition.z}`
    );
    mesh.isVisible = true;
    mesh.material = material;

    // Create instance data buffer: matrix (16 floats) + wind params (4 floats) = 20 floats per instance
    const instanceData = new Float32Array(20 * config.instanceCount);
    const m = Matrix.Identity();

    let index = 0;
    const blockX = config.blockPosition.x;
    const blockY = config.blockPosition.y;
    const blockZ = config.blockPosition.z;

    // Use configured offsets or defaults
    const offsetX = config.offset?.x ?? 0;
    const offsetY = config.offset?.y ?? 0;
    const offsetZ = config.offset?.z ?? 0;

    // Use configured scaling or defaults
    const scaleX = config.scaling?.x ?? 1;
    const scaleY = config.scaling?.y ?? 1;
    const scaleZ = config.scaling?.z ?? 1;

    // Distribute instances randomly within block bounds
    for (let i = 0; i < config.instanceCount; i++) {
      // Random position within block (0.8 = 80% of block size for margin)
      const randomX = (Math.random() - 0.5) * 0.8;
      const randomZ = (Math.random() - 0.5) * 0.8;

      // Set scaling using Matrix API
      Matrix.ScalingToRef(scaleX, scaleY, scaleZ, m);

      // Set instance position using Matrix API (block position + center offset + configured offset + random offset)
      m.setTranslation(
        new Vector3(
          blockX + 0.5 + offsetX + randomX,
          blockY + offsetY,
          blockZ + 0.5 + offsetZ + randomZ
        )
      );

      // Copy matrix to buffer (floats 0-15)
      m.copyToArray(instanceData, index * 20);

      // Add wind parameters (floats 16-19) with random variation if wind is configured
      if (config.wind) {
        // Leafiness with ±20% variation
        instanceData[index * 20 + 16] = config.wind.leafiness * (0.8 + Math.random() * 0.4);
        // Stability with ±20% variation
        instanceData[index * 20 + 17] = config.wind.stability * (0.8 + Math.random() * 0.4);
        // Lever values without variation (consistent geometry)
        instanceData[index * 20 + 18] = config.wind.leverUp;
        instanceData[index * 20 + 19] = config.wind.leverDown;
      } else {
        // No wind: set all to 0.0 (wind disabled in shader)
        instanceData[index * 20 + 16] = 0.0;
        instanceData[index * 20 + 17] = 0.0;
        instanceData[index * 20 + 18] = 0.0;
        instanceData[index * 20 + 19] = 0.0;
      }

      index++;
    }

    // Extract matrix data (every 20 floats, first 16 values)
    const matricesData = new Float32Array(16 * config.instanceCount);
    for (let i = 0; i < config.instanceCount; i++) {
      for (let j = 0; j < 16; j++) {
        matricesData[i * 16 + j] = instanceData[i * 20 + j];
      }
    }

    // Set matrix buffer
    mesh.thinInstanceSetBuffer('matrix', matricesData, 16);

    // Set wind parameters buffer if wind is configured
    if (config.wind) {
      const windData = new Float32Array(4 * config.instanceCount);
      for (let i = 0; i < config.instanceCount; i++) {
        windData[i * 4 + 0] = instanceData[i * 20 + 16];
        windData[i * 4 + 1] = instanceData[i * 20 + 17];
        windData[i * 4 + 2] = instanceData[i * 20 + 18];
        windData[i * 4 + 3] = instanceData[i * 20 + 19];
      }
      mesh.thinInstanceSetBuffer('windParams', windData, 4);
    }

    // Store group data
    const group: ThinInstanceGroup = {
      mesh,
      matricesData,
      instanceCount: config.instanceCount,
      chunkKey,
    };

    // Add to chunk groups
    if (!this.instanceGroups.has(chunkKey)) {
      this.instanceGroups.set(chunkKey, []);
    }
    this.instanceGroups.get(chunkKey)!.push(group);

    logger.debug('Thin instances created', {
      position: config.blockPosition,
      count: config.instanceCount,
      chunkKey,
    });

    // Create disposable for cleanup via DisposableResources
    const disposable = new ThinInstanceMeshDisposable(this, mesh);

    return { mesh, disposable };
  }

  /**
   * Get or create material with optional Y-axis billboard shader
   * Returns material and aspect ratio of the texture
   */
  private async getMaterial(texturePath: string): Promise<{ material: StandardMaterial; aspectRatio: number }> {
    // Check cache
    const cachedMaterial = this.materialCache.get(texturePath);
    if (cachedMaterial) {
      logger.debug('Using cached material', { texturePath });
      // Get aspect ratio from cached texture
      const texture = cachedMaterial.diffuseTexture as Texture;
      const aspectRatio = texture ? texture.getSize().width / texture.getSize().height : 1;
      return { material: cachedMaterial, aspectRatio };
    }

    logger.debug('🎨 Creating material for thin instances', { texturePath });

    // Try to use shader service first
    if (this.shaderService && typeof (this.shaderService as any).createThinInstanceMaterial === 'function') {
      try {
        const material = await (this.shaderService as any).createThinInstanceMaterial(texturePath);
        if (material) {
          this.materialCache.set(texturePath, material);
          logger.debug('✅ Shader material created', { texturePath });
          // Get aspect ratio from shader material's texture
          const texture = material.getEffect()?.getTexture('textureSampler') as Texture;
          const aspectRatio = texture ? texture.getSize().width / texture.getSize().height : 1;
          return { material, aspectRatio };
        }
      } catch (error) {
        logger.warn('Failed to create shader material, using fallback', { error });
      }
    }

    // Fallback: standard material with texture from NetworkService
    const networkService = this.appContext.services.network;
    if (!networkService) {
      logger.error('NetworkService not available', { texturePath });

      // Ultimate fallback: create material without texture
      const material = new StandardMaterial(`thinInstance_${texturePath}`, this.scene);
      material.backFaceCulling = false;
      material.specularColor.set(0, 0, 0);
      this.materialCache.set(texturePath, material);
      return { material, aspectRatio: 1 };
    }

    // Create material
    const material = new StandardMaterial(`thinInstance_${texturePath}`, this.scene);

    // Load texture with credentials
    const url = networkService.getAssetUrl(texturePath);
    const blobUrl = await loadTextureUrlWithCredentials(url);
    const texture = new Texture(blobUrl, this.scene);

    // Wait for texture to load to get dimensions
    await new Promise<void>((resolve) => {
      if (texture.isReady()) {
        resolve();
      } else {
        texture.onLoadObservable.addOnce(() => resolve());
      }
    });

    // Get texture dimensions
    const size = texture.getSize();
    const aspectRatio = size.width / size.height;

    logger.debug('Texture loaded', { texturePath, width: size.width, height: size.height, aspectRatio });

    // Configure texture
    texture.hasAlpha = true;
    texture.getAlphaFromRGB = false;
    texture.updateSamplingMode(Texture.NEAREST_SAMPLINGMODE); // Pixel-perfect rendering

    material.diffuseTexture = texture;
    material.backFaceCulling = false;
    material.specularColor.set(0, 0, 0); // No specular

    // Cache material
    this.materialCache.set(texturePath, material);

    logger.debug('Material created with texture', { texturePath, aspectRatio });

    return { material, aspectRatio };
  }

  /**
   * Dispose a single thin instance mesh
   * Removes it from the instance groups and disposes the mesh
   */
  disposeSingleMesh(mesh: Mesh): void {
    // Find and remove the group containing this mesh
    for (const [chunkKey, groups] of this.instanceGroups.entries()) {
      const index = groups.findIndex(group => group.mesh === mesh);
      if (index !== -1) {
        const group = groups[index];
        group.mesh.dispose();
        groups.splice(index, 1);

        // If no more groups for this chunk, remove the entry
        if (groups.length === 0) {
          this.instanceGroups.delete(chunkKey);
        }

        logger.debug('Single thin instance mesh disposed', {
          chunkKey,
          remainingGroupsInChunk: groups.length
        });
        return;
      }
    }

    logger.warn('Mesh not found in instance groups for disposal', { mesh: mesh.name });
  }

  /**
   * Dispose instances for a chunk
   */
  disposeChunkInstances(chunkKey: string): void {
    const groups = this.instanceGroups.get(chunkKey);
    if (!groups) {
      return;
    }

    for (const group of groups) {
      group.mesh.dispose();
    }

    this.instanceGroups.delete(chunkKey);

    logger.debug('Chunk instances disposed', { chunkKey, groupCount: groups.length });
  }

  /**
   * Dispose all instances
   */
  dispose(): void {
    for (const groups of this.instanceGroups.values()) {
      for (const group of groups) {
        group.mesh.dispose();
      }
    }

    this.instanceGroups.clear();

    // Dispose materials
    for (const material of this.materialCache.values()) {
      material.dispose();
    }
    this.materialCache.clear();

    this.baseMesh?.dispose();

    logger.debug('ThinInstancesService disposed');
  }

  /**
   * Get statistics
   */
  getStats(): { chunkCount: number; totalInstances: number; groupCount: number } {
    let totalInstances = 0;
    let groupCount = 0;

    for (const groups of this.instanceGroups.values()) {
      groupCount += groups.length;
      for (const group of groups) {
        totalInstances += group.instanceCount;
      }
    }

    return {
      chunkCount: this.instanceGroups.size,
      totalInstances,
      groupCount,
    };
  }
}
