/**
 * FlameRenderer - Renders animated flame effects
 *
 * Creates camera-facing vertical planes with FireMaterial for animated flames.
 * Each flame gets its own separate mesh with original texture.
 *
 * Features:
 * - Camera-facing billboard (Y-axis rotation only)
 * - Aspect ratio from texture dimensions (1 width : ratio height)
 * - Pivot point at block center (offset shifts this point)
 * - Supports scaling, color tint
 * - Uses Babylon.js FireMaterial for animation
 * - Flickering and wind movement via wind parameters
 */

import { Mesh, MeshBuilder, StandardMaterial, Color3, Texture } from '@babylonjs/core';
import { FireMaterial } from '@babylonjs/materials';
import { getLogger, TextureHelper, Shape } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('FlameRenderer');

/**
 * FlameRenderer - Renders animated flame blocks
 *
 * Creates camera-facing flames using Babylon.js FireMaterial.
 * Default size: 1 unit wide, height determined by texture aspect ratio.
 */
export class FlameRenderer extends BlockRenderer {
  /**
   * FlameRenderer needs separate mesh per block
   * (camera-facing, animated material)
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render a FLAME block
   *
   * Creates a vertical billboard plane with FireMaterial animation.
   * Size: 1 unit wide by default, height = width * texture aspect ratio.
   *
   * @param renderContext Render context
   * @param clientBlock Block to render
   */
  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('FlameRenderer: No visibility modifier', { block });
      return;
    }

    // Validate shape
    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.FLAME) {
      logger.warn('FlameRenderer: Not a FLAME shape', { shape, block });
      return;
    }

    // Get textures (can have multiple: diffuse, distortion, opacity)
    const textures = modifier.visibility.textures;
    if (!textures || Object.keys(textures).length === 0) {
      logger.warn('FlameRenderer: No textures defined', { block });
      return;
    }

    // Get first texture for size calculation
    const firstTexture = textures[0] || textures[1];
    const textureDef = TextureHelper.normalizeTexture(firstTexture);

    // Get transformations
    const scalingX = modifier.visibility.scalingX ?? 1.0;
    const scalingY = modifier.visibility.scalingY ?? 1.0;
    const scalingZ = modifier.visibility.scalingZ ?? 1.0;

    // Get pivot offset (offset shifts the flame center)
    let offsetX = 0;
    let offsetY = 0;
    let offsetZ = 0;

    if (block.offsets && block.offsets.length >= 3) {
      offsetX = block.offsets[0] || 0;
      offsetY = block.offsets[1] || 0;
      offsetZ = block.offsets[2] || 0;
    }

    // Get flame properties from wind modifier (used for flame animation)
    const flameStrength = modifier.wind?.stability ?? 1.0;
    const flicker = modifier.wind?.leafiness ?? 0.5;
    const leverUp = modifier.wind?.leverUp ?? 0.3;
    const leverDown = modifier.wind?.leverDown ?? 0.1;

    // Get color tint (default: orange)
    let colorTint = new Color3(1.0, 0.78, 0.39); // Orange
    if (textureDef.color) {
      // Parse color from hex string (e.g., "#FF6600" or "rgb(255,102,0)")
      colorTint = this.parseColor(textureDef.color);
    }

    // Block position
    const pos = block.position;

    // Calculate flame center (block center + offset)
    // IMPORTANT: Center must be above block (Y + 0.5) so flame stands ON the block
    const centerX = pos.x + 0.5 + offsetX;
    const centerY = pos.y + 0.5 + offsetY;
    const centerZ = pos.z + 0.5 + offsetZ;

    // Get texture aspect ratio
    const aspectRatio = await this.getTextureAspectRatio(
      textureDef.path,
      renderContext.renderService
    );

    // Calculate flame dimensions
    const width = scalingX;
    const height = (scalingY / aspectRatio);

    // Create flame mesh
    await this.createFlameMesh(
      clientBlock,
      centerX,
      centerY,
      centerZ,
      width,
      height,
      colorTint,
      flameStrength,
      flicker,
      textures,
      renderContext
    );
  }

  /**
   * Get texture aspect ratio (width / height)
   *
   * @param texturePath Path to texture
   * @param renderService RenderService for material access
   * @returns Aspect ratio (width / height)
   */
  private async getTextureAspectRatio(
    texturePath: string,
    renderService: any
  ): Promise<number> {
    try {
      const texture = (await renderService.materialService.loadTexture(texturePath)) as Texture;

      if (!texture) {
        logger.warn('Could not load texture for aspect ratio', { texturePath });
        return 1.0;
      }

      const size = texture.getSize();
      const aspectRatio = size.width / size.height;

      return aspectRatio;
    } catch (error) {
      logger.warn('Failed to get texture aspect ratio, using 1.0', { texturePath, error });
      return 1.0;
    }
  }

  /**
   * Parse color from string
   *
   * Supports: "#FF6600", "rgb(255,102,0)", etc.
   * Fallback: Orange
   */
  private parseColor(colorString: string): Color3 {
    try {
      // Remove whitespace
      colorString = colorString.trim();

      // Hex format: "#FF6600"
      if (colorString.startsWith('#')) {
        const hex = colorString.substring(1);
        const r = parseInt(hex.substring(0, 2), 16) / 255;
        const g = parseInt(hex.substring(2, 4), 16) / 255;
        const b = parseInt(hex.substring(4, 6), 16) / 255;
        return new Color3(r, g, b);
      }

      // RGB format: "rgb(255,102,0)"
      if (colorString.startsWith('rgb')) {
        const match = colorString.match(/\d+/g);
        if (match && match.length >= 3) {
          const r = parseInt(match[0]) / 255;
          const g = parseInt(match[1]) / 255;
          const b = parseInt(match[2]) / 255;
          return new Color3(r, g, b);
        }
      }

      logger.warn('Could not parse color, using default orange', { colorString });
      return new Color3(1.0, 0.78, 0.39);
    } catch (error) {
      logger.warn('Error parsing color, using default orange', { colorString, error });
      return new Color3(1.0, 0.78, 0.39);
    }
  }

  /**
   * Create flame mesh with FireMaterial
   *
   * @param clientBlock Block to create flame for
   * @param centerX Center X position
   * @param centerY Center Y position
   * @param centerZ Center Z position
   * @param width Flame width
   * @param height Flame height
   * @param colorTint Color tint for flame
   * @param flameStrength Flame animation strength
   * @param flicker Flicker intensity
   * @param textures Texture definitions
   * @param renderContext Render context with resourcesToDispose
   */
  private async createFlameMesh(
    clientBlock: ClientBlock,
    centerX: number,
    centerY: number,
    centerZ: number,
    width: number,
    height: number,
    colorTint: Color3,
    flameStrength: number,
    flicker: number,
    textures: Record<number, any>,
    renderContext: RenderContext
  ): Promise<void> {
    const block = clientBlock.block;
    const scene = renderContext.renderService.materialService.scene;

    // Create mesh name
    const meshName = `flame_${block.position.x}_${block.position.y}_${block.position.z}`;

    // Create vertical plane mesh
    const plane = MeshBuilder.CreatePlane(
      meshName,
      {
        width,
        height,
        sideOrientation: Mesh.DOUBLESIDE, // Visible from both sides
      },
      scene
    );

    // Position at center
    plane.position.set(centerX, centerY, centerZ);

    // Enable billboard mode - rotate around Y axis to face camera
    plane.billboardMode = Mesh.BILLBOARDMODE_Y;

    // Make flame not pickable (optimization)
    plane.isPickable = false;

    // Set rendering group for world content
    plane.renderingGroupId = RENDERING_GROUPS.WORLD;

    // Get texture paths (up to 3: diffuse, distortion, opacity)
    const texturePaths = this.getTexturePaths(textures);

    // Create material
    let material: FireMaterial | StandardMaterial;

    if (texturePaths.length > 0) {
      // Use FireMaterial with textures
      const fireMaterial = new FireMaterial(meshName + '_mat', scene);

      // Load and set diffuse texture (main flame texture)
      if (texturePaths[0]) {
        const diffuseTexture = (await renderContext.renderService.materialService.loadTexture(
          texturePaths[0]
        )) as Texture;
        if (diffuseTexture) {
          fireMaterial.diffuseTexture = diffuseTexture;
        }
      }

      // Load and set distortion texture if provided
      if (texturePaths[1]) {
        const distortionTexture = (await renderContext.renderService.materialService.loadTexture(
          texturePaths[1]
        )) as Texture;
        if (distortionTexture) {
          fireMaterial.distortionTexture = distortionTexture;
        }
      }

      // Load and set opacity texture if provided
      if (texturePaths[2]) {
        const opacityTexture = (await renderContext.renderService.materialService.loadTexture(
          texturePaths[2]
        )) as Texture;
        if (opacityTexture) {
          fireMaterial.opacityTexture = opacityTexture;
        }
      }

      // Apply color tint (FireMaterial uses diffuseColor)
      fireMaterial.diffuseColor = colorTint;

      // Flame animation speed
      fireMaterial.speed = flameStrength * 2.0;

      material = fireMaterial;
    } else {
      // Fallback to simple emissive material
      const standardMaterial = new StandardMaterial(meshName + '_mat', scene);

      standardMaterial.emissiveColor = colorTint;
      standardMaterial.alpha = 0.8;
      standardMaterial.useAlphaFromDiffuseTexture = false;

      material = standardMaterial;
    }

    // Common material properties
    material.backFaceCulling = false;

    // Store flame parameters in metadata
    material.metadata = {
      flameStrength,
      flicker,
      startTime: Date.now(),
    };

    plane.material = material;

    // Register mesh for illumination glow if block has illumination modifier
    const illuminationService = renderContext.renderService.appContext.services.illumination;
    if (illuminationService && clientBlock.currentModifier.illumination?.color) {
      illuminationService.registerMesh(
        plane,
        clientBlock.currentModifier.illumination.color,
        clientBlock.currentModifier.illumination.strength ?? 1.0
      );
    }

    // Register mesh for automatic disposal when chunk is unloaded
    renderContext.resourcesToDispose.addMesh(plane);

    logger.debug('FLAME mesh created', {
      meshName,
      position: `${centerX},${centerY},${centerZ}`,
      size: `${width}x${height}`,
      textures: texturePaths.length,
      material: material instanceof FireMaterial ? 'FireMaterial' : 'StandardMaterial',
    });
  }

  /**
   * Get texture paths from textures record
   *
   * Extracts up to 3 texture paths:
   * - Index 0 or 1: Diffuse texture (main flame)
   * - Index 9: Distortion texture
   * - Index 10: Opacity texture
   *
   * @param textures Texture definitions
   * @returns Array of texture paths [diffuse, distortion?, opacity?]
   */
  private getTexturePaths(textures: Record<number, any>): string[] {
    const paths: string[] = [];

    // Get diffuse texture (TextureKey.ALL = 0 or TextureKey.TOP = 1)
    const diffuse = textures[0] || textures[1];
    if (diffuse) {
      const diffuseDef = TextureHelper.normalizeTexture(diffuse);
      paths.push(diffuseDef.path);
    }

    // Get distortion texture (TextureKey.DISTORTION = 9)
    const distortion = textures[9];
    if (distortion) {
      const distortionDef = TextureHelper.normalizeTexture(distortion);
      paths.push(distortionDef.path);
    }

    // Get opacity texture (TextureKey.OPACITY = 10)
    const opacity = textures[10];
    if (opacity) {
      const opacityDef = TextureHelper.normalizeTexture(opacity);
      paths.push(opacityDef.path);
    }

    return paths;
  }
}
