/**
 * Storage Service
 * Manages storage information and content
 */

import { apiService } from './ApiService';

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

export class StorageService {
  /**
   * Get storage info by ID
   */
  async getInfo(storageId: string): Promise<StorageInfo> {
    return apiService.get<StorageInfo>(`/shared/storage/info/${storageId}`);
  }

  /**
   * Get storage content URL
   */
  getContentUrl(storageId: string): string {
    return `/shared/storage/content/${storageId}`;
  }
}

export const storageService = new StorageService();
