/**
 * CircleMarkerEffect - Animated circular ground marker
 *
 * Creates an expanding/fading circular marker on the ground (e.g., for AOE effects).
 * The marker spreads from 0 to full radius, stays visible, then fades out.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import {
  ShaderMaterial,
  Mesh,
  PlaneBuilder,
  Texture,
  Color3,
  Vector3,
} from '@babylonjs/core';
import circleMarkerVertexShader from './shaders/circleMarker.vertex.glsl?raw';
import circleMarkerFragmentShader from './shaders/circleMarker.fragment.glsl?raw';

const logger = getLogger('CircleMarkerEffect');

/**
 * Options for CircleMarkerEffect
 */
export interface CircleMarkerOptions {
  /** Position (x, y, z) - uses source or target from context if not provided */
  position?: { x: number; y: number; z: number };

  /** Radius of the circle in world units */
  radius: number;

  /** Color as hex string (e.g., "#ff0000") or CSS color */
  color: string;

  /** Duration to spread from 0 to full radius (seconds) */
  spreadDuration: number;

  /** Duration to stay at full radius (seconds) */
  stayDuration: number;

  /** Rotation speed in radians per second (0 = no rotation) */
  rotationSpeed?: number;

  /** Alpha/transparency (0-1, default 0.8) */
  alpha?: number;

  /** Texture path (optional, if not provided uses flat color) */
  texture?: string;

  /** Shape: 'square' (default, uses texture as-is) or 'circle' (applies circular mask) */
  shape?: 'square' | 'circle';
}

/**
 * CircleMarkerEffect - Creates an animated ground marker
 *
 * Usage:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "circleMarker",
 *   "target": "$patient",
 *   "ctx": {
 *     "radius": 5,
 *     "color": "#ff6600",
 *     "spreadDuration": 0.5,
 *     "stayDuration": 2.0,
 *     "rotationSpeed": 1.0,
 *     "alpha": 0.7,
 *     "texture": "effects/aoe_marker.png"
 *   }
 * }
 * ```
 */
export class CircleMarkerEffect extends ScrawlEffectHandler<CircleMarkerOptions> {
  private mesh: Mesh | null = null;
  private material: ShaderMaterial | null = null;
  private startTime: number = 0;
  private animationHandle: number | null = null;

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }

    try {
      // Resolve position from context
      const position = this.resolvePosition(ctx);
      if (!position) {
        logger.warn('No position available for circle marker');
        return;
      }

      // Create plane mesh
      this.mesh = PlaneBuilder.CreatePlane(
        'circleMarker',
        { size: 2, sideOrientation: Mesh.DOUBLESIDE },
        scene
      );

      // Position the mesh (slightly above ground)
      this.mesh.position = new Vector3(position.x, position.y + 0.05, position.z);
      this.mesh.rotation.x = Math.PI / 2; // Rotate to horizontal

      // Create shader material
      this.material = new ShaderMaterial(
        'circleMarkerMaterial',
        scene,
        {
          vertexSource: circleMarkerVertexShader,
          fragmentSource: circleMarkerFragmentShader,
        },
        {
          attributes: ['position', 'uv'],
          uniforms: [
            'worldViewProjection',
            'radius',
            'time',
            'rotationSpeed',
            'color',
            'alpha',
            'fadeProgress',
            'textureSampler',
            'useTexture',
            'useCircleMask',
          ],
        }
      );

      // Parse color
      const colorRgb = this.parseColor(this.options.color);
      const alpha = this.options.alpha ?? 0.8;
      const rotationSpeed = this.options.rotationSpeed ?? 0.0;
      const shape = this.options.shape ?? 'square';

      // Set initial uniforms
      this.material.setFloat('radius', 0);
      this.material.setFloat('time', 0);
      this.material.setFloat('rotationSpeed', rotationSpeed);
      this.material.setColor3('color', colorRgb);
      this.material.setFloat('alpha', alpha);
      this.material.setFloat('fadeProgress', 0);
      this.material.setInt('useTexture', 0);
      this.material.setInt('useCircleMask', shape === 'circle' ? 1 : 0);

      // Load texture if provided
      if (this.options.texture) {
        try {
          const networkService = ctx.appContext.services.network;
          if (networkService) {
            const textureUrl = networkService.getAssetUrl(this.options.texture);
            const texture = new Texture(textureUrl, scene, false, false);
            texture.hasAlpha = true;
            this.material.setTexture('textureSampler', texture);
            this.material.setInt('useTexture', 1);
          }
        } catch (error) {
          logger.warn('Failed to load texture', { texture: this.options.texture, error });
        }
      }

      // Configure material for transparency
      this.material.backFaceCulling = false;
      this.material.transparencyMode = 1; // ALPHATEST
      this.material.needAlphaBlending = () => false;
      this.material.needAlphaTesting = () => true;

      this.mesh.material = this.material;

      // Start animation
      this.startTime = this.now();
      this.animate();
    } catch (error) {
      logger.error('Failed to create circle marker', { error });
      this.cleanup();
    }
  }

  private animate = () => {
    if (!this.material || !this.mesh) {
      return;
    }

    const elapsed = this.now() - this.startTime;
    const { spreadDuration, stayDuration, radius } = this.options;
    const totalDuration = spreadDuration + stayDuration;

    if (elapsed > totalDuration + 1.0) {
      // Animation complete, cleanup
      this.cleanup();
      return;
    }

    // Calculate animation progress
    let currentRadius = 0;
    let fadeProgress = 1.0;

    if (elapsed < spreadDuration) {
      // Spreading phase
      const progress = elapsed / spreadDuration;
      currentRadius = radius * progress;
      fadeProgress = Math.min(progress * 2, 1.0); // Fade in during first half
    } else if (elapsed < spreadDuration + stayDuration) {
      // Stay phase
      currentRadius = radius;
      fadeProgress = 1.0;
    } else {
      // Fade out phase
      currentRadius = radius;
      const fadeElapsed = elapsed - (spreadDuration + stayDuration);
      fadeProgress = Math.max(1.0 - fadeElapsed, 0);
    }

    // Update shader uniforms
    this.material.setFloat('radius', currentRadius);
    this.material.setFloat('time', elapsed);
    this.material.setFloat('fadeProgress', fadeProgress);

    // Continue animation
    this.animationHandle = requestAnimationFrame(this.animate);
  };

  private resolvePosition(ctx: ScrawlExecContext): { x: number; y: number; z: number } | null {
    // Use explicit position if provided
    if (this.options.position) {
      return this.options.position;
    }

    // Try to get position from target (vars)
    const target = ctx.vars?.target;
    if (target?.position) {
      return target.position;
    }

    // Try to get position from source (vars)
    const source = ctx.vars?.source;
    if (source?.position) {
      return source.position;
    }

    return null;
  }

  private parseColor(colorString: string): Color3 {
    // Simple hex color parser (#RRGGBB)
    if (colorString.startsWith('#')) {
      const hex = colorString.substring(1);
      const r = parseInt(hex.substring(0, 2), 16) / 255;
      const g = parseInt(hex.substring(2, 4), 16) / 255;
      const b = parseInt(hex.substring(4, 6), 16) / 255;
      return new Color3(r, g, b);
    }

    // Fallback to white
    return new Color3(1, 1, 1);
  }

  private cleanup() {
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    if (this.mesh) {
      this.mesh.dispose();
      this.mesh = null;
    }

    if (this.material) {
      this.material.dispose();
      this.material = null;
    }
  }

  stop(): void {
    this.cleanup();
  }
}
