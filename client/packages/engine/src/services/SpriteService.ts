/**
 * SpriteService - Manages Babylon.js SpriteManager instances
 *
 * Creates one SpriteManager per unique texture to optimize rendering.
 * Each SpriteManager can render many sprite instances efficiently across all chunks.
 * Integrates with EnvironmentService for wind animation.
 */

import { SpriteManager, Scene, Texture, Sprite } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { EnvironmentService } from './EnvironmentService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('SpriteService');

/**
 * Sprite instance data for wind animation
 */
interface SpriteData {
  sprite: Sprite;
  baseX: number; // Original X position
  baseY: number; // Original Y position
  baseZ: number; // Original Z position
  phaseOffset: number; // Random phase offset for varied animation
  windLeafiness: number; // Per-sprite wind leafiness (0-1, 0 = no wind effect)
  windStability: number; // Per-sprite wind stability (0-1, 0 = maximum movement)
}

/**
 * SpriteService - Manages SpriteManager instances by texture
 *
 * Features:
 * - One SpriteManager per texture (shared across all chunks)
 * - Automatic texture dimension detection
 * - Wind animation for sprites
 * - Integration with EnvironmentService
 */
export class SpriteService {
  private scene: Scene;
  private appContext: AppContext;
  private environmentService?: EnvironmentService;

  // Map: texturePath -> SpriteManager
  private managers: Map<string, SpriteManager> = new Map();

  // Map: texturePath -> texture dimensions { width, height }
  private textureDimensions: Map<string, { width: number; height: number }> = new Map();

  // Array of all sprite data for wind animation
  private spriteData: SpriteData[] = [];

  // Default capacity per manager
  private readonly DEFAULT_CAPACITY = 10000;

  // Animation state
  private totalTime = 0;

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    // Setup animation loop
    this.setupAnimationLoop();

