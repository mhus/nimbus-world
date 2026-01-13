/**
 * ParticlePositionFlashEffect - Lightning strike effect from above
 *
 * Creates a dramatic lightning bolt that strikes from above onto a target position,
 * with branches, glow, and impact effects.
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
} from '@babylonjs/core';

const logger = getLogger('ParticlePositionFlashEffect');

/**
 * Lightning style presets
 */
export type LightningStyle = 'natural' | 'magical' | 'blue' | 'red' | 'purple';

/**
 * Options for ParticlePositionFlashEffect
 */
export interface ParticlePositionFlashOptions {
  /** Target position where lightning strikes */
  position: { x: number; y: number; z: number };

  // Basic Properties
  /** Height above target where lightning starts (default: 20.0) */
  startHeight?: number;

  /** Intensity 0-1+ (default: 1.0) */
  intensity?: number;

  /** Total duration in seconds (default: 0.5) */
  duration?: number;

  // Lightning Properties
  /** Thickness of lightning bolt (default: 0.2) */
  thickness?: number;

  /** Number of branches (default: 3) */
  branches?: number;

  /** Branch intensity 0-1 (default: 0.5) */
  branchIntensity?: number;

  /** How jagged the lightning is 0-1 (default: 0.7) */
  zigzag?: number;

  // Colors
  /** Core color (default: "#ffffff") */
  coreColor?: string;

  /** Glow color (default: "#aaccff") */
  glowColor?: string;

  /** Impact flash color (default: "#ffffff") */
  impactColor?: string;

  // Timing
  /** Strike speed (default: 100, higher = faster) */
  strikeSpeed?: number;

  /** Duration of main flash (default: 0.1) */
  flashDuration?: number;

  /** Duration of glow/afterglow (default: 0.3) */
  glowDuration?: number;

  /** Duration of impact flash (default: 0.2) */
  impactDuration?: number;

  // Effects
  /** Enable glow effect (default: true) */
  glow?: boolean;

  /** Enable impact flash (default: true) */
  impact?: boolean;

  /** Enable afterglow (default: true) */
  afterglow?: boolean;

  // Style
  /** Lightning style preset (default: 'natural') */
  lightningStyle?: LightningStyle;

  // Particle Count
  /** Particles for main bolt (default: 1000) */
  particleCount?: number;
}

/**
 * ParticlePositionFlashEffect - Lightning strike from above
 *
 * Usage examples:
 *
 * Standard lightning strike:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particlePositionFlash",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0}
 *   }
 * }
 * ```
 *
 * Intense lightning with branches:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particlePositionFlash",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "intensity": 1.5,
 *     "branches": 5,
 *     "thickness": 0.3,
 *     "zigzag": 0.9
 *   }
 * }
 * ```
 *
 * Magical purple lightning:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particlePositionFlash",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "lightningStyle": "magical"
 *   }
 * }
 * ```
 */
export class ParticlePositionFlashEffect extends ScrawlEffectHandler<ParticlePositionFlashOptions> {
  private particleSystems: ParticleSystem[] = [];
  private startTime: number = 0;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;
  private particleTexture: RawTexture | null = null;
  private lightningPath: Vector3[] = [];
  private branchPoints: Array<{ point: Vector3; direction: Vector3 }> = [];

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
      // Parse target position
      const targetPos = new Vector3(
        this.options.position.x,
        this.options.position.y,
        this.options.position.z
      );

      // Apply preset
      this.applyPreset();

      // Parse parameters
      const startHeight = this.options.startHeight ?? 20.0;
      const intensity = this.options.intensity ?? 1.0;
      const duration = this.options.duration ?? 0.5;
      const thickness = this.options.thickness ?? 0.2;
      const branches = this.options.branches ?? 3;
      const branchIntensity = this.options.branchIntensity ?? 0.5;
      const zigzag = this.options.zigzag ?? 0.7;

      const strikeSpeed = this.options.strikeSpeed ?? 100;
      const flashDuration = this.options.flashDuration ?? 0.1;
      const glowDuration = this.options.glowDuration ?? 0.3;
      const impactDuration = this.options.impactDuration ?? 0.2;

      const enableGlow = this.options.glow ?? true;
      const enableImpact = this.options.impact ?? true;
      const enableAfterglow = this.options.afterglow ?? true;

      const particleCount = this.options.particleCount ?? 1000;

