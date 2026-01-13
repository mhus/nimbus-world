<template>
  <div class="collapse collapse-arrow bg-base-200">
    <input type="checkbox" v-model="isOpen" />
    <div class="collapse-title text-xs font-medium">
      {{ title }}
      <span v-if="filteredPresets.length > 0" class="badge badge-sm badge-ghost ml-2">
        {{ filteredPresets.length }}
      </span>
    </div>
    <div class="collapse-content">
      <!-- Loading -->
      <div v-if="loading" class="text-center py-2">
        <span class="loading loading-spinner loading-sm"></span>
      </div>

      <!-- Loaded Presets -->
      <div v-else class="space-y-2">
        <!-- Search -->
        <input
          v-model="searchQuery"
          type="text"
          class="input input-bordered input-xs w-full"
          placeholder="Search presets..."
        />

        <!-- Preset List -->
        <div v-if="filteredPresets.length > 0" class="max-h-64 overflow-y-auto space-y-1">
          <button
            v-for="preset in filteredPresets"
            :key="preset.id"
            class="btn btn-xs btn-outline w-full justify-start text-left h-auto min-h-[3rem] py-2"
            @click="selectPreset(preset)"
          >
            <div class="flex flex-col items-start gap-0.5">
              <div class="font-semibold">{{ preset.name }}</div>
              <div class="text-xs opacity-60 leading-tight">{{ preset.description }}</div>
              <div v-if="preset.category" class="text-xs opacity-40">{{ preset.category }}</div>
            </div>
          </button>
        </div>

        <!-- Empty State -->
        <div v-else class="text-center py-4 opacity-50 text-xs">
          {{ searchQuery ? 'No presets found' : 'No presets available' }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { EffectPreset, CommandPreset } from '../services/presetService';

const props = defineProps<{
  title: string;
  presets: EffectPreset[] | CommandPreset[];
  loading?: boolean;
}>();

const emit = defineEmits<{
  select: [preset: EffectPreset | CommandPreset];
}>();

const isOpen = ref(false);
const searchQuery = ref('');

const filteredPresets = computed(() => {
  if (!searchQuery.value) {
    return props.presets;
  }

  const query = searchQuery.value.toLowerCase();
  return props.presets.filter(
    (p) =>
      p.name.toLowerCase().includes(query) ||
      p.description.toLowerCase().includes(query) ||
      p.category.toLowerCase().includes(query) ||
      p.id.toLowerCase().includes(query)
  );
});

function selectPreset(preset: EffectPreset | CommandPreset) {
  emit('select', preset);
  isOpen.value = false;
  searchQuery.value = '';
}
</script>
