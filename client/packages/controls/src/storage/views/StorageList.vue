<template>
  <div class="flex flex-col h-full">
    <!-- Header -->
    <div class="mb-6">
      <h2 class="text-2xl font-bold mb-2">Storage Browser</h2>
      <p class="text-base-content/70 text-sm">
        Browse binary storage data (chunks, assets, layers). Each entry represents a logical file identified by UUID.
      </p>
    </div>

    <!-- Search Bar -->
    <div class="mb-6">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search by UUID, path, schema, or worldId..."
        class="input input-bordered w-full"
        @input="handleSearchInput"
      />
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex-1 flex items-center justify-center">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Storage List -->
    <div v-else-if="storageList.length > 0" class="flex-1 overflow-auto">
      <div class="grid grid-cols-1 gap-4">
        <div
          v-for="storage in storageList"
          :key="storage.uuid"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow"
        >
          <div class="card-body p-4">
            <div class="flex justify-between items-start gap-4">
              <div class="flex-1 min-w-0">
                <h3 class="font-mono text-sm font-semibold truncate mb-2">
                  {{ storage.uuid }}
                </h3>
                <div class="space-y-1 text-sm">
                  <div class="flex gap-2">
                    <span class="text-base-content/70">Schema:</span>
                    <span class="font-medium">{{ storage.schema }}</span>
                    <span class="badge badge-sm">{{ storage.schemaVersion }}</span>
                  </div>
                  <div v-if="storage.worldId" class="flex gap-2">
                    <span class="text-base-content/70">World:</span>
                    <span class="font-mono text-xs">{{ storage.worldId }}</span>
                  </div>
                  <div v-if="storage.path" class="flex gap-2">
                    <span class="text-base-content/70">Path:</span>
                    <span class="font-mono text-xs truncate">{{ storage.path }}</span>
                  </div>
                  <div class="flex gap-4">
                    <div class="flex gap-2">
                      <span class="text-base-content/70">Size:</span>
                      <span>{{ formatBytes(storage.size) }}</span>
                    </div>
                    <div class="flex gap-2">
                      <span class="text-base-content/70">Created:</span>
                      <span>{{ formatDate(storage.createdAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="flex gap-2">
                <button
                  class="btn btn-sm btn-primary"
                  @click="handleDownload(storage.uuid)"
                  title="Download"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="flex-1 flex items-center justify-center">
      <div class="text-center text-base-content/70">
        <svg class="w-16 h-16 mx-auto mb-4 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
        </svg>
        <p class="text-lg">No storage entries found</p>
        <p class="text-sm mt-2">Try adjusting your search query</p>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="!loading && totalCount > 0" class="mt-6 flex items-center justify-between gap-4">
      <div class="text-sm text-base-content/70">
        Showing {{ Math.min(offset + 1, totalCount) }}-{{ Math.min(offset + storageList.length, totalCount) }} of {{ totalCount }}
      </div>
      <div class="flex gap-2">
        <button
          class="btn btn-sm"
          :disabled="!hasPreviousPage"
          @click="handlePreviousPage"
        >
          Previous
        </button>
        <span class="text-sm flex items-center">
          Page {{ currentPage }} of {{ totalPages }}
        </span>
        <button
          class="btn btn-sm"
          :disabled="!hasNextPage"
          @click="handleNextPage"
        >
          Next
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { storageServiceFrontend, type StorageEntry } from '../services/StorageServiceFrontend';

const storageList = ref<StorageEntry[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const currentPage = ref(1);
const pageSize = ref(20);
const totalCount = ref(0);

let searchTimeout: NodeJS.Timeout | null = null;

const offset = computed(() => (currentPage.value - 1) * pageSize.value);
const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

/**
 * Load storage entries
 */
const loadStorageList = async () => {
  loading.value = true;
  error.value = null;

  try {
    const response = await storageServiceFrontend.listStorage(
      searchQuery.value || undefined,
      offset.value,
      pageSize.value
    );

    storageList.value = response.items;
    totalCount.value = response.count;
  } catch (err: any) {
    error.value = err.message || 'Failed to load storage entries';
    console.error('[StorageList] Failed to load storage:', err);
  } finally {
    loading.value = false;
  }
};

/**
 * Handle search input (debounced)
 */
const handleSearchInput = () => {
  if (searchTimeout) {
    clearTimeout(searchTimeout);
  }

  searchTimeout = setTimeout(() => {
    currentPage.value = 1; // Reset to first page
    loadStorageList();
  }, 300); // 300ms debounce
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
    loadStorageList();
  }
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
    loadStorageList();
  }
};

/**
 * Handle download
 */
const handleDownload = (uuid: string) => {
  storageServiceFrontend.downloadContent(uuid);
};

/**
 * Format bytes to human-readable size
 */
const formatBytes = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
};

/**
 * Format date to readable string
 */
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString();
};

onMounted(() => {
  loadStorageList();
});
</script>
