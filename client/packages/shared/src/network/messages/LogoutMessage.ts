/**
 * Logout message
 */

import type { BaseMessage } from '../BaseMessage';

/**
 * Logout message (Client -> Server)
 * Session will be terminated
 */
export type LogoutMessage = BaseMessage<{}>;
