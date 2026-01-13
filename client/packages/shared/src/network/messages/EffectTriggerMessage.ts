/**
 * Effect Trigger Messages
 *
 * Used to synchronize visual/audio effects across clients.
 * When a player triggers an effect (e.g., using an item, entering an area),
 * it's sent to the server which broadcasts it to other clients in the affected chunks.
 */

import type { BaseMessage } from '../BaseMessage';
import type { ScriptActionDefinition } from '../../scrawl/ScriptActionDefinition';
import type { ChunkCoordinate } from './ChunkMessage';

/**
 * Effect trigger data
 *
 * Sent from client to server, and broadcasted from server to relevant clients.
 */
export interface EffectTriggerData {
  /**
   * Unique effect ID (UUID)
   * Used to identify and deduplicate effects
   */
  effectId: string;

  /**
   * Entity ID that triggered the effect (optional)
   * Example: "@player_1234"
   */
  entityId?: string;

  /**
   * Affected chunks (optional)
   * Calculated from source and target positions
   * Server uses this to determine which clients should receive the effect
   */
  chunks?: ChunkCoordinate[];

  /**
   * The effect to execute
   * Contains the script definition with source, target, and parameters
   */
  effect: ScriptActionDefinition;
}

/**
 * Effect Trigger Message (Client -> Server)
 *
 * Client sends this when a local effect is triggered that should be
 * synchronized to other clients.
 *
 * Message type: 'e.t'
 */
export type EffectTriggerClientMessage = BaseMessage<EffectTriggerData>;

/**
 * Effect Trigger Message (Server -> Client)
 *
 * Server broadcasts this to clients that have registered the affected chunks.
 *
 * Message type: 'e.t'
 */
export type EffectTriggerServerMessage = BaseMessage<EffectTriggerData>;
