/**
 * ProjectileEffect - Flying projectile effect
 *
 * Creates a projectile that flies from start to target position.
 * The projectile is a tapered cylinder (narrower at head) with texture and rotation.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import {
  Mesh,
  MeshBuilder,
  StandardMaterial,
  Texture,
  Vector3,
  Color3,
} from '@babylonjs/core';

const logger = getLogger('ProjectileEffect');

/**
 * Options for ProjectileEffect
 */
export interface ProjectileOptions {
  /** Start position - uses source from context if not provided */
  startPosition?: { x: number; y: number; z: number };

  /** Target position - uses target from context if not provided */
  targetPosition?: { x: number; y: number; z: number };

  /** Total width/length of projectile */
  projectileWidth: number;

  /** Width of the head section (tapered end) */
  projectileHeadWidth: number;

  /** Radius/thickness of the projectile */
  projectileRadius: number;

  /** Texture path for the projectile */
  projectileTexture: string;

  /** Flight speed in units per second */
  speed: number;

  /** Rotation speed in radians per second */
  rotationSpeed: number;

  /** Color tint (optional, default white) */
  color?: string;

  /** Shape: 'projectile' (tapered cylinder, default) or 'bullet' (sphere) */
  shape?: 'projectile' | 'bullet';
}

/**
 * ProjectileEffect - Creates a flying projectile
 *
 * Usage:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "projectile",
 *   "ctx": {
 *     "startPosition": {"x": 0, "y": 65, "z": 0},
 *     "targetPosition": {"x": 10, "y": 65, "z": 10},
 *     "projectileWidth": 2,
 *     "projectileHeadWidth": 0.5,
 *     "projectileRadius": 0.2,
 *     "projectileTexture": "effects/arrow.png",
 *     "speed": 20,
 *     "rotationSpeed": 5
 *   }
 * }
 * ```
 */
export class ProjectileEffect extends ScrawlEffectHandler<ProjectileOptions> {
  private mesh: Mesh | null = null;
  private material: StandardMaterial | null = null;
  private startTime: number = 0;
  private startPos: Vector3 | null = null;
  private targetPos: Vector3 | null = null;
  private distance: number = 0;
  private animationHandle: number | null = null;
  private rotationAngle: number = 0;

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }

    try {
      // Resolve positions
      this.startPos = this.resolvePosition(ctx, true, this.options.startPosition);
      this.targetPos = this.resolvePosition(ctx, false, this.options.targetPosition);

      if (!this.startPos || !this.targetPos) {
        logger.warn('Missing start or target position');
        return;
      }

      // Calculate distance and direction
      const directionVec = this.targetPos.subtract(this.startPos);
      this.distance = directionVec.length();

      if (this.distance === 0) {
        logger.warn('Start and target positions are identical');
        return;
      }

      const directionNorm = directionVec.normalize();

      // Create projectile mesh based on shape
      const shape = this.options.shape || 'projectile';
      this.mesh = shape === 'bullet'
        ? this.createBulletMesh(scene)
        : this.createProjectileMesh(scene);

      // Position at start
      this.mesh.position = this.startPos.clone();

      // Orient towards target (only for projectile shape, bullet is sphere so no orientation needed)
      if (shape === 'projectile') {
        // Since cylinder is along Y-axis and we rotated it to Z-axis,
        // we need to align the local Z-axis with the direction vector
        this.mesh.setDirection(directionNorm, 0, Math.PI / 2);
      }

      // Create material
      this.material = new StandardMaterial('projectileMaterial', scene);
      this.material.backFaceCulling = false;
      this.material.transparencyMode = 1; // ALPHATEST

      // Load texture
      const networkService = ctx.appContext.services.network;
      if (networkService) {
        const textureUrl = networkService.getAssetUrl(this.options.projectileTexture);
        const texture = new Texture(textureUrl, scene);
        texture.hasAlpha = true;
        this.material.diffuseTexture = texture;
      }

      // Apply color tint
      if (this.options.color) {
        this.material.diffuseColor = this.parseColor(this.options.color);
      }

      this.mesh.material = this.material;

      // Start animation
      this.startTime = this.now();
      this.animate();
    } catch (error) {
      logger.error('Failed to create projectile', { error });
      this.cleanup();
    }
  }

  private createProjectileMesh(scene: any): Mesh {
    const {
      projectileWidth,
      projectileHeadWidth,
      projectileRadius,
    } = this.options;

    // Create tapered cylinder (narrow at front/head)
    const cylinder = MeshBuilder.CreateCylinder(
      'projectile',
      {
        height: projectileWidth,
        diameterTop: projectileRadius * 2 * (projectileHeadWidth / projectileWidth),
        diameterBottom: projectileRadius * 2,
        tessellation: 16,
      },
      scene
    );

    // Rotate cylinder to align with forward direction (Y-axis to Z-axis)
    cylinder.rotation.x = Math.PI / 2;

    return cylinder;
  }

  private createBulletMesh(scene: any): Mesh {
    const { projectileRadius } = this.options;

    // Create sphere for bullet
    const sphere = MeshBuilder.CreateSphere(
      'bullet',
      {
        diameter: projectileRadius * 2,
        segments: 16,
      },
      scene
    );

    return sphere;
  }

  private animate = () => {
    if (!this.mesh || !this.startPos || !this.targetPos) {
      return;
    }

    const elapsed = this.now() - this.startTime;
    const travelDistance = elapsed * this.options.speed;

    // Check if reached target
    if (travelDistance >= this.distance) {
      this.cleanup();
      return;
    }

    // Calculate current position
    const direction = this.targetPos.subtract(this.startPos).normalize();
    const currentPos = this.startPos.add(direction.scale(travelDistance));
    this.mesh.position = currentPos;

    // Update rotation angle
    this.rotationAngle += this.options.rotationSpeed * 0.016; // Assume ~60fps

    // Apply rotation based on shape
    const shape = this.options.shape || 'projectile';
    if (shape === 'projectile') {
      // Reapply orientation and rotation around flight axis
      this.mesh.setDirection(direction, this.rotationAngle, Math.PI / 2);
    } else {
      // For bullet (sphere), just rotate around any axis (visual effect)
      this.mesh.rotation.y = this.rotationAngle;
      this.mesh.rotation.x = this.rotationAngle * 0.7;
    }

    // Continue animation
    this.animationHandle = requestAnimationFrame(this.animate);
  };

  private resolvePosition(
    ctx: ScrawlExecContext,
    isStart: boolean,
    explicitPos?: { x: number; y: number; z: number }
  ): Vector3 | null {
    // Use explicit position if provided
    if (explicitPos) {
      return new Vector3(explicitPos.x, explicitPos.y, explicitPos.z);
    }

    // For start: use source from vars
    const source = ctx.vars?.source;
    if (isStart && source?.position) {
      return new Vector3(source.position.x, source.position.y, source.position.z);
    }

    // For target: use target from vars
    const target = ctx.vars?.target;
    if (!isStart && target?.position) {
      const p = target.position;
      return new Vector3(p.x, p.y, p.z);
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
