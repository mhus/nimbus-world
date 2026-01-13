/**
 * HashRenderer - Renders hash-shaped blocks
 *
 * Creates mesh geometry for hash blocks where each face can be independently displaced.
 * Unlike cube blocks where corners are shared, hash blocks have 4 independent points per face (24 total).
 * This allows faces to slide past each other creating a hash (#) pattern when viewed from above.
 */

import { Vector3, Matrix } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';
import type { TextureDefinition, Block } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('HashRenderer');

/**
 * HashRenderer - Renders hash blocks
 *
 * Hash blocks have 24 independent points (4 per face) allowing each face to be displaced
 * independently. Offsets only affect specific axes per face:
 * - Top/Bottom faces: X and Z offsets (Y is fixed)
 * - Left/Right faces: Y and Z offsets (X is fixed)
 * - Front/Back faces: X and Y offsets (Z is fixed)
 *
 * When all offsets are 0, renders as a normal cube.
 */
export class HashRenderer extends BlockRenderer {
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
   * Render a hash block into the mesh data
   *
   * @param renderContext - Render context
   * @param block - The block to render
   */
  async render(renderContext: RenderContext, block: ClientBlock): Promise<void> {
    const renderService = renderContext.renderService;
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

    logger.debug('Rendering hash block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
    });

    const size = 1;
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // Define 24 points (4 per face)
    // Each face has its own 4 points, allowing independent displacement
    const points: number[][] = [];

    // Top face (4 points) - Indices 0-3
    points.push(
      [worldX, worldY + size, worldZ],              // 0: left-back
      [worldX + size, worldY + size, worldZ],       // 1: right-back
      [worldX + size, worldY + size, worldZ + size], // 2: right-front
      [worldX, worldY + size, worldZ + size]        // 3: left-front
    );

    // Bottom face (4 points) - Indices 4-7
    points.push(
      [worldX, worldY, worldZ],              // 4: left-back
      [worldX, worldY, worldZ + size],       // 5: left-front
      [worldX + size, worldY, worldZ + size], // 6: right-front
      [worldX + size, worldY, worldZ]        // 7: right-back
    );

    // Left face (4 points) - Indices 8-11
    points.push(
      [worldX, worldY, worldZ],              // 8: back-bottom
      [worldX, worldY + size, worldZ],       // 9: back-top
      [worldX, worldY + size, worldZ + size], // 10: front-top
      [worldX, worldY, worldZ + size]        // 11: front-bottom
    );

    // Right face (4 points) - Indices 12-15
    points.push(
      [worldX + size, worldY, worldZ],       // 12: back-bottom
      [worldX + size, worldY, worldZ + size], // 13: front-bottom
      [worldX + size, worldY + size, worldZ + size], // 14: front-top
      [worldX + size, worldY + size, worldZ] // 15: back-top
    );

    // Front face (4 points) - Indices 16-19
    points.push(
      [worldX, worldY, worldZ + size],       // 16: left-bottom
      [worldX, worldY + size, worldZ + size], // 17: left-top
      [worldX + size, worldY + size, worldZ + size], // 18: right-top
      [worldX + size, worldY, worldZ + size] // 19: right-bottom
    );

    // Back face (4 points) - Indices 20-23
    points.push(
      [worldX, worldY, worldZ],              // 20: left-bottom
      [worldX + size, worldY, worldZ],       // 21: right-bottom
      [worldX + size, worldY + size, worldZ], // 22: right-top
      [worldX, worldY + size, worldZ]        // 23: left-top
    );

    // Apply offsets with face-specific axis constraints
    // Offset array: [x0,y0,z0, x1,y1,z1, ..., x23,y23,z23] (72 values)
    const offsets = block.block.offsets;
    if (offsets) {
      // Top face (0-3): Only X and Z offsets (Y fixed at top)
      for (let i = 0; i < 4 && i * 3 + 2 < offsets.length; i++) {
        points[i][0] += offsets[i * 3] ?? 0;     // X offset
        // Y stays at worldY + size
        points[i][2] += offsets[i * 3 + 2] ?? 0; // Z offset
      }

      // Bottom face (4-7): Only X and Z offsets (Y fixed at bottom)
      for (let i = 4; i < 8 && i * 3 + 2 < offsets.length; i++) {
        points[i][0] += offsets[i * 3] ?? 0;     // X offset
        // Y stays at worldY
        points[i][2] += offsets[i * 3 + 2] ?? 0; // Z offset
      }

      // Left face (8-11): Only Y and Z offsets (X fixed at left)
      for (let i = 8; i < 12 && i * 3 + 2 < offsets.length; i++) {
        // X stays at worldX
        points[i][1] += offsets[i * 3 + 1] ?? 0; // Y offset
        points[i][2] += offsets[i * 3 + 2] ?? 0; // Z offset
      }

      // Right face (12-15): Only Y and Z offsets (X fixed at right)
      for (let i = 12; i < 16 && i * 3 + 2 < offsets.length; i++) {
        // X stays at worldX + size
        points[i][1] += offsets[i * 3 + 1] ?? 0; // Y offset
        points[i][2] += offsets[i * 3 + 2] ?? 0; // Z offset
      }

      // Front face (16-19): Only X and Y offsets (Z fixed at front)
      for (let i = 16; i < 20 && i * 3 + 2 < offsets.length; i++) {
        points[i][0] += offsets[i * 3] ?? 0;     // X offset
        points[i][1] += offsets[i * 3 + 1] ?? 0; // Y offset
        // Z stays at worldZ + size
      }

      // Back face (20-23): Only X and Y offsets (Z fixed at back)
      for (let i = 20; i < 24 && i * 3 + 2 < offsets.length; i++) {
        points[i][0] += offsets[i * 3] ?? 0;     // X offset
        points[i][1] += offsets[i * 3 + 1] ?? 0; // Y offset
        // Z stays at worldZ
      }
    }

