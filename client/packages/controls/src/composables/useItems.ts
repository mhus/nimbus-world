/**
 * useItems Composable
 * Manages item list loading from EItemController
 */

import { ref } from 'vue';
import type { WItem } from '@nimbus/shared/generated/entities/WItem';
import { apiService } from '../services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useItems');

export interface ItemSearchResult {
  itemId: string;
  name: string;
  texture?: string;
}

export interface UseItemsReturn {
  items: ReturnType<typeof ref<WItem[]>>;
  searchResults: ReturnType<typeof ref<ItemSearchResult[]>>;
  loading: ReturnType<typeof ref<boolean>>;
  error: ReturnType<typeof ref<string | null>>;
  searchItems: (query?: string) => Promise<void>;
  loadItem: (itemId: string) => Promise<WItem | null>;
  loadItems: (itemIds: string[]) => Promise<WItem[]>;
}

export function useItems(worldId: string): UseItemsReturn {
  const items = ref<WItem[]>([]);
  const searchResults = ref<ItemSearchResult[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Search items (returns compact list)
   */
  const searchItems = async (query: string = ''): Promise<void> => {
    loading.value = true;
    error.value = null;

    try {
      const queryParam = query ? `?query=${encodeURIComponent(query)}` : '';
      const url = `/control/worlds/${worldId}/items${queryParam}`;

      const response = await apiService.get<{ items: ItemSearchResult[] }>(url);
      searchResults.value = response.items || [];

      logger.info('Searched items', { worldId, query, count: searchResults.value.length });
    } catch (err) {
      error.value = 'Failed to search items';
      logger.error('Failed to search items', { worldId, query }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load full item data by ID
   */
  const loadItem = async (itemId: string): Promise<WItem | null> => {
    try {
      const url = `/control/worlds/${worldId}/item/${encodeURIComponent(itemId)}`;
      const item = await apiService.get<WItem>(url);
      logger.info('Loaded item', { worldId, itemId });
      return item;
    } catch (err: any) {
      if (err.response?.status === 404) {
        logger.warn('Item not found', { worldId, itemId });
        return null;
      }
      error.value = 'Failed to load item';
      logger.error('Failed to load item', { worldId, itemId }, err as Error);
      return null;
    }
  };

  /**
   * Load multiple items by IDs
   */
  const loadItems = async (itemIds: string[]): Promise<WItem[]> => {
    loading.value = true;
    error.value = null;

    try {
      const loadedItems = await Promise.all(
        itemIds.map(id => loadItem(id))
      );

      items.value = loadedItems.filter(item => item !== null) as WItem[];
      logger.info('Loaded items', { worldId, count: items.value.length });
      return items.value;
    } catch (err) {
      error.value = 'Failed to load items';
      logger.error('Failed to load items', { worldId }, err as Error);
      return [];
    } finally {
      loading.value = false;
    }
  };

  return {
    items,
    searchResults,
    loading,
    error,
    searchItems,
    loadItem,
    loadItems,
  };
}
