/**
 * CameraService - Manages camera control
 *
 * Handles camera positioning, rotation, and view modes.
 * Initial implementation supports ego-view (first-person) only.
 */

import {
  FreeCamera,
  Vector3,
  Scene,
  MeshBuilder,
  StandardMaterial,
  Color3,
  Color4,
  Mesh,
  TransformNode,
  SpotLight,
} from '@babylonjs/core';
import { getLogger, ExceptionHandler, getStateValues } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { PlayerService } from './PlayerService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('CameraService');

/**
 * CameraService - Manages camera
 *
 * Features:
 * - First-person camera (ego-view)
 * - Position and rotation control
 * - Dynamic turn speed from PlayerInfo (updated via events)
 * - Underwater water sphere rendering
 * - Future: Third-person view support
 */
export class CameraService {
  private scene: Scene;
  private appContext: AppContext;
  private playerService?: PlayerService;

  private camera?: FreeCamera;
  private _egoView: boolean = true;

  // Camera control
  private effectiveTurnSpeed: number = 0.003; // Mouse sensitivity on land (updated via event)
  private effectiveUnderwaterTurnSpeed: number = 0.002; // Mouse sensitivity underwater (updated via event)

  // Independent camera rotation (for third-person mode)
  private cameraYaw: number = 0; // Degrees
  private cameraPitch: number = 0; // Degrees

  // Underwater effects
  private waterSphereMesh?: Mesh;
  private waterMaterial?: StandardMaterial;
  private isUnderwater: boolean = false;
  private originalFogDensity: number = 0;
  private originalFogColor: Color3 = Color3.White();

  // Fog mode (for DEAD mode and manual activation)
  private fogSphereMesh?: Mesh;
  private fogMaterial?: StandardMaterial;
  private fogIntensity: number = 0; // 0 = disabled, 0.1-1.0 = intensity

  // Camera environment root - parent for all camera-attached effects
  private cameraEnvironmentRoot?: TransformNode;

  // Camera environment root XZ - follows only X/Z position (not Y) for horizon effects
  private cameraEnvironmentRootXZ?: TransformNode;

  // Camera light (torch/flashlight)
  private cameraLight?: SpotLight;
  private cameraLightEnabled: boolean = false;
  private cameraLightIntensity: number = 1.0;
  private cameraLightRange: number = 15; // blocks
  private cameraLightColor: Color3 = new Color3(1.0, 0.65, 0.0); // Warm torch color (#FFA500)

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;

    // Initialize turn speeds from PlayerInfo default state values
    if (appContext.playerInfo) {
      const defaultValues = getStateValues(appContext.playerInfo, 'default');
      this.effectiveTurnSpeed = defaultValues.effectiveTurnSpeed;
      // Underwater uses swim state turn speed
      const swimValues = getStateValues(appContext.playerInfo, 'swim');
      this.effectiveUnderwaterTurnSpeed = swimValues.effectiveTurnSpeed;
    }

    this.initializeCamera();

