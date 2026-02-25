/**
 * CampfireRenderer - Renders rising smoke particles via BabylonJS ParticleSystem
 *
 * Features:
 * - Upward-moving smoke particles from block center
 * - Wind drift: particles drift opposite to wind direction
 * - effectParameters controls intensity (emitRate, particle count)
 * - Uses block's ALL texture (key 0) or procedural soft-circle fallback
 */

import {
  ParticleSystem,
  Vector3,
  Color4,
  Texture,
  RawTexture,
  Constants,
} from '@babylonjs/core';
import type { Observer, Scene, IDisposable } from '@babylonjs/core';
import { getLogger, TextureHelper, Shape } from '@nimbus/shared';
import type { ClientBlock } from '../types';
import { BlockRenderer } from './BlockRenderer';
import type { RenderContext } from '../services/RenderService';

const logger = getLogger('CampfireRenderer');

/**
 * Disposable wrapper for campfire resources (particle system + observer + procedural texture)
 */
class CampfireDisposable implements IDisposable {
  constructor(
    private particleSystem: ParticleSystem,
    private observer: Observer<Scene> | null,
    private scene: Scene,
    private proceduralTexture: Texture | null
  ) {}

  dispose(): void {
    // Remove render observer
    if (this.observer) {
      this.scene.onBeforeRenderObservable.remove(this.observer);
      this.observer = null;
    }

    // Stop and dispose particle system
    this.particleSystem.stop();
    this.particleSystem.dispose();

    // Dispose procedural texture (loaded textures belong to MaterialService cache)
    if (this.proceduralTexture) {
      this.proceduralTexture.dispose();
      this.proceduralTexture = null;
    }
  }
}

/**
 * CampfireRenderer - Smoke particle effect for campfire blocks
 */
export class CampfireRenderer extends BlockRenderer {

  needsSeparateMesh(): boolean {
    return true;
  }

