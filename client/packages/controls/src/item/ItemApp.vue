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
        <h1 class="text-xl font-bold ml-4">Item Editor</h1>
      </div>
      <div class="flex-none gap-2 mr-4">
        <!-- World Selector -->
        <WorldSelector />
        <button
          v-if="!selectedItemId"
          class="btn btn-sm btn-primary"
          @click="createNewItem"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          New Item
        </button>
        <button
          v-if="selectedItemId"
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
      <!-- Item List (Left Panel) -->
      <div v-if="!selectedItemId" class="flex-1 p-6 overflow-auto">
        <ItemListView
          @select="openItem"
          @duplicate="duplicateItem"
          @delete="deleteItem"
        />
      </div>

      <!-- Item Editor (Right Panel) -->
      <div v-else class="flex-1 overflow-auto">
        <ItemEditorView
          :item-id="selectedItemId"
          :is-new="isNewItem"
          @save="saveItem"
          @close="closeEditor"
          @delete="deleteCurrentItem"
        />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ItemListView from './views/ItemListView.vue';
import ItemEditorView from './views/ItemEditorView.vue';
import WorldSelector from '@material/components/WorldSelector.vue';

// Read id from URL query parameter
const getIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
};

const urlItemId = getIdFromUrl();
const selectedItemId = ref<string | null>(urlItemId);
const isNewItem = ref(false);

function createNewItem() {
  selectedItemId.value = 'new_item';
  isNewItem.value = true;
}

function openItem(itemId: string) {
  selectedItemId.value = itemId;
  isNewItem.value = false;
}

function duplicateItem(itemId: string) {
  selectedItemId.value = `${itemId}_copy`;
  isNewItem.value = true;
}

function saveItem() {
  selectedItemId.value = null;
  isNewItem.value = false;
}

function closeEditor() {
  selectedItemId.value = null;
  isNewItem.value = false;
}

function deleteItem(itemId: string) {
  console.log('Delete item:', itemId);
}

function deleteCurrentItem() {
  selectedItemId.value = null;
  isNewItem.value = false;
}
</script>
