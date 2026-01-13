/**
 * useJobPresets Composable
 * Manages job presets from WAnything with collection="jobPreset"
 */

import { ref, type Ref } from 'vue';
import { anythingService } from '../services/AnythingService';
import { getLogger } from '@nimbus/shared';
import {WAnything} from "@nimbus/shared/generated/entities/WAnything";

const logger = getLogger('useJobPresets');

export interface JobPreset {
  id: string;
  name: string;
  title?: string;
  description?: string;
  data: any;
}

export interface UseJobPresetsReturn {
  presets: Ref<JobPreset[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadPresets: (worldId?: string) => Promise<void>;
}

export function useJobPresets(worldId?: string): UseJobPresetsReturn {
  const presets = ref<JobPreset[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load job presets from WAnything collection
   */
  const loadPresets = async (loadWorldId?: string) => {
    const targetWorldId = loadWorldId || worldId;

    loading.value = true;
    error.value = null;

    try {
      const response = await anythingService.list({
        collection: 'jobPreset',
        worldId: targetWorldId,
        enabledOnly: true,
        limit: 100,
      });

      presets.value = response.entities.map((entity: WAnything) => ({
        id: entity.name!,
        name: entity.name!,
        title: entity.title,
        description: entity.description,
        data: entity.data,
      }));

      logger.info('Loaded job presets', { count: presets.value.length, worldId: targetWorldId });
    } catch (err) {
      error.value = 'Failed to load job presets';
      logger.error('Failed to load job presets', { worldId: targetWorldId }, err as Error);
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
