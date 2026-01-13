/**
 * ParticleWandFlashSteadyEffect - Endless magic wand beam (Steady)
 *
 * Creates an endless magical beam from a wand (source) to a target position.
 * Runs until stop() is called. For a timed beam, use ParticleWandFlashEffect instead.
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
import type { WandStyle, PathStyle, AnimationMode } from './ParticleWandFlashEffect';

const logger = getLogger('ParticleWandFlashSteadyEffect');

/**
 * Options for ParticleWandFlashSteadyEffect
 */
export interface ParticleWandFlashSteadyOptions {
  /** Source position (wand tip) */
  source: { x: number; y: number; z: number };

  /** Target position */
  target: { x: number; y: number; z: number };

  // Beam Properties
  /** Thickness of beam (default: 0.15) */
  thickness?: number;

  /** Intensity 0-1+ (default: 1.0) */
  intensity?: number;

  /** Path style (default: 'straight') */
  pathStyle?: PathStyle;

  /** Zigzag intensity for zigzag paths 0-1 (default: 0.3) */
  zigzag?: number;

  /** Curvature for wave/spiral paths 0-1 (default: 0.0) */
  curvature?: number;

  // Animation
  /** Animation mode (default: 'continuous') */
  animationMode?: AnimationMode;

  /** Travel speed for traveling mode (default: 5.0) */
  travelSpeed?: number;

  /** Pulse frequency in Hz (default: 3.0) */
  pulseFrequency?: number;

  // Colors
  /** Core color (default: "#ffffff") */
  coreColor?: string;

  /** Outer beam color (default: "#aaccff") */
  outerColor?: string;

  /** Glow color (default: "#88aaff") */
  glowColor?: string;

  /** Impact color at target (default: same as coreColor) */
  impactColor?: string;

  /** Spark color (default: same as coreColor) */
  sparkColor?: string;

  /** Overall alpha transparency 0-1 (default: 1.0) */
  alpha?: number;

  // Effects
  /** Enable glow effect (default: true) */
  glow?: boolean;

  /** Enable sparks along path (default: false) */
  sparks?: boolean;

  /** Enable impact effect at target (default: true) */
  impact?: boolean;

  /** Impact radius (default: 0.5) */
  impactRadius?: number;

  /** Enable source glow at wand tip (default: false) */
  sourceGlow?: boolean;

  /** Source glow radius (default: 0.3) */
  sourceGlowRadius?: number;

  // Beam Structure
  /** Number of beam strands (default: 2, range: 1-5) */
  strandCount?: number;

  // Style Preset
  /** Wand style preset (default: 'basic') */
  wandStyle?: WandStyle;

  // Timing
  /** Fade-in duration at start (default: 0.2) */
  fadeInDuration?: number;

  // Particle Properties
  /** Particles per system (default: 1000) */
  particleCount?: number;

  /** Particle rotation enabled (default: false) */
  particleRotation?: boolean;

  /** Angular velocity for rotation in radians/second (default: 0) */
  angularVelocity?: number;

  /** Blending mode (default: 'add') */
  blend?: 'add' | 'alpha' | 'multiply';
}

/**
 * ParticleWandFlashSteadyEffect - Endless magic wand beam
 *
 * Usage examples:
 *
 * Endless basic beam:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleWandFlashSteady",
 *   "ctx": {
 *     "source": {"x": 0, "y": 66, "z": 0},
 *     "target": {"x": 10, "y": 65, "z": 10},
 *     "wandStyle": "basic"
 *   }
 * }
 * ```
 *
 * Continuous healing beam:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleWandFlashSteady",
 *   "ctx": {
 *     "source": {"x": 0, "y": 66, "z": 0},
 *     "target": {"x": 10, "y": 65, "z": 10},
 *     "wandStyle": "healing",
 *     "animationMode": "pulsing"
 *   }
 * }
 * ```
 *
 * Bidirectional energy flow:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleWandFlashSteady",
 *   "ctx": {
 *     "source": {"x": 0, "y": 66, "z": 0},
 *     "target": {"x": 10, "y": 65, "z": 10},
 *     "wandStyle": "dark",
 *     "animationMode": "bidirectional",
 *     "pathStyle": "wave"
 *   }
 * }
 * ```
 */
