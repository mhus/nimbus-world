/**
 * ParticleFireSteadyEffect - Realistic fire simulation with particles (Steady/Endless)
 *
 * Creates a multi-layered fire effect with core flames, outer flames, smoke, and sparks
 * that runs indefinitely. For limited duration fire, use ParticleFireEffect instead.
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
import type { FireStyle } from './ParticleFireEffect';

const logger = getLogger('ParticleFireSteadyEffect');

/**
 * Options for ParticleFireSteadyEffect
 */
export interface ParticleFireSteadyOptions {
  /** Position of the fire */
  position: { x: number; y: number; z: number };

  // Basic Properties
  /** Size of the fire (default: 1.0) */
  size?: number;

  /** Intensity 0-1 (default: 1.0) */
  intensity?: number;

  /** Height of flames (default: 2.0) */
  height?: number;

  // Colors
  /** Core color (hottest part, default: "#ffffff") */
  coreColor?: string;

  /** Inner flame color (default: "#ffff00") */
  innerFlameColor?: string;

  /** Mid flame color (default: "#ff8800") */
  midFlameColor?: string;

  /** Outer flame color (default: "#ff4400") */
  outerFlameColor?: string;

  /** Smoke color (default: "#333333") */
  smokeColor?: string;

  /** Spark color (default: "#ffaa00") */
  sparkColor?: string;

  // Behavior
  /** Turbulence/flickering 0-1 (default: 0.5) */
  turbulence?: number;

  /** Flicker speed (default: 5.0) */
  flickerSpeed?: number;

  /** Horizontal spread (default: 0.5) */
  spread?: number;

  /** Wind direction (optional) */
  wind?: { x: number; y: number; z: number };

  // Smoke System
  /** Enable smoke (default: true) */
  smoke?: boolean;

  /** Smoke amount 0-1 (default: 0.5) */
  smokeAmount?: number;

  /** How high smoke rises (default: 4.0) */
  smokeHeight?: number;

  // Sparks
  /** Enable sparks (default: true) */
  sparks?: boolean;

  /** Number of sparks (default: 50) */
  sparkCount?: number;

  /** Spark intensity 0-1 (default: 0.5) */
  sparkIntensity?: number;

  // Style Preset
  /** Fire style preset (default: 'campfire') */
  fireStyle?: FireStyle;

  // Timing (fade-in for steady effect)
  /** Fade-in duration in seconds (default: 0.5) */
  fadeInDuration?: number;

  // Particle Properties
  /** Particles per layer (default: 500) */
  particleCount?: number;

  /** Emission rate override (default: auto-calculated) */
  emitRate?: number;
}

/**
 * ParticleFireSteadyEffect - Creates realistic fire with multiple particle layers (Endless)
 *
 * Usage examples:
 *
 * Steady campfire:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleFireSteady",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "fireStyle": "torch"
 *   }
 * }
 * ```
 *
 * Steady magical fire:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleFireSteady",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "fireStyle": "magical",
 *     "turbulence": 0.8,
 *     "sparks": true
 *   }
 * }
 * ```
 *
 * Steady custom blue fire:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "particleFireSteady",
 *   "ctx": {
 *     "position": {"x": 0, "y": 65, "z": 0},
 *     "fireStyle": "blue",
 *     "size": 2.0,
 *     "height": 5.0,
 *     "smoke": false
 *   }
 * }
 * ```
 */
export class ParticleFireSteadyEffect extends ScrawlEffectHandler<ParticleFireSteadyOptions> {
  private particleSystems: ParticleSystem[] = [];
  private startTime: number = 0;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;
  private particleTexture: RawTexture | null = null;
  private firePosition: Vector3 | null = null;

