/**
 * EngineService - Core 3D engine service
 *
 * Initializes and manages the Babylon.js engine, scene, and rendering pipeline.
 * Coordinates all rendering-related sub-services.
 */

import { Engine, Scene, RenderingManager, Vector3 } from '@babylonjs/core';
import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { TextureAtlas } from '../rendering/TextureAtlas';
import { MaterialService } from './MaterialService';
import { ModelService } from './ModelService';
import { EntityRenderService } from './EntityRenderService';
import { CameraService } from './CameraService';
import { EnvironmentService } from './EnvironmentService';
import { PlayerService } from './PlayerService';
import { RenderService } from './RenderService';
import { InputService } from './InputService';
import { PhysicsService } from './PhysicsService';
import { SelectService, SelectMode } from './SelectService';
import { BackdropService } from './BackdropService';
import { SunService } from './SunService';
import { SkyBoxService } from './SkyBoxService';
import { MoonService } from './MoonService';
import { CloudsService } from './CloudsService';
import { HorizonGradientService } from './HorizonGradientService';
import { PrecipitationService } from './PrecipitationService';
import { IlluminationService } from './IlluminationService';
import { WebInputController } from '../input/WebInputController';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('EngineService');

/**
 * EngineService - Manages Babylon.js engine and scene
 *
 * Features:
 * - Babylon.js Engine initialization
 * - Scene management
 * - Render loop
 * - Sub-service coordination (Camera, Environment, Render, Player, Input)
 */
export class EngineService {
  private appContext: AppContext;
  private canvas: HTMLCanvasElement;

  // Babylon.js core
  private engine?: Engine;
  private scene?: Scene;

  // Rendering services
  private textureAtlas?: TextureAtlas;
  private materialService?: MaterialService;
  private modelService?: ModelService;
  private entityRenderService?: EntityRenderService;

  // Sub-services
  private cameraService?: CameraService;
  private sunService?: SunService;
  private skyBoxService?: SkyBoxService;
  private moonService?: MoonService;
  private cloudsService?: CloudsService;
  private horizonGradientService?: HorizonGradientService;
  private precipitationService?: PrecipitationService;
  private environmentService?: EnvironmentService;
  private illuminationService?: IlluminationService;
  private renderService?: RenderService;
  private physicsService?: PhysicsService;
  private playerService?: PlayerService;
  private inputService?: InputService;
  private selectService?: SelectService;
  private backdropService?: BackdropService;

  private isInitialized: boolean = false;
  private isRunning: boolean = false;

  constructor(appContext: AppContext, canvas: HTMLCanvasElement) {
    this.appContext = appContext;
    this.canvas = canvas;

    logger.debug('EngineService created');
  }

