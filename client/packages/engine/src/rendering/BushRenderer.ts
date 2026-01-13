/**
 * BushRenderer - Renders bush-shaped blocks
 *
 * Creates mesh geometry for bushes using multiple intersecting planes
 * to simulate foliage. The planes are arranged at various angles to
 * create a fuller, more natural bush appearance.
 */

import { Vector3, Matrix } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { TextureDefinition, Block, BlockModifier } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('BushRenderer');

/**
 * BushRenderer - Renders bush blocks
 *
 * Creates optimized mesh data for bush-shaped blocks using multiple
 * intersecting vertical planes. Offsets control the size and number
 * of planes:
 * - offset[0]: Number of planes (4-8, default: 6)
 * - offset[1]: Scale factor (0.3-1.5, default: 1.0)
 * - offset[2]: Variance/randomness (0.0-0.5, default: 0.3)
 *
 * Supports rotation and wind animation like CubeRenderer.
 */
export class BushRenderer extends BlockRenderer {
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
   * Render a bush block into the mesh data
   *
   * @param renderContext - Render context
   * @param block - The block to render
   */
  async render(renderContext: RenderContext, block: ClientBlock): Promise<void> {
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

    logger.debug('Rendering bush block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      hasTextures: !!textures,
      textureCount: Object.keys(textures).length
    });

    const size = 1;

    // Block center for transformations
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // Get offsets for controlling bush appearance
    const offsets = block.block.offsets || [];

    // offset[0]: Number of planes (4-8, default: 6)
    let planeCount = Math.floor(offsets[0] ?? 6);
    planeCount = Math.max(4, Math.min(8, planeCount));

    // offset[1]: Scale factor (0.3-1.5, default: 1.0)
    let scaleFactor = offsets[1] ?? 1.0;
    scaleFactor = Math.max(0.3, Math.min(1.5, scaleFactor));

    // offset[2]: Variance/randomness (0.0-0.5, default: 0.3)
    let variance = offsets[2] ?? 0.3;
    variance = Math.max(0.0, Math.min(0.5, variance));

    // Apply global scaling if specified
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    // Get texture (use texture 0, fall back to texture 7 or 0)
    const textureIndex = textures[0] ? 0 : (textures[7] ? 7 : 0);
    const texture = textures[textureIndex] ? this.normalizeTexture(textures[textureIndex]) : null;

    if (!texture) {
      logger.warn('No valid texture found for bush', { blockTypeId: block.blockType.id });
      return;
    }

    // Create rotation matrix if needed
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;
    const hasRotation = rotationX !== 0 || rotationY !== 0;

    let rotationMatrix: Matrix | null = null;
    if (hasRotation) {
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;
      rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);
    }

    // Generate planes at different angles
    for (let i = 0; i < planeCount; i++) {
      // Calculate angle for this plane (evenly distributed around 180 degrees)
      const baseAngle = (i / planeCount) * Math.PI;

      // Add some variance to the angle based on offset[2]
      // Use deterministic pseudo-random based on position and plane index
      const seed = (worldX * 73856093) ^ (worldY * 19349663) ^ (worldZ * 83492791) ^ (i * 541);
      const pseudoRandom = ((Math.sin(seed) * 0.5 + 0.5) * 2 - 1); // -1 to 1
      const angleVariance = pseudoRandom * variance * (Math.PI / 4); // Up to ±45° variance
      const angle = baseAngle + angleVariance;

      // Calculate scale for this plane (vary slightly for more natural look)
      const scaleVariance = 1.0 + pseudoRandom * variance * 0.3; // ±30% variance
      const planeScale = scaleFactor * scaleVariance;

      // Calculate plane size (slightly smaller than full block for natural look)
      const planeWidth = 0.9 * planeScale;
      const planeHeight = 0.9 * planeScale;

      // Calculate position offset for this plane (slight variation in Y)
      const yOffset = pseudoRandom * variance * 0.2; // Slight vertical variation

      // Calculate the 4 corners of this plane
      // Plane is centered at block center, oriented at 'angle'
      const halfWidth = planeWidth / 2;
      const halfHeight = planeHeight / 2;

      // Base corners (before rotation and scaling)
      const corners = [
        new Vector3(-halfWidth, -halfHeight + yOffset, 0),  // bottom-left
        new Vector3(halfWidth, -halfHeight + yOffset, 0),   // bottom-right
        new Vector3(halfWidth, halfHeight + yOffset, 0),    // top-right
        new Vector3(-halfWidth, halfHeight + yOffset, 0),   // top-left
      ];

      // Rotate plane around Y-axis by the calculated angle
      const planeRotation = Matrix.RotationY(angle);
      for (let j = 0; j < 4; j++) {
        corners[j] = Vector3.TransformCoordinates(corners[j], planeRotation);
      }

      // Apply global scaling
      if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
        const scaleMatrix = Matrix.Scaling(scalingX, scalingY, scalingZ);
        for (let j = 0; j < 4; j++) {
          corners[j] = Vector3.TransformCoordinates(corners[j], scaleMatrix);
        }
      }

      // Apply block rotation if specified
      if (rotationMatrix) {
        for (let j = 0; j < 4; j++) {
          corners[j] = Vector3.TransformCoordinates(corners[j], rotationMatrix);
        }
      }

      // Translate to world position
      for (let j = 0; j < 4; j++) {
        corners[j].x += centerX;
        corners[j].y += centerY;
        corners[j].z += centerZ;
      }

      // Calculate normal (perpendicular to plane, in world space)
      const edge1 = corners[1].subtract(corners[0]);
      const edge2 = corners[3].subtract(corners[0]);
      const normal = Vector3.Cross(edge1, edge2).normalize();

      // Add this plane as a face
      await this.addFace(
        [corners[0].x, corners[0].y, corners[0].z],
        [corners[1].x, corners[1].y, corners[1].z],
        [corners[2].x, corners[2].y, corners[2].z],
        [corners[3].x, corners[3].y, corners[3].z],
        [normal.x, normal.y, normal.z],
        texture,
        modifier,
        block.block,
        renderContext,
        false  // Standard winding order
      );
    }

    logger.debug('Bush rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      planesRendered: planeCount
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
   * @param modifier - Block modifier (contains wind properties)
   * @param block - Block instance
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

    // Add 4 normals (same normal for all vertices of this face)
    for (let i = 0; i < 4; i++) {
      faceData.normals.push(normal[0], normal[1], normal[2]);
    }

    // Add 4 UV coordinates
    if (texture && this.textureAtlas) {
      const atlasUV = await this.textureAtlas.getTextureUV(texture);
      if (atlasUV) {
        // Bush faces are vertical, flip V coordinates
        faceData.uvs.push(
          atlasUV.u0, atlasUV.v1,  // corner0 (world bottom) → v1 (texture bottom)
          atlasUV.u1, atlasUV.v1,  // corner1 (world bottom) → v1 (texture bottom)
          atlasUV.u1, atlasUV.v0,  // corner2 (world top) → v0 (texture top)
          atlasUV.u0, atlasUV.v0   // corner3 (world top) → v0 (texture top)
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
      faceData.indices.push(i0, i2, i1);
      faceData.indices.push(i0, i3, i2);
    } else {
      // Standard counter-clockwise winding
      faceData.indices.push(i0, i1, i2);
      faceData.indices.push(i0, i2, i3);
    }

    // Add wind attributes and colors (uses helper from base class)
    this.addWindAttributesAndColors(faceData, modifier, block, 4);

    renderContext.vertexOffset += 4;  // 4 vertices added
  }
}
