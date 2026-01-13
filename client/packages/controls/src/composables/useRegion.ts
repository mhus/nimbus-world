/**
 * useRegion Composable
 * Manages region selection state
 */

import { ref, computed } from 'vue';
import { regionService } from '../region/services/RegionService';
import type { Region } from '../region/services/RegionService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useRegion');

const REGION_STORAGE_KEY = 'nimbus.selectedRegionId';

// Read regionId from URL query parameter
const getRegionIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('regionId') || params.get('region');
};

// Read regionId from Local Storage
const getRegionIdFromStorage = (): string | null => {
  try {
    return localStorage.getItem(REGION_STORAGE_KEY);
  } catch (err) {
    logger.warn('Failed to read from localStorage', { error: err });
    return null;
  }
};

// Save regionId to Local Storage
const saveRegionIdToStorage = (regionId: string): void => {
  try {
    localStorage.setItem(REGION_STORAGE_KEY, regionId);
    logger.info('Saved regionId to localStorage', { regionId, key: REGION_STORAGE_KEY });
  } catch (err) {
    logger.warn('Failed to write to localStorage', { error: err });
  }
};

// Shared state across all instances
// Initialize from URL parameter only - Local Storage is checked after regions are loaded
const currentRegionId = ref<string | null>(getRegionIdFromUrl());
const regions = ref<Region[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

export function useRegion() {
  /**
   * Current region object
   */
  const currentRegion = computed(() => {
    return regions.value.find(r => r.name === currentRegionId.value);
  });

  /**
   * Load all regions from API
   */
  const loadRegions = async () => {
    loading.value = true;
    error.value = null;

    try {
      // 1. Load all regions from API
      regions.value = await regionService.listRegions();
      logger.info('Loaded regions', { count: regions.value.length });

      // 2. If no region selected yet, try to restore from Local Storage
      if (!currentRegionId.value) {
        const storedId = getRegionIdFromStorage();
        if (storedId) {
          currentRegionId.value = storedId;
          logger.info('Restored regionId from localStorage', { regionId: storedId });
        }
      }

      // 3. Validate selected region exists in loaded list
      if (currentRegionId.value) {
        const foundRegion = regions.value.find(r => r.name === currentRegionId.value);

        if (foundRegion) {
          logger.info('Selected region is valid', { regionId: currentRegionId.value });
        } else {
          logger.warn('Selected region not found in loaded list', {
            selectedId: currentRegionId.value,
            availableIds: regions.value.map(r => r.name)
          });

          // Fallback to first enabled region
          const firstEnabled = regions.value.find(r => r.enabled);
          if (firstEnabled) {
            currentRegionId.value = firstEnabled.name;
            saveRegionIdToStorage(currentRegionId.value);
            logger.info('Fallback to first enabled region', { regionId: currentRegionId.value });
          } else if (regions.value.length > 0) {
            currentRegionId.value = regions.value[0].name;
            saveRegionIdToStorage(currentRegionId.value);
            logger.info('Fallback to first region', { regionId: currentRegionId.value });
          }
        }
      } else if (regions.value.length > 0) {
        // No region selected and nothing in storage - use first enabled region
        const firstEnabled = regions.value.find(r => r.enabled);
        if (firstEnabled) {
          currentRegionId.value = firstEnabled.name;
          saveRegionIdToStorage(currentRegionId.value);
          logger.info('No region selected, using first enabled region', { regionId: currentRegionId.value });
        } else {
          currentRegionId.value = regions.value[0].name;
          saveRegionIdToStorage(currentRegionId.value);
          logger.info('No region selected, using first region', { regionId: currentRegionId.value });
        }
      }
    } catch (err) {
      error.value = 'Failed to load regions';
      logger.error('Failed to load regions', {}, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Select a region and save to Local Storage
   */
  const selectRegion = (regionName: string) => {
    if (!regions.value.find(r => r.name === regionName)) {
      logger.warn('Cannot select non-existent region', { regionName });
      return;
    }

    currentRegionId.value = regionName;
    saveRegionIdToStorage(regionName);
    logger.info('Selected region', { regionName });
  };

  return {
    currentRegion,
    currentRegionId,
    regions,
    loading,
    error,
    loadRegions,
    selectRegion,
  };
}