      // Colors
      const coreColor = this.parseColor(this.options.coreColor ?? '#ffffff');
      const glowColor = this.parseColor(this.options.glowColor ?? '#aaccff');
      const impactColor = this.parseColor(this.options.impactColor ?? '#ffffff');

      // Calculate lightning path
      const startPos = targetPos.add(new Vector3(0, startHeight, 0));
      this.calculateLightningPath(startPos, targetPos, zigzag);

      // Calculate branch points
      this.calculateBranchPoints(branches, branchIntensity, zigzag);

      // Create texture
      this.createParticleTexture();

      // Create main lightning bolt
      this.createMainBolt(
        thickness,
        intensity,
        coreColor,
        particleCount,
        flashDuration,
        strikeSpeed
      );

      // Create branches
      for (const branch of this.branchPoints) {
        this.createBranch(
          branch.point,
          branch.direction,
          thickness * 0.6,
          intensity * branchIntensity,
          coreColor,
          Math.floor(particleCount * 0.3),
          flashDuration
        );
      }

      // Create glow
      if (enableGlow) {
        this.createGlowEffect(
          thickness * 3,
          intensity,
          glowColor,
          particleCount,
          glowDuration
        );
      }

      // Create impact flash
      if (enableImpact) {
        this.createImpactFlash(
          targetPos,
          thickness * 2,
          intensity,
          impactColor,
          Math.floor(particleCount * 0.5),
          impactDuration
        );
      }

      // Create afterglow
      if (enableAfterglow && enableGlow) {
        this.createAfterglow(
          thickness * 2,
          intensity * 0.5,
          glowColor,
          Math.floor(particleCount * 0.3),
          duration - glowDuration
        );
      }

      // Start animation
      this.startTime = this.now();
      this.animate();

