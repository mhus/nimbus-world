/**
 * CylinderRenderer - Renders cylinder-shaped blocks
 *
 * Creates mesh geometry for cylinder blocks with proper UV mapping.
 * Supports independent radius control for top/bottom and displacement.
 */

import { Vector3, Matrix } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';
import type { TextureDefinition } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('CylinderRenderer');

/**
 * CylinderRenderer - Renders cylinder blocks
 *
 * Creates optimized mesh data for cylinder-shaped blocks.
 * Supports radius offset (top/bottom), displacement, scaling, rotation, and UV mapping.
 */
export class CylinderRenderer extends BlockRenderer {
  private textureAtlas: TextureAtlas;

  // Cylinder resolution (segments around the circle)
  private readonly RADIAL_SEGMENTS = 16;

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
   * Render a cylinder block into the mesh data
   *
   * Offset format for CYLINDER:
   * [0,1,2]: Radius Top (X, Y-unused, Z) - modifies top radius
   * [3,4,5]: Radius Bottom (X, Y-unused, Z) - modifies bottom radius
   * [6,7,8]: Displacement Top (X, Y, Z) - shifts top position
   * [9,10,11]: Displacement Bottom (X, Y, Z) - shifts bottom position
   *
   * @param renderContext - Render context
   * @param block - The block to render
   */
  async render(renderContext: RenderContext, block: ClientBlock): Promise<void> {
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    // Get block modifier
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

    logger.debug('Rendering cylinder block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      hasTextures: !!textures,
    });

    const size = 1;
    const baseRadius = size / 2;

    // Block center
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // Get offsets from visibility
    const offsets = block.block.offsets;
    let radiusTopX = baseRadius;
    let radiusTopZ = baseRadius;
    let radiusBottomX = baseRadius;
    let radiusBottomZ = baseRadius;
    let displacementTopX = 0;
    let displacementTopY = 0;
    let displacementTopZ = 0;
    let displacementBottomX = 0;
    let displacementBottomY = 0;
    let displacementBottomZ = 0;

    if (offsets && offsets.length >= 12) {
      // Radius offsets (added to base radius)
      radiusTopX = baseRadius + (offsets[0] ?? 0);
      radiusTopZ = baseRadius + (offsets[2] ?? 0);
      radiusBottomX = baseRadius + (offsets[3] ?? 0);
      radiusBottomZ = baseRadius + (offsets[5] ?? 0);

      // Displacements
      displacementTopX = offsets[6] ?? 0;
      displacementTopY = offsets[7] ?? 0;
      displacementTopZ = offsets[8] ?? 0;
      displacementBottomX = offsets[9] ?? 0;
      displacementBottomY = offsets[10] ?? 0;
      displacementBottomZ = offsets[11] ?? 0;
    }

    // Apply scaling to radii
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    radiusTopX *= scalingX;
    radiusTopZ *= scalingZ;
    radiusBottomX *= scalingX;
    radiusBottomZ *= scalingZ;

