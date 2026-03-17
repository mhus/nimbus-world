/**
 * RevivedCommand - Handle server notification that the player has been revived
 *
 * Exits dead mode (removes fog, re-enables movement and input)
 * and re-enables reconnection.
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('RevivedCommand');

export class RevivedCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'revived';
  }

  description(): string {
    return 'Server notification that the player has been revived';
  }

  execute(_parameters: any[]): any {
    logger.info('Player revived — exiting death state');

    // Exit dead mode (remove fog, re-enable movement, set idle pose, enable input)
    const playerService = this.appContext.services.player;
    if (playerService) {
      playerService.setPlayerDeadState(false);
    }

    // Re-enable reconnection
    const networkService = this.appContext.services.network;
    if (networkService) {
      networkService.setReconnectEnabled(true);
    }

    return { message: 'Player revived' };
  }
}
