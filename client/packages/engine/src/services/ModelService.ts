/**
 * ModelService - 3D Model loading and caching
 *
 * Manages 3D model assets:
 * - Loads models from asset server (.babylon, .glb, etc.)
 * - Caches loaded models for reuse
 * - Provides template meshes for cloning
 * - Handles model disposal and cache cleanup
 */

import { Mesh, SceneLoader, Scene, AssetContainer } from '@babylonjs/core';
import '@babylonjs/loaders'; // Required for .babylon and .glb file formats
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { NetworkService } from './NetworkService';

const logger = getLogger('ModelService');

/**
 * ModelService configuration
 */
export interface ModelServiceConfig {
  /** Maximum number of models in cache */
  maxCacheSize?: number;

  /** Cache eviction timeout in milliseconds (models not used for this time are evicted) */
  cacheEvictionTimeout?: number;

  /** Interval for cache cleanup in milliseconds */
  cacheCleanupInterval?: number;
}

/**
 * Cached model entry
 */
interface CachedModel {
  /** Template mesh (disabled, used for cloning) */
  mesh: Mesh;

  /** Last access timestamp */
  lastAccess: number;

  /** Model path */
  path: string;
}

/**
 * Cached GLB container entry
 */
interface CachedGlbContainer {
  /** Asset container (not added to scene, used for instantiation) */
  container: AssetContainer;

  /** Last access timestamp */
  lastAccess: number;

  /** Model path */
  path: string;
}

/**
 * ModelService - Manages 3D model loading and caching
 *
 * Features:
 * - Lazy loading of 3D models from asset server
 * - LRU cache with automatic eviction
 * - Template mesh management (disabled meshes for cloning)
 * - Support for multiple formats (.babylon, .glb, etc.)
 */
export class ModelService {
  private scene: Scene;
  private networkService: NetworkService;
  private config: Required<ModelServiceConfig>;

  // Model cache: path -> CachedModel
  private modelCache: Map<string, CachedModel> = new Map();

  // GLB container cache: path -> CachedGlbContainer
  private glbContainerCache: Map<string, CachedGlbContainer> = new Map();

  // Cache cleanup interval
  private cleanupInterval?: NodeJS.Timeout;

  constructor(scene: Scene, appContext: AppContext, config?: ModelServiceConfig) {
    if (!appContext.services.network) {
      throw new Error('NetworkService is required for ModelService');
    }

    this.scene = scene;
    this.networkService = appContext.services.network;

    // Default configuration
    this.config = {
      maxCacheSize: config?.maxCacheSize ?? 100,
      cacheEvictionTimeout: config?.cacheEvictionTimeout ?? 600000, // 10 minutes
      cacheCleanupInterval: config?.cacheCleanupInterval ?? 120000, // 2 minutes
    };

    // Configure BabylonJS to use credentials for all requests
    // This is required for cookie-based authentication
    if (typeof XMLHttpRequest !== 'undefined') {
      const originalOpen = XMLHttpRequest.prototype.open;
      XMLHttpRequest.prototype.open = function(...args: any[]) {
        originalOpen.apply(this, args as any);
        this.withCredentials = true;
      };
    }

    logger.debug('ModelService initialized', { config: this.config });

    // Start cache cleanup
    this.startCacheCleanup();
  }

