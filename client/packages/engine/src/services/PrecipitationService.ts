/**
 * PrecipitationService - Manages rain and snow particle effects
 *
 * Features:
 * - Particle-based rain and snow
 * - Follows camera movement
 * - Configurable intensity (0-100)
 * - Configurable particle size, color, and texture
 * - Can be enabled/disabled
 */

import {
  Scene,
  ParticleSystem,
  Texture,
  Color4,
  Vector3,
  RawTexture,
  Constants,
} from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { loadTextureUrlWithCredentials } from '../utils/ImageLoader';
import type { CameraService } from './CameraService';
import { RENDERING_GROUPS } from '../config/renderingGroups';

const logger = getLogger('PrecipitationService');

/**
 * Precipitation type
 */
export enum PrecipitationType {
  RAIN = 'rain',
  SNOW = 'snow',
}

/**
 * PrecipitationService - Manages precipitation effects
 */
export class PrecipitationService {
  private scene: Scene;
  private appContext: AppContext;
  private cameraService: CameraService;

  // Particle system (rain/snow)
  private particleSystem?: ParticleSystem;

  // Configuration
  private enabled: boolean = false;
  private intensity: number = 0; // 0-100
  private precipitationType: PrecipitationType = PrecipitationType.RAIN;

  // Particle properties (these are used when creating the particle system)
  private particleSize: number = 0.3;
  private particleColor: Color4 = new Color4(0.5, 0.5, 0.8, 1.0);
  private particleSpeed: number = 25; // EmitPower
  private particleGravity: number = 15; // Gravity strength
  private particleTexture?: Texture | RawTexture;

  // Lightning system
  private lightningParticleSystems: ParticleSystem[] = [];

  // Thunder sound system
  private thunderSoundPaths: string[] = [];
  private lastThunderSoundTime: number = 0;

  constructor(scene: Scene, appContext: AppContext) {
    this.scene = scene;
    this.appContext = appContext;
    this.cameraService = appContext.services.camera!;

    logger.debug('PrecipitationService initialized');
  }

  /**
   * Enable or disable precipitation
   * @param enabled True to enable, false to disable
   */
  public setEnabled(enabled: boolean): void {
    if (this.enabled === enabled) return;

    this.enabled = enabled;

    if (enabled) {
      this.createParticleSystem();
    } else {
      this.disposeParticleSystem();
    }

    logger.debug('Precipitation enabled state changed', { enabled });
  }

  /**
   * Check if precipitation is enabled
   * @returns True if enabled
   */
  public isEnabled(): boolean {
    return this.enabled;
  }

  /**
   * Set precipitation intensity
   * @param intensity Intensity (0+, higher = more particles)
   */
  public setIntensity(intensity: number): void {
    // Clamp to non-negative
    this.intensity = Math.max(0, intensity);

    // Update particle system if active
    if (this.particleSystem) {
      this.updateParticleIntensity();
    }

    logger.debug('Precipitation intensity changed', { intensity: this.intensity });
  }

  /**
   * Get current intensity
   * @returns Intensity (0-100)
   */
  public getIntensity(): number {
    return this.intensity;
  }

  /**
   * Set precipitation type
   * @param type Rain or snow
   */
  public setPrecipitationType(type: PrecipitationType): void {
    if (this.precipitationType === type) return;

    // Remember current state
    const wasEnabled = this.enabled;

    // Change type
    this.precipitationType = type;

    // Dispose old particle system if exists
    if (this.particleSystem) {
      this.disposeParticleSystem();
    }

    // Recreate particle system if it was enabled
    if (wasEnabled) {
      this.createParticleSystem();
    }

    logger.debug('Precipitation type changed', { type, wasEnabled });
  }

  /**
   * Get current precipitation type
   * @returns Precipitation type
   */
  public getPrecipitationType(): PrecipitationType {
    return this.precipitationType;
  }

  /**
   * Set particle size
   * @param size Particle size
   */
  public setParticleSize(size: number): void {
    if (size <= 0) {
      throw new Error('Particle size must be positive');
    }

    this.particleSize = size;
    logger.debug('Particle size set', { size });
  }