  /**
   * Initialize the engine
   *
   * Creates Babylon.js Engine, Scene, and initializes all sub-services
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) {
      logger.warn('Engine already initialized');
      return;
    }

    try {
      logger.debug('Initializing 3D engine');

      // DON'T create WebGL context manually - let BabylonJS handle it!
      // Creating context here locks the canvas to that version

      // Create Babylon.js Engine - it will automatically choose WebGL2 if available
      this.engine = new Engine(this.canvas, true, {
        preserveDrawingBuffer: true,
        stencil: true,
        antialias: false,
        powerPreference: 'high-performance',
        doNotHandleContextLost: true,
        // disableWebGL2Support: false, // Don't set this - let BabylonJS decide
      }, true); // adaptToDeviceRatio = true

      if (!this.engine) {
        throw new Error('Failed to create Babylon.js Engine');
      }

      logger.info('Babylon.js Engine created', {
        webGLVersion: this.engine.webGLVersion,
        isWebGL2: (this.engine as any).isWebGL2,
      });

      // Create Scene
      this.scene = new Scene(this.engine);

      logger.debug('Scene created');

      // Configure rendering groups - enable all groups used in the application
      // This ensures proper depth clearing and rendering order
      this.configureRenderingGroups();

      // Initialize texture atlas
      this.textureAtlas = new TextureAtlas(this.scene, this.appContext);
      await this.textureAtlas.load();
      logger.debug('TextureAtlas initialized');

      // Initialize material service
      this.materialService = new MaterialService(this.scene, this.appContext);
      this.materialService.setTextureAtlas(this.textureAtlas);
      logger.debug('MaterialService initialized');

      // Initialize model service
      this.modelService = new ModelService(this.scene, this.appContext);
      logger.debug('ModelService initialized');

      // Initialize camera
      this.cameraService = new CameraService(this.scene, this.appContext);
      this.appContext.services.camera = this.cameraService;
      logger.debug('CameraService initialized');

      // Initialize sun service (after CameraService, before EnvironmentService)
      this.sunService = new SunService(this.scene, this.appContext);
      this.appContext.services.sun = this.sunService;
      logger.debug('SunService initialized');

      // Initialize skybox service (after SunService, before EnvironmentService)
      this.skyBoxService = new SkyBoxService(this.scene, this.appContext);
      this.appContext.services.skyBox = this.skyBoxService;
      logger.debug('SkyBoxService initialized');

      // Initialize moon service (after SkyBoxService, before CloudsService)
      this.moonService = new MoonService(this.scene, this.appContext);
      this.appContext.services.moon = this.moonService;
      logger.debug('MoonService initialized');

      // Initialize clouds service (after MoonService, before HorizonGradientService)
      this.cloudsService = new CloudsService(this.scene, this.appContext);
      this.appContext.services.clouds = this.cloudsService;
      logger.debug('CloudsService initialized');

      // Initialize horizon gradient service (after CloudsService, before PrecipitationService)
      this.horizonGradientService = new HorizonGradientService(this.scene, this.appContext);
      this.appContext.services.horizonGradient = this.horizonGradientService;
      logger.debug('HorizonGradientService initialized');

      // Initialize precipitation service (after HorizonGradientService, before EnvironmentService)
      this.precipitationService = new PrecipitationService(this.scene, this.appContext);
      this.appContext.services.precipitation = this.precipitationService;
      logger.debug('PrecipitationService initialized');

      // Initialize environment
      this.environmentService = new EnvironmentService(this.scene, this.appContext);
      logger.debug('EnvironmentService initialized');

      // Initialize illumination service (for block glow effects)
      this.illuminationService = new IlluminationService(this.scene, this.appContext);
      this.appContext.services.illumination = this.illuminationService;
      logger.debug('IlluminationService initialized');

      // Initialize ShaderService with scene and connect to EnvironmentService
      const shaderService = this.appContext.services.shader;
      if (shaderService) {
        shaderService.initialize(this.scene);
        shaderService.setEnvironmentService(this.environmentService);

        // Connect MaterialService with ShaderService
        this.materialService.setShaderService(shaderService);

        logger.debug('ShaderService initialized with scene and connected to EnvironmentService');
        logger.debug('MaterialService connected to ShaderService');
      }

      // Initialize SpriteService with scene and connect to EnvironmentService
      const spriteService = new (await import('./SpriteService')).SpriteService(this.scene, this.appContext);
      this.appContext.services.sprite = spriteService;
      spriteService.setEnvironmentService(this.environmentService);
      logger.debug('SpriteService initialized with scene and connected to EnvironmentService');

      // Initialize ThinInstancesService with scene and shader service
      const thinInstancesService = new (await import('./ThinInstancesService')).ThinInstancesService(this.scene, this.appContext);
      this.appContext.services.thinInstances = thinInstancesService;
      if (shaderService) {
        thinInstancesService.setShaderService(shaderService);
      }
      logger.debug('ThinInstancesService initialized with scene and ShaderService');

      // Store environment service reference for access
      this.appContext.services.environment = this.environmentService;

      // Initialize physics
      this.physicsService = new PhysicsService(this.appContext);
      this.appContext.services.physics = this.physicsService;

      // Connect physics with chunk service for collision detection
      if (this.appContext.services.chunk) {
        this.physicsService.setChunkService(this.appContext.services.chunk);
      }

      logger.debug('PhysicsService initialized and registered');

      // Initialize AudioService with scene (async) - AFTER PhysicsService for event subscriptions
      const audioService = this.appContext.services.audio;
      if (audioService) {
        await audioService.initialize(this.scene);
        logger.debug('AudioService initialized with scene and subscribed to PhysicsService events');
      } else {
        logger.warn('AudioService not available in AppContext');
      }

      // Initialize player
      this.playerService = new PlayerService(this.appContext, this.cameraService);
      this.playerService.setPhysicsService(this.physicsService);
      this.appContext.services.player = this.playerService;
      logger.debug('PlayerService initialized');

      // Disable physics during initial chunk loading to prevent falling
      this.physicsService.disablePhysics();
      logger.debug('Physics disabled for initial spawn');

      // Connect CameraService with PlayerService for turnSpeed updates
      this.cameraService.setPlayerService(this.playerService);
      logger.debug('CameraService connected to PlayerService');

      // Initialize render service
      this.renderService = new RenderService(
        this.scene,
        this.appContext,
        this.materialService,
        this.textureAtlas
      );
      this.appContext.services.render = this.renderService;
      logger.debug('RenderService initialized');

      // Connect RenderService with PrecipitationService
      if (this.precipitationService) {
        this.renderService.setPrecipitationService(this.precipitationService);
        logger.debug('RenderService connected to PrecipitationService');
      }

      // Initialize entity render service (requires EntityService and ModelService)
      const entityService = this.appContext.services.entity;
      if (entityService && this.modelService) {
        this.entityRenderService = new EntityRenderService(
          this.scene,
          this.appContext,
          entityService,
          this.modelService
        );
        this.appContext.services.entityRender = this.entityRenderService;

        // Connect EntityRenderService to PlayerService for player avatar rendering
        if (this.playerService) {
          this.playerService.setEntityRenderService(this.entityRenderService);
          logger.debug('EntityRenderService connected to PlayerService');
        }

        logger.debug('EntityRenderService initialized');
      } else {
        logger.warn('EntityRenderService not initialized: missing EntityService or ModelService');
      }

      // Initialize backdrop service (requires scene and appContext with ChunkService)
      if (this.scene && this.appContext.services.chunk) {
        this.backdropService = new BackdropService(this.scene, this.appContext);
        logger.debug('BackdropService initialized');
      } else {
        logger.warn('BackdropService not initialized: missing Scene or ChunkService');
      }

      // Initialize input service
      this.inputService = new InputService(this.appContext, this.playerService);
      this.appContext.services.input = this.inputService; // Register in AppContext
      const webInputController = new WebInputController(this.canvas, this.playerService, this.appContext);
      this.inputService.setController(webInputController);
      logger.debug('InputService initialized');

      // Initialize select service (requires ChunkService and PlayerService)
      if (this.appContext.services.chunk && this.playerService && this.scene) {
        this.selectService = new SelectService(
          this.appContext,
          this.appContext.services.chunk,
          this.playerService,
          this.scene,
          this.appContext.services.entity // Add EntityService for entity selection
        );
        this.appContext.services.select = this.selectService;
        logger.debug('SelectService initialized');

        // Set auto-select mode based on build mode
        if (__EDITOR__) {
          this.selectService.autoSelectMode = SelectMode.BLOCK;
          logger.debug('Auto-select mode set to BLOCK (Editor build)');

          // Listen for new blocks from network (for model selector watchBlocks mode)
          const networkService = this.appContext.services.network;
          if (networkService) {
            networkService.on('newBlocks', (blocks: import('@nimbus/shared').Block[]) => {
              if (this.selectService) {
                // Convert Block positions with source field (SelectService will apply default color)
                const coordinates = blocks.map(b => ({
                  x: b.position.x,
                  y: b.position.y,
                  z: b.position.z,
                  source: b.source
                }));
                this.selectService.onNewBlocks(coordinates);
              }
            });
            logger.debug('SelectService: listening for newBlocks events');
          }
        } else if (__VIEWER__) {
          this.selectService.autoSelectMode = SelectMode.INTERACTIVE;
          logger.debug('Auto-select mode set to INTERACTIVE (Viewer build)');
        }
      } else {
        logger.warn('SelectService not initialized: missing ChunkService, PlayerService, or Scene');
      }

      // Listen to player position changes to update chunks
      this.playerService.on('position:changed', (position) => {
        const chunkService = this.appContext.services.chunk;
        if (chunkService) {
          chunkService.updateChunksAroundPosition(position.x, position.z);
        }
      });
      logger.debug('Player position listener registered');

      // Handle window resize
      window.addEventListener('resize', this.onResize);

      this.isInitialized = true;

      logger.debug('3D engine initialized successfully');
    } catch (error) {
      this.isInitialized = false;
      throw ExceptionHandler.handleAndRethrow(error, 'EngineService.initialize');
    }
  }

  /**
   * Start the render loop
   */
  startRenderLoop(): void {
    if (!this.isInitialized) {
      throw new Error('Engine not initialized');
    }

    if (this.isRunning) {
      logger.warn('Render loop already running');
      return;
    }

    logger.debug('Starting render loop');

    let lastTime = performance.now();

    this.engine!.runRenderLoop(() => {
      try {
        // Calculate delta time
        const currentTime = performance.now();
        const deltaTime = (currentTime - lastTime) / 1000; // Convert to seconds
        lastTime = currentTime;

        // Update services
        this.inputService?.update(deltaTime);
        this.physicsService?.update(deltaTime); // Physics before player
        this.playerService?.update(deltaTime);
        this.cameraService?.update(deltaTime);
        this.selectService?.update(deltaTime); // Update block selection and highlighting
        this.cloudsService?.update(deltaTime); // Update cloud positions and fading
        this.precipitationService?.update(deltaTime); // Update precipitation particle positions
        this.environmentService?.update(deltaTime);

        // Check and emit player direction updates (for beam:follow effects)
        this.physicsService?.checkAndEmitPlayerDirection();

        // Render scene
        this.scene!.render();
      } catch (error) {
        ExceptionHandler.handle(error, 'EngineService.renderLoop');
      }
    });

    this.isRunning = true;
  }

