<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-emerald-400">Wearing Panel</h1>
            <p class="text-gray-400 text-sm mt-1">Equip items to wearing slots</p>
          </div>
          <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Back to Panels">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </a>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-emerald-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
      </div>
    </main>

    <!-- Main Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-6">
      <!-- Action Message -->
      <div v-if="actionMessage" class="mb-4 p-3 rounded-lg text-center text-sm font-medium transition-all"
           :class="actionMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ actionMessage.text }}
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Backpack Items -->
        <div class="lg:col-span-1">
          <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700">
            <h2 class="text-lg font-bold text-emerald-400 mb-3">Backpack</h2>

            <div v-if="backpackItems.length === 0" class="text-center py-8 text-gray-500">
              No items in backpack
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in backpackItems"
                :key="item.itemId"
                class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
                :class="selectedItem?.itemId === item.itemId ? 'border-emerald-400 shadow-lg shadow-emerald-400/20' : 'border-gray-600'"
                :title="item.name"
                @click="selectItem(item)"
              >
                <img
                  v-if="item.texture"
                  :src="getAssetUrl(item.texture)"
                  :alt="item.name"
                  class="w-10 h-10 object-contain"
                  style="image-rendering: pixelated;"
                  @error="onImageError($event)"
                />
                <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
                <!-- Count badge -->
                <span
                  v-if="item.count > 1"
                  class="absolute -bottom-1 -right-1 bg-emerald-500 text-gray-900 text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                >{{ item.count > 99 ? '99+' : item.count }}</span>
              </div>
            </div>

            <!-- Selected Item Detail -->
            <div v-if="selectedItem" class="mt-4 pt-4 border-t border-gray-700">
              <div class="flex items-start gap-3">
                <div class="w-16 h-16 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                  <img
                    v-if="selectedItem.texture"
                    :src="getAssetUrl(selectedItem.texture)"
                    :alt="selectedItem.name"
                    class="w-12 h-12 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-gray-500 text-xs">No icon</span>
                </div>
                <div class="min-w-0">
                  <h3 class="font-semibold text-emerald-300 truncate">{{ selectedItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedItem.description" class="text-xs text-gray-500 mt-1">{{ selectedItem.description }}</p>
                  <p class="text-xs text-gray-500 mt-1">Count: {{ selectedItem.count }}</p>
                </div>
              </div>
              <p v-if="selectedItem.wearableSlots?.length" class="text-xs text-gray-500 mt-1">Groups: {{ selectedItem.wearableSlots.join(', ') }}</p>
              <p class="text-xs text-gray-500 mt-2 italic">Click a highlighted wearing slot to equip this item</p>
            </div>
          </div>
        </div>

        <!-- Right Column: Wearing Slots (Paper Doll) -->
        <div class="lg:col-span-2">
          <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700">
            <h2 class="text-lg font-bold text-emerald-400 mb-3">Wearing Slots</h2>

            <div class="wearing-grid mx-auto" style="max-width: 450px;">
              <div
                v-for="def in SLOT_DEFS"
                :key="def.slot"
                class="wearing-slot"
                :style="{ gridColumn: def.col, gridRow: def.row }"
              >
                <div
                  class="slot-box"
                  :class="slotClass(def.slot)"
                  @click="onSlotClick(def.slot)"
                  :title="def.label"
                >
                  <span class="slot-label">{{ def.label }}</span>
                  <img
                    v-if="wearingItems[def.slot]?.texture"
                    :src="getAssetUrl(wearingItems[def.slot]!.texture!)"
                    :alt="def.label"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems[def.slot]" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems[def.slot]!.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue';
import { apiService } from '@/services/ApiService';

interface BackpackItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  count: number;
  wearableSlots: string[] | null; // contains WEARABLE_GROUP names
}

interface WearingItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  count: number;
  wearableSlots: string[] | null;
}

type WearableSlot = 'HEAD' | 'BODY' | 'ARMS' | 'LEGS' | 'FEET' | 'NECK'
  | 'LEFT_RING' | 'RIGHT_RING'
  | 'LEFT_HAND_1' | 'RIGHT_HAND_1' | 'LEFT_HAND_2' | 'RIGHT_HAND_2';

const ALL_SLOTS: WearableSlot[] = [
  'HEAD', 'BODY', 'ARMS', 'LEGS', 'FEET', 'NECK',
  'LEFT_RING', 'RIGHT_RING',
  'LEFT_HAND_1', 'RIGHT_HAND_1', 'LEFT_HAND_2', 'RIGHT_HAND_2',
];

const SLOT_TO_GROUP: Record<WearableSlot, string> = {
  HEAD: 'HEAD',
  BODY: 'BODY',
  ARMS: 'ARMS',
  LEGS: 'LEGS',
  FEET: 'FEET',
  NECK: 'NECK',
  LEFT_RING: 'RING',
  RIGHT_RING: 'RING',
  LEFT_HAND_1: 'HAND',
  RIGHT_HAND_1: 'HAND',
  LEFT_HAND_2: 'HAND',
  RIGHT_HAND_2: 'HAND',
};

