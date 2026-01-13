/**
 * WallRenderer - Renders hollow wall blocks
 *
 * Creates hollow cube geometry with outer and inner faces.
 * - Outer faces: Rendered like CubeRenderer (normal textures)
 * - Inner faces: Rendered 0.1 units inward with INSIDE_* textures (reverse winding)
 * - Gap fills: Small faces connecting outer/inner where outer wall is missing
 *   - Gap textures: WALL texture (priority) → INSIDE_* textures (fallback)
 *
 * Total geometry:
 * - 32 corners (8 outer + 24 inner, 4 per inner face)
 * - Up to 12 faces (6 outer + 6 inner)
 * - Up to 24 gap-filling faces (4 per inner face, only where outer is missing)
 */

import { Vector3, Matrix } from '@babylonjs/core';
import { getLogger, FaceFlag, FaceVisibilityHelper, TextureKey } from '@nimbus/shared';
import type { BlockModifier, TextureDefinition, Block } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import type { TextureAtlas } from './TextureAtlas';

const logger = getLogger('WallRenderer');

/**
 * WallRenderer - Renders hollow wall blocks
 */
export class WallRenderer extends BlockRenderer {
  private textureAtlas: TextureAtlas;

  // Wall thickness (distance from outer to inner face)
  private readonly WALL_THICKNESS = 0.1;

  constructor(textureAtlas: TextureAtlas) {
    super();
    this.textureAtlas = textureAtlas;
  }

  /**
   * Normalize texture
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
   * Render a wall block
   */
  async render(
    renderContext: RenderContext,
    block: ClientBlock,
  ): Promise<void> {
    const worldX = block.block.position.x;
    const worldY = block.block.position.y;
    const worldZ = block.block.position.z;

    const modifier = block.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.debug('Wall block has no visibility modifier', { blockTypeId: block.blockType.id });
      return;
    }

    const textures = modifier.visibility.textures;
    if (!textures) {
      logger.warn('Wall block has no textures', { blockTypeId: block.blockType.id });
      return;
    }

    const size = 1;
    const centerX = worldX + size / 2;
    const centerY = worldY + size / 2;
    const centerZ = worldZ + size / 2;

    // === STEP 1: Calculate 32 corners ===

    // Outer corners (0-7): Standard cube corners
    const outerCorners = [
      [worldX, worldY, worldZ], // 0: left-back-bottom
      [worldX + size, worldY, worldZ], // 1: right-back-bottom
      [worldX + size, worldY, worldZ + size], // 2: right-front-bottom
      [worldX, worldY, worldZ + size], // 3: left-front-bottom
      [worldX, worldY + size, worldZ], // 4: left-back-top
      [worldX + size, worldY + size, worldZ], // 5: right-back-top
      [worldX + size, worldY + size, worldZ + size], // 6: right-front-top
      [worldX, worldY + size, worldZ + size], // 7: left-front-top
    ];

    const t = this.WALL_THICKNESS;

    // Inner corners for Top face (8-11): 0.1 below top
    const topInnerY = worldY + size - t;
    const topInnerCorners = [
      [worldX, topInnerY, worldZ], // 8: left-back
      [worldX + size, topInnerY, worldZ], // 9: right-back
      [worldX + size, topInnerY, worldZ + size], // 10: right-front
      [worldX, topInnerY, worldZ + size], // 11: left-front
    ];

    // Inner corners for Bottom face (12-15): 0.1 above bottom
    const bottomInnerY = worldY + t;
    const bottomInnerCorners = [
      [worldX, bottomInnerY, worldZ], // 12: left-back
      [worldX + size, bottomInnerY, worldZ], // 13: right-back
      [worldX + size, bottomInnerY, worldZ + size], // 14: right-front
      [worldX, bottomInnerY, worldZ + size], // 15: left-front
    ];

    // Inner corners for Left face (16-19): 0.1 right of left
    const leftInnerX = worldX + t;
    const leftInnerCorners = [
      [leftInnerX, worldY, worldZ], // 16: back-bottom
      [leftInnerX, worldY, worldZ + size], // 17: front-bottom
      [leftInnerX, worldY + size, worldZ + size], // 18: front-top
      [leftInnerX, worldY + size, worldZ], // 19: back-top
    ];