  /**
   * Get current particle size
   * @returns Particle size
   */
  public getParticleSize(): number {
    return this.particleSize;
  }

  /**
   * Set particle color
   * @param color Particle color (RGBA)
   */
  public setParticleColor(color: Color4): void {
    this.particleColor = color;
    logger.debug('Particle color set', {
      r: color.r,
      g: color.g,
      b: color.b,
      a: color.a,
    });
  }

  /**
   * Get current particle color
   * @returns Particle color
   */
  public getParticleColor(): Color4 {
    return this.particleColor.clone();
  }

  /**
   * Set particle speed (emit power)
   * @param speed Emit power value
   */
  public setParticleSpeed(speed: number): void {
    if (speed < 0) {
      throw new Error('Particle speed cannot be negative');
    }

    this.particleSpeed = speed;
    logger.debug('Particle speed set', { speed });
  }

  /**
   * Set particle gravity
   * @param gravity Gravity strength (negative for downward)
   */
  public setParticleGravity(gravity: number): void {
    this.particleGravity = Math.abs(gravity);
    logger.debug('Particle gravity set', { gravity: this.particleGravity });
  }

  /**
   * Set particle texture
   * @param texturePath Path to texture or null to remove
   */
  public async setParticleTexture(texturePath: string | null): Promise<void> {
    // Dispose old texture
    if (this.particleTexture) {
      this.particleTexture.dispose();
      this.particleTexture = undefined;
    }

    // Load new texture
    if (texturePath) {
      try {
        // Load texture with credentials
        const blobUrl = await loadTextureUrlWithCredentials(texturePath);
        this.particleTexture = new Texture(blobUrl, this.scene);
        if (this.particleSystem) {
          this.particleSystem.particleTexture = this.particleTexture;
        }
        logger.debug('Particle texture loaded', { path: texturePath });
      } catch (error) {
        logger.error('Failed to load particle texture', { path: texturePath, error });
      }
    } else {
      // No texture - use default point sprite
      if (this.particleSystem) {
        this.particleSystem.particleTexture = null;
      }
    }
  }

  /**
   * Update precipitation (called every frame)
   * @param deltaTime Time since last frame in seconds
   */
  public update(deltaTime: number): void {
    // Update emitter position to follow camera
    if (this.particleSystem && this.enabled) {
      // Get camera position and update emitter
      const cameraPos = this.cameraService.getPosition();
      if (cameraPos) {
        const emitterPos = cameraPos.clone();
        emitterPos.y += 30; // 30 blocks above camera
        this.particleSystem.emitter = emitterPos;

        // Optional: Debug logging (disabled by default)
        // if (Math.random() < 0.016) {
        //   logger.debug('Precipitation update', {
        //     activeParticles: this.particleSystem.getActiveCount(),
        //   });
        // }
      }
    }
  }

  /**
   * Trigger lightning storm with multiple groups
   * @param groupCount Number of lightning groups (each at different location)
   * @param toGround True if lightning reaches ground, false if stays in sky
   */
  public triggerLightning(groupCount: number, toGround: boolean): void {
    const cameraPos = this.cameraService.getPosition();
    if (!cameraPos) {
      logger.error('Camera position not available for lightning');
      return;
    }

    // Create multiple lightning groups at different locations
    for (let group = 0; group < groupCount; group++) {
      const delay = group * (200 + Math.random() * 1800); // 200ms - 2sec pause between groups

      setTimeout(() => {
        this.createLightningGroup(cameraPos, toGround);
      }, delay);
    }

    logger.debug('Lightning storm triggered', {
      groupCount,
      toGround,
    });
  }

  /**
   * Register thunder sound paths
   * @param soundPaths Array of sound paths to use for thunder (empty array clears the list)
   */
  public registerFlashSounds(soundPaths: string[]): void {
    this.thunderSoundPaths = soundPaths;
    logger.debug('Thunder sounds registered', { count: soundPaths.length, paths: soundPaths });
  }

