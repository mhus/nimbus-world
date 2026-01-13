/**
 * CubeRenderer - Renders cube-shaped blocks
 *
 * Creates mesh geometry for standard cube blocks with proper UV mapping.
 */

import { VertexData, Vector3, Matrix } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';
import type { Block, BlockType, TextureDefinition, TextureKey, BlockModifier } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer} from './BlockRenderer';
import { RenderService, RenderContext } from '../services/RenderService';
import type { TextureAtlas, AtlasUV } from './TextureAtlas';

const logger = getLogger('CubeRenderer');

/**
 * Face data for cube rendering
 */
interface FaceData {
  positions: number[];
  indices: number[];
  uvs: number[];
  normals: number[];
  colors?: number[];  // RGBA colors (per-vertex, 4 values per vertex)
  // Wind attributes (per-vertex)
  windLeafiness?: number[];
  windStability?: number[];
  windLeverUp?: number[];
  windLeverDown?: number[];
}

/**
 * CubeRenderer - Renders cube blocks
 *
 * Creates optimized mesh data for cube-shaped blocks.
 * Supports face culling and UV mapping from texture atlas.
 */
export class CubeRenderer extends BlockRenderer {
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
   * Render a cube block into the mesh data
   *
    * @param renderService - The RenderService instance
    * @param block - The block to render
    * @param worldX - World X position of the block
    * @param worldY - World Y position of the block
    * @param worldZ - World Z position of the block
    * @returns Number of vertices added to the mesh data
   */
  async render(
      renderContext: RenderContext,
      block: ClientBlock,
  ): Promise<void> {
    const renderService = renderContext.renderService;
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    // Get block modifier for current status (from BlockType.initialStatus)
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

    logger.debug('Rendering cube block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      hasTextures: !!textures,
      textureCount: Object.keys(textures).length
    });

    // Determine texture indices for each face
    // Use texture[index] if available, otherwise use default texture[7] or texture[0]
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

    // Get materials for visible faces
    const topMaterial = isTopVisible && topIndex ? await renderService.materialService.getMaterial(modifier, topIndex) : null;
    const bottomMaterial = isBottomVisible && bottomIndex ? await renderService.materialService.getMaterial(modifier, bottomIndex) : null;
    const leftMaterial = isLeftVisible && leftIndex ? await renderService.materialService.getMaterial(modifier, leftIndex) : null;
    const rightMaterial = isRightVisible && rightIndex ? await renderService.materialService.getMaterial(modifier, rightIndex) : null;
    const frontMaterial = isFrontVisible && frontIndex ? await renderService.materialService.getMaterial(modifier, frontIndex) : null;
    const backMaterial = isBackVisible && backIndex ? await renderService.materialService.getMaterial(modifier, backIndex) : null;

    logger.debug('Face visibility and materials', {
      faces: {
        top: { visible: isTopVisible, hasMaterial: !!topMaterial },
        bottom: { visible: isBottomVisible, hasMaterial: !!bottomMaterial },
        left: { visible: isLeftVisible, hasMaterial: !!leftMaterial },
        right: { visible: isRightVisible, hasMaterial: !!rightMaterial },
        front: { visible: isFrontVisible, hasMaterial: !!frontMaterial },
        back: { visible: isBackVisible, hasMaterial: !!backMaterial }
      }
    });

    const size = 1;

    // Block center for rotation
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    const corners = [
      // Bottom face (y = y)
      [worldX, worldY, worldZ], // 0: left-back-bottom
      [worldX + size, worldY, worldZ], // 1: right-back-bottom
      [worldX + size, worldY, worldZ + size], // 2: right-front-bottom
      [worldX, worldY, worldZ + size], // 3: left-front-bottom
      // Top face (y = y + size)
      [worldX, worldY + size, worldZ], // 4: left-back-top
      [worldX + size, worldY + size, worldZ], // 5: right-back-top
      [worldX + size, worldY + size, worldZ + size], // 6: right-front-top
      [worldX, worldY + size, worldZ + size], // 7: left-front-top
    ];

