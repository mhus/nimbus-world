<template>
  <div class="space-y-4">
    <!-- Header with Selectors and Actions -->
    <div class="flex flex-col gap-4">
      <!-- Collection, World and Search -->
      <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center">
        <!-- World Selector (Optional) -->
        <div class="flex-1">
          <label class="label">
            <span class="label-text">World (Optional)</span>
          </label>
          <select
            v-model="selectedWorldId"
            class="select select-bordered w-full"
            @change="handleWorldChange"
          >
            <option value="">No World Filter</option>
            <option v-for="world in worlds" :key="world.worldId" :value="world.worldId">
              {{ world.publicData?.name || world.worldId }}
            </option>
          </select>
        </div>

        <!-- Collection Input -->
        <div class="flex-1">
          <label class="label">
            <span class="label-text">Collection</span>
          </label>
          <div class="join w-full">
            <input
              v-model="collection"
              type="text"
              placeholder="Enter collection name..."
              class="input input-bordered join-item flex-1"
              @keyup.enter="handleSearch"
            />
            <button
              class="btn btn-square join-item"
              @click="openCollectionSearchDialog"
              title="Search collections"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Type Filter (Optional) -->
        <div class="flex-1">
          <label class="label">
            <span class="label-text">Type Filter (Optional)</span>
          </label>
          <input
            v-model="typeFilter"
            type="text"
            placeholder="Filter by type..."
            class="input input-bordered w-full"
            @keyup.enter="handleSearch"
          />
        </div>

        <!-- Actions -->
        <div class="flex gap-2 items-end">
          <button
            class="btn btn-primary"
            :disabled="!collection"
            @click="handleSearch"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            Search
          </button>
          <button
            class="btn btn-success"
            :disabled="!collection"
            @click="openCreateDialog"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Create
          </button>
        </div>
      </div>
    </div>

    <!-- Loading State -->
    <LoadingSpinner v-if="loading && entities.length === 0" />

    <!-- Error State -->
    <ErrorAlert v-else-if="error" :message="error" />

    <!-- Empty State -->
    <div v-else-if="!loading && entities.length === 0 && hasSearched" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No entities found</p>
      <p class="text-base-content/50 text-sm mt-2">Try a different collection or create a new entity</p>
    </div>

    <!-- Entities Table -->
    <div v-else-if="entities.length > 0" class="overflow-x-auto">
      <table class="table table-zebra w-full">
        <thead>
          <tr>
            <th>Name</th>
            <th>Title</th>
            <th>Type</th>
            <th>Description</th>
            <th>Region</th>
            <th>World</th>
            <th>Enabled</th>
            <th>Updated</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entity in entities" :key="entity.id" class="hover">
            <td class="font-medium">{{ entity.name || '-' }}</td>
            <td>{{ entity.title || '-' }}</td>
            <td>
              <span v-if="entity.type" class="badge badge-primary">{{ entity.type }}</span>
              <span v-else class="text-base-content/50">-</span>
            </td>
            <td class="max-w-xs truncate">{{ entity.description || '-' }}</td>
            <td>
              <span v-if="entity.regionId" class="badge badge-ghost">{{ entity.regionId }}</span>
              <span v-else class="text-base-content/50">-</span>
            </td>
            <td>
              <span v-if="entity.worldId" class="badge badge-ghost">{{ entity.worldId }}</span>
              <span v-else class="text-base-content/50">-</span>
            </td>
            <td>
              <input
                type="checkbox"
                :checked="entity.enabled"
                class="checkbox checkbox-sm"
                disabled
              />
            </td>
            <td class="text-sm text-base-content/70">
              {{ formatDate(entity.updatedAt) }}
            </td>
            <td>
              <div class="flex gap-2">
                <button
                  class="btn btn-sm btn-ghost"
                  @click="handleEdit(entity)"
                  title="Edit"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  class="btn btn-sm btn-ghost btn-error"
                  @click="handleDelete(entity)"
                  title="Delete"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
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
    <div v-if="!loading && entities.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
      <div class="text-sm text-base-content/70">
        Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} entities
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

    <!-- Create/Edit Dialog -->
    <AnythingDialog
      v-if="isDialogOpen"
      :entity="selectedEntity"
      :collection="collection"
      :region-id="currentRegionId"
      :world-id="selectedWorldId"
      @close="closeDialog"
      @saved="handleSaved"
    />

    <!-- Collection Search Dialog -->
    <CollectionSearchDialog
      v-if="isCollectionSearchOpen"
      @close="closeCollectionSearchDialog"
      @select="handleCollectionSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { WAnything } from '@shared/generated/entities/WAnything';