export class ParticleWandFlashSteadyEffect extends ScrawlEffectHandler<ParticleWandFlashSteadyOptions> {
  private particleSystems: ParticleSystem[] = [];
  private startTime: number = 0;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;
  private particleTexture: RawTexture | null = null;
  private beamPath: Vector3[] = [];
  private sourcePos: Vector3 | null = null;
  private targetPos: Vector3 | null = null;

  isSteadyEffect(): boolean {
    return true; // Steady effect
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    try {
      // Parse positions
      this.sourcePos = new Vector3(
        this.options.source.x,
        this.options.source.y,
        this.options.source.z
      );
      this.targetPos = new Vector3(
        this.options.target.x,
        this.options.target.y,
        this.options.target.z
      );

      // Apply preset
      this.applyPreset();

      // Parse parameters
      const thickness = this.options.thickness ?? 0.15;
      const intensity = this.options.intensity ?? 1.0;
      const pathStyle = this.options.pathStyle ?? 'straight';
      const zigzag = this.options.zigzag ?? 0.3;
      const curvature = this.options.curvature ?? 0.0;

      const animationMode = this.options.animationMode ?? 'continuous';
      const travelSpeed = this.options.travelSpeed ?? 5.0;
      const pulseFrequency = this.options.pulseFrequency ?? 3.0;

      const enableGlow = this.options.glow ?? true;
      const enableSparks = this.options.sparks ?? false;
      const enableImpact = this.options.impact ?? true;
      const impactRadius = this.options.impactRadius ?? 0.5;

      const particleCount = this.options.particleCount ?? 1000;
      const fadeInDuration = this.options.fadeInDuration ?? 0.2;

      // Colors
      const coreColor = this.parseColor(this.options.coreColor ?? '#ffffff');
      const outerColor = this.parseColor(this.options.outerColor ?? '#aaccff');
      const glowColor = this.parseColor(this.options.glowColor ?? '#88aaff');
      const impactColor = this.parseColor(this.options.impactColor ?? this.options.coreColor ?? '#ffffff');

      // Calculate beam path
      this.calculateBeamPath(pathStyle, zigzag, curvature);

      // Create texture
      this.createParticleTexture();

      // Create main beam (core) - no fadeOut for steady
      this.createBeamCore(
        thickness,
        intensity,
        coreColor,
        particleCount,
        animationMode,
        travelSpeed,
        pulseFrequency,
        fadeInDuration
      );

      // Create outer beam
      this.createBeamOuter(
        thickness * 2,
        intensity * 0.7,
        outerColor,
        Math.floor(particleCount * 0.7),
        animationMode,
        travelSpeed,
        pulseFrequency,
        fadeInDuration
      );

      // Create glow
      if (enableGlow) {
        this.createGlowEffect(
          thickness * 3,
          intensity * 0.5,
          glowColor,
          Math.floor(particleCount * 0.5),
          animationMode,
          travelSpeed,
          pulseFrequency,
          fadeInDuration
        );
      }

      // Create sparks
      if (enableSparks) {
        this.createSparkEffect(
          thickness,
          intensity,
          coreColor,
          Math.floor(particleCount * 0.3),
          fadeInDuration
        );
      }

      // Create impact
      if (enableImpact) {
        this.createImpactEffect(
          this.targetPos,
          impactRadius,
          intensity,
          impactColor,
          Math.floor(particleCount * 0.3),
          fadeInDuration
        );
      }

      // Start animation
      this.startTime = this.now();
      this.animate();

      logger.debug('Wand flash steady effect started', {
        source: this.sourcePos,
        target: this.targetPos,
        animationMode,
      });
    } catch (error) {
      logger.error('Failed to create wand flash steady effect', { error });
      this.cleanup();
    }
  }