    // Inner corners for Right face (20-23): 0.1 left of right
    const rightInnerX = worldX + size - t;
    const rightInnerCorners = [
      [rightInnerX, worldY, worldZ + size], // 20: front-bottom
      [rightInnerX, worldY, worldZ], // 21: back-bottom
      [rightInnerX, worldY + size, worldZ], // 22: back-top
      [rightInnerX, worldY + size, worldZ + size], // 23: front-top
    ];

    // Inner corners for Front face (24-27): 0.1 behind front
    const frontInnerZ = worldZ + size - t;
    const frontInnerCorners = [
      [worldX, worldY, frontInnerZ], // 24: left-bottom
      [worldX + size, worldY, frontInnerZ], // 25: right-bottom
      [worldX + size, worldY + size, frontInnerZ], // 26: right-top
      [worldX, worldY + size, frontInnerZ], // 27: left-top
    ];

    // Inner corners for Back face (28-31): 0.1 forward of back
    const backInnerZ = worldZ + t;
    const backInnerCorners = [
      [worldX + size, worldY, backInnerZ], // 28: right-bottom
      [worldX, worldY, backInnerZ], // 29: left-bottom
      [worldX, worldY + size, backInnerZ], // 30: left-top
      [worldX + size, worldY + size, backInnerZ], // 31: right-top
    ];

    // Combine all corners
    const corners = [
      ...outerCorners,
      ...topInnerCorners,
      ...bottomInnerCorners,
      ...leftInnerCorners,
      ...rightInnerCorners,
      ...frontInnerCorners,
      ...backInnerCorners,
    ];

    // === STEP 2: Apply offsets, scaling, rotation ===
    // (Similar to CubeRenderer - apply to all 32 corners)

    const offsets = block.block.offsets;
    if (offsets && offsets.length >= 3) {
      // Apply offsets to outer corners (0-7)
      for (let i = 0; i < 8; i++) {
        if (!offsets[i * 3] && !offsets[i * 3 + 1] && !offsets[i * 3 + 2]) {
            continue; // No offset for this corner
        }
        corners[i][0] += offsets[i * 3] ?? 0;
        corners[i][1] += offsets[i * 3 + 1] ?? 0;
        corners[i][2] += offsets[i * 3 + 2] ?? 0;
      }
      // Inner corners inherit offsets proportionally (simplified: same as outer)
      // TODO: More sophisticated offset interpolation if needed
    }

    const scalingX = modifier.visibility?.scalingX ?? 1.0;
    const scalingY = modifier.visibility?.scalingY ?? 1.0;
    const scalingZ = modifier.visibility?.scalingZ ?? 1.0;

