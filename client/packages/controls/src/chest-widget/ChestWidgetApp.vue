<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-xl font-bold text-amber-400">{{ chestTitle || 'Chest' }}</h1>
            <p class="text-gray-400 text-xs mt-0.5">Transfer items between chest and backpack</p>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="state === 'LOADING'" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-3 text-sm">Loading...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="state === 'ERROR'" class="flex-1 container mx-auto px-4 py-6">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-5 text-center">
        <h2 class="text-lg font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300 text-sm">{{ error }}</p>
      </div>
    </main>

    <!-- PIN Required State -->
    <main v-else-if="state === 'PIN_REQUIRED'" class="flex-1 flex items-center justify-center">
      <div class="bg-gray-800 rounded-lg shadow-lg border border-gray-700 p-6 max-w-sm w-full mx-4">
        <div class="text-center mb-5">
          <svg class="w-14 h-14 mx-auto text-amber-400 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
          <h2 class="text-lg font-bold text-amber-400">PIN Required</h2>
          <p class="text-gray-400 text-xs mt-1">Enter PIN to access this chest</p>
        </div>
        <div class="space-y-3">
          <input
            v-model="pinInput"
            type="password"
            maxlength="20"
            placeholder="Enter PIN..."
            class="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-gray-100 placeholder-gray-500 focus:outline-none focus:border-amber-400 text-sm"
            @keyup.enter="submitPin"
          />
          <p v-if="pinError" class="text-red-400 text-xs text-center">{{ pinError }}</p>
          <button
            @click="submitPin"
            :disabled="submittingPin || !pinInput"
            class="w-full px-3 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors font-semibold text-sm"
          >
            <span v-if="submittingPin">Validating...</span>
            <span v-else>Unlock</span>
          </button>
        </div>
      </div>
    </main>

    <!-- Active State -->
    <main v-else-if="state === 'ACTIVE'" class="flex-1 container mx-auto px-3 py-4">
      <!-- Transfer Message -->
      <div v-if="transferMessage" class="mb-3 p-2 rounded-lg text-center text-xs font-medium transition-all"
           :class="transferMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ transferMessage.text }}
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <!-- Left Column: Chest Items -->
        <div>
          <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
            <h2 class="text-base font-bold text-amber-400 mb-2">
              Chest
              <span v-if="chestCapacity > 0" class="text-xs font-normal text-gray-500 ml-2">
                ({{ chestItems.length }}/{{ chestCapacity }})
              </span>
            </h2>

            <div v-if="chestItems.length === 0" class="text-center py-6 text-gray-500 text-sm">
              Chest is empty
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in chestItems"
                :key="'chest-' + item.itemId"
                class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
                :class="selectedChestItem?.itemId === item.itemId ? 'border-amber-400 shadow-lg shadow-amber-400/20' : 'border-gray-600'"
                :title="item.name"
                @click="selectChestItem(item)"
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
                <span
                  v-if="item.amount > 1"
                  class="absolute -bottom-1 -right-1 bg-amber-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                >{{ item.amount > 99 ? '99+' : item.amount }}</span>
              </div>
            </div>

            <!-- Selected Chest Item Detail -->
            <div v-if="selectedChestItem" class="mt-3 pt-3 border-t border-gray-700">
              <div class="flex items-start gap-2">
                <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                  <img
                    v-if="selectedChestItem.texture"
                    :src="getAssetUrl(selectedChestItem.texture)"
                    :alt="selectedChestItem.name"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-gray-500 text-xs">No icon</span>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="font-semibold text-amber-300 truncate text-sm">{{ selectedChestItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedChestItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedChestItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedChestItem.description }}</p>
                  <p class="text-xs text-gray-500 mt-0.5">Amount: {{ selectedChestItem.amount }}</p>
                </div>
              </div>
              <div class="mt-2 flex items-center gap-2">
                <input
                  v-model.number="chestTransferAmount"
                  type="number"
                  min="1"
                  :max="selectedChestItem.amount"
                  class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-amber-400"
                />
                <button
                  @click="transferToBackpack"
                  :disabled="transferring"
                  class="flex-1 px-3 py-1.5 bg-blue-600 text-white rounded hover:bg-blue-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium"
                >
                  To Backpack &rarr;
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Backpack Items -->
        <div>
          <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
            <h2 class="text-base font-bold text-blue-400 mb-2">Backpack</h2>

            <div v-if="backpackItems.length === 0" class="text-center py-6 text-gray-500 text-sm">
              Backpack is empty
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in backpackItems"
                :key="'bp-' + item.itemId"
                class="relative w-14 h-14 rounded border-2 transition-all flex items-center justify-center bg-gray-700"
                :class="backpackItemClass(item)"
                :title="isShortcutItem(item.itemId) ? item.name + ' (shortcut)' : item.name"
                @click="selectBackpackItem(item)"
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
                <span
                  v-if="item.count > 1"
                  class="absolute -bottom-1 -right-1 bg-blue-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                >{{ item.count > 99 ? '99+' : item.count }}</span>
              </div>
            </div>

            <!-- Selected Backpack Item Detail -->
            <div v-if="selectedBackpackItem" class="mt-3 pt-3 border-t border-gray-700">
              <div class="flex items-start gap-2">
                <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                  <img
                    v-if="selectedBackpackItem.texture"
                    :src="getAssetUrl(selectedBackpackItem.texture)"
                    :alt="selectedBackpackItem.name"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-gray-500 text-xs">No icon</span>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="font-semibold text-blue-300 truncate text-sm">{{ selectedBackpackItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedBackpackItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedBackpackItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedBackpackItem.description }}</p>
                  <p class="text-xs text-gray-500 mt-0.5">Count: {{ selectedBackpackItem.count }}</p>
                </div>
              </div>
              <div class="mt-2 flex items-center gap-2">
                <input
                  v-model.number="backpackTransferAmount"
                  type="number"
                  min="1"
                  :max="selectedBackpackItem.count"
                  class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-blue-400"
                />
                <button
                  @click="transferToChest"
                  :disabled="transferring"
                  class="flex-1 px-3 py-1.5 bg-amber-600 text-white rounded hover:bg-amber-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium"
                >
                  &larr; To Chest
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface ChestItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  amount: number;
}

