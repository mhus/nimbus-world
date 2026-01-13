/**
 * ShowTeamCommand - Show/hide team table
 */

import { CommandHandler } from './CommandHandler';
import { toBoolean } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

/**
 * Show/hide team table command
 */
export class ShowTeamCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'showTeam';
  }

  description(): string {
    return 'Show/hide team table. Usage: showTeam <true|false>';
  }

  execute(parameters: any[]): any {
    const show = toBoolean(parameters[0]);
    const notificationService = this.appContext.services.notification;

    if (!notificationService) {
      return { error: 'NotificationService not available' };
    }

    notificationService.showTeamTable(show);
    return { message: `Team table ${show ? 'shown' : 'hidden'}` };
  }
}
