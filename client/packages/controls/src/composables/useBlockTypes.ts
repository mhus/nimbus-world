/**
 * useBlockTypes Composable
 * Manages block type list and operations
 */

import { ref, computed } from 'vue';
import type { BlockType } from '@nimbus/shared';
import { blockTypeService } from '../services/BlockTypeService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useBlockTypes');

export function useBlockTypes(worldId: string) {
  const blockTypes = ref<BlockType[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const searchQuery = ref('');

  // Paging state
  const totalCount = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(50);

  // Computed
  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
  const hasNextPage = computed(() => currentPage.value < totalPages.value);
  const hasPreviousPage = computed(() => currentPage.value > 1);

  /**
   * Load block types with pagination
   */
  const loadBlockTypes = async (page: number = 1) => {
    loading.value = true;
    error.value = null;
    currentPage.value = page;

    try {
      const offset = (page - 1) * pageSize.value;
      const response = await blockTypeService.getBlockTypes(worldId, {
        query: searchQuery.value || undefined,
        limit: pageSize.value,
        offset,
      });

      blockTypes.value = response.blockTypes;
      totalCount.value = response.count;

      logger.info('Loaded block types', {
        count: blockTypes.value.length,
        totalCount: totalCount.value,
        page,
        worldId,
      });
    } catch (err) {
      error.value = 'Failed to load block types';
      logger.error('Failed to load block types', { worldId, page }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Search block types
   */
  const searchBlockTypes = async (query: string) => {
    console.log('[useBlockTypes] searchBlockTypes called', { query, worldId });
    searchQuery.value = query;
    currentPage.value = 1; // Reset to first page on search
    await loadBlockTypes(1);
  };

  /**
   * Go to next page
   */
  const nextPage = async () => {
    if (hasNextPage.value) {
      await loadBlockTypes(currentPage.value + 1);
    }
  };

  /**
   * Go to previous page
   */
  const previousPage = async () => {
    if (hasPreviousPage.value) {
      await loadBlockTypes(currentPage.value - 1);
    }
  };

  /**
   * Go to specific page
   */
  const goToPage = async (page: number) => {
    if (page >= 1 && page <= totalPages.value) {
      await loadBlockTypes(page);
    }
  };

  /**
   * Get single block type
   */
  const getBlockType = async (id: number | string): Promise<BlockType | null> => {
    try {
      return await blockTypeService.getBlockType(worldId, id);
    } catch (err) {
      error.value = 'Failed to load block type';
      logger.error('Failed to load block type', { worldId, id }, err as Error);
      return null;
    }
  };

  /**
   * Create block type
   */
  const createBlockType = async (blockType: Partial<BlockType>): Promise<string | null> => {
    try {
      const id = await blockTypeService.createBlockType(worldId, blockType);
      logger.info('Created block type', { worldId, id });
      await loadBlockTypes(); // Refresh list
      return id;
    } catch (err) {
      error.value = 'Failed to create block type';
      logger.error('Failed to create block type', { worldId }, err as Error);
      return null;
    }
  };

  /**
   * Update block type
   */
  const updateBlockType = async (id: number | string, blockType: Partial<BlockType>): Promise<boolean> => {
    try {
      await blockTypeService.updateBlockType(worldId, id, blockType);
      logger.info('Updated block type', { worldId, id });
      await loadBlockTypes(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to update block type';
      logger.error('Failed to update block type', { worldId, id }, err as Error);
      return false;
    }
  };

  /**
   * Delete block type
   */
  const deleteBlockType = async (id: number | string): Promise<boolean> => {
    try {
      await blockTypeService.deleteBlockType(worldId, id);
      logger.info('Deleted block type', { worldId, id });
      await loadBlockTypes(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to delete block type';
      logger.error('Failed to delete block type', { worldId, id }, err as Error);
      return false;
    }
  };

  return {
    blockTypes,
    loading,
    error,
    searchQuery,
    // Paging
    totalCount,
    currentPage,
    pageSize,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    // Methods
    loadBlockTypes,
    searchBlockTypes,
    nextPage,
    previousPage,
    goToPage,
    getBlockType,
    createBlockType,
    updateBlockType,
    deleteBlockType,
  };
}
