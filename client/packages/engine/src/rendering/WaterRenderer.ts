/**
 * WaterRenderer - Renders water-shaped blocks
 *
 * Creates mesh geometry for water blocks with only the top face visible.
 * Supports transparency and color tinting for different water types.
 */

import { Matrix, Vector3 } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';
import type { Block, BlockType, TextureDefinition, TextureKey, BlockModifier } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import { RenderService, RenderContext } from '../services/RenderService';
import type { TextureAtlas, AtlasUV } from './TextureAtlas';

const logger = getLogger('WaterRenderer');

/**
 * WaterRenderer - Renders water blocks
 *
 * Creates optimized mesh data for water-shaped blocks.
 * Only renders the top face with transparency and color tinting.
 * Supports scaling, rotation, and offsets like CubeRenderer.
 */
export class WaterRenderer extends BlockRenderer {
  private textureAtlas: TextureAtlas;

  constructor(textureAtlas: TextureAtlas) {
    super();
    this.textureAtlas = textureAtlas;
  }

  /**
   * Normalize texture - convert string or TextureDefinition to TextureDefinition
   */
  private normalizeTexture(texture: any): TextureDefinition | null {
    if (!texture) return null;

    // If it's already a TextureDefinition object with 'path'
    if (typeof texture === 'object' && texture.path) {
      return texture as TextureDefinition;
    }

    // If it's a string, convert to TextureDefinition
    if (typeof texture === 'string') {
      return { path: texture };
    }

    return null;
  }

  /**
   * Render a water block into the mesh data
   *
   * @param renderContext - The render context
   * @param block - The block to render
   * @returns Promise<void>
   */
  async render(
    renderContext: RenderContext,
    block: ClientBlock,
  ): Promise<void> {
    const renderService = renderContext.renderService;
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    // Get block modifier for current status
    const modifier = block.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.debug('Block has no visibility modifier', { blockTypeId: block.blockType.id });
      return;
    }

    // Get textures from modifier
    const textures = modifier.visibility.textures;
    if (!textures) {
      logger.warn('Block has no textures', { blockTypeId: block.blockType.id });
      return;
    }

