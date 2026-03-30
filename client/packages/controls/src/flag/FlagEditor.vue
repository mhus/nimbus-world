<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search by flag name..."
          class="input input-bordered w-full"
          @keyup.enter="handleSearch"
        />
      </div>
      <button
        class="btn btn-primary"
        :disabled="!currentWorldId"
        @click="openCreateDialog"
      >
        <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        New Flag
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading && items.length === 0" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="alert alert-error">
      <span>{{ error }}</span>
    </div>

    <!-- Empty -->
    <div v-else-if="!loading && items.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No flag definitions found</p>
      <p class="text-base-content/50 text-sm mt-2">Flags are auto-created when rules fire, or create one manually</p>
    </div>

    <!-- Flag Table -->
    <div v-else class="overflow-x-auto">
      <table class="table table-sm">
        <thead>
          <tr>
            <th>Flag Name</th>
            <th>Type</th>
            <th>Default</th>
            <th>Description</th>
            <th>Auto</th>
            <th>Created</th>
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
            <td class="font-mono text-sm font-semibold">{{ item.flagName }}</td>
            <td>
              <span v-if="item.type" class="badge badge-sm badge-outline">{{ item.type }}</span>
              <span v-else class="text-base-content/40">-</span>
            </td>
            <td class="font-mono text-sm">{{ item.defaultValue ?? '-' }}</td>
            <td class="text-sm text-base-content/70 max-w-xs truncate">{{ item.description || '-' }}</td>
            <td>
              <span v-if="item.autoCreated" class="badge badge-xs badge-warning">auto</span>
            </td>
            <td class="text-xs text-base-content/60">{{ formatDate(item.createdAt) }}</td>
            <td class="text-right">
              <button
                class="btn btn-xs btn-error"
                @click.stop="handleDelete(item)"
                title="Delete"
              >
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Pagination -->
    <div v-if="!loading && totalCount > 0" class="flex items-center justify-between gap-4 mt-4">
      <div class="text-sm text-base-content/70">
        Showing {{ offset + 1 }}-{{ Math.min(offset + pageSize, totalCount) }} of {{ totalCount }}
      </div>
      <div class="flex gap-2">
        <button class="btn btn-sm" :disabled="offset === 0" @click="offset -= pageSize; loadData()">Previous</button>
        <button class="btn btn-sm" :disabled="offset + pageSize >= totalCount" @click="offset += pageSize; loadData()">Next</button>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <FlagDialog
      v-if="isDialogOpen"
      :world-id="currentWorldId!"
      :flag="selectedFlag"
      @close="closeDialog"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { apiService } from '@/services/ApiService';
import FlagDialog from './FlagDialog.vue';

interface FlagItem {
  id: string;
  worldId: string;
  flagName: string;
  defaultValue: any;
  type?: string;
  description?: string;
  autoCreated: boolean;
  createdAt: string;
}

const { currentWorldId } = useWorld();

const items = ref<FlagItem[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const totalCount = ref(0);
const offset = ref(0);
const pageSize = 50;

const isDialogOpen = ref(false);
const selectedFlag = ref<FlagItem | null>(null);

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
    const qs = new URLSearchParams(params).toString();
    const response = await apiService.get<{ flags: FlagItem[]; count: number }>(
      `/control/worlds/${currentWorldId.value}/logic-flags?${qs}`
    );
    items.value = response.flags || [];
    totalCount.value = response.count || 0;
  } catch (err: any) {
    error.value = err.message || 'Failed to load flags';
    items.value = [];
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => { offset.value = 0; loadData(); };

const openCreateDialog = () => { selectedFlag.value = null; isDialogOpen.value = true; };
const openEditDialog = (item: FlagItem) => { selectedFlag.value = item; isDialogOpen.value = true; };
const closeDialog = () => { isDialogOpen.value = false; selectedFlag.value = null; };
const handleSaved = () => { closeDialog(); loadData(); };

const handleDelete = async (item: FlagItem) => {
  if (!currentWorldId.value) return;
  if (!confirm(`Delete flag definition "${item.flagName}"?`)) return;
  try {
    await apiService.delete(`/control/worlds/${currentWorldId.value}/logic-flags/${item.id}`);
    loadData();
  } catch (err: any) {
    error.value = err.message || 'Failed to delete';
  }
};

const formatDate = (date: string | undefined): string => {
  if (!date) return '-';
  return new Date(date).toLocaleString();
};

watch(currentWorldId, () => {
  offset.value = 0;
  if (currentWorldId.value) loadData();
  else { items.value = []; totalCount.value = 0; }
}, { immediate: true });
</script>
