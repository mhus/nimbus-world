/**
 * Item API Service
 * Handles all HTTP requests to the item REST API
 */

import type { WItem } from '@nimbus/shared/generated/entities/WItem';
import { apiService } from '../../services/ApiService';

// Use WItem as primary type (includes metadata)
type ItemData = WItem;

export interface ItemSearchResult {
  itemId: string;
  title: string;
  texture?: string;
}

export class ItemApiService {
  private static apiService = apiService;

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
  static async createItem(item: ItemData, worldId: string, server?: Record<string, string>): Promise<void> {
    const url = `/control/worlds/${worldId}/items`;

    const body: any = { ...item };
    if (server !== undefined) {
      body.server = server;
    }
    await this.apiService.post(url, body);
  }

  /**
   * Update an existing item
   */
  static async updateItem(
    itemId: string,
    item: ItemData,
    worldId: string,
    server?: Record<string, string>,
    trading?: {
      itemTier?: string;
      rarityCategory?: string;
      basePrice?: number | null;
      materialPrice?: number | null;
      craftingCost?: number | null;
      usageBonus?: number | null;
      rarityBonus?: number | null;
    }
  ): Promise<void> {
    const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}`;

    const body: any = { ...item };
    if (server !== undefined) {
      body.server = server;
    }
    if (trading) {
      if (trading.itemTier) body.itemTier = trading.itemTier;
      if (trading.rarityCategory) body.rarityCategory = trading.rarityCategory;
      if (trading.basePrice != null) body.basePrice = trading.basePrice;
      if (trading.materialPrice != null) body.materialPrice = trading.materialPrice;
      if (trading.craftingCost != null) body.craftingCost = trading.craftingCost;
      if (trading.usageBonus != null) body.usageBonus = trading.usageBonus;
      if (trading.rarityBonus != null) body.rarityBonus = trading.rarityBonus;
    }
    await this.apiService.put(url, body);
  }

  /**
   * Duplicate an item. Returns the new WItem.
   */
  static async duplicateItem(itemId: string, worldId: string, newName: string): Promise<ItemData> {
    const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}/duplicate`;
    return this.apiService.post<ItemData>(url, { name: newName });
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
