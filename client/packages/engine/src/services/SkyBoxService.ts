/**
 * SkyBoxService - Manages skybox visualization
 *
 * Creates a skybox from 6 textured planes that renders the sky environment.
 * Supports both solid color and 6-sided texture modes.
 * Textures are loaded with credentials via fetch + blob URLs.
 */

import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Color3,
  Texture,
  Vector3,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import type { NetworkService } from './NetworkService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('SkyBoxService');

/** Face definitions for the 6 sides of the skybox cube */
const SKYBOX_FACES = [
  { name: 'px', suffix: '_px.png', rotation: { x: 0, y: -Math.PI / 2, z: 0 } },  // +X (right)
  { name: 'nx', suffix: '_nx.png', rotation: { x: 0, y: Math.PI / 2, z: 0 } },   // -X (left)
  { name: 'py', suffix: '_py.png', rotation: { x: Math.PI / 2, y: 0, z: 0 } },   // +Y (top)
  { name: 'ny', suffix: '_ny.png', rotation: { x: -Math.PI / 2, y: 0, z: 0 } },  // -Y (bottom)
  { name: 'pz', suffix: '_pz.png', rotation: { x: 0, y: 0, z: 0 } },             // +Z (front)
  { name: 'nz', suffix: '_nz.png', rotation: { x: 0, y: Math.PI, z: 0 } },       // -Z (back)
];

