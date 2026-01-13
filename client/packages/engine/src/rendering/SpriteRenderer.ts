/**
 * SpriteRenderer - Renders sprite-based blocks
 *
 * Uses SpriteService to create sprite instances for blocks.
 * Each block can have multiple sprites (one per texture defined).
 * Sprites automatically animate with wind via SpriteService.
 *
 * Features:
 * - Multiple sprites per block (one per TextureDefinition)
 * - Sprite count configurable via shaderParameters (default: 10)
 * - Random positioning within block bounds using scaling and offset
 * - Wind animation integration
 *
 * NOTE: Babylon.js Sprites are ALWAYS full billboards (face camera completely, including up/down).
 * This cannot be changed - it's a limitation of the Sprite system.
 * For Y-axis-only billboards (vertical quads), use Shape.BILLBOARD instead.
 */

import { Sprite, type IDisposable } from '@babylonjs/core';
import { getLogger, Shape, TextureHelper } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';

const logger = getLogger('SpriteRenderer');

/**
 * Disposable wrapper for sprite cleanup
 */
class SpriteDisposable implements IDisposable {
  constructor(private sprites: Sprite[]) {}

  dispose(): void {
    for (const sprite of this.sprites) {
      sprite.dispose();
    }
    logger.debug('Sprites disposed', { count: this.sprites.length });
  }
}

/**
 * SpriteRenderer - Renders blocks as sprite collections
 *
 * Creates multiple sprite instances for each block using SpriteService.
 * Sprites are positioned randomly within the block bounds and animate with wind.
 */
