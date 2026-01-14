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
   * Get all chests in a world with optional filters
   */
  async getChests(worldId: string, params?: ChestListParams): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/world/${worldId}/chests`,
      params
    );
  }

  /**
   * Get user-specific chests
   */
  async getUserChests(worldId: string, userId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/world/${worldId}/chests/user/${userId}`
    );
  }

  /**
   * Get region-related chests
   */
  async getRegionChests(worldId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/world/${worldId}/chests/region`
    );
  }

  /**
   * Get world-related chests
   */
  async getWorldChests(worldId: string): Promise<WChest[]> {
    return apiService.get<WChest[]>(
      `/control/world/${worldId}/chests`
    );
  }

  /**
   * Get chest by name
   */
  async getChest(worldId: string, name: string): Promise<WChest> {
    return apiService.get<WChest>(
      `/control/world/${worldId}/chests/${name}`
    );
  }

  /**
   * Create new chest
   */
  async createChest(worldId: string, request: ChestRequest): Promise<WChest> {
    return apiService.post<WChest>(
      `/control/world/${worldId}/chests`,
      request
    );
  }

  /**
   * Update existing chest
   */
  async updateChest(worldId: string, name: string, request: Partial<ChestRequest>): Promise<WChest> {
    return apiService.put<WChest>(
      `/control/world/${worldId}/chests/${name}`,
      request
    );
  }

  /**
   * Delete chest
   */
  async deleteChest(worldId: string, name: string): Promise<void> {
    return apiService.delete<void>(
      `/control/world/${worldId}/chests/${name}`
    );
  }

  /**
   * Add item reference to chest
   */
  async addItem(worldId: string, name: string, itemRef: ItemRef): Promise<WChest> {
    return apiService.post<WChest>(
      `/control/world/${worldId}/chests/${name}/items`,
      { itemRef }
    );
  }

  /**
   * Update item amount in chest
   */
  async updateItemAmount(worldId: string, name: string, itemId: string, amount: number): Promise<WChest> {
    return apiService.patch<WChest>(
      `/control/world/${worldId}/chests/${name}/items/${itemId}`,
      { amount }
    );
  }

  /**
   * Remove item from chest
   */
  async removeItem(worldId: string, name: string, itemId: string): Promise<WChest> {
    return apiService.delete<WChest>(
      `/control/world/${worldId}/chests/${name}/items/${itemId}`
    );
  }
}

export const chestService = new ChestService();
