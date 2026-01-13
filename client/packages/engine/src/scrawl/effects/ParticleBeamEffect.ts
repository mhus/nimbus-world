/**
 * ParticleBeamEffect - Magical beam effect between two positions
 *
 * Creates highly configurable particle beams with three intertwining strands,
 * fraying effects, various beam styles, and advanced visual options.
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

const logger = getLogger('ParticleBeamEffect');

/**
 * Options for ParticleBeamEffect
 */
export interface ParticleBeamOptions {
  /** Start position */
  startPosition: { x: number; y: number; z: number };

  /** End position */
  endPosition: { x: number; y: number; z: number };

  /** First beam color (hex format, e.g., "#ff0000") */
  color1: string;

  /** Second beam color (hex format, e.g., "#00ff00") */
  color2: string;

  /** Third beam color (hex format, e.g., "#0000ff") */
  color3: string;

  /** Total duration of the beam effect in seconds */
  duration: number;

  /** Thickness of the beam strands (default: 0.1) */
  thickness?: number;

  /** Alpha transparency of the beam (0.0 = fully transparent, 1.0 = fully opaque, default: 1.0) */
  alpha?: number;

  /** Speed multiplier for particle movement (default: 1.0) */
  speed?: number;

  /** Weight of color1 strand (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color1Weight?: number;

  /** Weight of color2 strand (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color2Weight?: number;

  /** Weight of color3 strand (0.0 = invisible, 1.0 = full opacity, default: 1.0) */
  color3Weight?: number;

  /** Setup duration - time to build beam from start to end (default: 0.2s) */
  setupDuration?: number;

  /** Fade duration - time to fade out at the end (default: 0.2s) */
  fadeDuration?: number;

  // Fraying Parameters
  /** Fraying intensity - how much the beam frays at the edges (0.0 = smooth, 1.0 = heavily frayed, default: 0.0) */
  fraying?: number;

  /** Distance along beam where fraying starts (0.0 = start, 1.0 = end, default: 0.5) */
  frayingDistance?: number;

  /** Noise factor for fraying randomness (default: 1.0) */
  frayingNoise?: number;

  // Spiral Control
  /** Spiral rotation speed in rotations per second (default: 4.0) */
  spiralSpeed?: number;

  /** Spiral radius multiplier (default: 2.0, multiplied by thickness) */
  spiralRadius?: number;

  /** Spiral pattern style (default: 'helix') */
  spiralPattern?: 'helix' | 'twist' | 'wave' | 'none';

  // Beam Behavior
  /** Beam style (default: 'continuous') */
  beamStyle?: 'continuous' | 'pulsing' | 'flickering' | 'lightning';

  /** Pulse frequency in Hz for pulsing beams (default: 2.0) */
  pulseFrequency?: number;

  /** Turbulence for lightning-style beams (0.0 = smooth, 1.0 = very turbulent, default: 0.0) */
  turbulence?: number;

  // Particle Properties
  /** Number of particles per strand (default: 2000) */
  particleCount?: number;

  /** Particle emission rate override (default: auto-calculated based on beam length) */
  emitRate?: number;

  /** Particle lifetime in seconds (default: 0.1-0.3) */
  particleLifetime?: number;

  // Beam Geometry
  /** Beam tapering at ends (default: 'none') */
  beamTaper?: 'none' | 'start' | 'end' | 'both';

  /** Separation distance between strands (default: 1.0) */
  strandSeparation?: number;

  // Advanced Effects
  /** Enable glow effect around beam (default: false) */
  glow?: boolean;

  /** Glow intensity multiplier (default: 1.0) */
  glowIntensity?: number;

  /** Core color for beam center (optional, hex format) */
  coreColor?: string;

  // Visuals
  /** Blending mode (default: 'add') */
  blend?: 'add' | 'alpha' | 'multiply';
}

