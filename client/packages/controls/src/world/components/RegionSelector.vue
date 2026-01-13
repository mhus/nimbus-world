<template>
  <div class="dropdown dropdown-end">
    <label tabindex="0" class="btn btn-ghost">
      <span class="mr-2">Region:</span>
      <span class="font-bold">{{ currentRegion?.name || 'Loading...' }}</span>
      <svg class="ml-2 w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </label>
    <ul tabindex="0" class="dropdown-content z-[1] menu p-2 shadow bg-base-200 rounded-box w-52 mt-4">
      <li v-if="loading">
        <a class="disabled">Loading...</a>
      </li>
      <li v-else-if="error">
        <a class="text-error disabled">Error loading regions</a>
      </li>
      <template v-else>
        <li v-for="region in enabledRegions" :key="region.id">
          <a
            @click="selectRegion(region.name)"
            :class="{ 'active': region.name === currentRegionId }"
          >
            {{ region.name }}
          </a>
        </li>
      </template>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRegion } from '@/composables/useRegion';

const { currentRegion, currentRegionId, regions, loading, error, loadRegions, selectRegion } = useRegion();

const enabledRegions = computed(() => regions.value.filter(r => r.enabled));

onMounted(() => {
  loadRegions();
});
</script>
