<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <!-- Header -->
    <header class="bg-blue-600 text-white shadow-lg">
      <div class="container mx-auto px-4 py-6">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-3xl font-bold">Nimbus Panels</h1>
            <p class="text-blue-100 mt-2">Panel navigation and management</p>
          </div>
          <div class="flex gap-2">
            <a href="/controls/index.html" class="p-2 rounded bg-blue-700 hover:bg-blue-800 transition-colors" title="Back to Home">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-8">
      <div class="max-w-4xl mx-auto space-y-2">
        <!-- Panel Strip -->
        <div
          v-for="panel in panels"
          :key="panel.id"
          class="bg-white rounded-lg shadow-md hover:shadow-lg transition-all cursor-pointer"
          @click="navigateToPanel(panel)"
        >
          <div class="p-4 flex items-center justify-between">
            <div class="flex items-center gap-4">
              <div class="w-2 h-12 rounded" :style="{ backgroundColor: panel.color }"></div>
              <div>
                <h2 class="text-xl font-bold text-gray-800">{{ panel.name }}</h2>
                <p v-if="panel.description" class="text-sm text-gray-600">{{ panel.description }}</p>
              </div>
            </div>
            <svg class="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="panels.length === 0" class="text-center py-12">
          <svg class="w-16 h-16 mx-auto text-gray-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
          </svg>
          <p class="text-gray-600 text-lg">No panels available</p>
          <p class="text-gray-500 text-sm mt-2">Add panels to the configuration to see them here</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

interface Panel {
  id: string;
  name: string;
  description?: string;
  url: string;
  color: string;
}

// Define available panels
const panels = ref<Panel[]>([
  {
    id: 'editor-shortcut-panel',
    name: 'Editor Shortcut Panel',
    description: 'Manage editor shortcuts for player',
    url: './editor-shortcut-panel.html',
    color: '#8B5CF6' // purple
  }
]);

/**
 * Navigate to a panel
 */
const navigateToPanel = (panel: Panel) => {
  window.location.href = panel.url;
};
</script>