export class SpriteRenderer extends BlockRenderer {
  /**
   * SpriteRenderer needs separate mesh per block
   * (sprites are managed by SpriteManager, not meshes)
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a SPRITE block
   *
   * Creates multiple sprite instances for each texture defined in the block.
   * Number of sprites per texture is configurable via shaderParameters (default: 10).
   *
   * @param renderContext Render context
   * @param clientBlock Block to render
   */
  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('SpriteRenderer: No visibility modifier', { block });
      return;
    }

    // Validate shape
    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.SPRITE) {
      logger.warn('SpriteRenderer: Not a SPRITE shape', { shape, block });
      return;
    }

    const spriteService = renderContext.renderService.appContext.services.sprite;
    if (!spriteService) {
      logger.error('SpriteRenderer: SpriteService not available');
      return;
    }

    // Get transformations
    const scalingX = modifier.visibility.scalingX ?? 1.0;
    const scalingY = modifier.visibility.scalingY ?? 1.0;
    const scalingZ = modifier.visibility.scalingZ ?? 1.0;

    // Get offset (shifts the center point of sprite distribution)
    let offsetX = 0;
    let offsetY = 0;
    let offsetZ = 0;

    if (block.offsets && block.offsets.length >= 3) {
      offsetX = block.offsets[0] || 0;
      offsetY = block.offsets[1] || 0;
      offsetZ = block.offsets[2] || 0;
    }

    // Get wind properties from modifier
    const windLeafiness = modifier.wind?.leafiness ?? 0.5;
    const windStability = modifier.wind?.stability ?? 0.5;

    // Process each texture defined in the block
    const textures = modifier.visibility.textures;
    if (!textures || Object.keys(textures).length === 0) {
      logger.warn('SpriteRenderer: No textures defined', { block });
      return;
    }

    for (const [key, textureValue] of Object.entries(textures)) {
      const textureDef = TextureHelper.normalizeTexture(textureValue);

      // Get sprite count from effectParameters (format: "count" e.g., "10")
      let spriteCount = 10; // Default
      if (textureDef.effectParameters) {
        const parsed = parseInt(textureDef.effectParameters, 10);
        if (!isNaN(parsed) && parsed > 0) {
          spriteCount = parsed;
        }
      }

      // Create sprites for this texture
      const sprites = await this.createSprites(
        clientBlock,
        textureDef.path,
        spriteCount,
        scalingX,
        scalingY,
        scalingZ,
        offsetX,
        offsetY,
        offsetZ,
        windLeafiness,
        windStability,
        spriteService,
        renderContext
      );

      // Register sprites for disposal
      if (sprites.length > 0) {
        const disposable = new SpriteDisposable(sprites);
        renderContext.resourcesToDispose.add(disposable);
      }
    }
  }

  /**
   * Create sprites for a texture
   *
   * Creates multiple sprite instances positioned randomly within block bounds.
   * Scaling determines the spread area, offset shifts the center point.
   *
   * @param clientBlock Block to create sprites for
   * @param texturePath Path to sprite texture
   * @param spriteCount Number of sprites to create
   * @param scalingX X-axis scaling (spread area)
   * @param scalingY Y-axis scaling (vertical spread)
   * @param scalingZ Z-axis scaling (spread area)
   * @param offsetX X-axis offset (shifts center)
   * @param offsetY Y-axis offset (shifts center)
   * @param offsetZ Z-axis offset (shifts center)
   * @param windLeafiness Wind leafiness parameter
   * @param windStability Wind stability parameter
   * @param spriteService SpriteService instance
   * @param renderContext Render context for disposal registration
   * @returns Array of created sprites
   */
  private async createSprites(
    clientBlock: ClientBlock,
    texturePath: string,
    spriteCount: number,
    scalingX: number,
    scalingY: number,
    scalingZ: number,
    offsetX: number,
    offsetY: number,
    offsetZ: number,
    windLeafiness: number,
    windStability: number,
    spriteService: any,
    renderContext: RenderContext
  ): Promise<Sprite[]> {
    const block = clientBlock.block;
    const sprites: Sprite[] = [];

    try {
      // Get or create SpriteManager for this texture
      // Note: spriteCount is used as capacity for the manager (max sprites it can hold)
      const manager = await spriteService.getManager(texturePath, spriteCount);

      // Block center position (with offset)
      const centerX = block.position.x + 0.5 + offsetX;
      const centerY = block.position.y + 0.5 + offsetY;
      const centerZ = block.position.z + 0.5 + offsetZ;

      // Calculate spread area (half-dimensions)
      const spreadX = 0.5 * scalingX;
      const spreadY = 0.5 * scalingY;
      const spreadZ = 0.5 * scalingZ;

      // Create sprites
      for (let i = 0; i < spriteCount; i++) {
        // Random position within spread area
        const randomX = (Math.random() - 0.5) * 2 * spreadX;
        const randomY = (Math.random() - 0.5) * 2 * spreadY;
        const randomZ = (Math.random() - 0.5) * 2 * spreadZ;

        const spriteX = centerX + randomX;
        const spriteY = centerY + randomY;
        const spriteZ = centerZ + randomZ;

        // Create sprite
        const sprite = new Sprite(`sprite_${block.position.x}_${block.position.y}_${block.position.z}_${i}`, manager);
        sprite.position.set(spriteX, spriteY, spriteZ);

        // Random size variation (0.8 to 1.2)
        const sizeVariation = 0.8 + Math.random() * 0.4;
        sprite.width = sizeVariation;
        sprite.height = sizeVariation;

        // Register sprite for wind animation
        spriteService.registerSprite(sprite, windLeafiness, windStability);

        // Add to array for disposal
        sprites.push(sprite);
      }

      logger.debug('Sprites created', {
        position: `${block.position.x},${block.position.y},${block.position.z}`,
        texturePath,
        count: spriteCount,
        center: `${centerX},${centerY},${centerZ}`,
        spread: `${spreadX},${spreadY},${spreadZ}`,
      });
    } catch (error) {
      logger.error('Failed to create sprites', {
        position: `${block.position.x},${block.position.y},${block.position.z}`,
        texturePath,
        error,
      });
    }

    return sprites;
  }
}
