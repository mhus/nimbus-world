/**
 * ParticleExplosionEffect - Particle explosion at a specific position
 *
 * Creates a highly configurable particle explosion with up to three weighted colors,
 * directional control, physics simulation, and various emission patterns.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import {
  Vector3,
  ParticleSystem,
  Color4,
  Scene,
  RawTexture,
  Constants,
  Quaternion,
  Space,
} from '@babylonjs/core';

const logger = getLogger('ParticleExplosionEffect');

/**
 * Options for ParticleExplosionEffect
 */
export interface ParticleExplosionOptions {
  /** Position of the explosion */
  position: { x: number; y: number; z: number };

  /** First color (hex format, e.g., "#ff0000") */
  color1: string;

  /** Second color (hex format, e.g., "#ff9900") */
  color2: string;

  /** Third color (hex format, e.g., "#ffff00") */
  color3: string;

  /** Weight of color1 (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color1Weight?: number;

  /** Weight of color2 (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color2Weight?: number;

  /** Weight of color3 (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color3Weight?: number;

  /** Initial radius where particles start (default: 0.1) */
  initialRadius?: number;

  /** Maximum spread radius (default: 5.0) */
  spreadRadius?: number;

  /** Number of particles to emit (default: 100) */
  particleCount?: number;

  /** Size of individual particles (default: 0.2) */
  particleSize?: number;

  /** Minimum particle size (overrides particleSize calculation, optional) */
  minParticleSize?: number;

  /** Maximum particle size (overrides particleSize calculation, optional) */
  maxParticleSize?: number;

  /** Duration of the explosion effect in seconds (default: 1.0) */
  duration?: number;

  /** Speed multiplier for particle movement (default: 1.0) */
  speed?: number;

  /** Alpha transparency of particles (0.0 = fully transparent, 1.0 = fully opaque, default: 1.0) */
  alpha?: number;

  // Direction & Cone
  /** Main direction of the explosion (optional, default: radial in all directions) */
  direction?: { x: number; y: number; z: number };

  /** How strongly the direction influences the explosion (0.0 = radial, 1.0 = fully directional, default: 0.0) */
  directionStrength?: number;

  /** Cone angle in degrees (0-180, default: 360 = full sphere) */
  coneAngle?: number;

  // Physics
  /** Gravity vector (default: {x: 0, y: 0, z: 0}) */
  gravity?: { x: number; y: number; z: number };

  /** Air resistance/drag (0.0 = no drag, 1.0 = maximum drag, default: 0.0) */
  drag?: number;

  /** Enable particle rotation (default: false) */
  rotation?: boolean;

  /** Angular velocity for rotation in radians/second (default: 0) */
  angularVelocity?: number;

  // Size Animation
  /** How particle size changes over lifetime (default: 'constant') */
  sizeOverLifetime?: 'constant' | 'grow' | 'shrink' | 'pulse';

  // Emission Pattern
  /** Emission pattern (default: 'sphere') */
  emissionPattern?: 'sphere' | 'hemisphere' | 'cone' | 'ring' | 'disc';

  /** Number of separate bursts (default: 1) */
  burstCount?: number;

  /** Delay between bursts in seconds (default: 0.0) */
  burstDelay?: number;

  // Visual Effects
  /** Fade-in duration in seconds (default: 10% of duration) */
  fadeInDuration?: number;

  /** Fade-out duration in seconds (default: 30% of duration) */
  fadeOutDuration?: number;

  /** Blending mode (default: 'add') */
  blend?: 'add' | 'alpha' | 'multiply';
}

/**
 * ParticleExplosionEffect - Creates a highly configurable particle explosion
 *
 * Usage examples:
 *
 * Basic radial explosion:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleExplosion",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "color1": "#ff0000",
 *     "color2": "#ff9900",
 *     "color3": "#ffff00",
 *     "particleCount": 200,
 *     "spreadRadius": 5.0
 *   }
 * }
 * ```
 *
 * Directional explosion with gravity:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleExplosion",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "direction": {"x": 0, "y": 1, "z": 0},
 *     "directionStrength": 0.7,
 *     "coneAngle": 45,
 *     "gravity": {"x": 0, "y": -9.81, "z": 0},
 *     "sizeOverLifetime": "shrink"
 *   }
 * }
 * ```
 *
 * Ring explosion with multiple bursts:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleExplosion",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "emissionPattern": "ring",
 *     "burstCount": 3,
 *     "burstDelay": 0.2,
 *     "rotation": true,
 *     "angularVelocity": 3.14
 *   }
 * }
 * ```
 */
