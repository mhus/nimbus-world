<template>
  <div class="space-y-4">
    <!-- Search -->
    <div class="form-control">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search items..."
        class="input input-bordered"
        @input="handleSearch"
      />
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Items Grid -->
    <div v-else-if="items.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="item in items"
        :key="item.itemId"
        class="card bg-base-200 shadow-md hover:shadow-lg transition-shadow cursor-pointer"
        @click="$emit('select', item.itemId)"
      >
        <div class="card-body p-4">
          <h3 class="card-title text-sm">{{ item.name }}</h3>
          <p class="text-xs opacity-70">{{ item.itemId }}</p>

          <div class="card-actions justify-end mt-2">
            <button
              class="btn btn-xs btn-ghost"
              title="Duplicate"
              @click.stop="$emit('duplicate', item.itemId)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>
            <button
              class="btn btn-xs btn-error"
              title="Delete"
              @click.stop="handleDelete(item.itemId)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="text-center py-8 opacity-50">
      <p>No items found</p>
      <p class="text-sm mt-2">Create a new item to get started</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { ItemApiService } from '../services/itemApiService';
import type { ItemSearchResult } from '../services/itemApiService';

const emit = defineEmits<{
  select: [itemId: string];
  duplicate: [itemId: string];
  delete: [itemId: string];
}>();

const { currentWorldId } = useWorld();

const searchQuery = ref('');
const items = ref<ItemSearchResult[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

async function loadItems() {
  if (!currentWorldId.value) {
    items.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    items.value = await ItemApiService.searchItems(searchQuery.value, currentWorldId.value);
  } catch (e: any) {
    error.value = e.message || 'Failed to load items';
    console.error('Failed to load items:', e);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  loadItems();
}

async function handleDelete(itemId: string) {
  if (!confirm(`Delete item "${itemId}"?`)) {
    return;
  }

  if (!currentWorldId.value) {
    return;
  }

  try {
    await ItemApiService.deleteItem(itemId, currentWorldId.value);
    await loadItems();
  } catch (e: any) {
    error.value = e.message || 'Failed to delete item';
    console.error('Failed to delete item:', e);
  }
}

// Watch for world changes
watch(currentWorldId, () => {
  loadItems();
}, { immediate: true });
</script>
