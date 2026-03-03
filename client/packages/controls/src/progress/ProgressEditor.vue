<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1 flex gap-2">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search by playerId, type, quest..."
          class="input input-bordered flex-1"
          @keyup.enter="handleSearch"
        />
        <button
          class="btn btn-primary btn-sm"
          :disabled="!currentWorldId || loading"
          @click="handleSearch"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
          </svg>
          Search
        </button>
      </div>
      <button
        class="btn btn-primary"
        :disabled="!currentWorldId"
        @click="openCreateDialog"
      >
        <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        New Progress
      </button>
    </div>

    <!-- Filter Row -->
    <div class="flex flex-wrap gap-2">
      <input
        v-model="filterPlayerId"
        type="text"
        placeholder="Filter: playerId"
        class="input input-bordered input-sm w-48"
        @keyup.enter="handleSearch"
      />
      <input
        v-model="filterType"
        type="text"
        placeholder="Filter: type"
        class="input input-bordered input-sm w-36"
        @keyup.enter="handleSearch"
      />
      <input
        v-model="filterQuest"
        type="text"
        placeholder="Filter: quest"
        class="input input-bordered input-sm w-36"
        @keyup.enter="handleSearch"
      />
      <button
        v-if="filterPlayerId || filterType || filterQuest"
        class="btn btn-ghost btn-sm"
        @click="clearFilters"
      >
        Clear
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading && items.length === 0" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <span>{{ error }}</span>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading && items.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No progress entries found</p>
      <p class="text-base-content/50 text-sm mt-2">Create a new progress entry or adjust your filters</p>
    </div>

    <!-- Progress List -->
    <div v-else class="overflow-x-auto">
      <table class="table table-sm">
        <thead>
          <tr>
            <th>Player ID</th>
            <th>Type</th>
            <th>Quest</th>
            <th>Data Keys</th>
            <th>Updated</th>
            <th class="text-right">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
            class="hover cursor-pointer"
            @click="openEditDialog(item)"
          >
            <td class="font-mono text-sm">{{ item.playerId }}</td>
            <td>
              <span class="badge badge-sm badge-outline">{{ item.type }}</span>
            </td>
            <td class="text-sm">{{ item.quest || '-' }}</td>
            <td class="text-xs text-base-content/60">
              {{ item.progressData ? Object.keys(item.progressData).join(', ') : '-' }}
            </td>
            <td class="text-xs text-base-content/60">{{ formatDate(item.updatedAt) }}</td>
            <td class="text-right">
              <div class="flex gap-1 justify-end">
                <button
                  class="btn btn-xs btn-ghost"
                  @click.stop="openEditDialog(item)"
                  title="Edit"
                >
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  class="btn btn-xs btn-error"
                  @click.stop="handleDelete(item)"
                  title="Delete"
                >
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination Controls -->
    <div v-if="!loading && totalCount > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-4">
      <div class="text-sm text-base-content/70">
        Showing {{ offset + 1 }}-{{ Math.min(offset + pageSize, totalCount) }} of {{ totalCount }} entries
      </div>
      <div class="flex gap-2">
        <button
          class="btn btn-sm"
          :disabled="offset === 0"
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
          :disabled="offset + pageSize >= totalCount"
          @click="handleNextPage"
        >
          Next
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <ProgressDialog
      v-if="isDialogOpen"
      :world-id="currentWorldId!"
      :progress="selectedProgress"
      @close="closeDialog"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { apiService } from '@/services/ApiService';
import ProgressDialog from './ProgressDialog.vue';

interface ProgressItem {
  id: string;
  worldId: string;
  playerId: string;
  quest?: string;
  type: string;
  progressData: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

const { currentWorldId } = useWorld();

const items = ref<ProgressItem[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterPlayerId = ref('');
const filterType = ref('');
const filterQuest = ref('');
const totalCount = ref(0);
const offset = ref(0);
const pageSize = 50;

const isDialogOpen = ref(false);
const selectedProgress = ref<ProgressItem | null>(null);

const currentPage = computed(() => Math.floor(offset.value / pageSize) + 1);
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize)));

/**
 * Load progress entries from server.
 */
const loadData = async () => {
  if (!currentWorldId.value) return;

  loading.value = true;
  error.value = null;

  try {
    const params: Record<string, string> = {
      offset: String(offset.value),
      limit: String(pageSize),
    };

    if (searchQuery.value) params.query = searchQuery.value;
    if (filterPlayerId.value) params.playerId = filterPlayerId.value;
    if (filterType.value) params.type = filterType.value;
    if (filterQuest.value) params.quest = filterQuest.value;

    const queryString = new URLSearchParams(params).toString();
    const url = `/control/worlds/${currentWorldId.value}/progress?${queryString}`;

    const response = await apiService.get<{ items: ProgressItem[]; count: number }>(url);
    items.value = response.items || [];
    totalCount.value = response.count || 0;
  } catch (err: any) {
    error.value = err.message || 'Failed to load progress entries';
    items.value = [];
    totalCount.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  offset.value = 0;
  loadData();
};

const clearFilters = () => {
  filterPlayerId.value = '';
  filterType.value = '';
  filterQuest.value = '';
  searchQuery.value = '';
  offset.value = 0;
  loadData();
};

const handleNextPage = () => {
  offset.value += pageSize;
  loadData();
};

const handlePreviousPage = () => {
  offset.value = Math.max(0, offset.value - pageSize);
  loadData();
};

const openCreateDialog = () => {
  selectedProgress.value = null;
  isDialogOpen.value = true;
};

const openEditDialog = (item: ProgressItem) => {
  selectedProgress.value = item;
  isDialogOpen.value = true;
};

const closeDialog = () => {
  isDialogOpen.value = false;
  selectedProgress.value = null;
};

const handleSaved = () => {
  closeDialog();
  loadData();
};

const handleDelete = async (item: ProgressItem) => {
  if (!currentWorldId.value) return;
  if (!confirm(`Delete progress entry for player "${item.playerId}" (${item.type})?`)) return;

  try {
    await apiService.delete(`/control/worlds/${currentWorldId.value}/progress/${item.id}`);
    loadData();
  } catch (err: any) {
    error.value = err.message || 'Failed to delete';
  }
};

const formatDate = (date: string | undefined): string => {
  if (!date) return '-';
  return new Date(date).toLocaleString();
};

// Reload when world changes
watch(currentWorldId, () => {
  offset.value = 0;
  if (currentWorldId.value) {
    loadData();
  } else {
    items.value = [];
    totalCount.value = 0;
  }
}, { immediate: true });
</script>
