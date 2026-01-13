/**
 * HorizonGradientService - Manages horizon gradient box visualization
 *
 * Creates a box without top and bottom (only 4 vertical sides) around the horizon
 * with a vertical color gradient effect.
 */

import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Color3,
  Vector3,
  VertexBuffer,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('HorizonGradientService');

/**
 * HorizonGradientService - Manages horizon gradient box for atmospheric depth
 *
 * Features:
 * - Box without top and bottom (4 vertical sides only)
 * - Vertical gradient from color0 (bottom) to color1 (top)
 * - Configurable distance, height, and Y position
 * - Transparency support
 * - Attached to camera (follows camera movement)
 * - WorldInfo integration
 */
export class HorizonGradientService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;

  // Mesh components
  private horizonRoot?: TransformNode;
  private planes: Mesh[] = [];
  private materials: StandardMaterial[] = [];

  // Configuration
  private enabled: boolean = false;
  private distance: number = 300; // Distance on XZ plane from camera
  private y: number = 0; // Y position of bottom edge
  private height: number = 100; // Height of vertical sides
  private color0: Color3 = new Color3(0.7, 0.8, 0.9); // Bottom color (light blue)
  private color1: Color3 = new Color3(0.3, 0.5, 0.8); // Top color (deep blue)
  private alpha: number = 0.5; // Transparency
  private illuminationColor?: Color3; // Illumination/glow color (undefined = no illumination)
  private illuminationStrength: number = 1.0; // Illumination intensity multiplier

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;

    this.initialize();
  }

  /**
   * Load initial parameters from WorldInfo settings
   */
  private loadParametersFromWorldInfo(): void {
    const settings = this.appContext.worldInfo?.settings;
    if (!settings?.horizonGradient) return;

    const config = settings.horizonGradient;

    this.enabled = config.enabled ?? false;
    this.distance = config.distance ?? 300;
    this.y = config.y ?? 0;
    this.height = config.height ?? 100;

    if (config.color0) {
      this.color0 = new Color3(config.color0.r, config.color0.g, config.color0.b);
    }

    if (config.color1) {
      this.color1 = new Color3(config.color1.r, config.color1.g, config.color1.b);
    }

    if (config.alpha !== undefined) {
      this.alpha = config.alpha;
    }

    // Load illumination settings
    if (config.illumination?.color) {
      const illumColor = config.illumination.color;
      this.illuminationColor = new Color3(illumColor.r, illumColor.g, illumColor.b);
    }

    if (config.illumination?.strength !== undefined) {
      this.illuminationStrength = config.illumination.strength;
    }

    logger.debug('Horizon gradient parameters loaded from WorldInfo', {
      enabled: this.enabled,
      distance: this.distance,
      y: this.y,
      height: this.height,
      color0: { r: this.color0.r, g: this.color0.g, b: this.color0.b },
      color1: { r: this.color1.r, g: this.color1.g, b: this.color1.b },
      alpha: this.alpha,
      illuminationColor: this.illuminationColor ? { r: this.illuminationColor.r, g: this.illuminationColor.g, b: this.illuminationColor.b } : undefined,
      illuminationStrength: this.illuminationStrength,
    });
  }

  /**
   * Initialize the service
   */
  private initialize(): void {
    // Load parameters from WorldInfo
    this.loadParametersFromWorldInfo();

    // Get camera environment root XZ (follows only X/Z, not Y)
    const cameraRootXZ = this.cameraService.getCameraEnvironmentRootXZ();
    if (!cameraRootXZ) {
      logger.error('Camera environment root XZ not available');
      return;
    }

    // Create root node attached to camera environment root XZ
    this.horizonRoot = new TransformNode('horizonGradientRoot', this.scene);
    this.horizonRoot.parent = cameraRootXZ;

    // Create horizon box
    this.createHorizonBox();

    // Set enabled state
    this.setEnabled(this.enabled);

    logger.debug('HorizonGradientService initialized', {
      enabled: this.enabled,
      distance: this.distance,
      height: this.height,
    });
  }

  /**
   * Create the horizon box with 4 vertical planes
   */
  private createHorizonBox(): void {
    if (!this.horizonRoot) return;

    // Dispose existing planes and materials
    this.planes.forEach((plane) => plane.dispose());
    this.materials.forEach((material) => material.dispose());
    this.planes = [];
    this.materials = [];

    // Plane configurations (North, East, South, West)
    const planeConfigs = [
      {
        name: 'horizonNorth',
        position: new Vector3(0, 0, -this.distance),
        rotationY: 0,
      },
      {
        name: 'horizonEast',
        position: new Vector3(this.distance, 0, 0),
        rotationY: Math.PI / 2,
      },
      {
        name: 'horizonSouth',
        position: new Vector3(0, 0, this.distance),
        rotationY: Math.PI,
      },
      {
        name: 'horizonWest',
        position: new Vector3(-this.distance, 0, 0),
        rotationY: -Math.PI / 2,
      },
    ];

    // Create each plane
    planeConfigs.forEach((config) => {
      // Create plane mesh
      const plane = MeshBuilder.CreatePlane(
        config.name,
        {
          width: this.distance * 2, // Full side width
          height: this.height,
        },
        this.scene
      );

      // Set parent first
      if (this.horizonRoot) {
        plane.parent = this.horizonRoot;
      }

      // Position and rotate
      plane.position.copyFrom(config.position);
      plane.position.y = this.y + this.height / 2; // Center vertically
      plane.rotation.y = config.rotationY;

      // Set rendering properties
      plane.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;
      plane.infiniteDistance = false; // Not infinite like skybox

      // Apply vertex gradient
      this.applyVertexGradient(plane);

      // Create and apply material
      const material = this.createMaterial();
      plane.material = material;

      // Store references
      this.planes.push(plane);
      this.materials.push(material);
    });

    logger.debug('Horizon box created', {
      planeCount: this.planes.length,
      distance: this.distance,
      height: this.height,
    });
  }

  /**
   * Apply vertex gradient to a mesh
   */
  private applyVertexGradient(mesh: Mesh): void {
    const positions = mesh.getVerticesData(VertexBuffer.PositionKind);
    if (!positions) return;

    const vertexCount = positions.length / 3;
    const colors: number[] = [];

    for (let i = 0; i < vertexCount; i++) {
      const yPos = positions[i * 3 + 1]; // Y component

      // Normalize Y position (0 = bottom, 1 = top)
      const t = (yPos - (-this.height / 2)) / this.height;

      // Interpolate colors
      const color = Color3.Lerp(this.color0, this.color1, t);

      // Add RGBA
      colors.push(color.r, color.g, color.b, this.alpha);
    }

    mesh.setVerticesData(VertexBuffer.ColorKind, new Float32Array(colors));
  }

  /**
   * Create material for horizon gradient planes
   */
  private createMaterial(): StandardMaterial {
    const material = new StandardMaterial('horizonGradientMaterial', this.scene);

    // Apply illumination (emissive color for glow effect)
    if (this.illuminationColor) {
      // With illumination: use emissive color scaled by strength
      material.emissiveColor = this.illuminationColor.scale(this.illuminationStrength);
      material.disableLighting = false; // Allow lighting interaction
    } else {
      // Without illumination: fully self-illuminated (original behavior)
      material.disableLighting = true; // Self-illuminated
      material.emissiveColor = Color3.White(); // Full brightness
    }

    (material as any).useVertexColors = true; // Enable vertex colors (not in TypeScript types but exists)
    material.transparencyMode = StandardMaterial.MATERIAL_ALPHABLEND;
    material.backFaceCulling = false; // Visible from inside
    material.alpha = this.alpha; // Set initial alpha from config

    return material;
  }

  /**
   * Rebuild the horizon box (when geometry changes)
   */
  private rebuildHorizonBox(): void {
    this.createHorizonBox();
    this.setEnabled(this.enabled);
    logger.debug('Horizon box rebuilt');
  }

  /**
   * Update vertex colors on all planes (when colors/alpha change)
   */
  private updateVertexColors(): void {
    this.planes.forEach((plane) => {
      this.applyVertexGradient(plane);
    });
    logger.debug('Vertex colors updated');
  }

  /**
   * Update materials on all planes (when illumination changes)
   */
  private updateMaterials(): void {
    // Dispose old materials
    this.materials.forEach((material) => material.dispose());
    this.materials = [];

    // Create and apply new materials to all planes
    this.planes.forEach((plane) => {
      const material = this.createMaterial();
      plane.material = material;
      this.materials.push(material);
    });

    logger.debug('Materials updated with new illumination settings');
  }

  /**
   * Enable/disable horizon gradient visibility
   * @param enabled True to show, false to hide
   */
  setEnabled(enabled: boolean): void {
    this.enabled = enabled;

    this.planes.forEach((plane) => {
      plane.setEnabled(enabled);
    });

    logger.debug('Horizon gradient visibility changed', { enabled });
  }

  /**
   * Set distance from camera on XZ plane
   * @param distance Distance in blocks
   */
  setDistance(distance: number): void {
    if (distance <= 0) {
      logger.warn('Distance must be positive', { distance });
      return;
    }

    this.distance = distance;
    this.rebuildHorizonBox();
    logger.debug('Horizon gradient distance updated', { distance });
  }

  /**
   * Set Y position of bottom edge
   * @param y Y position
   */
  setYPosition(y: number): void {
    this.y = y;
    this.rebuildHorizonBox();
    logger.debug('Horizon gradient Y position updated', { y });
  }

  /**
   * Set height of vertical sides
   * @param height Height in blocks
   */
  setHeight(height: number): void {
    if (height <= 0) {
      logger.warn('Height must be positive', { height });
      return;
    }

    this.height = height;
    this.rebuildHorizonBox();
    logger.debug('Horizon gradient height updated', { height });
  }

  /**
   * Set bottom color
   * @param color RGB color
   */
  setColor0(color: Color3): void {
    this.color0 = color;
    this.updateVertexColors();
    logger.debug('Horizon gradient color0 updated', {
      r: color.r,
      g: color.g,
      b: color.b,
    });
  }

  /**
   * Set top color
   * @param color RGB color
   */
  setColor1(color: Color3): void {
    this.color1 = color;
    this.updateVertexColors();
    logger.debug('Horizon gradient color1 updated', {
      r: color.r,
      g: color.g,
      b: color.b,
    });
  }

  /**
   * Set transparency
   * @param alpha Alpha value (0 = fully transparent, 1 = opaque)
   */
  setAlpha(alpha: number): void {
    if (alpha < 0 || alpha > 1) {
      logger.warn('Alpha must be between 0 and 1', { alpha });
      return;
    }

    this.alpha = alpha;

    // Update vertex colors (RGBA with new alpha)
    this.updateVertexColors();

    // Update material alpha for all planes
    this.materials.forEach((material) => {
      material.alpha = alpha;
    });

    logger.debug('Horizon gradient alpha updated', { alpha });
  }

  /**
   * Get current enabled state
   */
  isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Set illumination color
   * @param color RGB color for illumination (undefined to disable illumination)
   */
  setIlluminationColor(color: Color3 | undefined): void {
    this.illuminationColor = color;
    this.updateMaterials();
    logger.info('Horizon gradient illumination color updated', {
      color: color ? { r: color.r, g: color.g, b: color.b } : undefined,
    });
  }

  /**
   * Set illumination strength
   * @param strength Illumination intensity multiplier (0-10, typical 0.5-2.0)
   */
  setIlluminationStrength(strength: number): void {
    this.illuminationStrength = Math.max(0, strength);
    this.updateMaterials();
    logger.info('Horizon gradient illumination strength updated', { strength: this.illuminationStrength });
  }

  /**
   * Get illumination color
   */
  getIlluminationColor(): Color3 | undefined {
    return this.illuminationColor;
  }

  /**
   * Get illumination strength
   */
  getIlluminationStrength(): number {
    return this.illuminationStrength;
  }

  /**
   * Cleanup and dispose resources
   */
  dispose(): void {
    this.planes.forEach((plane) => plane.dispose());
    this.materials.forEach((material) => material.dispose());
    this.horizonRoot?.dispose();

    this.planes = [];
    this.materials = [];

    logger.debug('HorizonGradientService disposed');
  }
}
