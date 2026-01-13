/**
 * Layer Model Service
 * Manages layer model CRUD operations
 */

import type { LayerModelDto, CreateLayerModelRequest, UpdateLayerModelRequest } from '@nimbus/shared';
import { apiService } from './ApiService';

export interface LayerModelListResponse {
  models: LayerModelDto[];
  count: number;
}

export interface LayerModelCreateResponse {
  id: string;
}

export class LayerModelService {
  /**
   * Get all models for a layer
   */
  async getModels(
    worldId: string,
    layerId: string
  ): Promise<LayerModelListResponse> {
    return apiService.get<LayerModelListResponse>(
      `/control/worlds/${worldId}/layers/${layerId}/models`
    );
  }

  /**
   * Get single model by ID
   */
  async getModel(worldId: string, layerId: string, id: string): Promise<LayerModelDto> {
    return apiService.get<LayerModelDto>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}`
    );
  }

  /**
   * Create new model
   */
  async createModel(
    worldId: string,
    layerId: string,
    model: CreateLayerModelRequest
  ): Promise<string> {
    const response = await apiService.post<LayerModelCreateResponse>(
      `/control/worlds/${worldId}/layers/${layerId}/models`,
      model
    );
    return response.id;
  }

  /**
   * Update existing model
   */
  async updateModel(
    worldId: string,
    layerId: string,
    id: string,
    model: UpdateLayerModelRequest
  ): Promise<LayerModelDto> {
    return apiService.put<LayerModelDto>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}`,
      model
    );
  }

  /**
   * Delete model
   */
  async deleteModel(worldId: string, layerId: string, id: string): Promise<void> {
    return apiService.delete<void>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}`
    );
  }

  /**
   * Sync model to terrain
   * Manually triggers transfer of model data to terrain layer and marks chunks as dirty
   */
  async syncToTerrain(worldId: string, layerId: string, id: string): Promise<void> {
    return apiService.post<void>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}/sync`,
      {}
    );
  }

  /**
   * Transform model by automatically adjusting center
   * Calculates average position of all blocks and shifts coordinates to make this the new origin.
   * mountX/Y/Z are adjusted in opposite direction to keep world position.
   */
  async transformAutoAdjustCenter(worldId: string, layerId: string, id: string): Promise<LayerModelDto> {
    const response = await apiService.post<{ success: boolean; model: LayerModelDto; message: string }>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}/transform/auto-adjust-center`,
      {}
    );
    return response.model;
  }

  /**
   * Transform model by manually adjusting center
   * Shifts all block coordinates by specified offset and adjusts mountX/Y/Z in opposite direction.
   * The model remains at the same world position but with transformed origin.
   */
  async transformManualAdjustCenter(
    worldId: string,
    layerId: string,
    id: string,
    offsetX: number,
    offsetY: number,
    offsetZ: number
  ): Promise<LayerModelDto> {
    const response = await apiService.post<{ success: boolean; model: LayerModelDto; message: string }>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}/transform/manual-adjust-center?offsetX=${offsetX}&offsetY=${offsetY}&offsetZ=${offsetZ}`,
      {}
    );
    return response.model;
  }

  /**
   * Transform model by moving all blocks
   * Shifts all block coordinates by specified offset. Mount point stays the same.
   * Automatically syncs to terrain after transformation.
   */
  async transformMove(
    worldId: string,
    layerId: string,
    id: string,
    offsetX: number,
    offsetY: number,
    offsetZ: number
  ): Promise<{ model: LayerModelDto; chunksAffected: number }> {
    const response = await apiService.post<{
      success: boolean;
      model: LayerModelDto;
      chunksAffected: number;
      message: string;
    }>(
      `/control/worlds/${worldId}/layers/${layerId}/models/${id}/transform/move?offsetX=${offsetX}&offsetY=${offsetY}&offsetZ=${offsetZ}`,
      {}
    );
    return { model: response.model, chunksAffected: response.chunksAffected };
  }

  /**
   * Copy model to another layer (possibly in different world)
   * Creates a complete copy with new worldId and layerDataId from target layer
   */
  async copyModel(
    sourceWorldId: string,
    sourceLayerId: string,
    sourceModelId: string,
    targetLayerId: string,
    newName?: string
  ): Promise<{ id: string; model: LayerModelDto }> {
    const params = new URLSearchParams({ targetLayerId });
    if (newName) {
      params.append('newName', newName);
    }

    const response = await apiService.post<{
      success: boolean;
      id: string;
      model: LayerModelDto;
      message: string;
    }>(
      `/control/worlds/${sourceWorldId}/layers/${sourceLayerId}/models/${sourceModelId}/copy?${params.toString()}`,
      {}
    );
    return { id: response.id, model: response.model };
  }
}

export const layerModelService = new LayerModelService();
