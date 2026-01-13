<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus Edit Cache Editor" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <!-- Check if world is selected -->
      <div v-if="!currentWorldId" class="alert alert-info">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>Please select a world to view edit cache.</span>
      </div>

      <!-- Edit Cache Content (only shown when world is selected) -->
      <template v-else>
        <div class="space-y-6">
          <!-- Info Card -->
          <div class="card bg-base-100 shadow-md">
            <div class="card-body">
              <h2 class="card-title text-lg">Edit Cache Overview</h2>
              <p class="text-sm text-base-content/70">
                This shows all layers with pending edit cache entries.
                You can apply changes to merge them into layers or discard them.
              </p>
            </div>
          </div>

          <!-- Loading State -->
          <LoadingSpinner v-if="loading" />

          <!-- Error State -->
          <ErrorAlert v-else-if="error" :message="error" />

          <!-- Empty State -->
          <div v-else-if="statistics.length === 0" class="card bg-base-100 shadow-md">
            <div class="card-body text-center py-12">
              <svg class="w-16 h-16 mx-auto text-base-content/30 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
              </svg>
              <h3 class="text-xl font-semibold mb-2">No Pending Changes</h3>
              <p class="text-base-content/70">There are no cached edits for this world.</p>
            </div>
          </div>

          <!-- Statistics Table -->
          <div v-else class="card bg-base-100 shadow-md">
            <div class="card-body">
              <h2 class="card-title text-lg mb-4">Layers with Pending Changes</h2>

              <div class="overflow-x-auto">
                <table class="table table-zebra w-full">
                  <thead>
                    <tr>
                      <th>Layer Name</th>
                      <th>Block Count</th>
                      <th>First Edit</th>
                      <th>Last Edit</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="stat in statistics" :key="stat.layerDataId">
                      <td class="font-medium">{{ stat.layerName }}</td>
                      <td>
                        <span class="badge badge-primary">{{ stat.blockCount }}</span>
                      </td>
                      <td class="text-sm text-base-content/70">
                        {{ formatDate(stat.firstDate) }}
                      </td>
                      <td class="text-sm text-base-content/70">
                        {{ formatDate(stat.lastDate) }}
                      </td>
                      <td>
                        <div class="flex gap-2">
                          <button
                            class="btn btn-sm btn-success"
                            @click="handleApplyChanges(stat.layerDataId, stat.layerName)"
                            :disabled="processingLayer === stat.layerDataId"
                          >
                            <svg v-if="processingLayer === stat.layerDataId" class="animate-spin h-4 w-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                            </svg>
                            <svg v-else class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                            </svg>
                            Apply
                          </button>
                          <button
                            class="btn btn-sm btn-error"
                            @click="handleDiscardChanges(stat.layerDataId, stat.layerName)"
                            :disabled="processingLayer === stat.layerDataId"
                          >
                            <svg v-if="processingLayer === stat.layerDataId" class="animate-spin h-4 w-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                            </svg>
                            <svg v-else class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                            Discard
                          </button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </template>
    </main>

    <!-- Success Toast -->
    <div v-if="successMessage" class="toast toast-top toast-end">
      <div class="alert alert-success">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ successMessage }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, computed } from 'vue';
import { useWorld } from '@/composables/useWorld';
import AppHeader from '@material/components/AppHeader.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import axios from 'axios';
import { apiService } from '@/services/ApiService';

interface EditCacheStat {
  layerDataId: string;
  layerName: string;
  blockCount: number;
  firstDate: string | null;
  lastDate: string | null;
}

const { currentWorldId, loadWorlds } = useWorld();

// Read sessionId from URL query parameter
const params = new URLSearchParams(window.location.search);
const sessionId = ref(params.get('sessionId') || '');

