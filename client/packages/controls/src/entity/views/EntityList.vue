<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search entities by ID or model..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <button class="btn btn-primary" @click="handleCreate">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Entity
        </button>
      </div>
    </div>

    <!-- Filter Controls -->
    <div class="flex gap-2">
      <label class="label cursor-pointer gap-2">
        <input
          v-model="filterEnabled"
          type="checkbox"
          class="checkbox checkbox-sm"
        />
        <span class="label-text">Show only enabled</span>
      </label>
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
    <div v-else-if="!loading && paginatedEntities.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No entities found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first entity to get started</p>
    </div>

    <!-- Entities Grid -->
    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="entity in paginatedEntities"
          :key="entity.entityId"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="handleSelect(entity)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-base truncate" :title="entity.publicData?.id || entity.entityId">
              {{ entity.publicData?.id || entity.entityId }}
            </h3>
            <div class="space-y-2">
              <div class="text-xs text-base-content/70 truncate" :title="entity.modelId">
                Model: {{ entity.modelId }}
              </div>
              <div class="flex items-center gap-2">
                <span
                  class="badge badge-sm"
                  :class="entity.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ entity.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </div>
              <div v-if="entity.chunk" class="text-xs text-base-content/70">
                Chunk: {{ entity.chunk }}
              </div>
            </div>
            <div class="card-actions justify-end mt-2">
              <button
                class="btn btn-ghost btn-xs"
                @click.stop="handleSelect(entity)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-error"
                @click.stop="handleDelete(entity.entityId)"
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
          Showing {{ Math.min(offset + 1, totalCount) }}-{{ Math.min(offset + currentPageSize, totalCount) }} of {{ totalCount }} entities
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
import { useWorld } from '@/composables/useWorld';
import { entityService, type EntityData } from '../services/EntityService';

const emit = defineEmits<{
  select: [entity: EntityData];
  create: [];
}>();

const { currentWorldId } = useWorld();

const entities = ref<EntityData[]>([]);
const totalCount = ref(0);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterEnabled = ref(false);

// Paging
const currentPage = ref(1);
const pageSize = ref(20);
const offset = computed(() => (currentPage.value - 1) * pageSize.value);
const currentPageSize = computed(() => entities.value.length);

const filteredEntities = computed(() => {
  let result = entities.value;

  if (filterEnabled.value) {
    result = result.filter(e => e.enabled);
  }

  return result;
});

const paginatedEntities = computed(() => {
  return filteredEntities.value;
});

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const loadEntities = async () => {
  if (!currentWorldId.value || currentWorldId.value === '?') {
    entities.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    console.log('[EntityList] Loading entities for world:', currentWorldId.value);
    const response = await entityService.listEntities(
      currentWorldId.value,
      searchQuery.value || undefined,
      offset.value,
      pageSize.value
    );
    entities.value = response.entities.map((publicData, index) => ({
      entityId: publicData.id || `entity-${offset.value + index}`,
      publicData,
      worldId: currentWorldId.value!,
      chunk: '',
      modelId: publicData.model || '',
      enabled: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }));
    totalCount.value = response.count;
    console.log('[EntityList] Loaded entities:', entities.value.length, 'total:', totalCount.value);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load entities';
    console.error('[EntityList] Failed to load entities:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadEntities();
};

const handleCreate = () => {
  emit('create');
};

const handleSelect = (entity: EntityData) => {
  emit('select', entity);
};

const handleDelete = async (entityId: string) => {
  if (!confirm(`Are you sure you want to delete entity "${entityId}"?`)) {
    return;
  }

  if (!currentWorldId.value) {
    return;
  }

  try {
    await entityService.deleteEntity(currentWorldId.value, entityId);
    await loadEntities();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete entity';
    console.error('[EntityList] Failed to delete entity:', e);
  }
};

const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
    loadEntities();
  }
};

const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
    loadEntities();
  }
};

// Watch for world changes
watch(currentWorldId, () => {
  currentPage.value = 1;
  loadEntities();
}, { immediate: true });
</script>
