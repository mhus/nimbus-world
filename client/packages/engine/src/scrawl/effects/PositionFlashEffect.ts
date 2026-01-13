/**
 * PositionFlashEffect - Texture-based lightning flash from above
 *
 * Creates a lightning flash that strikes from above using animated textures.
 * The textures are stretched vertically from sky to ground position.
 * Multiple texture frames are cycled to create the flash animation effect.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import {
  Vector3,
  Scene,
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Texture,
  Color3,
  PointLight,
  SpotLight,
  Light,
} from '@babylonjs/core';

const logger = getLogger('PositionFlashEffect');

/**
 * Options for PositionFlashEffect
 */
export interface PositionFlashOptions {
  /** Target position on the ground where lightning strikes */
  position: { x: number; y: number; z: number };

  /** Total duration of the effect in seconds (default: 0.5) */
  duration?: number;

  /** Color of the lightning flash (default: "#ffffff") */
  color?: string;

  /** Intensity/brightness 0-1+ (default: 1.0) */
  intensity?: number;

  /** Height above ground where flash starts (default: 20) */
  height?: number;

  /** Width/thickness of the lightning bolt (default: 0.5) */
  width?: number;

  /** Enable dynamic light effect (default: true) */
  light?: boolean;

  /** Type of light if enabled: "point" or "spot" (default: "point") */
  lightType?: 'point' | 'spot';

  /** Light intensity multiplier (default: 10) */
  lightIntensity?: number;

  /** Light range/radius (default: 10) */
  lightRange?: number;

  /** Texture paths for flash animation frames (optional, uses procedural if not provided) */
  textureFrames?: string[];

  /** Frame rate for texture animation in FPS (default: 30) */
  frameRate?: number;
}

/**
 * PositionFlashEffect - Texture-based lightning strike effect
 *
 * Usage example:
 *
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "positionFlash",
 *   "ctx": {
 *     "position": {"x": 100, "y": 65, "z": 200},
 *     "duration": 0.5,
 *     "color": "#aaccff",
 *     "intensity": 1.5,
 *     "height": 25,
 *     "width": 0.8,
 *     "light": true,
 *     "lightType": "point"
 *   }
 * }
 * ```
 */
export class PositionFlashEffect extends ScrawlEffectHandler<PositionFlashOptions> {
  private flashMesh: Mesh | null = null;
  private flashMaterial: StandardMaterial | null = null;
  private light: Light | null = null;
  private scene: Scene | null = null;
  private startTime: number = 0;
  private animationHandle: number | null = null;
  private currentFrame: number = 0;
  private textures: Texture[] = [];

  isSteadyEffect(): boolean {
    return false; // Temporary effect
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    try {
      // Parse parameters
      const targetPos = new Vector3(
        this.options.position.x,
        this.options.position.y,
        this.options.position.z
      );
      const duration = this.options.duration ?? 0.5;
      const color = this.parseColor(this.options.color ?? '#ffffff');
      const intensity = this.options.intensity ?? 1.0;
      const height = this.options.height ?? 20;
      const width = this.options.width ?? 0.5;
      const enableLight = this.options.light ?? true;
      const lightType = this.options.lightType ?? 'point';
      const lightIntensity = this.options.lightIntensity ?? 10;
      const lightRange = this.options.lightRange ?? 10;
      const frameRate = this.options.frameRate ?? 30;

      // Create flash mesh (vertical plane from sky to target)
      const startPos = targetPos.clone().add(new Vector3(0, height, 0));
      const flashHeight = height;

      // Create vertical plane mesh
      this.flashMesh = MeshBuilder.CreatePlane(
        'positionFlash',
        {
          width: width,
          height: flashHeight,
        },
        scene
      );

      // Position mesh at midpoint between start and target
      const midPoint = Vector3.Lerp(startPos, targetPos, 0.5);
      this.flashMesh.position = midPoint;

      // Billboard mode: always face camera
      this.flashMesh.billboardMode = Mesh.BILLBOARDMODE_ALL;

      // Create emissive material
      this.flashMaterial = new StandardMaterial('flashMaterial', scene);
      this.flashMaterial.emissiveColor = color;
      this.flashMaterial.disableLighting = true;
      this.flashMaterial.alpha = intensity;

      // Load textures if provided, otherwise use solid color
      if (this.options.textureFrames && this.options.textureFrames.length > 0) {
        // Load texture frames for animation
        const networkService = ctx.appContext.services.network;
        if (networkService) {
          for (const texturePath of this.options.textureFrames) {
            const textureUrl = networkService.getAssetUrl(texturePath);
            const texture = new Texture(textureUrl, scene);
            texture.hasAlpha = true;
            this.textures.push(texture);
          }

          if (this.textures.length > 0) {
            this.flashMaterial.diffuseTexture = this.textures[0];
            this.flashMaterial.opacityTexture = this.textures[0];
          }
        }
      }

      this.flashMesh.material = this.flashMaterial;

      // Create dynamic light if enabled
      if (enableLight) {
        if (lightType === 'spot') {
          this.light = new SpotLight(
            'flashLight',
            startPos,
            new Vector3(0, -1, 0), // Direction: downward
            Math.PI / 3, // Angle: 60 degrees
            2, // Exponent
            scene
          );
        } else {
          this.light = new PointLight('flashLight', targetPos, scene);
        }

        this.light.diffuse = color;
        this.light.specular = color;
        this.light.intensity = lightIntensity * intensity;
        this.light.range = lightRange;
      }

      // Start animation
      this.startTime = this.now();
      this.animate(duration, frameRate);

      logger.debug('Position flash effect started', {
        position: targetPos,
        duration,
        height,
        color: this.options.color,
      });
    } catch (error) {
      logger.error('Failed to create position flash effect', { error });
      this.cleanup();
    }
  }