/**
 * ParticleBeamEffect - Creates highly configurable particle beams
 *
 * Usage examples:
 *
 * Basic magical beam:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleBeam",
 *   "ctx": {
 *     "startPosition": {"x": 0, "y": 65, "z": 0},
 *     "endPosition": {"x": 10, "y": 65, "z": 10},
 *     "color1": "#ff0000",
 *     "color2": "#00ff00",
 *     "color3": "#0000ff",
 *     "duration": 2.0
 *   }
 * }
 * ```
 *
 * Frayed lightning beam:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleBeam",
 *   "ctx": {
 *     "startPosition": {"x": 0, "y": 70, "z": 0},
 *     "endPosition": {"x": 10, "y": 60, "z": 10},
 *     "color1": "#ffffff",
 *     "color2": "#aaccff",
 *     "color3": "#ffffff",
 *     "duration": 0.5,
 *     "beamStyle": "lightning",
 *     "fraying": 0.8,
 *     "turbulence": 0.5,
 *     "thickness": 0.15
 *   }
 * }
 * ```
 *
 * Pulsing beam with glow:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleBeam",
 *   "ctx": {
 *     "startPosition": {"x": 0, "y": 65, "z": 0},
 *     "endPosition": {"x": 10, "y": 65, "z": 10},
 *     "color1": "#ff00ff",
 *     "color2": "#ff00ff",
 *     "color3": "#ff00ff",
 *     "duration": 3.0,
 *     "beamStyle": "pulsing",
 *     "pulseFrequency": 3.0,
 *     "glow": true,
 *     "glowIntensity": 1.5
 *   }
 * }
 * ```
 */
