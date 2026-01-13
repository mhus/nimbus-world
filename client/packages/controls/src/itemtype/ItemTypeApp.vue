<template>
  <div class="min-h-screen flex flex-col bg-base-100">
    <!-- Header -->
    <header class="navbar bg-base-300 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <h1 class="text-xl font-bold ml-4">ItemType Editor</h1>
      </div>
      <div class="flex-none gap-2 mr-4">
        <!-- World Selector -->
        <WorldSelector />
        <button
          v-if="!selectedItemTypeId"
          class="btn btn-sm btn-primary"
          @click="createNewItemType"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Add
        </button>
        <button
          v-if="selectedItemTypeId"
          class="btn btn-sm btn-ghost"
          @click="closeEditor"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
          Close
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 flex overflow-hidden">
      <!-- ItemType List (Left Panel) -->
      <div v-if="!selectedItemTypeId" class="flex-1 p-6 overflow-auto">
        <ItemTypeListView
          @select="openItemType"
        />
      </div>

      <!-- ItemType Editor (Right Panel) -->
      <div v-else class="flex-1 overflow-auto">
        <ItemTypeEditorView
          :item-type-id="selectedItemTypeId"
          :is-new="isNewItemType"
          @save="saveItemType"
          @close="closeEditor"
          @delete="deleteCurrentItemType"
        />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ItemTypeListView from './views/ItemTypeListView.vue';
import ItemTypeEditorView from './views/ItemTypeEditorView.vue';
import WorldSelector from '@material/components/WorldSelector.vue';

// Read id from URL query parameter
const getIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
};

const urlItemTypeId = getIdFromUrl();
const selectedItemTypeId = ref<string | null>(urlItemTypeId);
const isNewItemType = ref(false);

function createNewItemType() {
  selectedItemTypeId.value = 'new_itemtype';
  isNewItemType.value = true;
}

function openItemType(itemTypeId: string) {
  console.info('[ItemTypeApp] openItemType called with:', itemTypeId);
  selectedItemTypeId.value = itemTypeId;
  isNewItemType.value = false;
  console.info('[ItemTypeApp] selectedItemTypeId set to:', selectedItemTypeId.value);
}

function saveItemType() {
  // Refresh list after save
  selectedItemTypeId.value = null;
  isNewItemType.value = false;
}

function closeEditor() {
  selectedItemTypeId.value = null;
  isNewItemType.value = false;
}

function deleteCurrentItemType() {
  selectedItemTypeId.value = null;
  isNewItemType.value = false;
}
</script>
