/**
 * TeamDataMessageHandler - Handles team data messages
 *
 * Processes complete team data from server and updates TeamService
 */

import { BaseMessage, MessageType, getLogger, type TeamData } from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { AppContext } from '../../AppContext';
import type { TeamService } from '../../services/TeamService';

const logger = getLogger('TeamDataMessageHandler');

/**
 * Handles TEAM_DATA messages from server
 */
export class TeamDataMessageHandler extends MessageHandler<TeamData> {
  readonly messageType = MessageType.TEAM_DATA;

  constructor(
    private appContext: AppContext,
    private teamService: TeamService
  ) {
    super();
  }

  handle(message: BaseMessage<TeamData>): void {
    const data = message.d;

    if (!data) {
      logger.warn('Team data message has no data');
      return;
    }

    // Empty team = team dissolved
    if (data.members.length === 0) {
      this.teamService.clearTeam();
      logger.info('Team cleared');
      return;
    }

    this.teamService.setTeamData(data);
  }
}
