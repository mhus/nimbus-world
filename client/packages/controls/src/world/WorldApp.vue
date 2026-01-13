<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <header class="navbar bg-base-200 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <h1 class="text-xl font-bold px-4">Nimbus World Editor</h1>
      </div>
      <div class="flex-none">
        <!-- Region Selector -->
        <RegionSelector />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <!-- Show message if no region selected and on Worlds tab -->
      <div v-if="!currentRegionId && activeTab === 'worlds'" class="alert alert-info">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>Please select a region from the dropdown above</span>
      </div>

      <!-- Tabs Navigation -->
      <div v-else-if="!selectedWorld && !selectedCollection" class="tabs tabs-boxed mb-6">
        <a
          class="tab"
          :class="{ 'tab-active': activeTab === 'worlds' }"
          @click="handleTabChange('worlds')"
        >
          Worlds
        </a>
        <a
          class="tab"
          :class="{ 'tab-active': activeTab === 'instances' }"
          @click="handleTabChange('instances')"
        >
          Instances
        </a>
        <a
          class="tab"
          :class="{ 'tab-active': activeTab === 'collections' }"
          @click="handleTabChange('collections')"
        >
          Collections
        </a>
      </div>

      <!-- Tab Content -->
      <div>
        <!-- Worlds Tab -->
        <div v-if="activeTab === 'worlds' && !selectedWorld">
          <WorldList
            v-if="currentRegionId"
            @select="handleWorldSelect"
            @create="handleCreateNewWorld"
          />
        </div>

        <!-- Instances Tab -->
        <div v-else-if="activeTab === 'instances'">
          <InstanceList />
        </div>

        <!-- Collections Tab -->
        <div v-else-if="activeTab === 'collections' && !selectedCollection">
          <CollectionList
            @select="handleCollectionSelect"
            @create="handleCreateNewCollection"
          />
        </div>

        <!-- World Editor -->
        <WorldEditor
          v-if="selectedWorld"
          :world="selectedWorld"
          @back="handleBackWorld"
          @saved="handleSavedWorld"
        />

        <!-- Collection Editor -->
        <CollectionEditor
          v-if="selectedCollection"
          :collection="selectedCollection"
          @back="handleBackCollection"
          @saved="handleSavedCollection"
        />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRegion } from '@/composables/useRegion';
import RegionSelector from './components/RegionSelector.vue';
import WorldList from './views/WorldList.vue';
import WorldEditor from './views/WorldEditor.vue';
import InstanceList from './views/InstanceList.vue';
import CollectionList from './views/CollectionList.vue';
import CollectionEditor from './views/CollectionEditor.vue';
import { worldServiceFrontend, type World } from './services/WorldServiceFrontend';
import { type Collection } from './services/CollectionServiceFrontend';

const { currentRegionId } = useRegion();

// Active tab state
const activeTab = ref<'worlds' | 'instances' | 'collections'>('worlds');

// Read id from URL query parameter
const getIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
};

const selectedWorld = ref<World | 'new' | null>(null);
const selectedCollection = ref<Collection | 'new' | null>(null);
const urlWorldId = getIdFromUrl();

// Load world from URL parameter if provided
const loadWorldFromUrl = async () => {
  if (!urlWorldId || !currentRegionId.value) return;

  try {
    const world = await worldServiceFrontend.getWorld(currentRegionId.value, urlWorldId);
    selectedWorld.value = world;
    activeTab.value = 'worlds';
  } catch (e) {
    console.error('[WorldApp] Failed to load world from URL:', e);
  }
};

// Tab change handler
const handleTabChange = (tab: 'worlds' | 'instances' | 'collections') => {
  activeTab.value = tab;
  selectedWorld.value = null;
  selectedCollection.value = null;
};

// World handlers
const handleWorldSelect = (world: World) => {
  selectedWorld.value = world;
};

const handleCreateNewWorld = () => {
  selectedWorld.value = 'new';
};

const handleBackWorld = () => {
  selectedWorld.value = null;
};

const handleSavedWorld = () => {
  selectedWorld.value = null;
};

// Collection handlers
const handleCollectionSelect = (collection: Collection) => {
  selectedCollection.value = collection;
};

const handleCreateNewCollection = () => {
  selectedCollection.value = 'new';
};

const handleBackCollection = () => {
  selectedCollection.value = null;
};

const handleSavedCollection = () => {
  selectedCollection.value = null;
};

// Watch for region changes and load world if URL param exists
watch(currentRegionId, () => {
  if (urlWorldId && currentRegionId.value && !selectedWorld.value) {
    loadWorldFromUrl();
  }
}, { immediate: true });
</script>
