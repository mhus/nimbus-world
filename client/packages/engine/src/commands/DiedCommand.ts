/**
 * DiedCommand - Handle server notification that the player has died
 *
 * Enters dead mode (fog, no movement, death pose) and disables reconnection
 * so that when the server closes the connection after the death timeout,
 * the client redirects to the exit URL instead of attempting to reconnect.
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('DiedCommand');

export class DiedCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'died';
  }

  description(): string {
    return 'Server notification that the player has died';
  }

  execute(_parameters: any[]): any {
    logger.info('Player died — entering death state');

    // Enter dead mode (fog, no movement, death pose, disable input)
    const playerService = this.appContext.services.player;
    if (playerService) {
      playerService.setPlayerDeadState(true);
    }

    // Disable reconnection — when server closes the connection after timeout,
    // the client should redirect to exit URL, not try to reconnect
    const networkService = this.appContext.services.network;
    if (networkService) {
      networkService.setReconnectEnabled(false);
    }

    return { message: 'Player entered death state' };
  }
}
