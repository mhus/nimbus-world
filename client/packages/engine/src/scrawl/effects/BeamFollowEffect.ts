import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import {
  Vector3,
  MeshBuilder,
  StandardMaterial,
  Color3,
  Mesh,
  Scene,
} from '@babylonjs/core';

const logger = getLogger('BeamFollowEffect');

export interface BeamFollowOptions {
  color?: string; // Hex color (default: '#ffffff')
  thickness?: number; // Beam diameter (default: 0.1)
  alpha?: number; // Transparency (default: 1.0)
}

/**
 * Beam effect with dynamic target position
 * Reacts to parameter updates via onParameterChanged()
 */
export class BeamFollowEffect extends ScrawlEffectHandler<BeamFollowOptions> {
  private mesh: Mesh | null = null;
  private material: StandardMaterial | null = null;
  private sourcePos: Vector3 | null = null;
  private targetPos: Vector3 | null = null;
  private animationHandle: number | null = null;
  private scene: Scene | null = null;

  isSteadyEffect(): boolean {
    return true;
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    // Get initial positions from vars
    const source = ctx.vars?.source;
    if (source) {
      // Try different position properties
      let pos = source.currentPosition || source.position || source.block?.position || source.entity?.position;
      if (pos) {
        this.sourcePos = new Vector3(pos.x, pos.y, pos.z);
      } else {
        logger.warn('Source has no position', { source });
      }
    }

    const target = ctx.vars?.target;
    if (target) {
      // Try different position properties
      // Entity wrapper: .entity.position
      // Block wrapper: .block.position
      // Direct: .position
      // Mesh: .mesh.position
      let pos = null;

      // Try currentPosition (ClientEntity from SelectService)
      if (target.currentPosition) {
        pos = target.currentPosition;
      }
      // Try entity.position (Entity wrapper)
      else if (target.entity?.position) {
        pos = target.entity.position;
      }
      // Try direct position
      else if (target.position) {
        pos = target.position;
      }
      // Try block.position (Block wrapper)
      else if (target.block?.position) {
        pos = target.block.position;
      }
      // Try mesh position (Babylon.js Entity)
      else if (target.mesh?.position) {
        pos = target.mesh.position;
      }

      if (pos) {
        // Add 0.5 offset for blocks to center, 1.0 Y offset for entities
        const isBlock = !!target.block;
        const isEntity = !!target.currentPosition || !!target.entity;
        this.targetPos = new Vector3(
          pos.x + (isBlock ? 0.5 : 0),
          pos.y + (isBlock ? 0.5 : isEntity ? 1.0 : 0),
          pos.z + (isBlock ? 0.5 : 0)
        );
      } else {
        // Log available properties to debug
        const keys = Object.keys(target).filter(k => !k.startsWith('_')).slice(0, 10);
        logger.warn('Target has no position', {
          targetKeys: keys,
          hasPosition: !!target.position,
          hasEntity: !!target.entity,
          hasBlock: !!target.block,
          hasMesh: !!target.mesh,
        });
      }
    }

    if (!this.sourcePos || !this.targetPos) {
      logger.warn('Source or target position missing after parsing', {
        hasSource: !!source,
        hasTarget: !!target,
        sourcePos: this.sourcePos,
        targetPos: this.targetPos,
      });
      return;
    }

    // Create beam geometry
    this.createBeam();

    // Start animation
    this.animate();

    logger.debug('Beam follow effect started', {
      source: this.sourcePos,
      target: this.targetPos,
    });
  }

  onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
    // Update target position from vars
    const target = ctx.vars?.target;
    if (target) {
      // Try different position properties
      let pos = null;

      // Try currentPosition (ClientEntity from SelectService)
      if (target.currentPosition) {
        pos = target.currentPosition;
      }
      // Try entity.position (Entity wrapper)
      else if (target.entity?.position) {
        pos = target.entity.position;
      }
      // Try direct position
      else if (target.position) {
        pos = target.position;
      }
      // Try block.position (Block wrapper)
      else if (target.block?.position) {
        pos = target.block.position;
      }
      // Try mesh position (Babylon.js Entity)
      else if (target.mesh?.position) {
        pos = target.mesh.position;
      }

      if (pos) {
        // Position comes from PhysicsService.checkAndEmitPlayerDirection()
        // which already includes offsets (+0.5 for blocks, +1.0 for entities)
        // So we use position directly without additional offset
        this.targetPos = new Vector3(pos.x, pos.y, pos.z);

        logger.debug('Beam target position updated', {
          paramName,
          newTarget: this.targetPos,
        });
      }
    }
  }

  private createBeam(): void {
    if (!this.scene || !this.sourcePos || !this.targetPos) return;

    // Cylinder between source and target
    const distance = Vector3.Distance(this.sourcePos, this.targetPos);
    this.mesh = MeshBuilder.CreateCylinder(
      'beam',
      {
        height: distance,
        diameter: this.options.thickness ?? 0.1,
      },
      this.scene
    );

    // Glowing material
    this.material = new StandardMaterial('beamMat', this.scene);
    const color = this.options.color || '#ffffff'; // Default to white if not specified
    this.material.emissiveColor = Color3.FromHexString(color);
    this.material.alpha = this.options.alpha ?? 1.0;
    this.mesh.material = this.material;

    this.updateBeamTransform();
  }

  private updateBeamTransform(): void {
    if (!this.mesh || !this.sourcePos || !this.targetPos) return;

    // Position at midpoint
    const midpoint = Vector3.Center(this.sourcePos, this.targetPos);
    this.mesh.position = midpoint;

    // Orient towards target
    const direction = this.targetPos.subtract(this.sourcePos);
    this.mesh.lookAt(this.targetPos);
    this.mesh.rotate(Vector3.Right(), Math.PI / 2);

    // Scale to distance
    const distance = direction.length();
    this.mesh.scaling.y = distance;
  }

  private animate = (): void => {
    if (!this.isRunning()) {
      return;
    }

    // Update beam transform every frame
    this.updateBeamTransform();

    this.animationHandle = requestAnimationFrame(this.animate);
  };

  stop(): void {
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

    logger.debug('Beam follow effect stopped');
  }

  isRunning(): boolean {
    return this.mesh !== null;
  }
}
