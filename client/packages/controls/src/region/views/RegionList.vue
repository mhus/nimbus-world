<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search regions by name..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <button class="btn btn-primary" @click="handleCreate">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Region
        </button>
      </div>
    </div>

    <!-- Filter Controls -->
    <div class="flex gap-2">
      <label class="label cursor-pointer gap-2">
        <input
          v-model="filterEnabled"
          type="checkbox"
          class="checkbox checkbox-sm"
        />
        <span class="label-text">Show only enabled</span>
      </label>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading && filteredRegions.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No regions found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first region to get started</p>
    </div>

    <!-- Regions Grid -->
    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="region in paginatedRegions"
          :key="region.id"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="handleSelect(region.id)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-base truncate" :title="region.name">
              {{ region.name }}
            </h3>
            <div class="space-y-2">
              <div class="flex items-center gap-2">
                <span
                  class="badge badge-sm"
                  :class="region.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ region.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </div>
              <div class="text-xs text-base-content/70">
                <span class="font-medium">Maintainers:</span>
                {{ region.maintainers.length }}
              </div>
            </div>
            <div class="card-actions justify-end mt-2">
              <button
                class="btn btn-ghost btn-xs"
                @click.stop="handleSelect(region.id)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-error"
                @click.stop="handleDelete(region.id, region.name)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredRegions.length) }} of {{ filteredRegions.length }} regions
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { regionService, type Region } from '../services/RegionService';

const emit = defineEmits<{
  select: [id: string];
  create: [];
}>();

const regions = ref<Region[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterEnabled = ref(false);

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

const filteredRegions = computed(() => {
  let result = regions.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(r => r.name.toLowerCase().includes(query));
  }

  if (filterEnabled.value) {
    result = result.filter(r => r.enabled);
  }

  return result;
});

const paginatedRegions = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredRegions.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredRegions.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const loadRegions = async () => {
  loading.value = true;
  error.value = null;

  try {
    regions.value = await regionService.listRegions();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load regions';
    console.error('Failed to load regions:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  // Reset to first page when searching
  currentPage.value = 1;
};

const handleCreate = () => {
  emit('create');
};

const handleSelect = (id: string) => {
  emit('select', id);
};

const handleDelete = async (id: string, name: string) => {
  if (!confirm(`Are you sure you want to delete region "${name}"?`)) {
    return;
  }

  try {
    await regionService.deleteRegion(id);
    await loadRegions();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete region';
    console.error('Failed to delete region:', e);
  }
};

const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
  }
};

const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
  }
};

onMounted(() => {
  loadRegions();
});
</script>