    if (scalingX !== 1.0 || scalingY !== 1.0 || scalingZ !== 1.0) {
      for (let i = 0; i < corners.length; i++) {
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

      for (let i = 0; i < corners.length; i++) {
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

    // === STEP 3: Determine texture indices ===

    // Outer face textures (same as CubeRenderer)
    const topIndex = textures[TextureKey.TOP] ? TextureKey.TOP : (textures[TextureKey.ALL] ? TextureKey.ALL : 0);
    const bottomIndex = textures[TextureKey.BOTTOM] ? TextureKey.BOTTOM : (textures[TextureKey.ALL] ? TextureKey.ALL : 0);
    const leftIndex = textures[TextureKey.LEFT] ? TextureKey.LEFT : (textures[TextureKey.SIDE] ? TextureKey.SIDE : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));
    const rightIndex = textures[TextureKey.RIGHT] ? TextureKey.RIGHT : (textures[TextureKey.SIDE] ? TextureKey.SIDE : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));
    const frontIndex = textures[TextureKey.FRONT] ? TextureKey.FRONT : (textures[TextureKey.SIDE] ? TextureKey.SIDE : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));
    const backIndex = textures[TextureKey.BACK] ? TextureKey.BACK : (textures[TextureKey.SIDE] ? TextureKey.SIDE : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));

    // Inner face textures (with fallback chain: INSIDE_* → INSIDE_ALL → ALL)
    const insideTopIndex = textures[TextureKey.INSIDE_TOP] ? TextureKey.INSIDE_TOP : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));
    const insideBottomIndex = textures[TextureKey.INSIDE_BOTTOM] ? TextureKey.INSIDE_BOTTOM : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0));
    const insideLeftIndex = textures[TextureKey.INSIDE_LEFT] ? TextureKey.INSIDE_LEFT : (textures[TextureKey.INSIDE_SIDE] ? TextureKey.INSIDE_SIDE : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0)));
    const insideRightIndex = textures[TextureKey.INSIDE_RIGHT] ? TextureKey.INSIDE_RIGHT : (textures[TextureKey.INSIDE_SIDE] ? TextureKey.INSIDE_SIDE : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0)));
    const insideFrontIndex = textures[TextureKey.INSIDE_FRONT] ? TextureKey.INSIDE_FRONT : (textures[TextureKey.INSIDE_SIDE] ? TextureKey.INSIDE_SIDE : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0)));
    const insideBackIndex = textures[TextureKey.INSIDE_BACK] ? TextureKey.INSIDE_BACK : (textures[TextureKey.INSIDE_SIDE] ? TextureKey.INSIDE_SIDE : (textures[TextureKey.INSIDE_ALL] ? TextureKey.INSIDE_ALL : (textures[TextureKey.ALL] ? TextureKey.ALL : 0)));

    // Gap texture (with fallback chain: WALL → INSIDE_* → INSIDE_ALL → ALL)
    const getGapTextureIndex = (insideIndex: number): number => {
      return textures[TextureKey.WALL] ? TextureKey.WALL : insideIndex;
    };

    // === STEP 4: Determine face visibility ===

    // Outer face visibility: From block (like CubeRenderer)
    const isOuterTopVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.TOP);
    const isOuterBottomVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BOTTOM);
    const isOuterLeftVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.LEFT);
    const isOuterRightVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.RIGHT);
    const isOuterFrontVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.FRONT);
    const isOuterBackVisible = FaceVisibilityHelper.isVisible(block, FaceFlag.BACK);

    // Inner face visibility: Only from modifier.visibility.faceVisibility (NOT from block)
    const modifierFaceVisibility = modifier.visibility?.faceVisibility ?? 63; // Default: all visible
    const isInnerTopVisible = (modifierFaceVisibility & FaceFlag.TOP) !== 0;
    const isInnerBottomVisible = (modifierFaceVisibility & FaceFlag.BOTTOM) !== 0;
    const isInnerLeftVisible = (modifierFaceVisibility & FaceFlag.LEFT) !== 0;
    const isInnerRightVisible = (modifierFaceVisibility & FaceFlag.RIGHT) !== 0;
    const isInnerFrontVisible = (modifierFaceVisibility & FaceFlag.FRONT) !== 0;
    const isInnerBackVisible = (modifierFaceVisibility & FaceFlag.BACK) !== 0;

    // === STEP 5: Render outer faces ===

    if (isOuterTopVisible) {
      const texture = textures[topIndex] ? this.normalizeTexture(textures[topIndex]) : null;
      await this.addFace(
        corners[4], corners[5], corners[6], corners[7],
        [0, 1, 0],
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    if (isOuterBottomVisible) {
      const texture = textures[bottomIndex] ? this.normalizeTexture(textures[bottomIndex]) : null;
      await this.addFace(
        corners[0], corners[3], corners[2], corners[1],
        [0, -1, 0],
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    if (isOuterLeftVisible) {
      const texture = textures[leftIndex] ? this.normalizeTexture(textures[leftIndex]) : null;
      await this.addFace(
        corners[0], corners[3], corners[7], corners[4],
        [-1, 0, 0],
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    if (isOuterRightVisible) {
      const texture = textures[rightIndex] ? this.normalizeTexture(textures[rightIndex]) : null;
      await this.addFace(
        corners[2], corners[1], corners[5], corners[6],
        [1, 0, 0],
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    if (isOuterFrontVisible) {
      const texture = textures[frontIndex] ? this.normalizeTexture(textures[frontIndex]) : null;
      await this.addFace(
        corners[3], corners[2], corners[6], corners[7],
        [0, 0, 1],
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    if (isOuterBackVisible) {
      const texture = textures[backIndex] ? this.normalizeTexture(textures[backIndex]) : null;
      await this.addFace(
        corners[1], corners[0], corners[4], corners[5],
        [0, 0, -1],
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    // === STEP 6: Render inner faces (reverse winding) ===

    if (isInnerTopVisible) {
      const texture = textures[insideTopIndex] ? this.normalizeTexture(textures[insideTopIndex]) : null;
      // Inner top face (corners 8-11), reverse winding (visible from below)
      await this.addFace(
        corners[8], corners[11], corners[10], corners[9],
        [0, -1, 0], // Normal pointing down (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    if (isInnerBottomVisible) {
      const texture = textures[insideBottomIndex] ? this.normalizeTexture(textures[insideBottomIndex]) : null;
      // Inner bottom face (corners 12-15), reverse winding (visible from above)
      await this.addFace(
        corners[12], corners[13], corners[14], corners[15],
        [0, 1, 0], // Normal pointing up (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    if (isInnerLeftVisible) {
      const texture = textures[insideLeftIndex] ? this.normalizeTexture(textures[insideLeftIndex]) : null;
      // Inner left face (corners 16-19), reverse winding (visible from right)
      await this.addFace(
        corners[16], corners[19], corners[18], corners[17],
        [1, 0, 0], // Normal pointing right (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    if (isInnerRightVisible) {
      const texture = textures[insideRightIndex] ? this.normalizeTexture(textures[insideRightIndex]) : null;
      // Inner right face (corners 20-23), reverse winding (visible from left)
      await this.addFace(
        corners[20], corners[21], corners[22], corners[23],
        [-1, 0, 0], // Normal pointing left (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    if (isInnerFrontVisible) {
      const texture = textures[insideFrontIndex] ? this.normalizeTexture(textures[insideFrontIndex]) : null;
      // Inner front face (corners 24-27), reverse winding (visible from back)
      await this.addFace(
        corners[24], corners[27], corners[26], corners[25],
        [0, 0, -1], // Normal pointing back (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        true
      );
    }

    if (isInnerBackVisible) {
      const texture = textures[insideBackIndex] ? this.normalizeTexture(textures[insideBackIndex]) : null;
      // Inner back face (corners 28-31), reverse winding (visible from front)
      await this.addFace(
        corners[28], corners[29], corners[30], corners[31],
        [0, 0, 1], // Normal pointing front (inside)
        texture,
        modifier,
        block.block,
        renderContext,
        false
      );
    }

    // === STEP 7: Render gap-filling faces ===
    // Fill gaps where outer wall is missing (connect inner to outer edge)

    // Top inner wall gaps (4 edges: left, right, front, back)
    if (isInnerTopVisible) {
      const gapTextureIndex = getGapTextureIndex(insideTopIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      // Left edge gap (if left outer wall missing)
      if (!isOuterLeftVisible) {
        await this.addFace(
          corners[4], corners[8], corners[11], corners[7], // outer-left-back, inner-left-back, inner-left-front, outer-left-front
          [-1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      // Right edge gap (if right outer wall missing)
      if (!isOuterRightVisible) {
        await this.addFace(
          corners[6], corners[10], corners[9], corners[5], // outer-right-front, inner-right-front, inner-right-back, outer-right-back
          [1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      // Front edge gap (if front outer wall missing)
      if (!isOuterFrontVisible) {
        await this.addFace(
          corners[7], corners[11], corners[10], corners[6], // outer-left-front, inner-left-front, inner-right-front, outer-right-front
          [0, 0, 1],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      // Back edge gap (if back outer wall missing)
      if (!isOuterBackVisible) {
        await this.addFace(
          corners[5], corners[9], corners[8], corners[4], // outer-right-back, inner-right-back, inner-left-back, outer-left-back
          [0, 0, -1],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }
    }

    // Bottom inner wall gaps (4 edges: left, right, front, back)
    if (isInnerBottomVisible) {
      const gapTextureIndex = getGapTextureIndex(insideBottomIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      if (!isOuterLeftVisible) {
        await this.addFace(
          corners[0], corners[12], corners[15], corners[3],
          [-1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterRightVisible) {
        await this.addFace(
          corners[2], corners[14], corners[13], corners[1],
          [1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterFrontVisible) {
        await this.addFace(
          corners[3], corners[15], corners[14], corners[2],
          [0, 0, 1],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterBackVisible) {
        await this.addFace(
          corners[1], corners[13], corners[12], corners[0],
          [0, 0, -1],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }
    }

    // Left inner wall gaps (4 edges: top, bottom, front, back)
    if (isInnerLeftVisible) {
      const gapTextureIndex = getGapTextureIndex(insideLeftIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      if (!isOuterTopVisible) {
        await this.addFace(
          corners[4], corners[7], corners[18], corners[19],
          [0, 1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterBottomVisible) {
        await this.addFace(
          corners[0], corners[16], corners[17], corners[3],
          [0, -1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterFrontVisible) {
        await this.addFace(
          corners[3], corners[17], corners[18], corners[7],
          [0, 0, 1],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterBackVisible) {
        await this.addFace(
          corners[4], corners[19], corners[16], corners[0],
          [0, 0, -1],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }
    }

    // Right inner wall gaps (4 edges: top, bottom, front, back)
    if (isInnerRightVisible) {
      const gapTextureIndex = getGapTextureIndex(insideRightIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      if (!isOuterTopVisible) {
        await this.addFace(
          corners[6], corners[23], corners[22], corners[5],
          [0, 1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterBottomVisible) {
        await this.addFace(
          corners[2], corners[1], corners[21], corners[20],
          [0, -1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterFrontVisible) {
        await this.addFace(
          corners[6], corners[2], corners[20], corners[23],
          [0, 0, 1],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }

      if (!isOuterBackVisible) {
        await this.addFace(
          corners[5], corners[22], corners[21], corners[1],
          [0, 0, -1],
          texture,
          modifier,
        block.block,
        renderContext,
          false
        );
      }
    }

    // Front inner wall gaps (4 edges: top, bottom, left, right)
    if (isInnerFrontVisible) {
      const gapTextureIndex = getGapTextureIndex(insideFrontIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      if (!isOuterTopVisible) {
        await this.addFace(
          corners[7], corners[6], corners[26], corners[27],
          [0, 1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterBottomVisible) {
        await this.addFace(
          corners[3], corners[24], corners[25], corners[2],
          [0, -1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterLeftVisible) {
        await this.addFace(
          corners[3], corners[7], corners[27], corners[24],
          [-1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterRightVisible) {
        await this.addFace(
          corners[2], corners[25], corners[26], corners[6],
          [1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }
    }

    // Back inner wall gaps (4 edges: top, bottom, left, right)
    if (isInnerBackVisible) {
      const gapTextureIndex = getGapTextureIndex(insideBackIndex);
      const texture = textures[gapTextureIndex] ? this.normalizeTexture(textures[gapTextureIndex]) : null;

      if (!isOuterTopVisible) {
        await this.addFace(
          corners[5], corners[4], corners[30], corners[31],
          [0, 1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterBottomVisible) {
        await this.addFace(
          corners[1], corners[28], corners[29], corners[0],
          [0, -1, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterLeftVisible) {
        await this.addFace(
          corners[0], corners[29], corners[30], corners[4],
          [-1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }

      if (!isOuterRightVisible) {
        await this.addFace(
          corners[1], corners[5], corners[31], corners[28],
          [1, 0, 0],
          texture,
          modifier,
        block.block,
        renderContext,
          true
        );
      }
    }

    logger.debug('Wall rendered', {
      blockTypeId: block.blockType.id,
      position: { x: worldX, y: worldY, z: worldZ },
    });
  }

  /**
   * Add a face to the mesh data
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

    faceData.positions.push(
      corner0[0], corner0[1], corner0[2],
      corner1[0], corner1[1], corner1[2],
      corner2[0], corner2[1], corner2[2],
      corner3[0], corner3[1], corner3[2]
    );

    for (let i = 0; i < 4; i++) {
      faceData.normals.push(normal[0], normal[1], normal[2]);
    }

    if (texture && this.textureAtlas) {
      const atlasUV = await this.textureAtlas.getTextureUV(texture);
      if (atlasUV) {
        const isHorizontalFace = normal[1] !== 0;

        if (isHorizontalFace) {
          faceData.uvs.push(
            atlasUV.u0, atlasUV.v0,
            atlasUV.u1, atlasUV.v0,
            atlasUV.u1, atlasUV.v1,
            atlasUV.u0, atlasUV.v1
          );
        } else {
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

    const i0 = renderContext.vertexOffset;
    const i1 = renderContext.vertexOffset + 1;
    const i2 = renderContext.vertexOffset + 2;
    const i3 = renderContext.vertexOffset + 3;

    if (reverseWinding) {
      faceData.indices.push(i0, i2, i1);
      faceData.indices.push(i0, i3, i2);
    } else {
      faceData.indices.push(i0, i1, i2);
      faceData.indices.push(i0, i2, i3);
    }

    this.addWindAttributesAndColors(faceData, modifier, block, 4);

    renderContext.vertexOffset += 4;
  }
}
