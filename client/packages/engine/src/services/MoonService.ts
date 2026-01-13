import { getLogger } from '@nimbus/shared';
import {
  Scene,
  TransformNode,
  Mesh,
  PlaneBuilder,
  ShaderMaterial,
  Color3,
  Texture,
  Effect,
  Material,
} from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { CameraService } from './CameraService';
import { RENDERING_GROUPS } from '../config/renderingGroups';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';

const logger = getLogger('MoonService');

interface MoonInstance {
  // Configuration
  enabled: boolean;
  size: number;
  positionOnCircle: number; // 0-360°
  heightOverCamera: number; // -90 to 90°
  distance: number; // Distance from camera
  phase: number; // 0.0 (new moon) to 1.0 (full moon)
  texture?: string; // Optional texture path

  // BabylonJS objects
  root?: TransformNode;
  mesh?: Mesh;
  material?: ShaderMaterial;
  textureObject?: Texture;
}

/**
 * MoonService - Manages up to 3 moons in the sky
 *
 * Features:
 * - Billboard meshes that always face camera
 * - Shader-based moon phases (geometric, no textures needed)
 * - Positioned using spherical coordinates
 * - Attached to camera environment root
 * - Renders in ENVIRONMENT rendering group
 */
export class MoonService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;

  private moons: MoonInstance[] = [];

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;

    this.initialize();
  }

  /**
   * Register shader
   */
  private registerShaders(): void {
    // Vertex shader
    Effect.ShadersStore['moonVertexShader'] = `
      precision highp float;
      attribute vec3 position;
      attribute vec2 uv;
      uniform mat4 worldViewProjection;
      varying vec2 vUV;

      void main(void) {
        gl_Position = worldViewProjection * vec4(position, 1.0);
        vUV = uv;
      }
    `;

    // Fragment shader - moon with phases and optional texture
    Effect.ShadersStore['moonFragmentShader'] = `
      precision highp float;
      varying vec2 vUV;

      uniform float phase;
      uniform vec3 moonColor;
      uniform sampler2D moonTexture;
      uniform float hasTexture;

      void main(void) {
        // Convert UV to centered coordinates (-1 to 1)
        vec2 centered = (vUV - 0.5) * 2.0;
        float dist = length(centered);

        // Discard pixels outside the circle
        if (dist > 1.0) {
          discard;
        }

        // Base color (from texture or solid color)
        vec3 baseColor;
        float baseAlpha = 1.0;

        if (hasTexture > 0.5) {
          // Use texture (always full moon texture)
          vec4 texColor = texture2D(moonTexture, vUV);
          baseColor = texColor.rgb;
          baseAlpha = texColor.a;
        } else {
          // Use solid color
          baseColor = moonColor;
        }

        // Calculate 3D sphere position
        float x = centered.x;
        float y = centered.y;
        float z = sqrt(max(0.0, 1.0 - x * x - y * y));

        // Calculate moon phase with CURVED terminator
        // The terminator is an ellipse on the sphere surface
        // Light comes from right, terminator position depends on phase

        // Phase angle: 0 = new moon, 0.5 = half moon, 1 = full moon
        float phaseAngle = (phase - 0.5) * 3.14159; // -PI/2 to PI/2

        // The terminator is at angle phaseAngle on the sphere
        // For a point (x,y,z) on the sphere, check if it's lit
        // Lit if: x > sin(phaseAngle) when viewed from front

        // But we need the CURVED edge: the terminator is where x = sin(phaseAngle) * sqrt(1 - y^2)
        // This creates an ellipse
        float terminatorXatY = sin(phaseAngle) * sqrt(max(0.0, 1.0 - y * y));

        // Calculate if point is illuminated
        float illumination;
        float transition = 0.08;

        if (phase < 0.5) {
          // Waxing: crescent to half
          // Dark from left, ellipse on right
          illumination = smoothstep(terminatorXatY - transition, terminatorXatY + transition, x);
        } else {
          // Waning: half to full
          // Keep the ellipse shape
          illumination = smoothstep(terminatorXatY - transition, terminatorXatY + transition, x);
        }

        // Apply sphere shading (Lambert)
        float brightness = illumination * z * 1.2;

        // Final color (texture or solid color, clipped by phase)
        vec3 finalColor = baseColor * brightness;

        // Smooth alpha at edge to remove white border
        float edgeSoftness = 0.02;
        float edgeAlpha = smoothstep(1.0, 1.0 - edgeSoftness, dist);

        gl_FragColor = vec4(finalColor, baseAlpha * edgeAlpha);
      }
    `;

    logger.debug('Moon shaders registered');
  }

  /**
   * Create shader material for a moon
   */
  private createMoonMaterial(index: number): ShaderMaterial {
    const moon = this.moons[index];

    const shaderMaterial = new ShaderMaterial(
      `moon${index}Shader`,
      this.scene,
      {
        vertexSource: Effect.ShadersStore['moonVertexShader'],
        fragmentSource: Effect.ShadersStore['moonFragmentShader'],
      },
      {
        attributes: ['position', 'uv'],
        uniforms: ['worldViewProjection', 'phase', 'moonColor', 'hasTexture'],
        samplers: ['moonTexture'],
      }
    );

    // Set uniforms
    shaderMaterial.setFloat('phase', moon.phase);
    shaderMaterial.setColor3('moonColor', new Color3(0.9, 0.9, 0.95));
    shaderMaterial.setFloat('hasTexture', moon.textureObject ? 1.0 : 0.0);

    if (moon.textureObject) {
      shaderMaterial.setTexture('moonTexture', moon.textureObject);
    }

    shaderMaterial.backFaceCulling = false;

    return shaderMaterial;
  }

  /**
   * Create a moon mesh
   */
  private async createMoon(index: number): Promise<void> {
    const moon = this.moons[index];

    const cameraRoot = this.cameraService.getCameraEnvironmentRoot();

    if (!cameraRoot) {
      logger.error('Camera environment root not available');
      return;
    }

    // Create root node
    moon.root = new TransformNode(`moon${index}Root`, this.scene);
    moon.root.parent = cameraRoot;

    // Load texture if specified
    if (moon.texture) {
      const networkService = this.appContext.services.network;
      const textureUrl = networkService
        ? networkService.getAssetUrl(moon.texture)
        : moon.texture;

      // Load texture with credentials
      const blobUrl = await loadTextureUrlWithCredentials(textureUrl);
      moon.textureObject = new Texture(blobUrl, this.scene, false, true);
      moon.textureObject.hasAlpha = true;
    }

    // Create material
    moon.material = this.createMoonMaterial(index);

    // Create billboard mesh
    moon.mesh = PlaneBuilder.CreatePlane(
      `moon${index}`,
      { size: moon.size },
      this.scene
    );
    moon.mesh.parent = moon.root;
    moon.mesh.material = moon.material;
    moon.mesh.billboardMode = Mesh.BILLBOARDMODE_ALL;
    moon.mesh.infiniteDistance = true;
    moon.mesh.renderingGroupId = RENDERING_GROUPS.ENVIRONMENT;
    moon.mesh.setEnabled(moon.enabled);

    // Set initial position
    this.updateMoonPosition(index);

    logger.debug(`Moon ${index} created`, {
      size: moon.size,
      phase: moon.phase,
      enabled: moon.enabled,
      hasTexture: !!moon.textureObject,
    });
  }

  /**
   * Update moon position using spherical coordinates
   */
  private updateMoonPosition(index: number): void {
    const moon = this.moons[index];
    if (!moon.root) return;

    const angleYRad = moon.positionOnCircle * (Math.PI / 180);
    const elevationRad = moon.heightOverCamera * (Math.PI / 180);
    const distance = moon.distance;

    // Spherical coordinates
    const y = distance * Math.sin(elevationRad);
    const horizontalDist = distance * Math.cos(elevationRad);
    const x = horizontalDist * Math.sin(angleYRad);
    const z = horizontalDist * Math.cos(angleYRad);

    moon.root.position.set(x, y, z);

    logger.debug(`Moon ${index} position updated`, {
      angleY: moon.positionOnCircle,
      elevation: moon.heightOverCamera,
      distance,
      position: { x, y, z },
    });
  }

  /**
   * Load moon parameters from WorldInfo
   */
  private loadParametersFromWorldInfo(): void {
    const settings = this.appContext.worldInfo?.settings;
    if (!settings?.moons) {
      logger.debug('No moon configuration in WorldInfo');
      return;
    }

    const moonConfigs = settings.moons.slice(0, 3); // Max 3 moons

    for (let i = 0; i < moonConfigs.length; i++) {
      const config = moonConfigs[i];

      this.moons.push({
        enabled: config.enabled ?? false,
        size: config.size ?? 60,
        positionOnCircle: config.positionOnCircle ?? i * 120, // Spread around sky
        heightOverCamera: config.heightOverCamera ?? 45,
        distance: config.distance ?? 450,
        phase: config.phase ?? 0.5, // Default: half moon
        texture: config.texture, // Optional texture
      });
    }

    logger.debug('Moon parameters loaded from WorldInfo', {
      moonCount: this.moons.length,
    });
  }

  /**
   * Initialize moon service
   */
  private async initialize(): Promise<void> {
    // Register shaders
    this.registerShaders();

    // Load parameters from WorldInfo
    this.loadParametersFromWorldInfo();

    // No default moons - only create from WorldInfo

    // Create moons
    for (let i = 0; i < this.moons.length; i++) {
      await this.createMoon(i);
    }

    logger.debug('MoonService initialized', {
      moonCount: this.moons.length,
      enabledCount: this.moons.filter((m) => m.enabled).length,
    });
  }

  // ========== Public API ==========

  /**
   * Enable or disable a moon
   */
  public setMoonEnabled(index: number, enabled: boolean): void {
    if (index < 0 || index >= this.moons.length) return;

    this.moons[index].enabled = enabled;
    this.moons[index].mesh?.setEnabled(enabled);

    logger.debug(`Moon ${index} ${enabled ? 'enabled' : 'disabled'}`);
  }

  /**
   * Set moon size
   */
  public setMoonSize(index: number, size: number): void {
    if (index < 0 || index >= this.moons.length) return;

    const oldSize = this.moons[index].size;
    this.moons[index].size = size;

    if (this.moons[index].mesh) {
      const scale = size / oldSize;
      this.moons[index].mesh!.scaling.scaleInPlace(scale);
    }

    logger.debug(`Moon ${index} size set to ${size}`);
  }

  /**
   * Set moon position on circle (horizontal angle)
   */
  public setMoonPositionOnCircle(index: number, angleY: number): void {
    if (index < 0 || index >= this.moons.length) return;

    this.moons[index].positionOnCircle = angleY;
    this.updateMoonPosition(index);
  }

  /**
   * Set moon height over camera (elevation angle)
   */
  public setMoonHeightOverCamera(index: number, elevation: number): void {
    if (index < 0 || index >= this.moons.length) return;

    this.moons[index].heightOverCamera = elevation;
    this.updateMoonPosition(index);
  }

  /**
   * Set moon distance from camera
   */
  public setMoonDistance(index: number, distance: number): void {
    if (index < 0 || index >= this.moons.length) return;

    this.moons[index].distance = distance;
    this.updateMoonPosition(index);

    logger.debug(`Moon ${index} distance set to ${distance}`);
  }

  /**
   * Set moon phase (0.0 = new moon, 1.0 = full moon)
   */
  public setMoonPhase(index: number, phase: number): void {
    if (index < 0 || index >= this.moons.length) return;

    // Clamp phase to 0.0 - 1.0
    phase = Math.max(0.0, Math.min(1.0, phase));

    this.moons[index].phase = phase;

    // Update shader uniform
    if (this.moons[index].material) {
      this.moons[index].material!.setFloat('phase', phase);
    }

    logger.debug(`Moon ${index} phase set to ${phase.toFixed(2)}`);
  }

  /**
   * Set moon texture
   */
  public async setMoonTexture(index: number, texturePath: string | null): Promise<void> {
    if (index < 0 || index >= this.moons.length) return;

    const moon = this.moons[index];

    // Dispose old texture
    if (moon.textureObject) {
      moon.textureObject.dispose();
      moon.textureObject = undefined;
    }

    if (texturePath) {
      // Load new texture
      const networkService = this.appContext.services.network;
      const textureUrl = networkService
        ? networkService.getAssetUrl(texturePath)
        : texturePath;

      moon.texture = texturePath;
      // Load texture with credentials
      const blobUrl = await loadTextureUrlWithCredentials(textureUrl);
      moon.textureObject = new Texture(blobUrl, this.scene, false, true);
      moon.textureObject.hasAlpha = true;

      // Update shader
      if (moon.material) {
        moon.material.setFloat('hasTexture', 1.0);
        moon.material.setTexture('moonTexture', moon.textureObject);
      }

      logger.debug(`Moon ${index} texture set to ${texturePath}`);
    } else {
      // Remove texture, use solid color
      moon.texture = undefined;

      if (moon.material) {
        moon.material.setFloat('hasTexture', 0.0);
      }

      logger.debug(`Moon ${index} texture removed`);
    }
  }

  /**
   * Get moon position
   */
  public getMoonPosition(
    index: number
  ): { angleY: number; elevation: number; distance: number } | null {
    if (index < 0 || index >= this.moons.length) return null;

    const moon = this.moons[index];
    return {
      angleY: moon.positionOnCircle,
      elevation: moon.heightOverCamera,
      distance: moon.distance,
    };
  }

  /**
   * Get moon phase
   */
  public getMoonPhase(index: number): number | null {
    if (index < 0 || index >= this.moons.length) return null;
    return this.moons[index].phase;
  }

  /**
   * Dispose moon service
   */
  public dispose(): void {
    for (const moon of this.moons) {
      moon.mesh?.dispose();
      moon.material?.dispose();
      moon.root?.dispose();
    }

    this.moons = [];
    logger.debug('MoonService disposed');
  }
}
