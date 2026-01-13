/**
 * ItemType API Service
 *
 * Handles all REST API calls for ItemTypes
 */

import type { ItemType } from '@nimbus/shared';
import { apiService } from '@/services/ApiService';

/**
 * Search ItemTypes
 */
export async function searchItemTypes(query: string | undefined, worldId: string): Promise<ItemType[]> {
  const params = query ? { query } : {};
  const response = await apiService.get<{ itemTypes: ItemType[] }>(
    `/control/worlds/${worldId}/itemtypes`,
    params
  );
  return response.itemTypes;
}

/**
 * Get ItemType by ID
 */
export async function getItemType(itemTypeId: string, worldId: string): Promise<ItemType> {
  return apiService.get<ItemType>(`/control/worlds/${worldId}/itemtypes/${itemTypeId}`);
}

/**
 * Create new ItemType
 */
export async function createItemType(itemType: ItemType, worldId: string): Promise<ItemType> {
  return apiService.post<ItemType>(`/control/worlds/${worldId}/itemtypes`, itemType);
}

/**
 * Update ItemType
 */
export async function updateItemType(
  itemTypeId: string,
  updates: Partial<ItemType>,
  worldId: string
): Promise<ItemType> {
  return apiService.put<ItemType>(`/control/worlds/${worldId}/itemtypes/${itemTypeId}`, updates);
}

/**
 * Delete ItemType
 */
export async function deleteItemType(itemTypeId: string, worldId: string): Promise<void> {
  return apiService.delete<void>(`/control/worlds/${worldId}/itemtypes/${itemTypeId}`);
}