  private applyPreset(): void {
    const style = this.options.wandStyle ?? 'basic';

    switch (style) {
      case 'basic':
        this.options.thickness = this.options.thickness ?? 0.15;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.coreColor = this.options.coreColor ?? '#ffffff';
        this.options.outerColor = this.options.outerColor ?? '#aaccff';
        this.options.glowColor = this.options.glowColor ?? '#88aaff';
        break;

      case 'powerful':
        this.options.thickness = this.options.thickness ?? 0.3;
        this.options.intensity = this.options.intensity ?? 1.5;
        this.options.coreColor = this.options.coreColor ?? '#ffffff';
        this.options.outerColor = this.options.outerColor ?? '#ffaa00';
        this.options.glowColor = this.options.glowColor ?? '#ff8800';
        this.options.glow = this.options.glow ?? true;
        this.options.impact = this.options.impact ?? true;
        this.options.impactRadius = this.options.impactRadius ?? 1.0;
        break;

      case 'dark':
        this.options.thickness = this.options.thickness ?? 0.2;
        this.options.intensity = this.options.intensity ?? 1.2;
        this.options.coreColor = this.options.coreColor ?? '#aa00ff';
        this.options.outerColor = this.options.outerColor ?? '#440088';
        this.options.glowColor = this.options.glowColor ?? '#220044';
        this.options.pathStyle = this.options.pathStyle ?? 'wave';
        break;

      case 'healing':
        this.options.thickness = this.options.thickness ?? 0.2;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.coreColor = this.options.coreColor ?? '#ffffaa';
        this.options.outerColor = this.options.outerColor ?? '#88ff88';
        this.options.glowColor = this.options.glowColor ?? '#44ff44';
        this.options.animationMode = this.options.animationMode ?? 'pulsing';
        this.options.pulseFrequency = this.options.pulseFrequency ?? 2.0;
        this.options.sparks = this.options.sparks ?? true;
        break;

      case 'lightning':
        this.options.thickness = this.options.thickness ?? 0.2;
        this.options.intensity = this.options.intensity ?? 1.5;
        this.options.coreColor = this.options.coreColor ?? '#ffffff';
        this.options.outerColor = this.options.outerColor ?? '#aaddff';
        this.options.glowColor = this.options.glowColor ?? '#4488ff';
        this.options.pathStyle = this.options.pathStyle ?? 'zigzag';
        this.options.zigzag = this.options.zigzag ?? 0.7;
        break;

      case 'custom':
        // No preset - use all manual parameters
        break;
    }
  }

