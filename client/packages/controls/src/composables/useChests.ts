/**
 * useChests Composable
 * Manages chest list and operations
 */

import { ref } from 'vue';
import { chestService, type ChestRequest } from '../services/ChestService';
import type { WChest, ChestType } from '@nimbus/shared/generated/entities/WChest';
import type { ItemRef } from '@nimbus/shared';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useChests');

export function useChests(regionId: string) {
  const chests = ref<WChest[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Filters
  const typeFilter = ref<ChestType | undefined>(undefined);
  const userIdFilter = ref<string | undefined>(undefined);
  const worldIdFilter = ref<string | undefined>(undefined);

  /**
   * Load chests with optional filters
   */
  const loadChests = async () => {
    loading.value = true;
    error.value = null;

    try {
      chests.value = await chestService.getChests(regionId, {
        type: typeFilter.value,
        userId: userIdFilter.value,
        worldId: worldIdFilter.value,
      });

      logger.info('Loaded chests', {
        count: chests.value.length,
        regionId,
        filters: {
          type: typeFilter.value,
          userId: userIdFilter.value,
          worldId: worldIdFilter.value,
        },
      });
    } catch (err) {
      error.value = 'Failed to load chests';
      logger.error('Failed to load chests', { regionId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load user-specific chests
   */
  const loadUserChests = async (userId: string) => {
    loading.value = true;
    error.value = null;

    try {
      chests.value = await chestService.getUserChests(regionId, userId);
      logger.info('Loaded user chests', { count: chests.value.length, regionId, userId });
    } catch (err) {
      error.value = 'Failed to load user chests';
      logger.error('Failed to load user chests', { regionId, userId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load region-related chests
   */
  const loadRegionChests = async () => {
    loading.value = true;
    error.value = null;

    try {
      chests.value = await chestService.getRegionChests(regionId);
      logger.info('Loaded region chests', { count: chests.value.length, regionId });
    } catch (err) {
      error.value = 'Failed to load region chests';
      logger.error('Failed to load region chests', { regionId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load world-related chests
   */
  const loadWorldChests = async (worldId: string) => {
    loading.value = true;
    error.value = null;

    try {
      chests.value = await chestService.getWorldChests(regionId, worldId);
      logger.info('Loaded world chests', { count: chests.value.length, regionId, worldId });
    } catch (err) {
      error.value = 'Failed to load world chests';
      logger.error('Failed to load world chests', { regionId, worldId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Create new chest
   */
  const createChest = async (request: ChestRequest): Promise<WChest | null> => {
    error.value = null;

    try {
      const chest = await chestService.createChest(regionId, request);
      logger.info('Created chest', { regionId, name: chest.name });
      await loadChests(); // Refresh list
      return chest;
    } catch (err) {
      error.value = 'Failed to create chest';
      logger.error('Failed to create chest', { regionId, name: request.name }, err as Error);
      return null;
    }
  };

  /**
   * Update existing chest
   */
  const updateChest = async (name: string, request: Partial<ChestRequest>): Promise<WChest | null> => {
    error.value = null;

    try {
      const chest = await chestService.updateChest(regionId, name, request);
      logger.info('Updated chest', { regionId, name });
      await loadChests(); // Refresh list
      return chest;
    } catch (err) {
      error.value = 'Failed to update chest';
      logger.error('Failed to update chest', { regionId, name }, err as Error);
      return null;
    }
  };

  /**
   * Delete chest
   */
  const deleteChest = async (name: string): Promise<boolean> => {
    try {
      await chestService.deleteChest(regionId, name);
      logger.info('Deleted chest', { regionId, name });
      await loadChests(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to delete chest';
      logger.error('Failed to delete chest', { regionId, name }, err as Error);
      return false;
    }
  };

  /**
   * Add item reference to chest
   */
  const addItem = async (chestName: string, itemRef: ItemRef): Promise<boolean> => {
    error.value = null;

    try {
      await chestService.addItem(regionId, chestName, itemRef);
      logger.info('Added item to chest', { regionId, chestName, itemId: itemRef.itemId });
      await loadChests(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to add item to chest';
      logger.error('Failed to add item to chest', { regionId, chestName, itemId: itemRef.itemId }, err as Error);
      return false;
    }
  };

  /**
   * Remove item from chest
   */
  const removeItem = async (chestName: string, itemId: string): Promise<boolean> => {
    error.value = null;

    try {
      await chestService.removeItem(regionId, chestName, itemId);
      logger.info('Removed item from chest', { regionId, chestName, itemId });
      await loadChests(); // Refresh list
      return true;
    } catch (err) {
      error.value = 'Failed to remove item from chest';
      logger.error('Failed to remove item from chest', { regionId, chestName, itemId }, err as Error);
      return false;
    }
  };

  /**
   * Set filters and reload
   */
  const setFilters = async (type?: ChestType, userId?: string, worldId?: string) => {
    typeFilter.value = type;
    userIdFilter.value = userId;
    worldIdFilter.value = worldId;
    await loadChests();
  };

  return {
    chests,
    loading,
    error,
    typeFilter,
    userIdFilter,
    worldIdFilter,
    // Methods
    loadChests,
    loadUserChests,
    loadRegionChests,
    loadWorldChests,
    createChest,
    updateChest,
    deleteChest,
    addItem,
    removeItem,
    setFilters,
  };
}