  /**
   * Load GLB container (for entities with animations)
   * Returns AssetContainer that can be instantiated for each entity
   *
   * @param modelPath - Relative model path (e.g., "models/cow.glb")
   * @returns AssetContainer for instantiation, or null if loading failed
   */
  async loadGlbContainer(modelPath: string): Promise<AssetContainer | null> {
    try {
      // Check cache
      const cached = this.glbContainerCache.get(modelPath);
      if (cached) {
        cached.lastAccess = Date.now();
        logger.debug('GLB container cache hit', { modelPath });
        return cached.container;
      }

      // Load from asset server
      logger.debug('Loading GLB model from asset server', { modelPath });

      // Get full model URL via NetworkService with cache-busting timestamp
      const timestamp = Date.now();
      const baseUrl = this.networkService.getAssetUrl(modelPath);
      const fullModelUrl = `${baseUrl}?t=${timestamp}`;

      // Load the GLB model using LoadAssetContainerAsync
      let container: AssetContainer;
      try {
        container = await SceneLoader.LoadAssetContainerAsync(
          fullModelUrl,
          '',
          this.scene,
          undefined,
          '.glb'
        );
      } catch (loadError) {
        throw new Error(
          `Failed to load GLB model from URL: ${fullModelUrl}\n` +
          `Error: ${loadError instanceof Error ? loadError.message : String(loadError)}\n` +
          `This usually means:\n` +
          `- The file does not exist on the server\n` +
          `- The URL is incorrect\n` +
          `- The file format is invalid or corrupted\n` +
          `- CORS is blocking the request`
        );
      }

      if (!container) {
        throw new Error(`LoadAssetContainerAsync returned null for: ${fullModelUrl}`);
      }

      if (!container.meshes || container.meshes.length === 0) {
        throw new Error(`No meshes found in GLB model: ${fullModelUrl}`);
      }

      // Don't add to scene or merge - keep container for instantiation
      // Add to cache
      const cachedContainer: CachedGlbContainer = {
        container,
        lastAccess: Date.now(),
        path: modelPath,
      };

      this.glbContainerCache.set(modelPath, cachedContainer);
      this.evictGlbContainerCache();

      logger.debug('GLB container loaded and cached', {
        modelPath,
        meshCount: container.meshes.length,
        animationCount: container.animationGroups.length,
      });
      return container;
    } catch (error) {
      ExceptionHandler.handle(error, 'ModelService.loadGlbModel', {
        modelPath,
        troubleshooting: [
          'Make sure the GLB file exists in the assets directory on the server',
          'Check that the file path is correct (e.g., "models/cow.glb")',
          'Verify the GLB file is valid (try opening in Babylon Sandbox)',
          'Check CORS headers on the server',
          'Verify worldInfo.assetPath is configured correctly',
        ],
      });
      return null;
    }
  }

  /**
   * Load or get cached model
   * Returns a template mesh (disabled) that should be cloned for use
   *
   * @param modelPath - Relative model path (e.g., "models/skull.babylon")
   * @returns Template mesh for cloning, or null if loading failed
   */
  async loadModel(modelPath: string): Promise<Mesh | null> {
    // Check if this is a GLB file and use specialized loader
    if (modelPath.toLowerCase().endsWith('.glb') || modelPath.toLowerCase().endsWith('.gltf')) {
      const container = await this.loadGlbContainer(modelPath);
      if (!container || !container.meshes || container.meshes.length === 0) {
        return null;
      }
      // Return first mesh from container (usually the root mesh)
      return container.meshes[0] as Mesh;
    }
    try {
      // Check cache
      const cached = this.modelCache.get(modelPath);
      if (cached) {
        cached.lastAccess = Date.now();
        logger.debug('Model cache hit', { modelPath });
        return cached.mesh;
      }

      // Load from asset server
      logger.debug('Loading model from asset server', { modelPath });

      // Get full model URL via NetworkService with cache-busting timestamp
      const timestamp = Date.now();
      const baseUrl = this.networkService.getAssetUrl(modelPath);
      const fullModelUrl = `${baseUrl}?t=${timestamp}`;

      // Parse URL to get root path and filename
      const lastSlashIndex = fullModelUrl.lastIndexOf('/');
      const rootUrl = fullModelUrl.substring(0, lastSlashIndex + 1);
      const filename = fullModelUrl.substring(lastSlashIndex + 1);

      // Load the model
      let result;
      try {
        result = await SceneLoader.ImportMeshAsync(
          '', // Load all meshes
          rootUrl,
          filename,
          this.scene
        );
      } catch (loadError) {
        throw new Error(
          `Failed to load model from URL: ${fullModelUrl}\n` +
          `Error: ${loadError instanceof Error ? loadError.message : String(loadError)}\n` +
          `This usually means:\n` +
          `- The file does not exist on the server\n` +
          `- The URL is incorrect\n` +
          `- The file format is invalid or corrupted`
        );
      }

      if (!result) {
        throw new Error(`SceneLoader returned null for: ${fullModelUrl}`);
      }

      if (!result.meshes) {
        throw new Error(`SceneLoader result has no meshes property for: ${fullModelUrl}`);
      }

      if (result.meshes.length === 0) {
        throw new Error(`No meshes found in model: ${fullModelUrl}. The file might be empty or invalid.`);
      }

      // Merge all meshes into one for better performance
      const allMeshes = result.meshes.filter(m => m instanceof Mesh) as Mesh[];

      if (allMeshes.length === 0) {
        throw new Error('No Mesh instances found in loaded model');
      }

      let templateMesh: Mesh;

      if (allMeshes.length > 1) {
        const merged = Mesh.MergeMeshes(
          allMeshes,
          true, // disposeSource
          true, // allow32BitsIndices
          undefined,
          false, // subdivideWithSubMeshes
          true // multiMultiMaterials
        );

        if (!merged) {
          logger.warn('Failed to merge meshes, using first mesh', { modelPath });
          templateMesh = allMeshes[0];
        } else {
          templateMesh = merged;
          templateMesh.name = `model_template_${modelPath}`;
        }
      } else {
        templateMesh = allMeshes[0];
      }

      // Disable template mesh (it's just for cloning)
      templateMesh.setEnabled(false);

      // Add to cache
      const cachedModel: CachedModel = {
        mesh: templateMesh,
        lastAccess: Date.now(),
        path: modelPath,
      };

      this.modelCache.set(modelPath, cachedModel);
      this.evictCache();

      logger.debug('Model loaded and cached', { modelPath });
      return templateMesh;
    } catch (error) {
      ExceptionHandler.handle(error, 'ModelService.loadModel', {
        modelPath,
        troubleshooting: [
          'Make sure the model file exists in the assets directory on the server',
          'Check that the file path is correct (e.g., "models/skull.babylon")',
          'Verify the model file format is valid',
          'Check that NetworkService.getAssetUrl() returns correct URL',
          'Verify worldInfo.assetPath is configured correctly',
        ],
      });
      return null;
    }
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): {
    cacheSize: number;
    maxCacheSize: number;
    cachedModels: string[];
  } {
    return {
      cacheSize: this.modelCache.size,
      maxCacheSize: this.config.maxCacheSize,
      cachedModels: Array.from(this.modelCache.keys()),
    };
  }