interface BackpackItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  count: number;
}

type WidgetState = 'LOADING' | 'ERROR' | 'PIN_REQUIRED' | 'ACTIVE';

const state = ref<WidgetState>('LOADING');
const error = ref<string | null>(null);
const transferMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const progressId = ref('');
const worldId = ref('');
const chestTitle = ref('');
const chestCapacity = ref(0);

const chestItems = ref<ChestItemInfo[]>([]);
const backpackItems = ref<BackpackItemInfo[]>([]);
const shortcutItemIds = ref<Set<string>>(new Set());

const selectedChestItem = ref<ChestItemInfo | null>(null);
const selectedBackpackItem = ref<BackpackItemInfo | null>(null);
const chestTransferAmount = ref(1);
const backpackTransferAmount = ref(1);

const transferring = ref(false);
const pinInput = ref('');
const pinError = ref('');
const submittingPin = ref(false);

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !worldId.value) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
};

const showMessage = (text: string, type: 'success' | 'error') => {
  transferMessage.value = { text, type };
  setTimeout(() => { transferMessage.value = null; }, 3000);
};

const selectChestItem = (item: ChestItemInfo) => {
  selectedBackpackItem.value = null;
  if (selectedChestItem.value?.itemId === item.itemId) {
    selectedChestItem.value = null;
  } else {
    selectedChestItem.value = item;
    chestTransferAmount.value = item.amount;
  }
};

const isShortcutItem = (itemId: string): boolean => shortcutItemIds.value.has(itemId);

const backpackItemClass = (item: BackpackItemInfo): string => {
  if (isShortcutItem(item.itemId)) return 'border-red-500 opacity-70 cursor-not-allowed';
  if (selectedBackpackItem.value?.itemId === item.itemId) return 'border-blue-400 shadow-lg shadow-blue-400/20 cursor-pointer hover:border-gray-500';
  return 'border-gray-600 cursor-pointer hover:border-gray-500';
};

