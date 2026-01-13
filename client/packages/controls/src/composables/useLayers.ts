/**
 * useLayers Composable
 * Manages layer list and operations
 */

import { ref, computed } from 'vue';
import type { LayerDto, CreateLayerRequest, UpdateLayerRequest } from '@nimbus/shared';
import { layerService } from '../services/LayerService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useLayers');

export function useLayers(worldId: string) {
  const layers = ref<LayerDto[]>([]);
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
   * Load layers with pagination
   */
  const loadLayers = async (page: number = 1) => {
    loading.value = true;
    error.value = null;
    currentPage.value = page;

    try {
      const offset = (page - 1) * pageSize.value;
      const response = await layerService.getLayers(worldId, {
        query: searchQuery.value || undefined,
        limit: pageSize.value,
        offset,
      });

      layers.value = response.layers;
      totalCount.value = response.count;

      logger.info('Loaded layers', {
        count: layers.value.length,
        totalCount: totalCount.value,
        page,
        worldId,
      });
    } catch (err) {
      error.value = 'Failed to load layers';
      logger.error('Failed to load layers', { worldId, page }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Search layers
   */
  const searchLayers = async (query: string) => {
    console.log('[useLayers] searchLayers called', { query, worldId });
    searchQuery.value = query;
    currentPage.value = 1; // Reset to first page on search
    await loadLayers(1);
  };

  /**
   * Go to next page
   */
  const nextPage = async () => {
    if (hasNextPage.value) {
      await loadLayers(currentPage.value + 1);
    }
  };

  /**
   * Go to previous page
   */
  const previousPage = async () => {
    if (hasPreviousPage.value) {
      await loadLayers(currentPage.value - 1);
    }
  };

  /**
   * Go to specific page
   */
  const goToPage = async (page: number) => {
    if (page >= 1 && page <= totalPages.value) {
      await loadLayers(page);
    }
  };

  /**
   * Get single layer
   */
  const getLayer = async (id: string): Promise<LayerDto | null> => {
    try {
      return await layerService.getLayer(worldId, id);
    } catch (err) {
      error.value = 'Failed to load layer';
      logger.error('Failed to load layer', { worldId, id }, err as Error);
      return null;
    }
  };

  /**
   * Create layer
   */
  const createLayer = async (layer: CreateLayerRequest): Promise<string | null> => {
    try {
      const id = await layerService.createLayer(worldId, layer);
      logger.info('Created layer', { worldId, id });
      await loadLayers(); // Refresh list
      return id;
    } catch (err) {
      error.value = 'Failed to create layer';
      logger.error('Failed to create layer', { worldId }, err as Error);
      return null;
    }
  };

  /**
   * Update layer
   */
  const updateLayer = async (id: string, layer: UpdateLayerRequest): Promise<boolean> => {
    try {
      await layerService.updateLayer(worldId, id, layer);
      logger.info('Updated layer', { worldId, id });
      await loadLayers(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to update layer';
      logger.error('Failed to update layer', { worldId, id }, err as Error);
      return false;
    }
  };

  /**
   * Delete layer
   */
  const deleteLayer = async (id: string): Promise<boolean> => {
    try {
      await layerService.deleteLayer(worldId, id);
      logger.info('Deleted layer', { worldId, id });
      await loadLayers(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to delete layer';
      logger.error('Failed to delete layer', { worldId, id }, err as Error);
      return false;
    }
  };

  return {
    layers,
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
    loadLayers,
    searchLayers,
    nextPage,
    previousPage,
    goToPage,
    getLayer,
    createLayer,
    updateLayer,
    deleteLayer,
  };
}