  async render(renderContext: RenderContext, clientBlock: ClientBlock): Promise<void> {
    const block = clientBlock.block;
    const modifier = clientBlock.currentModifier;

    if (!modifier || !modifier.visibility) {
      logger.warn('CampfireRenderer: No visibility modifier', { block });
      return;
    }

    const shape = modifier.visibility.shape ?? Shape.CUBE;
    if (shape !== Shape.CAMPFIRE) {
      logger.warn('CampfireRenderer: Not a CAMPFIRE shape', { shape, block });
      return;
    }

    const scene = renderContext.renderService.scene;
    const pos = block.position;

    // Emitter position: block center, slightly above
    const emitterPos = new Vector3(pos.x + 0.5, pos.y + 0.8, pos.z + 0.5);

    // Parse intensity from effectParameters (default 1.0, max 5.0)
    let intensity = 1.0;
    const effectParams = modifier.visibility.effectParameters;
    if (effectParams) {
      const parsed = parseFloat(effectParams);
      if (!isNaN(parsed)) {
        intensity = Math.min(Math.max(parsed, 0.1), 5.0);
      }
    }

    const meshName = `campfire_${pos.x}_${pos.y}_${pos.z}`;

    // Create particle system
    const maxParticles = Math.ceil(60 * intensity);
    const ps = new ParticleSystem(meshName, maxParticles, scene);

    // Load texture: try block's ALL texture (key 0), fallback to procedural
    let proceduralTexture: Texture | null = null;
    const textures = modifier.visibility.textures;
    let textureLoaded = false;

    if (textures) {
      const allTexture = textures[0];
      if (allTexture) {
        try {
          const textureDef = TextureHelper.normalizeTexture(allTexture);
          const loaded = await renderContext.renderService.materialService.loadTexture(textureDef.path) as Texture;
          if (loaded) {
            ps.particleTexture = loaded;
            textureLoaded = true;
          }
        } catch (error) {
          logger.warn('CampfireRenderer: Failed to load texture, using procedural', { error });
        }
      }
    }

    if (!textureLoaded) {
      proceduralTexture = this.createSoftCircleTexture(scene);
      ps.particleTexture = proceduralTexture;
    }

    // Emitter
    ps.emitter = emitterPos;
    ps.minEmitBox = new Vector3(-0.15, 0, -0.15);
    ps.maxEmitBox = new Vector3(0.15, 0, 0.15);

    // Direction: upward
    ps.direction1 = new Vector3(-0.1, 0.3, -0.1);
    ps.direction2 = new Vector3(0.1, 0.6, 0.1);
    ps.minEmitPower = 0.3;
    ps.maxEmitPower = 0.6;

    // Lifetime
    ps.minLifeTime = 2.0;
    ps.maxLifeTime = 3.5;

    // Size: grows over time (smoke expands)
    ps.minSize = 0.4;
    ps.maxSize = 1.0;
    ps.addSizeGradient(0.0, 0.4);
    ps.addSizeGradient(0.5, 1.0);
    ps.addSizeGradient(1.0, 1.8);

    // Color: gray smoke
    ps.color1 = new Color4(0.4, 0.4, 0.4, 0.25);
    ps.color2 = new Color4(0.5, 0.5, 0.5, 0.20);
    ps.colorDead = new Color4(0.3, 0.3, 0.3, 0.0);

    // Alpha gradient: fade in, sustain, fade out
    ps.addColorGradient(0.0, new Color4(0.4, 0.4, 0.4, 0.0));
    ps.addColorGradient(0.15, new Color4(0.4, 0.4, 0.4, 0.25));
    ps.addColorGradient(0.6, new Color4(0.45, 0.45, 0.45, 0.15));
    ps.addColorGradient(1.0, new Color4(0.3, 0.3, 0.3, 0.0));

    // Blend mode: standard (not additive like fire)
    ps.blendMode = ParticleSystem.BLENDMODE_STANDARD;

    // Emit rate based on intensity
    ps.emitRate = Math.ceil(15 * intensity);

    // Gravity: slight upward pull (overridden per-frame for wind)
    ps.gravity = new Vector3(0, 0.2, 0);

    // Angular speed for natural rotation
    ps.minAngularSpeed = -0.5;
    ps.maxAngularSpeed = 0.5;

    // Start emitting
    ps.start();

    // Per-frame wind drift update
    const envService = renderContext.renderService.appContext.services.environment;
    let observer: Observer<Scene> | null = null;

    if (envService) {
      observer = scene.onBeforeRenderObservable.add(() => {
        const wind = envService.getWindParameters();
        const gravityX = -wind.windDirection.x * wind.windStrength * 2.0;
        const gravityZ = -wind.windDirection.z * wind.windStrength * 2.0;
        ps.gravity.x = gravityX;
        ps.gravity.z = gravityZ;
      });
    }

    // Register for disposal
    const disposable = new CampfireDisposable(ps, observer, scene, proceduralTexture);
    renderContext.resourcesToDispose.add(disposable);

    logger.debug('CAMPFIRE smoke created', {
      meshName,
      position: `${pos.x},${pos.y},${pos.z}`,
      intensity,
      emitRate: ps.emitRate,
      maxParticles,
      textureType: textureLoaded ? 'loaded' : 'procedural',
    });
  }

  /**
   * Create a procedural soft-circle texture for smoke particles
   */
  private createSoftCircleTexture(scene: Scene): Texture {
    const textureSize = 32;
    const textureData = new Uint8Array(textureSize * textureSize * 4);
    const center = textureSize / 2;

    for (let y = 0; y < textureSize; y++) {
      for (let x = 0; x < textureSize; x++) {
        const dx = x - center + 0.5;
        const dy = y - center + 0.5;
        const dist = Math.sqrt(dx * dx + dy * dy) / center;
        const alpha = Math.max(0, 1 - dist);

        const index = (y * textureSize + x) * 4;
        textureData[index] = 255;     // R
        textureData[index + 1] = 255; // G
        textureData[index + 2] = 255; // B
        textureData[index + 3] = Math.floor(alpha * 255); // A
      }
    }

    return RawTexture.CreateRGBATexture(
      textureData,
      textureSize,
      textureSize,
      scene,
      false,
      false,
      Constants.TEXTURE_BILINEAR_SAMPLINGMODE
    );
  }
}