    // Apply edge offsets if available
    const offsets = block.block.offsets;
    if (offsets && offsets.length >= 3) {
      for (let i = 0; i < 8; i++) {
        if (!offsets[i * 3] && !offsets[i * 3 + 1] && !offsets[i * 3 + 2]) {
          continue; // No offset for this corner
        }
        const offsetX = offsets[i * 3];
        const offsetY = offsets[i * 3 + 1];
        const offsetZ = offsets[i * 3 + 2];

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
      for (let i = 0; i < 8; i++) {
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
      // YawPitchRoll corresponds to Y, X, Z rotations (Z is 0)
      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      // Apply rotation to each corner around the block center
      for (let i = 0; i < 8; i++) {
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

    // Render visible faces (with or without material)
    let facesRendered = 0;

    // Top face (y = y + size)
    if (isTopVisible) {
      const texture = textures[topIndex] ? this.normalizeTexture(textures[topIndex]) : null;
      await this.addFace(
        corners[4], corners[5], corners[6], corners[7],  // left-back, right-back, right-front, left-front
        [0, 1, 0],  // Normal pointing up
        texture,
        modifier,
        block.block,
        renderContext,
        false  // reverseWinding
      );
      facesRendered++;
    }

    // Bottom face (y = y)
    if (isBottomVisible) {
      const texture = textures[bottomIndex] ? this.normalizeTexture(textures[bottomIndex]) : null;
      await this.addFace(
        corners[0], corners[3], corners[2], corners[1],  // left-back, left-front, right-front, right-back
        [0, -1, 0],  // Normal pointing down
        texture,
        modifier,
        block.block,
        renderContext,
        false  // reverseWinding
      );
      facesRendered++;
    }

    // Left face (x = x)
    if (isLeftVisible) {
      const texture = textures[leftIndex] ? this.normalizeTexture(textures[leftIndex]) : null;
      await this.addFace(
        corners[0], corners[3], corners[7], corners[4],  // back-bottom, front-bottom, front-top, back-top
        [-1, 0, 0],  // Normal pointing left
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Right face (x = x + size) - East
    if (isRightVisible) {
      const texture = textures[rightIndex] ? this.normalizeTexture(textures[rightIndex]) : null;
      await this.addFace(
        corners[2], corners[1], corners[5], corners[6],  // front-bottom, back-bottom, back-top, front-top
        [1, 0, 0],  // Normal pointing right
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Front face (z = z + size) - North
    if (isFrontVisible) {
      const texture = textures[frontIndex] ? this.normalizeTexture(textures[frontIndex]) : null;
      await this.addFace(
        corners[3], corners[2], corners[6], corners[7],  // left-bottom, right-bottom, right-top, left-top
        [0, 0, 1],  // Normal pointing forward
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    // Back face (z = z) - South
    if (isBackVisible) {
      const texture = textures[backIndex] ? this.normalizeTexture(textures[backIndex]) : null;
      await this.addFace(
        corners[1], corners[0], corners[4], corners[5],  // right-bottom, left-bottom, left-top, right-top
        [0, 0, -1],  // Normal pointing backward
        texture,
        modifier,
        block.block,
        renderContext,
        true  // Reverse winding order
      );
      facesRendered++;
    }

    logger.debug('Cube rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      facesRendered
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
    modifier: BlockModifier,
    block: Block,
    renderContext : RenderContext,
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

    // Add 4 normals (same normal for all vertices of this face)
    for (let i = 0; i < 4; i++) {
      faceData.normals.push(normal[0], normal[1], normal[2]);
    }

    // Add 4 UV coordinates
    if (texture && this.textureAtlas) {
      const atlasUV = await this.textureAtlas.getTextureUV(texture);
      if (atlasUV) {
        // Determine if this is a horizontal face (top/bottom) or vertical face (sides)
        const isHorizontalFace = normal[1] !== 0; // normal.y != 0 means top or bottom face

        if (isHorizontalFace) {
          // Top/Bottom faces: standard UV mapping
          faceData.uvs.push(
            atlasUV.u0, atlasUV.v0,  // corner0: bottom-left
            atlasUV.u1, atlasUV.v0,  // corner1: bottom-right
            atlasUV.u1, atlasUV.v1,  // corner2: top-right
            atlasUV.u0, atlasUV.v1   // corner3: top-left
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

    // Add wind attributes and colors (uses helper from base class)
    this.addWindAttributesAndColors(faceData, modifier, block, 4);

    renderContext.vertexOffset += 4;  // 4 vertices added
  }

}
