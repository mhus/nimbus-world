/**
 * SkyBoxService - Manages skybox visualization
 *
 * Creates a skybox mesh that renders the sky environment.
 * Supports both solid color and 6-sided cube texture modes.
 */

import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Color3,
  CubeTexture,
  Texture,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import type { NetworkService } from './NetworkService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('SkyBoxService');

/**
 * SkyBoxService - Manages skybox for sky rendering
 *
 * Features:
 * - Color mode: Solid color skybox
 * - Texture mode: 6-sided cube texture skybox
 * - Attached to camera (follows camera movement)
 * - Configurable size and rotation
 * - WorldInfo integration
 */
export class SkyBoxService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;
  private networkService?: NetworkService;

  // SkyBox components
  private skyBoxRoot?: TransformNode;
  private skyBoxMesh?: Mesh;
  private skyBoxMaterial?: StandardMaterial;

  // Configuration
  private enabled: boolean = false; // Disabled by default
  private mode: 'color' | 'texture' = 'color';
  private skyBoxColor: Color3 = new Color3(0.2, 0.5, 1.0); // Sky blue
  private texturePath?: string;
  private size: number = 2000;
  private rotationY: number = 0; // Rotation in degrees

  // Original clear color (from WorldInfo or default)
  private originalClearColor: Color3 = new Color3(0.5, 0.7, 1.0);

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;
    this.networkService = appContext.services.network;

    this.initialize();
  }

  /**
   * Load initial skybox parameters from WorldInfo settings
   */
  private loadParametersFromWorldInfo(): void {
    const settings = this.appContext.worldInfo?.settings;
    if (!settings?.skyBox) return;

    const skyBoxSettings = settings.skyBox;

    // Load enabled state
    if (skyBoxSettings.enabled !== undefined) {
      this.enabled = skyBoxSettings.enabled;
    }

    // Load mode
    if (skyBoxSettings.mode) {
      this.mode = skyBoxSettings.mode;
    }

    // Load color (for color mode)
    if (skyBoxSettings.color) {
      this.skyBoxColor = new Color3(
        skyBoxSettings.color.r,
        skyBoxSettings.color.g,
        skyBoxSettings.color.b
      );
    }

    // Load texture path (for texture mode)
    if (skyBoxSettings.texturePath) {
      this.texturePath = skyBoxSettings.texturePath;
    }

    // Load size
    if (skyBoxSettings.size !== undefined) {
      this.size = skyBoxSettings.size;
    }

    // Load rotation
    if (skyBoxSettings.rotation !== undefined) {
      this.rotationY = skyBoxSettings.rotation;
    }

    logger.debug('SkyBox parameters loaded from WorldInfo', {
      enabled: this.enabled,
      mode: this.mode,
      size: this.size,
      rotation: this.rotationY,
      hasTexturePath: !!this.texturePath,
    });
  }

  private async initialize(): Promise<void> {
    // Load initial parameters from WorldInfo
    this.loadParametersFromWorldInfo();

    // Store original clear color from WorldInfo or default
    const settings = this.appContext.worldInfo?.settings;
    if (settings?.clearColor) {
      this.originalClearColor = new Color3(
        settings.clearColor.r,
        settings.clearColor.g,
        settings.clearColor.b
      );
    }

    // Create skybox root node attached to camera environment root
    const cameraRoot = this.cameraService.getCameraEnvironmentRoot();
    if (!cameraRoot) {
      logger.error('Camera environment root not available');
      return;
    }

    this.skyBoxRoot = new TransformNode('skyBoxRoot', this.scene);
    this.skyBoxRoot.parent = cameraRoot;

    // Create skybox mesh
    this.createSkyBoxMesh();

    // Apply material based on mode
    if (this.mode === 'texture' && this.texturePath) {
      await this.applyTextureMaterial(this.texturePath);
    } else {
      this.applyColorMaterial();
    }

    // Set enabled state and update clear color
    if (this.skyBoxMesh) {
      this.skyBoxMesh.setEnabled(this.enabled);
    }

    // Update scene clear color based on enabled state
    if (this.enabled) {
      this.scene.clearColor = new Color3(0, 0, 0).toColor4();
    }

    logger.debug('SkyBoxService initialized', {
      mode: this.mode,
      enabled: this.enabled,
      size: this.size,
    });
  }

  /**
   * Create skybox mesh
   */
  private createSkyBoxMesh(): void {
    if (!this.skyBoxRoot) return;

    // Create box mesh with inside visible
    this.skyBoxMesh = MeshBuilder.CreateBox(
      'skyBox',
      {
        size: this.size,
        sideOrientation: Mesh.BACKSIDE, // Inside visible
      },
      this.scene
    );

    this.skyBoxMesh.parent = this.skyBoxRoot;
    this.skyBoxMesh.infiniteDistance = true; // Always at horizon
    this.skyBoxMesh.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT; // Render behind everything

    // Set initial rotation
    if (this.rotationY !== 0) {
      this.skyBoxMesh.rotation.y = this.rotationY * (Math.PI / 180);
    }

    logger.debug('SkyBox mesh created', { size: this.size });
  }

  /**
   * Apply color material to skybox
   */
  private applyColorMaterial(): void {
    if (!this.skyBoxMesh) return;

    // Dispose old material
    this.skyBoxMaterial?.dispose();

    // Create new material
    this.skyBoxMaterial = new StandardMaterial('skyBoxMaterial', this.scene);
    this.skyBoxMaterial.diffuseColor = this.skyBoxColor;
    this.skyBoxMaterial.emissiveColor = this.skyBoxColor; // Self-illuminated
    this.skyBoxMaterial.disableLighting = true;
    this.skyBoxMaterial.backFaceCulling = false;

    this.skyBoxMesh.material = this.skyBoxMaterial;

    logger.debug('SkyBox color material applied', {
      color: { r: this.skyBoxColor.r, g: this.skyBoxColor.g, b: this.skyBoxColor.b },
    });
  }

  /**
   * Load cube texture from base path
   * @param basePath Base path for textures (e.g., "textures/skybox/stars")
   */
  private async loadCubeTexture(basePath: string): Promise<CubeTexture | null> {
    if (!this.networkService) {
      logger.error('NetworkService not available for texture loading');
      return null;
    }

    try {
      // Get base URL from network service
      const baseUrl = this.networkService.getAssetUrl(basePath);

      // Create cube texture (Babylon.js automatically appends _px.png, _nx.png, etc.)
      const cubeTexture = new CubeTexture(
        baseUrl,
        this.scene,
        ['_px.png', '_nx.png', '_py.png', '_ny.png', '_pz.png', '_nz.png']
      );

      logger.debug('CubeTexture loaded', { basePath });
      return cubeTexture;
    } catch (error) {
      logger.error('Failed to load CubeTexture', { basePath, error });
      return null;
    }
  }

  /**
   * Apply texture material to skybox
   * @param basePath Base path for cube textures
   */
  private async applyTextureMaterial(basePath: string): Promise<void> {
    if (!this.skyBoxMesh) return;

    const cubeTexture = await this.loadCubeTexture(basePath);
    if (!cubeTexture) {
      logger.warn('CubeTexture loading failed, keeping current material');
      return;
    }

    // Dispose old material
    this.skyBoxMaterial?.dispose();

    // Create new material with cube texture
    this.skyBoxMaterial = new StandardMaterial('skyBoxMaterial', this.scene);
    this.skyBoxMaterial.reflectionTexture = cubeTexture;
    this.skyBoxMaterial.reflectionTexture.coordinatesMode = Texture.SKYBOX_MODE;
    this.skyBoxMaterial.diffuseColor = new Color3(0, 0, 0);
    this.skyBoxMaterial.specularColor = new Color3(0, 0, 0);
    this.skyBoxMaterial.disableLighting = true;
    this.skyBoxMaterial.backFaceCulling = false;

    this.skyBoxMesh.material = this.skyBoxMaterial;

    logger.debug('SkyBox texture material applied', { basePath });
  }

  /**
   * Enable/disable skybox visibility
   * @param enabled True to show skybox, false to hide
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;

    if (this.skyBoxMesh) {
      this.skyBoxMesh.setEnabled(enabled);
    }

    // When skybox is enabled, set scene clear color to black
    // When disabled, restore original clear color from WorldInfo
    if (enabled) {
      this.scene.clearColor = new Color3(0, 0, 0).toColor4();
    } else {
      this.scene.clearColor = this.originalClearColor.toColor4();
    }

    logger.debug('SkyBox visibility changed', { enabled });
  }

  /**
   * Set skybox to color mode
   * @param color RGB color
   */
  setColorMode(color: Color3): void {
    this.mode = 'color';
    this.skyBoxColor = color;
    this.applyColorMaterial();

    logger.debug('SkyBox switched to color mode', {
      color: { r: color.r, g: color.g, b: color.b },
    });
  }

  /**
   * Set skybox to texture mode
   * @param basePath Base path for cube textures (e.g., "textures/skybox/stars")
   */
  async setTextureMode(basePath: string): Promise<void> {
    this.mode = 'texture';
    this.texturePath = basePath;
    await this.applyTextureMaterial(basePath);

    logger.debug('SkyBox switched to texture mode', { basePath });
  }

  /**
   * Set skybox size
   * @param size Box size
   */
  setSize(size: number): void {
    this.size = size;

    if (this.skyBoxMesh) {
      // Scale mesh relative to default size
      this.skyBoxMesh.scaling.setAll(size / 2000);
    }

    logger.debug('SkyBox size updated', { size });
  }

  /**
   * Set skybox rotation
   * @param degrees Rotation angle in degrees (around Y axis)
   */
  setRotation(degrees: number): void {
    this.rotationY = degrees;

    if (this.skyBoxMesh) {
      this.skyBoxMesh.rotation.y = degrees * (Math.PI / 180);
    }

    logger.debug('SkyBox rotation updated', { degrees });
  }

  /**
   * Get current skybox enabled state
   */
  isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Get current skybox mode
   */
  getMode(): 'color' | 'texture' {
    return this.mode;
  }

  /**
   * Cleanup and dispose resources
   */
  dispose(): void {
    this.skyBoxMesh?.dispose();
    this.skyBoxMaterial?.dispose();
    this.skyBoxRoot?.dispose();

    logger.debug('SkyBoxService disposed');
  }
}