export class ParticleExplosionEffect extends ScrawlEffectHandler<ParticleExplosionOptions> {
  private particleSystems: ParticleSystem[] = [];
  private startTime: number = 0;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;
  private particleTexture: RawTexture | null = null;
  private burstTimers: NodeJS.Timeout[] = [];

  isSteadyEffect(): boolean {
    return false; // One-shot effect
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    try {
      // Parse position
      const position = new Vector3(
        this.options.position.x,
        this.options.position.y,
        this.options.position.z
      );

      // Parse parameters with defaults
      const duration = this.options.duration ?? 1.0;
      const burstCount = this.options.burstCount ?? 1;
      const burstDelay = this.options.burstDelay ?? 0.0;

      // Create texture once for all bursts
      this.createParticleTexture();

      // Create bursts
      for (let burst = 0; burst < burstCount; burst++) {
        const delay = burst * burstDelay;

        if (delay === 0) {
          this.createBurst(position);
        } else {
          const timer = setTimeout(() => {
            this.createBurst(position);
          }, delay * 1000);
          this.burstTimers.push(timer);
        }
      }

      // Start animation timer
      this.startTime = this.now();
      this.animate();

      logger.debug('Particle explosion effect started', {
        position,
        burstCount,
        duration,
      });
    } catch (error) {
      logger.error('Failed to create particle explosion effect', { error });
      this.cleanup();
    }
  }

  private createParticleTexture(): void {
    if (this.particleTexture || !this.scene) {
      return;
    }

    const textureSize = 16;
    const textureData = new Uint8Array(textureSize * textureSize * 4);
    const center = textureSize / 2;

    for (let y = 0; y < textureSize; y++) {
      for (let x = 0; x < textureSize; x++) {
        const dx = x - center + 0.5;
        const dy = y - center + 0.5;
        const dist = Math.sqrt(dx * dx + dy * dy) / center;
        const texAlpha = Math.max(0, 1 - dist);

        const index = (y * textureSize + x) * 4;
        textureData[index] = 255;     // R
        textureData[index + 1] = 255; // G
        textureData[index + 2] = 255; // B
        textureData[index + 3] = Math.floor(texAlpha * 255); // A
      }
    }

    this.particleTexture = RawTexture.CreateRGBATexture(
      textureData,
      textureSize,
      textureSize,
      this.scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );
  }