    // Check face visibility (priority: modifier.faceVisibility > block.faceVisibility > default all visible)
    const isTopVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.TOP);
    const isBottomVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BOTTOM);
    const isSideVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.FRONT);

    // Generate cylinder vertices
    const vertices: Vector3[] = [];
    const uvs: number[] = [];
    const indices: number[] = [];
    const normals: number[] = [];

    const segments = this.RADIAL_SEGMENTS;
    const height = size * scalingY;

    // Bottom center (for cap)
    const bottomCenterY = worldY + displacementBottomY;
    const bottomCenterX = centerX + displacementBottomX;
    const bottomCenterZ = centerZ + displacementBottomZ;

    // Top center (for cap)
    const topCenterY = worldY + height + displacementTopY;
    const topCenterX = centerX + displacementTopX;
    const topCenterZ = centerZ + displacementTopZ;

    // Generate vertices for cylinder sides (only if visible)
    let sideVertexStart = 0;
    if (isSideVisible) {
      sideVertexStart = vertices.length;
      for (let i = 0; i <= segments; i++) {
        const angle = (i / segments) * Math.PI * 2;
        const cosA = Math.cos(angle);
        const sinA = Math.sin(angle);

        // Bottom vertex
        const bottomX = bottomCenterX + radiusBottomX * cosA;
        const bottomZ = bottomCenterZ + radiusBottomZ * sinA;

        // Top vertex
        const topX = topCenterX + radiusTopX * cosA;
        const topZ = topCenterZ + radiusTopZ * sinA;

        // Add bottom vertex
        vertices.push(new Vector3(bottomX, bottomCenterY, bottomZ));
        // Add top vertex
        vertices.push(new Vector3(topX, topCenterY, topZ));

        // UV coordinates (wrap around cylinder)
        // Note: V-coordinates inverted - v0=top in texture, v1=bottom in texture
        const u = i / segments;
        uvs.push(u, 1); // Bottom (v1 = bottom in texture)
        uvs.push(u, 0); // Top (v0 = top in texture)

        // Normal for cylinder sides (pointing outward)
        const normal = new Vector3(cosA, 0, sinA).normalize();
        normals.push(normal.x, normal.y, normal.z); // Bottom
        normals.push(normal.x, normal.y, normal.z); // Top
      }
    }

    // Generate indices for cylinder sides (only if visible)
    if (isSideVisible) {
      for (let i = 0; i < segments; i++) {
        const bottom1 = sideVertexStart + i * 2;
        const top1 = sideVertexStart + i * 2 + 1;
        const bottom2 = sideVertexStart + (i + 1) * 2;
        const top2 = sideVertexStart + (i + 1) * 2 + 1;

        // Two triangles per segment
        indices.push(bottom1, bottom2, top1);
        indices.push(top1, bottom2, top2);
      }
    }

    // Add bottom cap (only if visible)
    let bottomCapStartVertex = -1;
    if (isBottomVisible) {
      bottomCapStartVertex = vertices.length;
      vertices.push(new Vector3(bottomCenterX, bottomCenterY, bottomCenterZ)); // Center vertex
      normals.push(0, -1, 0); // Normal pointing down
      uvs.push(0.5, 0.5); // Center UV

      for (let i = 0; i <= segments; i++) {
        const angle = (i / segments) * Math.PI * 2;
        const cosA = Math.cos(angle);
        const sinA = Math.sin(angle);

        const x = bottomCenterX + radiusBottomX * cosA;
        const z = bottomCenterZ + radiusBottomZ * sinA;

        vertices.push(new Vector3(x, bottomCenterY, z));
        normals.push(0, -1, 0);

        // UV for cap (circular mapping)
        const u = 0.5 + 0.5 * cosA;
        const v = 0.5 + 0.5 * sinA;
        uvs.push(u, v);
      }

      // Bottom cap indices (clockwise winding for downward face when viewed from below)
      for (let i = 0; i < segments; i++) {
        indices.push(
          bottomCapStartVertex,
          bottomCapStartVertex + i + 2,
          bottomCapStartVertex + i + 1
        );
      }
    }

    // Add top cap (only if visible)
    let topCapStartVertex = -1;
    if (isTopVisible) {
      topCapStartVertex = vertices.length;
      vertices.push(new Vector3(topCenterX, topCenterY, topCenterZ)); // Center vertex
      normals.push(0, 1, 0); // Normal pointing up
      uvs.push(0.5, 0.5); // Center UV

      for (let i = 0; i <= segments; i++) {
        const angle = (i / segments) * Math.PI * 2;
        const cosA = Math.cos(angle);
        const sinA = Math.sin(angle);

        const x = topCenterX + radiusTopX * cosA;
        const z = topCenterZ + radiusTopZ * sinA;

        vertices.push(new Vector3(x, topCenterY, z));
        normals.push(0, 1, 0);

        // UV for cap (circular mapping)
        const u = 0.5 + 0.5 * cosA;
        const v = 0.5 + 0.5 * sinA;
        uvs.push(u, v);
      }

      // Top cap indices (counter-clockwise winding for upward face)
      for (let i = 0; i < segments; i++) {
        indices.push(
          topCapStartVertex,
          topCapStartVertex + i + 1,
          topCapStartVertex + i + 2
        );
      }
    }

    // Apply rotation if specified (after all vertices are created)
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      // Convert degrees to radians
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      // Create rotation matrix
      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      // Rotation center (average of top and bottom centers)
      const rotCenterX = (bottomCenterX + topCenterX) / 2;
      const rotCenterY = (bottomCenterY + topCenterY) / 2;
      const rotCenterZ = (bottomCenterZ + topCenterZ) / 2;

      // Apply rotation to each vertex and normal
      for (let i = 0; i < vertices.length; i++) {
        // Translate to rotation center
        const relativePos = new Vector3(
          vertices[i].x - rotCenterX,
          vertices[i].y - rotCenterY,
          vertices[i].z - rotCenterZ
        );

        // Apply rotation
        const rotatedPos = Vector3.TransformCoordinates(relativePos, rotationMatrix);

        // Translate back
        vertices[i].x = rotatedPos.x + rotCenterX;
        vertices[i].y = rotatedPos.y + rotCenterY;
        vertices[i].z = rotatedPos.z + rotCenterZ;

        // Rotate normals
        const normal = new Vector3(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
        const rotatedNormal = Vector3.TransformNormal(normal, rotationMatrix);
        normals[i * 3] = rotatedNormal.x;
        normals[i * 3 + 1] = rotatedNormal.y;
        normals[i * 3 + 2] = rotatedNormal.z;
      }
    }

    // Get texture (use texture 0, SIDE, or ALL)
    const textureIndex = textures[0] ? 0 : (textures[7] ? 7 : 0);
    const texture = textures[textureIndex] ? this.normalizeTexture(textures[textureIndex]) : null;

    // Get atlas UVs if texture available
    let atlasUV = null;
    if (texture && this.textureAtlas) {
      atlasUV = await this.textureAtlas.getTextureUV(texture);
    }

    // Add all vertices to face data
    const faceData = renderContext.faceData;
    const vertexOffset = renderContext.vertexOffset;

    for (let i = 0; i < vertices.length; i++) {
      faceData.positions.push(vertices[i].x, vertices[i].y, vertices[i].z);
      faceData.normals.push(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);

      // Map UV to atlas if available
      if (atlasUV) {
        const u = uvs[i * 2];
        const v = uvs[i * 2 + 1];
        // Map [0,1] range to atlas UV range
        const atlasU = atlasUV.u0 + u * (atlasUV.u1 - atlasUV.u0);
        const atlasV = atlasUV.v0 + v * (atlasUV.v1 - atlasUV.v0);
        faceData.uvs.push(atlasU, atlasV);
      } else {
        faceData.uvs.push(uvs[i * 2], uvs[i * 2 + 1]);
      }
    }

    // Add wind attributes and colors (uses helper from base class)
    this.addWindAttributesAndColors(faceData, modifier, block.block, vertices.length);

    // Add indices (offset by current vertex count)
    for (let i = 0; i < indices.length; i++) {
      faceData.indices.push(indices[i] + vertexOffset);
    }

    renderContext.vertexOffset += vertices.length;

    logger.debug('Cylinder rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      vertices: vertices.length,
      triangles: indices.length / 3,
      radiusTop: { x: radiusTopX, z: radiusTopZ },
      radiusBottom: { x: radiusBottomX, z: radiusBottomZ },
      parts: {
        side: isSideVisible,
        topCap: isTopVisible,
        bottomCap: isBottomVisible
      }
    });
  }
}
