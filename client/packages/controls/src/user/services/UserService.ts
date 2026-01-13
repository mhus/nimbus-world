import { apiService } from '@/services/ApiService';
import type { RUser } from '@nimbus/shared/generated/entities/RUser';
import type { Settings } from '@nimbus/shared';
import { SectorRoles } from '@nimbus/shared/generated/entities/SectorRoles';
import type { RegionRoles } from '@nimbus/shared/generated/entities/RegionRoles';

export type { RUser, Settings, RegionRoles };
export { SectorRoles };

export interface UserRequest {
  email: string;
  sectorRolesRaw: string;
  publicData?: {
    userId: string;
    displayName: string;
  };
}

export interface UpdatePublicDataRequest {
  displayName: string;
}

class UserService {
  async listUsers(): Promise<RUser[]> {
    return apiService.get<RUser[]>('/control/users');
  }

  async getUser(username: string): Promise<RUser> {
    return apiService.get<RUser>(`/control/users/${username}`);
  }

  async updateUser(username: string, user: RUser): Promise<RUser> {
    return apiService.put<RUser>(`/control/users/${username}`, user);
  }

  async deleteUser(username: string): Promise<void> {
    return apiService.delete<void>(`/control/users/${username}`);
  }

  async getUserSettings(username: string): Promise<Record<string, Settings>> {
    return apiService.get<Record<string, Settings>>(`/control/users/${username}/settings`);
  }

  async getSettingsForClientType(username: string, clientType: string): Promise<Settings> {
    return apiService.get<Settings>(`/control/users/${username}/settings/${clientType}`);
  }

  async updateSettingsForClientType(username: string, clientType: string, settings: Settings): Promise<any> {
    return apiService.put<any>(`/control/users/${username}/settings/${clientType}`, settings);
  }

  async deleteSettingsForClientType(username: string, clientType: string): Promise<void> {
    return apiService.delete<void>(`/control/users/${username}/settings/${clientType}`);
  }

  async updateAllSettings(username: string, settings: Record<string, Settings>): Promise<any> {
    return apiService.put<any>(`/control/users/${username}/settings`, settings);
  }

  async addSectorRole(username: string, role: string): Promise<RUser> {
    return apiService.post<RUser>(`/control/users/${username}/roles/${role}`);
  }

  async removeSectorRole(username: string, role: string): Promise<RUser> {
    return apiService.delete<RUser>(`/control/users/${username}/roles/${role}`);
  }
}

export const userService = new UserService();