  /**
   * Play thunder sound at flash position
   * Called directly when a flash is created (no event system needed)
   * @param position Flash position in world coordinates
   * @param intensity Flash intensity (affects sound emission range)
   */
  private playThunder(position: Vector3, intensity: number): void {
    // Check if sounds are registered
    if (this.thunderSoundPaths.length === 0) {
      return; // No sounds to play
    }

    // Throttle: max one sound every 500ms
    const now = Date.now();
    if (now - this.lastThunderSoundTime < 500) {
      return; // Too soon
    }

    this.lastThunderSoundTime = now;

    // Select random sound from list
    const randomIndex = Math.floor(Math.random() * this.thunderSoundPaths.length);
    const soundPath = this.thunderSoundPaths[randomIndex];

    // Calculate emission range: 20 + intensity * 2 (max 64)
    const emissionRange = Math.min(64, 20 + intensity * 2);

    // Get audio service
    const audioService = this.appContext.services.audio;
    if (!audioService) {
      logger.warn('AudioService not available for thunder sound');
      return;
    }

    // Play sound at position
    audioService.playSoundAtPosition(soundPath, position.x, position.y, position.z, 1.0);

    logger.debug('Thunder sound played', {
      soundPath,
      position: { x: position.x, y: position.y, z: position.z },
      intensity,
      emissionRange,
    });
  }

  /**
   * Create one lightning group (5-30 flashes at same location)
   * Uses camera-relative coordinates via cameraEnvironmentRoot
   */
  private createLightningGroup(cameraPos: Vector3, toGround: boolean): void {
    // Random RELATIVE position (x/z between -64 and 64, 4x4 chunks around camera)
    const lightningX = -64 + Math.random() * 128;
    const lightningZ = -64 + Math.random() * 128;

    // Start Y position (high above, relative to camera)
    const startY = 40 + Math.random() * 20;

    // End Y position (relative to camera)
    const endY = toGround ? -30 : (10 + Math.random() * 20);

    // Generate zigzag path with RELATIVE coordinates
    const path = this.generateZigzagPath(
      new Vector3(lightningX, startY, lightningZ),
      new Vector3(lightningX, endY, lightningZ)
    );

    // Random number of flashes in this group (5-30)
    const flashCount = 5 + Math.floor(Math.random() * 26);

    // Calculate intensity based on flash count and brightness (5-20 range)
    const intensity = 5 + (flashCount / 30) * 15;

    // Calculate absolute world position for thunder sound (camera position + relative offset)
    const thunderPosition = new Vector3(
      cameraPos.x + lightningX,
      cameraPos.y + startY,
      cameraPos.z + lightningZ
    );

    // Play thunder sound at flash position
    this.playThunder(thunderPosition, intensity);

    // First flash is brightest
    this.createLightningFlash(path, 0.2, 1.0, 0);

    // Subsequent flashes along same path
    for (let i = 1; i < flashCount; i++) {
      const delay = 20 + i * 30; // 20-30ms intervals
      const brightness = 0.4 + Math.random() * 0.3;

      setTimeout(() => {
        this.createLightningFlash(path, 0.15, brightness, i);
      }, delay);
    }

    logger.debug('Lightning group created', {
      position: { x: lightningX, z: lightningZ },
      flashCount,
      pathSegments: path.length - 1,
      intensity,
    });
  }

