<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <SearchInput
          v-model="searchQuery"
          placeholder="Search block types..."
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
          New Block Type
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <LoadingSpinner v-if="loading && blockTypes.length === 0" />

    <!-- Error State -->
    <ErrorAlert v-else-if="error" :message="error" />

    <!-- Empty State -->
    <div v-else-if="!loading && blockTypes.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No block types found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first block type to get started</p>
    </div>

    <!-- Block Type List -->
    <BlockTypeList
      v-else
      :block-types="blockTypes"
      :loading="loading"
      @edit="openEditDialog"
      @delete="handleDelete"
    />

    <!-- Pagination Controls -->
    <div v-if="!loading && blockTypes.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
      <div class="text-sm text-base-content/70">
        Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} block types
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

    <!-- Editor Dialog -->
    <BlockTypeEditorPanel
      v-if="isEditorOpen"
      ref="blockTypeEditorRef"
      :block-type="selectedBlockType"
      :world-id="currentWorldId!"
      @close="closeEditor"
      @saved="handleSaved"
      @edit-modifier="handleEditModifier"
    />

    <!-- Modifier Editor Dialog (separate, higher level) -->
    <ModifierEditorDialog
      v-if="isModifierEditorOpen && editingModifier"
      :modifier="editingModifier.modifier"
      :status-number="editingModifier.status"
      :world-id="currentWorldId!"
      @close="closeModifierEditor"
      @save="handleModifierSaved"
    />

    <!-- Delete Confirmation Dialog -->
    <div v-if="showDeleteConfirmation" class="modal modal-open">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Delete Block Type</h3>
        <p class="py-4">
          Are you sure you want to delete block type
          <strong>"{{ blockTypeToDelete?.description || blockTypeToDelete?.id }}"</strong>?
        </p>
        <p class="text-sm text-warning pb-4">
          This action cannot be undone.
        </p>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="cancelDelete">
            Cancel
          </button>
          <button class="btn btn-error" @click="confirmDeleteAction">
            Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { BlockType } from '@nimbus/shared';
import { useWorld } from '@/composables/useWorld';
import { useBlockTypes } from '@/composables/useBlockTypes';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import BlockTypeList from '@material/components/BlockTypeList.vue';
import BlockTypeEditorPanel from '@material/components/BlockTypeEditorPanel.vue';
import ModifierEditorDialog from '@components/ModifierEditorDialog.vue';

const { currentWorldId } = useWorld();

const blockTypesComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useBlockTypes(currentWorldId.value);
});

const blockTypes = computed(() => blockTypesComposable.value?.blockTypes.value || []);
const loading = computed(() => blockTypesComposable.value?.loading.value || false);
const error = computed(() => blockTypesComposable.value?.error.value || null);
const searchQuery = ref('');

// Paging
const totalCount = computed(() => blockTypesComposable.value?.totalCount.value || 0);
const currentPage = computed(() => blockTypesComposable.value?.currentPage.value || 1);
const pageSize = computed(() => blockTypesComposable.value?.pageSize.value || 50);
const totalPages = computed(() => blockTypesComposable.value?.totalPages.value || 0);
const hasNextPage = computed(() => blockTypesComposable.value?.hasNextPage.value || false);
const hasPreviousPage = computed(() => blockTypesComposable.value?.hasPreviousPage.value || false);

const isEditorOpen = ref(false);
const selectedBlockType = ref<BlockType | null>(null);
const isModifierEditorOpen = ref(false);
const editingModifier = ref<{ blockType: BlockType; status: number; modifier: any } | null>(null);
const blockTypeEditorRef = ref<any>(null);

// Delete confirmation
const showDeleteConfirmation = ref(false);
const blockTypeToDelete = ref<BlockType | null>(null);

// Load block types when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value) {
    blockTypesComposable.value?.loadBlockTypes();
  }
}, { immediate: true });

/**
 * Handle search
 */
const handleSearch = (query: string) => {
  if (!blockTypesComposable.value) return;
  blockTypesComposable.value.searchBlockTypes(query);
};

/**
 * Open create dialog
 */
const openCreateDialog = () => {
  selectedBlockType.value = null;
  isEditorOpen.value = true;
};

/**
 * Open edit dialog
 * Reloads the block type from server to get fresh data
 */
const openEditDialog = async (blockType: BlockType) => {
  if (!blockTypesComposable.value || !currentWorldId.value) return;

  // Reload from server to get fresh data
  const freshBlockType = await blockTypesComposable.value.getBlockType(blockType.id!);
  if (freshBlockType) {
    selectedBlockType.value = freshBlockType;
    isEditorOpen.value = true;
  } else {
    console.error('Failed to load block type from server', blockType.id);
    // Fallback to cached data
    selectedBlockType.value = blockType;
    isEditorOpen.value = true;
  }
};

/**
 * Close editor
 */
const closeEditor = () => {
  isEditorOpen.value = false;
  selectedBlockType.value = null;
};

/**
 * Handle saved
 */
const handleSaved = () => {
  closeEditor();
};

/**
 * Handle delete - show confirmation dialog
 */
const handleDelete = (blockType: BlockType) => {
  blockTypeToDelete.value = blockType;
  showDeleteConfirmation.value = true;
};

/**
 * Cancel delete action
 */
const cancelDelete = () => {
  showDeleteConfirmation.value = false;
  blockTypeToDelete.value = null;
};

/**
 * Confirm delete action
 */
const confirmDeleteAction = async () => {
  if (!blockTypesComposable.value || !blockTypeToDelete.value) return;

  await blockTypesComposable.value.deleteBlockType(blockTypeToDelete.value.id);

  // Close confirmation dialog
  showDeleteConfirmation.value = false;
  blockTypeToDelete.value = null;
};

/**
 * Handle edit modifier
 */
const handleEditModifier = (data: { blockType: BlockType; status: number; modifier: any }) => {
  editingModifier.value = data;
  isModifierEditorOpen.value = true;
};

/**
 * Close modifier editor
 */
const closeModifierEditor = () => {
  isModifierEditorOpen.value = false;
  editingModifier.value = null;
};

/**
 * Handle modifier saved
 */
const handleModifierSaved = (modifier: any) => {
  if (editingModifier.value && blockTypeEditorRef.value) {
    // Update the modifier directly in BlockTypeEditorPanel
    blockTypeEditorRef.value.updateModifier(editingModifier.value.status, modifier);
  }
  closeModifierEditor();
};

/**
 * Handle next page
 */
const handleNextPage = () => {
  if (!blockTypesComposable.value) return;
  blockTypesComposable.value.nextPage();
};

/**
 * Handle previous page
 */
const handlePreviousPage = () => {
  if (!blockTypesComposable.value) return;
  blockTypesComposable.value.previousPage();
};

onMounted(async () => {
  // Note: WorldSelector in AppHeader loads worlds with 'withCollections' filter

  if (currentWorldId.value && blockTypesComposable.value) {
    await blockTypesComposable.value.loadBlockTypes();

    // Check if there's a block type ID in the URL
    const params = new URLSearchParams(window.location.search);
    const blockTypeIdParam = params.get('id');

    if (blockTypeIdParam) {
      const blockTypeId = parseInt(blockTypeIdParam, 10);
      if (!isNaN(blockTypeId)) {
        // Set search query to the ID to show the block type in the list
        searchQuery.value = blockTypeId.toString();
        handleSearch(searchQuery.value);
      }
    }
  }
});
</script>