    logger.debug('Rendering water block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      hasTextures: !!textures,
      textureCount: Object.keys(textures).length
    });

    // Use TOP texture (index 1) if available, otherwise fallback to ALL (index 7) or default (index 0)
    const topIndex = textures[1] ? 1 : (textures[7] ? 7 : 0);

    // Check face visibility - only render top face
    const isTopVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.TOP);

    if (!isTopVisible) {
      logger.debug('Top face not visible, skipping water block', {
        blockTypeId: block.blockType.id,
        position: { x: worldX, y: worldY, z: worldZ }
      });
      return;
    }

    // Get material for top face
    const topMaterial = topIndex ? await renderService.materialService.getMaterial(modifier, topIndex) : null;

    logger.debug('Face visibility and material', {
      top: { visible: isTopVisible, hasMaterial: !!topMaterial }
    });

    const size = 1;

    // Block center for rotation
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    const corners = [
      // Top face (y = y + size)
      [worldX, worldY + size, worldZ], // 4: left-back-top
      [worldX + size, worldY + size, worldZ], // 5: right-back-top
      [worldX + size, worldY + size, worldZ + size], // 6: right-front-top
      [worldX, worldY + size, worldZ + size], // 7: left-front-top
    ];

    // Apply edge offsets if available
    const offsets = block.block.offsets;
    if (offsets && offsets.length >= 3) {
      // Apply offsets to the 4 top corners (corner indices 4-7 in CubeRenderer, 0-3 here)
      for (let i = 0; i < 4; i++) {
        const cornerIndex = i + 4; // Map to original cube corner indices (4-7)
        if (!offsets[cornerIndex * 3] && !offsets[cornerIndex * 3 + 1] && !offsets[cornerIndex * 3 + 2]) {
          continue; // No offset for this corner
        }
        const offsetX = offsets[cornerIndex * 3];
        const offsetY = offsets[cornerIndex * 3 + 1];
        const offsetZ = offsets[cornerIndex * 3 + 2];

        corners[i][0] += offsetX ?? 0;
        corners[i][1] += offsetY ?? 0;
        corners[i][2] += offsetZ ?? 0;
      }
    }

    // Apply scaling if specified (after offsets, before rotation)
    const scalingX = modifier.visibility.scalingX ?? 1.0;
    const scalingY = modifier.visibility.scalingY ?? 1.0;
    const scalingZ = modifier.visibility.scalingZ ?? 1.0;

    if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
      for (let i = 0; i < 4; i++) {
        // Translate to origin (relative to center)
        corners[i][0] -= centerX;
        corners[i][1] -= centerY;
        corners[i][2] -= centerZ;

        // Apply scaling
        corners[i][0] *= scalingX;
        corners[i][1] *= scalingY;
        corners[i][2] *= scalingZ;

        // Translate back
        corners[i][0] += centerX;
        corners[i][1] += centerY;
        corners[i][2] += centerZ;
      }
    }

    // Apply rotation if specified (after scaling)
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      // Convert degrees to radians
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      // Create rotation matrix using Babylon.js
      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      // Apply rotation to each corner around the block center
      for (let i = 0; i < 4; i++) {
        // Translate to origin (relative to center)
        const relativePos = new Vector3(
          corners[i][0] - centerX,
          corners[i][1] - centerY,
          corners[i][2] - centerZ
        );

        // Apply rotation
        const rotatedPos = Vector3.TransformCoordinates(relativePos, rotationMatrix);

        // Translate back and update corner
        corners[i][0] = rotatedPos.x + centerX;
        corners[i][1] = rotatedPos.y + centerY;
        corners[i][2] = rotatedPos.z + centerZ;
      }
    }

    // Extract water color and transparency from offsets
    // offsets[24], offsets[25], offsets[26] = RGB color tint (0-1)
    // offsets[27] = transparency (0-1, where 0=fully transparent, 1=fully opaque)
    let waterColorR = 1.0;
    let waterColorG = 1.0;
    let waterColorB = 1.0;
    let waterAlpha = 0.5; // Default: half transparent

    if (offsets && offsets.length > 27) {
      if (offsets[24] !== undefined) waterColorR = Math.max(0, Math.min(1, offsets[24]));
      if (offsets[25] !== undefined) waterColorG = Math.max(0, Math.min(1, offsets[25]));
      if (offsets[26] !== undefined) waterColorB = Math.max(0, Math.min(1, offsets[26]));
      if (offsets[27] !== undefined) waterAlpha = Math.max(0, Math.min(1, offsets[27]));
    }

    logger.debug('Water color and transparency', {
      blockTypeId: block.blockType.id,
      color: { r: waterColorR, g: waterColorG, b: waterColorB },
      alpha: waterAlpha
    });

    // Render top face
    const texture = textures[topIndex] ? this.normalizeTexture(textures[topIndex]) : null;
    await this.addFace(
      corners[0], corners[1], corners[2], corners[3],  // left-back, right-back, right-front, left-front
      [0, 1, 0],  // Normal pointing up
      texture,
      modifier,
      block.block,
      renderContext,
      false,  // reverseWinding
      waterColorR, waterColorG, waterColorB, waterAlpha
    );

    logger.debug('Water rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ }
    });
  }

  /**
   * Add a face to the mesh data
   * @param corner0 - First corner position [x, y, z]
   * @param corner1 - Second corner position [x, y, z]
   * @param corner2 - Third corner position [x, y, z]
   * @param corner3 - Fourth corner position [x, y, z]
   * @param normal - Face normal vector [x, y, z]
   * @param texture - Texture definition for the face
   * @param modifier - Block modifier (contains wind properties)
   * @param block - Block instance
   * @param renderContext - Render context
   * @param reverseWinding - Reverse triangle winding order for backface culling
   * @param colorR - Red color component (0-1)
   * @param colorG - Green color component (0-1)
   * @param colorB - Blue color component (0-1)
   * @param alpha - Alpha transparency (0-1)
   */
  private async addFace(
    corner0: number[],
    corner1: number[],
    corner2: number[],
    corner3: number[],
    normal: number[],
    texture: TextureDefinition | null,
    modifier: BlockModifier,
    block: Block,
    renderContext: RenderContext,
    reverseWinding: boolean = false,
    colorR: number = 1.0,
    colorG: number = 1.0,
    colorB: number = 1.0,
    alpha: number = 0.5
  ): Promise<void> {
    const faceData = renderContext.faceData;

    // Add 4 vertices (positions)
    faceData.positions.push(
      corner0[0], corner0[1], corner0[2],
      corner1[0], corner1[1], corner1[2],
      corner2[0], corner2[1], corner2[2],
      corner3[0], corner3[1], corner3[2]
    );

    // Add 4 normals (same normal for all vertices of this face)
    for (let i = 0; i < 4; i++) {
      faceData.normals.push(normal[0], normal[1], normal[2]);
    }

    // Add 4 UV coordinates
    if (texture && this.textureAtlas) {
      const atlasUV = await this.textureAtlas.getTextureUV(texture);
      if (atlasUV) {
        // Top face: standard UV mapping
        faceData.uvs.push(
          atlasUV.u0, atlasUV.v0,  // corner0: bottom-left
          atlasUV.u1, atlasUV.v0,  // corner1: bottom-right
          atlasUV.u1, atlasUV.v1,  // corner2: top-right
          atlasUV.u0, atlasUV.v1   // corner3: top-left
        );
      } else {
        // Default UVs if texture not found
        faceData.uvs.push(0, 0, 1, 0, 1, 1, 0, 1);
      }
    } else {
      // Default UVs if no texture
      faceData.uvs.push(0, 0, 1, 0, 1, 1, 0, 1);
    }

    // Add 6 indices (2 triangles)
    const i0 = renderContext.vertexOffset;
    const i1 = renderContext.vertexOffset + 1;
    const i2 = renderContext.vertexOffset + 2;
    const i3 = renderContext.vertexOffset + 3;

    if (reverseWinding) {
      // Reverse winding order: CW → CCW
      faceData.indices.push(i0, i2, i1);  // Triangle 1: reversed
      faceData.indices.push(i0, i3, i2);  // Triangle 2: reversed
    } else {
      // Standard counter-clockwise winding
      faceData.indices.push(i0, i1, i2);  // Triangle 1
      faceData.indices.push(i0, i2, i3);  // Triangle 2
    }

    // Add vertex colors with custom RGBA values for water tinting and transparency
    if (faceData.colors) {
      for (let i = 0; i < 4; i++) {
        faceData.colors.push(colorR, colorG, colorB, alpha);
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

      for (let i = 0; i < 4; i++) {
        faceData.windLeafiness.push(windLeafiness);
        faceData.windStability.push(windStability);
        faceData.windLeverUp.push(windLeverUp);
        faceData.windLeverDown.push(windLeverDown);
      }
    }

    renderContext.vertexOffset += 4;  // 4 vertices added
  }
}
