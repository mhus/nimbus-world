/**
 * TeamService - Manages team data and member status
 *
 * Handles team data storage and updates notification service for UI rendering
 */

import { getLogger, type TeamData, type TeamMember, type TeamStatusUpdate } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

const logger = getLogger('TeamService');

/**
 * TeamService - Manages team data and coordinates with NotificationService for UI
 */
export class TeamService {
  private appContext: AppContext;
  private currentTeam: TeamData | null = null;
  private memberMap: Map<string, TeamMember> = new Map();

  constructor(appContext: AppContext) {
    this.appContext = appContext;
    logger.debug('TeamService initialized');
  }

  /**
   * Set complete team data
   * @param team Team data
   */
  setTeamData(team: TeamData): void {
    this.currentTeam = team;
    this.memberMap.clear();

    team.members.forEach(member => {
      this.memberMap.set(member.playerId, member);
    });

    // Update UI
    const notificationService = this.appContext.services.notification;
    if (notificationService) {
      notificationService.setTeam(team.name, team.members);
    }

    logger.info('Team data set', { teamId: team.id, memberCount: team.members.length });
  }

  /**
   * Update team member status
   * @param update Status update
   */
  updateTeamStatus(update: TeamStatusUpdate): void {
    if (!this.currentTeam || this.currentTeam.id !== update.id) {
      return;
    }

    const notificationService = this.appContext.services.notification;

    update.ms.forEach(memberUpdate => {
      const member = this.memberMap.get(memberUpdate.id);
      if (!member) return;

      // Update member data
      if (memberUpdate.h !== undefined) member.health = memberUpdate.h;
      if (memberUpdate.po !== undefined) member.position = memberUpdate.po;
      if (memberUpdate.st !== undefined) member.status = memberUpdate.st;

      // Update UI
      if (notificationService) {
        notificationService.updateTeamMember(member);
      }
    });
  }

  /**
   * Get current team
   * @returns Current team or null
   */
  getTeam(): TeamData | null {
    return this.currentTeam;
  }

  /**
   * Get team member by player ID
   * @param playerId Player ID
   * @returns Team member or null
   */
  getTeamMember(playerId: string): TeamMember | null {
    return this.memberMap.get(playerId) || null;
  }

  /**
   * Clear current team
   */
  clearTeam(): void {
    this.currentTeam = null;
    this.memberMap.clear();

    const notificationService = this.appContext.services.notification;
    if (notificationService) {
      notificationService.showTeamTable(false);
    }

    logger.info('Team cleared');
  }
}
