import { apiService } from '@/services/ApiService';

export interface BackdropData {
  backdropId: string;
  publicData: any;
  worldId: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface BackdropListResponse {
  backdrops: any[];
  count: number;
  limit: number;
  offset: number;
}

export interface CreateBackdropRequest {
  backdropId: string;
  publicData: any;
}

export interface UpdateBackdropRequest {
  publicData?: any;
  enabled?: boolean;
}

class BackdropService {
  async listBackdrops(worldId: string, query?: string, offset: number = 0, limit: number = 50): Promise<BackdropListResponse> {
    const params: any = { offset, limit };
    if (query) params.query = query;

    return apiService.get<BackdropListResponse>(`/control/worlds/${worldId}/backdrops`, params);
  }

  async getBackdrop(worldId: string, backdropId: string): Promise<any> {
    return apiService.get<any>(`/control/worlds/${worldId}/backdrops/${backdropId}`);
  }

  async createBackdrop(worldId: string, request: CreateBackdropRequest): Promise<any> {
    return apiService.post<any>(`/control/worlds/${worldId}/backdrops`, request);
  }

  async updateBackdrop(worldId: string, backdropId: string, request: UpdateBackdropRequest): Promise<any> {
    return apiService.put<any>(`/control/worlds/${worldId}/backdrops/${backdropId}`, request);
  }

  async deleteBackdrop(worldId: string, backdropId: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/backdrops/${backdropId}`);
  }
}

export const backdropService = new BackdropService();