/**
 * SkyBoxService - Manages skybox for sky rendering
 *
 * Features:
 * - Color mode: Solid color skybox (single box mesh)
 * - Texture mode: 6 textured planes with credential-aware loading
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
  private skyBoxMesh?: Mesh; // Used for color mode
  private skyBoxMaterial?: StandardMaterial; // Used for color mode
  private texturePlanes: Mesh[] = []; // Used for texture mode
  private textureMaterials: StandardMaterial[] = []; // Used for texture mode

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

    if (skyBoxSettings.enabled !== undefined) {
      this.enabled = skyBoxSettings.enabled;
    }
    if (skyBoxSettings.mode) {
      this.mode = skyBoxSettings.mode;
    }
    if (skyBoxSettings.color) {
      this.skyBoxColor = new Color3(
        skyBoxSettings.color.r,
        skyBoxSettings.color.g,
        skyBoxSettings.color.b
      );
    }
    if (skyBoxSettings.texturePath) {
      this.texturePath = skyBoxSettings.texturePath;
    }
    if (skyBoxSettings.size !== undefined) {
      this.size = skyBoxSettings.size;
    }
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

    // Apply material based on mode
    if (this.mode === 'texture' && this.texturePath) {
      await this.applyTextureMaterial(this.texturePath);
    } else {
      this.createColorSkyBox();
      this.applyColorMaterial();
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
   * Create single box mesh for color mode
   */
  private createColorSkyBox(): void {
    if (!this.skyBoxRoot) return;

    this.skyBoxMesh = MeshBuilder.CreateBox(
      'skyBox',
      { size: this.size, sideOrientation: Mesh.BACKSIDE },
      this.scene
    );
    this.skyBoxMesh.parent = this.skyBoxRoot;
    this.skyBoxMesh.infiniteDistance = true;
    this.skyBoxMesh.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;

    if (this.rotationY !== 0) {
      this.skyBoxMesh.rotation.y = this.rotationY * (Math.PI / 180);
    }
  }

  /**
   * Apply color material to skybox box mesh
   */
  private applyColorMaterial(): void {
    if (!this.skyBoxMesh) return;

    this.skyBoxMaterial?.dispose();

    this.skyBoxMaterial = new StandardMaterial('skyBoxMaterial', this.scene);
    this.skyBoxMaterial.diffuseColor = this.skyBoxColor;
    this.skyBoxMaterial.emissiveColor = this.skyBoxColor;
    this.skyBoxMaterial.disableLighting = true;
    this.skyBoxMaterial.backFaceCulling = false;

    this.skyBoxMesh.material = this.skyBoxMaterial;
    this.skyBoxMesh.setEnabled(this.enabled);

    logger.debug('SkyBox color material applied', {
      color: { r: this.skyBoxColor.r, g: this.skyBoxColor.g, b: this.skyBoxColor.b },
    });
  }

  /**
   * Apply texture material: creates 6 planes with individually loaded textures.
   * Each face texture is loaded with credentials via blob URLs.
   */
  private async applyTextureMaterial(basePath: string): Promise<void> {
    if (!this.skyBoxRoot || !this.networkService) return;

    // Dispose previous texture planes and color box
    this.disposeTexturePlanes();
    this.skyBoxMesh?.dispose();
    this.skyBoxMesh = undefined;
    this.skyBoxMaterial?.dispose();
    this.skyBoxMaterial = undefined;

    const baseUrl = this.networkService.getAssetUrl(basePath);
    const halfSize = this.size / 2;

    // Load all 6 face textures with credentials in parallel
    const blobUrls = await Promise.all(
      SKYBOX_FACES.map(async (face) => {
        try {
          return await loadTextureUrlWithCredentials(baseUrl + face.suffix);
        } catch (error) {
          logger.error('Failed to load skybox face', { face: face.name, error });
          return null;
        }
      })
    );

    // Check if all textures loaded
    if (blobUrls.some(url => url === null)) {
      logger.warn('Some skybox textures failed to load, falling back to color mode');
      this.createColorSkyBox();
      this.applyColorMaterial();
      return;
    }

    logger.debug('All 6 skybox face textures loaded', { basePath });

    // Create a plane for each face
    for (let i = 0; i < SKYBOX_FACES.length; i++) {
      const face = SKYBOX_FACES[i];
      const blobUrl = blobUrls[i]!;

      // Create plane
      const plane = MeshBuilder.CreatePlane(
        `skyBox_${face.name}`,
        { size: this.size, sideOrientation: Mesh.BACKSIDE },
        this.scene
      );
      plane.parent = this.skyBoxRoot;
      plane.infiniteDistance = true;
      plane.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;

      // Position plane at the face of the cube
      plane.rotation.set(face.rotation.x, face.rotation.y, face.rotation.z);
      // Move plane outward along its normal (after rotation)
      plane.position = new Vector3(0, 0, 0);
      switch (face.name) {
        case 'px': plane.position.x = halfSize; break;
        case 'nx': plane.position.x = -halfSize; break;
        case 'py': plane.position.y = halfSize; break;
        case 'ny': plane.position.y = -halfSize; break;
        case 'pz': plane.position.z = halfSize; break;
        case 'nz': plane.position.z = -halfSize; break;
      }

      // Apply rotation offset
      if (this.rotationY !== 0) {
        // Rotation is applied to the root node instead
      }

      // Create material with texture
      const material = new StandardMaterial(`skyBoxMat_${face.name}`, this.scene);
      const texture = new Texture(blobUrl, this.scene, false, false);
      texture.hasAlpha = false;
      material.diffuseTexture = texture;
      material.emissiveTexture = texture;
      material.disableLighting = true;
      material.backFaceCulling = false;

      plane.material = material;
      plane.setEnabled(this.enabled);

      this.texturePlanes.push(plane);
      this.textureMaterials.push(material);
    }

    // Apply rotation to root
    if (this.rotationY !== 0 && this.skyBoxRoot) {
      this.skyBoxRoot.rotation.y = this.rotationY * (Math.PI / 180);
    }

    logger.debug('SkyBox texture planes created', { basePath, faceCount: this.texturePlanes.length });
  }

  /**
   * Dispose texture planes and materials
   */
  private disposeTexturePlanes(): void {
    for (const mat of this.textureMaterials) {
      mat.diffuseTexture?.dispose();
      mat.emissiveTexture?.dispose();
      mat.dispose();
    }
    for (const plane of this.texturePlanes) {
      plane.dispose();
    }
    this.texturePlanes = [];
    this.textureMaterials = [];
  }

  /**
   * Enable/disable skybox visibility
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;

    if (this.skyBoxMesh) {
      this.skyBoxMesh.setEnabled(enabled);
    }
    for (const plane of this.texturePlanes) {
      plane.setEnabled(enabled);
    }

    if (enabled) {
      this.scene.clearColor = new Color3(0, 0, 0).toColor4();
    } else {
      this.scene.clearColor = this.originalClearColor.toColor4();
    }

    logger.debug('SkyBox visibility changed', { enabled });
  }

  /**
   * Set skybox to color mode
   */
  setColorMode(color: Color3): void {
    this.mode = 'color';
    this.skyBoxColor = color;
    this.disposeTexturePlanes();

    if (!this.skyBoxMesh) {
      this.createColorSkyBox();
    }
    this.applyColorMaterial();

    logger.debug('SkyBox switched to color mode', {
      color: { r: color.r, g: color.g, b: color.b },
    });
  }

  /**
   * Set skybox to texture mode
   */
  async setTextureMode(basePath: string): Promise<void> {
    this.mode = 'texture';
    this.texturePath = basePath;
    await this.applyTextureMaterial(basePath);

    logger.debug('SkyBox switched to texture mode', { basePath });
  }

  /**
   * Set skybox size
   */
  setSize(size: number): void {
    this.size = size;

    if (this.skyBoxMesh) {
      this.skyBoxMesh.scaling.setAll(size / 2000);
    }
    // For texture planes, we'd need to recreate them - size changes are rare
    if (this.texturePlanes.length > 0) {
      const scale = size / 2000;
      for (const plane of this.texturePlanes) {
        plane.scaling.setAll(scale);
      }
    }

    logger.debug('SkyBox size updated', { size });
  }

  /**
   * Set skybox rotation
   */
  setRotation(degrees: number): void {
    this.rotationY = degrees;

    if (this.skyBoxMesh) {
      this.skyBoxMesh.rotation.y = degrees * (Math.PI / 180);
    }
    if (this.skyBoxRoot && this.texturePlanes.length > 0) {
      this.skyBoxRoot.rotation.y = degrees * (Math.PI / 180);
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
    this.disposeTexturePlanes();
    this.skyBoxMesh?.dispose();
    this.skyBoxMaterial?.dispose();
    this.skyBoxRoot?.dispose();

    logger.debug('SkyBoxService disposed');
  }
}
