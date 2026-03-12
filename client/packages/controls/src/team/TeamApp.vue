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
        <h1 class="text-xl font-bold px-4">Nimbus Team Editor</h1>
      </div>
      <div class="flex-none">
        <RegionSelector />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <div v-if="!currentRegionId" class="alert alert-info">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>Please select a region from the dropdown above</span>
      </div>

      <div v-else-if="!selectedTeam">
        <TeamList
          :region-id="currentRegionId"
          @select="handleSelectTeam"
        />
      </div>

      <TeamEditor
        v-else
        :team="selectedTeam"
        @back="handleBack"
        @saved="handleSaved"
        @deleted="handleBack"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRegion } from '@/composables/useRegion';
import RegionSelector from '../world/components/RegionSelector.vue';
import TeamList from './views/TeamList.vue';
import TeamEditor from './views/TeamEditor.vue';
import type { Team } from './services/TeamServiceFrontend';

const { currentRegionId } = useRegion();
const selectedTeam = ref<Team | null>(null);

const handleSelectTeam = (team: Team) => {
  selectedTeam.value = team;
};

const handleBack = () => {
  selectedTeam.value = null;
};

const handleSaved = (team: Team) => {
  selectedTeam.value = team;
};
</script>