  /**
   * Clear the model cache
   * Disposes all cached meshes and containers
   */
  clearCache(): void {
    for (const cached of this.modelCache.values()) {
      cached.mesh.dispose();
    }
    this.modelCache.clear();

    for (const cached of this.glbContainerCache.values()) {
      cached.container.dispose();
    }
    this.glbContainerCache.clear();

    logger.debug('Model cache cleared');
  }

  /**
   * Remove specific model from cache
   */
  removeModel(modelPath: string): void {
    const cached = this.modelCache.get(modelPath);
    if (cached) {
      cached.mesh.dispose();
      this.modelCache.delete(modelPath);
      logger.debug('Model removed from cache', { modelPath });
    }
  }

  /**
   * Dispose service (stop cleanup interval and clear cache)
   */
  dispose(): void {
    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = undefined;
      logger.debug('Cache cleanup stopped');
    }

    this.clearCache();
  }

  /**
   * Start cache cleanup interval
   */
  private startCacheCleanup(): void {
    this.cleanupInterval = setInterval(() => {
      this.cleanupCache();
    }, this.config.cacheCleanupInterval);

    logger.debug('Cache cleanup started', { interval: this.config.cacheCleanupInterval });
  }

  /**
   * Cleanup expired models from cache
   */
  private cleanupCache(): void {
    const now = Date.now();
    const evictionThreshold = now - this.config.cacheEvictionTimeout;

    let evictedCount = 0;

    // Evict old models
    for (const [path, cached] of this.modelCache.entries()) {
      if (cached.lastAccess < evictionThreshold) {
        cached.mesh.dispose();
        this.modelCache.delete(path);
        evictedCount++;
      }
    }

    if (evictedCount > 0) {
      logger.debug('Cache cleanup evicted models', { count: evictedCount });
    }
  }

  /**
   * Evict oldest models if cache is full
   */
  private evictCache(): void {
    if (this.modelCache.size <= this.config.maxCacheSize) {
      return;
    }

    // Find model with oldest lastAccess
    let oldestPath: string | null = null;
    let oldestAccess = Date.now();

    for (const [path, cached] of this.modelCache.entries()) {
      if (cached.lastAccess < oldestAccess) {
        oldestAccess = cached.lastAccess;
        oldestPath = path;
      }
    }

    if (oldestPath) {
      const cached = this.modelCache.get(oldestPath)!;
      cached.mesh.dispose();
      this.modelCache.delete(oldestPath);
      logger.debug('Model evicted from cache', { modelPath: oldestPath });
    }
  }

  /**
   * Evict oldest GLB containers if cache is full
   */
  private evictGlbContainerCache(): void {
    if (this.glbContainerCache.size <= this.config.maxCacheSize) {
      return;
    }

    // Find container with oldest lastAccess
    let oldestPath: string | null = null;
    let oldestAccess = Date.now();

    for (const [path, cached] of this.glbContainerCache.entries()) {
      if (cached.lastAccess < oldestAccess) {
        oldestAccess = cached.lastAccess;
        oldestPath = path;
      }
    }

    if (oldestPath) {
      const cached = this.glbContainerCache.get(oldestPath)!;
      cached.container.dispose();
      this.glbContainerCache.delete(oldestPath);
      logger.debug('GLB container evicted from cache', { modelPath: oldestPath });
    }
  }
}
