/**
 * FlyModeController - Fly/creative mode physics
 *
 * Simple direct movement without gravity or collision.
 * Used for:
 * - Fly mode (creative/editor)
 * - Teleport mode (waiting for chunks)
 */

import { Vector3 } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { PhysicsEntity } from './types';
import type { AppContext } from '../../AppContext';
import * as PhysicsUtils from './PhysicsUtils';

const logger = getLogger('FlyModeController');

/**
 * FlyModeController - Handles fly/creative physics
 */
export class FlyModeController {
  constructor(private appContext: AppContext, private flySpeed: number = 10.0) {}

  /**
   * Update fly mode - direct movement, no gravity, no collision
   */
  update(entity: PhysicsEntity, movementVector: Vector3, deltaTime: number): void {
    // Direct velocity from input
    const speed = this.getFlySpeed(entity);
    entity.velocity.copyFrom(movementVector.scale(speed));

    // Apply movement
    entity.position.addInPlace(entity.velocity.scale(deltaTime));

    // Clamp to world bounds
    PhysicsUtils.clampToWorldBounds(entity, this.appContext);

    // Mark as not grounded
    entity.grounded = false;
    entity.onSlope = false;
  }

  /**
   * Get fly speed for entity
   */
  private getFlySpeed(entity: PhysicsEntity): number {
    // Check if player entity
    if ('playerInfo' in entity) {
      const playerInfo = (entity as any).playerInfo;
      return playerInfo.effectiveWalkSpeed * 2.0; // Fly is faster
    }
    return this.flySpeed;
  }
}
