/**
 * ModelRenderer - Renders 3D model files as blocks
 *
 * Loads external 3D models (.babylon files) and renders them as blocks.
 * Supports scaling, rotation, offsets, and automatic size normalization.
 * Each model gets a separate mesh (not part of chunk mesh).
 */

import { Vector3 } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('ModelRenderer');

/**
 * ModelRenderer - Renders blocks as 3D models
 *
 * Features:
 * - Uses ModelService for loading and caching
 * - Automatic scaling to fit block size
 * - Supports offset, scaling, rotation transformations
 * - Each block gets its own mesh instance
 */
export class ModelRenderer extends BlockRenderer {
  /**
   * MODEL blocks need separate meshes
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a MODEL block
   *
   * @param renderContext - Render context
   * @param block - The block to render
   */
  async render(renderContext: RenderContext, block: ClientBlock): Promise<void> {
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    const modifier = block.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.debug('Block has no visibility modifier', { blockTypeId: block.blockType.id });
      return;
    }

    // Get model path from VisibilityModifier.path
    const modelPath = modifier.visibility.path;

    if (!modelPath) {
      logger.warn('No model path found in visibility.path', {
        blockTypeId: block.blockType.id,
        position: { x: worldX, y: worldY, z: worldZ }
      });
      return;
    }

    // Get full model URL via NetworkService
    // No path normalization - use path as-is (e.g., "models/skull.babylon")
    const networkService = renderContext.renderService.appContext.services.network;
    if (!networkService) {
      logger.error('NetworkService not available', {
        blockTypeId: block.blockType.id,
        position: { x: worldX, y: worldY, z: worldZ }
      });
      return;
    }

    const fullModelUrl = networkService.getAssetUrl(modelPath);

    logger.debug('Loading model', {
      modelPath,
      fullModelUrl,
      position: { x: worldX, y: worldY, z: worldZ }
    });

    // Get transformations
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    // Get position offset from offsets (first 3 values: XYZ)
    const offsets = block.block.offsets;
    let offsetX = 0;
    let offsetY = 0;
    let offsetZ = 0;

    if (offsets && offsets.length >= 3) {
      offsetX = (offsets[0] ?? 0) / 127.0;
      offsetY = (offsets[1] ?? 0) / 127.0;
      offsetZ = (offsets[2] ?? 0) / 127.0;
    }

    // Calculate final position (block center + offset)
    const posX = worldX + 0.5 + offsetX;
    const posY = worldY + 0.5 + offsetY;
    const posZ = worldZ + 0.5 + offsetZ;

    try {
      // Get ModelService from EngineService
      const engineService = renderContext.renderService.appContext.services.engine;
      if (!engineService) {
        logger.error('EngineService not available', {
          blockTypeId: block.blockType.id,
          position: { x: worldX, y: worldY, z: worldZ }
        });
        return;
      }

      const modelService = engineService.getModelService();
      if (!modelService) {
        logger.error('ModelService not available', {
          blockTypeId: block.blockType.id,
          position: { x: worldX, y: worldY, z: worldZ }
        });
        return;
      }

      // Load model via ModelService (cached)
      const templateMesh = await modelService.loadModel(modelPath);
      if (!templateMesh) {
        logger.error('Failed to load model', {
          modelPath,
          fullModelUrl,
          blockTypeId: block.blockType.id,
          position: { x: worldX, y: worldY, z: worldZ }
        });
        return;
      }

      // Clone template mesh for this instance
      const modelMesh = templateMesh.clone(`model_${worldX}_${worldY}_${worldZ}`, null)!;
      modelMesh.setEnabled(true); // Enable the clone

      // Set rendering group for world content
      modelMesh.renderingGroupId = RENDERING_GROUPS.WORLD;

      // Calculate bounding box for automatic scaling
      modelMesh.computeWorldMatrix(true);
      const boundingInfo = modelMesh.getBoundingInfo();
      const boundingBox = boundingInfo.boundingBox;
      const modelSize = boundingBox.extendSize.scale(2); // extendSize is half-size

      // Calculate automatic scale factor to fit model into 1 block
      const maxDimension = Math.max(modelSize.x, modelSize.y, modelSize.z);
      const autoScale = maxDimension > 0 ? 1.0 / maxDimension : 1.0;

      logger.debug('Model dimensions', {
        size: { x: modelSize.x, y: modelSize.y, z: modelSize.z },
        maxDimension,
        autoScale
      });

      // Apply transformations
      modelMesh.position = new Vector3(posX, posY, posZ);

      // Combine user scaling with auto-scaling
      modelMesh.scaling = new Vector3(
        scalingX * autoScale,
        scalingY * autoScale,
        scalingZ * autoScale
      );

      // Apply rotation
      const rotationX = block.block.rotation?.x ?? 0;
      const rotationY = block.block.rotation?.y ?? 0;

      if (rotationX !== 0) {
        modelMesh.rotation.x = (rotationX * Math.PI) / 180;
      }

      if (rotationY !== 0) {
        modelMesh.rotation.y = (rotationY * Math.PI) / 180;
      }

      // Ensure mesh is visible
      modelMesh.isVisible = true;
      modelMesh.visibility = 1.0;

      // Register mesh for illumination glow if block has illumination modifier
      const illuminationService = renderContext.renderService.appContext.services.illumination;
      if (illuminationService && modifier.illumination?.color) {
        illuminationService.registerMesh(
          modelMesh,
          modifier.illumination.color,
          modifier.illumination.strength ?? 1.0
        );
      }

      // Register mesh for automatic disposal when chunk is unloaded
      renderContext.resourcesToDispose.addMesh(modelMesh);

      logger.debug('Model rendered', {
        blockTypeId: block.blockType.id,
        position: { x: worldX, y: worldY, z: worldZ },
        modelPath,
        finalScaling: modelMesh.scaling,
        rotation: { x: modelMesh.rotation.x, y: modelMesh.rotation.y }
      });

    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ModelRenderer.render',
        {
          modelPath,
          fullModelUrl,
          position: { x: worldX, y: worldY, z: worldZ },
          blockTypeId: block.blockType.id,
          troubleshooting: [
            'Make sure the model file exists in the assets directory on the server',
            'Check that the file path is correct (e.g., "models/skull.babylon")',
            'Verify the model file format is valid .babylon JSON',
            'Check that NetworkService.getAssetUrl() returns correct URL',
            'Verify worldInfo.assetPath is configured correctly'
          ]
        }
      );
    }
  }
}
