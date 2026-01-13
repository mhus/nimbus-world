<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header (hidden in embedded mode) -->
    <header v-if="!isEmbedded()" class="navbar bg-base-300 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <h1 class="text-xl font-bold px-4">Nimbus Block Instance Editor</h1>
      </div>
      <div class="flex-none">
        <span class="text-sm text-base-content/70">World: {{ worldId }}</span>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <BlockInstanceEditor />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import BlockInstanceEditor from './views/BlockInstanceEditor.vue';
import { useModal } from '@/composables/useModal';

// Modal composable for embedded detection
const { isEmbedded } = useModal();

// Get world ID from URL or env
const params = new URLSearchParams(window.location.search);
const worldId = computed(() => params.get('world'));
</script>
