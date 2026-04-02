import { apiService } from '@/services/ApiService';

export type InstanceAccessType = 'PRIVATE' | 'TEAM' | 'PUBLIC';
export type InstanceDurationType = 'SHORT' | 'SEASONAL' | 'EVENT';

export interface Instance {
  id: string;
  instanceId: string;
  worldId: string;
  title: string;
  description: string;
  creator: string;
  players: string[];
  activePlayers: string[];
  accessType: InstanceAccessType;
  durationType: InstanceDurationType;
  expiresAt?: string;
  createdAt: string;
  updatedAt: string;
  enabled: boolean;
  epoch: number;
}

export interface InstanceUpdateRequest {
  title?: string;
  description?: string;
  accessType?: InstanceAccessType;
  durationType?: InstanceDurationType;
  expiresAt?: string;
  enabled?: boolean;
  players?: string[];
}

export interface InstanceStats {
  totalCount: number;
  worldId: string;
  creator: string;
}

class InstanceServiceFrontend {
  async listInstances(worldId?: string, creator?: string): Promise<Instance[]> {
    const params = new URLSearchParams();
    if (worldId) params.append('worldId', worldId);
    if (creator) params.append('creator', creator);

    const queryString = params.toString();
    const url = queryString ? `/control/instances?${queryString}` : '/control/instances';

    return apiService.get<Instance[]>(url);
  }

  async getInstance(instanceId: string): Promise<Instance> {
    return apiService.get<Instance>(`/control/instances/${instanceId}`);
  }

  async updateInstance(instanceId: string, request: InstanceUpdateRequest): Promise<Instance> {
    return apiService.put<Instance>(`/control/instances/${instanceId}`, request);
  }

  async deleteInstance(instanceId: string): Promise<void> {
    return apiService.delete<void>(`/control/instances/${instanceId}`);
  }

  async switchEpoch(instanceId: string, epoch: number): Promise<Instance> {
    return apiService.put<Instance>(`/control/instances/${instanceId}/epoch`, { epoch });
  }

  async getStats(worldId?: string, creator?: string): Promise<InstanceStats> {
    const params = new URLSearchParams();
    if (worldId) params.append('worldId', worldId);
    if (creator) params.append('creator', creator);

    const queryString = params.toString();
    const url = queryString ? `/control/instances/stats?${queryString}` : '/control/instances/stats';

    return apiService.get<InstanceStats>(url);
  }
}

export const instanceServiceFrontend = new InstanceServiceFrontend();