  /**
   * Stop the render loop
   */
  stopRenderLoop(): void {
    if (!this.isRunning) {
      return;
    }

    logger.debug('Stopping render loop');

    this.engine?.stopRenderLoop();
    this.isRunning = false;
  }

  /**
   * Configure rendering groups for proper rendering order and depth clearing
   *
   * By default, BabylonJS only renders objects in rendering group 0.
   * We need to enable all rendering groups used in the application.
   *
   * Rendering order (low to high):
   * 0: ENVIRONMENT (sky, sun) - clears depth
   * 1: BACKDROP (chunk boundaries)
   * 2: WORLD (blocks, entities) - main content with depth testing
   * 3: PRECIPITATION (rain, snow)
   * 4: SELECTION_OVERLAY (highlights) - should render on top, clears depth
   */
  private configureRenderingGroups(): void {
    if (!this.scene) {
      logger.error('Cannot configure rendering groups: scene not available');
      return;
    }

    // Get all unique rendering group IDs and find the maximum
    const groupIds = [...new Set(Object.values(RENDERING_GROUPS))];
    const maxGroupId = Math.max(...groupIds);

    // Set MAX_RENDERINGGROUPS to enable all groups we need
    // BabylonJS 7 requires this to be set explicitly
    RenderingManager.MAX_RENDERINGGROUPS = maxGroupId + 1;

    // Configure auto-clear behavior for each group
    for (let groupId = 0; groupId <= maxGroupId; groupId++) {
      let autoClearDepth = false;
      let autoClearStencil = false;

      if (groupId === RENDERING_GROUPS.ENVIRONMENT) {
        // ENVIRONMENT (group 0): Clear depth to start fresh
        autoClearDepth = true;
        autoClearStencil = true;
      } else if (groupId === RENDERING_GROUPS.SELECTION_OVERLAY) {
        // SELECTION_OVERLAY: Clear depth so overlays render on top
        autoClearDepth = true;
        autoClearStencil = false;
      }
      // All other groups preserve depth from previous groups for proper occlusion

      this.scene.setRenderingAutoClearDepthStencil(groupId, autoClearDepth, autoClearStencil);
    }

    logger.debug('Rendering groups configured', {
      maxGroupId,
      maxRenderingGroups: RenderingManager.MAX_RENDERINGGROUPS,
      configuredGroups: maxGroupId + 1,
      usedGroupIds: groupIds.sort((a, b) => a - b),
    });
  }

