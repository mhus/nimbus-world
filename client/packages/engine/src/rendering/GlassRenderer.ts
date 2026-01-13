/**
 * GlassRenderer - Renders glass cube blocks
 *
 * Creates separate mesh for glass blocks with transparency and glass-like material.
 * Glass blocks use a special material with color tint and specular highlights.
 * Supports all cube properties: offset, scaling, rotation.
 */

import { Mesh, VertexData, Vector3, Matrix } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper, TextureHelper } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';

const logger = getLogger('GlassRenderer');

/**
 * GlassRenderer - Renders glass cube blocks
 *
 * Creates separate meshes with glass material for each block.
 * Supports offset, scaling, rotation like CubeRenderer.
 */
export class GlassRenderer extends BlockRenderer {
  /**
   * Glass needs separate mesh (transparency requires separate rendering)
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a glass block
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

    logger.debug('Rendering glass block', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
    });

    const size = 1;

    // Block center for transformations
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // Define 8 corners
    const corners = [
      // Bottom corners
      [worldX, worldY, worldZ],                    // 0: left-back-bottom
      [worldX + size, worldY, worldZ],             // 1: right-back-bottom
      [worldX + size, worldY, worldZ + size],      // 2: right-front-bottom
      [worldX, worldY, worldZ + size],             // 3: left-front-bottom
      // Top corners
      [worldX, worldY + size, worldZ],             // 4: left-back-top
      [worldX + size, worldY + size, worldZ],      // 5: right-back-top
      [worldX + size, worldY + size, worldZ + size], // 6: right-front-top
      [worldX, worldY + size, worldZ + size],      // 7: left-front-top
    ];

    // Apply edge offsets if available
    const offsets = block.block.offsets;
    if (offsets) {
      for (let i = 0; i < 8 && i * 3 + 2 < offsets.length; i++) {
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

    // Apply scaling
    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
      for (let i = 0; i < 8; i++) {
        corners[i][0] -= centerX;
        corners[i][1] -= centerY;
        corners[i][2] -= centerZ;

        corners[i][0] *= scalingX;
        corners[i][1] *= scalingY;
        corners[i][2] *= scalingZ;

        corners[i][0] += centerX;
        corners[i][1] += centerY;
        corners[i][2] += centerZ;
      }
    }

    // Apply rotation
    const rotationX = block.block.rotation?.x ?? 0;
    const rotationY = block.block.rotation?.y ?? 0;

    if (rotationX !== 0 || rotationY !== 0) {
      const radX = rotationX * Math.PI / 180;
      const radY = rotationY * Math.PI / 180;

      const rotationMatrix = Matrix.RotationYawPitchRoll(radY, radX, 0);

      for (let i = 0; i < 8; i++) {
        const relativePos = new Vector3(
          corners[i][0] - centerX,
          corners[i][1] - centerY,
          corners[i][2] - centerZ
        );

        const rotatedPos = Vector3.TransformCoordinates(relativePos, rotationMatrix);

        corners[i][0] = rotatedPos.x + centerX;
        corners[i][1] = rotatedPos.y + centerY;
        corners[i][2] = rotatedPos.z + centerZ;
      }
    }

    // Create mesh data
    const positions: number[] = [];
    const indices: number[] = [];
    const normals: number[] = [];
    const uvs: number[] = [];
    let vertexOffset = 0;

    // Check face visibility (priority: modifier.faceVisibility > block.faceVisibility > default all visible)
    const isTopVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.TOP);
    const isBottomVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BOTTOM);
    const isLeftVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.LEFT);
    const isRightVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.RIGHT);
    const isFrontVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.FRONT);
    const isBackVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BACK);

    // Helper to add a face
    const addFace = (
      c0: number[], c1: number[], c2: number[], c3: number[],
      normal: number[],
      reverseWinding: boolean = false
    ) => {
      positions.push(
        c0[0], c0[1], c0[2],
        c1[0], c1[1], c1[2],
        c2[0], c2[1], c2[2],
        c3[0], c3[1], c3[2]
      );

      for (let i = 0; i < 4; i++) {
        normals.push(normal[0], normal[1], normal[2]);
      }

      // Simple UVs (no texture atlas for glass)
      uvs.push(0, 0, 1, 0, 1, 1, 0, 1);

      const i0 = vertexOffset;
      const i1 = vertexOffset + 1;
      const i2 = vertexOffset + 2;
      const i3 = vertexOffset + 3;

      if (reverseWinding) {
        indices.push(i0, i2, i1);
        indices.push(i0, i3, i2);
      } else {
        indices.push(i0, i1, i2);
        indices.push(i0, i2, i3);
      }

      vertexOffset += 4;
    };

    // Add faces
    if (isTopVisible) {
      addFace(corners[4], corners[5], corners[6], corners[7], [0, 1, 0]);
    }
    if (isBottomVisible) {
      addFace(corners[0], corners[3], corners[2], corners[1], [0, -1, 0]);
    }
    if (isLeftVisible) {
      addFace(corners[0], corners[4], corners[7], corners[3], [-1, 0, 0], true);
    }
    if (isRightVisible) {
      addFace(corners[1], corners[2], corners[6], corners[5], [1, 0, 0], true);
    }
    if (isFrontVisible) {
      addFace(corners[3], corners[7], corners[6], corners[2], [0, 0, 1], true);
    }
    if (isBackVisible) {
      addFace(corners[0], corners[1], corners[5], corners[4], [0, 0, -1], true);
    }

    // Create mesh
    const mesh = new Mesh(
      `glass_${worldX}_${worldY}_${worldZ}`,
      renderService.scene
    );

    const vertexData = new VertexData();
    vertexData.positions = positions;
    vertexData.indices = indices;
    vertexData.normals = normals;
    vertexData.uvs = uvs;

    vertexData.applyToMesh(mesh);

    // Get glass color and opacity from first texture
    const firstTexture = textures[0] || textures[7] || textures[1];
    const textureDef = firstTexture ? TextureHelper.normalizeTexture(firstTexture) : null;

    const glassColor = textureDef?.color ?? '#ffffff';
    const glassOpacity = textureDef?.opacity ?? 0.5;

    // Create glass material
    const materialKey = `glass:${glassColor}:${glassOpacity}`;
    let glassMaterial = renderService.materialService.materials.get(materialKey);

    if (!glassMaterial) {
      glassMaterial = renderService.materialService.createGlassMaterial(
        materialKey,
        glassColor,
        glassOpacity
      );
      renderService.materialService.materials.set(materialKey, glassMaterial);
    }

    mesh.material = glassMaterial;

    // Register mesh for illumination glow if block has illumination modifier
    const illuminationService = renderContext.renderService.appContext.services.illumination;
    if (illuminationService && modifier.illumination?.color) {
      illuminationService.registerMesh(
        mesh,
        modifier.illumination.color,
        modifier.illumination.strength ?? 1.0
      );
    }

    // Register mesh for disposal
    renderContext.resourcesToDispose.addMesh(mesh);

    logger.debug('Glass block rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
      color: glassColor,
      opacity: glassOpacity,
    });
  }
}
