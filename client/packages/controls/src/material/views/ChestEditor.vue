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
              class="p-3 border rounded mb-2 hover:bg-base-200 cursor-pointer flex justify-between items-center"
              @click="handleAddItemToChest(item)"
            >
              <div class="flex-1">
                <div class="font-semibold">{{ item.name }}</div>
                <div class="text-xs font-mono text-base-content/60">{{ item.itemId }}</div>
                <div v-if="item.texture" class="text-xs text-base-content/50">{{ item.texture }}</div>
              </div>
              <svg class="w-5 h-5 text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7l5 5m0 0l-5 5m5-5H6" />
              </svg>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Panel: Chests -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
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
              <option value="USER">User Chests</option>
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
              :key="chest.id"
              class="p-3 border rounded mb-2 hover:bg-base-200 cursor-pointer transition-all"
              :class="{ 'ring-2 ring-primary bg-base-200': selectedChest?.id === chest.id }"
              @click="selectChestForItemAdd(chest)"
            >
              <div class="flex justify-between items-start">
                <div class="flex-1">
                  <div class="flex items-center gap-2">
                    <div class="font-semibold">{{ chest.displayName || chest.name }}</div>
                    <svg v-if="selectedChest?.id === chest.id" class="w-4 h-4 text-primary" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                  </div>
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

              <!-- Items in Chest -->
              <div v-if="chest.items && chest.items.length > 0" class="mt-2 pl-2 border-l-2 border-base-300">
                <div v-for="itemRef in chest.items.slice(0, 3)" :key="itemRef.itemId" class="text-xs flex justify-between items-center py-1">
                  <span>{{ itemRef.name || itemRef.itemId }}</span>
                  <div class="flex gap-2 items-center">
                    <span
                      class="badge badge-xs cursor-pointer hover:badge-primary transition-colors"
                      @click.stop="handleEditAmount(chest, itemRef)"
                      title="Click to edit amount"
                    >
                      x{{ itemRef.amount }}
                    </span>
                    <button
                      class="btn btn-xs btn-ghost"
                      @click.stop="handleRemoveItemFromChest(chest, itemRef.itemId)"
                      title="Remove item"
                    >
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>
                <div v-if="chest.items.length > 3" class="text-xs text-base-content/50">
                  +{{ chest.items.length - 3 }} more...
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create/Edit Chest Dialog -->
    <ChestDialog
      v-if="isDialogOpen"
      :region-id="currentRegionId!"
      :chest="selectedChest"
      @close="closeDialog"
      @saved="handleChestSaved"
    />

    <!-- Add Item Dialog -->
    <ItemRefDialog
      v-if="isItemRefDialogOpen && selectedItem && selectedChest"
      :item="selectedItem"
      :chest="selectedChest"
      :region-id="currentRegionId!"
      @close="closeItemRefDialog"
      @saved="handleItemRefSaved"
    />

    <!-- Edit Amount Dialog -->
    <AmountEditDialog
      v-if="isAmountEditDialogOpen && editingItemRef && editingChest"
      :item-ref="editingItemRef"
      :chest="editingChest"
      :region-id="currentRegionId!"
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
import { useRegion } from '@/composables/useRegion';
import { useWorld } from '@/composables/useWorld';
import { useChests } from '@/composables/useChests';
import { useItems } from '@/composables/useItems';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import ChestDialog from '@material/components/ChestDialog.vue';
import ItemRefDialog from '@material/components/ItemRefDialog.vue';
import AmountEditDialog from '@material/components/AmountEditDialog.vue';

const { currentRegionId } = useRegion();
const { currentWorldId, worlds, loadWorlds } = useWorld();

const selectedWorldId = ref<string | null>(currentWorldId.value);
const selectedChestType = ref<ChestType | ''>('');

const chestsComposable = computed(() => {
  if (!currentRegionId.value) return null;
  return useChests(currentRegionId.value);
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

const itemSearchQuery = ref('');
const isDialogOpen = ref(false);
const isItemRefDialogOpen = ref(false);
const isAmountEditDialogOpen = ref(false);
const selectedChest = ref<WChest | null>(null);
const selectedItem = ref<ItemSearchResult | null>(null);
const editingItemRef = ref<ItemRef | null>(null);
const editingChest = ref<WChest | null>(null);

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
    case 'USER':
      return 'badge-accent';
    default:
      return '';
  }
};

/**
 * Open create chest dialog
 */
const openCreateDialog = () => {
  selectedChest.value = null;
  isDialogOpen.value = true;
};

/**
 * Select chest for adding items (does NOT open dialog)
 */
const selectChestForItemAdd = (chest: WChest) => {
  selectedChest.value = chest;
};

/**
 * Handle edit chest (opens dialog)
 */
const handleEditChest = (chest: WChest) => {
  selectedChest.value = chest;
  isDialogOpen.value = true;
};

/**
 * Close chest dialog
 */
const closeDialog = () => {
  isDialogOpen.value = false;
  selectedChest.value = null;
};

/**
 * Handle chest saved
 */
const handleChestSaved = async () => {
  closeDialog();
  if (chestsComposable.value) {
    await chestsComposable.value.loadChests();
  }
};

/**
 * Handle delete chest
 */
const handleDeleteChest = async (chest: WChest) => {
  if (!chestsComposable.value) return;

  const displayName = chest.displayName || chest.name;
  if (!confirm(`Are you sure you want to delete chest "${displayName}"?`)) {
    return;
  }

  await chestsComposable.value.deleteChest(chest.name);
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
 * Handle item ref saved
 */
const handleItemRefSaved = async () => {
  closeItemRefDialog();
  if (chestsComposable.value) {
    await chestsComposable.value.loadChests();
  }
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
  if (chestsComposable.value) {
    await chestsComposable.value.loadChests();
  }
};

/**
 * Handle remove item from chest
 */
const handleRemoveItemFromChest = async (chest: WChest, itemId: string) => {
  if (!chestsComposable.value) return;

  await chestsComposable.value.removeItem(chest.name, itemId);
};

// Load worlds and chests when region changes
watch(currentRegionId, () => {
  if (currentRegionId.value) {
    loadWorlds('mainOnly');
    if (chestsComposable.value) {
      chestsComposable.value.loadChests();
    }
  }
}, { immediate: true });

onMounted(async () => {
  await loadWorlds('mainOnly');

  if (currentRegionId.value && chestsComposable.value) {
    await chestsComposable.value.loadChests();
  }
});
</script>