    logger.debug('SpriteService initialized with wind animation');
  }

  /**
   * Set EnvironmentService for accessing wind parameters
   */
  setEnvironmentService(environmentService: EnvironmentService): void {
    this.environmentService = environmentService;
    logger.debug('EnvironmentService set for wind animation');
  }

  /**
   * Get or create a SpriteManager for a given texture
   *
   * @param texturePath Path to the texture
   * @param capacity Optional capacity override (default: 10000)
   * @returns Promise<SpriteManager> instance
   */
  async getManager(texturePath: string, capacity?: number): Promise<SpriteManager> {
    const actualCapacity = capacity ?? this.DEFAULT_CAPACITY;

    // Create cache key with capacity to allow different managers for different capacities
    const cacheKey = `${texturePath}|${actualCapacity}`;

    // Check if manager already exists
    if (this.managers.has(cacheKey)) {
      logger.debug('Reusing existing SpriteManager', { texturePath, capacity: actualCapacity });
      return this.managers.get(cacheKey)!;
    }

    // Create new manager
    const manager = await this.createManager(texturePath, actualCapacity);

    // Cache manager with capacity-specific key
    this.managers.set(cacheKey, manager);
    logger.debug('Created new SpriteManager', { texturePath, capacity: actualCapacity });

    return manager;
  }

  /**
   * Create a new SpriteManager instance (async - waits for texture to load)
   *
   * @param texturePath Path to the texture
   * @param capacity Maximum number of sprites
   * @returns Promise<SpriteManager> instance
   */
  private async createManager(texturePath: string, capacity: number): Promise<SpriteManager> {
    try {
      // Get full texture URL from NetworkService
      const networkService = this.appContext.services.network;
      if (!networkService) {
        throw new Error('NetworkService not available');
      }

      const textureUrl = networkService.getAssetUrl(texturePath);

      logger.debug('Loading sprite texture with credentials', { texturePath, textureUrl });

      // Load texture with credentials
      const blobUrl = await loadTextureUrlWithCredentials(textureUrl);

      // Load texture and wait for it to be ready
      const texture = new Texture(blobUrl, this.scene, false, false);

      await new Promise<void>((resolve, reject) => {
        if (texture.isReady()) {
          resolve();
        } else {
          texture.onLoadObservable.addOnce(() => resolve());
          // Set timeout for texture load
          setTimeout(() => reject(new Error('Texture load timeout')), 10000);
        }
      });

      // Get actual texture dimensions
      const size = texture.getSize();
      const cellWidth = size.width;
      const cellHeight = size.height;

      logger.debug('Sprite texture loaded', {
        texturePath,
        width: cellWidth,
        height: cellHeight,
      });

      // Store dimensions
      this.textureDimensions.set(texturePath, { width: cellWidth, height: cellHeight });

      // Dispose temporary texture (SpriteManager will load its own)
      texture.dispose();

      // Create manager name
      const managerName = `spriteManager_${texturePath.replace(/[^a-zA-Z0-9]/g, '_')}`;

      // Create SpriteManager
      const manager = new SpriteManager(
        managerName,
        textureUrl,
        capacity,
        { width: cellWidth, height: cellHeight },
        this.scene
      );

      // Note: Babylon.js Sprites are always full billboards (face camera completely)
      // There is no built-in Y-axis-only billboard mode for sprites
      // To achieve Y-axis-only billboards, we would need to use Mesh with BILLBOARDMODE_Y instead
      // For now, sprites will face the camera completely (including up/down rotation)

      // Set rendering group for world content
      manager.renderingGroupId = RENDERING_GROUPS.WORLD;

      // Configure transparency
      manager.blendMode = 2; // ALPHA_COMBINE

      if (manager.texture) {
        manager.texture.hasAlpha = true;
        manager.texture.getAlphaFromRGB = false;

        // Reapply after texture loads
        manager.texture.onLoadObservable.addOnce(() => {
          if (manager.texture) {
            manager.texture.hasAlpha = true;
            manager.texture.getAlphaFromRGB = false;
            manager.texture.updateSamplingMode(8); // NEAREST_NEAREST
          }
        });
      }

      logger.debug('SpriteManager created', {
        name: managerName,
        dimensions: `${cellWidth}x${cellHeight}`,
        capacity,
      });

      return manager;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'SpriteService.createManager',
        { texturePath, capacity }
      );
    }
  }

  /**
   * Get texture dimensions for a given texture path
   *
   * @param texturePath Path to the texture
   * @returns Texture dimensions { width, height } or default { width: 16, height: 16 }
   */
  getTextureDimensions(texturePath: string): { width: number; height: number } {
    return this.textureDimensions.get(texturePath) || { width: 16, height: 16 };
  }

  /**
   * Register a sprite for wind animation
   *
   * @param sprite The sprite instance
   * @param windLeafiness Per-sprite wind leafiness (0-1, default 0 = no wind)
   * @param windStability Per-sprite wind stability (0-1, default 0 = maximum movement)
   */
  registerSprite(sprite: Sprite, windLeafiness: number = 0, windStability: number = 0): void {
    const spriteData: SpriteData = {
      sprite,
      baseX: sprite.position.x,
      baseY: sprite.position.y,
      baseZ: sprite.position.z,
      phaseOffset: Math.random() * Math.PI * 2, // Random phase for variation
      windLeafiness: Math.max(0, Math.min(1, windLeafiness)), // Clamp to 0-1
      windStability: Math.max(0, Math.min(1, windStability)), // Clamp to 0-1
    };
    this.spriteData.push(spriteData);

    logger.debug('Sprite registered for wind animation', {
      position: `${sprite.position.x},${sprite.position.y},${sprite.position.z}`,
      windLeafiness,
      windStability,
    });
  }

  /**
   * Setup animation loop for wind effects
   */
  private setupAnimationLoop(): void {
    this.scene.onBeforeRenderObservable.add(() => {
      // Update time
      this.totalTime += this.scene.getEngine().getDeltaTime() / 1000.0;

      // Skip if no environment service or no sprites
      if (!this.environmentService || this.spriteData.length === 0) {
        return;
      }

      // Get wind parameters
      const windParams = this.environmentService.getWindParameters();

      // Animate each sprite
      for (const data of this.spriteData) {
        // Base sway wave (smooth sinusoidal)
        const baseWave =
          Math.sin(this.totalTime * windParams.windSwayFactor + data.phaseOffset) *
          windParams.windStrength;

        // Gust effect (faster, irregular pulses)
        const gustWave =
          Math.sin(this.totalTime * windParams.windSwayFactor * 2.3 + data.phaseOffset * 0.7) *
          windParams.windGustStrength;
        const gustModulation = Math.sin(this.totalTime * windParams.windSwayFactor * 0.7);

        // Secondary wave for more organic movement (leafiness effect) - per sprite
        const leafWave =
          Math.sin(this.totalTime * windParams.windSwayFactor * 1.7 + data.phaseOffset) *
          data.windLeafiness;

        // Combine waves
        const totalWave = (baseWave + gustWave * 0.5 * gustModulation + leafWave * 0.3) * 0.15; // Scale down amplitude

        // Apply stability (reduces movement) - per sprite
        const stabilityFactor = 1.0 - data.windStability;

        // Apply wind direction with stability
        const offsetX = windParams.windDirection.x * totalWave * stabilityFactor;
        const offsetZ = windParams.windDirection.z * totalWave * stabilityFactor;

        // Update sprite position (add offset to base position)
        data.sprite.position.x = data.baseX + offsetX;
        data.sprite.position.z = data.baseZ + offsetZ;
      }
    });
  }

  /**
   * Remove sprites that have been disposed
   * Call this when chunks are unloaded
   */
  removeDisposedSprites(): void {
    const beforeCount = this.spriteData.length;
    this.spriteData = this.spriteData.filter((data) => {
      try {
        // Check if sprite still exists (no isDisposed() method on Sprite)
        return data.sprite && data.sprite.position !== undefined;
      } catch {
        return false;
      }
    });
    const removed = beforeCount - this.spriteData.length;
    if (removed > 0) {
      logger.debug('Removed disposed sprites from animation', { count: removed });
    }
  }

  /**
   * Get statistics about managed sprite managers
   */
  getStats(): { managerCount: number; textures: string[]; spriteCount: number } {
    return {
      managerCount: this.managers.size,
      textures: Array.from(this.managers.keys()),
      spriteCount: this.spriteData.length,
    };
  }

  /**
   * Clear all managers (cleanup)
   */
  dispose(): void {
    for (const [texturePath, manager] of this.managers) {
      manager.dispose();
      logger.debug('Disposed SpriteManager', { texturePath });
    }
    this.managers.clear();
    this.textureDimensions.clear();
    this.spriteData = [];

    logger.debug('SpriteService disposed');
  }
}
