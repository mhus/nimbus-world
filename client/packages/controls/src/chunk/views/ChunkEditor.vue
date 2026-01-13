<template>
  <div class="space-y-4">
    <!-- Check if world is selected -->
    <div v-if="!currentWorldId" class="alert alert-info">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Please select a world to view chunks.</span>
    </div>

    <!-- Chunk Editor Content (only shown when world is selected) -->
    <template v-else>
      <!-- Header with Search -->
      <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
        <div class="flex-1">
          <SearchInput
            v-model="searchQuery"
            placeholder="Search chunks by key (e.g., 0:0)..."
            @search="handleSearch"
          />
        </div>
      </div>

      <!-- Loading State -->
      <LoadingSpinner v-if="loading && chunks.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && chunks.length === 0" class="text-center py-12">
        <p class="text-base-content/70 text-lg">No chunks found</p>
      </div>

      <!-- Chunk List -->
      <ChunkList
        v-else
        :chunks="chunks"
        :loading="loading"
        @view="handleViewChunk"
        @mark-dirty="handleMarkDirty"
      />

      <!-- Pagination Controls -->
      <div v-if="!loading && chunks.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} chunks
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-sm"
            :disabled="!hasPreviousPage"
            @click="handlePreviousPage"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
            Previous
          </button>
          <div class="flex items-center gap-2 px-4">
            <span class="text-sm">Page {{ currentPage }} of {{ totalPages }}</span>
          </div>
          <button
            class="btn btn-sm"
            :disabled="!hasNextPage"
            @click="handleNextPage"
          >
            Next
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>

      <!-- Chunk Detail Modal -->
      <ChunkDetailModal
        v-if="selectedChunk"
        :world-id="currentWorldId!"
        :chunk-key="selectedChunk"
        @close="closeChunkDetail"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useChunks } from '@/composables/useChunks';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import ChunkList from '../components/ChunkList.vue';
import ChunkDetailModal from '../components/ChunkDetailModal.vue';

const { currentWorldId, loadWorlds } = useWorld();

const chunksComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useChunks(currentWorldId.value);
});

const chunks = computed(() => chunksComposable.value?.chunks.value || []);
const loading = computed(() => chunksComposable.value?.loading.value || false);
const error = computed(() => chunksComposable.value?.error.value || null);
const searchQuery = ref('');

// Paging
const totalCount = computed(() => chunksComposable.value?.totalCount.value || 0);
const currentPage = computed(() => chunksComposable.value?.currentPage.value || 1);
const pageSize = computed(() => chunksComposable.value?.pageSize.value || 50);
const totalPages = computed(() => chunksComposable.value?.totalPages.value || 0);
const hasNextPage = computed(() => chunksComposable.value?.hasNextPage.value || false);
const hasPreviousPage = computed(() => chunksComposable.value?.hasPreviousPage.value || false);

const selectedChunk = ref<string | null>(null);

// Load chunks when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value) {
    chunksComposable.value?.loadChunks();
  }
}, { immediate: true });

onMounted(() => {
  // Load worlds with allWithoutInstances filter
  loadWorlds('allWithoutInstances');
});

/**
 * Handle search
 */
const handleSearch = (query: string) => {
  if (!chunksComposable.value) return;
  chunksComposable.value.searchChunks(query);
};

/**
 * Handle view chunk
 */
const handleViewChunk = (chunkKey: string) => {
  selectedChunk.value = chunkKey;
};

/**
 * Handle mark chunk as dirty
 */
const handleMarkDirty = async (chunkKey: string) => {
  if (!currentWorldId.value) return;

  if (!confirm(`Mark chunk ${chunkKey} as dirty for regeneration?`)) {
    return;
  }

  try {
    await chunksComposable.value?.markChunkDirty(chunkKey);
    alert(`Chunk ${chunkKey} marked as dirty successfully!`);
  } catch (e: any) {
    alert(`Failed to mark chunk as dirty: ${e.message}`);
  }
};

/**
 * Close chunk detail
 */
const closeChunkDetail = () => {
  selectedChunk.value = null;
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (!chunksComposable.value) return;
  chunksComposable.value.nextPage();
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (!chunksComposable.value) return;
  chunksComposable.value.previousPage();
};
</script>
