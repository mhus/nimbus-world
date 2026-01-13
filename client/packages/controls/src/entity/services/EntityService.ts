import { apiService } from '@/services/ApiService';

export interface EntityData {
  entityId: string;
  publicData: any;
  worldId: string;
  chunk: string;
  modelId: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EntityListResponse {
  entities: any[];
  count: number;
  limit: number;
  offset: number;
}

export interface CreateEntityRequest {
  entityId: string;
  publicData: any;
  modelId: string;
}

export interface UpdateEntityRequest {
  publicData?: any;
  modelId?: string;
  enabled?: boolean;
}

class EntityService {
  async listEntities(worldId: string, query?: string, offset: number = 0, limit: number = 50): Promise<EntityListResponse> {
    const params: any = { offset, limit };
    if (query) params.query = query;

    return apiService.get<EntityListResponse>(`/control/worlds/${worldId}/entities`, params);
  }

  async getEntity(worldId: string, entityId: string): Promise<any> {
    return apiService.get<any>(`/control/worlds/${worldId}/entities/${entityId}`);
  }

  async createEntity(worldId: string, request: CreateEntityRequest): Promise<any> {
    return apiService.post<any>(`/control/worlds/${worldId}/entities`, request);
  }

  async updateEntity(worldId: string, entityId: string, request: UpdateEntityRequest): Promise<any> {
    return apiService.put<any>(`/control/worlds/${worldId}/entities/${entityId}`, request);
  }

  async deleteEntity(worldId: string, entityId: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/entities/${entityId}`);
  }
}

export const entityService = new EntityService();
