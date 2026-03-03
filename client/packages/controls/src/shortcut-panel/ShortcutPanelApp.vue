<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-amber-400">Shortcut Panel</h1>
            <p class="text-gray-400 text-sm mt-1">Assign backpack items to shortcut slots</p>
          </div>
          <div class="flex gap-2">
            <button
              v-if="hasChanges"
              @click="saveShortcuts"
              :disabled="saving"
              class="px-4 py-2 bg-amber-500 text-gray-900 rounded-lg hover:bg-amber-400 disabled:bg-gray-600 disabled:text-gray-400 transition-colors font-semibold text-sm"
            >
              <span v-if="saving">Saving...</span>
              <span v-else>Save</span>
            </button>
            <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Back to Panels">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
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
      <!-- Save Message -->
      <div v-if="saveMessage" class="mb-4 p-3 rounded-lg text-center text-sm font-medium transition-all"
           :class="saveMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ saveMessage.text }}
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Left Column: Backpack Items -->
        <div class="lg:col-span-1">
          <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700">
            <h2 class="text-lg font-bold text-amber-400 mb-3">Backpack</h2>

            <div v-if="backpackItems.length === 0" class="text-center py-8 text-gray-500">
              No items in backpack
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in backpackItems"
                :key="item.itemId"
                class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
                :class="selectedItem?.itemId === item.itemId ? 'border-amber-400 shadow-lg shadow-amber-400/20' : 'border-gray-600'"
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
                  class="absolute -bottom-1 -right-1 bg-amber-500 text-gray-900 text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
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
                  <h3 class="font-semibold text-amber-300 truncate">{{ selectedItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedItem.description" class="text-xs text-gray-500 mt-1">{{ selectedItem.description }}</p>
                  <p class="text-xs text-gray-500 mt-1">Count: {{ selectedItem.count }}</p>
                </div>
              </div>
              <p class="text-xs text-gray-500 mt-2 italic">Click a shortcut slot to assign this item</p>
            </div>
          </div>
        </div>

        <!-- Right Column: Shortcut Slots -->
        <div class="lg:col-span-2">
          <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700">
            <h2 class="text-lg font-bold text-amber-400 mb-3">Shortcut Slots</h2>

            <div v-for="row in SLOT_ROWS" :key="row.label" class="mb-4">
              <h3 class="text-sm font-semibold text-gray-400 mb-2">{{ row.label }}</h3>
              <div class="grid grid-cols-10 gap-2">
                <div
                  v-for="(key, idx) in row.keys"
                  :key="key"
                  class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex flex-col items-center justify-center bg-gray-700"
                  :class="shortcuts[key] ? 'border-amber-600' : 'border-gray-600'"
                  :title="shortcuts[key] ? shortcuts[key].name || key : key"
                  @click="onSlotClick(key)"
                >
                  <img
                    v-if="shortcuts[key]?.iconPath"
                    :src="getAssetUrl(shortcuts[key].iconPath)"
                    :alt="shortcuts[key].name || key"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else-if="!shortcuts[key]" class="text-gray-500 text-xs">-</span>
                  <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ shortcuts[key].name?.substring(0, 6) }}</span>
                  <!-- Slot label -->
                  <span class="absolute -top-2 left-1/2 -translate-x-1/2 text-[10px] text-gray-500 bg-gray-800 px-1 rounded">{{ row.labels[idx] }}</span>
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
import { ref, onMounted, computed } from 'vue';
import { apiService } from '@/services/ApiService';
import type { ShortcutDefinition } from '@nimbus/shared/types/ShortcutDefinition';

interface BackpackItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  count: number;
}

const SLOT_ROWS = [
  {
    label: 'Click',
    keys: ['click0', 'click1', 'click2', 'click3', 'click4', 'click5', 'click6', 'click7', 'click8', 'click9'],
    labels: ['C0', 'C1', 'C2', 'C3', 'C4', 'C5', 'C6', 'C7', 'C8', 'C9'],
  },
  {
    label: 'Keys',
    keys: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
    labels: ['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'],
  },
  {
    label: 'Slots',
    keys: ['slot0', 'slot1', 'slot2', 'slot3', 'slot4', 'slot5', 'slot6', 'slot7', 'slot8', 'slot9'],
    labels: ['S0', 'S1', 'S2', 'S3', 'S4', 'S5', 'S6', 'S7', 'S8', 'S9'],
  },
];

const loading = ref(true);
const saving = ref(false);
const error = ref<string | null>(null);
const saveMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const worldId = ref('');
const backpackItems = ref<BackpackItemInfo[]>([]);
const shortcuts = ref<Record<string, ShortcutDefinition>>({});
const originalShortcuts = ref<string>('{}');
const selectedItem = ref<BackpackItemInfo | null>(null);

const hasChanges = computed(() => {
  return JSON.stringify(shortcuts.value) !== originalShortcuts.value;
});

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

const onSlotClick = (slotKey: string) => {
  if (selectedItem.value) {
    // Assign selected item to slot
    shortcuts.value[slotKey] = {
      type: 'use',
      itemId: selectedItem.value.itemId,
      name: selectedItem.value.name,
      iconPath: selectedItem.value.texture || '',
      wait: 0,
    };
  } else if (shortcuts.value[slotKey]) {
    // Clear slot if no item selected
    delete shortcuts.value[slotKey];
    // Trigger reactivity
    shortcuts.value = { ...shortcuts.value };
  }
};

const loadBackpack = async () => {
  try {
    const response = await apiService.get<{ worldId: string; items: BackpackItemInfo[] }>(
      '/control/player/backpack-shortcut/backpack'
    );
    worldId.value = response.worldId || '';
    backpackItems.value = response.items || [];
  } catch (err) {
    console.error('[ShortcutPanel] Failed to load backpack:', err);
    error.value = 'Failed to load backpack items.';
  }
};

const loadShortcuts = async () => {
  try {
    const response = await apiService.get<{ shortcuts: Record<string, ShortcutDefinition> }>(
      '/control/player/backpack-shortcut'
    );
    shortcuts.value = response.shortcuts || {};
    originalShortcuts.value = JSON.stringify(shortcuts.value);
  } catch (err) {
    console.error('[ShortcutPanel] Failed to load shortcuts:', err);
    error.value = 'Failed to load shortcuts.';
  }
};

const saveShortcuts = async () => {
  saving.value = true;
  try {
    const response = await apiService.put<{ shortcuts: Record<string, ShortcutDefinition> }>(
      '/control/player/backpack-shortcut',
      { shortcuts: shortcuts.value }
    );
    shortcuts.value = response.shortcuts || shortcuts.value;
    originalShortcuts.value = JSON.stringify(shortcuts.value);
    showMessage('Shortcuts saved successfully!', 'success');
  } catch (err) {
    console.error('[ShortcutPanel] Failed to save shortcuts:', err);
    showMessage('Failed to save shortcuts. Please try again.', 'error');
  } finally {
    saving.value = false;
  }
};

const showMessage = (text: string, type: 'success' | 'error') => {
  saveMessage.value = { text, type };
  setTimeout(() => { saveMessage.value = null; }, 3000);
};

onMounted(async () => {
  loading.value = true;
  await Promise.all([loadBackpack(), loadShortcuts()]);
  loading.value = false;
});
</script>
