/**
 * Item API Service
 * Handles all HTTP requests to the item REST API
 */

import type { WItem } from '@nimbus/shared/generated/entities/WItem';
import { ApiService } from '../../services/ApiService';

// Use WItem as primary type (includes metadata)
type ItemData = WItem;

export interface ItemSearchResult {
  itemId: string;
  name: string;
  texture?: string;
}

export class ItemApiService {
  private static apiService = new ApiService();

  /**
   * Search for items
   */
  static async searchItems(query: string = '', worldId: string): Promise<ItemSearchResult[]> {
    const queryParam = query ? `?query=${encodeURIComponent(query)}` : '';
    const url = `/control/worlds/${worldId}/items${queryParam}`;

    const response = await this.apiService.get<{ items: ItemSearchResult[] }>(url);
    return response.items || [];
  }

  /**
   * Get item data by ID
   */
  static async getItem(itemId: string, worldId: string): Promise<ItemData | null> {
    const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}`;

    try {
      return await this.apiService.get<ItemData>(url);
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * Create a new item
   */
  static async createItem(item: ItemData, worldId: string): Promise<void> {
    const url = `/control/worlds/${worldId}/items`;

    await this.apiService.post(url, item);
  }

  /**
   * Update an existing item
   */
  static async updateItem(itemId: string, item: ItemData, worldId: string): Promise<void> {
    const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}`;

    await this.apiService.put(url, item);
  }

  /**
   * Delete an item
   */
  static async deleteItem(itemId: string, worldId: string): Promise<void> {
    const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}`;

    try {
      await this.apiService.delete(url);
    } catch (error: any) {
      // Ignore 404 errors
      if (error.response?.status !== 404) {
        throw error;
      }
    }
  }
}
