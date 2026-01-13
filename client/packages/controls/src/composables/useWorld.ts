/**
 * useWorld Composable
 * Manages world selection state
 */

import { ref, computed, type Ref, type ComputedRef } from 'vue';
import { worldService, type WorldFilter } from '../services/WorldService';
import { getLogger, type WorldInfo } from '@nimbus/shared';

const logger = getLogger('useWorld');

export interface UseWorldReturn {
  currentWorld: ComputedRef<WorldInfo | undefined>;
  currentWorldId: Ref<string>;
  worlds: Ref<WorldInfo[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadWorlds: (filter?: WorldFilter) => Promise<void>;
  selectWorld: (worldId: string) => void;
}

const WORLD_STORAGE_KEY = 'nimbus.selectedWorldId';

// Read worldId from URL query parameter
const getWorldIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('world') || params.get('worldId');
};

// Read worldId from Local Storage
const getWorldIdFromStorage = (): string | null => {
  try {
    return localStorage.getItem(WORLD_STORAGE_KEY);
  } catch (err) {
    logger.warn('Failed to read from localStorage', { error: err });
    return null;
  }
};

// Save worldId to Local Storage
const saveWorldIdToStorage = (worldId: string): void => {
  try {
    localStorage.setItem(WORLD_STORAGE_KEY, worldId);
    logger.info('Saved world/collection to localStorage', { worldId, key: WORLD_STORAGE_KEY });
  } catch (err) {
    logger.warn('Failed to write to localStorage', { error: err });
  }
};

// Shared state across all instances
// Initialize from URL parameter only - Local Storage is checked after worlds are loaded
const currentWorldId = ref<string>(
  getWorldIdFromUrl() || ''
);
const worlds = ref<WorldInfo[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

export function useWorld(): UseWorldReturn {
  /**
   * Current world object
   */
  const currentWorld = computed((): WorldInfo | undefined => {
    return worlds.value.find(w => w.worldId === currentWorldId.value);
  });

  /**
   * Load all worlds from API with optional filter
   * @param filter Optional filter type for world selection
   */
  const loadWorlds = async (filter?: WorldFilter) => {
    loading.value = true;
    error.value = null;

    try {
      // 1. Load all worlds from API
      worlds.value = await worldService.getWorlds(filter);
      logger.info('Loaded worlds', { count: worlds.value.length, filter });

      // 2. If no world selected yet, try to restore from Local Storage
      if (!currentWorldId.value) {
        const storedId = getWorldIdFromStorage();
        if (storedId) {
          currentWorldId.value = storedId;
          logger.info('Restored worldId from localStorage', { worldId: storedId });
        }
      }

      // 3. Validate selected world exists in loaded list
      if (currentWorldId.value) {
        const foundWorld = worlds.value.find(w => w.worldId === currentWorldId.value);

        if (foundWorld) {
          logger.info('Selected world is valid', { worldId: currentWorldId.value });
        } else {
          logger.warn('Selected world not found in loaded list', {
            selectedId: currentWorldId.value,
            availableIds: worlds.value.map(w => w.worldId)
          });

          // Fallback to first world
          if (worlds.value.length > 0) {
            currentWorldId.value = worlds.value[0].worldId;
            saveWorldIdToStorage(currentWorldId.value);
            logger.info('Fallback to first world', { worldId: currentWorldId.value });
          }
        }
      } else if (worlds.value.length > 0) {
        // No world selected and nothing in storage - use first world
        currentWorldId.value = worlds.value[0].worldId;
        saveWorldIdToStorage(currentWorldId.value);
        logger.info('No world selected, using first world', { worldId: currentWorldId.value });
      }
    } catch (err) {
      error.value = 'Failed to load worlds';
      logger.error('Failed to load worlds', {}, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Select a world and save to Local Storage
   */
  const selectWorld = (worldId: string) => {
    if (!worlds.value.find(w => w.worldId === worldId)) {
      logger.warn('Cannot select non-existent world', { worldId });
      return;
    }

    currentWorldId.value = worldId;
    saveWorldIdToStorage(worldId);
    logger.info('Selected world', { worldId });
  };

  return {
    currentWorld,
    currentWorldId,
    worlds,
    loading,
    error,
    loadWorlds,
    selectWorld,
  };
}
