/**
 * RenderService - Renders chunks as meshes
 *
 * Manages chunk rendering, mesh generation, and cleanup.
 * Listens to ChunkService events to render/unload chunks.
 */

import { Mesh, VertexData, Scene, VertexBuffer } from '@babylonjs/core';
import { getLogger, ExceptionHandler, Shape, BlockEffect, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ClientChunk } from '../types/ClientChunk';
import type { ClientBlock } from '../types/ClientBlock';
import type { MaterialService } from './MaterialService';
import type { BlockTypeService } from './BlockTypeService';
import { BlockRenderer } from '../rendering/BlockRenderer';
import { CubeRenderer } from '../rendering/CubeRenderer';
import { CrossRenderer } from '../rendering/CrossRenderer';
import { HashRenderer } from '../rendering/HashRenderer';
import { GlassRenderer } from '../rendering/GlassRenderer';
import { SphereRenderer } from '../rendering/SphereRenderer';
import { CylinderRenderer } from '../rendering/CylinderRenderer';
import { StairRenderer } from '../rendering/StairRenderer';
import { StepsRenderer } from '../rendering/StepsRenderer';
import { FlipboxRenderer } from '../rendering/FlipboxRenderer';
import { FogRenderer } from '../rendering/FogRenderer';
import { WallRenderer } from '../rendering/WallRenderer';
import { ModelRenderer } from '../rendering/ModelRenderer';
import { BillboardRenderer } from '../rendering/BillboardRenderer';
import { ItemRenderer } from '../rendering/ItemRenderer';
import { SpriteRenderer } from '../rendering/SpriteRenderer';
import { ThinInstancesRenderer } from '../rendering/ThinInstancesRenderer';
import { FlameRenderer } from '../rendering/FlameRenderer';
import { OceanRenderer } from '../rendering/OceanRenderer';
import { BushRenderer } from '../rendering/BushRenderer';
import { WaterRenderer } from '../rendering/WaterRenderer';
import { DisposableResources } from '../rendering/DisposableResources';
import type { TextureAtlas } from '../rendering/TextureAtlas';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import type { PrecipitationService } from './PrecipitationService';

const logger = getLogger('RenderService');

/**
 * Face data for mesh building
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
 * Describes a single face to be rendered
 */
interface FaceDescriptor {
  clientBlock: ClientBlock;
  textureKey: number;  // 0=ALL, 1=TOP, 2=BOTTOM, 3=LEFT, 4=RIGHT, 5=FRONT, 6=BACK, 7=SIDE
  isVisible: boolean;
}

/**
 * Render context passed to block renderers
 */
export interface RenderContext {
  renderService: RenderService;
  faceData: FaceData;
  vertexOffset: number;
  resourcesToDispose: DisposableResources;
}

/**
 * RenderService - Manages chunk rendering
 *
 * Features:
 * - Renders chunks as optimized meshes
 * - Uses CubeRenderer for cube blocks
 * - Skips INVISIBLE blocks
 * - Listens to ChunkService events
 * - Manages mesh lifecycle
 */
export class RenderService {
  public scene: Scene; // Public for renderer access to scene
  public appContext: AppContext; // Public for renderer access to services
  public materialService: MaterialService;
  private blockTypeService: BlockTypeService;
  private textureAtlas: TextureAtlas;
  private precipitationService?: PrecipitationService;

  // Renderers
  private cubeRenderer: CubeRenderer;
  private crossRenderer: CrossRenderer;
  private hashRenderer: HashRenderer;
  private glassRenderer: GlassRenderer;
  private sphereRenderer: SphereRenderer;
  private cylinderRenderer: CylinderRenderer;
  private stairRenderer: StairRenderer;
  private stepsRenderer: StepsRenderer;
  private flipboxRenderer: FlipboxRenderer;
  private fogRenderer: FogRenderer;
  private wallRenderer: WallRenderer;
  private modelRenderer: ModelRenderer;
  private billboardRenderer: BillboardRenderer;
  private itemRenderer: ItemRenderer;
  private spriteRenderer: SpriteRenderer;
  private thinInstancesRenderer: ThinInstancesRenderer;
  private flameRenderer: FlameRenderer;
  private oceanRenderer: OceanRenderer;
  private bushRenderer: BushRenderer;
  private waterRenderer: WaterRenderer;

