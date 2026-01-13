/**
 * World-related messages
 */

import type { BaseMessage } from '../BaseMessage';

/**
 * World status update data
 */
export interface WorldStatusUpdateData {
  /** New world status value */
  s: number;
}

/**
 * World status update (Server -> Client)
 * Server sends new world status
 * All chunks will be re-rendered if status changed
 */
export type WorldStatusUpdateMessage = BaseMessage<WorldStatusUpdateData>;
