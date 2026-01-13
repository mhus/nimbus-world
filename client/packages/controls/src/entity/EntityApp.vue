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
        <h1 class="text-xl font-bold px-4">Nimbus Entity Editor</h1>
      </div>
      <div class="flex-none">
        <!-- World Selector -->
        <WorldSelector />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <!-- Show message if no world selected -->
      <div v-if="!currentWorldId" class="alert alert-info">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>Please select a world from the dropdown above</span>
      </div>

      <!-- Entity List or Editor -->
      <div v-else>
        <EntityList
          v-if="!selectedEntity"
          @select="handleEntitySelect"
          @create="handleCreateNew"
        />
        <EntityEditor
          v-else
          :entity="selectedEntity"
          @back="handleBack"
          @saved="handleSaved"
        />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import WorldSelector from '@material/components/WorldSelector.vue';
import EntityList from './views/EntityList.vue';
import EntityEditor from './views/EntityEditor.vue';
import { entityService, type EntityData } from './services/EntityService';

const { currentWorldId } = useWorld();

// Read id from URL query parameter
const getIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
};

const selectedEntity = ref<EntityData | 'new' | null>(null);
const urlEntityId = getIdFromUrl();

// Load entity from URL if provided
const loadEntityFromUrl = async () => {
  if (!urlEntityId || !currentWorldId.value) return;

  try {
    const publicData = await entityService.getEntity(currentWorldId.value, urlEntityId);
    selectedEntity.value = {
      entityId: urlEntityId,
      publicData,
      worldId: currentWorldId.value,
      chunk: '',
      modelId: publicData.model || '',
      enabled: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
  } catch (e) {
    console.error('[EntityApp] Failed to load entity from URL:', e);
  }
};

const handleEntitySelect = (entity: EntityData) => {
  selectedEntity.value = entity;
};

const handleCreateNew = () => {
  selectedEntity.value = 'new';
};

const handleBack = () => {
  selectedEntity.value = null;
};

const handleSaved = () => {
  selectedEntity.value = null;
};

// Watch for world changes and load entity if URL param exists
watch(currentWorldId, () => {
  if (urlEntityId && currentWorldId.value && !selectedEntity.value) {
    loadEntityFromUrl();
  }
}, { immediate: true });
</script>
