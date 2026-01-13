<template>
  <div class="space-y-4">
    <!-- Tabs -->
    <div class="tabs tabs-boxed">
      <a
        class="tab"
        :class="{ 'tab-active': activeTab === 'list' }"
        @click="activeTab = 'list'"
      >
        <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h16M4 18h16" />
        </svg>
        Liste
      </a>
      <a
        class="tab"
        :class="{ 'tab-active': activeTab === 'grid' }"
        @click="activeTab = 'grid'"
      >
        <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
        </svg>
        Grid
      </a>
    </div>

    <!-- Header with Search and Actions (only for list view) -->
    <div v-if="activeTab === 'list'" class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <SearchInput
          v-model="searchQuery"
          placeholder="Search hex grids (by name or position)..."
          @search="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <button
          class="btn btn-primary"
          @click="openCreateDialog"
        >
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          New Hex Grid
        </button>
      </div>
    </div>

    <!-- List View -->
    <div v-if="activeTab === 'list'">
      <!-- Loading State -->
      <LoadingSpinner v-if="loading && hexGrids.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && hexGrids.length === 0" class="text-center py-12">
        <p class="text-base-content/70 text-lg">No hex grids found</p>
        <p class="text-base-content/50 text-sm mt-2">Create your first hex grid to get started</p>
      </div>

      <!-- Hex Grid List -->
      <HexGridList
        v-else
        :hex-grids="filteredHexGrids"
        :loading="loading"
        @edit="openEditDialog"
        @delete="handleDelete"
        @toggle-enabled="handleToggleEnabled"
      />
    </div>

    <!-- Grid View -->
    <div v-else-if="activeTab === 'grid'">
      <HexGridVisual
        :hex-grids="hexGrids"
        :loading="loading"
        @edit="openEditDialog"
        @create="openCreateDialogAtPosition"
      />
    </div>

    <!-- Editor Dialog -->
    <HexGridEditorPanel
      v-if="isEditorOpen"
      :hex-grid="selectedHexGrid"
      :world-id="currentWorldId!"
      @close="closeEditor"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useHexGrids, type HexGridWithId } from '@/composables/useHexGrids';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import HexGridList from '@material/components/HexGridList.vue';
import HexGridEditorPanel from '@material/components/HexGridEditorPanel.vue';
import HexGridVisual from '@material/components/HexGridVisual.vue';

const { currentWorldId, loadWorlds } = useWorld();

const activeTab = ref<'list' | 'grid'>('list');

const hexGridsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useHexGrids(currentWorldId.value);
});

const hexGrids = computed(() => hexGridsComposable.value?.hexGrids.value || []);
const loading = computed(() => hexGridsComposable.value?.loading.value || false);
const error = computed(() => hexGridsComposable.value?.error.value || null);
const searchQuery = ref('');

const isEditorOpen = ref(false);
const selectedHexGrid = ref<HexGridWithId | null>(null);

// Load hex grids when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value && currentWorldId.value !== '?') {
    hexGridsComposable.value?.loadHexGrids();
  }
}, { immediate: true });

onMounted(() => {
  // Load worlds with allWithoutInstances filter for hex grid editor
  loadWorlds('allWithoutInstances');
});

/**
 * Filter hex grids based on search query
 */
const filteredHexGrids = computed(() => {
  if (!searchQuery.value) {
    return hexGrids.value;
  }

  const query = searchQuery.value.toLowerCase();
  return hexGrids.value.filter(grid => {
    return (
      grid.publicData.name?.toLowerCase().includes(query) ||
      grid.publicData.description?.toLowerCase().includes(query) ||
      grid.position.includes(query)
    );
  });
});

/**
 * Parse position string (e.g., "0:0") to q and r
 */
const parsePosition = (position: string): { q: number; r: number } => {
  const [q, r] = position.split(':').map(Number);
  return { q, r };
};

/**
 * Handle search
 */
const handleSearch = (query: string) => {
  searchQuery.value = query;
};

/**
 * Open create dialog
 */
const openCreateDialog = () => {
  selectedHexGrid.value = null;
  isEditorOpen.value = true;
};

/**
 * Open create dialog with pre-filled position
 */
const openCreateDialogAtPosition = (q: number, r: number) => {
  // We'll handle this by passing initial position to the editor
  // For now, just open the create dialog - position will be set in the form
  selectedHexGrid.value = {
    id: '',
    worldId: currentWorldId.value || '',
    position: `${q}:${r}`,
    publicData: {
      position: { q, r },
      name: '',
      description: ''
    },
    enabled: true
  } as HexGridWithId;
  isEditorOpen.value = true;
};

/**
 * Open edit dialog
 */
const openEditDialog = async (hexGrid: HexGridWithId) => {
  if (!hexGridsComposable.value) return;

  // Load fresh data from server
  const { q, r } = parsePosition(hexGrid.position);
  const freshData = await hexGridsComposable.value.loadHexGrid(q, r);

  if (freshData) {
    selectedHexGrid.value = freshData;
    isEditorOpen.value = true;
  }
};

/**
 * Close editor
 */
const closeEditor = () => {
  isEditorOpen.value = false;
  selectedHexGrid.value = null;
};

/**
 * Handle saved
 */
const handleSaved = async () => {
  if (!hexGridsComposable.value) return;

  // Reload list after save
  await hexGridsComposable.value.loadHexGrids();
  closeEditor();
};

/**
 * Handle delete
 */
const handleDelete = async (hexGrid: HexGridWithId) => {
  if (!hexGridsComposable.value) return;

  const name = hexGrid.publicData.name || hexGrid.position;
  if (!confirm(`Are you sure you want to delete hex grid "${name}"?`)) {
    return;
  }

  const { q, r } = parsePosition(hexGrid.position);
  await hexGridsComposable.value.deleteHexGrid(q, r);
};

/**
 * Handle toggle enabled
 */
const handleToggleEnabled = async (hexGrid: HexGridWithId) => {
  if (!hexGridsComposable.value) return;

  const { q, r } = parsePosition(hexGrid.position);
  if (hexGrid.enabled) {
    await hexGridsComposable.value.disableHexGrid(q, r);
  } else {
    await hexGridsComposable.value.enableHexGrid(q, r);
  }
};
</script>