  /**
   * Handle window resize
   */
  private onResize = (): void => {
    this.engine?.resize();
  };

  /**
   * Get the Babylon.js engine
   */
  getEngine(): Engine | undefined {
    return this.engine;
  }

  /**
   * Get the Babylon.js scene
   */
  getScene(): Scene | undefined {
    return this.scene;
  }

  /**
   * Get the texture atlas
   */
  getTextureAtlas(): TextureAtlas | undefined {
    return this.textureAtlas;
  }

  /**
   * Get the material service
   */
  getMaterialService(): MaterialService | undefined {
    return this.materialService;
  }

  /**
   * Get the model service
   */
  getModelService(): ModelService | undefined {
    return this.modelService;
  }

  /**
   * Get the entity render service
   */
  getEntityRenderService(): EntityRenderService | undefined {
    return this.entityRenderService;
  }

  /**
   * Get the camera service
   */
  getCameraService(): CameraService | undefined {
    return this.cameraService;
  }

  /**
   * Get the environment service
   */
  getEnvironmentService(): EnvironmentService | undefined {
    return this.environmentService;
  }

  /**
   * Get the physics service
   */
  getPhysicsService(): PhysicsService | undefined {
    return this.physicsService;
  }

  /**
   * Get the player service
   */
  getPlayerService(): PlayerService | undefined {
    return this.playerService;
  }