const SLOT_DEFS: Array<{ slot: WearableSlot; label: string; col: string; row: number }> = [
  { slot: 'HEAD',         label: 'HEAD',    col: '3 / 5', row: 1 },
  { slot: 'NECK',         label: 'NECK',    col: '3 / 5', row: 2 },
  { slot: 'BODY',         label: 'BODY',    col: '3',     row: 3 },
  { slot: 'ARMS',         label: 'ARMS',    col: '4',     row: 3 },
  { slot: 'LEFT_HAND_2',  label: 'L.HAND2', col: '1',     row: 4 },
  { slot: 'LEFT_HAND_1',  label: 'L.HAND1', col: '2',     row: 4 },
  { slot: 'RIGHT_HAND_1', label: 'R.HAND1', col: '5',     row: 4 },
  { slot: 'RIGHT_HAND_2', label: 'R.HAND2', col: '6',     row: 4 },
  { slot: 'LEFT_RING',    label: 'L.RING',  col: '2',     row: 5 },
  { slot: 'RIGHT_RING',   label: 'R.RING',  col: '5',     row: 5 },
  { slot: 'LEGS',         label: 'LEGS',    col: '3 / 5', row: 6 },
  { slot: 'FEET',         label: 'FEET',    col: '3 / 5', row: 7 },
];

const loading = ref(true);
const error = ref<string | null>(null);
const actionMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const worldId = ref('');
const backpackItems = ref<BackpackItemInfo[]>([]);

const initialWearing: Record<WearableSlot, WearingItemInfo | null> = {} as any;
for (const s of ALL_SLOTS) initialWearing[s] = null;
const wearingItems = reactive<Record<WearableSlot, WearingItemInfo | null>>(initialWearing);

const selectedItem = ref<BackpackItemInfo | null>(null);

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !worldId.value) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
};

const selectItem = (item: BackpackItemInfo) => {
  if (selectedItem.value?.itemId === item.itemId) {
    selectedItem.value = null;
  } else {
    selectedItem.value = item;
  }
};

const isSlotAllowed = (slot: WearableSlot): boolean => {
  if (!selectedItem.value) return false;
  const groups = selectedItem.value.wearableSlots;
  if (!groups || groups.length === 0) return true; // no restriction = all slots allowed
  return groups.includes(SLOT_TO_GROUP[slot]);
};

const slotClass = (slot: WearableSlot): string => {
  if (wearingItems[slot]) return 'border-emerald-600';
  if (selectedItem.value && isSlotAllowed(slot)) return 'border-emerald-400 shadow-lg shadow-emerald-400/30 animate-pulse';
  return 'border-gray-600';
};

const onSlotClick = async (slot: WearableSlot) => {
  const currentItem = wearingItems[slot];

  if (currentItem) {
    // Unequip
    await doUnequip(slot);
  } else if (selectedItem.value) {
    if (!isSlotAllowed(slot)) return; // slot not allowed for this item
    // Equip selected item
    await doEquip(selectedItem.value.itemId, slot);
  }
};

const doEquip = async (itemId: string, slot: WearableSlot) => {
  try {
    await apiService.post('/control/player/wearing/equip', { itemId, slot });
    selectedItem.value = null;
    await loadData();
    showMessage('Item equipped!', 'success');
  } catch (err) {
    console.error('[WearingPanel] Failed to equip:', err);
    showMessage('Failed to equip item.', 'error');
  }
};

const doUnequip = async (slot: WearableSlot) => {
  try {
    await apiService.post('/control/player/wearing/unequip', { slot });
    await loadData();
    showMessage('Item unequipped!', 'success');
  } catch (err) {
    console.error('[WearingPanel] Failed to unequip:', err);
    showMessage('Failed to unequip item.', 'error');
  }
};

const loadData = async () => {
  try {
    const response = await apiService.get<{
      worldId: string;
      backpackItems: BackpackItemInfo[];
      wearingItems: Record<WearableSlot, WearingItemInfo | null>;
    }>('/control/player/wearing');

    worldId.value = response.worldId || '';
    backpackItems.value = response.backpackItems || [];

    // Update wearing items
    for (const slot of ALL_SLOTS) {
      wearingItems[slot] = response.wearingItems?.[slot] || null;
    }
  } catch (err) {
    console.error('[WearingPanel] Failed to load data:', err);
    error.value = 'Failed to load wearing data.';
  }
};

const showMessage = (text: string, type: 'success' | 'error') => {
  actionMessage.value = { text, type };
  setTimeout(() => { actionMessage.value = null; }, 3000);
};

onMounted(async () => {
  loading.value = true;
  await loadData();
  loading.value = false;
});
</script>

<style scoped>
.wearing-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  grid-template-rows: repeat(7, auto);
  gap: 8px;
  justify-items: center;
}

.wearing-slot {
  display: flex;
  justify-content: center;
}

.slot-box {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 6px;
  border-width: 2px;
  cursor: pointer;
  transition: all 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: rgb(55, 65, 81); /* bg-gray-700 */
}

.slot-box:hover {
  border-color: rgb(156, 163, 175); /* gray-400 */
}

.slot-label {
  position: absolute;
  top: -10px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 9px;
  color: rgb(156, 163, 175); /* gray-400 */
  background-color: rgb(31, 41, 55); /* gray-800 */
  padding: 0 4px;
  border-radius: 2px;
  white-space: nowrap;
}

.slot-icon {
  width: 40px;
  height: 40px;
  object-fit: contain;
  image-rendering: pixelated;
}
</style>
