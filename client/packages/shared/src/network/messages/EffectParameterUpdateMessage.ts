/**
 * Effect Parameter Update Messages
 *
 * Used to synchronize effect parameter changes across clients.
 * When an effect's parameters are updated (e.g., beam position following cursor),
 * the update is sent to the server which broadcasts it to other clients.
 */

import type { BaseMessage } from '../BaseMessage';
import type { ChunkCoordinate } from './ChunkMessage';
import type { SerializableTargetingContext } from '../../types/TargetingTypes';

/**
 * Effect parameter update data
 *
 * Sent from client to server, and broadcasted from server to relevant clients.
 */
export interface EffectParameterUpdateData {
  /**
   * Effect ID (matches the effectId from EffectTriggerData)
   * Used to identify which effect to update
   */
  effectId: string;

  /**
   * Parameter name to update
   */
  paramName: string;

  /**
   * New parameter value (optional when targeting context is provided)
   */
  value?: any;

  /**
   * Affected chunks (optional)
   * Used by server to determine which clients should receive the update
   * Copied from the original effect trigger
   */
  chunks?: ChunkCoordinate[];

  /**
   * Targeting context for position-based parameters (optional)
   *
   * When paramName is 'targetPos', this provides full targeting context
   * so remote clients can properly resolve/track targets.
   *
   * This allows remote clients to understand:
   * - What targeting mode was used (ENTITY, BLOCK, GROUND, etc.)
   * - What type of target was resolved (entity ID, block position, etc.)
   * - The final position of the target
   */
  targeting?: SerializableTargetingContext;
}

/**
 * Effect Parameter Update Message (Client -> Server)
 *
 * Client sends this when a local effect's parameters change.
 *
 * Message type: 'ef.p.u'
 */
export type EffectParameterUpdateClientMessage = BaseMessage<EffectParameterUpdateData>;

/**
 * Effect Parameter Update Message (Server -> Client)
 *
 * Server broadcasts this to clients that have registered the affected chunks.
 *
 * Message type: 'ef.p.u'
 */
export type EffectParameterUpdateServerMessage = BaseMessage<EffectParameterUpdateData>;