  /**
   * Generate realistic lightning path with organic zigzag
   */
  private generateZigzagPath(start: Vector3, end: Vector3): Vector3[] {
    const segmentLength = 1.5;
    const maxDeviation = 2.0;
    const roughness = 0.8;

    const points: Vector3[] = [];
    points.push(start.clone());

    let current = start.clone();

    const totalDir = end.subtract(start);
    const totalDist = totalDir.length();
    if (totalDist === 0) {
      return [start.clone(), end.clone()];
    }

    // Main loop: travel from top to bottom
    let traveled = 0;
    while (traveled < totalDist) {
      const remaining = totalDist - traveled;
      const step = Math.min(segmentLength, remaining);

      // Direction to target from current position
      const toEnd = end.subtract(current).normalize();

      // Base movement towards target
      let stepVec = toEnd.scale(step);

      // Random lateral deviation
      const randomSide = this.randomPerpendicular(toEnd);

      // Deviation strength (slightly random scaled)
      const deviationStrength = maxDeviation * (0.5 + Math.random() * roughness);
      const deviation = randomSide.scale(deviationStrength);

      // Don't let Y component get too strong (keep going down)
      deviation.y *= 0.2;

      stepVec = stepVec.add(deviation);

      // Next point
      current = current.add(stepVec);
      points.push(current.clone());

      traveled += stepVec.length();

      // Safety break
      if (points.length > 1000) break;
    }

    // Ensure we end exactly at target
    points[points.length - 1] = end.clone();

    return points;
  }

  /**
   * Generate random vector perpendicular to direction
   */
  private randomPerpendicular(dir: Vector3): Vector3 {
    // Choose fallback axis that's not parallel
    const up = Math.abs(dir.y) < 0.9 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);

    // First perpendicular cross product
    let side = Vector3.Cross(dir, up);
    if (side.lengthSquared() < 0.0001) {
      side = Vector3.Cross(dir, new Vector3(0, 0, 1));
    }
    side.normalize();

    // Second, to have a "lateral plane"
    let side2 = Vector3.Cross(dir, side).normalize();

    // Random combination of both side directions
    const a = (Math.random() * 2 - 1); // -1..1
    const b = (Math.random() * 2 - 1); // -1..1

