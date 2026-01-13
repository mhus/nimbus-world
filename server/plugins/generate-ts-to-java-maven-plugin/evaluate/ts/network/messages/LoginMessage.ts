/**
 * Login messages
 */

import type { RequestMessage, ResponseMessage } from '../BaseMessage';
import type { ClientType } from '../MessageTypes';

/**
 * Login request with username/password
 */
export interface LoginRequestData {
  username?: string;
  password?: string;
  token?: string;
  worldId: string;
  clientType: ClientType;
  sessionId?: string;
}

/**
 * Login response data (success)
 *
 * Note: WorldInfo is now loaded separately via REST API /api/worlds/{worldId}/config
 * before connecting to WebSocket
 */
export interface LoginResponseData {
  success: true;
  userId: string;
  displayName: string;
  sessionId: string;
}

/**
 * Login response data (failure)
 */
export interface LoginErrorData {
  success: false;
  errorCode: number;
  errorMessage: string;
}

/**
 * Login request message
 */
export type LoginMessage = RequestMessage<LoginRequestData>;

/**
 * Login response message
 */
export type LoginResponseMessage = ResponseMessage<
  LoginResponseData | LoginErrorData
>;
