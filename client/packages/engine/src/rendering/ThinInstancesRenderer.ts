/**
 * ThinInstancesRenderer - Renders THIN_INSTANCES blocks
 *
 * Uses ThinInstancesService to create highly performant instance groups.
 * Supports Y-axis billboards and GPU-based wind animation.
 *
 * Features:
 * - Instance count configurable via shaderParameters (default: 100)
 * - Random positioning within block bounds
 * - Y-axis-only billboard (stays vertical) via shader
 * - GPU wind animation via shader
 */

import { getLogger, Shape, TextureHelper, ExceptionHandler } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';

const logger = getLogger('ThinInstancesRenderer');

export class ThinInstancesRenderer extends BlockRenderer {
  /**
   * ThinInstancesRenderer needs separate handling per block
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a THIN_INSTANCES block
   */
  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('ThinInstancesRenderer: No visibility modifier', { block });
      return;
    }

    // Validate shape
    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.THIN_INSTANCES) {
      logger.warn('ThinInstancesRenderer: Not a THIN_INSTANCES shape', { shape, block });
      return;
    }

    const thinInstancesService = renderContext.renderService.appContext.services.thinInstances;
    if (!thinInstancesService) {
      logger.error('ThinInstancesRenderer: ThinInstancesService not available');
      return;
    }

    // Get textures
    const textures = modifier.visibility.textures;
    if (!textures || Object.keys(textures).length === 0) {
      logger.warn('ThinInstancesRenderer: No textures defined', { block });
      return;
    }

    // Get offset from visibility.offsets (if available)
    // offsets array: [x0,y0,z0, x1,y1,z1, ..., x7,y7,z7] (8 corners × 3 axes = 24 values)
    // Use first corner's offset (indices 0, 1, 2) for positioning
    let offset: { x: number; y: number; z: number } | undefined;
    if (modifier.visibility.offsets && modifier.visibility.offsets.length >= 3) {
      offset = {
        x: modifier.visibility.offsets[0],
        y: modifier.visibility.offsets[1],
        z: modifier.visibility.offsets[2],
      };
      logger.debug('Using offset from visibility.offsets', { offset });
    }

    // Get scaling from visibility (if available)
    let scaling: { x: number; y: number; z: number } | undefined;
    if (modifier.visibility.scalingX || modifier.visibility.scalingY || modifier.visibility.scalingZ) {
      scaling = {
        x: modifier.visibility.scalingX ?? 1,
        y: modifier.visibility.scalingY ?? 1,
        z: modifier.visibility.scalingZ ?? 1,
      };
      logger.debug('Using scaling from visibility', { scaling });
    }

    // Get wind parameters from modifier (if available)
    let wind: { leafiness: number; stability: number; leverUp: number; leverDown: number } | undefined;
    if (modifier.wind) {
      wind = {
        leafiness: modifier.wind.leafiness ?? 0.5,
        stability: modifier.wind.stability ?? 0.5,
        leverUp: modifier.wind.leverUp ?? 0.0,
        leverDown: modifier.wind.leverDown ?? 0.0,
      };
      logger.debug('Using wind parameters from modifier', { wind });
    }

    // Get chunk key for tracking - chunk coordinates must always be set
    if (!clientBlock.chunk) {
      logger.error('ClientBlock missing chunk coordinates', {
        blockPosition: block.position,
        blockTypeId: clientBlock.blockType.id
      });
      return; // Cannot render without chunk information
    }
    const chunkKey = `chunk_${clientBlock.chunk.cx}_${clientBlock.chunk.cz}`;

    // Process first 4 textures (keys 0-3) for mixing
    let totalInstancesCreated = 0;
    for (let textureKey = 0; textureKey <= 3; textureKey++) {
      const texture = textures[textureKey];
      if (!texture) {
        continue; // Skip if texture not set
      }

      const textureDef = TextureHelper.normalizeTexture(texture);

      // Get instance count from effectParameters (default: 100)
      let instanceCount = 10;
      if (textureDef.effectParameters) {
        const parsed = parseInt(textureDef.effectParameters, 10);
        if (!isNaN(parsed) && parsed > 0) {
          instanceCount = parsed;
        }
      }

      // Create thin instances for this texture
      try {
        const result = await thinInstancesService.createInstances(
          {
            texturePath: textureDef.path,
            instanceCount,
            blockPosition: block.position,
            offset,
            scaling,
            wind,
          },
          chunkKey
        );

        // Register mesh and disposable for cleanup
        renderContext.resourcesToDispose.addMesh(result.mesh);
        renderContext.resourcesToDispose.add(result.disposable);

        totalInstancesCreated += instanceCount;

        logger.debug('ThinInstances rendered for texture', {
          textureKey,
          texturePath: textureDef.path,
          instanceCount,
        });
      } catch (error) {
        ExceptionHandler.handle(error, 'ThinInstancesRenderer.render', {
          position: block.position,
          textureKey,
          texturePath: textureDef.path,
        });
      }
    }

    if (totalInstancesCreated === 0) {
      logger.warn('ThinInstancesRenderer: No instances created (no valid textures in slots 0-3)', { block });
      return;
    }

    logger.debug('ThinInstances rendered for all textures', {
      position: block.position,
      totalInstances: totalInstancesCreated,
    });
  }
}
