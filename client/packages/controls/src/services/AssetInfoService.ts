/**
 * Asset Info Service
 * Manages asset .info file operations (metadata for assets)
 */

import { apiService } from './ApiService';

/**
 * Asset info metadata (key-value pairs)
 * description is always required
 */
export interface AssetInfo {
  description: string;
  source?: string;
  author?: string;
  license?: string;
  licenseFixed?: boolean;
  [key: string]: string | number | boolean | undefined;
}

export class AssetInfoService {
  /**
   * Get asset info file
   * Returns empty object with description if .info file doesn't exist
   */
  async getAssetInfo(worldId: string, assetPath: string): Promise<AssetInfo> {
    try {
      return await apiService.get<AssetInfo>(
        `/control/worlds/${worldId}/assetinfo/${assetPath}`
      );
    } catch (error) {
      // If .info file doesn't exist, return empty object
      return { description: '' };
    }
  }

  /**
   * Save asset info file (create or update)
   */
  async saveAssetInfo(worldId: string, assetPath: string, info: AssetInfo): Promise<void> {
    if (!info.description || typeof info.description !== 'string') {
      throw new Error('description field is required');
    }

    await apiService.put<void>(
      `/control/worlds/${worldId}/assetinfo/${assetPath}`,
      info
    );
  }

  /**
   * Delete asset info file
   * Note: The assetinfo endpoint does not support DELETE (metadata only).
   * To delete asset info, delete the asset itself via AssetService.
   */
  async deleteAssetInfo(_worldId: string, _assetPath: string): Promise<void> {
    throw new Error('Delete asset info not supported. Delete the asset via AssetService instead.');
  }

  /**
   * Check if info has any custom fields besides description
   */
  hasCustomFields(info: AssetInfo): boolean {
    const keys = Object.keys(info);
    return keys.length > 1 || (keys.length === 1 && keys[0] !== 'description');
  }
}

export const assetInfoService = new AssetInfoService();
