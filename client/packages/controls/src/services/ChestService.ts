/**
 * Chest Service
 * Manages chest CRUD operations
 */

import { apiService } from './ApiService';
import type { WChest, ChestType } from '@nimbus/shared/generated/entities/WChest';
import type { ItemRef } from '@nimbus/shared';

export interface ChestListParams {
  type?: ChestType;
  userId?: string;
  worldId?: string;
}

export interface ChestRequest {
  name: string;
  displayName?: string;
  description?: string;
  worldId?: string;
  userId?: string;
  type: ChestType;
  items?: ItemRef[];
}

export interface ItemRefRequest {
  itemRef: ItemRef;
}

export class ChestService {
  /**
   * Get all chests in a region with optional filters
   */
  async getChests(regionId: string, params?: ChestListParams): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/regions/${regionId}/chests`,
      params
    );
  }

  /**
   * Get user-specific chests
   */
  async getUserChests(regionId: string, userId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/regions/${regionId}/chests/user/${userId}`
    );
  }

  /**
   * Get region-related chests
   */
  async getRegionChests(regionId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/regions/${regionId}/chests/region`
    );
  }

  /**
   * Get world-related chests
   */
  async getWorldChests(regionId: string, worldId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/regions/${regionId}/chests/world/${worldId}`
    );
  }

  /**
   * Get chest by name
   */
  async getChest(regionId: string, name: string): Promise<WChest> {
    return apiService.get<WChest>(
      `/control/regions/${regionId}/chests/${name}`
    );
  }

  /**
   * Create new chest
   */
  async createChest(regionId: string, request: ChestRequest): Promise<WChest> {
    return apiService.post<WChest>(
      `/control/regions/${regionId}/chests`,
      request
    );
  }

  /**
   * Update existing chest
   */
  async updateChest(regionId: string, name: string, request: Partial<ChestRequest>): Promise<WChest> {
    return apiService.put<WChest>(
      `/control/regions/${regionId}/chests/${name}`,
      request
    );
  }

  /**
   * Delete chest
   */
  async deleteChest(regionId: string, name: string): Promise<void> {
    return apiService.delete<void>(
      `/control/regions/${regionId}/chests/${name}`
    );
  }

  /**
   * Add item reference to chest
   */
  async addItem(regionId: string, name: string, itemRef: ItemRef): Promise<WChest> {
    return apiService.post<WChest>(
      `/control/regions/${regionId}/chests/${name}/items`,
      { itemRef }
    );
  }

  /**
   * Update item amount in chest
   */
  async updateItemAmount(regionId: string, name: string, itemId: string, amount: number): Promise<WChest> {
    return apiService.patch<WChest>(
      `/control/regions/${regionId}/chests/${name}/items/${itemId}`,
      { amount }
    );
  }

  /**
   * Remove item from chest
   */
  async removeItem(regionId: string, name: string, itemId: string): Promise<WChest> {
    return apiService.delete<WChest>(
      `/control/regions/${regionId}/chests/${name}/items/${itemId}`
    );
  }
}

export const chestService = new ChestService();
