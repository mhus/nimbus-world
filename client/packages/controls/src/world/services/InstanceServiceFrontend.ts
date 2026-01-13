import { apiService } from '@/services/ApiService';

export interface Instance {
  id: string;
  instanceId: string;
  worldId: string;
  title: string;
  description: string;
  creator: string;
  players: string[];
  createdAt: string;
  updatedAt: string;
  enabled: boolean;
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

  async deleteInstance(instanceId: string): Promise<void> {
    return apiService.delete<void>(`/control/instances/${instanceId}`);
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
