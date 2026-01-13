/**
 * PlayerEntity - Player as physics entity with player info
 *
 * Extends PhysicsEntity with PlayerInfo to store player-specific
 * properties that can change dynamically during gameplay.
 *
 * **Cached Effective Values:**
 * For performance, effective values from PlayerInfo are cached directly on the entity.
 * These are updated when 'playerInfo:updated' event is emitted.
 * PhysicsService uses these cached values instead of looking up PlayerInfo each frame.
 */

import type { PhysicsEntity } from '../services/PhysicsService';
import type { PlayerInfo } from '@nimbus/shared';

/**
 * Player entity with physics and player-specific properties
 *
 * Combines physics simulation (PhysicsEntity) with player configuration (PlayerInfo).
 * PlayerInfo values can be updated dynamically through power-ups, status effects, equipment, etc.
 *
 * State-dependent cached values are now in PhysicsEntity (effectiveSpeed, effectiveJumpSpeed, etc.)
 * and are updated when movement state changes via PlayerService.onMovementStateChanged().
 */
export interface PlayerEntity extends PhysicsEntity {
  /** Player-specific configuration and properties (full info) */
  playerInfo: PlayerInfo;

  // ============================================
  // NOTE: State-dependent caches moved to PhysicsEntity
  // ============================================
  // effectiveSpeed - in PhysicsEntity (varies: WALK=5.0, SPRINT=7.0, CROUCH=2.5)
  // effectiveJumpSpeed - in PhysicsEntity (varies: WALK=8.0, CROUCH=4.0, RIDING=10.0)
  // effectiveTurnSpeed - in PhysicsEntity
  // cachedEyeHeight - in PhysicsEntity (varies: WALK=1.6, CROUCH=0.8)
  // cachedSelectionRadius - in PhysicsEntity (varies: WALK=5.0, CROUCH=4.0, FLY=8.0)
}
