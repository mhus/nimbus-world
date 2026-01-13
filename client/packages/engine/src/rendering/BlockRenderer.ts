/**
 * Shape Renderer Base Class
 * Abstract base class for rendering different block shapes
 * Each shape type extends this class and implements the render method
 */

import { Matrix, Vector3 } from '@babylonjs/core';
import type { RenderService, RenderContext } from '../services/RenderService';
import type { ClientBlock } from '../types';
import type { BlockModifier, Block } from '@nimbus/shared';

/**
 * Abstract base class for shape renderers
 * Provides common functionality for rendering block faces with rotation, UVs, and colors
 */
export abstract class BlockRenderer {
  /**
   * Render a block using the provided context
   * @param renderService - The render service instance
   * @param block - The client block to render
   * @param renderContext - The rendering context with transformation and other info
   * @returns Number of vertices added to the geometry arrays (or Promise for async renderers)
   */
  abstract render(
      renderContext: RenderContext,
      block: ClientBlock
): void | Promise<void>;

  /**
   * Determine if this renderer requires a separate mesh for each block
   *
   * Most renderers (CUBE) return false - they batch all blocks into a single chunk mesh.
   * Special renderers (FLIPBOX, BILLBOARD, SPRITE, MODEL) return true - they need
   * individual meshes with original textures or special shader materials.
   *
   * @returns true if this block needs its own mesh, false if it can be batched (default)
   */
  needsSeparateMesh(): boolean {
    return false; // Default: batch into chunk mesh
  }

  /**
   * Add wind attributes and vertex colors to faceData for wind shader support
   *
   * This helper method should be called by renderers after adding vertices.
   * It adds the required attributes for wind animation (if faceData has wind arrays initialized).
   *
   * @param faceData - Face data to add attributes to
   * @param modifier - Block modifier containing wind properties
   * @param block - Block instance (for level parameter)
   * @param vertexCount - Number of vertices to add attributes for
   */
  protected addWindAttributesAndColors(
    faceData: any,
    modifier: BlockModifier,
    block: Block,
    vertexCount: number
  ): void {
    // Add vertex colors (white by default, RGBA format: 4 values per vertex)
    if (faceData.colors) {
      for (let i = 0; i < vertexCount; i++) {
        faceData.colors.push(1.0, 1.0, 1.0, 1.0);
      }
    }

    // Add wind attributes (per-vertex, 1 value per vertex)
    // Only add if arrays exist (indicates wind shader is used for this material group)
    if (faceData.windLeafiness && faceData.windStability && faceData.windLeverUp && faceData.windLeverDown) {
      const windLeafiness = modifier.wind?.leafiness ?? 0.5;
      const windStability = modifier.wind?.stability ?? 0.5;
      let windLeverUp = modifier.wind?.leverUp ?? 0.0;
      let windLeverDown = modifier.wind?.leverDown ?? 0.0;

      // Multiply lever values with block.level if set
      if (block.level !== undefined) {
        windLeverUp *= block.level;
        windLeverDown *= block.level;
      }

      for (let i = 0; i < vertexCount; i++) {
        faceData.windLeafiness.push(windLeafiness);
        faceData.windStability.push(windStability);
        faceData.windLeverUp.push(windLeverUp);
        faceData.windLeverDown.push(windLeverDown);
      }
    }
  }

}
