/**
 * Chunk Service
 * Manages chunk viewing and metadata
 */

import { apiService } from './ApiService';

export interface ChunkMetadata {
  id: string;
  worldId: string;
  chunk: string;
  storageId: string;
  compressed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ChunkListResponse {
  chunks: ChunkMetadata[];
  count: number;
  limit: number;
  offset: number;
}

export interface ChunkPagingParams {
  query?: string;
  limit?: number;
  offset?: number;
}

export interface ChunkDataResponse {
  cx: number;
  cz: number;
  size: number;
  blockCount: number;
  blocks: any[];
  heightData: number[][];
}

export class ChunkService {
  /**
   * Get all chunks or search with pagination
   */
  async getChunks(
    worldId: string,
    params?: ChunkPagingParams
  ): Promise<ChunkListResponse> {
    return apiService.get<ChunkListResponse>(
      `/control/worlds/${worldId}/chunks`,
      params
    );
  }

  /**
   * Get single chunk metadata
   */
  async getChunk(worldId: string, chunkKey: string): Promise<ChunkMetadata> {
    return apiService.get<ChunkMetadata>(
      `/control/worlds/${worldId}/chunks/${encodeURIComponent(chunkKey)}`
    );
  }

  /**
   * Get chunk data (blocks, heightData)
   */
  async getChunkData(worldId: string, chunkKey: string): Promise<ChunkDataResponse> {
    return apiService.get<ChunkDataResponse>(
      `/control/worlds/${worldId}/chunks/${encodeURIComponent(chunkKey)}/data`
    );
  }

  /**
   * Mark chunk as dirty for regeneration
   */
  async markChunkDirty(worldId: string, chunkKey: string): Promise<void> {
    return apiService.post<void>(
      `/control/worlds/${worldId}/chunks/${encodeURIComponent(chunkKey)}/dirty`,
      {}
    );
  }
}

export const chunkService = new ChunkService();
