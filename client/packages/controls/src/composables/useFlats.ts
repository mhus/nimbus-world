import { ref } from 'vue';
import { flatService, type FlatListItem } from '@/services/FlatService';

export function useFlats(worldId: string) {
  const flats = ref<FlatListItem[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load flats for the world
   */
  async function loadFlats() {
    loading.value = true;
    error.value = null;

    try {
      flats.value = await flatService.getFlats(worldId);
    } catch (e: any) {
      error.value = e.message || 'Failed to load flats';
      console.error('[useFlats] Failed to load flats:', e);
    } finally {
      loading.value = false;
    }
  }

  /**
   * Delete flat by ID
   */
  async function deleteFlat(id: string) {
    await flatService.deleteFlat(id);
    // Reload flats after deletion
    await loadFlats();
  }

  return {
    flats,
    loading,
    error,
    loadFlats,
    deleteFlat
  };
}
