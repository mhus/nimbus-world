import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  PlaneBuilder,
  StandardMaterial,
  Color3,
  RawTexture,
  Constants,
  Texture,
  LensFlareSystem,
  LensFlare,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import type { NetworkService } from './NetworkService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('SunService');

/**
 * SunService - Manages sun visualization using a simple billboard
 *
 * Creates a single billboard plane that always faces the camera.
 * Supports custom texture from WorldInfo or fallback circular disc.
 */
export class SunService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;
  private networkService?: NetworkService;

  // Sun components
  private sunRoot?: TransformNode;
  private sunMesh?: Mesh;
  private sunMaterial?: StandardMaterial;
  private sunTexture?: Texture | RawTexture;

  // Lens flare system
  private lensFlareSystem?: LensFlareSystem;
  private lensFlareEnabled: boolean = false;
  private lensFlareIntensity: number = 1.0;
  private lensFlareColor: Color3 = new Color3(1, 0.9, 0.7); // Warm flare color
  private lensFlareTexture: string = 'w:textures/sun/flare.png';

  // Sun position parameters
  private currentAngleY: number = 90; // Default: East
  private currentElevation: number = 45; // Default: 45° above horizon
  private orbitRadius: number = 400; // Distance from camera

  // Sun appearance (defaults, will be overridden by WorldInfo)
  private sunColor: Color3 = new Color3(1, 1, 0.9); // Warm white/yellow
  private sunSize: number = 1; // Billboard scaling factor (default: 1)
  private enabled: boolean = true;

  // Automatic sun adjustment
  private automaticSunAdjustment: boolean = true;
  private sunLightIntensityMultiplier: number = 1.0;
  private ambientLightIntensityMultiplier: number = 0.5;

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;
    this.networkService = appContext.services.network;

    this.initialize();
  }

  /**
   * Load initial sun parameters from WorldInfo settings
   */
  private loadParametersFromWorldInfo(): void {
    const settings = this.appContext.worldInfo?.settings;
    if (!settings) return;

    // Load sun size
    if (settings.sunSize !== undefined) {
      this.sunSize = settings.sunSize;
    }

    // Load sun position
    if (settings.sunAngleY !== undefined) {
      this.currentAngleY = settings.sunAngleY;
    }
    if (settings.sunElevation !== undefined) {
      this.currentElevation = settings.sunElevation;
    }

    // Load sun color
    if (settings.sunColor) {
      this.sunColor = new Color3(
        settings.sunColor.r,
        settings.sunColor.g,
        settings.sunColor.b
      );
    }

    // Load sun enabled state
    if (settings.sunEnabled !== undefined) {
      this.enabled = settings.sunEnabled;
    }

    logger.debug('Sun parameters loaded from WorldInfo', {
      size: this.sunSize,
      angleY: this.currentAngleY,
      elevation: this.currentElevation,
      enabled: this.enabled,
    });
  }

  private async initialize(): Promise<void> {
    // Load initial parameters from WorldInfo
    this.loadParametersFromWorldInfo();

    // Create sun root node attached to camera environment root
    const cameraRoot = this.cameraService.getCameraEnvironmentRoot();
    if (!cameraRoot) {
      logger.error('Camera environment root not available');
      return;
    }

    this.sunRoot = new TransformNode('sunRoot', this.scene);
    this.sunRoot.parent = cameraRoot;

    // Load texture (from WorldInfo or fallback to procedural)
    await this.loadSunTexture();

    // Create material
    this.sunMaterial = new StandardMaterial('sunMaterial', this.scene);
    this.sunMaterial.diffuseTexture = this.sunTexture!;
    this.sunMaterial.emissiveTexture = this.sunTexture!;
    this.sunMaterial.emissiveColor = this.sunColor;
    this.sunMaterial.disableLighting = true;
    this.sunMaterial.opacityTexture = this.sunTexture!;
    this.sunMaterial.useAlphaFromDiffuseTexture = false;
    this.sunMaterial.backFaceCulling = false;

    // Create sun billboard mesh (base size 80, then scaled by sunSize factor)
    this.sunMesh = PlaneBuilder.CreatePlane('sun', { size: 80 }, this.scene);
    this.sunMesh.parent = this.sunRoot;
    this.sunMesh.material = this.sunMaterial;
    this.sunMesh.billboardMode = Mesh.BILLBOARDMODE_ALL;
    this.sunMesh.infiniteDistance = true; // Always at horizon
    this.sunMesh.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;

    // Apply size scaling
    this.sunMesh.scaling.setAll(this.sunSize);

    // Set initial position
    this.updateSunPosition();

    // Create lens flare system
    await this.createLensFlareSystem();

    logger.debug('SunService initialized', {
      angleY: this.currentAngleY,
      elevation: this.currentElevation,
      lensFlareEnabled: this.lensFlareEnabled,
    });
  }

  /**
   * Create lens flare system attached to sun mesh
   */
  private async createLensFlareSystem(): Promise<void> {
    if (!this.sunMesh) {
      logger.error('Cannot create lens flare system: sun mesh not available');
      return;
    }

    // Create lens flare system with sun mesh as emitter
    this.lensFlareSystem = new LensFlareSystem('sunLensFlare', this.sunMesh, this.scene);

    // Get lens flare texture URL from asset server with credentials
    let flareTextureUrl: string;
    if (this.networkService) {
      const textureUrl = this.networkService.getAssetUrl(this.lensFlareTexture);
      // Load texture with credentials
      flareTextureUrl = await loadTextureUrlWithCredentials(textureUrl);
    } else {
      // Fallback to data URL if network service not available
      flareTextureUrl = this.createLensFlareTextureDataUrl();
      logger.warn('NetworkService not available, using fallback lens flare texture');
    }

    // Add multiple flares at different positions along the flare axis
    // Position 0 = at emitter, 1 = opposite side, 0.5 = middle
    // Create flares immediately like in the Babylon.js example

    // Main bright flare at sun position (reduced intensity)
    new LensFlare(0.3, 0, this.lensFlareColor.scale(0.5), flareTextureUrl, this.lensFlareSystem);

    // Secondary flares along the axis (reduced intensity)
    new LensFlare(0.1, 0.2, new Color3(0.5, 0.4, 0.3), flareTextureUrl, this.lensFlareSystem);
    new LensFlare(0.15, 0.4, new Color3(0.45, 0.35, 0.25), flareTextureUrl, this.lensFlareSystem);
    new LensFlare(0.08, 0.6, new Color3(0.5, 0.45, 0.35), flareTextureUrl, this.lensFlareSystem);
    new LensFlare(0.12, 0.8, new Color3(0.4, 0.3, 0.2), flareTextureUrl, this.lensFlareSystem);
    new LensFlare(0.2, 1.0, new Color3(0.5, 0.425, 0.3), flareTextureUrl, this.lensFlareSystem);

    // Configure lens flare system
    this.lensFlareSystem.borderLimit = 300; // Distance from screen edge before fading
    this.lensFlareSystem.isEnabled = this.lensFlareEnabled;

    // Set lens flare rendering group - only occlude by world meshes
    this.lensFlareSystem.meshesSelectionPredicate = (mesh) => {
      return mesh.renderingGroupId === RENDERING_GROUPS.ENVIRONMENT;
    };

    logger.debug('Lens flare system created', {
      textureUrl: flareTextureUrl,
      enabled: this.lensFlareEnabled
    });
  }

  /**
   * Create data URL for lens flare texture (fallback if NetworkService unavailable)
   */
  private createLensFlareTextureDataUrl(): string {
    const size = 128;
    const center = size / 2;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    // Create radial gradient
    const gradient = ctx.createRadialGradient(center, center, 0, center, center, center);
    gradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
    gradient.addColorStop(0.5, 'rgba(255, 255, 255, 0.5)');
    gradient.addColorStop(1, 'rgba(255, 255, 255, 0)');

    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, size, size);

    return canvas.toDataURL();
  }

  /**
   * Load sun texture from WorldInfo or use default
   */
  private async loadSunTexture(): Promise<void> {

    if (this.networkService) {
      try {
        const texturePath = this.appContext.worldInfo?.settings?.sunTexture || 'w:textures/sun/sun1.png';
        // Load texture from asset server with credentials
        const textureUrl = this.networkService.getAssetUrl(texturePath);
        const blobUrl = await loadTextureUrlWithCredentials(textureUrl);

        this.sunTexture = new Texture(
          blobUrl,
          this.scene,
          false, // noMipmap
          true, // invertY
          Constants.TEXTURE_TRILINEAR_SAMPLINGMODE,
          () => {
            logger.debug('Sun texture loaded', { path: texturePath });
          },
          (message) => {
            logger.error('Failed to load sun texture, using fallback', { path: texturePath, error: message });
            this.sunTexture = this.createFallbackTexture();
            if (this.sunMaterial) {
              this.sunMaterial.diffuseTexture = this.sunTexture;
              this.sunMaterial.emissiveTexture = this.sunTexture;
              this.sunMaterial.opacityTexture = this.sunTexture;
            }
          }
        );
        this.sunTexture.hasAlpha = true;
      } catch (error) {
        logger.error('Error loading sun texture, using fallback', { error });
        this.sunTexture = this.createFallbackTexture();
      }
    } else {
      // Use fallback circular disc if network service not available
      this.sunTexture = this.createFallbackTexture();
      logger.debug('Using fallback circular sun texture (no NetworkService)');
    }
  }

  /**
   * Create simple circular disc texture as fallback
   */
  private createFallbackTexture(): RawTexture {
    const size = 256;
    const center = size / 2;
    const radius = size / 2 - 10;
    const textureData = new Uint8Array(size * size * 4);

    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const dx = x - center;
        const dy = y - center;
        const dist = Math.sqrt(dx * dx + dy * dy);

        let alpha = 0;

        if (dist < radius) {
          // Smooth edge falloff
          const edgeDist = radius - dist;
          if (edgeDist < 10) {
            alpha = edgeDist / 10; // Soft edge
          } else {
            alpha = 1.0; // Full opacity
          }
        }

        const idx = (y * size + x) * 4;
        textureData[idx] = 255; // R
        textureData[idx + 1] = 255; // G
        textureData[idx + 2] = 255; // B
        textureData[idx + 3] = Math.floor(alpha * 255); // A
      }
    }

    return RawTexture.CreateRGBATexture(
      textureData,
      size,
      size,
      this.scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );
  }

  /**
   * Set sun texture from asset path
   * @param texturePath Path to texture (will be loaded via NetworkService.getAssetUrl)
   */
  async setSunTexture(texturePath: string | null): Promise<void> {
    if (!texturePath) {
      // Use fallback
      this.sunTexture?.dispose();
      this.sunTexture = this.createFallbackTexture();
      if (this.sunMaterial) {
        this.sunMaterial.diffuseTexture = this.sunTexture;
        this.sunMaterial.emissiveTexture = this.sunTexture;
        this.sunMaterial.opacityTexture = this.sunTexture;
      }
      logger.debug('Sun texture reset to fallback');
      return;
    }

    if (!this.networkService) {
      logger.error('NetworkService not available, cannot load texture');
      return;
    }

    try {
      const textureUrl = this.networkService.getAssetUrl(texturePath);

      // Dispose old texture
      this.sunTexture?.dispose();

      // Load texture with credentials
      const blobUrl = await loadTextureUrlWithCredentials(textureUrl);

      // Load new texture
      this.sunTexture = new Texture(
        blobUrl,
        this.scene,
        false,
        true,
        Constants.TEXTURE_TRILINEAR_SAMPLINGMODE,
        () => {
          logger.debug('Sun texture loaded', { path: texturePath });
          if (this.sunMaterial) {
            this.sunMaterial.diffuseTexture = this.sunTexture!;
            this.sunMaterial.emissiveTexture = this.sunTexture!;
            this.sunMaterial.opacityTexture = this.sunTexture!;
          }
        },
        (message) => {
          logger.error('Failed to load sun texture', { path: texturePath, error: message });
          this.sunTexture = this.createFallbackTexture();
          if (this.sunMaterial) {
            this.sunMaterial.diffuseTexture = this.sunTexture;
            this.sunMaterial.emissiveTexture = this.sunTexture;
            this.sunMaterial.opacityTexture = this.sunTexture;
          }
        }
      );
      this.sunTexture.hasAlpha = true;
    } catch (error) {
      logger.error('Error loading sun texture', { error });
    }
  }

  /**
   * Set sun position on circular orbit around camera using Y-axis angle
   * @param angleY Horizontal angle in degrees (0=North, 90=East, 180=South, 270=West)
   */
  setSunPositionOnCircle(angleY: number): void {
    this.currentAngleY = angleY;
    this.updateSunPosition();
  }

  /**
   * Set sun height (elevation) over camera
   * @param elevation Vertical angle in degrees (-90=down, 0=horizon, 90=up)
   */
  setSunHeightOverCamera(elevation: number): void {
    this.currentElevation = elevation;
    this.updateSunPosition();
  }

  /**
   * Update sun root position based on current angleY and elevation
   */
  private updateSunPosition(): void {
    if (!this.sunRoot) return;

    // Convert to radians
    const angleYRad = this.currentAngleY * (Math.PI / 180);
    const elevationRad = this.currentElevation * (Math.PI / 180);

    // Calculate position on sphere (relative to camera)
    const y = this.orbitRadius * Math.sin(elevationRad);
    const horizontalDist = this.orbitRadius * Math.cos(elevationRad);
    const x = horizontalDist * Math.sin(angleYRad);
    const z = horizontalDist * Math.cos(angleYRad);

    this.sunRoot.position.set(x, y, z);

    // Apply automatic light adjustments if enabled
    if (this.automaticSunAdjustment) {
      this.updateAutomaticLighting();
    }

    logger.debug('Sun position updated', {
      angleY: this.currentAngleY,
      elevation: this.currentElevation,
      position: { x, y, z },
      automaticAdjustment: this.automaticSunAdjustment,
    });
  }

  /**
   * Update sun light and ambient light based on sun position
   * Automatically adjusts direction and intensity based on elevation
   */
  private updateAutomaticLighting(): void {
    const environmentService = this.appContext.services.environment;
    if (!environmentService) {
      return;
    }

    // Convert angles to radians
    const angleYRad = this.currentAngleY * (Math.PI / 180);
    const elevationRad = this.currentElevation * (Math.PI / 180);

    // Calculate sun light direction (pointing from sun position to origin)
    // This creates shadows as if light is coming from the sun
    const dirX = -Math.cos(elevationRad) * Math.sin(angleYRad);
    const dirY = -Math.sin(elevationRad);
    const dirZ = -Math.cos(elevationRad) * Math.cos(angleYRad);

    // Set sun light direction
    environmentService.setSunLightDirection(dirX, dirY, dirZ);

    // Calculate intensity based on elevation
    // Elevation range: -90° (below horizon) to 90° (zenith)
    // Intensity: 0 when below horizon, max at zenith
    const elevationNormalized = Math.max(0, this.currentElevation / 90); // 0-1 range

    // Apply smooth curve for more realistic light falloff
    // Use squared value for softer sunrise/sunset
    const intensityFactor = Math.pow(elevationNormalized, 0.5);

    // Calculate sun light intensity with multiplier
    const sunLightIntensity = intensityFactor * this.sunLightIntensityMultiplier;
    environmentService.setSunLightIntensity(sunLightIntensity);

    // Calculate ambient light intensity with multiplier
    // Ambient light should be softer and always present
    const ambientBaseIntensity = 0.3 + (intensityFactor * 0.7); // Range: 0.3-1.0
    const ambientLightIntensity = ambientBaseIntensity * this.ambientLightIntensityMultiplier;
    environmentService.setAmbientLightIntensity(ambientLightIntensity);

    logger.debug('Automatic lighting updated', {
      elevation: this.currentElevation,
      angleY: this.currentAngleY,
      sunLightDirection: { x: dirX, y: dirY, z: dirZ },
      sunLightIntensity,
      ambientLightIntensity,
      intensityFactor,
    });
  }

  /**
   * Set sun color
   * @param r Red component (0-1)
   * @param g Green component (0-1)
   * @param b Blue component (0-1)
   */
  setSunColor(r: number, g: number, b: number): void {
    this.sunColor = new Color3(r, g, b);

    if (this.sunMaterial) {
      this.sunMaterial.emissiveColor = this.sunColor;
    }

    logger.debug('Sun color updated', { r, g, b });
  }

  /**
   * Set sun size
   * @param size Billboard scaling factor (1.0 = default size)
   */
  setSunSize(size: number): void {
    this.sunSize = size;

    if (this.sunMesh) {
      this.sunMesh.scaling.setAll(size);
    }

    logger.debug('Sun size updated', { size });
  }

  /**
   * Enable/disable sun visibility
   * @param enabled True to show sun, false to hide
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;

    if (this.sunMesh) {
      this.sunMesh.setEnabled(enabled);
    }

    // Also disable lens flare when sun is disabled
    if (this.lensFlareSystem) {
      this.lensFlareSystem.isEnabled = enabled && this.lensFlareEnabled;
    }

    logger.debug('Sun visibility changed', { enabled });
  }

  /**
   * Enable/disable lens flare effect
   * @param enabled True to show lens flare, false to hide
   */
  setSunLensFlareEnabled(enabled: boolean): void {
    this.lensFlareEnabled = enabled;

    if (this.lensFlareSystem) {
      this.lensFlareSystem.isEnabled = enabled;
    }

    logger.debug('Lens flare visibility changed', { enabled });
  }

  /**
   * Set lens flare intensity
   * @param intensity Intensity multiplier (0-2, default: 1.0)
   */
  setSunLensFlareIntensity(intensity: number): void {
    this.lensFlareIntensity = intensity;

    if (this.lensFlareSystem) {
      // Update all flares in the system
      this.lensFlareSystem.lensFlares.forEach((flare, index) => {
        // Main flare (index 0) gets full intensity
        if (index === 0) {
          flare.size = 0.3 * intensity;
        } else {
          // Secondary flares get scaled intensity
          const baseSizes = [0.1, 0.15, 0.08, 0.12, 0.2];
          flare.size = baseSizes[index - 1] * intensity;
        }
      });
    }

    logger.debug('Lens flare intensity set', { intensity });
  }

  /**
   * Set lens flare color
   * @param r Red component (0-1)
   * @param g Green component (0-1)
   * @param b Blue component (0-1)
   */
  setSunLensFlareColor(r: number, g: number, b: number): void {
    this.lensFlareColor = new Color3(r, g, b);

    if (this.lensFlareSystem && this.lensFlareSystem.lensFlares.length > 0) {
      // Update main flare color
      this.lensFlareSystem.lensFlares[0].color = this.lensFlareColor;

      // Update secondary flares with tinted variations
      if (this.lensFlareSystem.lensFlares.length > 1) {
        this.lensFlareSystem.lensFlares[1].color = new Color3(r, g * 0.9, b * 0.8);
      }
      if (this.lensFlareSystem.lensFlares.length > 2) {
        this.lensFlareSystem.lensFlares[2].color = new Color3(r * 0.95, g * 0.8, b * 0.7);
      }
      if (this.lensFlareSystem.lensFlares.length > 3) {
        this.lensFlareSystem.lensFlares[3].color = new Color3(r, g * 0.95, b * 0.85);
      }
      if (this.lensFlareSystem.lensFlares.length > 4) {
        this.lensFlareSystem.lensFlares[4].color = new Color3(r * 0.9, g * 0.75, b * 0.6);
      }
      if (this.lensFlareSystem.lensFlares.length > 5) {
        this.lensFlareSystem.lensFlares[5].color = new Color3(r, g * 0.9, b * 0.75);
      }
    }

    logger.debug('Lens flare color updated', { r, g, b });
  }

  /**
   * Get current sun position
   */
  getSunPosition(): { angleY: number; elevation: number } {
    return {
      angleY: this.currentAngleY,
      elevation: this.currentElevation,
    };
  }

  /**
   * Set automatic sun adjustment
   * @param enabled True to enable automatic light adjustment, false to disable
   */
  setAutomaticSunAdjustment(enabled: boolean): void {
    this.automaticSunAdjustment = enabled;

    // Apply adjustment immediately if enabled
    if (enabled) {
      this.updateAutomaticLighting();
    }

    logger.debug('Automatic sun adjustment changed', { enabled });
  }

  /**
   * Get automatic sun adjustment state
   */
  getAutomaticSunAdjustment(): boolean {
    return this.automaticSunAdjustment;
  }

  /**
   * Set sun light intensity multiplier
   * @param multiplier Multiplier for sun light intensity (default: 1.0)
   */
  setSunLightIntensityMultiplier(multiplier: number): void {
    this.sunLightIntensityMultiplier = multiplier;

    // Re-apply lighting if automatic adjustment is enabled
    if (this.automaticSunAdjustment) {
      this.updateAutomaticLighting();
    }

    logger.debug('Sun light intensity multiplier set', { multiplier });
  }

  /**
   * Get sun light intensity multiplier
   */
  getSunLightIntensityMultiplier(): number {
    return this.sunLightIntensityMultiplier;
  }

  /**
   * Set ambient light intensity multiplier
   * @param multiplier Multiplier for ambient light intensity (default: 0.5)
   */
  setAmbientLightIntensityMultiplier(multiplier: number): void {
    this.ambientLightIntensityMultiplier = multiplier;

    // Re-apply lighting if automatic adjustment is enabled
    if (this.automaticSunAdjustment) {
      this.updateAutomaticLighting();
    }

    logger.debug('Ambient light intensity multiplier set', { multiplier });
  }

  /**
   * Get ambient light intensity multiplier
   */
  getAmbientLightIntensityMultiplier(): number {
    return this.ambientLightIntensityMultiplier;
  }

  /**
   * Cleanup and dispose resources
   */
  dispose(): void {
    this.lensFlareSystem?.dispose();
    this.sunMesh?.dispose();
    this.sunMaterial?.dispose();
    this.sunTexture?.dispose();
    this.sunRoot?.dispose();

    logger.debug('SunService disposed');
  }
}
