/**
 * Layer Service
 * Manages layer CRUD operations
 */

import type { CreateLayerRequest, UpdateLayerRequest, LayerDto } from '@nimbus/shared';
import { apiService } from './ApiService';

export interface LayerListResponse {
  layers: LayerDto[];
  count: number;
  limit: number;
  offset: number;
}

export interface LayerCreateResponse {
  id: string;
}

export interface LayerPagingParams {
  query?: string;
  limit?: number;
  offset?: number;
}

export class LayerService {
  /**
   * Get all layers or search with pagination
   */
  async getLayers(
    worldId: string,
    params?: LayerPagingParams
  ): Promise<LayerListResponse> {
    console.log('[LayerService] getLayers called', { worldId, params });
    console.log('[LayerService] Request URL:', `/control/worlds/${worldId}/layers`);

    const response = await apiService.get<LayerListResponse>(
      `/control/worlds/${worldId}/layers`,
      params
    );
    console.log('[LayerService] API response:', response);
    console.log('[LayerService] Returning:', {
      count: response.count,
      layers: response.layers?.length ?? 0,
      limit: response.limit,
      offset: response.offset
    });
    return response;
  }

  /**
   * Get single layer by ID
   */
  async getLayer(worldId: string, id: string): Promise<LayerDto> {
    return apiService.get<LayerDto>(`/control/worlds/${worldId}/layers/${id}`);
  }

  /**
   * Create new layer
   */
  async createLayer(worldId: string, layer: CreateLayerRequest): Promise<string> {
    const response = await apiService.post<LayerCreateResponse>(
      `/control/worlds/${worldId}/layers`,
      layer
    );
    return response.id;
  }

  /**
   * Update existing layer
   */
  async updateLayer(worldId: string, id: string, layer: UpdateLayerRequest): Promise<LayerDto> {
    return apiService.put<LayerDto>(
      `/control/worlds/${worldId}/layers/${id}`,
      layer
    );
  }

  /**
   * Delete layer
   */
  async deleteLayer(worldId: string, id: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/layers/${id}`);
  }

  /**
   * Regenerate layer
   * Triggers complete regeneration of layer data
   */
  async regenerate(worldId: string, id: string): Promise<void> {
    return apiService.post<void>(
      `/control/worlds/${worldId}/layers/${id}/regenerate`,
      {}
    );
  }
}

export const layerService = new LayerService();
