/**
 * useHexGridPresets Composable
 * Manages hex grid presets from WAnything with collection="hexGridPreset"
 */

import { ref, type Ref } from 'vue';
import { anythingService } from '../services/AnythingService';
import { getLogger } from '@nimbus/shared';
import { WorldId } from '@nimbus/shared/utils/WorldId';
import {WAnything} from "@nimbus/shared/generated/entities/WAnything";

const logger = getLogger('useHexGridPresets');

export interface HexGridPreset {
  id: string;
  name: string;
  title?: string;
  description?: string;
  data: {
    icon?: string;
    splashScreen?: string;
    splashAudio?: string;
    parameters?: Record<string, string>;
  };
}

export interface UseHexGridPresetsReturn {
  presets: Ref<HexGridPreset[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadPresets: (worldId?: string) => Promise<void>;
}

export function useHexGridPresets(worldId?: string): UseHexGridPresetsReturn {
  const presets = ref<HexGridPreset[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load hex grid presets from WAnything collection
   * Loads all presets from the region (not world-specific) so all worlds can share presets
   */
  const loadPresets = async (loadWorldId?: string) => {
    const targetWorldId = loadWorldId || worldId;

    if (!targetWorldId) {
      logger.warn('No worldId provided for loading presets');
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      // Convert worldId to region collection for region-scoped presets
      const parsedWorldId = WorldId.unchecked(targetWorldId);
      const regionWorldId = parsedWorldId.toRegionCollection().toString();

      const response = await anythingService.list({
        worldId: regionWorldId,
        collection: 'hexGridPreset',
        enabledOnly: true,
        limit: 100,
      });

      presets.value = response.entities.map((entity: WAnything) => ({
        id: entity.name!,
        name: entity.name!,
        title: entity.title,
        description: entity.description,
        data: entity.data || {},
      }));

      logger.info('Loaded hex grid presets', { count: presets.value.length, regionWorldId, worldId: targetWorldId });
    } catch (err) {
      error.value = 'Failed to load hex grid presets';
      logger.error('Failed to load hex grid presets', { worldId: targetWorldId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  return {
    presets,
    loading,
    error,
    loadPresets,
  };
}
