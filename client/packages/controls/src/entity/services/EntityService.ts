import { apiService } from '@/services/ApiService';

export type EntityType = 'OTHER' | 'ANIMAL' | 'NPC' | 'PLAYER' | 'REMOTE';

export interface SchedulePhase {
  name: string;
  fromHour: number;
  toHour: number;
  present: boolean;
  point?: string;
  behavior?: string;
  roamRadius?: number;
  speed?: number;
}

export interface EntityData {
  entityId: string;
  publicData: any;
  worldId: string;
  chunk: string;
  modelId: string;
  enabled: boolean;
  type: EntityType | null;
  portraitPath: string | null;
  server: Record<string, string> | null;
  epoches: number[];
  schedule: SchedulePhase[];
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
  type?: EntityType;
  portraitPath?: string;
  server?: Record<string, string>;
  epoches?: number[];
  schedule?: SchedulePhase[];
}

export interface UpdateEntityRequest {
  publicData?: any;
  modelId?: string;
  enabled?: boolean;
  type?: EntityType;
  portraitPath?: string;
  server?: Record<string, string>;
  epoches?: number[];
  schedule?: SchedulePhase[];
}

class EntityService {
  async listEntities(worldId: string, query?: string, offset: number = 0, limit: number = 50, epoch?: number): Promise<EntityListResponse> {
    const params: any = { offset, limit };
    if (query) params.query = query;
    if (epoch !== undefined) params.epoch = epoch;

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