  isSteadyEffect(): boolean {
    return true; // Endless effect
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
      this.firePosition = new Vector3(
        this.options.position.x,
        this.options.position.y,
        this.options.position.z
      );

      // Apply preset
      this.applyPreset();

      // Parse parameters
      const size = this.options.size ?? 1.0;
      const intensity = this.options.intensity ?? 1.0;
      const height = this.options.height ?? 2.0;

      const turbulence = this.options.turbulence ?? 0.5;
      const flickerSpeed = this.options.flickerSpeed ?? 5.0;
      const spread = this.options.spread ?? 0.5;

      const enableSmoke = this.options.smoke ?? true;
      const smokeAmount = this.options.smokeAmount ?? 0.5;
      const smokeHeight = this.options.smokeHeight ?? 4.0;

      const enableSparks = this.options.sparks ?? true;
      const sparkCount = this.options.sparkCount ?? 50;
      const sparkIntensity = this.options.sparkIntensity ?? 0.5;

      const particleCount = this.options.particleCount ?? 500;

      const fadeInDuration = this.options.fadeInDuration ?? 0.5;

      // Wind
      const wind = this.options.wind
        ? new Vector3(this.options.wind.x, this.options.wind.y, this.options.wind.z)
        : Vector3.Zero();

      // Colors
      const coreColor = this.parseColor(this.options.coreColor ?? '#ffffff');
      const innerFlameColor = this.parseColor(this.options.innerFlameColor ?? '#ffff00');
      const midFlameColor = this.parseColor(this.options.midFlameColor ?? '#ff8800');
      const outerFlameColor = this.parseColor(this.options.outerFlameColor ?? '#ff4400');
      const smokeColor = this.parseColor(this.options.smokeColor ?? '#333333');
      const sparkColor = this.parseColor(this.options.sparkColor ?? '#ffaa00');

      // Create texture
      this.createParticleTexture();

      // Create fire layers
      this.createCoreLayer(
        this.firePosition,
        size,
        height,
        intensity,
        turbulence,
        flickerSpeed,
        spread,
        wind,
        coreColor,
        particleCount,
        fadeInDuration
      );

      this.createFlameLayer(
        this.firePosition,
        size,
        height,
        intensity,
        turbulence,
        flickerSpeed,
        spread,
        wind,
        innerFlameColor,
        'inner',
        particleCount,
        fadeInDuration
      );

      this.createFlameLayer(
        this.firePosition,
        size,
        height,
        intensity,
        turbulence,
        flickerSpeed,
        spread,
        wind,
        midFlameColor,
        'mid',
        particleCount,
        fadeInDuration
      );

      this.createFlameLayer(
        this.firePosition,
        size,
        height,
        intensity,
        turbulence,
        flickerSpeed,
        spread,
        wind,
        outerFlameColor,
        'outer',
        particleCount,
        fadeInDuration
      );

      // Create smoke layer
      if (enableSmoke) {
        this.createSmokeLayer(
          this.firePosition,
          size,
          smokeHeight,
          smokeAmount,
          turbulence,
          spread,
          wind,
          smokeColor,
          particleCount,
          fadeInDuration
        );
      }

      // Create spark layer
      if (enableSparks) {
        this.createSparkLayer(
          this.firePosition,
          size,
          height,
          sparkIntensity,
          turbulence,
          spread,
          wind,
          sparkColor,
          sparkCount,
          fadeInDuration
        );
      }

      // Start animation
      this.startTime = this.now();
      this.animate();

      logger.debug('Particle fire steady effect started', {
        position: this.firePosition,
      });
    } catch (error) {
      logger.error('Failed to create particle fire steady effect', { error });
      this.cleanup();
    }
  }

  private applyPreset(): void {
    const style = this.options.fireStyle ?? 'campfire';

    switch (style) {
      case 'campfire':
        this.options.size = this.options.size ?? 1.0;
        this.options.height = this.options.height ?? 2.0;
        this.options.intensity = this.options.intensity ?? 0.8;
        this.options.turbulence = this.options.turbulence ?? 0.5;
        this.options.smoke = this.options.smoke ?? true;
        this.options.smokeAmount = this.options.smokeAmount ?? 0.6;
        this.options.sparks = this.options.sparks ?? true;
        this.options.sparkIntensity = this.options.sparkIntensity ?? 0.4;
        break;

      case 'torch':
        this.options.size = this.options.size ?? 0.5;
        this.options.height = this.options.height ?? 3.0;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.turbulence = this.options.turbulence ?? 0.7;
        this.options.spread = this.options.spread ?? 0.3;
        this.options.smoke = this.options.smoke ?? true;
        this.options.smokeAmount = this.options.smokeAmount ?? 0.3;
        this.options.sparks = this.options.sparks ?? false;
        break;

      case 'bonfire':
        this.options.size = this.options.size ?? 2.0;
        this.options.height = this.options.height ?? 4.0;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.turbulence = this.options.turbulence ?? 0.6;
        this.options.smoke = this.options.smoke ?? true;
        this.options.smokeAmount = this.options.smokeAmount ?? 0.8;
        this.options.sparks = this.options.sparks ?? true;
        this.options.sparkIntensity = this.options.sparkIntensity ?? 0.7;
        this.options.sparkCount = this.options.sparkCount ?? 100;
        break;

      case 'magical':
        this.options.size = this.options.size ?? 1.5;
        this.options.height = this.options.height ?? 3.0;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.turbulence = this.options.turbulence ?? 0.8;
        this.options.coreColor = this.options.coreColor ?? '#ff00ff';
        this.options.innerFlameColor = this.options.innerFlameColor ?? '#ff00aa';
        this.options.midFlameColor = this.options.midFlameColor ?? '#aa00ff';
        this.options.outerFlameColor = this.options.outerFlameColor ?? '#8800ff';
        this.options.smoke = this.options.smoke ?? false;
        this.options.sparks = this.options.sparks ?? true;
        this.options.sparkIntensity = this.options.sparkIntensity ?? 0.8;
        this.options.sparkColor = this.options.sparkColor ?? '#ff88ff';
        break;

      case 'blue':
        this.options.size = this.options.size ?? 1.0;
        this.options.height = this.options.height ?? 2.5;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.coreColor = this.options.coreColor ?? '#aaddff';
        this.options.innerFlameColor = this.options.innerFlameColor ?? '#4488ff';
        this.options.midFlameColor = this.options.midFlameColor ?? '#0066ff';
        this.options.outerFlameColor = this.options.outerFlameColor ?? '#0044aa';
        this.options.smoke = this.options.smoke ?? true;
        this.options.smokeAmount = this.options.smokeAmount ?? 0.3;
        break;

      case 'green':
        this.options.size = this.options.size ?? 1.0;
        this.options.height = this.options.height ?? 2.5;
        this.options.intensity = this.options.intensity ?? 1.0;
        this.options.coreColor = this.options.coreColor ?? '#aaffaa';
        this.options.innerFlameColor = this.options.innerFlameColor ?? '#44ff44';
        this.options.midFlameColor = this.options.midFlameColor ?? '#00ff00';
        this.options.outerFlameColor = this.options.outerFlameColor ?? '#00aa00';
        this.options.smoke = this.options.smoke ?? true;
        this.options.smokeAmount = this.options.smokeAmount ?? 0.3;
        break;

      case 'custom':
        // No preset - use all manual parameters
        break;
    }
  }

  private createParticleTexture(): void {
    if (this.particleTexture || !this.scene) {
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

  private createCoreLayer(
    position: Vector3,
    size: number,
    height: number,
    intensity: number,
    turbulence: number,
    flickerSpeed: number,
    spread: number,
    wind: Vector3,
    color: Color4,
    particleCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('fireCore', particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone();

    ps.minSize = size * 0.05;
    ps.maxSize = size * 0.15;
    ps.minLifeTime = 0.3;
    ps.maxLifeTime = 0.5;

    ps.emitRate = this.options.emitRate ?? particleCount * 2;

    // Upward movement
    ps.direction1 = new Vector3(0, 1, 0);
    ps.direction2 = new Vector3(0, 1, 0);
    ps.minEmitPower = height * 2;
    ps.maxEmitPower = height * 3;

    ps.gravity = new Vector3(wind.x * 0.5, -1, wind.z * 0.5);

    // Color gradients
    ps.addColorGradient(0.0, color);
    ps.addColorGradient(0.5, new Color4(color.r, color.g, color.b, color.a * intensity));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    // Size gradients
    ps.addSizeGradient(0.0, 0.5);
    ps.addSizeGradient(0.5, 1.0);
    ps.addSizeGradient(1.0, 0.8);

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    this.applyTurbulenceToSystem(ps, turbulence, flickerSpeed, spread, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private createFlameLayer(
    position: Vector3,
    size: number,
    height: number,
    intensity: number,
    turbulence: number,
    flickerSpeed: number,
    spread: number,
    wind: Vector3,
    color: Color4,
    layer: 'inner' | 'mid' | 'outer',
    particleCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene) return;

    const layerMultiplier = layer === 'inner' ? 1.0 : layer === 'mid' ? 1.5 : 2.0;
    const alphaMultiplier = layer === 'inner' ? 1.0 : layer === 'mid' ? 0.8 : 0.6;

    const ps = new ParticleSystem(`flame_${layer}`, particleCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone();

    ps.minSize = size * 0.1 * layerMultiplier;
    ps.maxSize = size * 0.3 * layerMultiplier;
    ps.minLifeTime = 0.4 + (layerMultiplier * 0.1);
    ps.maxLifeTime = 0.7 + (layerMultiplier * 0.2);

    ps.emitRate = this.options.emitRate ?? particleCount * 1.5;

    // Upward movement with spread
    const spreadFactor = spread * layerMultiplier;
    ps.direction1 = new Vector3(-spreadFactor, 1, -spreadFactor);
    ps.direction2 = new Vector3(spreadFactor, 1, spreadFactor);
    ps.minEmitPower = height * 1.5;
    ps.maxEmitPower = height * 2.5;

    ps.gravity = new Vector3(wind.x, -0.5, wind.z);

    // Color gradients
    ps.addColorGradient(0.0, new Color4(color.r, color.g, color.b, 0));
    ps.addColorGradient(0.2, new Color4(color.r, color.g, color.b, color.a * intensity * alphaMultiplier));
    ps.addColorGradient(0.7, new Color4(color.r, color.g, color.b, color.a * intensity * alphaMultiplier * 0.7));
    ps.addColorGradient(1.0, new Color4(color.r * 0.5, color.g * 0.5, color.b * 0.5, 0));

    // Size gradients
    ps.addSizeGradient(0.0, 0.3);
    ps.addSizeGradient(0.5, 1.0);
    ps.addSizeGradient(1.0, 1.3);

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.015;

    this.applyTurbulenceToSystem(ps, turbulence, flickerSpeed, spread * layerMultiplier, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private createSmokeLayer(
    position: Vector3,
    size: number,
    smokeHeight: number,
    smokeAmount: number,
    turbulence: number,
    spread: number,
    wind: Vector3,
    color: Color4,
    particleCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('smoke', Math.floor(particleCount * smokeAmount), this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone().add(new Vector3(0, size * 0.5, 0));

    ps.minSize = size * 0.4;
    ps.maxSize = size * 0.8;
    ps.minLifeTime = 2.0;
    ps.maxLifeTime = 3.0;

    ps.emitRate = particleCount * smokeAmount * 0.5;

    // Slow upward movement with spread
    ps.direction1 = new Vector3(-spread, 1, -spread);
    ps.direction2 = new Vector3(spread, 1, spread);
    ps.minEmitPower = smokeHeight * 0.3;
    ps.maxEmitPower = smokeHeight * 0.5;

    ps.gravity = new Vector3(wind.x * 2, 0.2, wind.z * 2);

    // Color gradients (smoke fades in and expands)
    ps.addColorGradient(0.0, new Color4(color.r, color.g, color.b, 0));
    ps.addColorGradient(0.3, new Color4(color.r, color.g, color.b, color.a * 0.3));
    ps.addColorGradient(0.7, new Color4(color.r, color.g, color.b, color.a * 0.2));
    ps.addColorGradient(1.0, new Color4(color.r, color.g, color.b, 0));

    // Size gradients (smoke expands)
    ps.addSizeGradient(0.0, 0.5);
    ps.addSizeGradient(0.5, 1.0);
    ps.addSizeGradient(1.0, 2.0);

    ps.blendMode = ParticleSystem.BLENDMODE_STANDARD;
    ps.updateSpeed = 0.01;

    this.applyTurbulenceToSystem(ps, turbulence * 0.5, 2.0, spread * 2, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private createSparkLayer(
    position: Vector3,
    size: number,
    height: number,
    sparkIntensity: number,
    turbulence: number,
    spread: number,
    wind: Vector3,
    color: Color4,
    sparkCount: number,
    fadeInDuration: number
  ): void {
    if (!this.scene) return;

    const ps = new ParticleSystem('sparks', sparkCount, this.scene);
    ps.particleTexture = this.particleTexture;
    ps.emitter = position.clone();

    ps.minSize = size * 0.02;
    ps.maxSize = size * 0.05;
    ps.minLifeTime = 0.5;
    ps.maxLifeTime = 1.5;

    ps.emitRate = sparkCount * 0.5;

    // Upward and outward
    ps.direction1 = new Vector3(-spread * 2, 1, -spread * 2);
    ps.direction2 = new Vector3(spread * 2, 2, spread * 2);
    ps.minEmitPower = height * 1.5;
    ps.maxEmitPower = height * 3;

    ps.gravity = new Vector3(wind.x * 0.3, -2, wind.z * 0.3);

    // Color gradients (bright to dark)
    ps.addColorGradient(0.0, color);
    ps.addColorGradient(0.5, new Color4(color.r, color.g, color.b, color.a * sparkIntensity));
    ps.addColorGradient(1.0, new Color4(color.r * 0.3, color.g * 0.3, color.b * 0.3, 0));

    // Size gradients
    ps.addSizeGradient(0.0, 1.0);
    ps.addSizeGradient(0.5, 0.8);
    ps.addSizeGradient(1.0, 0.3);

    ps.blendMode = ParticleSystem.BLENDMODE_ADD;
    ps.updateSpeed = 0.02;

    this.applyTurbulenceToSystem(ps, turbulence * 2, 10.0, spread, fadeInDuration);

    ps.start();
    this.particleSystems.push(ps);
  }

  private applyTurbulenceToSystem(
    ps: ParticleSystem,
    turbulence: number,
    flickerSpeed: number,
    spread: number,
    fadeInDuration: number
  ): void {
    ps.updateFunction = (particles: any) => {
      const currentTime = this.now() - this.startTime;

      // Calculate fade multiplier (only fade in, no fade out for steady effect)
      let fadeMultiplier = 1.0;
      // Fade in
      if (currentTime < fadeInDuration) {
        fadeMultiplier = currentTime / fadeInDuration;
      }

      // Flicker effect
      const flicker = 0.9 + 0.1 * Math.sin(currentTime * flickerSpeed * Math.PI * 2);
      fadeMultiplier *= flicker;

      for (const particle of particles) {
        particle.age += this.scene!.getEngine().getDeltaTime() / 1000;

        if (particle.age >= particle.lifeTime) {
          particles.splice(particles.indexOf(particle), 1);
          continue;
        }

        // Apply turbulence
        if (turbulence > 0) {
          const turbX = (Math.random() - 0.5) * turbulence * spread * 0.1;
          const turbZ = (Math.random() - 0.5) * turbulence * spread * 0.1;
          particle.position.x += turbX;
          particle.position.z += turbZ;
        }

        // Apply fade
        const originalAlpha = particle.color.a;
        particle.color.a = originalAlpha * fadeMultiplier;
      }
    };
  }

  private animate = () => {
    // Endless animation loop for steady effect
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

    logger.debug('Particle fire steady effect cleaned up');
  }

  isRunning(): boolean {
    return this.particleSystems.length > 0 && this.animationHandle !== null;
  }

  stop(): void {
    this.cleanup();
  }
}