  // Chunk meshes: Map<chunkKey, Map<materialKey, Mesh>>
  // Each chunk can have multiple meshes (one per material type)
  private chunkMeshes: Map<string, Map<string, Mesh>> = new Map();

  constructor(
    scene: Scene,
    appContext: AppContext,
    materialService: MaterialService,
    textureAtlas: TextureAtlas
  ) {
    this.scene = scene;
    this.appContext = appContext;
    this.materialService = materialService;
    this.textureAtlas = textureAtlas;

    const blockTypeService = appContext.services.blockType;
    if (!blockTypeService) {
      throw new Error('BlockTypeService not available');
    }
    this.blockTypeService = blockTypeService;

    // Initialize renderers
    this.cubeRenderer = new CubeRenderer(textureAtlas);
    this.crossRenderer = new CrossRenderer(textureAtlas);
    this.hashRenderer = new HashRenderer(textureAtlas);
    this.glassRenderer = new GlassRenderer();
    this.sphereRenderer = new SphereRenderer(textureAtlas);
    this.cylinderRenderer = new CylinderRenderer(textureAtlas);
    this.stairRenderer = new StairRenderer(textureAtlas);
    this.stepsRenderer = new StepsRenderer(textureAtlas);
    this.flipboxRenderer = new FlipboxRenderer();
    this.fogRenderer = new FogRenderer(textureAtlas);
    this.wallRenderer = new WallRenderer(textureAtlas);
    this.modelRenderer = new ModelRenderer();
    this.billboardRenderer = new BillboardRenderer();
    this.itemRenderer = new ItemRenderer();
    this.spriteRenderer = new SpriteRenderer();
    this.thinInstancesRenderer = new ThinInstancesRenderer();
    this.flameRenderer = new FlameRenderer();
    this.oceanRenderer = new OceanRenderer();
    this.bushRenderer = new BushRenderer(textureAtlas);
    this.waterRenderer = new WaterRenderer(textureAtlas);

    // Listen to chunk events
    this.setupChunkEventListeners();

    logger.debug('RenderService initialized');
  }

  /**
   * Set PrecipitationService reference
   *
   * Called after PrecipitationService is created to provide access
   *
   * @param precipitationService PrecipitationService instance
   */
  setPrecipitationService(precipitationService: PrecipitationService): void {
    this.precipitationService = precipitationService;
    logger.debug('PrecipitationService connected to RenderService');
  }

  /**
   * Setup chunk event listeners
   */
  private setupChunkEventListeners(): void {
    const chunkService = this.appContext.services.chunk;
    if (!chunkService) {
      logger.warn('ChunkService not available, cannot listen to chunk events');
      return;
    }

    chunkService.on('chunk:loaded', (chunk: any) => {
      this.onChunkLoaded(chunk);
    });

    chunkService.on('chunk:updated', (chunk: any) => {
      this.onChunkUpdated(chunk);
    });

    chunkService.on('chunk:unloaded', (coord: { cx: number; cz: number }) => {
      this.onChunkUnloaded(coord);
    });
  }

  /**
   * Handle single chunk loaded event
   */
  private onChunkLoaded(clientChunk: ClientChunk): void {
    const cx = clientChunk.data.transfer.cx;
    const cz = clientChunk.data.transfer.cz;

    logger.debug('Chunk loaded, rendering', { cx, cz });

    this.renderChunk(clientChunk).catch((error) => {
      ExceptionHandler.handle(error, 'RenderService.onChunkLoaded', {
        cx,
        cz,
      });
    });
  }

  /**
   * Handle chunk updated event (blocks changed via b.u message)
   */
  private onChunkUpdated(clientChunk: ClientChunk): void {
    const cx = clientChunk.data.transfer.cx;
    const cz = clientChunk.data.transfer.cz;

    logger.debug('Chunk updated, re-rendering', { cx, cz, blockCount: clientChunk.data.data.size });

    // Remove old mesh first
    this.unloadChunk(cx, cz);

    // Re-render chunk with updated ClientBlocks
    this.renderChunk(clientChunk).catch((error) => {
      ExceptionHandler.handle(error, 'RenderService.onChunkUpdated', {
        cx,
        cz,
      });
    });
  }