  private animate(duration: number, frameRate: number): void {
    const frameDuration = 1000 / frameRate; // ms per frame
    let lastFrameTime = 0;

    const animationLoop = () => {
      const elapsed = this.now() - this.startTime;
      const progress = Math.min(elapsed / (duration * 1000), 1.0);

      // Update texture frame if we have multiple frames
      if (this.textures.length > 1) {
        const now = this.now();
        if (now - lastFrameTime >= frameDuration) {
          this.currentFrame = (this.currentFrame + 1) % this.textures.length;
          if (this.flashMaterial && this.textures[this.currentFrame]) {
            this.flashMaterial.diffuseTexture = this.textures[this.currentFrame];
            this.flashMaterial.opacityTexture = this.textures[this.currentFrame];
          }
          lastFrameTime = now;
        }
      }

      // Fade out effect
      if (this.flashMaterial) {
        // Fast fade in (0-0.1), hold (0.1-0.7), fast fade out (0.7-1.0)
        let alpha: number;
        if (progress < 0.1) {
          alpha = progress / 0.1; // Fade in
        } else if (progress < 0.7) {
          alpha = 1.0; // Hold
        } else {
          alpha = 1.0 - (progress - 0.7) / 0.3; // Fade out
        }

        this.flashMaterial.alpha = alpha * (this.options.intensity ?? 1.0);
      }

      // Fade out light
      if (this.light) {
        const lightAlpha = progress < 0.5 ? 1.0 : 1.0 - (progress - 0.5) / 0.5;
        this.light.intensity =
          (this.options.lightIntensity ?? 10) *
          (this.options.intensity ?? 1.0) *
          lightAlpha;
      }

      // End effect when duration reached
      if (progress >= 1.0) {
        this.cleanup();
        return;
      }

      this.animationHandle = requestAnimationFrame(animationLoop);
    };

    this.animationHandle = requestAnimationFrame(animationLoop);
  }

  private parseColor(colorString: string): Color3 {
    if (colorString.startsWith('#')) {
      const hex = colorString.substring(1);
      const r = parseInt(hex.substring(0, 2), 16) / 255;
      const g = parseInt(hex.substring(2, 4), 16) / 255;
      const b = parseInt(hex.substring(4, 6), 16) / 255;
      return new Color3(r, g, b);
    }
    return new Color3(1, 1, 1);
  }

  private cleanup(): void {
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    if (this.flashMesh) {
      this.flashMesh.dispose();
      this.flashMesh = null;
    }

    if (this.flashMaterial) {
      this.flashMaterial.dispose();
      this.flashMaterial = null;
    }

    if (this.light) {
      this.light.dispose();
      this.light = null;
    }

    // Dispose textures
    this.textures.forEach((texture) => texture.dispose());
    this.textures = [];

    logger.debug('Position flash effect cleaned up');
  }

  isRunning(): boolean {
    return this.flashMesh !== null || this.animationHandle !== null;
  }

  stop(): void {
    this.cleanup();
  }
}
