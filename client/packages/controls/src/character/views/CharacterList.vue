<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search characters by name or user..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <input
          v-model="filterUserId"
          type="text"
          placeholder="Filter by User ID"
          class="input input-bordered"
        />
        <button class="btn btn-primary" @click="handleCreate">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Character
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading && filteredCharacters.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No characters found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first character to get started</p>
    </div>

    <!-- Characters Grid -->
    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="character in paginatedCharacters"
          :key="character.id"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="handleSelect(character.id)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-base truncate" :title="character.name">
              {{ character.name }}
            </h3>
            <div class="space-y-2">
              <div class="text-sm text-base-content/70 truncate" :title="character.display">
                {{ character.display }}
              </div>
              <div class="flex items-center gap-2">
                <span class="badge badge-outline badge-sm">
                  {{ character.userId }}
                </span>
              </div>
              <div class="text-xs text-base-content/70">
                <span class="font-medium">Created:</span>
                {{ formatDate(character.createdAt) }}
              </div>
              <div class="text-xs text-base-content/70">
                <span class="font-medium">Skills:</span> {{ Object.keys(character.skills || {}).length }}
              </div>
            </div>
            <div class="card-actions justify-end mt-2">
              <button
                class="btn btn-ghost btn-xs"
                @click.stop="handleSelect(character.id)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-error"
                @click.stop="handleDelete(character.id, character.userId, character.name)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredCharacters.length) }} of {{ filteredCharacters.length }} characters
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
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { characterService, type Character } from '../services/CharacterService';

const emit = defineEmits<{
  select: [character: Character];
  create: [];
}>();

const { currentRegionId } = useRegion();

const characters = ref<Character[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterUserId = ref('');

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

const filteredCharacters = computed(() => {
  let result = characters.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(c =>
      c.name.toLowerCase().includes(query) ||
      c.display.toLowerCase().includes(query) ||
      c.userId.toLowerCase().includes(query)
    );
  }

  return result;
});

const paginatedCharacters = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredCharacters.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredCharacters.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const loadCharacters = async () => {
  if (!currentRegionId.value) {
    characters.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    console.log('[CharacterList] Loading characters for region:', currentRegionId.value, 'userId:', filterUserId.value || 'ALL');
    // If filterUserId is empty, load all characters in region
    // If filterUserId is set, load only that user's characters
    characters.value = await characterService.listCharacters(
      currentRegionId.value,
      filterUserId.value || undefined
    );
    console.log('[CharacterList] Loaded characters:', characters.value.length);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load characters';
    console.error('[CharacterList] Failed to load characters:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  // Reset to first page when searching
  currentPage.value = 1;
};

const handleCreate = () => {
  emit('create');
};

const handleSelect = (id: string) => {
  const character = characters.value.find(c => c.id === id);
  if (character) {
    emit('select', character);
  }
};

const handleDelete = async (id: string, userId: string, name: string) => {
  if (!confirm(`Are you sure you want to delete character "${name}"?`)) {
    return;
  }

  if (!currentRegionId.value) {
    return;
  }

  try {
    await characterService.deleteCharacter(currentRegionId.value, id, userId, name);
    await loadCharacters();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete character';
    console.error('Failed to delete character:', e);
  }
};

const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
  }
};

const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
  }
};

const formatDate = (date: string) => {
  return new Date(date).toLocaleDateString();
};

// Watch for region or user changes
watch([currentRegionId, filterUserId], () => {
  currentPage.value = 1;
  loadCharacters();
}, { immediate: true });
</script>
