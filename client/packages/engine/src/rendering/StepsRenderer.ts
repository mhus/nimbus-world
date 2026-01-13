/**
 * StepsRenderer - Renders steps-shaped blocks
 *
 * Creates mesh geometry for step stones (individual stone blocks forming stairs).
 * Steps consist of two separate half-height blocks:
 * - Lower step: back half (z=0 to 0.5), height y=0 to 0.5
 * - Upper step: front half (z=0.5 to 1), height y=0.5 to 1
 * Supports scaling and rotation.
 */

import { Vector3, Matrix } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { TextureDefinition, Block } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('StepsRenderer');

/**
 * StepsRenderer - Renders steps blocks
 *
 * Creates optimized mesh data for step-stone blocks.
 * Supports scaling, rotation, and UV mapping from texture atlas.
 */
export class StepsRenderer extends BlockRenderer {
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

    if (typeof texture === 'object' && texture.path) {
      return texture as TextureDefinition;
    }

    if (typeof texture === 'string') {
      return { path: texture };
    }

    return null;
  }

  /**
   * Render a steps block into the mesh data
   *
   * Steps structure:
   * - Lower step: back half (z=0 to 0.5), height y=0 to 0.5
   * - Upper step: front half (z=0.5 to 1), height y=0.5 to 1
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

    const textures = modifier.visibility.textures;
    if (!textures) {
      logger.warn('Block has no textures', { blockTypeId: block.blockType.id });
      return;
    }

    logger.debug('Rendering steps block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
    });

    const size = 1;

    // Block center for rotation
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // Define corners for a two-step stair
    const corners = [
      // Lower step - bottom 4 corners (y=0, z=0 to 0.5)
      [worldX, worldY, worldZ],                      // 0: left-back-bottom
      [worldX + size, worldY, worldZ],               // 1: right-back-bottom
      [worldX + size, worldY, worldZ + size / 2],    // 2: right-mid-bottom
      [worldX, worldY, worldZ + size / 2],           // 3: left-mid-bottom

      // Lower step - top 4 corners (y=0.5, z=0 to 0.5)
      [worldX, worldY + size / 2, worldZ],           // 4: left-back-mid
      [worldX + size, worldY + size / 2, worldZ],    // 5: right-back-mid
      [worldX + size, worldY + size / 2, worldZ + size / 2],  // 6: right-mid-mid
      [worldX, worldY + size / 2, worldZ + size / 2],         // 7: left-mid-mid

      // Upper step - bottom 4 corners (y=0.5, z=0.5 to 1.0)
      [worldX, worldY + size / 2, worldZ + size / 2],         // 8: left-mid-mid
      [worldX + size, worldY + size / 2, worldZ + size / 2],  // 9: right-mid-mid
      [worldX + size, worldY + size / 2, worldZ + size],      // 10: right-front-mid
      [worldX, worldY + size / 2, worldZ + size],             // 11: left-front-mid

      // Upper step - top 4 corners (y=1.0, z=0.5 to 1.0)
      [worldX, worldY + size, worldZ + size / 2],             // 12: left-mid-top
      [worldX + size, worldY + size, worldZ + size / 2],      // 13: right-mid-top
      [worldX + size, worldY + size, worldZ + size],          // 14: right-front-top
      [worldX, worldY + size, worldZ + size],                 // 15: left-front-top
    ];

    // Apply scaling if specified (before rotation)
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
      for (let i = 0; i < 16; i++) {
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

    // Apply rotation if specified
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      for (let i = 0; i < 16; i++) {
        // Translate to origin
        const relativePos = new Vector3(
          corners[i][0] - centerX,
          corners[i][1] - centerY,
          corners[i][2] - centerZ
        );

        // Apply rotation
        const rotatedPos = Vector3.TransformCoordinates(relativePos, rotationMatrix);

        // Translate back
        corners[i][0] = rotatedPos.x + centerX;
        corners[i][1] = rotatedPos.y + centerY;
        corners[i][2] = rotatedPos.z + centerZ;
      }
    }

    // Determine texture indices
    const topIndex = textures[1] ? 1 : (textures[7] ? 7 : 0);
    const bottomIndex = textures[2] ? 2 : (textures[7] ? 7 : 0);
    const sideIndex = textures[7] ? 7 : 0;

    // Get textures
    const topTexture = textures[topIndex] ? this.normalizeTexture(textures[topIndex]) : null;
    const bottomTexture = textures[bottomIndex] ? this.normalizeTexture(textures[bottomIndex]) : null;
    const sideTexture = textures[sideIndex] ? this.normalizeTexture(textures[sideIndex]) : null;

    let facesRendered = 0;

    // ===== LOWER STEP (back half, 6 faces) =====

    // Top face of lower step (4, 5, 6, 7)
    await this.addFace(
      corners[4], corners[5], corners[6], corners[7],
      [0, 1, 0],
      topTexture, modifier,
        block.block,
      renderContext
    );
    facesRendered++;

    // Bottom face of lower step (3, 2, 1, 0)
    await this.addFace(
      corners[3], corners[2], corners[1], corners[0],
      [0, -1, 0],
      bottomTexture, modifier,
        block.block,
      renderContext
    );
    facesRendered++;

    // Front face of lower step (3, 7, 6, 2) - This is the "step" face
    await this.addFace(
      corners[3], corners[7], corners[6], corners[2],
      [0, 0, 1],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Back face of lower step (1, 5, 4, 0)
    await this.addFace(
      corners[1], corners[5], corners[4], corners[0],
      [0, 0, -1],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Right face of lower step (2, 6, 5, 1)
    await this.addFace(
      corners[2], corners[6], corners[5], corners[1],
      [1, 0, 0],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Left face of lower step (0, 4, 7, 3)
    await this.addFace(
      corners[0], corners[4], corners[7], corners[3],
      [-1, 0, 0],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // ===== UPPER STEP (front half, 6 faces) =====

    // Top face of upper step (12, 13, 14, 15)
    await this.addFace(
      corners[12], corners[13], corners[14], corners[15],
      [0, 1, 0],
      topTexture, modifier,
        block.block,
      renderContext
    );
    facesRendered++;

    // Bottom face of upper step (11, 10, 9, 8)
    await this.addFace(
      corners[11], corners[10], corners[9], corners[8],
      [0, -1, 0],
      bottomTexture, modifier,
        block.block,
      renderContext
    );
    facesRendered++;

    // Front face of upper step (11, 15, 14, 10)
    await this.addFace(
      corners[11], corners[15], corners[14], corners[10],
      [0, 0, 1],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Back face of upper step (9, 13, 12, 8) - This is the "step" face
    await this.addFace(
      corners[9], corners[13], corners[12], corners[8],
      [0, 0, -1],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Right face of upper step (10, 14, 13, 9)
    await this.addFace(
      corners[10], corners[14], corners[13], corners[9],
      [1, 0, 0],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    // Left face of upper step (8, 12, 15, 11)
    await this.addFace(
      corners[8], corners[12], corners[15], corners[11],
      [-1, 0, 0],
      sideTexture, modifier,
        block.block,
        renderContext,
      true  // Reverse winding order
    );
    facesRendered++;

    logger.debug('Steps rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      facesRendered
    });
  }

  /**
   * Add a face to the mesh data
   *
   * @param corner0 - First corner position [x, y, z]
   * @param corner1 - Second corner position [x, y, z]
   * @param corner2 - Third corner position [x, y, z]
   * @param corner3 - Fourth corner position [x, y, z]
   * @param normal - Face normal vector [x, y, z]
   * @param texture - Texture definition for the face
   * @param renderContext - Render context
   * @param reverseWinding - Reverse triangle winding order for backface culling
   */
  private async addFace(
    corner0: number[],
    corner1: number[],
    corner2: number[],
    corner3: number[],
    normal: number[],
    texture: TextureDefinition | null,
    modifier: any,
    block: Block,
    renderContext: RenderContext,
    reverseWinding: boolean = false
  ): Promise<void> {
    const faceData = renderContext.faceData;

    // Add 4 vertices (positions)
    faceData.positions.push(
      corner0[0], corner0[1], corner0[2],
      corner1[0], corner1[1], corner1[2],
      corner2[0], corner2[1], corner2[2],
      corner3[0], corner3[1], corner3[2]
    );

    // Add 4 normals
    for (let i = 0; i < 4; i++) {
      faceData.normals.push(normal[0], normal[1], normal[2]);
    }

    // Add 4 UV coordinates
    if (texture && this.textureAtlas) {
      const atlasUV = await this.textureAtlas.getTextureUV(texture);
      if (atlasUV) {
        // Determine if this is a horizontal face (top/bottom) or vertical face (sides)
        const isHorizontalFace = normal[1] !== 0;

        if (isHorizontalFace) {
          // Top/Bottom faces: standard UV mapping
          faceData.uvs.push(
            atlasUV.u0, atlasUV.v0,
            atlasUV.u1, atlasUV.v0,
            atlasUV.u1, atlasUV.v1,
            atlasUV.u0, atlasUV.v1
          );
        } else {
          // Side faces: flip V coordinates
          faceData.uvs.push(
            atlasUV.u0, atlasUV.v1,
            atlasUV.u1, atlasUV.v1,
            atlasUV.u1, atlasUV.v0,
            atlasUV.u0, atlasUV.v0
          );
        }
      } else {
        faceData.uvs.push(0, 0, 1, 0, 1, 1, 0, 1);
      }
    } else {
      faceData.uvs.push(0, 0, 1, 0, 1, 1, 0, 1);
    }

    // Add 6 indices (2 triangles)
    const i0 = renderContext.vertexOffset;
    const i1 = renderContext.vertexOffset + 1;
    const i2 = renderContext.vertexOffset + 2;
    const i3 = renderContext.vertexOffset + 3;

    if (reverseWinding) {
      // Reverse winding order: CW → CCW
      faceData.indices.push(i0, i2, i1);
      faceData.indices.push(i0, i3, i2);
    } else {
      // Standard counter-clockwise winding
      faceData.indices.push(i0, i1, i2);
      faceData.indices.push(i0, i2, i3);
    }

    // Add wind attributes and colors (uses helper from base class)
    this.addWindAttributesAndColors(faceData, modifier, block, 4);

    renderContext.vertexOffset += 4;
  }
}
