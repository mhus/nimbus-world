<template>
  <div class="space-y-4">
    <!-- Search Bar -->
    <div class="form-control">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search ItemTypes..."
        class="input input-bordered w-full"
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

    <!-- ItemType List -->
    <div v-else>
      <div v-if="itemTypes.length === 0" class="text-center text-base-content/50 py-8">
        No ItemTypes found
      </div>

      <div v-else class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="itemType in itemTypes"
          :key="itemType.type"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="selectItemType(itemType.type)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-sm">{{ itemType.name || itemType.type }}</h3>
            <p v-if="itemType.description" class="text-xs text-base-content/70 line-clamp-2">
              {{ itemType.description }}
            </p>
            <div class="flex flex-wrap gap-1 text-xs text-base-content/50 mt-2">
              <span class="badge badge-xs">{{ itemType.type }}</span>
              <span v-if="itemType.modifier?.texture" class="badge badge-xs badge-primary">
                Texture
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Results Count -->
    <div v-if="itemTypes.length > 0" class="text-sm text-base-content/50 text-center">
      Showing {{ itemTypes.length }} ItemType(s)
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ItemType } from '@nimbus/shared';
import { searchItemTypes } from '../services/itemTypeApiService';
import { useWorld } from '@/composables/useWorld';

const emit = defineEmits<{
  select: [itemTypeId: string];
}>();

const { currentWorldId } = useWorld();

const searchQuery = ref('');
const itemTypes = ref<ItemType[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

let searchTimeout: NodeJS.Timeout | null = null;

async function loadItemTypes(query?: string) {
  if (!currentWorldId.value) {
    itemTypes.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    itemTypes.value = await searchItemTypes(query, currentWorldId.value);
  } catch (err) {
    error.value = (err as Error).message;
    console.error('Failed to load ItemTypes:', err);
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  // Debounce search
  if (searchTimeout) {
    clearTimeout(searchTimeout);
  }

  searchTimeout = setTimeout(() => {
    loadItemTypes(searchQuery.value || undefined);
  }, 300);
}

function selectItemType(itemTypeId: string) {
  console.info('[ItemTypeListView] selectItemType called with:', itemTypeId);
  emit('select', itemTypeId);
  console.info('[ItemTypeListView] emit completed');
}

// Watch for world changes
watch(currentWorldId, () => {
  loadItemTypes();
}, { immediate: true });
</script>