const statistics = ref<EditCacheStat[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const processingLayer = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const apiUrl = computed(() => apiService.getBaseUrl());

/**
 * Load edit cache statistics for current world
 * @param silent If true, don't show loading spinner and errors
 */
const loadStatistics = async (silent = false) => {
  if (!currentWorldId.value) {
    statistics.value = [];
    return;
  }

  if (!silent) {
    loading.value = true;
    error.value = null;
  }

  try {
    const response = await axios.get(
      `${apiUrl.value}/control/editor/${currentWorldId.value}/editcache/statistics`,
      { withCredentials: true }
    );

    // Only update if data has changed (compare JSON strings)
    const newData = response.data;
    const currentData = statistics.value;

    const hasChanged = JSON.stringify(newData) !== JSON.stringify(currentData);

    if (hasChanged || !silent) {
      statistics.value = newData;
    }
  } catch (err: any) {
    if (!silent) {
      console.error('Failed to load edit cache statistics:', err);
      error.value = err.response?.data?.message || 'Failed to load statistics';
    }
  } finally {
    if (!silent) {
      loading.value = false;
    }
  }
};

/**
 * Apply changes for a layer
 */
const handleApplyChanges = async (layerDataId: string, layerName: string) => {
  if (!currentWorldId.value) return;

  if (!confirm(`Apply all ${statistics.value.find(s => s.layerDataId === layerDataId)?.blockCount} pending changes to layer "${layerName}"?\n\nThis will merge the cached blocks into the layer.`)) {
    return;
  }

  processingLayer.value = layerDataId;
  error.value = null;

  try {
    await axios.post(
      `${apiUrl.value}/control/editor/${currentWorldId.value}/editcache/${layerDataId}/apply`,
      {},
      { withCredentials: true }
    );

    showSuccess(`Changes applied to layer "${layerName}"`);

    // Reload statistics after a short delay to allow backend processing
    setTimeout(() => {
      loadStatistics();
    }, 1000);
  } catch (err: any) {
    console.error('Failed to apply changes:', err);
    error.value = err.response?.data?.message || 'Failed to apply changes';
  } finally {
    processingLayer.value = null;
  }
};

/**
 * Discard changes for a layer
 */
const handleDiscardChanges = async (layerDataId: string, layerName: string) => {
  if (!currentWorldId.value) return;

  const blockCount = statistics.value.find(s => s.layerDataId === layerDataId)?.blockCount;
  if (!confirm(`Discard all ${blockCount} pending changes for layer "${layerName}"?\n\nThis action cannot be undone!`)) {
    return;
  }

  processingLayer.value = layerDataId;
  error.value = null;

  try {
    const response = await axios.post(
      `${apiUrl.value}/control/editor/${currentWorldId.value}/editcache/${layerDataId}/discard`,
      {},
      { withCredentials: true }
    );

    const deleted = response.data.deleted || blockCount;
    showSuccess(`Discarded ${deleted} changes from layer "${layerName}"`);

    // Reload statistics
    await loadStatistics();
  } catch (err: any) {
    console.error('Failed to discard changes:', err);
    error.value = err.response?.data?.message || 'Failed to discard changes';
  } finally {
    processingLayer.value = null;
  }
};

/**
 * Format date for display
 */
const formatDate = (dateStr: string | null): string => {
  if (!dateStr) return 'N/A';

  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return 'just now';
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;

  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
};

/**
 * Show success message
 */
const showSuccess = (message: string) => {
  successMessage.value = message;
  setTimeout(() => {
    successMessage.value = null;
  }, 3000);
};

// Auto-refresh interval
let refreshInterval: number | null = null;

// Watch for world changes
watch(currentWorldId, () => {
  loadStatistics();
}, { immediate: true });

onMounted(() => {
  // Load worlds with allWithoutInstances filter
  loadWorlds('allWithoutInstances');

  // Setup auto-refresh every 5 seconds (silent mode)
  refreshInterval = window.setInterval(() => {
    loadStatistics(true); // Silent mode - only updates if data changed
  }, 5000);
});

onUnmounted(() => {
  // Clear interval on unmount
  if (refreshInterval !== null) {
    clearInterval(refreshInterval);
  }
});
</script>
