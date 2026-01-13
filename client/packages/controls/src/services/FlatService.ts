/**
 * Flat Service
 * Manages flat terrain data viewing and operations
 */

import { apiService } from './ApiService';

export interface FlatListItem {
  id: string;
  worldId: string;
  layerDataId: string;
  flatId: string;
  title: string | null;
  description: string | null;
  sizeX: number;
  sizeZ: number;
  mountX: number;
  mountZ: number;
  oceanLevel: number;
  createdAt: string;
  updatedAt: string;
}

export interface FlatDetail {
  id: string;
  worldId: string;
  layerDataId: string;
  flatId: string;
  title: string | null;
  description: string | null;
  sizeX: number;
  sizeZ: number;
  mountX: number;
  mountZ: number;
  oceanLevel: number;
  oceanBlockId: string;
  unknownProtected: boolean;
  levels: number[];
  columns: number[];
  createdAt: string;
  updatedAt: string;
}

export interface MaterialDefinition {
  materialId: number;
  blockDef: string;
  nextBlockDef: string | null;
  hasOcean: boolean;
  isBlockMapDelta: boolean;
  blockAtLevels: Record<number, string>;
}

export interface UpdateMaterialRequest {
  blockDef: string;
  nextBlockDef?: string | null;
  hasOcean: boolean;
  isBlockMapDelta: boolean;
  blockAtLevels: Record<number, string>;
}

export class FlatService {
  /**
   * Get all flats for a world
   */
  async getFlats(worldId: string): Promise<FlatListItem[]> {
    return apiService.get<FlatListItem[]>(
      '/control/flats',
      { worldId }
    );
  }

  /**
   * Get single flat detail
   */
  async getFlat(id: string): Promise<FlatDetail> {
    return apiService.get<FlatDetail>(
      `/control/flats/${encodeURIComponent(id)}`
    );
  }

  /**
   * Delete flat by ID
   */
  async deleteFlat(id: string): Promise<void> {
    return apiService.delete<void>(
      `/control/flats/${encodeURIComponent(id)}`
    );
  }

  /**
   * Get export URL for downloading flat data
   */
  getExportUrl(id: string): string {
    return `${apiService.getBaseUrl()}/control/flats/${encodeURIComponent(id)}/export`;
  }

  /**
   * Import flat data from uploaded file
   */
  async importFlat(id: string, file: File): Promise<FlatDetail> {
    const formData = new FormData();
    formData.append('file', file);

    const response = await fetch(
      `${apiService.getBaseUrl()}/control/flats/${encodeURIComponent(id)}/import`,
      {
        method: 'POST',
        body: formData,
      }
    );

    if (!response.ok) {
      throw new Error(`Import failed: ${response.statusText}`);
    }

    return response.json();
  }

  /**
   * Update flat metadata (title and description)
   */
  async updateMetadata(id: string, title: string | null, description: string | null): Promise<FlatDetail> {
    return apiService.patch<FlatDetail>(
      `/control/flats/${encodeURIComponent(id)}/metadata`,
      { title, description }
    );
  }

  /**
   * List all materials for a flat
   */
  async listMaterials(flatId: string): Promise<MaterialDefinition[]> {
    return apiService.get<MaterialDefinition[]>(
      `/control/flats/${encodeURIComponent(flatId)}/materials`
    );
  }

  /**
   * Get a single material definition
   */
  async getMaterial(flatId: string, materialId: number): Promise<MaterialDefinition> {
    return apiService.get<MaterialDefinition>(
      `/control/flats/${encodeURIComponent(flatId)}/materials/${materialId}`
    );
  }

  /**
   * Create or update a material definition
   */
  async updateMaterial(
    flatId: string,
    materialId: number,
    data: UpdateMaterialRequest
  ): Promise<MaterialDefinition> {
    return apiService.put<MaterialDefinition>(
      `/control/flats/${encodeURIComponent(flatId)}/materials/${materialId}`,
      data
    );
  }

  /**
   * Delete a material definition
   */
  async deleteMaterial(flatId: string, materialId: number): Promise<void> {
    return apiService.delete<void>(
      `/control/flats/${encodeURIComponent(flatId)}/materials/${materialId}`
    );
  }

  /**
   * Apply a preset palette to the flat
   */
  async applyPalette(flatId: string, paletteName: string): Promise<MaterialDefinition[]> {
    return apiService.post<MaterialDefinition[]>(
      `/control/flats/${encodeURIComponent(flatId)}/materials/palette`,
      { paletteName }
    );
  }
}

export const flatService = new FlatService();
