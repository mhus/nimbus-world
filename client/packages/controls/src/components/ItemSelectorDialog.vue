<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-2xl">
      <h3 class="font-bold text-lg mb-4">Select Item</h3>

      <!-- Search -->
      <div class="form-control mb-4">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search items..."
          class="input input-bordered w-full"
        />
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-8">
        <span class="loading loading-spinner loading-lg"></span>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="alert alert-error">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
        <span>{{ error }}</span>
      </div>

      <!-- Item List -->
      <div v-else class="max-h-96 overflow-y-auto">
        <div v-if="items.length === 0" class="text-center py-8 text-base-content/70">
          No items found
        </div>
        <div v-else class="grid grid-cols-1 gap-2">
          <button
            v-for="item in items"
            :key="item.itemId"
            class="btn btn-outline btn-sm justify-start"
            :class="{ 'btn-primary': item.itemId === currentItemId }"
            @click="handleSelect(item.itemId)"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
            <span class="flex-1 text-left">
              <span class="font-mono text-xs">{{ item.itemId }}</span>
              <span v-if="item.name" class="text-xs text-base-content/70 ml-2">- {{ item.name }}</span>
            </span>
            <span v-if="item.itemId === currentItemId" class="badge badge-sm badge-primary">Current</span>
          </button>
        </div>
      </div>

      <!-- Actions -->
      <div class="modal-action">
        <button class="btn btn-ghost" @click="handleClose">
          Cancel
        </button>
      </div>
    </div>
    <div class="modal-backdrop" @click="handleClose"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { ItemApiService, type ItemSearchResult } from '../item/services/itemApiService';

interface Props {
  worldId: string;
  currentItemId?: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  select: [itemId: string];
}>();

const items = ref<ItemSearchResult[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');

let debounceTimer: number | null = null;

const loadItems = async (query?: string) => {
  loading.value = true;
  error.value = null;

  try {
    // Load items with server-side filtering
    const results = await ItemApiService.searchItems(query || '', props.worldId);
    items.value = results;
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load items';
    console.error('[ItemSelectorDialog] Failed to load items:', e);
  } finally {
    loading.value = false;
  }
};

// Watch searchQuery and reload with debounce
watch(searchQuery, (newQuery) => {
  if (debounceTimer !== null) {
    clearTimeout(debounceTimer);
  }

  debounceTimer = setTimeout(() => {
    loadItems(newQuery || undefined);
  }, 300) as unknown as number;
});

const handleSelect = (itemId: string) => {
  emit('select', itemId);
};

const handleClose = () => {
  emit('close');
};

onMounted(() => {
  loadItems();
});
</script>
