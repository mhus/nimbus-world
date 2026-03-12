import { apiService } from '@/services/ApiService';

export type TeamStatus = 'LOBBY' | 'ACTIVE' | 'INACTIVE';

export interface Team {
  id: string;
  worldId: string;
  teamId: string;
  title: string;
  members: string[];
  invitation: string[];
  status: TeamStatus;
  createdAt: string;
  updatedAt: string;
}

export interface TeamCreateRequest {
  worldId: string;
  title: string;
  creatorPlayerName: string;
}

class TeamServiceFrontend {
  async listTeams(worldId: string, status?: TeamStatus): Promise<Team[]> {
    const params = new URLSearchParams();
    params.append('worldId', worldId);
    if (status) params.append('status', status);
    return apiService.get<Team[]>(`/control/teams?${params.toString()}`);
  }

  async getTeam(teamId: string): Promise<Team> {
    return apiService.get<Team>(`/control/teams/${teamId}`);
  }

  async createTeam(request: TeamCreateRequest): Promise<Team> {
    return apiService.post<Team>('/control/teams', request);
  }

  async updateTeam(teamId: string, title: string): Promise<Team> {
    return apiService.put<Team>(`/control/teams/${teamId}`, { title });
  }

  async deleteTeam(teamId: string): Promise<void> {
    return apiService.delete<void>(`/control/teams/${teamId}`);
  }

  async updateStatus(teamId: string, status: TeamStatus): Promise<Team> {
    return apiService.put<Team>(`/control/teams/${teamId}/status`, { status });
  }

  async addMember(teamId: string, playerName: string): Promise<Team> {
    return apiService.post<Team>(`/control/teams/${teamId}/members`, { playerName });
  }

  async removeMember(teamId: string, playerName: string): Promise<Team> {
    return apiService.delete<Team>(`/control/teams/${teamId}/members/${playerName}`);
  }

  async addInvitation(teamId: string, playerName: string): Promise<Team> {
    return apiService.post<Team>(`/control/teams/${teamId}/invitations`, { playerName });
  }

  async removeInvitation(teamId: string, playerName: string): Promise<Team> {
    return apiService.delete<Team>(`/control/teams/${teamId}/invitations/${playerName}`);
  }

  async emigrate(teamId: string, instanceWorldId: string): Promise<Team> {
    return apiService.post<Team>(`/control/teams/${teamId}/emigrate`, { instanceWorldId });
  }
}

export const teamServiceFrontend = new TeamServiceFrontend();