    // Apply scaling (after offsets, before rotation)
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
      for (let i = 0; i < 24; i++) {
        // Translate to origin (relative to center)
        points[i][0] -= centerX;
        points[i][1] -= centerY;
        points[i][2] -= centerZ;

        // Apply scaling
        points[i][0] *= scalingX;
        points[i][1] *= scalingY;
        points[i][2] *= scalingZ;

        // Translate back
        points[i][0] += centerX;
        points[i][1] += centerY;
        points[i][2] += centerZ;
      }
    }

    // Apply rotation (after scaling)
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      for (let i = 0; i < 24; i++) {
        // Translate to origin
        const relativePos = new Vector3(
          points[i][0] - centerX,
          points[i][1] - centerY,
          points[i][2] - centerZ
        );

        // Apply rotation
        const rotatedPos = Vector3.TransformCoordinates(relativePos, rotationMatrix);

        // Translate back
        points[i][0] = rotatedPos.x + centerX;
        points[i][1] = rotatedPos.y + centerY;
        points[i][2] = rotatedPos.z + centerZ;
      }
    }

    // Determine texture indices for each face
    const topIndex = textures[1] ? 1 : (textures[7] ? 7 : 0);
    const bottomIndex = textures[2] ? 2 : (textures[7] ? 7 : 0);
    const leftIndex = textures[3] ? 3 : (textures[7] ? 7 : 0);
    const rightIndex = textures[4] ? 4 : (textures[7] ? 7 : 0);
    const frontIndex = textures[5] ? 5 : (textures[7] ? 7 : 0);
    const backIndex = textures[6] ? 6 : (textures[7] ? 7 : 0);

    // Check face visibility (priority: modifier.faceVisibility > block.faceVisibility > default all visible)
    const isTopVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.TOP);
    const isBottomVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BOTTOM);
    const isLeftVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.LEFT);
    const isRightVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.RIGHT);
    const isFrontVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.FRONT);
    const isBackVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BACK);

    // Render faces
    let facesRendered = 0;

    // Top face (points 0-3)
    if (isTopVisible) {
      const texture = textures[topIndex] ? this.normalizeTexture(textures[topIndex]) : null;
      await this.addFace(
        points[0], points[1], points[2], points[3],  // left-back, right-back, right-front, left-front
        [0, 1, 0],  // Normal pointing up
        texture,
        modifier,
        block.block,
        renderContext,
        false  // reverseWinding
      );
      facesRendered++;
    }

    // Bottom face (points 4-7)
    if (isBottomVisible) {
      const texture = textures[bottomIndex] ? this.normalizeTexture(textures[bottomIndex]) : null;
      await this.addFace(
        points[4], points[5], points[6], points[7],  // left-back, left-front, right-front, right-back
        [0, -1, 0],  // Normal pointing down
        texture,
        modifier,
        block.block,
        renderContext,
        false  // reverseWinding
      );
      facesRendered++;
    }

    // Left face (points 8-11)
    if (isLeftVisible) {
      const texture = textures[leftIndex] ? this.normalizeTexture(textures[leftIndex]) : null;
      await this.addFace(
        points[8], points[11], points[10], points[9],  // back-bottom, front-bottom, front-top, back-top
        [-1, 0, 0],  // Normal pointing left
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Right face (points 12-15)
    if (isRightVisible) {
      const texture = textures[rightIndex] ? this.normalizeTexture(textures[rightIndex]) : null;
      await this.addFace(
        points[13], points[12], points[15], points[14],  // front-bottom, back-bottom, back-top, front-top
        [1, 0, 0],  // Normal pointing right
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Front face (points 16-19)
    if (isFrontVisible) {
      const texture = textures[frontIndex] ? this.normalizeTexture(textures[frontIndex]) : null;
      await this.addFace(
        points[16], points[19], points[18], points[17],  // left-bottom, right-bottom, right-top, left-top
        [0, 0, 1],  // Normal pointing forward
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Back face (points 20-23)
    if (isBackVisible) {
      const texture = textures[backIndex] ? this.normalizeTexture(textures[backIndex]) : null;
      await this.addFace(
        points[21], points[20], points[23], points[22],  // right-bottom, left-bottom, left-top, right-top
        [0, 0, -1],  // Normal pointing backward
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    logger.debug('Hash rendered', {
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
            atlasUV.u0, atlasUV.v0,  // corner0
            atlasUV.u1, atlasUV.v0,  // corner1
            atlasUV.u1, atlasUV.v1,  // corner2
            atlasUV.u0, atlasUV.v1   // corner3
          );
        } else {
          // Side faces: flip V coordinates (v0=top in texture, v1=bottom in texture)
          faceData.uvs.push(
            atlasUV.u0, atlasUV.v1,  // corner0 (world bottom) → v1 (texture bottom)
            atlasUV.u1, atlasUV.v1,  // corner1 (world bottom) → v1 (texture bottom)
            atlasUV.u1, atlasUV.v0,  // corner2 (world top) → v0 (texture top)
            atlasUV.u0, atlasUV.v0   // corner3 (world top) → v0 (texture top)
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
