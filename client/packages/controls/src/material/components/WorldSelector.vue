<template>
  <div class="dropdown dropdown-end">
    <label tabindex="0" class="btn btn-ghost">
      <span class="mr-2">World:</span>
      <span class="font-bold">{{ currentWorld?.name || 'Loading...' }}</span>
      <svg class="ml-2 w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </label>
    <ul tabindex="0" class="dropdown-content z-[1] menu p-2 shadow bg-base-200 rounded-box w-52 mt-4">
      <li v-if="loading">
        <a class="disabled">Loading...</a>
      </li>
      <li v-else-if="error">
        <a class="text-error disabled">Error loading worlds</a>
      </li>
      <template v-else>
        <li v-for="world in worlds" :key="world.worldId">
          <a
            @click="selectWorld(world.worldId)"
            :class="{ 'active': world.worldId === currentWorldId }"
          >
            {{ world.name }}
          </a>
        </li>
      </template>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import type { WorldFilter } from '@/services/WorldService';

interface Props {
  filter?: WorldFilter;
}

const props = withDefaults(defineProps<Props>(), {
  filter: undefined
});

const { currentWorld, currentWorldId, worlds, loading, error, loadWorlds, selectWorld } = useWorld();

onMounted(() => {
  loadWorlds(props.filter);
});
</script>
