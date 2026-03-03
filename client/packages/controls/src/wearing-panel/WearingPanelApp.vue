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
              <p v-if="selectedItem.wearableSlots?.length" class="text-xs text-gray-500 mt-1">Slots: {{ selectedItem.wearableSlots.join(', ') }}</p>
              <p class="text-xs text-gray-500 mt-2 italic">Click a highlighted wearing slot to equip this item</p>
            </div>
          </div>
        </div>

        <!-- Right Column: Wearing Slots (Paper Doll) -->
        <div class="lg:col-span-2">
          <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700">
            <h2 class="text-lg font-bold text-emerald-400 mb-3">Wearing Slots</h2>

            <div class="wearing-grid mx-auto" style="max-width: 380px;">
              <!-- Row 1: HEAD -->
              <div class="wearing-slot" style="grid-column: 3; grid-row: 1;">
                <div
                  class="slot-box"
                  :class="slotClass('HEAD')"
                  @click="onSlotClick('HEAD')"
                  title="Head"
                >
                  <span class="slot-label">HEAD</span>
                  <img
                    v-if="wearingItems.HEAD?.texture"
                    :src="getAssetUrl(wearingItems.HEAD.texture)"
                    alt="Head"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.HEAD" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.HEAD.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <!-- Row 2: NECK -->
              <div class="wearing-slot" style="grid-column: 3; grid-row: 2;">
                <div
                  class="slot-box"
                  :class="slotClass('NECK')"
                  @click="onSlotClick('NECK')"
                  title="Neck"
                >
                  <span class="slot-label">NECK</span>
                  <img
                    v-if="wearingItems.NECK?.texture"
                    :src="getAssetUrl(wearingItems.NECK.texture)"
                    alt="Neck"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.NECK" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.NECK.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <!-- Row 3: LEFT_RING, BODY, RIGHT_RING, HANDS -->
              <div class="wearing-slot" style="grid-column: 2; grid-row: 3;">
                <div
                  class="slot-box"
                  :class="slotClass('LEFT_RING')"
                  @click="onSlotClick('LEFT_RING')"
                  title="Left Ring"
                >
                  <span class="slot-label">L.RING</span>
                  <img
                    v-if="wearingItems.LEFT_RING?.texture"
                    :src="getAssetUrl(wearingItems.LEFT_RING.texture)"
                    alt="Left Ring"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.LEFT_RING" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.LEFT_RING.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <div class="wearing-slot" style="grid-column: 3; grid-row: 3;">
                <div
                  class="slot-box"
                  :class="slotClass('BODY')"
                  @click="onSlotClick('BODY')"
                  title="Body"
                >
                  <span class="slot-label">BODY</span>
                  <img
                    v-if="wearingItems.BODY?.texture"
                    :src="getAssetUrl(wearingItems.BODY.texture)"
                    alt="Body"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.BODY" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.BODY.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <div class="wearing-slot" style="grid-column: 4; grid-row: 3;">
                <div
                  class="slot-box"
                  :class="slotClass('RIGHT_RING')"
                  @click="onSlotClick('RIGHT_RING')"
                  title="Right Ring"
                >
                  <span class="slot-label">R.RING</span>
                  <img
                    v-if="wearingItems.RIGHT_RING?.texture"
                    :src="getAssetUrl(wearingItems.RIGHT_RING.texture)"
                    alt="Right Ring"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.RIGHT_RING" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.RIGHT_RING.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <div class="wearing-slot" style="grid-column: 5; grid-row: 3;">
                <div
                  class="slot-box"
                  :class="slotClass('HANDS')"
                  @click="onSlotClick('HANDS')"
                  title="Hands"
                >
                  <span class="slot-label">HANDS</span>
                  <img
                    v-if="wearingItems.HANDS?.texture"
                    :src="getAssetUrl(wearingItems.HANDS.texture)"
                    alt="Hands"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.HANDS" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.HANDS.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <!-- Row 4: LEGS -->
              <div class="wearing-slot" style="grid-column: 3; grid-row: 4;">
                <div
                  class="slot-box"
                  :class="slotClass('LEGS')"
                  @click="onSlotClick('LEGS')"
                  title="Legs"
                >
                  <span class="slot-label">LEGS</span>
                  <img
                    v-if="wearingItems.LEGS?.texture"
                    :src="getAssetUrl(wearingItems.LEGS.texture)"
                    alt="Legs"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.LEGS" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.LEGS.name?.substring(0, 6) }}</span>
                  <span v-else class="text-gray-600 text-lg">-</span>
                </div>
              </div>

              <!-- Row 5: FEET -->
              <div class="wearing-slot" style="grid-column: 3; grid-row: 5;">
                <div
                  class="slot-box"
                  :class="slotClass('FEET')"
                  @click="onSlotClick('FEET')"
                  title="Feet"
                >
                  <span class="slot-label">FEET</span>
                  <img
                    v-if="wearingItems.FEET?.texture"
                    :src="getAssetUrl(wearingItems.FEET.texture)"
                    alt="Feet"
                    class="slot-icon"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="wearingItems.FEET" class="text-xs text-gray-400 text-center leading-tight px-1">{{ wearingItems.FEET.name?.substring(0, 6) }}</span>
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
  wearableSlots: string[] | null;
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

type WearableSlot = 'HEAD' | 'BODY' | 'LEGS' | 'FEET' | 'HANDS' | 'NECK' | 'LEFT_RING' | 'RIGHT_RING';

const loading = ref(true);
const error = ref<string | null>(null);
const actionMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const worldId = ref('');
const backpackItems = ref<BackpackItemInfo[]>([]);
const wearingItems = reactive<Record<WearableSlot, WearingItemInfo | null>>({
  HEAD: null,
  NECK: null,
  BODY: null,
  LEFT_RING: null,
  RIGHT_RING: null,
  HANDS: null,
  LEGS: null,
  FEET: null,
});
const selectedItem = ref<BackpackItemInfo | null>(null);

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !worldId.value) return '';
  return `${apiService.getBaseUrl()}/control/worlds/${worldId.value}/assets/${texturePath}`;
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
  const slots = selectedItem.value.wearableSlots;
  if (!slots || slots.length === 0) return true; // no restriction = all slots allowed
  return slots.includes(slot);
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
    const slots: WearableSlot[] = ['HEAD', 'NECK', 'BODY', 'LEFT_RING', 'RIGHT_RING', 'HANDS', 'LEGS', 'FEET'];
    for (const slot of slots) {
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
  grid-template-columns: repeat(5, 1fr);
  grid-template-rows: repeat(5, auto);
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
