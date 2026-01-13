<template>
  <div class="space-y-4">
    <!-- Check if world is selected -->
    <div v-if="!currentWorldId" class="alert alert-info">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Please select a world to view and edit layers.</span>
    </div>

    <!-- Layer Editor Content (only shown when world is selected) -->
    <template v-else>
      <!-- Header with Search and Actions -->
      <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
        <div class="flex-1">
          <SearchInput
            v-model="searchQuery"
            placeholder="Search layers by name..."
            @search="handleSearch"
          />
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-primary"
            @click="openCreateDialog"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            New Layer
          </button>
        </div>
      </div>

      <!-- Loading State -->
      <LoadingSpinner v-if="loading && layers.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && layers.length === 0" class="text-center py-12">
        <p class="text-base-content/70 text-lg">No layers found</p>
        <p class="text-base-content/50 text-sm mt-2">Create your first layer to get started</p>
      </div>

      <!-- Layer List -->
      <LayerList
        v-else
        :layers="layers"
        :loading="loading"
        @edit="openEditDialog"
        @delete="handleDelete"
      />

      <!-- Pagination Controls -->
      <div v-if="!loading && layers.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} layers
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

      <!-- Editor Dialog (hidden when model editor is open) -->
      <LayerEditorPanel
        v-if="isEditorOpen && !isModelEditorOpen"
        ref="layerEditorRef"
        :layer="selectedLayer"
        :world-id="currentWorldId!"
        @close="closeEditor"
        @saved="handleSaved"
        @open-model-editor="handleOpenModelEditor"
        @open-grid-editor="handleOpenGridEditor"
      />

    </template>
  </div>

  <!-- Model Editor Dialog (using Teleport to separate from Layer Editor) -->
  <Teleport to="body">
    <ModelEditorPanel
      v-if="isModelEditorOpen && modelEditorLayerId && modelEditorLayerDataId"
      :model="selectedModel"
      :layer-id="modelEditorLayerId"
      :layer-data-id="modelEditorLayerDataId"
      :world-id="currentWorldId!"
      @close="closeModelEditor"
      @saved="handleModelSaved"
      @open-grid-editor="handleOpenGridEditor"
    />
  </Teleport>

  <!-- Block Grid Editor (using Teleport) -->
  <Teleport to="body">
    <BlockGridEditor
      v-if="isGridEditorOpen"
      :world-id="currentWorldId!"
      :layer-id="gridEditorParams.layerId!"
      :layer-name="gridEditorParams.layerName || ''"
      :source-type="gridEditorParams.sourceType"
      :model-id="gridEditorParams.modelId"
      :model-name="gridEditorParams.modelName"
      @close="closeGridEditor"
    />
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import type { WLayer, LayerModelDto } from '@nimbus/shared';
import { useWorld } from '@/composables/useWorld';
import { useLayers } from '@/composables/useLayers';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import LayerList from '@layer/components/LayerList.vue';
import LayerEditorPanel from '@layer/components/LayerEditorPanel.vue';
import ModelEditorPanel from '@layer/components/ModelEditorPanel.vue';
import BlockGridEditor from '@layer/components/BlockGridEditor.vue';

const { currentWorldId } = useWorld();

const layersComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useLayers(currentWorldId.value);
});

const layers = computed(() => layersComposable.value?.layers.value || []);
const loading = computed(() => layersComposable.value?.loading.value || false);
const error = computed(() => layersComposable.value?.error.value || null);
const searchQuery = ref('');

// Paging
const totalCount = computed(() => layersComposable.value?.totalCount.value || 0);
const currentPage = computed(() => layersComposable.value?.currentPage.value || 1);
const pageSize = computed(() => layersComposable.value?.pageSize.value || 50);
const totalPages = computed(() => layersComposable.value?.totalPages.value || 0);
const hasNextPage = computed(() => layersComposable.value?.hasNextPage.value || false);
const hasPreviousPage = computed(() => layersComposable.value?.hasPreviousPage.value || false);

const isEditorOpen = ref(false);
const selectedLayer = ref<WLayer | null>(null);
const layerEditorRef = ref<InstanceType<typeof LayerEditorPanel> | null>(null);

// Model editor state
const isModelEditorOpen = ref(false);
const selectedModel = ref<LayerModelDto | null>(null);
const modelEditorLayerId = ref<string | null>(null);
const modelEditorLayerDataId = ref<string | null>(null);

// Grid editor state
const isGridEditorOpen = ref(false);
const gridEditorParams = ref<{
  sourceType: 'terrain' | 'model';
  layerId?: string;
  layerName?: string;
  modelId?: string;
  modelName?: string;
}>({
  sourceType: 'terrain'
});

// Load layers when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value) {
    layersComposable.value?.loadLayers();
  }
}, { immediate: true });

onMounted(() => {
  // Note: WorldSelector in AppHeader loads worlds with 'withCollections' filter
});

/**
 * Handle search
 */
const handleSearch = (query: string) => {
  if (!layersComposable.value) return;
  layersComposable.value.searchLayers(query);
};

/**
 * Open create dialog
 */
const openCreateDialog = () => {
  selectedLayer.value = null;
  isEditorOpen.value = true;
};

/**
 * Open edit dialog
 */
const openEditDialog = (layer: WLayer) => {
  selectedLayer.value = layer;
  isEditorOpen.value = true;
};

/**
 * Close editor
 */
const closeEditor = () => {
  isEditorOpen.value = false;
  selectedLayer.value = null;
};

/**
 * Handle saved
 */
const handleSaved = async () => {
  closeEditor();
  // Reload layers list to show new/updated layer
  if (layersComposable.value) {
    await layersComposable.value.loadLayers();
  }
};

/**
 * Handle delete
 */
const handleDelete = async (layer: WLayer) => {
  if (!layersComposable.value) return;

  if (!confirm(`Are you sure you want to delete layer "${layer.name}"?`)) {
    return;
  }

  await layersComposable.value.deleteLayer(layer.id);
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (!layersComposable.value) return;
  layersComposable.value.nextPage();
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (!layersComposable.value) return;
  layersComposable.value.previousPage();
};

/**
 * Handle open model editor
 */
const handleOpenModelEditor = (layerId: string, layerDataId: string, model: LayerModelDto | null) => {
  modelEditorLayerId.value = layerId;
  modelEditorLayerDataId.value = layerDataId;
  selectedModel.value = model;
  isModelEditorOpen.value = true;
};

/**
 * Close model editor
 */
const closeModelEditor = () => {
  isModelEditorOpen.value = false;
  selectedModel.value = null;
  modelEditorLayerId.value = null;
  modelEditorLayerDataId.value = null;
};

/**
 * Handle model saved
 */
const handleModelSaved = async () => {
  closeModelEditor();
  // Reload models in layer editor
  if (layerEditorRef.value) {
    await layerEditorRef.value.loadModels();
  }
};

/**
 * Handle open grid editor
 */
const handleOpenGridEditor = (params: {
  sourceType: 'terrain' | 'model';
  layerId?: string;
  layerName?: string;
  modelId?: string;
  modelName?: string;
}) => {
  gridEditorParams.value = params;
  isGridEditorOpen.value = true;
};

/**
 * Close grid editor
 */
const closeGridEditor = () => {
  isGridEditorOpen.value = false;
  gridEditorParams.value = {
    sourceType: 'terrain'
  };
};
</script>
