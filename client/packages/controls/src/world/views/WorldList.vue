<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search worlds by name or worldId..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <button class="btn btn-primary" @click="handleCreate">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create World
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
      <label class="label cursor-pointer gap-2">
        <input
          v-model="filterPublic"
          type="checkbox"
          class="checkbox checkbox-sm"
        />
        <span class="label-text">Show only public</span>
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
    <div v-else-if="!loading && filteredWorlds.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No worlds found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first world to get started</p>
    </div>

    <!-- Worlds Grid -->
    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="world in paginatedWorlds"
          :key="world.worldId"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="handleSelect(world.worldId)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-base truncate" :title="world.name">
              {{ world.name }}
            </h3>
            <div class="space-y-2">
              <div class="text-xs text-base-content/70 truncate" :title="world.worldId">
                ID: {{ world.worldId }}
              </div>
              <div class="flex items-center gap-2">
                <span
                  class="badge badge-sm"
                  :class="world.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ world.enabled ? 'Enabled' : 'Disabled' }}
                </span>
                <span
                  v-if="world.publicFlag"
                  class="badge badge-sm badge-info"
                >
                  Public
                </span>
              </div>
              <div v-if="world.description" class="text-xs text-base-content/70 line-clamp-2">
                {{ world.description }}
              </div>
              <div class="text-xs text-base-content/70">
                <span class="font-medium">Ground:</span> Y{{ world.groundLevel }}
              </div>
            </div>
            <div class="card-actions justify-end mt-2">
              <button
                class="btn btn-ghost btn-xs"
                @click.stop="handleSelect(world.worldId)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-info"
                @click.stop="handleCreateZone(world.worldId, world.name)"
                title="Create Zone"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-error"
                @click.stop="handleDelete(world.worldId, world.name)"
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
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredWorlds.length) }} of {{ filteredWorlds.length }} worlds
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

    <!-- Create Zone Modal -->
    <dialog ref="zoneModal" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Create Zone</h3>
        <p class="py-4">Create a zone for world: <strong>{{ zoneSourceWorldName }}</strong></p>
        <div class="form-control">
          <label class="label">
            <span class="label-text">Zone Name</span>
          </label>
          <input
            v-model="zoneName"
            type="text"
            placeholder="Enter zone name..."
            class="input input-bordered"
            @keyup.enter="handleConfirmCreateZone"
          />
        </div>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="handleCancelCreateZone">Cancel</button>
          <button class="btn btn-primary" @click="handleConfirmCreateZone" :disabled="!zoneName || zoneName.trim() === ''">
            Create Zone
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button>close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { worldServiceFrontend, type World } from '../services/WorldServiceFrontend';

const emit = defineEmits<{
  select: [world: World];
  create: [];
}>();

const { currentRegionId } = useRegion();

const worlds = ref<World[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterEnabled = ref(false);
const filterPublic = ref(false);

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

// Zone creation modal
const zoneModal = ref<HTMLDialogElement | null>(null);
const zoneName = ref('');
const zoneSourceWorldId = ref('');
const zoneSourceWorldName = ref('');

const filteredWorlds = computed(() => {
  let result = worlds.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(w =>
      w.name.toLowerCase().includes(query) ||
      w.worldId.toLowerCase().includes(query) ||
      (w.description && w.description.toLowerCase().includes(query))
    );
  }

  if (filterEnabled.value) {
    result = result.filter(w => w.enabled);
  }

  if (filterPublic.value) {
    result = result.filter(w => w.publicFlag);
  }

  return result;
});

const paginatedWorlds = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredWorlds.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredWorlds.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const loadWorlds = async () => {
  if (!currentRegionId.value) {
    worlds.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    console.log('[WorldList] Loading worlds for region:', currentRegionId.value);
    worlds.value = await worldServiceFrontend.listWorlds(currentRegionId.value);
    console.log('[WorldList] Loaded worlds:', worlds.value.length);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load worlds';
    console.error('[WorldList] Failed to load worlds:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
};

const handleCreate = () => {
  emit('create');
};

const handleSelect = (worldId: string) => {
  const world = worlds.value.find(w => w.worldId === worldId);
  if (world) {
    emit('select', world);
  }
};

const handleDelete = async (worldId: string, name: string) => {
  if (!confirm(`Are you sure you want to delete world "${name}"?`)) {
    return;
  }

  if (!currentRegionId.value) {
    return;
  }

  try {
    await worldServiceFrontend.deleteWorld(currentRegionId.value, worldId);
    await loadWorlds();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete world';
    console.error('[WorldList] Failed to delete world:', e);
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

const handleCreateZone = (worldId: string, name: string) => {
  zoneSourceWorldId.value = worldId;
  zoneSourceWorldName.value = name;
  zoneName.value = '';
  zoneModal.value?.showModal();
};

const handleCancelCreateZone = () => {
  zoneName.value = '';
  zoneSourceWorldId.value = '';
  zoneSourceWorldName.value = '';
  zoneModal.value?.close();
};

const handleConfirmCreateZone = async () => {
  if (!zoneName.value || zoneName.value.trim() === '') {
    return;
  }

  if (!currentRegionId.value) {
    return;
  }

  try {
    loading.value = true;
    await worldServiceFrontend.createZone(
      currentRegionId.value,
      zoneSourceWorldId.value,
      zoneName.value.trim()
    );

    // Close modal
    handleCancelCreateZone();

    // Reload worlds list
    await loadWorlds();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to create zone';
    console.error('[WorldList] Failed to create zone:', e);
  } finally {
    loading.value = false;
  }
};

// Watch for region changes
watch(currentRegionId, () => {
  currentPage.value = 1;
  loadWorlds();
}, { immediate: true });
</script>
