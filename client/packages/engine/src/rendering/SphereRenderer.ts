/**
 * SphereRenderer - Renders sphere-shaped blocks
 *
 * Creates mesh geometry for sphere blocks with proper UV mapping.
 * Supports radius offset and displacement transformations.
 */

import { Vector3, Matrix, VertexData } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { TextureDefinition } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('SphereRenderer');

/**
 * SphereRenderer - Renders sphere blocks
 *
 * Creates optimized mesh data for sphere-shaped blocks.
 * Supports radius offset, displacement, scaling, rotation, and UV mapping.
 */
export class SphereRenderer extends BlockRenderer {
  private textureAtlas: TextureAtlas;

  // Sphere resolution (segments)
  private readonly HORIZONTAL_SEGMENTS = 16;
  private readonly VERTICAL_SEGMENTS = 12;

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
   * Render a sphere block into the mesh data
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

    logger.debug('Rendering sphere block', {
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
    // Offset format for SPHERE:
    // [0-2]: Radius Offset (XYZ) - modifies the radius
    // [3-5]: Displacement (XYZ) - shifts the sphere position
    const offsets = block.block.offsets;
    let radiusOffsetX = 0;
    let radiusOffsetY = 0;
    let radiusOffsetZ = 0;
    let displacementX = 0;
    let displacementY = 0;
    let displacementZ = 0;

    if (offsets && offsets.length >= 6) {
      radiusOffsetX = offsets[0] ?? 0;
      radiusOffsetY = offsets[1] ?? 0;
      radiusOffsetZ = offsets[2] ?? 0;
      displacementX = offsets[3] ?? 0;
      displacementY = offsets[4] ?? 0;
      displacementZ = offsets[5] ?? 0;
    }

    // Apply scaling
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    // Calculate effective radius for each axis
    const radiusX = (baseRadius + radiusOffsetX) * scalingX;
    const radiusY = (baseRadius + radiusOffsetY) * scalingY;
    const radiusZ = (baseRadius + radiusOffsetZ) * scalingZ;

    // Generate sphere vertices
    const vertices: Vector3[] = [];
    const uvs: number[] = [];
    const indices: number[] = [];
    const normals: number[] = [];

    const hSegments = this.HORIZONTAL_SEGMENTS;
    const vSegments = this.VERTICAL_SEGMENTS;

    // Generate vertices
    for (let lat = 0; lat <= vSegments; lat++) {
      const theta = (lat * Math.PI) / vSegments; // 0 to PI (top to bottom)
      const sinTheta = Math.sin(theta);
      const cosTheta = Math.cos(theta);

      for (let lon = 0; lon <= hSegments; lon++) {
        const phi = (lon * 2 * Math.PI) / hSegments; // 0 to 2PI (around)
        const sinPhi = Math.sin(phi);
        const cosPhi = Math.cos(phi);

        // Sphere point (before displacement)
        const x = radiusX * sinTheta * cosPhi;
        const y = radiusY * cosTheta;
        const z = radiusZ * sinTheta * sinPhi;

        // Normal (normalized direction from center)
        const normal = new Vector3(
          sinTheta * cosPhi,
          cosTheta,
          sinTheta * sinPhi
        ).normalize();

        // Create vertex position (relative to center, before rotation)
        vertices.push(new Vector3(x, y, z));
        normals.push(normal.x, normal.y, normal.z);

        // UV coordinates
        const u = lon / hSegments;
        const v = lat / vSegments;
        uvs.push(u, v);
      }
    }

    // Apply rotation if specified (around origin, before translation)
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      // Convert degrees to radians
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      // Create rotation matrix
      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      // Apply rotation to each vertex and normal
      for (let i = 0; i < vertices.length; i++) {
        vertices[i] = Vector3.TransformCoordinates(vertices[i], rotationMatrix);

        // Rotate normals too
        const normal = new Vector3(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
        const rotatedNormal = Vector3.TransformNormal(normal, rotationMatrix);
        normals[i * 3] = rotatedNormal.x;
        normals[i * 3 + 1] = rotatedNormal.y;
        normals[i * 3 + 2] = rotatedNormal.z;
      }
    }

    // Translate vertices to world position (center + displacement)
    const finalCenterX = centerX + displacementX;
    const finalCenterY = centerY + displacementY;
    const finalCenterZ = centerZ + displacementZ;

    for (let i = 0; i < vertices.length; i++) {
      vertices[i].x += finalCenterX;
      vertices[i].y += finalCenterY;
      vertices[i].z += finalCenterZ;
    }

    // Generate indices
    for (let lat = 0; lat < vSegments; lat++) {
      for (let lon = 0; lon < hSegments; lon++) {
        const first = lat * (hSegments + 1) + lon;
        const second = first + hSegments + 1;

        // Two triangles per quad
        indices.push(first, second, first + 1);
        indices.push(second, second + 1, first + 1);
      }
    }

    // Get texture (use texture 0, or DIFFUSE, or ALL)
    const textureIndex = textures[0] ? 0 : (textures[8] ? 8 : 0);
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

    // Add indices (offset by current vertex count)
    for (let i = 0; i < indices.length; i++) {
      faceData.indices.push(indices[i] + vertexOffset);
    }

    // Add wind attributes and colors (uses helper from base class)
    this.addWindAttributesAndColors(faceData, modifier, block.block, vertices.length);

    renderContext.vertexOffset += vertices.length;

    logger.debug('Sphere rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      vertices: vertices.length,
      triangles: indices.length / 3,
      radiusOffset: { x: radiusOffsetX, y: radiusOffsetY, z: radiusOffsetZ },
      displacement: { x: displacementX, y: displacementY, z: displacementZ },
    });
  }
}
