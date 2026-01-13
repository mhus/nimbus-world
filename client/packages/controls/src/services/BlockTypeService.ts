/**
 * BlockType Service
 * Manages block type CRUD operations
 */

import type { BlockType } from '@nimbus/shared';
import { apiService } from './ApiService';

export interface BlockTypeListResponse {
  blockTypes: BlockType[];
  count: number;
  limit: number;
  offset: number;
}

export interface BlockTypeCreateResponse {
  blockId: string;
}

export interface BlockTypePagingParams {
  query?: string;
  limit?: number;
  offset?: number;
}

export class BlockTypeService {
  /**
   * Get all block types or search with pagination
   */
  async getBlockTypes(
    worldId: string,
    params?: BlockTypePagingParams
  ): Promise<BlockTypeListResponse> {
    console.log('[BlockTypeService] getBlockTypes called', { worldId, params });
    console.log('[BlockTypeService] Request URL:', `/control/worlds/${worldId}/blocktypes`);

    const response = await apiService.get<BlockTypeListResponse>(
      `/control/worlds/${worldId}/blocktypes`,
      params
    );
    console.log('[BlockTypeService] API response:', response);
    console.log('[BlockTypeService] Returning:', {
      count: response.count,
      blockTypes: response.blockTypes?.length ?? 0,
      limit: response.limit,
      offset: response.offset
    });
    return response;
  }

  /**
   * Get single block type by ID
   */
  async getBlockType(worldId: string, id: number | string): Promise<BlockType> {
    return apiService.get<BlockType>(`/control/worlds/${worldId}/blocktypes/type/${id}`);
  }

  /**
   * Create new block type
   */
  async createBlockType(worldId: string, blockType: Partial<BlockType>): Promise<string> {
    // Map to CreateBlockTypeRequest format expected by backend
    const request = {
      blockId: blockType.id,
      publicData: blockType,
      blockTypeGroup: undefined  // Optional, will be extracted from blockId on server
    };

    const response = await apiService.post<BlockTypeCreateResponse>(
      `/control/worlds/${worldId}/blocktypes/type`,
      request
    );
    return response.blockId;
  }

  /**
   * Update existing block type
   */
  async updateBlockType(worldId: string, id: number | string, blockType: Partial<BlockType>): Promise<BlockType> {
    // Server expects UpdateBlockTypeRequest format with publicData wrapper
    const updateRequest = {
      publicData: blockType
    };

    return apiService.put<BlockType>(
      `/control/worlds/${worldId}/blocktypes/type/${id}`,
      updateRequest
    );
  }

  /**
   * Delete block type
   */
  async deleteBlockType(worldId: string, id: number | string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/blocktypes/type/${id}`);
  }
}

export const blockTypeService = new BlockTypeService();
