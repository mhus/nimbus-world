import { ref, computed } from 'vue';
import { chunkService, type ChunkMetadata } from '@/services/ChunkService';

export function useChunks(worldId: string) {
  const chunks = ref<ChunkMetadata[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  // Pagination
  const currentPage = ref(1);
  const pageSize = ref(50);
  const totalCount = ref(0);
  const searchQuery = ref('');

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
  const hasNextPage = computed(() => currentPage.value < totalPages.value);
  const hasPreviousPage = computed(() => currentPage.value > 1);

  /**
   * Load chunks with current pagination and search
   */
  async function loadChunks() {
    loading.value = true;
    error.value = null;

    try {
      const offset = (currentPage.value - 1) * pageSize.value;
      const response = await chunkService.getChunks(worldId, {
        query: searchQuery.value || undefined,
        limit: pageSize.value,
        offset
      });

      chunks.value = response.chunks;
      totalCount.value = response.count;
    } catch (e: any) {
      error.value = e.message || 'Failed to load chunks';
      console.error('Failed to load chunks:', e);
    } finally {
      loading.value = false;
    }
  }

  /**
   * Search chunks
   */
  function searchChunks(query: string) {
    searchQuery.value = query;
    currentPage.value = 1;
    loadChunks();
  }

  /**
   * Go to next page
   */
  function nextPage() {
    if (hasNextPage.value) {
      currentPage.value++;
      loadChunks();
    }
  }

  /**
   * Go to previous page
   */
  function previousPage() {
    if (hasPreviousPage.value) {
      currentPage.value--;
      loadChunks();
    }
  }

  /**
   * Mark chunk as dirty
   */
  async function markChunkDirty(chunkKey: string) {
    await chunkService.markChunkDirty(worldId, chunkKey);
  }

  return {
    chunks,
    loading,
    error,
    currentPage,
    pageSize,
    totalCount,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    loadChunks,
    searchChunks,
    nextPage,
    previousPage,
    markChunkDirty
  };
}