  private createBurst(position: Vector3): void {
    if (!this.scene) {
      return;
    }

    // Parse parameters
    const initialRadius = this.options.initialRadius ?? 0.1;
    const spreadRadius = this.options.spreadRadius ?? 5.0;
    const particleCount = this.options.particleCount ?? 100;
    const particleSize = this.options.particleSize ?? 0.2;
    const duration = this.options.duration ?? 1.0;
    const speed = this.options.speed ?? 1.0;
    const alpha = this.options.alpha ?? 1.0;

    // Direction parameters
    const direction = this.options.direction
      ? new Vector3(this.options.direction.x, this.options.direction.y, this.options.direction.z).normalize()
      : null;
    const directionStrength = this.options.directionStrength ?? 0.0;
    const coneAngle = this.options.coneAngle ?? 360;

    // Physics parameters
    const gravity = this.options.gravity
      ? new Vector3(this.options.gravity.x, this.options.gravity.y, this.options.gravity.z)
      : Vector3.Zero();
    const drag = this.options.drag ?? 0.0;

    // Size parameters
    const minParticleSize = this.options.minParticleSize ?? particleSize * 0.8;
    const maxParticleSize = this.options.maxParticleSize ?? particleSize * 1.2;

    // Visual parameters
    const fadeInDuration = this.options.fadeInDuration ?? duration * 0.1;
    const fadeOutDuration = this.options.fadeOutDuration ?? duration * 0.3;
    const fadeInPercent = fadeInDuration / duration;
    const fadeOutStartPercent = 1.0 - (fadeOutDuration / duration);

    // Color weights
    const colorWeights = [
      this.options.color1Weight ?? 1.0,
      this.options.color2Weight ?? 1.0,
      this.options.color3Weight ?? 1.0,
    ];

    // Parse colors
    const colors = [
      this.parseColor(this.options.color1),
      this.parseColor(this.options.color2),
      this.parseColor(this.options.color3),
    ];

    // Create particle system for each color with non-zero weight
    for (let i = 0; i < 3; i++) {
      // Skip this color if weight is 0
      if (colorWeights[i] === 0) {
        continue;
      }

      const particleSystem = new ParticleSystem(
        `explosion${i}_${Date.now()}`,
        particleCount,
        this.scene
      );

      // Use the procedural circular texture
      particleSystem.particleTexture = this.particleTexture;

      // Emission from explosion center
      particleSystem.emitter = position.clone();

      // Particle appearance
      particleSystem.minSize = minParticleSize;
      particleSystem.maxSize = maxParticleSize;

      // Particle lifetime equals effect duration
      particleSystem.minLifeTime = duration * 0.9;
      particleSystem.maxLifeTime = duration * 1.1;

      // Color with weight applied
      const weightedColor = new Color4(
        colors[i].r,
        colors[i].g,
        colors[i].b,
        colors[i].a * alpha * colorWeights[i]
      );

      // Configure emitter based on pattern
      this.configureEmitter(particleSystem, direction, coneAngle);

      // Calculate speed to reach spreadRadius over duration
      const baseSpeed = (spreadRadius - initialRadius) / duration;
      const effectiveSpeed = baseSpeed * speed;
      particleSystem.minEmitPower = effectiveSpeed * 0.8;
      particleSystem.maxEmitPower = effectiveSpeed * 1.2;

      // Apply direction strength if direction is set
      if (direction && directionStrength > 0) {
        particleSystem.direction1 = direction.scale(directionStrength);
        particleSystem.direction2 = direction.scale(directionStrength);
      }

      // Apply gravity
      particleSystem.gravity = gravity;

      // Apply drag (implemented via updateSpeed)
      if (drag > 0) {
        particleSystem.updateSpeed = 1.0 - drag * 0.01;
      }

      // Rotation
      if (this.options.rotation) {
        particleSystem.minAngularSpeed = this.options.angularVelocity ?? 0;
        particleSystem.maxAngularSpeed = this.options.angularVelocity ?? 0;
      }

      // Blending mode
      const blend = this.options.blend ?? 'add';
      switch (blend) {
        case 'add':
          particleSystem.blendMode = ParticleSystem.BLENDMODE_ADD;
          break;
        case 'alpha':
          particleSystem.blendMode = ParticleSystem.BLENDMODE_STANDARD;
          break;
        case 'multiply':
          particleSystem.blendMode = ParticleSystem.BLENDMODE_MULTIPLY;
          break;
      }

      // Emit all particles at once (burst)
      particleSystem.manualEmitCount = Math.floor(particleCount / 3);

      // Disable looping
      particleSystem.targetStopDuration = 0;

      // Color gradients for fade effect
      particleSystem.addColorGradient(0.0, new Color4(colors[i].r, colors[i].g, colors[i].b, 0.0));
      particleSystem.addColorGradient(fadeInPercent, weightedColor);
      particleSystem.addColorGradient(fadeOutStartPercent, weightedColor);
      particleSystem.addColorGradient(1.0, new Color4(colors[i].r, colors[i].g, colors[i].b, 0.0));

      // Size gradients based on sizeOverLifetime
      const sizeMode = this.options.sizeOverLifetime ?? 'constant';
      this.configureSizeGradients(particleSystem, sizeMode, minParticleSize, maxParticleSize);

      particleSystem.start();
      this.particleSystems.push(particleSystem);
    }
  }