import { useRegion } from '@/composables/useRegion';
import { useWorld } from '@/composables/useWorld';
import { anythingService } from '@/services/AnythingService';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import AnythingDialog from '@material/components/AnythingDialog.vue';
import CollectionSearchDialog from '@material/components/CollectionSearchDialog.vue';

const { currentRegionId, loadRegions } = useRegion();
const { worlds, loadWorlds } = useWorld();

const selectedWorldId = ref<string>('');
const collection = ref<string>('');
const typeFilter = ref<string>('');
const entities = ref<WAnything[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const hasSearched = ref(false);

// Pagination
const totalCount = ref(0);
const currentPage = ref(1);
const pageSize = ref(50);
const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

// Dialog state
const isDialogOpen = ref(false);
const selectedEntity = ref<WAnything | null>(null);
const isCollectionSearchOpen = ref(false);

/**
 * Format date for display
 */
const formatDate = (date: Date | string | undefined): string => {
  if (!date) return '-';
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
};

/**
 * Handle world change
 */
const handleWorldChange = () => {
  currentPage.value = 1;
  if (hasSearched.value) {
    handleSearch();
  }
};

/**
 * Handle search
 */
const handleSearch = async () => {
  if (!collection.value) {
    error.value = 'Collection is required';
    return;
  }

  loading.value = true;
  error.value = null;
  hasSearched.value = true;

  try {
    const offset = (currentPage.value - 1) * pageSize.value;
    const result = await anythingService.list({
      collection: collection.value,
      regionId: currentRegionId.value || undefined,
      worldId: selectedWorldId.value || undefined,
      type: typeFilter.value || undefined,
      enabledOnly: true,
      offset,
      limit: pageSize.value,
    });

    entities.value = result.entities;
    totalCount.value = result.count;
  } catch (e: any) {
    error.value = e.message || 'Failed to load entities';
    entities.value = [];
    totalCount.value = 0;
  } finally {
    loading.value = false;
  }
};

/**
 * Open create dialog
 */
const openCreateDialog = () => {
  selectedEntity.value = null;
  isDialogOpen.value = true;
};

/**
 * Handle edit
 */
const handleEdit = (entity: WAnything) => {
  selectedEntity.value = entity;
  isDialogOpen.value = true;
};

/**
 * Close dialog
 */
const closeDialog = () => {
  isDialogOpen.value = false;
  selectedEntity.value = null;
};

/**
 * Open collection search dialog
 */
const openCollectionSearchDialog = () => {
  isCollectionSearchOpen.value = true;
};

/**
 * Close collection search dialog
 */
const closeCollectionSearchDialog = () => {
  isCollectionSearchOpen.value = false;
};

/**
 * Handle collection selection from dialog
 */
const handleCollectionSelect = (selectedCollection: string) => {
  collection.value = selectedCollection;
};

/**
 * Handle saved
 */
const handleSaved = () => {
  closeDialog();
  handleSearch();
};

/**
 * Handle delete
 */
const handleDelete = async (entity: WAnything) => {
  if (!confirm(`Are you sure you want to delete "${entity.name}"?`)) {
    return;
  }

  try {
    if (entity.regionId && entity.worldId) {
      await anythingService.deleteByRegionAndWorld(entity.regionId, entity.worldId, entity.collection, entity.name);
    } else if (entity.regionId) {
      await anythingService.deleteByRegion(entity.regionId, entity.collection, entity.name);
    } else if (entity.worldId) {
      await anythingService.deleteByWorld(entity.worldId, entity.collection, entity.name);
    } else {
      await anythingService.deleteByCollection(entity.collection, entity.name);
    }

    handleSearch();
  } catch (e: any) {
    error.value = e.message || 'Failed to delete entity';
  }
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
    handleSearch();
  }
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
    handleSearch();
  }
};

// Watch for region changes from header
watch(currentRegionId, () => {
  currentPage.value = 1;
  if (hasSearched.value) {
    handleSearch();
  }
});

onMounted(async () => {
  // Load regions and worlds
  await loadRegions();
  await loadWorlds('all');
});
</script>