    const result = side.scale(a).add(side2.scale(b));
    return result.normalize();
  }

  /**
   * Create single flash along zigzag path
   */
  private createLightningFlash(path: Vector3[], flashDuration: number, brightness: number, index: number): void {
    // Create particles along each segment of the path
    for (let i = 0; i < path.length - 1; i++) {
      this.createLightningStrand(path[i], path[i + 1], flashDuration, index * 100 + i, brightness);
    }
  }

  /**
   * Dispose precipitation service
   */
  public dispose(): void {
    this.disposeParticleSystem();
    this.disposeLightning();
    this.particleTexture?.dispose();
    logger.debug('PrecipitationService disposed');
  }

  // ========== Private Helper Methods ==========

  /**
   * Create particle system
   */
  private createParticleSystem(): void {
    // Get camera position
    const cameraPos = this.cameraService.getPosition();
    if (!cameraPos) {
      logger.error('Camera position not available');
      return;
    }

    // Create particle texture (CRITICAL - without this, particles won't render!)
    this.createParticleTexture();

    // Create particle system with large capacity
    // At 500 particles/sec with 6 sec lifetime = 3000 particles
    // Allow up to 20000 for heavy precipitation
    this.particleSystem = new ParticleSystem('precipitation', 20000, this.scene);

    // Set emitter ABOVE camera
    const emitterPos = cameraPos.clone();
    emitterPos.y += 30; // 30 blocks above camera
    this.particleSystem.emitter = emitterPos;

    // LARGE emitter area - min 5x5 chunks, camera in center
    this.particleSystem.minEmitBox = new Vector3(-80, -5, -80);
    this.particleSystem.maxEmitBox = new Vector3(80, 5, 80);

    // USE the texture we created
    this.particleSystem.particleTexture = this.particleTexture!;

    // Use stored particle properties
    this.particleSystem.direction1 = new Vector3(-0.1, -1, -0.1);
    this.particleSystem.direction2 = new Vector3(0.1, -1, 0.1);
    this.particleSystem.minEmitPower = this.particleSpeed * 0.8;
    this.particleSystem.maxEmitPower = this.particleSpeed * 1.2;
    this.particleSystem.gravity = new Vector3(0, -this.particleGravity, 0);

    // Particle size from stored value
    this.particleSystem.minSize = this.particleSize * 0.8;
    this.particleSystem.maxSize = this.particleSize * 1.2;

    // Particle color from stored value
    this.particleSystem.addColorGradient(0.0, this.particleColor);
    this.particleSystem.addColorGradient(1.0, this.particleColor);

    // Particle lifetime
    this.particleSystem.minLifeTime = 4;
    this.particleSystem.maxLifeTime = 6;

    // Size gradients - constant size
    this.particleSystem.addSizeGradient(0.0, 1.0);
    this.particleSystem.addSizeGradient(1.0, 1.0);

    // Emission rate based on intensity (direct mapping: intensity = particles/sec)
    this.particleSystem.emitRate = this.intensity;

    // Emission rate (based on intensity)
    this.updateParticleIntensity();

    // Billboard mode
    this.particleSystem.isBillboardBased = true;

    // Blending - use ADD for better visibility
    this.particleSystem.blendMode = ParticleSystem.BLENDMODE_ADD;

    // Update speed
    this.particleSystem.updateSpeed = 0.02;

    // Rendering group
    this.particleSystem.renderingGroupId = RENDERING_GROUPS.PRECIPITATION;

    // Start emitting
    this.particleSystem.start();

    logger.debug('✨ Particle system created and started', {
      type: this.precipitationType,
      intensity: this.intensity,
      emissionRate: this.particleSystem.emitRate,
      particleCount: this.particleSystem.getCapacity(),
      size: {
        min: this.particleSystem.minSize,
        max: this.particleSystem.maxSize,
      },
      color: {
        r: this.particleColor.r,
        g: this.particleColor.g,
        b: this.particleColor.b,
        a: this.particleColor.a,
      },
      emitPower: {
        min: this.particleSystem.minEmitPower,
        max: this.particleSystem.maxEmitPower,
      },
      lifetime: {
        min: this.particleSystem.minLifeTime,
        max: this.particleSystem.maxLifeTime,
      },
      emitterPosition: this.particleSystem.emitter,
      emitBox: {
        min: this.particleSystem.minEmitBox,
        max: this.particleSystem.maxEmitBox,
      },
      renderingGroupId: this.particleSystem.renderingGroupId,
      isStarted: this.particleSystem.isStarted(),
    });
  }

  /**
   * Update particle emission rate based on intensity
   */
  private updateParticleIntensity(): void {
    if (!this.particleSystem) return;

    // Map intensity (0-100) to emission rate (0-500 particles/sec)
    const emissionRate = (this.intensity / 100) * 500;
    this.particleSystem.emitRate = emissionRate;

    logger.debug('💧 Particle intensity updated', {
      intensity: this.intensity,
      emissionRate,
      isStarted: this.particleSystem.isStarted(),
    });
  }

  /**
   * Create particle texture - simple circular gradient
   */
  private createParticleTexture(): void {
    // Always recreate texture to avoid disposed texture issues
    if (this.particleTexture) {
      try {
        this.particleTexture.dispose();
      } catch (e) {
        // Ignore dispose errors
      }
      this.particleTexture = undefined;
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

    logger.debug('Particle texture created');
  }

  /**
   * Dispose particle system
   */
  private disposeParticleSystem(): void {
    if (this.particleSystem) {
      this.particleSystem.dispose();
      this.particleSystem = undefined;
      logger.debug('Particle system disposed');
    }
  }

  /**
   * Create single lightning discharge strand
   * @param brightness 0.0-1.0 (1.0 = main flash, lower = subsequent flashes)
   */
  private createLightningStrand(
    startPos: Vector3,
    endPos: Vector3,
    flashDuration: number,
    strandIndex: number,
    brightness: number
  ): void {
    // Create fresh texture for each strand
    const texture = this.createFreshLightningTexture();

    const particleCount = Math.floor(400 * brightness); // Main flash has more particles
    const ps = new ParticleSystem(`lightning_${Date.now()}_${strandIndex}`, particleCount + 100, this.scene);
    ps.particleTexture = texture;

    // Get camera environment root for relative positioning
    const cameraRoot = this.cameraService.getCameraEnvironmentRoot();
    if (!cameraRoot) {
      logger.error('Camera environment root not available');
      return;
    }

    // Lightning emitter - RELATIVE position to camera
    ps.emitter = startPos.clone();

    // NO emit box - all particles from exact point
    ps.minEmitBox = new Vector3(0, 0, 0);
    ps.maxEmitBox = new Vector3(0, 0, 0);

    // Exact direction from start to end - NO randomness
    const direction = endPos.subtract(startPos).normalize();
    ps.direction1 = direction;
    ps.direction2 = direction;

    // Very fast particles traveling from start to end
    const distance = Vector3.Distance(startPos, endPos);
    const speed = distance / 0.05; // Travel segment in 50ms
    ps.minEmitPower = speed * 0.95;
    ps.maxEmitPower = speed * 1.05;

    // Very short lifetime for flash effect
    ps.minLifeTime = 0.05;
    ps.maxLifeTime = 0.1;

    // Burst emission
    ps.emitRate = 5000;
    ps.manualEmitCount = particleCount;

    // Thin lightning streaks
    ps.minSize = 0.3 * brightness;
    ps.maxSize = 0.5 * brightness;

    // Bright white/blue color scaled by brightness
    const alpha = brightness;
    const lightningColor = new Color4(0.95, 0.95, 1.0, alpha);
    ps.addColorGradient(0.0, lightningColor);
    ps.addColorGradient(0.3, new Color4(0.95, 0.95, 1.0, alpha * 0.9));
    ps.addColorGradient(1.0, new Color4(0.95, 0.95, 1.0, 0)); // Quick fade

    // Size gradients - constant thin
    ps.addSizeGradient(0.0, 1.0);
    ps.addSizeGradient(1.0, 0.8);

    // No gravity
    ps.gravity = Vector3.Zero();

    // Additive blending for intense brightness
    ps.blendMode = ParticleSystem.BLENDMODE_ADD;

    // Billboard
    ps.isBillboardBased = true;

    // Rendering group
    ps.renderingGroupId = RENDERING_GROUPS.PRECIPITATION;

    // Attach to camera environment root for relative positioning
    ps.emitter = startPos.clone();
    // Note: ParticleSystem doesn't have parent, but emitter position is relative to world
    // We need to convert relative to absolute
    const absoluteStart = cameraRoot.getAbsolutePosition().add(startPos);
    ps.emitter = absoluteStart;

    // Start immediately
    ps.start();
    this.lightningParticleSystems.push(ps);

    // Stop emitting after initial burst
    setTimeout(() => {
      ps.stop();
    }, 50);

    // Dispose after flash duration
    setTimeout(() => {
      const index = this.lightningParticleSystems.indexOf(ps);
      if (index > -1) {
        this.lightningParticleSystems.splice(index, 1);
      }
      ps.dispose();
    }, flashDuration * 1000);
  }

  /**
   * Create fresh lightning texture (new instance each time)
   */
  private createFreshLightningTexture(): RawTexture {
    const textureSize = 16;
    const textureData = new Uint8Array(textureSize * textureSize * 4);
    const center = textureSize / 2;

    for (let y = 0; y < textureSize; y++) {
      for (let x = 0; x < textureSize; x++) {
        const dx = x - center + 0.5;
        const dy = y - center + 0.5;
        const dist = Math.sqrt(dx * dx + dy * dy) / center;
        // Sharper falloff for lightning streaks
        const texAlpha = Math.max(0, Math.pow(1 - dist, 2));

        const index = (y * textureSize + x) * 4;
        textureData[index] = 255;     // R
        textureData[index + 1] = 255; // G
        textureData[index + 2] = 255; // B
        textureData[index + 3] = Math.floor(texAlpha * 255); // A
      }
    }

    return RawTexture.CreateRGBATexture(
      textureData,
      textureSize,
      textureSize,
      this.scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );
  }

  /**
   * Dispose all lightning particle systems
   */
  private disposeLightning(): void {
    for (const ps of this.lightningParticleSystems) {
      ps.dispose();
    }
    this.lightningParticleSystems = [];
  }
}
