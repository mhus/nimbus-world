<template>
  <div class="min-h-screen flex flex-col">
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
        <h1 class="text-xl font-bold px-4">Nimbus Rule Editor</h1>
      </div>
      <div class="flex-none flex items-center gap-2">
        <!-- Epoch Selector -->
        <div v-if="epoches.length > 0" class="flex items-center gap-1">
          <span class="text-sm">Epoch:</span>
          <select
            v-model.number="selectedEpoch"
            class="select select-ghost select-sm"
          >
            <option :value="-1">All</option>
            <option v-for="ep in epoches" :key="ep.epoch" :value="ep.epoch">
              {{ ep.epoch }} - {{ ep.name }}
            </option>
          </select>
        </div>
        <!-- World Selector -->
        <WorldSelector filter="withCollectionsAndZones" />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <RuleEditor :epoch="selectedEpoch" />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { worldService, type EpochMeta } from '@/services/WorldService';
import WorldSelector from '@material/components/WorldSelector.vue';
import RuleEditor from '@rule/views/RuleEditor.vue';

const { currentWorldId } = useWorld();

const epoches = ref<EpochMeta[]>([]);
const selectedEpoch = ref<number>(-1);

const loadEpoches = async () => {
  if (!currentWorldId.value || currentWorldId.value.startsWith('@')) {
    epoches.value = [];
    return;
  }
  try {
    const detail = await worldService.getWorldDetail(currentWorldId.value);
    epoches.value = detail.epoches || [];
    if (epoches.value.length > 0) {
      selectedEpoch.value = -1;
    }
  } catch {
    epoches.value = [];
  }
};

watch(currentWorldId, () => {
  loadEpoches();
}, { immediate: true });
</script>