  /**
   * Handle single chunk unloaded event
   */
  private onChunkUnloaded(coord: { cx: number; cz: number }): void {
    logger.debug('Chunk unloaded, cleaning up', { cx: coord.cx, cz: coord.cz });
    this.unloadChunk(coord.cx, coord.cz);
  }

  /**
   * Render a chunk
   *
   * NEW IMPLEMENTATION: Groups blocks by material properties and creates
   * separate meshes for each material group.
   *
   * @param clientChunk Client-side chunk with processed ClientBlocks
   */
  async renderChunk(clientChunk: ClientChunk): Promise<void> {
    const chunk = clientChunk.data.transfer; // Get transfer object for chunk coordinates
    try {
      const chunkKey = this.getChunkKey(chunk.cx, chunk.cz);

      // Check if already rendered
      if (this.chunkMeshes.has(chunkKey)) {
        logger.debug('Chunk already rendered, skipping', { cx: chunk.cx, cz: chunk.cz });
        return;
      }

      const clientBlocksMap = clientChunk.data.data;
      const blockCount = clientBlocksMap.size;

      logger.debug('Rendering chunk from ClientBlocks', {
        cx: chunk.cx,
        cz: chunk.cz,
        blockCount,
      });

      // Dispose old resources if they exist (e.g., during chunk update)
      if (clientChunk.data.resourcesToDispose) {
        const oldStats = clientChunk.data.resourcesToDispose.getStats();
        clientChunk.data.resourcesToDispose.dispose();
        logger.debug('Disposed old chunk resources before re-render', {
          cx: chunk.cx,
          cz: chunk.cz,
          oldResources: oldStats.total,
        });
      }

      // Create new DisposableResources for this chunk
      const resourcesToDispose = new DisposableResources();
      clientChunk.data.resourcesToDispose = resourcesToDispose;

      // Separate blocks into chunk mesh blocks vs separate mesh blocks
      const { chunkMeshBlocks, separateMeshBlocks } = this.separateBlocksByRenderType(clientChunk);

      logger.debug('Blocks separated by render type', {
        cx: chunk.cx,
        cz: chunk.cz,
        chunkMeshBlocks: chunkMeshBlocks.length,
        separateMeshBlocks: separateMeshBlocks.length,
      });

      // 1. Render chunk mesh blocks (batched by material at face level)
      const materialGroups = this.groupFacesByMaterial(clientChunk, chunkMeshBlocks);

      logger.debug('Material groups created', {
        cx: chunk.cx,
        cz: chunk.cz,
        groupCount: materialGroups.size,
        groups: Array.from(materialGroups.keys()),
      });

      // Create mesh for each material group
      const meshMap = new Map<string, Mesh>();

      for (const [materialKey, faceDescriptors] of materialGroups) {
        // Check if this material group needs wind attributes (check first face's block modifier)
        const firstFace = faceDescriptors[0];
        const needsWind = firstFace?.clientBlock.currentModifier?.visibility?.effect === BlockEffect.WIND;

        const faceData: FaceData = {
          positions: [],
          indices: [],
          uvs: [],
          normals: [],
          // Only initialize wind-specific attributes if needed
          ...(needsWind && {
            colors: [],
            windLeafiness: [],
            windStability: [],
            windLeverUp: [],
            windLeverDown: [],
          }),
        };

        const renderContext: RenderContext = {
          renderService: this,
          faceData,
          vertexOffset: 0,
          resourcesToDispose,
        };

        // Render all faces in this material group
        // Track blocks already rendered (for renderers without renderSingleFace support)
        const renderedBlocks = new Set<ClientBlock>();

        for (const faceDescriptor of faceDescriptors) {
          const { clientBlock, textureKey } = faceDescriptor;
          const block = clientBlock.block;

          // Validate block data
          if (!block || typeof block.blockTypeId === 'undefined' || !block.position) {
            logger.warn('Invalid block data in ClientBlock', { block });
            continue;
          }

          const modifier = clientBlock.currentModifier;
          if (!modifier || !modifier.visibility) {
            continue;
          }

          const shape = modifier.visibility.shape ?? Shape.CUBE;

          // Skip invisible blocks
          if (shape === Shape.INVISIBLE) {
            continue;
          }

          const renderer = this.getRendererForShape(shape);
          if (!renderer) {
            logger.debug('Unsupported shape, skipping', {
              shape,
              blockTypeId: block.blockTypeId,
            });
            continue;
          }

          // Use renderSingleFace if available, fallback to render for entire block
          if (renderer.renderSingleFace) {
            await renderer.renderSingleFace(renderContext, clientBlock, textureKey);
          } else {
            // Fallback: render entire block only ONCE (not per face)
            if (!renderedBlocks.has(clientBlock)) {
              await renderer.render(renderContext, clientBlock);
              renderedBlocks.add(clientBlock);
            }
          }
        }

        // Create mesh if we have any geometry
        if (faceData.positions.length > 0) {
          const vertexCount = faceData.positions.length / 3;

          logger.debug('Creating mesh - array sizes', {
            materialKey,
            vertexCount,
            positions: faceData.positions.length,
            normals: faceData.normals.length,
            uvs: faceData.uvs.length,
            colors: faceData.colors?.length ?? 0,
            windLeafiness: faceData.windLeafiness?.length ?? 0,
            windStability: faceData.windStability?.length ?? 0,
            windLeverUp: faceData.windLeverUp?.length ?? 0,
            windLeverDown: faceData.windLeverDown?.length ?? 0,
          });

          const meshName = `${chunkKey}_${materialKey}`;
          const mesh = this.createMesh(meshName, faceData);

          // Get and apply material
          // Resolve texture index for material (first face is representative of this material group)
          const firstFace = faceDescriptors[0];
          const firstModifier = firstFace.clientBlock.currentModifier;
          const firstTextures = firstModifier.visibility?.textures;
          const firstShape = firstModifier.visibility?.shape ?? Shape.CUBE;
          const resolvedTextureIndex = this.resolveTextureIndex(
            firstTextures,
            firstFace.textureKey,
            firstShape
          );

          const material = await this.materialService.getMaterial(
            firstModifier,
            resolvedTextureIndex // Use the RESOLVED texture index for material
          );
          mesh.material = material;

          // Note: backFaceCulling is configured in MaterialService based on TextureDefinition.backFaceCulling

          meshMap.set(materialKey, mesh);

          // Register mesh for illumination glow if block has illumination modifier
          const illuminationService = this.appContext.services.illumination;
          if (illuminationService && faceDescriptors[0].clientBlock.currentModifier.illumination?.color) {
            const { color, strength } = faceDescriptors[0].clientBlock.currentModifier.illumination;
            illuminationService.registerMesh(mesh, color, strength ?? 1.0);
          }

          logger.debug('Material group mesh created', {
            cx: chunk.cx,
            cz: chunk.cz,
            materialKey,
            vertices: faceData.positions.length / 3,
            faces: faceData.indices.length / 3,
            backFaceCulling: material.backFaceCulling,
          });
        }
      }

      // Store chunk meshes
      if (meshMap.size > 0) {
        this.chunkMeshes.set(chunkKey, meshMap);

        // Enable shadow receiving and casting for all chunk meshes
        const envService = this.appContext.services.environment;
        if (envService && envService.getShadowGenerator) {
          const shadowGenerator = envService.getShadowGenerator();
          if (shadowGenerator) {
            const shadowMap = shadowGenerator.getShadowMap();
            if (shadowMap && shadowMap.renderList) {
              for (const mesh of meshMap.values()) {
                mesh.receiveShadows = true;
                shadowMap.renderList.push(mesh); // All meshes cast shadows
              }
              logger.debug('Chunk meshes registered for shadows (casting + receiving)', {
                cx: chunk.cx,
                cz: chunk.cz,
                meshCount: meshMap.size,
              });
            }
          }
        }

        logger.debug('Chunk meshes rendered', {
          cx: chunk.cx,
          cz: chunk.cz,
          meshCount: meshMap.size,
        });
      } else {
        logger.debug('Chunk has no chunk mesh blocks', { cx: chunk.cx, cz: chunk.cz });
      }

      // 2. Render separate mesh blocks (individual meshes)
      for (const clientBlock of separateMeshBlocks) {
        await this.renderSeparateMeshBlock(clientBlock, chunkKey, resourcesToDispose);
      }

      logger.debug('Chunk fully rendered', {
        cx: chunk.cx,
        cz: chunk.cz,
        chunkMeshes: meshMap.size,
        separateMeshes: separateMeshBlocks.length,
      });

      // Mark chunk as rendered
      clientChunk.isRendered = true;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'RenderService.renderChunk', {
        cx: chunk.cx,
        cz: chunk.cz,
      });
    }
  }

  /**
   * Get appropriate renderer for a shape
   *
   * Routes shapes to the correct renderer.
   *
   * @param shape Shape to get renderer for
   * @returns BlockRenderer instance or null if not supported
   */
  private getRendererForShape(shape: Shape): BlockRenderer | null {
    switch (shape) {
      case Shape.CUBE:
        return this.cubeRenderer;
      case Shape.CROSS:
        return this.crossRenderer;
      case Shape.HASH:
        return this.hashRenderer;
      case Shape.GLASS:
        return this.glassRenderer;
      case Shape.SPHERE:
        return this.sphereRenderer;
      case Shape.CYLINDER:
        return this.cylinderRenderer;
      case Shape.STEPS:
        return this.stepsRenderer;
      case Shape.STAIR:
        return this.stairRenderer;
      case Shape.FLIPBOX:
        return this.flipboxRenderer;
      case Shape.FOG:
        return this.fogRenderer;
      case Shape.WALL:
        return this.wallRenderer;
      case Shape.MODEL:
        return this.modelRenderer;
      case Shape.BILLBOARD:
        return this.billboardRenderer;
      case Shape.ITEM:
        return this.itemRenderer;
      case Shape.SPRITE:
        return this.spriteRenderer;
      case Shape.THIN_INSTANCES:
        return this.thinInstancesRenderer;
      case Shape.FLAME:
        return this.flameRenderer;
      case Shape.OCEAN:
        return this.oceanRenderer;
      case Shape.BUSH:
        return this.bushRenderer;
      case Shape.WATER:
        return this.waterRenderer;
      default:
        return null;
    }
  }

  /**
   * Get appropriate renderer for a block
   *
   * Routes blocks to the correct renderer based on shader or shape.
   *
   * @param clientBlock Block to get renderer for
   * @returns BlockRenderer instance or null if not supported
   */
  private getRenderer(clientBlock: ClientBlock): BlockRenderer | null {
    const modifier = clientBlock.currentModifier;
    if (!modifier || !modifier.visibility) {
      return null;
    }

    const shape = modifier.visibility.shape ?? Shape.CUBE;
    return this.getRendererForShape(shape);
  }

  /**
   * Separate blocks into chunk mesh blocks vs separate mesh blocks
   *
   * Uses BlockRenderer.needsSeparateMesh() to determine separation.
   * This is the clean Strategy Pattern approach - each renderer knows its requirements.
   *
   * @param clientChunk Chunk with all blocks
   * @returns Separated block lists
   */
  private separateBlocksByRenderType(clientChunk: ClientChunk): {
    chunkMeshBlocks: ClientBlock[];
    separateMeshBlocks: ClientBlock[];
  } {
    const chunkMeshBlocks: ClientBlock[] = [];
    const separateMeshBlocks: ClientBlock[] = [];

    for (const clientBlock of clientChunk.data.data.values()) {
      const renderer = this.getRenderer(clientBlock);

      if (!renderer) {
        continue; // Skip blocks without renderer
      }

      // Use renderer's needsSeparateMesh() to determine grouping
      if (renderer.needsSeparateMesh()) {
        separateMeshBlocks.push(clientBlock);
      } else {
        chunkMeshBlocks.push(clientBlock);
      }
    }

    return { chunkMeshBlocks, separateMeshBlocks };
  }

  /**
   * Get effect from modifier
   * Checks texture-level effect first, then visibility-level effect
   */
  private getBlockEffect(modifier: any): BlockEffect {
    // Check texture-level effect (highest priority)
    if (modifier.visibility?.textures) {
      for (const texture of Object.values(modifier.visibility.textures)) {
        if (typeof texture === 'object' && texture !== null && 'effect' in texture) {
          return (texture as any).effect ?? BlockEffect.NONE;
        }
      }
    }

    // Check visibility-level effect
    return modifier.visibility?.effect ?? BlockEffect.NONE;
  }

  /**
   * Group blocks by their material key
   * Blocks with the same material properties will be grouped together
   *
   * @param clientChunk Chunk with all blocks
   * @param blocksToGroup Specific blocks to group (allows filtering)
   * @returns Grouped blocks by material key
   */
  private groupBlocksByMaterial(
    clientChunk: ClientChunk,
    blocksToGroup?: ClientBlock[]
  ): Map<string, ClientBlock[]> {
    const groups = new Map<string, ClientBlock[]>();
    const blocks = blocksToGroup || Array.from(clientChunk.data.data.values());

    for (const clientBlock of blocks) {
      const modifier = clientBlock.currentModifier;
      if (!modifier || !modifier.visibility) {
        continue;
      }

      // Get material key (based on properties, not texture)
      // Use textureIndex 0 as placeholder - actual texture determined by UVs
      const materialKey = this.materialService.getMaterialKey(modifier, 0);

      if (!groups.has(materialKey)) {
        groups.set(materialKey, []);
      }

      groups.get(materialKey)!.push(clientBlock);
    }

    return groups;
  }

  /**
   * Get relevant texture keys for a shape
   * Returns the texture keys that should be checked for this shape type
   */
  private getTextureKeysForShape(shape: Shape): number[] {
    switch (shape) {
      case Shape.CUBE:
        return [1, 2, 3, 4, 5, 6]; // TOP, BOTTOM, LEFT, RIGHT, FRONT, BACK
      case Shape.CROSS:
        return [3, 4]; // LEFT (diagonal1), RIGHT (diagonal2)
      case Shape.HASH:
        return [3, 4, 5, 6]; // 4 vertical faces
      default:
        return [0]; // ALL
    }
  }

  /**
   * Resolve texture index with fallback logic
   * Returns the actual texture index to use after applying fallback rules
   */
  private resolveTextureIndex(textures: any, textureKey: number, shape: Shape): number {
    if (!textures) {
      return 0;
    }

    // For CROSS blocks: special fallback logic
    if (shape === Shape.CROSS) {
      if (textureKey === 3) {
        // LEFT diagonal: 3 -> 7 -> 0
        if (textures[3]) return 3;
        if (textures[7]) return 7;
        return 0;
      } else if (textureKey === 4) {
        // RIGHT diagonal: 4 -> 3 -> 7 -> 0
        if (textures[4]) return 4;
        if (textures[3]) return 3;
        if (textures[7]) return 7;
        return 0;
      }
    }

    // For CUBE and HASH blocks: standard fallback
    // textureKey -> 7 (SIDE) -> 0 (ALL)
    if (textures[textureKey]) {
      return textureKey;
    }
    if (textures[7]) {
      return 7;
    }
    return 0;
  }

  /**
   * Convert TextureKey to FaceFlag for visibility check
   */
  private textureKeyToFaceFlag(textureKey: number): FaceFlag {
    const mapping: { [key: number]: FaceFlag } = {
      1: FaceFlag.TOP,
      2: FaceFlag.BOTTOM,
      3: FaceFlag.LEFT,
      4: FaceFlag.RIGHT,
      5: FaceFlag.FRONT,
      6: FaceFlag.BACK
    };
    return mapping[textureKey] ?? FaceFlag.ALL;
  }

  /**
   * Group faces by material properties
   * Returns Map<materialKey, FaceDescriptor[]>
   *
   * This is the new face-level grouping system that allows different faces
   * of the same block to have different materials.
   */
  private groupFacesByMaterial(
    clientChunk: ClientChunk,
    blocksToGroup?: ClientBlock[]
  ): Map<string, FaceDescriptor[]> {
    const groups = new Map<string, FaceDescriptor[]>();
    const blocks = blocksToGroup || Array.from(clientChunk.data.data.values());

    for (const clientBlock of blocks) {
      const modifier = clientBlock.currentModifier;
      if (!modifier || !modifier.visibility) {
        continue;
      }

      const shape = modifier.visibility.shape ?? Shape.CUBE;
      const textureKeys = this.getTextureKeysForShape(shape);

      // For each face of this block
      for (const textureKey of textureKeys) {
        // Check face visibility
        const isVisible = FaceVisibilityHelper.isVisible(
          clientBlock,
          this.textureKeyToFaceFlag(textureKey)
        );

        if (!isVisible) {
          continue;
        }

        // Resolve texture index with fallback logic FOR MATERIAL GROUPING ONLY
        const textures = modifier.visibility.textures;
        const resolvedTextureIndex = this.resolveTextureIndex(textures, textureKey, shape);

        // Calculate materialKey using the RESOLVED texture index
        const materialKey = this.materialService.getMaterialKey(modifier, resolvedTextureIndex);

        if (!groups.has(materialKey)) {
          groups.set(materialKey, []);
        }

        groups.get(materialKey)!.push({
          clientBlock,
          textureKey, // Store ORIGINAL textureKey (face ID: 1=TOP, 2=BOTTOM, etc.) for rendering
          isVisible: true
        });
      }
    }

    return groups;
  }

  /**
   * Render a single block with separate mesh
   *
   * Used for blocks that need individual meshes (FLIPBOX, BILLBOARD, etc.)
   *
   * @param clientBlock Block to render
   * @param chunkKey Parent chunk key for tracking
   */
  private async renderSeparateMeshBlock(
    clientBlock: ClientBlock,
    chunkKey: string,
    resourcesToDispose: DisposableResources
  ): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      return;
    }

    try {
      // Get appropriate renderer using getRenderer()
      const renderer = this.getRenderer(clientBlock);
      if (!renderer) {
        logger.warn('No renderer found for separate mesh block', { position: block.position });
        return;
      }

      const renderContext: RenderContext = {
        renderService: this,
        faceData: { positions: [], indices: [], uvs: [], normals: [] }, // Not used by separate renderers
        vertexOffset: 0,
        resourcesToDispose,
      };

      // Render using appropriate renderer
      // Renderer will add created meshes/sprites to resourcesToDispose
      await renderer.render(renderContext, clientBlock);

      logger.debug('Separate mesh rendered', {
        position: `${block.position.x},${block.position.y},${block.position.z}`,
        renderer: renderer.constructor.name,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'RenderService.renderSeparateMeshBlock', {
        position: `${block.position.x},${block.position.y},${block.position.z}`,
      });
    }
  }

  /**
   * Create a mesh from face data
   * Note: Material is NOT set here - caller must assign it
   */
  private createMesh(name: string, faceData: FaceData): Mesh {
    const mesh = new Mesh(name, this.scene);

    // Set rendering group for world content (blocks)
    mesh.renderingGroupId = RENDERING_GROUPS.WORLD;

    // Create vertex data
    const vertexData = new VertexData();
    vertexData.positions = faceData.positions;
    vertexData.indices = faceData.indices;
    vertexData.uvs = faceData.uvs;
    vertexData.normals = faceData.normals;

    // Apply to mesh
    vertexData.applyToMesh(mesh);

    // Set vertex colors if present
    if (faceData.colors && faceData.colors.length > 0) {
      mesh.setVerticesData(VertexBuffer.ColorKind, faceData.colors);
    }

    // Set wind attributes if present (for wind shader)
    // Custom attributes must be created as VertexBuffer instances
    if (faceData.windLeafiness && faceData.windLeafiness.length > 0) {
      const buffer = new VertexBuffer(this.scene.getEngine(), faceData.windLeafiness, 'windLeafiness', false, false, 1, false);
      mesh.setVerticesBuffer(buffer);
    }
    if (faceData.windStability && faceData.windStability.length > 0) {
      const buffer = new VertexBuffer(this.scene.getEngine(), faceData.windStability, 'windStability', false, false, 1, false);
      mesh.setVerticesBuffer(buffer);
    }
    if (faceData.windLeverUp && faceData.windLeverUp.length > 0) {
      const buffer = new VertexBuffer(this.scene.getEngine(), faceData.windLeverUp, 'windLeverUp', false, false, 1, false);
      mesh.setVerticesBuffer(buffer);
    }
    if (faceData.windLeverDown && faceData.windLeverDown.length > 0) {
      const buffer = new VertexBuffer(this.scene.getEngine(), faceData.windLeverDown, 'windLeverDown', false, false, 1, false);
      mesh.setVerticesBuffer(buffer);
    }

    // Material will be set by caller (renderChunk)
    // mesh.material = ... (assigned after creation)

    return mesh;
  }

  /**
   * Unload a chunk and dispose all its resources
   *
   * Disposes:
   * - Chunk meshes (batched material groups)
   * - Separate meshes/sprites via DisposableResources (including thin instances)
   */
  private unloadChunk(cx: number, cz: number): void {
    const chunkKey = this.getChunkKey(cx, cz);

    // Dispose chunk meshes (batched material groups)
    const meshMap = this.chunkMeshes.get(chunkKey);
    let chunkMeshCount = 0;

    if (meshMap) {
      // Unregister from illumination service before disposal
      const illuminationService = this.appContext.services.illumination;
      if (illuminationService) {
        for (const mesh of meshMap.values()) {
          illuminationService.unregisterMesh(mesh);
        }
      }

      for (const [materialKey, mesh] of meshMap) {
        mesh.dispose();
        chunkMeshCount++;
      }
      this.chunkMeshes.delete(chunkKey);
    }

    // Dispose separate meshes/sprites via DisposableResources
    // (includes thin instances, billboards, water, etc.)
    const chunkService = this.appContext.services.chunk;
    if (chunkService) {
      const clientChunk = chunkService.getChunk(cx, cz);
      if (clientChunk?.data.resourcesToDispose) {
        const stats = clientChunk.data.resourcesToDispose.getStats();
        clientChunk.data.resourcesToDispose.dispose();
        // Clear reference to disposed resources to prevent double-dispose in renderChunk()
        clientChunk.data.resourcesToDispose = undefined as any;

        logger.debug('Chunk resources disposed', {
          cx,
          cz,
          chunkMeshes: chunkMeshCount,
          totalResources: stats.total,
          namedResources: stats.named,
        });
        return;
      }
    }

    logger.debug('Chunk unloaded', { cx, cz, chunkMeshes: chunkMeshCount });
  }

  /**
   * Get chunk key for map storage
   */
  private getChunkKey(cx: number, cz: number): string {
    return `chunk_${cx}_${cz}`;
  }

  /**
   * Get all rendered chunk meshes
   * Returns flattened map of all meshes (chunkKey_materialKey -> Mesh)
   */
  getChunkMeshes(): Map<string, Mesh> {
    const flatMap = new Map<string, Mesh>();
    for (const [chunkKey, meshMap] of this.chunkMeshes) {
      for (const [materialKey, mesh] of meshMap) {
        flatMap.set(`${chunkKey}_${materialKey}`, mesh);
      }
    }
    return flatMap;
  }

  /**
   * Get statistics
   */
  getStats(): { renderedChunks: number; totalVertices: number; totalFaces: number; totalMeshes: number } {
    let totalVertices = 0;
    let totalFaces = 0;
    let totalMeshes = 0;

    for (const meshMap of this.chunkMeshes.values()) {
      for (const mesh of meshMap.values()) {
        totalVertices += mesh.getTotalVertices();
        totalFaces += mesh.getTotalIndices() / 3;
        totalMeshes++;
      }
    }

    return {
      renderedChunks: this.chunkMeshes.size,
      totalMeshes,
      totalVertices,
      totalFaces,
    };
  }

  /**
   * Dispose all chunks and resources
   *
   * Note: Separate meshes/sprites are disposed via ClientChunk.resourcesToDispose
   * when chunks are unloaded. This method only disposes chunk meshes.
   */
  dispose(): void {
    // Dispose chunk meshes
    for (const meshMap of this.chunkMeshes.values()) {
      for (const mesh of meshMap.values()) {
        mesh.dispose();
      }
    }
    this.chunkMeshes.clear();

    logger.debug('RenderService disposed');
  }
}
