<template>
  <div class="space-y-4">
    <!-- World Selector for Items -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center">
      <div class="flex-1">
        <label class="label">
          <span class="label-text">World (for loading Items)</span>
        </label>
        <select
          v-model="selectedWorldId"
          class="select select-bordered w-full"
          @change="handleWorldChange"
        >
          <option value="">Select World</option>
          <option v-for="world in worlds" :key="world.worldId" :value="world.worldId">
            {{ world.publicData?.name || world.worldId }}
          </option>
        </select>
      </div>
    </div>

    <!-- Error State -->
    <ErrorAlert v-if="error || itemsError" :message="error || itemsError || ''" />

    <!-- Two-Panel Layout: Items | Chests -->
    <div v-else class="grid grid-cols-1 lg:grid-cols-2 gap-4">
      <!-- Left Panel: Items -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h2 class="card-title">Available Items</h2>

          <!-- Item Search -->
          <div class="form-control">
            <input
              v-model="itemSearchQuery"
              type="text"
              placeholder="Search items..."
              class="input input-bordered"
              :disabled="!selectedWorldId"
              @keyup.enter="handleItemSearch"
            />
            <button
              class="btn btn-primary btn-sm mt-2"
              :disabled="!selectedWorldId || itemsLoading"
              @click="handleItemSearch"
            >
              <svg v-if="itemsLoading" class="w-4 h-4 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              Search
            </button>
          </div>

          <!-- Items List -->
          <div v-if="!selectedWorldId" class="text-center py-8 text-base-content/50">
            Select a world to load items
          </div>
          <div v-else-if="itemSearchResults.length === 0" class="text-center py-8 text-base-content/50">
            No items found. Try searching.
          </div>
          <div v-else class="overflow-y-auto max-h-96">
            <div
              v-for="item in itemSearchResults"
              :key="item.itemId"
              class="p-3 border rounded mb-2 hover:bg-base-200 cursor-pointer flex items-center gap-3"
              @click="handleAddItemToChest(item)"
            >
              <div class="w-10 h-10 flex-shrink-0 rounded bg-base-300 flex items-center justify-center overflow-hidden">
                <img
                  v-if="item.texture"
                  :src="getAssetUrl(item.texture)"
                  :alt="item.itemId"
                  class="w-10 h-10 object-contain"
                  style="image-rendering: pixelated;"
                  @error="onImageError($event)"
                />
                <span v-else class="text-base-content/30 text-xs">?</span>
              </div>
              <div class="flex-1 min-w-0">
                <div class="font-semibold text-sm">{{ item.itemId }}</div>
                <div v-if="item.itemType" class="text-xs text-base-content/60">{{ item.itemType }}</div>
                <div v-if="item.title" class="text-xs text-base-content/50">{{ item.title }}</div>
              </div>
              <svg class="w-5 h-5 text-primary flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Panel: Chests -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">

          <!-- Mode 1: Chest List -->
          <template v-if="!selectedChest">
            <div class="flex justify-between items-center">
              <h2 class="card-title">Chests</h2>
              <button class="btn btn-primary btn-sm" @click="openCreateDialog">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                Create
              </button>
            </div>

            <!-- Chest Type Filter -->
            <div class="form-control">
              <select
                v-model="selectedChestType"
                class="select select-bordered select-sm"
                @change="handleChestFilterChange"
              >
                <option value="">All Types</option>
                <option value="REGION">Region Chests</option>
                <option value="WORLD">World Chests</option>
                <option value="PLAYER">Player Chests</option>
                <option value="BANK">Bank Chests</option>
                <option value="TRANSFER">Transfer Chests</option>
              </select>
            </div>

            <!-- Loading State -->
            <LoadingSpinner v-if="chestsLoading" />

            <!-- Chests List -->
            <div v-else-if="chests.length === 0" class="text-center py-8 text-base-content/50">
              No chests found. Create one!
            </div>
            <div v-else class="overflow-y-auto max-h-96">
              <div
                v-for="chest in chests"
                :key="chest.name"
                class="p-3 border rounded mb-2 hover:bg-base-200 cursor-pointer transition-all"
                @click="selectChestForItemAdd(chest)"
              >
                <div class="flex justify-between items-start">
                  <div class="flex-1">
                    <div class="font-semibold">{{ chest.title || chest.name }}</div>
                    <div class="text-xs font-mono text-base-content/60">{{ chest.name }}</div>
                    <div class="flex gap-2 mt-1">
                      <span class="badge badge-sm" :class="getTypeClass(chest.type)">{{ chest.type }}</span>
                      <span class="badge badge-sm badge-info">{{ chest.items?.length || 0 }} items</span>
                    </div>
                  </div>
                  <div class="flex gap-1">
                    <button
                      class="btn btn-xs btn-ghost"
                      @click.stop="handleEditChest(chest)"
                      title="Edit chest"
                    >
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                      </svg>
                    </button>
                    <button
                      class="btn btn-xs btn-error"
                      @click.stop="handleDeleteChest(chest)"
                      title="Delete chest"
                    >
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- Mode 2: Chest Detail -->
          <template v-else>
            <div class="flex justify-between items-center">
              <h2 class="card-title">{{ selectedChest.title || selectedChest.name }}</h2>
              <div class="flex gap-1">
                <button
                  class="btn btn-xs btn-ghost"
                  @click.stop="handleEditChest(selectedChest)"
                  title="Edit chest"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button class="btn btn-sm btn-ghost" @click="closeChestDetail" title="Close">
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>

            <div class="flex gap-2">
              <span class="badge badge-sm" :class="getTypeClass(selectedChest.type)">{{ selectedChest.type }}</span>
              <span class="text-xs font-mono text-base-content/60">{{ selectedChest.name }}</span>
            </div>

            <!-- Items in Chest -->
            <div v-if="chestItemsLoading" class="flex justify-center py-4">
              <span class="loading loading-spinner loading-sm"></span>
            </div>
            <div v-else-if="!selectedChest.items || selectedChest.items.length === 0" class="text-center py-8 text-base-content/50">
              No items in this chest
            </div>
            <div v-else class="overflow-y-auto max-h-96 space-y-2 mt-2">
              <div
                v-for="itemRef in selectedChest.items"
                :key="itemRef.itemId"
                class="p-2 border rounded flex items-center gap-3"
              >
                <div class="w-10 h-10 flex-shrink-0 rounded bg-base-300 flex items-center justify-center overflow-hidden">
                  <img
                    v-if="itemRef.texture"
                    :src="getAssetUrl(itemRef.texture)"
                    :alt="itemRef.itemId"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-base-content/30 text-xs">?</span>
                </div>
                <div class="flex-1 min-w-0">
                  <div class="font-semibold text-sm">{{ itemRef.itemId }}</div>
                  <div v-if="itemRef.name" class="text-xs text-base-content/50">{{ itemRef.name }}</div>
                </div>
                <div class="flex gap-2 items-center flex-shrink-0">
                  <span
                    class="badge badge-sm cursor-pointer hover:badge-primary transition-colors"
                    @click.stop="handleEditAmount(selectedChest, itemRef)"
                    title="Click to edit amount"
                  >
                    x{{ itemRef.amount }}
                  </span>
                  <button
                    class="btn btn-xs btn-ghost"
                    @click.stop="handleRemoveItemFromChest(selectedChest, itemRef.itemId)"
                    title="Remove item"
                  >
                    <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </template>

        </div>
      </div>
    </div>

    <!-- Create/Edit Chest Dialog -->
    <ChestDialog
      v-if="isDialogOpen"
      :world-id="currentWorldId!"
      :chest="dialogChest"
      @close="closeDialog"
      @saved="handleChestSaved"
    />

    <!-- Add Item Dialog -->
    <ItemRefDialog
      v-if="isItemRefDialogOpen && selectedItem && selectedChest"
      :item="selectedItem"
      :chest="selectedChest"
      :world-id="currentWorldId!"
      @close="closeItemRefDialog"
      @saved="handleItemRefSaved"
    />

    <!-- Edit Amount Dialog -->
    <AmountEditDialog
      v-if="isAmountEditDialogOpen && editingItemRef && editingChest"
      :item-ref="editingItemRef"
      :chest="editingChest"
      :world-id="currentWorldId!"
      @close="closeAmountEditDialog"
      @saved="handleAmountEditSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { WChest, ChestType } from '@shared/generated/entities/WChest';