  /**
   * Get the render service
   */
  getRenderService(): RenderService | undefined {
    return this.renderService;
  }

  /**
   * Get the input service
   */
  getInputService(): InputService | undefined {
    return this.inputService;
  }

  /**
   * Check if engine is initialized
   */
  isReady(): boolean {
    return (
      this.isInitialized &&
      (this.textureAtlas?.isReady() ?? false) &&
      (this.materialService?.isReady() ?? false)
    );
  }

  /**
   * Set wireframe mode for all materials
   *
   * @param enabled true to enable wireframe mode, false to disable
   */
  setWireframeMode(enabled: boolean): void {
    if (!this.scene) {
      logger.warn('Cannot set wireframe mode: scene not initialized');
      return;
    }

    // Set wireframe mode on all materials in the scene
    this.scene.materials.forEach((material) => {
      material.wireframe = enabled;
    });

    logger.debug(`Wireframe mode ${enabled ? 'enabled' : 'disabled'}`, {
      materialCount: this.scene.materials.length,
    });
  }

  /**
   * Dispose engine and all resources
   */
  dispose(): void {
    logger.debug('Disposing engine');

    this.stopRenderLoop();

    // Remove event listeners
    window.removeEventListener('resize', this.onResize);

    // Dispose sub-services
    this.inputService?.dispose();
    this.selectService?.dispose();
    this.backdropService?.dispose();
    this.renderService?.dispose();
    this.entityRenderService?.dispose();
    this.playerService?.dispose();
    this.physicsService?.dispose();
    this.environmentService?.dispose();
    this.illuminationService?.dispose();
    this.precipitationService?.dispose();
    this.horizonGradientService?.dispose();
    this.cloudsService?.dispose();
    this.moonService?.dispose();
    this.skyBoxService?.dispose();
    this.sunService?.dispose();
    this.cameraService?.dispose();
    this.modelService?.dispose();
    this.materialService?.dispose();

    // Dispose Babylon.js resources
    this.scene?.dispose();
    this.engine?.dispose();

    this.isInitialized = false;
    this.isRunning = false;

    logger.debug('Engine disposed');
  }
}
