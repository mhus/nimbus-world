/**
 * LoginMessageHandler - Handles login response messages
 *
 * Processes login responses from server and updates AppContext
 * with world information.
 */

import {
  ResponseMessage,
  MessageType,
  LoginResponseData,
  LoginErrorData,
  getLogger,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { AppContext } from '../../AppContext';
import type { NetworkService } from '../../services/NetworkService';

const logger = getLogger('LoginMessageHandler');

/**
 * Handles LOGIN_RESPONSE messages from server
 */
export class LoginMessageHandler extends MessageHandler<LoginResponseData | LoginErrorData> {
  readonly messageType = MessageType.LOGIN_RESPONSE;

  constructor(
    private appContext: AppContext,
    private networkService: NetworkService
  ) {
    super();
  }

  handle(message: ResponseMessage<LoginResponseData | LoginErrorData>): void {
    const data = message.d;

    if (!data) {
      logger.error('Login response has no data');
      this.networkService.emit('login:error', new Error('No data in login response'));
      return;
    }

    if (data.success) {
      // Success response
      const successData = data as LoginResponseData;

      // Debug log the response structure
      logger.debug('Login response data', { data: successData });

      // Generate player entity ID from username and sessionId
      // Format: @{username}_{sessionId}
      if (this.appContext.playerInfo) {
        const username = successData.userId || 'player';
        const sessionId = successData.sessionId || 'unknown';
        this.appContext.playerInfo.playerId = `@${username}_${sessionId}`;
        this.appContext.playerInfo.title = successData.title || username;

        logger.debug('Player ID generated', {
          playerId: this.appContext.playerInfo.playerId,
          displayName: this.appContext.playerInfo.title,
        });
      }

      logger.debug('Login successful', {
        userId: successData.userId,
        displayName: successData.title,
        sessionId: successData.sessionId,
      });

      // Emit event for other services
      this.networkService.emit('login:success', successData);

      // Check if this is a session restoration (reconnect with sessionId)
      // If sessionId was already set before login, this is a restoration
      const isReconnect = this.networkService.getConnectionState() === 'CONNECTED' &&
                          this.appContext.sessionId === successData.sessionId;

      if (isReconnect) {
        logger.debug('Session restored after reconnect');

        // Clear DEAD mode after successful reconnect
        const playerService = this.appContext.services.player;
        if (playerService) {
          playerService.setPlayerDeadState(false);
        }

        // Emit session:restore event for services to restore their state
        this.networkService.emit('session:restore', successData);
      }
    } else {
      // Error response
      const errorData = data as LoginErrorData;

      logger.error('Login failed', {
        errorCode: errorData.errorCode,
        errorMessage: errorData.errorMessage,
      });

      // Emit error event
      // no need for this: this.networkService.emit('login:error', new Error(errorData.errorMessage));
      this.appContext.services.config?.gotoLoginScreen();
    }
  }
}
