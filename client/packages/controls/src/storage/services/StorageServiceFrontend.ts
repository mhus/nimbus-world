/**
 * StorageServiceFrontend - Frontend service for storage management
 */

import { apiService } from '../../services/ApiService';

/**
 * Storage entry (grouped by UUID)
 */
export interface StorageEntry {
  uuid: string;
  schema: string;
  schemaVersion: string;
  worldId: string;
  path: string;
  size: number;
  createdAt: string;
}

/**
 * Paginated storage list response
 */
export interface StorageListResponse {
  items: StorageEntry[];
  count: number;
  offset: number;
  limit: number;
}

/**
 * Storage info response
 */
export interface StorageInfo {
  id: string;
  size: number;
  createdAt: string;
  worldId: string;
  path: string;
  schema: string;
  schemaVersion: {
    major: number;
    minor: number;
  };
}

/**
 * Frontend service for storage data operations
 */
class StorageServiceFrontend {
  /**
   * List storage entries with pagination and search
   *
   * @param query Search query (optional)
   * @param offset Pagination offset (default: 0)
   * @param limit Pagination limit (default: 50)
   * @returns Paginated list of storage entries
   */
  async listStorage(query?: string, offset: number = 0, limit: number = 50): Promise<StorageListResponse> {
    const params: any = { offset, limit };
    if (query) {
      params.query = query;
    }

    return apiService.get<StorageListResponse>('/shared/storage/list', params);
  }

  /**
   * Get storage entry info by UUID
   *
   * @param uuid Storage UUID
   * @returns Storage info
   */
  async getInfo(uuid: string): Promise<StorageInfo> {
    return apiService.get<StorageInfo>(`/shared/storage/info/${uuid}`);
  }

  /**
   * Get download URL for storage content
   *
   * @param uuid Storage UUID
   * @returns Download URL
   */
  getContentUrl(uuid: string): string {
    return `${apiService.getBaseUrl()}/shared/storage/content/${uuid}`;
  }

  /**
   * Download storage content (opens in new tab)
   *
   * @param uuid Storage UUID
   */
  downloadContent(uuid: string): void {
    const url = this.getContentUrl(uuid);
    window.open(url, '_blank');
  }
}

export const storageServiceFrontend = new StorageServiceFrontend();
