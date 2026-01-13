/**
 * Asset Service
 * Manages asset CRUD operations (textures, models, etc.)
 */

import { apiService } from './ApiService';
import type { FolderInfo } from '@nimbus/shared/generated/entities/FolderInfo';

export interface Asset {
  path: string;
  name?: string;
  size: number;
  mimeType: string;
  lastModified: string;
  extension: string;
  category: string;
}

export interface AssetListResponse {
  assets: Asset[];
  count: number;
  limit: number;
  offset: number;
}

export interface AssetPagingParams {
  query?: string;
  limit?: number;
  offset?: number;
  /** Filter by file extensions (comma-separated, e.g., "ogg,wav,mp3") */
  ext?: string;
}

export class AssetService {
  /**
   * Get all assets or search with pagination
   */
  async getAssets(worldId: string, params?: AssetPagingParams): Promise<AssetListResponse> {
    const response = await apiService.get<AssetListResponse>(
      `/control/worlds/${worldId}/assets`,
      params
    );
    return response;
  }

  /**
   * Upload new asset
   */
  async uploadAsset(worldId: string, assetPath: string, file: File): Promise<Asset> {
    const arrayBuffer = await file.arrayBuffer();
    return apiService.uploadBinary<Asset>(
      `/control/worlds/${worldId}/assets/${assetPath}`,
      arrayBuffer,
      file.type
    );
  }

  /**
   * Update existing asset
   */
  async updateAsset(worldId: string, assetPath: string, file: File): Promise<Asset> {
    const arrayBuffer = await file.arrayBuffer();
    return apiService.updateBinary<Asset>(
      `/control/worlds/${worldId}/assets/${assetPath}`,
      arrayBuffer,
      file.type
    );
  }

  /**
   * Delete asset
   */
  async deleteAsset(worldId: string, assetPath: string): Promise<void> {
    return apiService.delete<void>(`/control/worlds/${worldId}/assets/${assetPath}`);
  }

  /**
   * Duplicate asset to new path (copy within same world)
   */
  async duplicateAsset(worldId: string, sourcePath: string, newPath: string): Promise<void> {
    return apiService.patch(`/control/worlds/${worldId}/assets/duplicate`, {
      sourcePath,
      newPath,
    });
  }

  /**
   * Copy asset from source world to target world (cross-world copy)
   * Preserves all metadata (publicData)
   */
  async copyAssetFromWorld(targetWorldId: string, sourceWorldId: string, sourcePath: string, newPath: string): Promise<void> {
    return apiService.post(`/control/worlds/${targetWorldId}/assets/copy-from`, {
      sourceWorldId,
      sourcePath,
      newPath,
    });
  }

  /**
   * Get asset download URL
   */
  getAssetUrl(worldId: string, assetPath: string): string {
    return `${apiService.getBaseUrl()}/control/worlds/${worldId}/assets/${assetPath}`;
  }

  /**
   * Check if asset is an image (for preview)
   */
  isImageAsset(asset: Asset): boolean {
    const imageExtensions = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'];
    return imageExtensions.includes(asset.extension.toLowerCase());
  }

  /**
   * Check if asset is an audio file (for preview)
   */
  isAudioAsset(asset: Asset): boolean {
    const audioExtensions = ['.mp3', '.wav', '.ogg'];
    return audioExtensions.includes(asset.extension.toLowerCase());
  }

  /**
   * Get icon for asset type
   */
  getAssetIcon(asset: Asset): string {
    const ext = asset.extension.toLowerCase();

    // Image files
    if (this.isImageAsset(asset)) {
      return '🖼️';
    }

    // 3D models
    if (['.obj', '.mtl', '.fbx', '.gltf', '.glb'].includes(ext)) {
      return '🎨';
    }

    // Audio files
    if (this.isAudioAsset({ extension: ext } as Asset)) {
      return '🔊';
    }

    // JSON files
    if (ext === '.json') {
      return '📄';
    }

    // Default
    return '📦';
  }

  /**
   * Get folders for a world
   * Returns virtual folders derived from asset paths
   */
  async getFolders(worldId: string, parent?: string): Promise<FolderListResponse> {
    const params: Record<string, string> = {};
    if (parent) {
      params.parent = parent;
    }

    const response = await apiService.get<FolderListResponse>(
      `/control/worlds/${worldId}/assets/folders`,
      params
    );
    return response;
  }

  /**
   * Move/rename a folder (bulk path update)
   * Note: This endpoint may not be implemented yet (Phase 2 - optional)
   */
  async moveFolder(worldId: string, oldPath: string, newPath: string): Promise<void> {
    return apiService.patch(`/control/worlds/${worldId}/assets/folders/move`, {
      oldPath,
      newPath,
    });
  }
}

export interface FolderListResponse {
  folders: FolderInfo[];
  count: number;
  parent: string;
}

export const assetService = new AssetService();
