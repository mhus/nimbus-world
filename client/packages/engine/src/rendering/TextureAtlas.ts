/**
 * Dynamic Texture Atlas System
 *
 * Loads individual textures from the asset server and builds a dynamic texture atlas
 * at runtime for efficient rendering.
 *
 * Adapted for Client 2.0 with BlockType/BlockModifier system.
 *
 * @deprecated TextureAtlas is deprecated in favor of MaterialService.getMaterial()
 * which loads textures directly and supports per-material properties like
 * opacity, transparency modes, sampling modes, and shader effects.
 *
 * The new approach allows for:
 * - Individual texture properties per material
 * - Shader effect support
 * - Better caching based on complete material properties
 * - Direct integration with ShaderService
 *
 * This class is kept for backwards compatibility but should not be used
 * for new implementations.
 */

import { DynamicTexture, StandardMaterial, Color3, Scene, RawTexture } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { TextureDefinition } from '@nimbus/shared';
import { loadImageWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('TextureAtlas');

/**
 * UV coordinates for a texture in the atlas
 */
export interface AtlasUV {
  u0: number;  // Left
  v0: number;  // Top
  u1: number;  // Right
  v1: number;  // Bottom
}

/**
 * Face-specific UV mapping for blocks with different textures per face
 */
export interface BlockFaceUVs {
  top: AtlasUV;
  bottom: AtlasUV;
  sides: AtlasUV;
  north?: AtlasUV;  // Optional individual face UVs
  south?: AtlasUV;
  east?: AtlasUV;
  west?: AtlasUV;
}

/**
 * Dynamic Texture Atlas - Builds runtime atlas from server textures
 *
 * @deprecated Use MaterialService.getMaterial() instead
 */
export class TextureAtlas {
  private scene: Scene;
  private appContext: AppContext;

  // Atlas configuration
  private textureSize: number = 16;
  private maxAtlasSize: number = 2048;
  private texturesPerRow: number;
  private uvSize: number;

  // Dynamic atlas canvas and texture
  private atlasCanvas?: HTMLCanvasElement;
  private atlasContext?: CanvasRenderingContext2D;
  private atlasTexture?: DynamicTexture;
  private material?: StandardMaterial;

  // Texture tracking
  private textureMap: Map<string, { x: number; y: number }> = new Map();
  private nextSlot = 0;
  private textureLoaded: Set<string> = new Set();

  // Image cache: Map<path, HTMLImageElement> to avoid loading same image multiple times
  private imageCache: Map<string, HTMLImageElement> = new Map();

  // UV cache
  private uvCache: Map<string, AtlasUV> = new Map();

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    this.texturesPerRow = Math.floor(this.maxAtlasSize / this.textureSize);
    this.uvSize = 1.0 / this.texturesPerRow;

    logger.debug('TextureAtlas initialized', {
      slots: `${this.texturesPerRow}x${this.texturesPerRow}`,
      atlasSize: `${this.maxAtlasSize}x${this.maxAtlasSize}px`,
    });
  }

  /**
   * Initialize the dynamic texture atlas
   */
  async load(): Promise<void> {
    logger.debug('Creating dynamic texture atlas');

    try {
      // Create our own canvas for drawing
      this.atlasCanvas = document.createElement('canvas');
      this.atlasCanvas.width = this.maxAtlasSize;
      this.atlasCanvas.height = this.maxAtlasSize;
      this.atlasContext = this.atlasCanvas.getContext('2d', { willReadFrequently: true })!;

      // Fill with transparent background
      this.atlasContext.fillStyle = 'rgba(0, 0, 0, 0)';
      this.atlasContext.fillRect(0, 0, this.maxAtlasSize, this.maxAtlasSize);

      // Create dynamic texture from our canvas
      this.atlasTexture = new DynamicTexture(
        'dynamicAtlas',
        this.maxAtlasSize,
        this.scene,
        false,
        RawTexture.NEAREST_NEAREST
      );

      // Initial update from canvas
      this.updateAtlasTexture();

      // Create material
      this.material = new StandardMaterial('atlasMaterial', this.scene);
      this.material.diffuseTexture = this.atlasTexture;
      this.material.specularColor = new Color3(0, 0, 0);
      this.material.backFaceCulling = true;

      // Enable alpha for atlas texture (contains both transparent and opaque textures)
      this.atlasTexture.hasAlpha = true;

      logger.debug('Dynamic atlas created', {
        ready: this.atlasTexture.isReady(),
      });
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'TextureAtlas.load');
    }
  }

  /**
   * Update the Babylon.js texture from canvas
   */
  private updateAtlasTexture(): void {
    if (!this.atlasTexture || !this.atlasCanvas || !this.atlasContext) return;

    // Get the DynamicTexture's context
    const textureContext = this.atlasTexture.getContext();

    // Clear the texture context
    textureContext.clearRect(0, 0, this.maxAtlasSize, this.maxAtlasSize);

    // Draw our canvas onto the texture's canvas
    textureContext.drawImage(this.atlasCanvas, 0, 0);

    // Tell Babylon.js to update the GPU texture with invertY = false
    this.atlasTexture.update(false);

    // Force mark the material as dirty so Babylon.js knows to re-render with the new texture
    if (this.material) {
      this.material.markDirty();
    }
  }

  /**
   * Load a texture into the dynamic atlas
   *
   * @param textureDef Texture definition
   * @returns Atlas position
   */
  async loadTextureIntoAtlas(textureDef: TextureDefinition): Promise<{ x: number; y: number }> {
    // Create cache key
    const cacheKey = this.createCacheKey(textureDef);

    // Check if already loaded
    if (this.textureMap.has(cacheKey)) {
      return this.textureMap.get(cacheKey)!;
    }

    // Check if we have space
    const maxSlots = this.texturesPerRow * this.texturesPerRow;
    if (this.nextSlot >= maxSlots) {
      logger.warn('Atlas full! Cannot load texture', { cacheKey });
      return { x: 0, y: 0 };
    }

    // Calculate slot position
    const slotX = this.nextSlot % this.texturesPerRow;
    const slotY = Math.floor(this.nextSlot / this.texturesPerRow);
    this.nextSlot++;

    try {
      // Check if image is already cached
      let img: HTMLImageElement;
      if (this.imageCache.has(textureDef.path)) {
        img = this.imageCache.get(textureDef.path)!;
      } else {
        // Load image from server
        const networkService = this.appContext.services.network;
        if (!networkService) {
          throw new Error('NetworkService not available');
        }

        const url = networkService.getAssetUrl(textureDef.path);
        logger.debug('Loading texture into atlas', { path: textureDef.path, url });
        img = await this.loadImage(url);

        // Cache the loaded image for future use
        this.imageCache.set(textureDef.path, img);
        logger.debug('Loaded and cached image', { path: textureDef.path });
      }

      // Draw into atlas canvas
      const pixelX = slotX * this.textureSize;
      const pixelY = slotY * this.textureSize;

      if (textureDef.uvMapping) {
        // Draw sub-texture from source image
        this.atlasContext!.drawImage(
          img,
          textureDef.uvMapping.x,
          textureDef.uvMapping.y,
          textureDef.uvMapping.w,
          textureDef.uvMapping.h, // Source
          pixelX,
          pixelY,
          this.textureSize,
          this.textureSize // Destination
        );
      } else {
        // Draw full texture
        this.atlasContext!.drawImage(img, pixelX, pixelY, this.textureSize, this.textureSize);
      }

      // Update dynamic texture from canvas
      this.updateAtlasTexture();

      // Cache position
      const position = { x: slotX, y: slotY };
      this.textureMap.set(cacheKey, position);
      this.textureLoaded.add(cacheKey);

      return position;
    } catch (error) {
      ExceptionHandler.handle(error, 'TextureAtlas.loadTextureIntoAtlas', { cacheKey });
      return { x: 0, y: 0 };
    }
  }

  /**
   * Load image from URL with credentials
   */
  private loadImage(url: string): Promise<HTMLImageElement> {
    return loadImageWithCredentials(url);
  }

  /**
   * Get UV coordinates for a texture in the atlas
   *
   * @param textureDef Texture definition
   * @returns UV coordinates
   */
  async getTextureUV(textureDef: TextureDefinition): Promise<AtlasUV> {
    const cacheKey = this.createCacheKey(textureDef);

    // Check cache
    if (this.uvCache.has(cacheKey)) {
      return this.uvCache.get(cacheKey)!;
    }

    // Ensure texture is loaded into atlas
    const pos = await this.loadTextureIntoAtlas(textureDef);

    // Calculate UV coordinates
    const u0 = pos.x * this.uvSize;
    const v0 = pos.y * this.uvSize;
    const u1 = u0 + this.uvSize;
    const v1 = v0 + this.uvSize;

    const uv = { u0, v0, u1, v1 };

    // Cache result
    this.uvCache.set(cacheKey, uv);

    return uv;
  }

  /**
   * Create cache key for a texture definition
   */
  private createCacheKey(textureDef: TextureDefinition): string {
    if (textureDef.uvMapping) {
      return `${textureDef.path}:${textureDef.uvMapping.x},${textureDef.uvMapping.y},${textureDef.uvMapping.w},${textureDef.uvMapping.h}`;
    }
    return textureDef.path;
  }

  /**
   * Get the atlas material
   */
  getMaterial(): StandardMaterial | undefined {
    return this.material;
  }

  /**
   * Get the dynamic atlas texture
   */
  getTexture(): DynamicTexture | undefined {
    return this.atlasTexture;
  }

  /**
   * Check if texture system is ready
   */
  isReady(): boolean {
    return this.atlasTexture !== undefined && this.material !== undefined;
  }

  /**
   * Clear caches
   */
  clearCache(): void {
    this.uvCache.clear();
    logger.debug('UV cache cleared');
  }

  /**
   * Get atlas statistics
   */
  getStats(): { loadedTextures: number; totalSlots: number; usedSlots: number } {
    const totalSlots = this.texturesPerRow * this.texturesPerRow;
    return {
      loadedTextures: this.textureLoaded.size,
      totalSlots,
      usedSlots: this.nextSlot,
    };
  }
}