      logger.debug('Lightning strike effect started', {
        targetPos,
        startHeight,
        intensity,
      });
    } catch (error) {
      logger.error('Failed to create lightning strike effect', { error });
      this.cleanup();
    }
  }

  private applyPreset(): void {
    const style = this.options.lightningStyle ?? 'natural';

    switch (style) {
      case 'natural':
        this.options.coreColor = this.options.coreColor ?? '#ffffff';
        this.options.glowColor = this.options.glowColor ?? '#aaccff';
        this.options.impactColor = this.options.impactColor ?? '#ffffff';
        break;

      case 'magical':
        this.options.coreColor = this.options.coreColor ?? '#ff88ff';
        this.options.glowColor = this.options.glowColor ?? '#ff00ff';
        this.options.impactColor = this.options.impactColor ?? '#ff88ff';
        this.options.zigzag = this.options.zigzag ?? 0.9;
        break;

      case 'blue':
        this.options.coreColor = this.options.coreColor ?? '#aaddff';
        this.options.glowColor = this.options.glowColor ?? '#0088ff';
        this.options.impactColor = this.options.impactColor ?? '#aaddff';
        break;

      case 'red':
        this.options.coreColor = this.options.coreColor ?? '#ffaaaa';
        this.options.glowColor = this.options.glowColor ?? '#ff0000';
        this.options.impactColor = this.options.impactColor ?? '#ffaaaa';
        break;

      case 'purple':
        this.options.coreColor = this.options.coreColor ?? '#dd88ff';
        this.options.glowColor = this.options.glowColor ?? '#8800ff';
        this.options.impactColor = this.options.impactColor ?? '#dd88ff';
        break;
    }
  }

  private calculateLightningPath(start: Vector3, end: Vector3, zigzag: number): void {
    this.lightningPath = [];

    const segments = 20;
    const direction = end.subtract(start);
    const segmentLength = direction.length() / segments;

    for (let i = 0; i <= segments; i++) {
      const t = i / segments;
      const point = start.add(direction.scale(t));

      // Add zigzag offset
      if (i > 0 && i < segments) {
        const offsetAmount = zigzag * segmentLength * 0.5;
        const offsetX = (Math.random() - 0.5) * offsetAmount;
        const offsetZ = (Math.random() - 0.5) * offsetAmount;
        point.x += offsetX;
        point.z += offsetZ;
      }

      this.lightningPath.push(point);
    }
  }

  private calculateBranchPoints(
    branchCount: number,
    branchIntensity: number,
    zigzag: number
  ): void {
    this.branchPoints = [];

    if (branchCount === 0 || this.lightningPath.length < 3) {
      return;
    }

    // Create branches at random points along the path
    for (let i = 0; i < branchCount; i++) {
      // Random point between 20% and 80% of path
      const segmentIndex = Math.floor(
        this.lightningPath.length * (0.2 + Math.random() * 0.6)
      );
      const point = this.lightningPath[segmentIndex];

      // Random branch direction (perpendicular to main bolt)
      const angle = Math.random() * Math.PI * 2;
      const branchLength = 3 + Math.random() * 5;
      const direction = new Vector3(
        Math.cos(angle) * branchLength,
        -1 - Math.random() * 2,
        Math.sin(angle) * branchLength
      );

      this.branchPoints.push({ point: point.clone(), direction });
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
        textureData[index] = 255;
        textureData[index + 1] = 255;
        textureData[index + 2] = 255;
        textureData[index + 3] = Math.floor(texAlpha * 255);
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

  private createMainBolt(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    flashDuration: number,
    strikeSpeed: number
  ): void {
    if (!this.scene || this.lightningPath.length === 0) return;

    const ps = new ParticleSystem('mainBolt', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;

    // Emit along path
    ps.createPointEmitter(Vector3.Zero(), Vector3.Zero());
    ps.emitter = this.lightningPath[0].clone();

    ps.minSize = thickness * 0.8;
    ps.maxSize = thickness * 1.2;
    ps.minLifeTime = flashDuration * 0.9;
    ps.maxLifeTime = flashDuration * 1.1;

    ps.emitRate = particleCount * (strikeSpeed / 10);

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    // Color gradient
    ps.addColorGradient(0.0, color);
    ps.addColorGradient(0.5, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    // Custom update to follow path
    const path = this.lightningPath;
    const startTime = this.now();

    ps.updateFunction = (particles: any) => {
      const elapsed = this.now() - startTime;
      const pathProgress = Math.min(elapsed * strikeSpeed, 1.0);

      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        // Position along path
        const particleProgress = particle._pathProgress ?? Math.random();
        const adjustedProgress = Math.min(particleProgress * pathProgress, 1.0);
        const segmentIndex = Math.floor(adjustedProgress * (path.length - 1));

        if (segmentIndex >= 0 && segmentIndex < path.length) {
          particle.position = path[segmentIndex].clone();

          // Small random offset for thickness
          const offset = (Math.random() - 0.5) * thickness * 0.5;
          particle.position.x += offset;
          particle.position.z += offset;
        }

        if (!particle._pathProgress) {
          particle._pathProgress = particleProgress;
        }
      }
    };

    // Initialize path progress for each particle
    const originalStart = ps.startPositionFunction;
    ps.startPositionFunction = (worldMatrix: any, positionToUpdate: Vector3, particle: any) => {
      if (originalStart) {
        originalStart(worldMatrix, positionToUpdate, particle, false);
      }
      particle._pathProgress = Math.random();
    };

    ps.start();
    this.particleSystems.push(ps);
  }

  private createBranch(
    startPoint: Vector3,
    direction: Vector3,
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    flashDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('branch', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = startPoint.clone();

    ps.minSize = thickness * 0.6;
    ps.maxSize = thickness * 1.0;
    ps.minLifeTime = flashDuration * 0.5;
    ps.maxLifeTime = flashDuration * 0.8;

    ps.emitRate = particleCount * 20;

    ps.direction1 = direction.scale(0.8);
    ps.direction2 = direction.scale(1.2);
    ps.minEmitPower = 10;
    ps.maxEmitPower = 15;
    ps.gravity = Vector3.Zero();

    // Color gradient
    ps.addColorGradient(0.0, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(0.5, new Color4(color.r, color.g, color.b, color.a * intensity * 0.7));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;
    ps.targetStopDuration = flashDuration;

    ps.start();
    this.particleSystems.push(ps);
  }

  private createGlowEffect(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    glowDuration: number
  ): void {
    if (!this.scene || this.lightningPath.length === 0) return;

    const ps = new ParticleSystem('glow', Math.floor(particleCount * 0.5), this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.lightningPath[0].clone();

    ps.minSize = thickness * 2;
    ps.maxSize = thickness * 4;
    ps.minLifeTime = glowDuration * 0.8;
    ps.maxLifeTime = glowDuration * 1.2;

    ps.emitRate = particleCount * 5;

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    // Color gradient
    ps.addColorGradient(0.0, new Color4(color.r, color.g, color.b, color.a * intensity * 0.5));
    ps.addColorGradient(0.3, new Color4(color.r, color.g, color.b, color.a * intensity * 0.3));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.01;

    // Follow path like main bolt
    const path = this.lightningPath;
    ps.updateFunction = (particles: any) => {
      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        const particleProgress = particle._pathProgress ?? Math.random();
        const segmentIndex = Math.floor(particleProgress * (path.length - 1));

        if (segmentIndex >= 0 && segmentIndex < path.length) {
          particle.position = path[segmentIndex].clone();
        }

        if (!particle._pathProgress) {
          particle._pathProgress = particleProgress;
        }
      }
    };

    const originalStart = ps.startPositionFunction;
    ps.startPositionFunction = (worldMatrix: any, positionToUpdate: Vector3, particle: any) => {
      if (originalStart) {
        originalStart(worldMatrix, positionToUpdate, particle, false);
      }
      particle._pathProgress = Math.random();
    };

    ps.start();
    this.particleSystems.push(ps);
  }

  private createImpactFlash(
    position: Vector3,
    size: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    impactDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('impact', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone();

    ps.minSize = size * 0.5;
    ps.maxSize = size * 1.5;
    ps.minLifeTime = impactDuration * 0.8;
    ps.maxLifeTime = impactDuration * 1.2;

    ps.manualEmitCount = particleCount;

    // Radial outward (mostly horizontal)
    ps.createSphereEmitter(0.1, 1);
    ps.direction1 = new Vector3(-1, 0.2, -1);
    ps.direction2 = new Vector3(1, 0.5, 1);
    ps.minEmitPower = 5;
    ps.maxEmitPower = 10;
    ps.gravity = new Vector3(0, -2, 0);

    // Color gradient
    ps.addColorGradient(0.0, color);
    ps.addColorGradient(0.3, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    // Size gradient
    ps.addSizeGradient(0.0, 0.5);
    ps.addSizeGradient(0.5, 1.0);
    ps.addSizeGradient(1.0, 0.3);

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;
    ps.targetStopDuration = 0;

    ps.start();
    this.particleSystems.push(ps);
  }

  private createAfterglow(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    afterglowDelay: number
  ): void {
    if (!this.scene || this.lightningPath.length === 0) return;

    const ps = new ParticleSystem('afterglow', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.lightningPath[0].clone();

    ps.minSize = thickness;
    ps.maxSize = thickness * 2;
    ps.minLifeTime = 0.3;
    ps.maxLifeTime = 0.5;

    ps.emitRate = particleCount * 2;

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    // Delayed start
    ps.startDelay = afterglowDelay * 1000;

    // Color gradient (very faint)
    ps.addColorGradient(0.0, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.005;

    // Follow path
    const path = this.lightningPath;
    ps.updateFunction = (particles: any) => {
      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        const particleProgress = particle._pathProgress ?? Math.random();
        const segmentIndex = Math.floor(particleProgress * (path.length - 1));

        if (segmentIndex >= 0 && segmentIndex < path.length) {
          particle.position = path[segmentIndex].clone();
        }

        if (!particle._pathProgress) {
          particle._pathProgress = particleProgress;
        }
      }
    };

    const originalStart = ps.startPositionFunction;
    ps.startPositionFunction = (worldMatrix: any, positionToUpdate: Vector3, particle: any) => {
      if (originalStart) {
        originalStart(worldMatrix, positionToUpdate, particle, false);
      }
      particle._pathProgress = Math.random();
    };

    ps.start();
    this.particleSystems.push(ps);
  }

  private animate = () => {
    const elapsed = this.now() - this.startTime;
    const duration = this.options.duration ?? 0.5;

    // Stop after duration + buffer
    if (elapsed >= duration + 0.5) {
      this.cleanup();
      return;
    }

    this.animationHandle = requestAnimationFrame(this.animate);
  };

  private parseColor(colorString: string): Color4 {
    if (colorString.startsWith('#')) {
      const hex = colorString.substring(1);
      const r = parseInt(hex.substring(0, 2), 16) / 255;
      const g = parseInt(hex.substring(2, 4), 16) / 255;
      const b = parseInt(hex.substring(4, 6), 16) / 255;
      return new Color4(r, g, b, 1.0);
    }
    return new Color4(1, 1, 1, 1);
  }

  private cleanup() {
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

    this.lightningPath = [];
    this.branchPoints = [];

    logger.debug('Lightning strike effect cleaned up');
  }

  stop(): void {
    this.cleanup();
  }
}