    logger.debug('CameraService initialized', {
      turnSpeed: this.effectiveTurnSpeed,
    });
  }

  /**
   * Set PlayerService and subscribe to PlayerInfo updates
   *
   * Called after PlayerService is created to avoid circular dependency.
   *
   * @param playerService PlayerService instance
   */
  setPlayerService(playerService: PlayerService): void {
    this.playerService = playerService;

    // Subscribe to PlayerInfo updates
    playerService.on('playerInfo:updated', (info: import('@nimbus/shared').PlayerInfo) => {
      const defaultValues = getStateValues(info, 'default');
      this.effectiveTurnSpeed = defaultValues.effectiveTurnSpeed;
      // Underwater uses swim state turn speed
      const swimValues = getStateValues(info, 'swim');
      this.effectiveUnderwaterTurnSpeed = swimValues.effectiveTurnSpeed;
      logger.debug('CameraService: turnSpeed updated', {
        turnSpeed: this.effectiveTurnSpeed,
        underwaterTurnSpeed: this.effectiveUnderwaterTurnSpeed,
      });
    });

    logger.debug('PlayerService connected to CameraService');
  }

  /**
   * Get camera environment root node for attaching camera-relative effects
   * @returns Camera environment root transform node
   */
  getCameraEnvironmentRoot(): TransformNode | undefined {
    return this.cameraEnvironmentRoot;
  }

  /**
   * Get camera environment root XZ node for attaching horizon/ground-level effects
   * Follows only X/Z position of camera (Y stays at 0)
   * @returns Camera environment root XZ transform node
   */
  getCameraEnvironmentRootXZ(): TransformNode | undefined {
    return this.cameraEnvironmentRootXZ;
  }

  /**
   * Initialize the camera
   */
  private initializeCamera(): void {
    try {
      // Create first-person camera
      this.camera = new FreeCamera('playerCamera', new Vector3(0, 64, 0), this.scene);

      // Set camera properties
      this.camera.minZ = 0.1;

      // Load maxZ from WorldInfo or use default
      const settings = this.appContext.worldInfo?.settings;
      this.camera.maxZ = settings?.cameraMaxZ ?? 500;

      this.camera.fov = 70 * (Math.PI / 180); // 70 degrees in radians

      // Set initial rotation (looking forward)
      this.camera.rotation = new Vector3(0, 0, 0);

      // Create root node for camera-attached environment effects
      this.cameraEnvironmentRoot = new TransformNode('cameraEnvironmentRoot', this.scene);
      this.cameraEnvironmentRoot.position.copyFrom(this.camera.position);

      // Create root node for XZ-only effects (horizon, etc.)
      this.cameraEnvironmentRootXZ = new TransformNode('cameraEnvironmentRootXZ', this.scene);
      this.cameraEnvironmentRootXZ.position.set(this.camera.position.x, 0, this.camera.position.z);

      // Attach camera to canvas for input (will be controlled by InputService later)
      // this.camera.attachControl(canvas, true);

      logger.debug('Camera initialized', {
        position: this.camera.position,
        fov: this.camera.fov,
        minZ: this.camera.minZ,
        maxZ: this.camera.maxZ,
      });

      // Load camera light settings from localStorage
      this.loadCameraLightSettings();

      // Create camera light if enabled
      if (this.cameraLightEnabled) {
        this.createCameraLight();
      }
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'CameraService.initializeCamera');
    }
  }

  /**
   * Get the camera
   */
  getCamera(): FreeCamera | undefined {
    return this.camera;
  }

  /**
   * Set camera position
   *
   * @param x World X coordinate
   * @param y World Y coordinate
   * @param z World Z coordinate
   */
  setPosition(x: number, y: number, z: number): void {
    if (!this.camera) {
      logger.warn('Cannot set position: camera not initialized');
      return;
    }

    this.camera.position.set(x, y, z);
  }

  /**
   * Get camera position
   */
  getPosition(): Vector3 {
    if (!this.camera) {
      return Vector3.Zero();
    }

    return this.camera.position.clone();
  }

  /**
   * Set camera position for third-person view (orbiting around player)
   *
   * @param playerPosition Player's position
   * @param distance Distance from player
   */
  setThirdPersonPosition(playerPosition: Vector3, distance: number): void {
    if (!this.camera) {
      logger.warn('Cannot set third-person position: camera not initialized');
      return;
    }

    // Use independent camera rotation (not player rotation)
    const yawRad = this.cameraYaw * (Math.PI / 180);
    const pitchRad = this.cameraPitch * (Math.PI / 180);

    // Calculate offset from player based on camera rotation
    // Camera orbits around player
    const horizontalDistance = distance * Math.cos(pitchRad);
    const offsetX = -Math.sin(yawRad) * horizontalDistance;
    const offsetZ = -Math.cos(yawRad) * horizontalDistance;
    const offsetY = distance * Math.sin(pitchRad) + 1.5; // Height based on pitch + base height

    // Set camera position
    this.camera.position.set(
      playerPosition.x + offsetX,
      playerPosition.y + offsetY,
      playerPosition.z + offsetZ
    );

    // Camera rotation is already set in rotate() method
    // No need to call setTarget() - rotation is independent
  }

  /**
   * Set camera rotation
   *
   * @param pitch Pitch (X rotation) in radians
   * @param yaw Yaw (Y rotation) in radians
   * @param roll Roll (Z rotation) in radians (usually 0)
   */
  setRotation(pitch: number, yaw: number, roll: number = 0): void {
    if (!this.camera) {
      logger.warn('Cannot set rotation: camera not initialized');
      return;
    }

    this.camera.rotation.set(pitch, yaw, roll);
  }

  /**
   * Get camera rotation
   */
  getRotation(): Vector3 {
    if (!this.camera) {
      return Vector3.Zero();
    }

    return this.camera.rotation.clone();
  }

  /**
   * Get camera yaw (in degrees)
   */
  getCameraYaw(): number {
    return this.cameraYaw;
  }

  /**
   * Get camera pitch (in degrees)
   */
  getCameraPitch(): number {
    return this.cameraPitch;
  }

  /**
   * Rotate camera by delta
   *
   * Applies effectiveTurnSpeed scaling for player-controlled sensitivity.
   * Uses separate underwater turn speed for realistic underwater feel.
   *
   * @param deltaPitch Pitch delta (from mouse movement)
   * @param deltaYaw Yaw delta (from mouse movement)
   */
  rotate(deltaPitch: number, deltaYaw: number): void {
    if (!this.camera) {
      return;
    }

    // Use appropriate turn speed based on underwater state
    const turnSpeed = this.isUnderwater
      ? this.effectiveUnderwaterTurnSpeed // Slower/more realistic underwater
      : this.effectiveTurnSpeed;          // Normal on land

    // Apply effective turn speed scaling (for dynamic sensitivity control)
    // Note: RotationHandlers already applies a base sensitivity,
    // this adds player-specific sensitivity from PlayerInfo
    const scaleFactor = turnSpeed / 0.003; // Normalize against default

    const scaledDeltaPitch = deltaPitch * scaleFactor;
    const scaledDeltaYaw = deltaYaw * scaleFactor;

    // Update independent camera rotation (in degrees for easier handling)
    this.cameraYaw += scaledDeltaYaw * (180 / Math.PI); // Convert to degrees
    this.cameraPitch += scaledDeltaPitch * (180 / Math.PI);

    // Clamp pitch to prevent camera flip
    const maxPitchDeg = 89; // Slightly less than 90 degrees
    this.cameraPitch = Math.max(-maxPitchDeg, Math.min(maxPitchDeg, this.cameraPitch));

    // Apply to camera (convert back to radians)
    this.camera.rotation.x = this.cameraPitch * (Math.PI / 180);
    this.camera.rotation.y = this.cameraYaw * (Math.PI / 180);
  }

  /**
   * Check if camera is in ego-view (first-person)
   */
  get egoView(): boolean {
    return this._egoView;
  }

  /**
   * Set ego-view mode
   *
   * Note: Third-person view is not implemented yet
   */
  set egoView(value: boolean) {
    if (!value) {
      logger.warn('Third-person view not implemented yet');
      return;
    }

    this._egoView = value;
  }

  /**
   * Update camera (called each frame if needed)
   *
   * Updates environment root position - all child effects follow automatically
   */
  update(deltaTime: number): void {
    // Update environment root position - all children follow automatically
    if (this.cameraEnvironmentRoot && this.camera) {
      this.cameraEnvironmentRoot.position.copyFrom(this.camera.position);
    }

    // Update XZ-only environment root - horizon effects stay at ground level
    if (this.cameraEnvironmentRootXZ && this.camera) {
      this.cameraEnvironmentRootXZ.position.set(this.camera.position.x, 0, this.camera.position.z);
    }

    // Update camera light direction (follows camera view direction)
    if (this.cameraLight && this.camera) {
      const direction = this.camera.getDirection(new Vector3(0, 0, 1));
      this.cameraLight.direction = direction;
    }
  }

  /**
   * Set underwater state and render water sphere
   *
   * When underwater (below waterHeight in ClientHeightData):
   * - Renders a translucent water sphere around the camera
   * - Adds blue-tinted fog effect
   * - Reduces visibility for underwater atmosphere
   *
   * @param underwater True if camera is underwater
   */
  setUnderwater(underwater: boolean): void {
    try {
      // No state change, skip
      if (this.isUnderwater === underwater) {
        return;
      }

      this.isUnderwater = underwater;

      if (underwater) {
        this.enableUnderwaterEffects();
      } else {
        this.disableUnderwaterEffects();
      }

      // Notify PlayerService for auto ego-view switch
      const playerService = this.appContext.services.player;
      if (playerService) {
        playerService.setUnderwaterViewMode(underwater);
      }

      logger.debug('Underwater state changed', { underwater });
    } catch (error) {
      ExceptionHandler.handle(error, 'CameraService.setUnderwater', { underwater });
    }
  }

  /**
   * Enable underwater visual effects
   */
  private enableUnderwaterEffects(): void {
    if (!this.camera) {
      logger.warn('💧 Cannot enable underwater effects: camera not initialized');
      return;
    }

    logger.debug('💧 ENABLING UNDERWATER EFFECTS');

    // Create water sphere mesh around camera
    if (!this.waterSphereMesh) {
      this.waterSphereMesh = MeshBuilder.CreateSphere(
        'waterSphere',
        {
          diameter: 8, // 8 block radius around camera
          segments: 16, // Lower segments for performance
        },
        this.scene
      );

      // Flip normals so sphere is visible from inside
      this.waterSphereMesh.flipFaces(true);

      // Set rendering group for camera decorators
      this.waterSphereMesh.renderingGroupId = RENDERING_GROUPS.CAM_DECORATORS;

      // Create water material
      this.waterMaterial = new StandardMaterial('waterMaterial', this.scene);
      this.waterMaterial.diffuseColor = new Color3(0.1, 0.3, 0.6); // Blue color
      this.waterMaterial.alpha = 0.3; // Semi-transparent
      this.waterMaterial.backFaceCulling = false; // Render from inside

      this.waterSphereMesh.material = this.waterMaterial;

      // Attach to environment root (follows camera automatically)
      if (this.cameraEnvironmentRoot) {
        this.waterSphereMesh.parent = this.cameraEnvironmentRoot;
      }

      logger.debug('💧 Water sphere created', {
        position: this.waterSphereMesh.position,
        diameter: 8,
        material: {
          color: this.waterMaterial.diffuseColor,
          alpha: this.waterMaterial.alpha,
        },
      });
    }

    // Show water sphere
    this.waterSphereMesh.isVisible = true;
    logger.debug('💧 Water sphere visible:', this.waterSphereMesh.isVisible);

    // Store original fog settings
    this.originalFogDensity = this.scene.fogDensity;
    this.originalFogColor = this.scene.fogColor.clone();

    // Enable fog with blue tint
    this.scene.fogMode = Scene.FOGMODE_EXP2;
    this.scene.fogDensity = 0.05; // Moderate fog density
    this.scene.fogColor = new Color3(0.1, 0.4, 0.7); // Blue fog

    // Optionally reduce ambient light
    // this.scene.ambientColor = new Color3(0.3, 0.4, 0.5);

    logger.debug('💧 Underwater effects enabled', {
      fogMode: this.scene.fogMode,
      fogDensity: this.scene.fogDensity,
      fogColor: { r: this.scene.fogColor.r, g: this.scene.fogColor.g, b: this.scene.fogColor.b },
      sphereVisible: this.waterSphereMesh.isVisible,
    });
  }

  /**
   * Disable underwater visual effects
   */
  private disableUnderwaterEffects(): void {
    logger.debug('💧 DISABLING UNDERWATER EFFECTS');

    // Hide water sphere
    if (this.waterSphereMesh) {
      this.waterSphereMesh.isVisible = false;
      logger.debug('💧 Water sphere hidden');
    }

    // Restore original fog settings
    this.scene.fogMode = Scene.FOGMODE_NONE;
    this.scene.fogDensity = this.originalFogDensity;
    this.scene.fogColor = this.originalFogColor;

    // Restore ambient light
    // this.scene.ambientColor = new Color3(1, 1, 1);

    logger.debug('💧 Underwater effects disabled', {
      fogMode: this.scene.fogMode,
      fogRestored: true,
    });
  }

  /**
   * Set fog mode intensity
   *
   * When intensity > 0:
   * - Renders a translucent fog sphere around the camera
   * - Adds gray-tinted fog effect
   * - Higher intensity = worse visibility
   *
   * @param intensity Fog intensity (0 = disabled, 0.1-1.0 = intensity level)
   */
  setFogMode(intensity: number): void {
    try {
      // Clamp intensity to valid range
      intensity = Math.max(0, Math.min(1.0, intensity));

      // No state change, skip
      if (this.fogIntensity === intensity) {
        return;
      }

      const wasEnabled = this.fogIntensity > 0;
      const nowEnabled = intensity > 0;

      this.fogIntensity = intensity;

      if (nowEnabled && !wasEnabled) {
        // Enabling fog
        this.enableFogEffects(intensity);
      } else if (!nowEnabled && wasEnabled) {
        // Disabling fog
        this.disableFogEffects();
      } else if (nowEnabled) {
        // Intensity changed, update fog
        this.updateFogIntensity(intensity);
      }

      logger.debug('Fog mode intensity changed', { intensity, enabled: nowEnabled });
    } catch (error) {
      ExceptionHandler.handle(error, 'CameraService.setFogMode', { intensity });
    }
  }

  /**
   * Enable fog visual effects with given intensity
   *
   * @param intensity Fog intensity (0.1-1.0)
   */
  private enableFogEffects(intensity: number): void {
    logger.debug('🌫️ ENABLING FOG EFFECTS', { intensity });

    if (!this.camera) {
      logger.warn('Cannot enable fog effects: camera not initialized');
      return;
    }

    // Create fog sphere if not exists
    if (!this.fogSphereMesh) {
      this.fogSphereMesh = MeshBuilder.CreateSphere(
        'fogSphere',
        {
          diameter: 50, // Large sphere around camera
          segments: 16,
        },
        this.scene
      );

      // Flip faces to render inside
      this.fogSphereMesh.flipFaces(true);

      // Set rendering group for camera decorators
      this.fogSphereMesh.renderingGroupId = RENDERING_GROUPS.CAM_DECORATORS;

      // Create fog material (gray/white translucent)
      this.fogMaterial = new StandardMaterial('fogMaterial', this.scene);
      this.fogMaterial.diffuseColor = new Color3(0.8, 0.8, 0.8); // Light gray
      this.fogMaterial.backFaceCulling = false;

      this.fogSphereMesh.material = this.fogMaterial;

      // Attach to environment root (follows camera automatically)
      if (this.cameraEnvironmentRoot) {
        this.fogSphereMesh.parent = this.cameraEnvironmentRoot;
      }

      logger.debug('🌫️ Fog sphere created', {
        position: this.fogSphereMesh.position,
      });
    }

    // Show fog sphere
    this.fogSphereMesh.isVisible = true;

    // Update intensity
    this.updateFogIntensity(intensity);

    logger.debug('🌫️ Fog effects enabled', {
      intensity,
      sphereVisible: this.fogSphereMesh.isVisible,
    });
  }

  /**
   * Update fog intensity (density and sphere alpha)
   *
   * @param intensity Fog intensity (0.1-1.0)
   */
  private updateFogIntensity(intensity: number): void {
    // Store original fog settings if not already stored
    if (!this.isUnderwater && this.scene.fogMode === Scene.FOGMODE_NONE) {
      this.originalFogDensity = this.scene.fogDensity;
      this.originalFogColor = this.scene.fogColor.clone();
    }

    // Map intensity to fog density (0.02 - 0.15)
    const fogDensity = 0.02 + intensity * 0.13;

    // Map intensity to sphere alpha (0.05 - 0.25)
    const sphereAlpha = 0.05 + intensity * 0.2;

    // Enable fog with gray tint
    this.scene.fogMode = Scene.FOGMODE_EXP2;
    this.scene.fogDensity = fogDensity;
    this.scene.fogColor = new Color3(0.5, 0.5, 0.5); // Gray fog

    // Update sphere alpha
    if (this.fogMaterial) {
      this.fogMaterial.alpha = sphereAlpha;
    }

    logger.debug('🌫️ Fog intensity updated', {
      intensity,
      fogDensity,
      sphereAlpha,
      fogColor: { r: this.scene.fogColor.r, g: this.scene.fogColor.g, b: this.scene.fogColor.b },
    });
  }

  /**
   * Disable fog visual effects
   */
  private disableFogEffects(): void {
    logger.debug('🌫️ DISABLING FOG EFFECTS');

    // Hide fog sphere
    if (this.fogSphereMesh) {
      this.fogSphereMesh.isVisible = false;
      logger.debug('🌫️ Fog sphere hidden');
    }

    // Restore original fog settings (only if not underwater)
    if (!this.isUnderwater) {
      this.scene.fogMode = Scene.FOGMODE_NONE;
      this.scene.fogDensity = this.originalFogDensity;
      this.scene.fogColor = this.originalFogColor;
    }

    logger.debug('🌫️ Fog effects disabled', {
      fogMode: this.scene.fogMode,
      fogRestored: !this.isUnderwater,
    });
  }

  /**
   * Load camera light settings from localStorage
   * Note: Camera light settings are NOT persisted - always use defaults
   */
  private loadCameraLightSettings(): void {
    // Camera light settings are not loaded from localStorage
    // Always use default values defined in constructor
    logger.debug('Camera light using default settings (not loaded from localStorage)', {
      enabled: this.cameraLightEnabled,
      intensity: this.cameraLightIntensity,
      range: this.cameraLightRange,
    });
  }

  /**
   * Save camera light settings to localStorage
   * Note: Camera light settings are NOT persisted - settings are temporary per session
   */
  private saveCameraLightSettings(): void {
    // Camera light settings are not saved to localStorage
    // Settings are temporary and reset to defaults on each restart
    logger.debug('Camera light settings NOT saved (temporary per session)');
  }

  /**
   * Create camera light (torch/flashlight)
   * Creates a SpotLight attached to the camera that points in camera direction
   */
  private createCameraLight(): void {
    if (!this.camera) {
      logger.warn('Cannot create camera light: camera not initialized');
      return;
    }

    if (this.cameraLight) {
      logger.info('Camera light already exists');
      return;
    }

    // Create SpotLight at origin (will be positioned via parent)
    // Direction will be updated every frame in update() method
    const position = Vector3.Zero(); // Local position relative to parent
    const direction = new Vector3(0, 0, 1); // Forward direction (will be updated)

    this.cameraLight = new SpotLight(
      'cameraLight',
      position,
      direction,
      1.5, // Angle in radians (~86 degrees, wide torch cone)
      2, // Exponent (controls falloff at edges)
      this.scene
    );

    // Set light properties
    this.cameraLight.diffuse = this.cameraLightColor;
    this.cameraLight.specular = this.cameraLightColor;
    this.cameraLight.intensity = this.cameraLightIntensity;
    this.cameraLight.range = this.cameraLightRange;

    // Attach to camera environment root (follows camera automatically)
    if (this.cameraEnvironmentRoot) {
      this.cameraLight.parent = this.cameraEnvironmentRoot;
    }

    logger.info('Camera light created', {
      intensity: this.cameraLightIntensity,
      range: this.cameraLightRange,
      color: this.cameraLightColor,
    });
  }

  /**
   * Dispose camera light
   */
  private disposeCameraLight(): void {
    if (this.cameraLight) {
      this.cameraLight.dispose();
      this.cameraLight = undefined;
      logger.debug('Camera light disposed');
    }
  }

  /**
   * Enable or disable camera light (torch)
   *
   * @param enabled True to enable, false to disable
   */
  setCameraLightEnabled(enabled: boolean): void {
    if (this.cameraLightEnabled === enabled) {
      return; // No change
    }

    this.cameraLightEnabled = enabled;

    if (enabled) {
      this.createCameraLight();
    } else {
      this.disposeCameraLight();
    }

    this.saveCameraLightSettings();
    logger.info('Camera light ' + (enabled ? 'enabled' : 'disabled'));
  }

  /**
   * Get camera light enabled state
   */
  isCameraLightEnabled(): boolean {
    return this.cameraLightEnabled;
  }

  /**
   * Set camera light intensity
   *
   * @param intensity Light intensity (0-10, typical 0.5-2.0)
   */
  setCameraLightIntensity(intensity: number): void {
    this.cameraLightIntensity = Math.max(0, intensity);

    if (this.cameraLight) {
      this.cameraLight.intensity = this.cameraLightIntensity;
    }

    this.saveCameraLightSettings();
    logger.info('Camera light intensity set', { intensity: this.cameraLightIntensity });
  }

  /**
   * Get camera light intensity
   */
  getCameraLightIntensity(): number {
    return this.cameraLightIntensity;
  }

  /**
   * Set camera light range (in blocks)
   *
   * @param range Light range/radius in blocks (typical 5-30)
   */
  setCameraLightRange(range: number): void {
    this.cameraLightRange = Math.max(1, range);

    if (this.cameraLight) {
      this.cameraLight.range = this.cameraLightRange;
    }

    this.saveCameraLightSettings();
    logger.info('Camera light range set', { range: this.cameraLightRange });
  }

  /**
   * Get camera light range
   */
  getCameraLightRange(): number {
    return this.cameraLightRange;
  }

  /**
   * Get camera light info for debugging
   */
  getCameraLightInfo(): {
    enabled: boolean;
    intensity: number;
    range: number;
    color: { r: number; g: number; b: number };
  } {
    return {
      enabled: this.cameraLightEnabled,
      intensity: this.cameraLightIntensity,
      range: this.cameraLightRange,
      color: {
        r: this.cameraLightColor.r,
        g: this.cameraLightColor.g,
        b: this.cameraLightColor.b,
      },
    };
  }

  /**
   * Dispose camera and underwater effects
   */
  dispose(): void {
    // Dispose water sphere
    if (this.waterSphereMesh) {
      this.waterSphereMesh.dispose();
      this.waterSphereMesh = undefined;
    }

    // Dispose water material
    if (this.waterMaterial) {
      this.waterMaterial.dispose();
      this.waterMaterial = undefined;
    }

    // Dispose fog sphere
    if (this.fogSphereMesh) {
      this.fogSphereMesh.dispose();
      this.fogSphereMesh = undefined;
    }

    // Dispose fog material
    if (this.fogMaterial) {
      this.fogMaterial.dispose();
      this.fogMaterial = undefined;
    }

    // Dispose camera light
    this.disposeCameraLight();

    // Dispose environment root (automatically disposes all children)
    if (this.cameraEnvironmentRoot) {
      this.cameraEnvironmentRoot.dispose();
      this.cameraEnvironmentRoot = undefined;
    }

    // Dispose camera
    this.camera?.dispose();

    logger.debug('Camera disposed');
  }
}