  private configureEmitter(
    particleSystem: ParticleSystem,
    direction: Vector3 | null,
    coneAngle: number
  ): void {
    const emissionPattern = this.options.emissionPattern ?? 'sphere';

    switch (emissionPattern) {
      case 'sphere':
        if (coneAngle >= 360) {
          // Full sphere
          particleSystem.createSphereEmitter(0.01, 1);
        } else {
          // Cone
          const radius = Math.tan((coneAngle * Math.PI) / 360) * 1.0;
          particleSystem.createConeEmitter(radius, coneAngle * (Math.PI / 180));

          // Orient cone in direction if specified
          if (direction) {
            const rotation = Quaternion.FromUnitVectorsToRef(
              Vector3.Up(),
              direction,
              new Quaternion()
            );
            particleSystem.emitter = particleSystem.emitter as Vector3;
          }
        }
        break;

      case 'hemisphere':
        particleSystem.createHemisphericEmitter(1, Math.PI);
        break;

      case 'cone':
        const radius = Math.tan((coneAngle * Math.PI) / 360) * 1.0;
        particleSystem.createConeEmitter(radius, coneAngle * (Math.PI / 180));
        break;

      case 'ring':
        // Ring emitter (particles emit in a ring/torus shape)
        particleSystem.createDirectedSphereEmitter(1, new Vector3(0, 0, 1), new Vector3(0, 0, -1));
        particleSystem.direction1 = new Vector3(-1, 0, -1);
        particleSystem.direction2 = new Vector3(1, 0, 1);
        break;

      case 'disc':
        // Disc emitter (flat circular emission)
        particleSystem.createSphereEmitter(0.01, 1);
        particleSystem.direction1 = new Vector3(-1, 0, -1);
        particleSystem.direction2 = new Vector3(1, 0, 1);
        break;

      default:
        particleSystem.createSphereEmitter(0.01, 1);
    }
  }

  private configureSizeGradients(
    particleSystem: ParticleSystem,
    sizeMode: 'constant' | 'grow' | 'shrink' | 'pulse',
    minSize: number,
    maxSize: number
  ): void {
    switch (sizeMode) {
      case 'grow':
        particleSystem.addSizeGradient(0.0, minSize);
        particleSystem.addSizeGradient(1.0, maxSize);
        break;

      case 'shrink':
        particleSystem.addSizeGradient(0.0, maxSize);
        particleSystem.addSizeGradient(1.0, minSize);
        break;

      case 'pulse':
        particleSystem.addSizeGradient(0.0, minSize);
        particleSystem.addSizeGradient(0.25, maxSize);
        particleSystem.addSizeGradient(0.5, minSize);
        particleSystem.addSizeGradient(0.75, maxSize);
        particleSystem.addSizeGradient(1.0, minSize);
        break;

      case 'constant':
      default:
        // No size gradients = constant size
        break;
    }
  }

  private animate = () => {
    const elapsed = this.now() - this.startTime;
    const duration = this.options.duration ?? 1.0;
    const burstCount = this.options.burstCount ?? 1;
    const burstDelay = this.options.burstDelay ?? 0.0;
    const totalDuration = duration + (burstCount - 1) * burstDelay;

    // Stop after total duration + small buffer for cleanup
    if (elapsed >= totalDuration + 0.5) {
      this.cleanup();
      return;
    }

    // Continue animation
    this.animationHandle = requestAnimationFrame(this.animate);
  };

  private parseColor(colorString: string): Color4 {
    // Simple hex color parser (#RRGGBB)
    if (colorString.startsWith('#')) {
      const hex = colorString.substring(1);
      const r = parseInt(hex.substring(0, 2), 16) / 255;
      const g = parseInt(hex.substring(2, 4), 16) / 255;
      const b = parseInt(hex.substring(4, 6), 16) / 255;
      return new Color4(r, g, b, 1.0);
    }

    // Fallback to white
    return new Color4(1, 1, 1, 1);
  }

  private cleanup() {
    // Clear burst timers
    for (const timer of this.burstTimers) {
      clearTimeout(timer);
    }
    this.burstTimers = [];

    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    for (const ps of this.particleSystems) {
      ps.stop();
      ps.dispose();
    }
    this.particleSystems = [];

    if (this.particleTexture) {
      this.particleTexture.dispose();
      this.particleTexture = null;
    }

    logger.debug('Particle explosion effect cleaned up');
  }

  stop(): void {
    this.cleanup();
  }
}
