<template>
  <div class="space-y-4">
    <!-- Header with Search -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <SearchInput
          v-model="searchQuery"
          placeholder="Search assets..."
          @search="handleSearch"
        />
      </div>
    </div>

    <!-- Loading State -->
    <LoadingSpinner v-if="loading && assets.length === 0" />

    <!-- Error State -->
    <ErrorAlert v-else-if="error" :message="error" />

    <!-- Empty State -->
    <div v-else-if="!loading && assets.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No assets found</p>
      <p class="text-base-content/50 text-sm mt-2">Upload your first asset to get started</p>
    </div>

    <!-- Asset Grid -->
    <AssetGrid
      v-else
      :assets="assets"
      :get-url="getAssetUrl"
      :is-image="isImage"
      :get-icon="getIcon"
      @delete="handleDelete"
      @asset-click="handleAssetClick"
    />

    <!-- Pagination Controls -->
    <div v-if="!loading && assets.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
      <div class="text-sm text-base-content/70">
        Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} assets
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

    <!-- Asset Info Dialog -->
    <AssetInfoDialog
      v-if="isInfoDialogOpen && selectedAsset"
      :world-id="currentWorldId!"
      :asset-path="selectedAsset.path"
      @close="closeInfoDialog"
      @saved="handleInfoSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { Asset } from '@/services/AssetService';
import { useWorld } from '@/composables/useWorld';
import { useAssets } from '@/composables/useAssets';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import AssetGrid from '@material/components/AssetGrid.vue';
import AssetInfoDialog from '@material/components/AssetInfoDialog.vue';

const { currentWorldId } = useWorld();

const assetsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useAssets(currentWorldId.value);
});

const assets = computed(() => assetsComposable.value?.assets.value || []);
const loading = computed(() => assetsComposable.value?.loading.value || false);
const error = computed(() => assetsComposable.value?.error.value || null);
const getAssetUrl = computed(() => assetsComposable.value?.getAssetUrl || (() => ''));
const isImage = computed(() => assetsComposable.value?.isImage || (() => false));
const getIcon = computed(() => assetsComposable.value?.getIcon || (() => '📦'));
const searchQuery = ref('');

// Paging
const totalCount = computed(() => assetsComposable.value?.totalCount.value || 0);
const currentPage = computed(() => assetsComposable.value?.currentPage.value || 1);
const pageSize = computed(() => assetsComposable.value?.pageSize.value || 50);
const totalPages = computed(() => assetsComposable.value?.totalPages.value || 0);
const hasNextPage = computed(() => assetsComposable.value?.hasNextPage.value || false);
const hasPreviousPage = computed(() => assetsComposable.value?.hasPreviousPage.value || false);

const isInfoDialogOpen = ref(false);
const selectedAsset = ref<Asset | null>(null);

// Load assets when world changes (but not for '?')
watch(currentWorldId, () => {
  if (currentWorldId.value && currentWorldId.value !== '?') {
    assetsComposable.value?.loadAssets();
  }
}, { immediate: true });

/**
 * Handle search
 */
const handleSearch = (query: string) => {
  if (!assetsComposable.value) return;
  assetsComposable.value.searchAssets(query);
};

/**
 * Handle asset click (open info dialog)
 */
const handleAssetClick = (asset: Asset) => {
  selectedAsset.value = asset;
  isInfoDialogOpen.value = true;
};

/**
 * Close info dialog
 */
const closeInfoDialog = () => {
  isInfoDialogOpen.value = false;
  selectedAsset.value = null;
};

/**
 * Handle info saved
 */
const handleInfoSaved = () => {
  closeInfoDialog();
};

/**
 * Handle delete
 */
const handleDelete = async (asset: Asset) => {
  if (!assetsComposable.value) return;

  if (!confirm(`Are you sure you want to delete "${asset.path}"?`)) {
    return;
  }

  await assetsComposable.value.deleteAsset(asset.path);
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (!assetsComposable.value) return;
  assetsComposable.value.nextPage();
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (!assetsComposable.value) return;
  assetsComposable.value.previousPage();
};

onMounted(() => {
  // Note: WorldSelector in AssetAppHeader loads worlds with 'withCollections' filter

  // Load assets if valid worldId is set
  if (currentWorldId.value && currentWorldId.value !== '?' && assetsComposable.value) {
    assetsComposable.value.loadAssets();
  }
});
</script>
