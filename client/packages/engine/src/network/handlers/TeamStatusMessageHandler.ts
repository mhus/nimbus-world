/**
 * TeamStatusMessageHandler - Handles team status update messages
 *
 * Processes team member status updates from server
 */

import { BaseMessage, MessageType, getLogger, type TeamStatusUpdate } from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { AppContext } from '../../AppContext';
import type { TeamService } from '../../services/TeamService';

const logger = getLogger('TeamStatusMessageHandler');

/**
 * Handles TEAM_STATUS messages from server
 */
export class TeamStatusMessageHandler extends MessageHandler<TeamStatusUpdate> {
  readonly messageType = MessageType.TEAM_STATUS;

  constructor(
    private appContext: AppContext,
    private teamService: TeamService
  ) {
    super();
  }

  handle(message: BaseMessage<TeamStatusUpdate>): void {
    const data = message.d;

    if (!data) {
      logger.warn('Team status message has no data');
      return;
    }

    this.teamService.updateTeamStatus(data);
  }
}
