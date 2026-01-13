<template>
  <div class="space-y-4">
    <!-- Header with Search and Filters -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search instances by title, instanceId or worldId..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
    </div>

    <!-- Filter Controls -->
    <div class="flex flex-wrap gap-4">
      <div class="form-control">
        <label class="label">
          <span class="label-text">Filter by World ID:</span>
        </label>
        <input
          v-model="filterWorldId"
          type="text"
          placeholder="Enter worldId"
          class="input input-bordered input-sm"
          @input="handleFilterChange"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text">Filter by Creator:</span>
        </label>
        <input
          v-model="filterCreator"
          type="text"
          placeholder="Enter playerId"
          class="input input-bordered input-sm"
          @input="handleFilterChange"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text">&nbsp;</span>
        </label>
        <button class="btn btn-sm btn-ghost" @click="handleClearFilters">
          Clear Filters
        </button>
      </div>
    </div>

    <!-- Info Alert -->
    <div class="alert alert-info">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Instances are created by players in-game. Here you can view and delete instances.</span>
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
    <div v-else-if="!loading && filteredInstances.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No instances found</p>
      <p class="text-base-content/50 text-sm mt-2">Instances will appear here when players create them in-game</p>
    </div>

    <!-- Instances Table -->
    <div v-else>
      <div class="overflow-x-auto">
        <table class="table table-zebra w-full">
          <thead>
            <tr>
              <th>Instance ID</th>
              <th>Title</th>
              <th>World ID</th>
              <th>Creator</th>
              <th>Players</th>
              <th>Created</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="instance in paginatedInstances" :key="instance.instanceId">
              <td>
                <div class="font-mono text-xs truncate max-w-xs" :title="instance.instanceId">
                  {{ instance.instanceId }}
                </div>
              </td>
              <td>
                <div class="font-medium" :title="instance.title">
                  {{ instance.title }}
                </div>
                <div v-if="instance.description" class="text-xs text-base-content/70 truncate max-w-xs">
                  {{ instance.description }}
                </div>
              </td>
              <td>
                <div class="font-mono text-xs" :title="instance.worldId">
                  {{ instance.worldId }}
                </div>
              </td>
              <td>
                <div class="font-mono text-xs" :title="instance.creator">
                  {{ instance.creator }}
                </div>
              </td>
              <td>
                <div class="badge badge-sm badge-outline">
                  {{ instance.players.length }} player{{ instance.players.length !== 1 ? 's' : '' }}
                </div>
              </td>
              <td>
                <div class="text-xs text-base-content/70">
                  {{ formatDate(instance.createdAt) }}
                </div>
              </td>
              <td>
                <span
                  class="badge badge-sm"
                  :class="instance.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ instance.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </td>
              <td>
                <button
                  class="btn btn-ghost btn-xs text-error"
                  @click="handleDelete(instance.instanceId, instance.title)"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                  Delete
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredInstances.length) }} of {{ filteredInstances.length }} instances
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
import { instanceServiceFrontend, type Instance } from '../services/InstanceServiceFrontend';

const instances = ref<Instance[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterWorldId = ref('');
const filterCreator = ref('');

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

const filteredInstances = computed(() => {
  let result = instances.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(i =>
      i.title.toLowerCase().includes(query) ||
      i.instanceId.toLowerCase().includes(query) ||
      i.worldId.toLowerCase().includes(query) ||
      (i.description && i.description.toLowerCase().includes(query))
    );
  }

  return result;
});

const paginatedInstances = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredInstances.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredInstances.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const formatDate = (dateString: string): string => {
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  } catch {
    return dateString;
  }
};

const loadInstances = async () => {
  loading.value = true;
  error.value = null;

  try {
    console.log('[InstanceList] Loading instances with filters:', {
      worldId: filterWorldId.value,
      creator: filterCreator.value,
    });

    instances.value = await instanceServiceFrontend.listInstances(
      filterWorldId.value || undefined,
      filterCreator.value || undefined
    );

    console.log('[InstanceList] Loaded instances:', instances.value.length);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load instances';
    console.error('[InstanceList] Failed to load instances:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
};

const handleFilterChange = () => {
  currentPage.value = 1;
  loadInstances();
};

const handleClearFilters = () => {
  filterWorldId.value = '';
  filterCreator.value = '';
  searchQuery.value = '';
  currentPage.value = 1;
  loadInstances();
};

const handleDelete = async (instanceId: string, title: string) => {
  if (!confirm(`Are you sure you want to delete instance "${title}"?\n\nThis action cannot be undone and will remove all data associated with this instance.`)) {
    return;
  }

  try {
    await instanceServiceFrontend.deleteInstance(instanceId);
    await loadInstances();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete instance';
    console.error('[InstanceList] Failed to delete instance:', e);
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
  loadInstances();
});
</script>
