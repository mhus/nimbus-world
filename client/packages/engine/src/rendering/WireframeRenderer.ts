/**
 * WireframeRenderer - Renders cube grid lines only (no faces)
 *
 * Creates a separate LinesMesh per block showing the 12 cube edges.
 * Supports offset, scaling and rotation like CubeRenderer.
 * Color is taken from textures[TextureKey.ALL].color (fallback: white).
 */

import { MeshBuilder, Vector3, Matrix, Color3, Color4 } from '@babylonjs/core';
import { getLogger, TextureHelper, TextureKey } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('WireframeRenderer');

/**
 * WireframeRenderer - Renders cube edges as lines
 */
export class WireframeRenderer extends BlockRenderer {
  /**
   * Wireframe blocks use their own LinesMesh per block
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  async render(renderContext: RenderContext, block: ClientBlock): Promise<void> {
    const renderService = renderContext.renderService;
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    const modifier = block.currentModifier;
    if (!modifier || !modifier.visibility) {
      logger.debug('Block has no visibility modifier', { blockTypeId: block.blockType.name });
      return;
    }

    const size = 1;
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    const corners: number[][] = [
      // Bottom (y = y)
      [worldX, worldY, worldZ],                          // 0: left-back-bottom
      [worldX + size, worldY, worldZ],                   // 1: right-back-bottom
      [worldX + size, worldY, worldZ + size],            // 2: right-front-bottom
      [worldX, worldY, worldZ + size],                   // 3: left-front-bottom
      // Top (y = y + size)
      [worldX, worldY + size, worldZ],                   // 4: left-back-top
      [worldX + size, worldY + size, worldZ],            // 5: right-back-top
      [worldX + size, worldY + size, worldZ + size],     // 6: right-front-top
      [worldX, worldY + size, worldZ + size],            // 7: left-front-top
    ];

    const offsets = block.block.offsets;
    if (offsets && offsets.length >= 3) {
      for (let i = 0; i < 8; i++) {
        if (!offsets[i * 3] && !offsets[i * 3 + 1] && !offsets[i * 3 + 2]) {
          continue;
        }
        corners[i][0] += offsets[i * 3] ?? 0;
        corners[i][1] += offsets[i * 3 + 1] ?? 0;
        corners[i][2] += offsets[i * 3 + 2] ?? 0;
      }
    }

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

    const v = corners.map(c => new Vector3(c[0], c[1], c[2]));

    // 12 edges of the cube as separate line segments
    const lines: Vector3[][] = [
      // Bottom rectangle
      [v[0], v[1]], [v[1], v[2]], [v[2], v[3]], [v[3], v[0]],
      // Top rectangle
      [v[4], v[5]], [v[5], v[6]], [v[6], v[7]], [v[7], v[4]],
      // Vertical edges
      [v[0], v[4]], [v[1], v[5]], [v[2], v[6]], [v[3], v[7]],
    ];

    // Resolve color from textures[TextureKey.ALL].color
    const textures = modifier.visibility.textures;
    const allTexture = textures?.[TextureKey.ALL];
    const textureDef = allTexture ? TextureHelper.normalizeTexture(allTexture) : null;
    const colorHex = textureDef?.color ?? '#ffffff';
    const color = Color3.FromHexString(colorHex);

    const mesh = MeshBuilder.CreateLineSystem(
      `wireframe_${worldX}_${worldY}_${worldZ}`,
      { lines },
      renderService.scene
    );

    mesh.color = color;
    mesh.renderingGroupId = RENDERING_GROUPS.WORLD;

    renderContext.resourcesToDispose.addMesh(mesh);

    logger.debug('Wireframe block rendered', {
      blockTypeId: block.blockType.name,
      position: { x: worldX, y: worldY, z: worldZ },
      color: colorHex,
    });
  }
}