const selectBackpackItem = (item: BackpackItemInfo) => {
  if (isShortcutItem(item.itemId)) return;
  selectedChestItem.value = null;
  if (selectedBackpackItem.value?.itemId === item.itemId) {
    selectedBackpackItem.value = null;
  } else {
    selectedBackpackItem.value = item;
    backpackTransferAmount.value = item.count;
  }
};

const loadChest = async () => {
  try {
    const response = await apiService.get<{
      worldId: string;
      chest: { name: string; title: string; capacity: number; items?: ChestItemInfo[] };
      backpack?: { items: BackpackItemInfo[] };
      shortcutItemIds?: string[];
      accessGranted: boolean;
      requiresPin: boolean;
    }>(`/control/player/chest-widget?progressId=${encodeURIComponent(progressId.value)}`);

    worldId.value = response.worldId || '';
    chestTitle.value = response.chest.title || response.chest.name;
    chestCapacity.value = response.chest.capacity || 0;

    if (response.accessGranted) {
      chestItems.value = response.chest.items || [];
      backpackItems.value = response.backpack?.items || [];
      shortcutItemIds.value = new Set(response.shortcutItemIds || []);
      state.value = 'ACTIVE';
    } else if (response.requiresPin) {
      state.value = 'PIN_REQUIRED';
    } else {
      error.value = 'Access denied';
      state.value = 'ERROR';
    }
  } catch (err: any) {
    console.error('[ChestWidget] Failed to load chest:', err);
    error.value = err?.response?.data?.error || 'Failed to load chest data.';
    state.value = 'ERROR';
  }
};

const submitPin = async () => {
  if (!pinInput.value || submittingPin.value) return;
  submittingPin.value = true;
  pinError.value = '';

  try {
    await apiService.post('/control/player/chest-widget/pin', {
      progressId: progressId.value,
      pin: pinInput.value,
    });

    state.value = 'LOADING';
    await loadChest();
  } catch (err: any) {
    console.error('[ChestWidget] PIN validation failed:', err);
    pinError.value = err?.response?.data?.error || 'Invalid PIN';
  } finally {
    submittingPin.value = false;
  }
};

const transferToBackpack = async () => {
  if (!selectedChestItem.value || transferring.value) return;
  const amount = Math.max(1, Math.min(chestTransferAmount.value, selectedChestItem.value.amount));
  transferring.value = true;

  try {
    await apiService.post('/control/player/chest-widget/to-backpack', {
      progressId: progressId.value,
      itemId: selectedChestItem.value.itemId,
      amount,
    });

    selectedChestItem.value = null;
    showMessage(`Transferred ${amount} item(s) to backpack`, 'success');
    await loadChest();
  } catch (err: any) {
    console.error('[ChestWidget] Transfer to backpack failed:', err);
    showMessage(err?.response?.data?.error || 'Transfer failed', 'error');
  } finally {
    transferring.value = false;
  }
};

const transferToChest = async () => {
  if (!selectedBackpackItem.value || transferring.value) return;
  const amount = Math.max(1, Math.min(backpackTransferAmount.value, selectedBackpackItem.value.count));
  transferring.value = true;

  try {
    await apiService.post('/control/player/chest-widget/to-chest', {
      progressId: progressId.value,
      itemId: selectedBackpackItem.value.itemId,
      amount,
    });

    selectedBackpackItem.value = null;
    showMessage(`Transferred ${amount} item(s) to chest`, 'success');
    await loadChest();
  } catch (err: any) {
    console.error('[ChestWidget] Transfer to chest failed:', err);
    showMessage(err?.response?.data?.error || 'Transfer failed', 'error');
  } finally {
    transferring.value = false;
  }
};

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const paramProgressId = params.get('progressId') || '';

  if (!paramProgressId) {
    error.value = 'Missing progressId parameter';
    state.value = 'ERROR';
    return;
  }

  progressId.value = paramProgressId;
  await loadChest();
});
</script>