  private calculateBeamPath(pathStyle: PathStyle, zigzag: number, curvature: number): void {
    if (!this.sourcePos || !this.targetPos) return;

    this.beamPath = [];
    const segments = 30;
    const direction = this.targetPos.subtract(this.sourcePos);
    const distance = direction.length();

    for (let i = 0; i <= segments; i++) {
      const t = i / segments;
      let point = this.sourcePos.add(direction.scale(t));

      switch (pathStyle) {
        case 'zigzag':
          if (i > 0 && i < segments) {
            const zigzagAmount = zigzag * (distance / segments) * 0.5;
            const offsetX = (Math.random() - 0.5) * zigzagAmount;
            const offsetY = (Math.random() - 0.5) * zigzagAmount * 0.5;
            const offsetZ = (Math.random() - 0.5) * zigzagAmount;
            point = point.add(new Vector3(offsetX, offsetY, offsetZ));
          }
          break;

        case 'wave':
          const waveFrequency = 2;
          const waveAmplitude = curvature * distance * 0.1;
          const perpendicular = Vector3.Cross(direction, Vector3.Up()).normalize();
          const waveOffset = Math.sin(t * Math.PI * waveFrequency) * waveAmplitude;
          point = point.add(perpendicular.scale(waveOffset));
          break;

        case 'spiral':
          const spiralFrequency = 3;
          const spiralRadius = curvature * distance * 0.1;
          const perp1 = Vector3.Cross(direction, Vector3.Up()).normalize();
          const perp2 = Vector3.Cross(direction, perp1).normalize();
          const angle = t * Math.PI * 2 * spiralFrequency;
          const spiralOffset = perp1.scale(Math.cos(angle) * spiralRadius)
            .add(perp2.scale(Math.sin(angle) * spiralRadius));
          point = point.add(spiralOffset);
          break;

        case 'straight':
        default:
          // No modifications - straight line
          break;
      }

      this.beamPath.push(point);
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

  private createBeamCore(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    animationMode: AnimationMode,
    travelSpeed: number,
    pulseFrequency: number,
    fadeInDuration: number
  ): void {
    if (!this.scene || this.beamPath.length === 0) return;

    const ps = new ParticleSystem('wandCoreSteady', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.sourcePos!.clone();

    ps.minSize = thickness * 0.6;
    ps.maxSize = thickness * 1.0;
    ps.minLifeTime = 0.3;
    ps.maxLifeTime = 0.5;

    ps.emitRate = particleCount * 5;

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    this.applyAnimationMode(
      ps,
      animationMode,
      travelSpeed,
      pulseFrequency,
      color,
      intensity,
      fadeInDuration
    );

    ps.start();
    this.particleSystems.push(ps);
  }

  private createBeamOuter(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    animationMode: AnimationMode,
    travelSpeed: number,
    pulseFrequency: number,
    fadeInDuration: number
  ): void {
    if (!this.scene || this.beamPath.length === 0) return;

    const ps = new ParticleSystem('wandOuterSteady', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.sourcePos!.clone();

    ps.minSize = thickness * 0.5;
    ps.maxSize = thickness * 0.8;
    ps.minLifeTime = 0.3;
    ps.maxLifeTime = 0.5;

    ps.emitRate = particleCount * 4;

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    this.applyAnimationMode(
      ps,
      animationMode,
      travelSpeed,
      pulseFrequency,
      color,
      intensity,
      fadeInDuration
    );

    ps.start();
    this.particleSystems.push(ps);
  }

  private createGlowEffect(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    animationMode: AnimationMode,
    travelSpeed: number,
    pulseFrequency: number,
    fadeInDuration: number
  ): void {
    if (!this.scene || this.beamPath.length === 0) return;

    const ps = new ParticleSystem('wandGlowSteady', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.sourcePos!.clone();

    ps.minSize = thickness * 1.5;
    ps.maxSize = thickness * 2.5;
    ps.minLifeTime = 0.4;
    ps.maxLifeTime = 0.6;

    ps.emitRate = particleCount * 3;

    ps.minEmitPower = 0.01;
    ps.maxEmitPower = 0.01;
    ps.gravity = Vector3.Zero();

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.01;

    this.applyAnimationMode(
      ps,
      animationMode,
      travelSpeed,
      pulseFrequency,
      color,
      intensity,
      fadeInDuration
    );

    ps.start();
    this.particleSystems.push(ps);
  }

  private createSparkEffect(
    thickness: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene || this.beamPath.length === 0) return;

    const ps = new ParticleSystem('wandSparksSteady', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = this.sourcePos!.clone();

    ps.minSize = thickness * 0.1;
    ps.maxSize = thickness * 0.2;
    ps.minLifeTime = 0.2;
    ps.maxLifeTime = 0.4;

    ps.emitRate = particleCount * 2;

    ps.direction1 = new Vector3(-1, -1, -1);
    ps.direction2 = new Vector3(1, 1, 1);
    ps.minEmitPower = 1;
    ps.maxEmitPower = 3;
    ps.gravity = new Vector3(0, -5, 0);

    // Color gradient
    ps.addColorGradient(0.0, color);
    ps.addColorGradient(0.5, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    this.applyFadeInOnly(ps, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private createImpactEffect(
    position: Vector3,
    radius: number,
    intensity: number,
    color: Color4,
    particleCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('wandImpactSteady', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone();

    ps.minSize = radius * 0.3;
    ps.maxSize = radius * 0.6;
    ps.minLifeTime = 0.2;
    ps.maxLifeTime = 0.4;

    ps.emitRate = particleCount * 5;

    // Radial outward
    ps.createSphereEmitter(0.1, 1);
    ps.minEmitPower = 2;
    ps.maxEmitPower = 4;
    ps.gravity = Vector3.Zero();

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

    this.applyFadeInOnly(ps, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private applyAnimationMode(
    ps: ParticleSystem,
    animationMode: AnimationMode,
    travelSpeed: number,
    pulseFrequency: number,
    color: Color4,
    intensity: number,
    fadeInDuration: number
  ): void {
    const path = this.beamPath;
    const startTime = this.now();

    ps.updateFunction = (particles: any) => {
      const elapsed = this.now() - startTime;

      // Calculate fade (only fade-in for steady)
      let fadeMultiplier = 1.0;
      if (elapsed < fadeInDuration) {
        fadeMultiplier = elapsed / fadeInDuration;
      }

      // Calculate animation multiplier
      let animMultiplier = 1.0;
      switch (animationMode) {
        case 'pulsing':
          animMultiplier = 0.6 + 0.4 * Math.sin(elapsed * pulseFrequency * Math.PI * 2);
          break;

        case 'traveling':
          // Handled per particle below
          break;

        case 'bidirectional':
          // Handled per particle below
          break;

        case 'continuous':
        default:
          animMultiplier = 1.0;
      }

      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        // Position along path
        let pathProgress = particle._pathProgress ?? Math.random();

        if (animationMode === 'traveling') {
          // Particle travels from start to end
          const particleStartTime = particle._startTime ?? elapsed;
          if (!particle._startTime) {
            particle._startTime = particleStartTime;
          }
          const travelTime = elapsed - particleStartTime;
          pathProgress = Math.min(travelTime * travelSpeed * 0.1, 1.0);
          particle._pathProgress = pathProgress;
        } else if (animationMode === 'bidirectional') {
          // Particle travels back and forth
          const cycle = (elapsed * travelSpeed * 0.05) % 2.0;
          pathProgress = cycle > 1.0 ? 2.0 - cycle : cycle;
        }

        const segmentIndex = Math.floor(pathProgress * (path.length - 1));

        if (segmentIndex >= 0 && segmentIndex < path.length) {
          particle.position = path[segmentIndex].clone();

          // Small random offset for thickness variation
          const offset = (Math.random() - 0.5) * this.options.thickness! * 0.3;
          particle.position.x += offset;
          particle.position.z += offset;
        }

        // Apply fade and animation
        particle.color.a = color.a * intensity * fadeMultiplier * animMultiplier;

        if (!particle._pathProgress && animationMode === 'continuous') {
          particle._pathProgress = pathProgress;
        }
      }
    };

    // Initialize particles
    const originalStart = ps.startPositionFunction;
    ps.startPositionFunction = (worldMatrix: any, positionToUpdate: Vector3, particle: any) => {
      if (originalStart) {
        originalStart(worldMatrix, positionToUpdate, particle, false);
      }
      if (animationMode === 'continuous') {
        particle._pathProgress = Math.random();
      }
    };
  }

  private applyFadeInOnly(
    ps: ParticleSystem,
    fadeInDuration: number
  ): void {
    const startTime = this.now();

    ps.updateFunction = (particles: any) => {
      const elapsed = this.now() - startTime;

      let fadeMultiplier = 1.0;
      if (elapsed < fadeInDuration) {
        fadeMultiplier = elapsed / fadeInDuration;
      }

      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        const originalAlpha = particle._originalAlpha ?? particle.color.a;
        if (!particle._originalAlpha) {
          particle._originalAlpha = originalAlpha;
        }
        particle.color.a = originalAlpha * fadeMultiplier;
      }
    };
  }

  private animate = () => {
    // Endless - only stop when stop() is called
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

    this.beamPath = [];

    logger.debug('Wand flash steady effect cleaned up');
  }

  onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
    let needsPathRecalculation = false;

    // Update source position
    if (paramName === 'source' || ctx.actor) {
      const source = paramName === 'source' ? value : ctx.actor;
      if (source?.position || source?.x !== undefined) {
        const newSource = new Vector3(
          source.position?.x ?? source.x,
          source.position?.y ?? source.y,
          source.position?.z ?? source.z
        );
        this.sourcePos = newSource;
        needsPathRecalculation = true;

        logger.debug('Wand flash steady source position updated', { paramName, newSource });
      }
    }

    // Update target position
    if (paramName === 'target' || ctx.patients?.[0]) {
      const target = paramName === 'target' ? value : ctx.patients?.[0];
      if (target?.position || target?.x !== undefined) {
        const newTarget = new Vector3(
          target.position?.x ?? target.x,
          target.position?.y ?? target.y,
          target.position?.z ?? target.z
        );
        this.targetPos = newTarget;
        needsPathRecalculation = true;

        logger.debug('Wand flash steady target position updated', { paramName, newTarget });
      }
    }

    // Recalculate beam path if positions changed
    if (needsPathRecalculation && this.sourcePos && this.targetPos) {
      const pathStyle = this.options.pathStyle ?? 'straight';
      const zigzag = this.options.zigzag ?? 0.3;
      const curvature = this.options.curvature ?? 0.0;
      this.calculateBeamPath(pathStyle, zigzag, curvature);
    }
  }

  stop(): void {
    this.cleanup();
  }

  isRunning(): boolean {
    return this.particleSystems.length > 0 && this.animationHandle !== null;
  }
}