import type { ItemRef } from '@shared/generated/types/ItemRef';
import type { ItemSearchResult } from '@/composables/useItems';
import { useWorld } from '@/composables/useWorld';
import { useChests } from '@/composables/useChests';
import { useItems } from '@/composables/useItems';
import { apiService } from '@/services/ApiService';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import ChestDialog from '@material/components/ChestDialog.vue';
import ItemRefDialog from '@material/components/ItemRefDialog.vue';
import AmountEditDialog from '@material/components/AmountEditDialog.vue';

const { currentWorldId, worlds, loadWorlds } = useWorld();

const selectedWorldId = ref<string | null>(currentWorldId.value);
const selectedChestType = ref<ChestType | ''>('');

const chestsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useChests(currentWorldId.value);
});

const itemsComposable = computed(() => {
  if (!selectedWorldId.value) return null;
  return useItems(selectedWorldId.value);
});

const chests = computed(() => chestsComposable.value?.chests.value || []);
const chestsLoading = computed(() => chestsComposable.value?.loading.value || false);
const error = computed(() => chestsComposable.value?.error.value || null);

const itemSearchResults = computed(() => itemsComposable.value?.searchResults.value || []);
const itemsLoading = computed(() => itemsComposable.value?.loading.value || false);
const itemsError = computed(() => itemsComposable.value?.error.value || null);

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !selectedWorldId.value) return '';
  return `${apiService.getBaseUrl()}/control/worlds/${selectedWorldId.value}/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
};

const itemSearchQuery = ref('');
const isDialogOpen = ref(false);
const isItemRefDialogOpen = ref(false);
const isAmountEditDialogOpen = ref(false);
const selectedChest = ref<WChest | null>(null);
const dialogChest = ref<WChest | null>(null);
const selectedItem = ref<ItemSearchResult | null>(null);
const editingItemRef = ref<ItemRef | null>(null);
const editingChest = ref<WChest | null>(null);
const chestItemsLoading = ref(false);

/**
 * Handle world change
 */
const handleWorldChange = () => {
  currentWorldId.value = selectedWorldId.value;
  itemSearchQuery.value = '';
  itemSearchResults.value = [];
};

/**
 * Handle chest filter change
 */
const handleChestFilterChange = async () => {
  if (!chestsComposable.value) return;
  const type = selectedChestType.value || undefined;
  await chestsComposable.value.setFilters(type as ChestType | undefined);
};

/**
 * Handle item search
 */
const handleItemSearch = async () => {
  if (!itemsComposable.value) return;
  await itemsComposable.value.searchItems(itemSearchQuery.value);
};

/**
 * Get CSS class for chest type badge
 */
const getTypeClass = (type: ChestType): string => {
  switch (type) {
    case 'REGION':
      return 'badge-primary';
    case 'WORLD':
      return 'badge-secondary';
    case 'PLAYER':
      return 'badge-accent';
    case 'BANK':
      return 'badge-warning';
    case 'TRANSFER':
      return 'badge-info';
    default:
      return '';
  }
};

/**
 * Open create chest dialog
 */
const openCreateDialog = () => {
  dialogChest.value = null;
  isDialogOpen.value = true;
};

/**
 * Select chest to show detail view
 */
const selectChestForItemAdd = (chest: WChest) => {
  selectedChest.value = chest;
};

/**
 * Close chest detail view, return to list
 */
const closeChestDetail = () => {
  selectedChest.value = null;
};

/**
 * Handle edit chest (opens dialog)
 */
const handleEditChest = (chest: WChest) => {
  dialogChest.value = chest;
  isDialogOpen.value = true;
};

/**
 * Close chest dialog
 */
const closeDialog = () => {
  isDialogOpen.value = false;
  dialogChest.value = null;
};

/**
 * Handle chest saved
 */
const handleChestSaved = async () => {
  closeDialog();
  await reloadChests();
};

/**
 * Handle delete chest
 */
const handleDeleteChest = async (chest: WChest) => {
  if (!chestsComposable.value) return;

  const chestTitle = chest.title || chest.name;
  if (!confirm(`Are you sure you want to delete chest "${chestTitle}"?`)) {
    return;
  }

  await chestsComposable.value.deleteChest(chest.name);
  if (selectedChest.value?.name === chest.name) {
    selectedChest.value = null;
  }
};

/**
 * Handle add item to chest
 * - If item already in chest: open amount edit dialog
 * - If item not in chest: open add item dialog
 */
const handleAddItemToChest = (item: ItemSearchResult) => {
  if (!selectedChest.value) {
    // Need to select a chest first
    alert('Please select a chest first');
    return;
  }

  // Check if item already exists in selected chest
  const existingItemRef = selectedChest.value.items?.find(
    itemRef => itemRef.itemId === item.itemId
  );

  if (existingItemRef) {
    // Item exists - open amount edit dialog
    editingChest.value = selectedChest.value;
    editingItemRef.value = existingItemRef;
    isAmountEditDialogOpen.value = true;
  } else {
    // Item doesn't exist - open add dialog
    selectedItem.value = item;
    isItemRefDialogOpen.value = true;
  }
};

/**
 * Close item ref dialog
 */
const closeItemRefDialog = () => {
  isItemRefDialogOpen.value = false;
  selectedItem.value = null;
};

/**
 * Reload chests and refresh selected chest if in detail view
 */
const reloadChests = async () => {
  if (!chestsComposable.value) return;
  await chestsComposable.value.loadChests();
  if (selectedChest.value) {
    const updated = chests.value.find(c => c.name === selectedChest.value?.name);
    selectedChest.value = updated || null;
  }
};

/**
 * Handle item ref saved
 */
const handleItemRefSaved = async () => {
  closeItemRefDialog();
  await reloadChests();
};

/**
 * Handle edit amount
 */
const handleEditAmount = (chest: WChest, itemRef: ItemRef) => {
  editingChest.value = chest;
  editingItemRef.value = itemRef;
  isAmountEditDialogOpen.value = true;
};

/**
 * Close amount edit dialog
 */
const closeAmountEditDialog = () => {
  isAmountEditDialogOpen.value = false;
  editingItemRef.value = null;
  editingChest.value = null;
};

/**
 * Handle amount edit saved
 */
const handleAmountEditSaved = async () => {
  closeAmountEditDialog();
  await reloadChests();
};

/**
 * Handle remove item from chest
 */
const handleRemoveItemFromChest = async (chest: WChest, itemId: string) => {
  if (!chestsComposable.value) return;
  await chestsComposable.value.removeItem(chest.name, itemId);
  await reloadChests();
};

// Load worlds and chests when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value) {
    if (chestsComposable.value) {
      chestsComposable.value.loadChests();
    }
  }
}, { immediate: true });

onMounted(async () => {
  await loadWorlds('mainOnly');

  if (currentWorldId.value && chestsComposable.value) {
    await chestsComposable.value.loadChests();
  }
});
</script>
