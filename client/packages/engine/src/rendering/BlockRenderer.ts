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
   * Render a single face of a block
   * Optional method for renderers that support face-level rendering
   *
   * @param renderContext - Render context
   * @param block - Block to render
   * @param textureKey - Which face to render (0=ALL, 1=TOP, 2=BOTTOM, 3=LEFT, 4=RIGHT, 5=FRONT, 6=BACK, 7=SIDE)
   */
  renderSingleFace?(
    renderContext: RenderContext,
    block: ClientBlock,
    textureKey: number
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
    if (faceData.windLeafiness && faceData.windStability && faceData.windHeight) {
      const windLeafiness = modifier.wind?.leafiness ?? 0.5;
      const windStability = modifier.wind?.stability ?? 0.5;
      const level = block.level ?? 0;
      const blockY = block.position?.y ?? 0;

      // Calculate windHeight per vertex from actual Y position
      // windHeight = level + (vertexY - blockY), where vertexY - blockY is 0..1 within the block
      const posStartIndex = faceData.positions.length - vertexCount * 3;
      for (let i = 0; i < vertexCount; i++) {
        const vertexY = faceData.positions[posStartIndex + i * 3 + 1]; // Y component
        const relativeY = vertexY - blockY; // 0 at bottom, ~1 at top
        const windHeight = level + relativeY;
        faceData.windLeafiness.push(windLeafiness);
        faceData.windStability.push(windStability);
        faceData.windHeight.push(windHeight);
      }
    }
  }

  /**
   * Add wind attributes with per-vertex windHeight values
   * Used by CubeRenderer to set different windHeight for top/bottom vertices
   *
   * @param faceData - Face data to add attributes to
   * @param modifier - Block modifier containing wind properties
   * @param windHeights - Array of windHeight values (one per vertex)
   */
  protected addWindAttributesPerVertex(
    faceData: any,
    modifier: BlockModifier,
    windHeights: number[]
  ): void {
    // Add vertex colors (white by default, RGBA format: 4 values per vertex)
    if (faceData.colors) {
      for (let i = 0; i < windHeights.length; i++) {
        faceData.colors.push(1.0, 1.0, 1.0, 1.0);
      }
    }

    // Add wind attributes with per-vertex windHeight
    if (faceData.windLeafiness && faceData.windStability && faceData.windHeight) {
      const windLeafiness = modifier.wind?.leafiness ?? 0.5;
      const windStability = modifier.wind?.stability ?? 0.5;

      for (let i = 0; i < windHeights.length; i++) {
        faceData.windLeafiness.push(windLeafiness);
        faceData.windStability.push(windStability);
        faceData.windHeight.push(windHeights[i]);
      }
    }
  }

}