export class ParticleBeamEffect extends ScrawlEffectHandler<ParticleBeamOptions> {
  private particleSystems: ParticleSystem[] = [];
  private glowSystems: ParticleSystem[] = [];
  private startTime: number = 0;
  private startPos: Vector3 | null = null;
  private endPos: Vector3 | null = null;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;
  private particleTexture: RawTexture | null = null;
  private glowTexture: RawTexture | null = null;

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    try {
      // Parse positions
      this.startPos = new Vector3(
        this.options.startPosition.x,
        this.options.startPosition.y,
        this.options.startPosition.z
      );
      this.endPos = new Vector3(
        this.options.endPosition.x,
        this.options.endPosition.y,
        this.options.endPosition.z
      );

      // Parse parameters
      const thickness = this.options.thickness ?? 0.1;
      const alpha = this.options.alpha ?? 1.0;
      const speed = this.options.speed ?? 1.0;
      const setupDuration = this.options.setupDuration ?? 0.2;
      const particleCount = this.options.particleCount ?? 2000;
      const particleLifetime = this.options.particleLifetime ?? 0.5;

      // Fraying parameters
      const fraying = this.options.fraying ?? 0.0;
      const frayingDistance = this.options.frayingDistance ?? 0.5;
      const frayingNoise = this.options.frayingNoise ?? 1.0;

      // Spiral parameters
      const spiralSpeed = this.options.spiralSpeed ?? 4.0;
      const spiralRadiusMultiplier = this.options.spiralRadius ?? 2.0;
      const spiralRadius = thickness * spiralRadiusMultiplier;
      const spiralPattern = this.options.spiralPattern ?? 'helix';

      // Beam style parameters
      const beamStyle = this.options.beamStyle ?? 'continuous';
      const pulseFrequency = this.options.pulseFrequency ?? 2.0;
      const turbulence = this.options.turbulence ?? 0.0;

      // Geometry parameters
      const beamTaper = this.options.beamTaper ?? 'none';
      const strandSeparation = this.options.strandSeparation ?? 1.0;

      // Visual parameters
      const blend = this.options.blend ?? 'add';
      const enableGlow = this.options.glow ?? false;
      const glowIntensity = this.options.glowIntensity ?? 1.0;

      // Color weights for individual strands
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

      const coreColor = this.options.coreColor ? this.parseColor(this.options.coreColor) : null;

      const direction = this.endPos.subtract(this.startPos);
      const distance = direction.length();

      // Create textures
      this.createParticleTexture();
      if (enableGlow) {
        this.createGlowTexture();
      }

      // Create main beam particle systems
      for (let i = 0; i < 3; i++) {
        // Skip this strand if weight is 0
        if (colorWeights[i] === 0) {
          continue;
        }

        const particleSystem = new ParticleSystem(
          `beamStrand${i}`,
          particleCount,
          this.scene
        );

        // Use the procedural circular texture
        particleSystem.particleTexture = this.particleTexture;

        // Emission from start point
        particleSystem.emitter = this.startPos.clone();

        // Particle appearance
        particleSystem.minSize = thickness * 0.5;
        particleSystem.maxSize = thickness * 1.5;
        particleSystem.minLifeTime = particleLifetime * 0.5;
        particleSystem.maxLifeTime = particleLifetime * 1.5;

        // Color
        const strandColor = coreColor && i === 1 ? coreColor : colors[i];
        particleSystem.color1 = strandColor;
        particleSystem.color2 = strandColor;
        particleSystem.colorDead = new Color4(strandColor.r, strandColor.g, strandColor.b, 0);

        // Emission rate (increased for better visibility)
        const emitRate = this.options.emitRate ?? Math.max(1000, 500 * (distance / 10));
        particleSystem.emitRate = emitRate;

        // Direction along beam (very small variation for emission)
        particleSystem.direction1 = direction.normalize().scale(0.01);
        particleSystem.direction2 = direction.normalize().scale(0.01);

        // Speed (minimal, since we position manually in update function)
        particleSystem.minEmitPower = 0.1;
        particleSystem.maxEmitPower = 0.1;

        // Gravity and forces
        particleSystem.gravity = Vector3.Zero();

        // Blending mode
        this.setBlendMode(particleSystem, blend);

        // Custom update function for advanced effects
        const phaseOffset = (i * Math.PI * 2) / 3; // 120 degrees apart
        const strandIndex = i;

        particleSystem.updateFunction = (particles: any) => {
          const currentTime = this.now() - this.startTime;
          const beamProgress = Math.min(currentTime / setupDuration, 1.0);

          for (const particle of particles) {
            particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

            if (particle.age >= particle.lifeTime) {
              particles.splice(particles.indexOf(particle), 1);
              continue;
            }

            // Calculate position along beam
            const normalizedAge = particle.age / particle.lifeTime;
            // After setup is complete (beamProgress = 1.0), particles travel the full length
            const beamPosition = normalizedAge * distance * Math.max(beamProgress, 0.3);
            const beamProgressNormalized = Math.min(beamPosition / distance, 1.0);

            // Apply spiral pattern
            let spiralOffset = Vector3.Zero();
            if (spiralPattern !== 'none') {
              spiralOffset = this.calculateSpiralOffset(
                beamPosition,
                spiralSpeed,
                spiralRadius * strandSeparation,
                phaseOffset,
                particle._randomOffset,
                direction,
                spiralPattern
              );
            }

            // Apply fraying
            if (fraying > 0 && beamProgressNormalized >= frayingDistance) {
              const frayFactor = (beamProgressNormalized - frayingDistance) / (1.0 - frayingDistance);
              const frayAmount = fraying * frayFactor * frayingNoise;
              const frayX = (Math.random() - 0.5) * frayAmount * thickness * 10;
              const frayY = (Math.random() - 0.5) * frayAmount * thickness * 10;
              const frayZ = (Math.random() - 0.5) * frayAmount * thickness * 10;
              spiralOffset = spiralOffset.add(new Vector3(frayX, frayY, frayZ));
            }

            // Apply turbulence for lightning effect
            if (turbulence > 0) {
              const turbX = (Math.random() - 0.5) * turbulence * thickness * 5;
              const turbY = (Math.random() - 0.5) * turbulence * thickness * 5;
              const turbZ = (Math.random() - 0.5) * turbulence * thickness * 5;
              spiralOffset = spiralOffset.add(new Vector3(turbX, turbY, turbZ));
            }

            // Set particle position
            particle.position = this.startPos!.clone()
              .add(direction.normalize().scale(beamPosition))
              .add(spiralOffset);

            // Calculate alpha based on fade, beam style, and taper
            let baseAlpha = alpha * colorWeights[strandIndex];

            // Fade in/out
            const fadeInProgress = Math.min(currentTime / (setupDuration * 0.5), 1.0);
            const fadeOutStart = this.options.duration - (this.options.fadeDuration ?? 0.2);
            const fadeOutProgress = currentTime > fadeOutStart
              ? 1.0 - Math.min((currentTime - fadeOutStart) / (this.options.fadeDuration ?? 0.2), 1.0)
              : 1.0;

            baseAlpha *= fadeInProgress * fadeOutProgress;

            // Apply beam style
            baseAlpha *= this.calculateBeamStyleMultiplier(
              beamStyle,
              currentTime,
              pulseFrequency,
              particle._randomOffset
            );

            // Apply beam taper
            baseAlpha *= this.calculateTaperMultiplier(beamTaper, beamProgressNormalized);

            particle.color.a = strandColor.a * baseAlpha;

            // Apply size taper
            const taperSize = this.calculateTaperMultiplier(beamTaper, beamProgressNormalized);
            particle.size = (thickness * 0.5 + (thickness * 1.0 * normalizedAge)) * taperSize;
          }
        };

        // Add random offset to each particle on creation
        const originalStartParticle = particleSystem.startPositionFunction;
        particleSystem.startPositionFunction = (
          worldMatrix: any,
          positionToUpdate: Vector3,
          particle: any,
          isLocal: boolean
        ) => {
          if (originalStartParticle) {
            originalStartParticle(worldMatrix, positionToUpdate, particle, isLocal);
          }
          particle._randomOffset = Math.random() * Math.PI * 2;
        };

        particleSystem.start();
        this.particleSystems.push(particleSystem);
      }

      // Create glow effect if enabled
      if (enableGlow && this.glowTexture) {
        this.createGlowSystem(
          this.startPos,
          direction,
          distance,
          thickness,
          colors,
          colorWeights,
          alpha,
          glowIntensity,
          setupDuration,
          particleLifetime
        );
      }

      // Start animation
      this.startTime = this.now();
      this.animate();

      logger.debug('Particle beam effect started', {
        distance,
        beamStyle,
        fraying,
      });
    } catch (error) {
      logger.error('Failed to create particle beam effect', { error });
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

  private createGlowTexture(): void {
    if (this.glowTexture || !this.scene) {
      return;
    }

    const textureSize = 32;
    const textureData = new Uint8Array(textureSize * textureSize * 4);
    const center = textureSize / 2;

    for (let y = 0; y < textureSize; y++) {
      for (let x = 0; x < textureSize; x++) {
        const dx = x - center + 0.5;
        const dy = y - center + 0.5;
        const dist = Math.sqrt(dx * dx + dy * dy) / center;
        // Softer falloff for glow
        const texAlpha = Math.max(0, Math.pow(1 - dist, 2));

        const index = (y * textureSize + x) * 4;
        textureData[index] = 255;     // R
        textureData[index + 1] = 255; // G
        textureData[index + 2] = 255; // B
        textureData[index + 3] = Math.floor(texAlpha * 255); // A
      }
    }

    this.glowTexture = RawTexture.CreateRGBATexture(
      textureData,
      textureSize,
      textureSize,
      this.scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );
  }

  private createGlowSystem(
    startPos: Vector3,
    direction: Vector3,
    distance: number,
    thickness: number,
    colors: Color4[],
    colorWeights: number[],
    alpha: number,
    glowIntensity: number,
    setupDuration: number,
    particleLifetime: number
  ): void {
    if (!this.scene || !this.glowTexture) {
      return;
    }

    // Create a single glow system with average color
    const avgColor = new Color4(
      (colors[0].r * colorWeights[0] + colors[1].r * colorWeights[1] + colors[2].r * colorWeights[2]) / 3,
      (colors[0].g * colorWeights[0] + colors[1].g * colorWeights[1] + colors[2].g * colorWeights[2]) / 3,
      (colors[0].b * colorWeights[0] + colors[1].b * colorWeights[1] + colors[2].b * colorWeights[2]) / 3,
      alpha * 0.3 * glowIntensity
    );

    const glowSystem = new ParticleSystem(
      'beamGlow',
      1000,
      this.scene
    );

    glowSystem.particleTexture = this.glowTexture;
    glowSystem.emitter = startPos.clone();

    // Larger particles for glow
    glowSystem.minSize = thickness * 3;
    glowSystem.maxSize = thickness * 5;
    glowSystem.minLifeTime = particleLifetime * 0.5;
    glowSystem.maxLifeTime = particleLifetime * 1.5;

    glowSystem.color1 = avgColor;
    glowSystem.color2 = avgColor;
    glowSystem.colorDead = new Color4(avgColor.r, avgColor.g, avgColor.b, 0);

    glowSystem.emitRate = 200 * (distance / 10);
    glowSystem.direction1 = direction.normalize();
    glowSystem.direction2 = direction.normalize();

    const baseSpeed = distance / setupDuration;
    glowSystem.minEmitPower = baseSpeed * 0.9;
    glowSystem.maxEmitPower = baseSpeed * 1.1;

    glowSystem.gravity = Vector3.Zero();
    glowSystem.blendMode = ParticleSystem.BLENDMODE_ADD;

    glowSystem.start();
    this.glowSystems.push(glowSystem);
  }

  private calculateSpiralOffset(
    beamPosition: number,
    spiralSpeed: number,
    spiralRadius: number,
    phaseOffset: number,
    randomOffset: number,
    direction: Vector3,
    pattern: 'helix' | 'twist' | 'wave'
  ): Vector3 {
    let spiralAngle: number;

    switch (pattern) {
      case 'helix':
        spiralAngle = beamPosition * spiralSpeed + phaseOffset + randomOffset;
        break;
      case 'twist':
        // Twisting pattern - angle increases with distance
        spiralAngle = Math.pow(beamPosition * 0.1, 2) * spiralSpeed + phaseOffset;
        break;
      case 'wave':
        // Wave pattern - sinusoidal
        spiralAngle = Math.sin(beamPosition * spiralSpeed * 0.5) * Math.PI + phaseOffset;
        break;
      default:
        spiralAngle = 0;
    }

    const perpendicular1 = Vector3.Cross(direction, Vector3.Up());
    if (perpendicular1.lengthSquared() < 0.0001) {
      // If beam is vertical, use a different reference vector
      perpendicular1.copyFrom(Vector3.Cross(direction, Vector3.Right()));
    }
    perpendicular1.normalize();
    const perpendicular2 = Vector3.Cross(direction, perpendicular1).normalize();

    return perpendicular1.scale(Math.cos(spiralAngle) * spiralRadius)
      .add(perpendicular2.scale(Math.sin(spiralAngle) * spiralRadius));
  }

  private calculateBeamStyleMultiplier(
    beamStyle: 'continuous' | 'pulsing' | 'flickering' | 'lightning',
    currentTime: number,
    pulseFrequency: number,
    randomOffset: number
  ): number {
    switch (beamStyle) {
      case 'pulsing':
        return 0.5 + 0.5 * Math.sin(currentTime * pulseFrequency * Math.PI * 2);

      case 'flickering':
        // Random flicker with some smoothness
        const flickerBase = Math.sin(currentTime * 30 + randomOffset * 10);
        return Math.random() > 0.7 ? 0.3 : (0.7 + 0.3 * flickerBase);

      case 'lightning':
        // Combination of flicker and pulse
        const lightningFlicker = Math.random() > 0.8 ? Math.random() * 0.5 : 1.0;
        const lightningPulse = 0.7 + 0.3 * Math.sin(currentTime * 10);
        return lightningFlicker * lightningPulse;

      case 'continuous':
      default:
        return 1.0;
    }
  }

  private calculateTaperMultiplier(
    beamTaper: 'none' | 'start' | 'end' | 'both',
    progress: number
  ): number {
    switch (beamTaper) {
      case 'start':
        return Math.min(progress * 3, 1.0);

      case 'end':
        return Math.min((1.0 - progress) * 3, 1.0);

      case 'both':
        return Math.min(progress * 3, 1.0) * Math.min((1.0 - progress) * 3, 1.0);

      case 'none':
      default:
        return 1.0;
    }
  }

  private setBlendMode(
    particleSystem: ParticleSystem,
    blend: 'add' | 'alpha' | 'multiply'
  ): void {
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
  }

  private animate = () => {
    const elapsed = this.now() - this.startTime;

    // Stop after duration
    if (elapsed >= this.options.duration) {
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
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    for (const ps of this.particleSystems) {
      ps.stop();
      ps.dispose();
    }
    this.particleSystems = [];

    for (const gs of this.glowSystems) {
      gs.stop();
      gs.dispose();
    }
    this.glowSystems = [];

    if (this.particleTexture) {
      this.particleTexture.dispose();
      this.particleTexture = null;
    }

    if (this.glowTexture) {
      this.glowTexture.dispose();
      this.glowTexture = null;
    }

    logger.debug('Particle beam effect cleaned up');
  }

  stop(): void {
    this.cleanup();
  }
}
