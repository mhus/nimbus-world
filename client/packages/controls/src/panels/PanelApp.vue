<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-emerald-400">Nimbus Panels</h1>
            <p class="text-gray-400 text-sm mt-1">Panel navigation and management</p>
          </div>
        </div>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-8">
      <div class="max-w-4xl mx-auto space-y-2">
        <!-- Panel Strip -->
        <div
          v-for="panel in visiblePanels"
          :key="panel.id"
          class="bg-gray-800 rounded-lg shadow-md hover:shadow-lg hover:bg-gray-750 transition-all cursor-pointer border border-gray-700"
          @click="navigateToPanel(panel)"
        >
          <div class="p-4 flex items-center justify-between">
            <div class="flex items-center gap-4">
              <div class="w-2 h-12 rounded" :style="{ backgroundColor: panel.color }"></div>
              <div>
                <h2 class="text-xl font-bold text-gray-100">{{ panel.name }}</h2>
                <p v-if="panel.description" class="text-sm text-gray-400">{{ panel.description }}</p>
              </div>
            </div>
            <svg class="w-6 h-6 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </div>

        <!-- Empty State -->
        <div v-if="visiblePanels.length === 0" class="text-center py-12">
          <svg class="w-16 h-16 mx-auto text-gray-600 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
          </svg>
          <p class="text-gray-400 text-lg">No panels available</p>
          <p class="text-gray-500 text-sm mt-2">Add panels to the configuration to see them here</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

interface Panel {
  id: string;
  name: string;
  description?: string;
  url: string;
  color: string;
  editorOnly?: boolean;
}

// Read actor from URL query params
const urlParams = new URLSearchParams(window.location.search);
const actor = urlParams.get('actor') || '';

// Define available panels
const panels = ref<Panel[]>([
  {
    id: 'editor-shortcut-panel',
    name: 'Editor Shortcut Panel',
    description: 'Manage editor shortcuts for player',
    url: './editor-shortcut-panel.html',
    color: '#8B5CF6', // purple
    editorOnly: true
  },
  {
    id: 'shortcut-panel',
    name: 'Shortcut Panel',
    description: 'Assign backpack items to shortcut slots',
    url: './shortcut-panel.html',
    color: '#F59E0B' // amber
  },
  {
    id: 'chest-panel',
    name: 'Chest Panel',
    description: 'Transfer items between chest and backpack',
    url: './chest-panel.html',
    color: '#EF4444' // red
  },
  {
    id: 'wearing-panel',
    name: 'Wearing Panel',
    description: 'Equip items to wearing slots',
    url: './wearing-panel.html',
    color: '#10B981' // emerald
  },
  {
    id: 'skill-panel',
    name: 'Skill Panel',
    description: 'Verwalte und verteile deine Faehigkeiten',
    url: './skill-panel.html',
    color: '#06B6D4' // cyan
  },
  {
    id: 'status-panel',
    name: 'Status',
    description: 'Charakter-Uebersicht: Vitals, Skills, Zustand',
    url: './status-panel.html',
    color: '#34D399' // emerald-400
  },
  {
    id: 'map-panel',
    name: 'Map',
    description: 'Weltkarte mit Hex-Navigation',
    url: './map-panel.html',
    color: '#3B82F6' // blue
  },
  {
    id: 'library-panel',
    name: 'Bibliothek',
    description: 'Gesammelte Dokumente einsehen',
    url: './library-panel.html',
    color: '#A78BFA' // violet
  },
  {
    id: 'team-panel',
    name: 'Team',
    description: 'Team verwalten, Einladungen annehmen oder ablehnen',
    url: './team-panel.html',
    color: '#14B8A6' // teal
  }
]);

const visiblePanels = computed(() =>
  panels.value.filter(p => !p.editorOnly || actor === 'EDITOR')
);

/**
 * Navigate to a panel
 */
const navigateToPanel = (panel: Panel) => {
  window.location.href = panel.url;
};
</script>
