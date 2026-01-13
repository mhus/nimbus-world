import { apiService } from '@/services/ApiService';

export interface EntityModelData {
  modelId: string;
  publicData: any;
  worldId: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface EntityModelListResponse {
  entityModels: any[];
  count: number;
  limit: number;
  offset: number;
}

export interface CreateEntityModelRequest {
  modelId: string;
  publicData: any;
}

export interface UpdateEntityModelRequest {
  publicData?: any;
  enabled?: boolean;
}

class EntityModelService {
  async listEntityModels(worldId: string, query?: string, offset: number = 0, limit: number = 50): Promise<EntityModelListResponse> {
    const params: any = { offset, limit };
    if (query) params.query = query;

    return apiService.get<EntityModelListResponse>(`/control/worlds/${worldId}/entitymodels`, params);
  }

  async getEntityModel(worldId: string, modelId: string): Promise<any> {
    return apiService.get<any>(`/control/worlds/${worldId}/entitymodels/${modelId}`);
  }

  async createEntityModel(worldId: string, request: CreateEntityModelRequest): Promise<any> {
    return apiService.post<any>(`/control/worlds/${worldId}/entitymodels`, request);
  }

  async updateEntityModel(worldId: string, modelId: string, request: UpdateEntityModelRequest): Promise<any> {
    return apiService.put<any>(`/control/worlds/${worldId}/entitymodels/${modelId}`, request);
  }

  async deleteEntityModel(worldId: string, modelId: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/entitymodels/${modelId}`);
  }
}

export const entityModelService = new EntityModelService();
