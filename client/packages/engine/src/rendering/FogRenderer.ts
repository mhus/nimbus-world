/**
 * FogRenderer - Renders volumetric fog blocks
 *
 * Creates a cube-shaped fog volume with volumetric fog shader effect.
 * Each fog block gets its own mesh with the fog shader applied.
 *
 * Fog parameters can be specified in visibility.effectParameters:
 * Format: "density" (e.g., "0.5")
 * - density: Fog thickness (0.0 = transparent, 1.0 = opaque), default: 0.3
 */

import { getLogger } from '@nimbus/shared';
import { MeshBuilder } from '@babylonjs/core';
import type { ClientBlock } from '../types';
import type { BlockModifier, TextureDefinition } from '@nimbus/shared';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('FogRenderer');

/**
 * FogRenderer - Renders volumetric fog blocks with shader effect
 *
 * Creates individual box meshes with fog shader material.
 * Each fog block is rendered separately (not batched in chunk mesh).
 */
export class FogRenderer extends BlockRenderer {
  private textureAtlas: TextureAtlas;

  constructor(textureAtlas: TextureAtlas) {
    super();
    this.textureAtlas = textureAtlas;
  }

  /**
   * Fog blocks need separate meshes (not batched in chunk mesh)
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a volumetric fog block
   *
   * Creates a box mesh with fog shader material.
   * Supports scaling, rotation, and density parameters.
   */
  async render(
    renderContext: RenderContext,
    block: ClientBlock,
  ): Promise<void> {
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    const modifier = block.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.debug('Fog block has no visibility modifier', { blockTypeId: block.blockType.id });
      return;
    }

    try {
      const scene = renderContext.renderService.scene;
      const shaderService = renderContext.renderService.appContext.services.shader;

      if (!shaderService) {
        logger.warn('ShaderService not available, cannot render fog', {
          position: { x: worldX, y: worldY, z: worldZ }
        });
        return;
      }

      // Get scaling
      const scalingX = modifier.visibility?.scalingX ?? 1.0;
      const scalingY = modifier.visibility?.scalingY ?? 1.0;
      const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

      // Get rotation
      const rotationX = block.block.rotation?.x ?? 0;
      const rotationY = block.block.rotation?.y ?? 0;

      // Calculate box dimensions
      const width = scalingX;
      const height = scalingY;
      const depth = scalingZ;

      // Calculate box center position
      const centerX = worldX + 0.5;
      const centerY = worldY + 0.5;
      const centerZ = worldZ + 0.5;

      // Create fog box mesh
      const fogMesh = MeshBuilder.CreateBox(
        `fog_${worldX}_${worldY}_${worldZ}`,
        {
          width: width,
          height: height,
          depth: depth
        },
        scene
      );

      // Position at block center
      fogMesh.position.set(centerX, centerY, centerZ);

      // Apply rotation (convert degrees to radians)
      if (rotationX !== 0 || rotationY !== 0) {
        fogMesh.rotation.x = rotationX * Math.PI / 180;
        fogMesh.rotation.y = rotationY * Math.PI / 180;
      }

      // Get fog shader material from ShaderService
      const atlasTexture = this.textureAtlas?.getTexture();
      const effectParameters = modifier.visibility?.effectParameters;

      const fogMaterial = shaderService.createMaterial('fog', {
        texture: atlasTexture,
        effectParameters: effectParameters,
        name: `fogMaterial_${worldX}_${worldY}_${worldZ}`
      });

      if (fogMaterial) {
        fogMesh.material = fogMaterial;
      } else {
        logger.warn('Failed to create fog material, using default', {
          position: { x: worldX, y: worldY, z: worldZ }
        });
      }

      // Fog rendering settings
      fogMesh.isPickable = false;
      fogMesh.renderingGroupId = RENDERING_GROUPS.WORLD; // Same as blocks for proper depth testing

      // Register mesh for illumination glow if block has illumination modifier
      const illuminationService = renderContext.renderService.appContext.services.illumination;
      if (illuminationService && modifier.illumination?.color) {
        illuminationService.registerMesh(
          fogMesh,
          modifier.illumination.color,
          modifier.illumination.strength ?? 1.0
        );
      }

      // Add to disposable resources
      renderContext.resourcesToDispose.addMesh(fogMesh);

      logger.debug('Volumetric fog block rendered', {
        blockTypeId: block.blockType.id,
        position: { x: worldX, y: worldY, z: worldZ },
        dimensions: { width, height, depth },
        rotation: { x: rotationX, y: rotationY },
        density: effectParameters
      });
    } catch (error) {
      logger.error('Failed to render fog block', {
        position: { x: worldX, y: worldY, z: worldZ },
        error
      });
    }
  }
}
