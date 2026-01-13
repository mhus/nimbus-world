/**
 * OceanRenderer - Renders ocean water surfaces
 *
 * Creates flat water surfaces on TOP of blocks using Babylon.js WaterMaterial.
 * Each ocean block gets its own separate mesh with animated water effects.
 *
 * Features:
 * - Flat horizontal water surface at block top
 * - Babylon.js WaterMaterial with waves and reflections
 * - Configurable via wind parameters (windForce, waveHeight, etc.)
 * - Supports offset, scaling transformations
 */

import { Mesh, MeshBuilder, Texture, Vector2, Color3 } from '@babylonjs/core';
import { WaterMaterial } from '@babylonjs/materials';
import { getLogger, TextureHelper, Shape } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('OceanRenderer');

/**
 * OceanRenderer - Renders flat ocean water surfaces
 *
 * Creates horizontal planes with WaterMaterial for realistic water animation.
 * Default size: 1x1 unit, positioned at TOP of block.
 */
export class OceanRenderer extends BlockRenderer {
  /**
   * OceanRenderer needs separate mesh per block
   * (WaterMaterial needs separate mesh for reflections/refractions)
   */
  needsSeparateMesh(): boolean {
    return true;
  }

  /**
   * Render an OCEAN block
   *
   * Creates a flat horizontal water surface at the top of the block.
   * Uses WaterMaterial for animated waves.
   *
   * @param renderContext Render context
   * @param clientBlock Block to render
   */
  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('OceanRenderer: No visibility modifier', { block });
      return;
    }

    // Validate shape
    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.OCEAN) {
      logger.warn('OceanRenderer: Not an OCEAN shape', { shape, block });
      return;
    }

    // Get textures
    const textures = modifier.visibility.textures;
    if (!textures || Object.keys(textures).length === 0) {
      logger.warn('OceanRenderer: No textures defined (need bump texture)', { block });
      return;
    }

    // Get bump texture (required for WaterMaterial)
    // Can be defined as TextureKey.ALL (0) or TextureKey.TOP (1)
    const bumpTexture = textures[0] || textures[1];
    if (!bumpTexture) {
      logger.warn('OceanRenderer: No bump texture defined', { block });
      return;
    }

    const bumpTextureDef = TextureHelper.normalizeTexture(bumpTexture);

    // Get water color from texture color field (default: blue)
    let waterColor = new Color3(0.1, 0.4, 0.8); // Blue
    if (bumpTextureDef.color) {
      waterColor = this.parseColor(bumpTextureDef.color);
    }

    // Block position
    const pos = block.position;

    // Get chunk coordinates from clientBlock
    const chunkX = clientBlock.chunk.cx;
    const chunkZ = clientBlock.chunk.cz;

    // Water surface Y position (TOP of block)
    const waterY = pos.y + 0.5;

    // Create ocean mesh
    await this.createOceanMesh(
      clientBlock,
      waterY,
      bumpTextureDef.path,
      waterColor,
      renderContext
    );
  }

  /**
   * Parse color from string
   *
   * Supports: "#FF6600", "rgb(255,102,0)", etc.
   * Fallback: Blue
   */
  private parseColor(colorString: string): Color3 {
    try {
      colorString = colorString.trim();

      // Hex format: "#0066CC"
      if (colorString.startsWith('#')) {
        const hex = colorString.substring(1);
        const r = parseInt(hex.substring(0, 2), 16) / 255;
        const g = parseInt(hex.substring(2, 4), 16) / 255;
        const b = parseInt(hex.substring(4, 6), 16) / 255;
        return new Color3(r, g, b);
      }

      // RGB format: "rgb(10,102,204)"
      if (colorString.startsWith('rgb')) {
        const match = colorString.match(/\d+/g);
        if (match && match.length >= 3) {
          const r = parseInt(match[0]) / 255;
          const g = parseInt(match[1]) / 255;
          const b = parseInt(match[2]) / 255;
          return new Color3(r, g, b);
        }
      }

      logger.warn('Could not parse color, using default blue', { colorString });
      return new Color3(0.1, 0.4, 0.8);
    } catch (error) {
      logger.warn('Error parsing color, using default blue', { colorString, error });
      return new Color3(0.1, 0.4, 0.8);
    }
  }

  /**
   * Create ocean mesh with WaterMaterial
   *
   * Uses shared mesh per Y-level within chunk for efficiency.
   * Multiple ocean blocks at same Y-level share one large water surface.
   * The mesh is automatically registered for disposal when chunk is unloaded.
   *
   * @param clientBlock Block to create ocean for
   * @param waterY Water surface Y position
   * @param bumpTexturePath Bump texture for water waves
   * @param waterColor Water color tint
   * @param renderContext Render context with resourcesToDispose
   */
  private async createOceanMesh(
    clientBlock: ClientBlock,
    waterY: number,
    bumpTexturePath: string,
    waterColor: Color3,
    renderContext: RenderContext
  ): Promise<void> {
    const block = clientBlock.block;
    const scene = renderContext.renderService.materialService.scene;

    // Get chunk size from world info
    const worldInfo = renderContext.renderService.appContext.worldInfo;
    const chunkSize = worldInfo?.chunkSize ?? 32; // fallback to 32 if worldInfo not available

    // Get chunk coordinates from clientBlock
    const chunkX = clientBlock.chunk.cx;
    const chunkZ = clientBlock.chunk.cz;

    // Create shared mesh name per Y-level WITHIN THIS CHUNK (all ocean blocks at same Y in same chunk share one mesh)
    const sharedMeshName = `ocean_${chunkX}_${chunkZ}_y${block.position.y}`;

    // Get or create shared ocean mesh for this Y-level
    const plane = renderContext.resourcesToDispose.getOrCreateMesh(
      sharedMeshName,
      () => {
        logger.debug('Creating shared ocean mesh', { name: sharedMeshName, chunkSize });

        // Calculate chunk center position in world coordinates
        const chunkCenterX = chunkX * chunkSize + chunkSize / 2;
        const chunkCenterZ = chunkZ * chunkSize + chunkSize / 2;

        // Create large flat ground with overlap for seamless chunk transitions
        // Slightly larger than chunk size with random offset = slight overlap that hides seams
        const randomOffsetX = (Math.random() - 0.5) * 0.3; // Random -0.15 to +0.15
        const randomOffsetZ = (Math.random() - 0.5) * 0.3;
        const randomSize = chunkSize + 0.5 + Math.random() * 1.0; // chunkSize + random 0.5 to 1.5

        const mesh = MeshBuilder.CreateGround(
          sharedMeshName,
          {
            width: randomSize, // Slightly larger with variation
            height: randomSize,
            subdivisions: 32, // More subdivisions = smoother waves
          },
          scene
        );

        // Position at chunk center with random offset (creates frayed/organic edges)
        mesh.position.set(
          chunkCenterX + randomOffsetX,
          waterY,
          chunkCenterZ + randomOffsetZ
        );

        // Set rendering group for world content
        mesh.renderingGroupId = RENDERING_GROUPS.WORLD;

        return mesh;
      }
    );

    // Setup WaterMaterial only once (when mesh is first created)
    if (!plane.material) {
      await this.setupWaterMaterial(
        plane,
        sharedMeshName,
        bumpTexturePath,
        waterColor,
        renderContext
      );
    }

    renderContext.resourcesToDispose.add(plane);

    logger.debug('Ocean block added to shared mesh', {
      sharedMeshName,
      position: `${block.position.x},${block.position.y},${block.position.z}`,
    });
  }

  /**
   * Setup WaterMaterial for ocean mesh
   *
   * Called only once when mesh is first created.
   * Configures WaterMaterial with textures, properties, and render list.
   *
   * @param mesh Ocean mesh
   * @param meshName Mesh name
   * @param bumpTexturePath Bump texture path
   * @param waterColor Water color
   * @param renderContext Render context
   */
  private async setupWaterMaterial(
    mesh: Mesh,
    meshName: string,
    bumpTexturePath: string,
    waterColor: Color3,
    renderContext: RenderContext
  ): Promise<void> {
    const scene = renderContext.renderService.materialService.scene;

    // Create WaterMaterial with render target size
    const waterMaterial = new WaterMaterial(meshName + '_mat', scene, new Vector2(512, 512));

    // Load bump texture (REQUIRED for WaterMaterial)
    const bumpTexture = (await renderContext.renderService.materialService.loadTexture(
      bumpTexturePath
    )) as Texture;

    if (bumpTexture) {
      waterMaterial.bumpTexture = bumpTexture;
    } else {
      logger.warn('Failed to load bump texture for ocean', { bumpTexturePath });
    }

    // Configure water properties (adjusted for voxel scale)
    waterMaterial.windForce = -5; // Reduced for calmer waves
    waterMaterial.waveHeight = 0.05; // Much smaller for realistic voxel-scale waves
    waterMaterial.bumpHeight = 0.02; // Reduced bump intensity
    waterMaterial.waveLength = 0.15; // Slightly longer waves
    waterMaterial.waterColor = waterColor;
    waterMaterial.colorBlendFactor = 0.3;
    waterMaterial.windDirection = new Vector2(1, 1);

    // Disable backface culling for water
    waterMaterial.backFaceCulling = false;

    // Add all chunk meshes to render list for reflections/refractions
    const chunkMeshes = renderContext.renderService.getChunkMeshes();
    for (const chunkMesh of chunkMeshes.values()) {
      waterMaterial.addToRenderList(chunkMesh);
    }

    // TODO: Add skybox to render list when available
    // if (scene.skybox) {
    //   waterMaterial.addToRenderList(scene.skybox);
    // }

    // Apply material to mesh
    mesh.material = waterMaterial;

    // Make water not pickable (optimization)
    mesh.isPickable = false;

    logger.debug('WaterMaterial setup complete', {
      meshName,
      waterColor,
      chunkMeshesInRenderList: chunkMeshes.size,
    });
  }
}
