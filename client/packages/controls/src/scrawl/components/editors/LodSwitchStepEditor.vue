<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">LOD Switch Step</div>
    <div class="text-xs opacity-60">Different steps for quality levels</div>

    <!-- LOD Levels -->
    <div class="space-y-3">
      <!-- High LOD -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="label py-0">
            <span class="label-text text-xs font-semibold">High Quality</span>
          </label>
          <button
            v-if="!modelValue.levels.high"
            class="btn btn-xs btn-ghost"
            @click="addLevel('high')"
          >
            Add
          </button>
          <button
            v-else
            class="btn btn-xs btn-error"
            @click="removeLevel('high')"
          >
            Remove
          </button>
        </div>
        <div v-if="modelValue.levels.high" class="ml-4 pl-4 border-l-2 border-success">
          <StepEditor
            :model-value="modelValue.levels.high"
            @update:model-value="updateLevel('high', $event)"
          />
        </div>
      </div>

      <!-- Medium LOD -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="label py-0">
            <span class="label-text text-xs font-semibold">Medium Quality</span>
          </label>
          <button
            v-if="!modelValue.levels.medium"
            class="btn btn-xs btn-ghost"
            @click="addLevel('medium')"
          >
            Add
          </button>
          <button
            v-else
            class="btn btn-xs btn-error"
            @click="removeLevel('medium')"
          >
            Remove
          </button>
        </div>
        <div v-if="modelValue.levels.medium" class="ml-4 pl-4 border-l-2 border-warning">
          <StepEditor
            :model-value="modelValue.levels.medium"
            @update:model-value="updateLevel('medium', $event)"
          />
        </div>
      </div>

      <!-- Low LOD -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <label class="label py-0">
            <span class="label-text text-xs font-semibold">Low Quality</span>
          </label>
          <button
            v-if="!modelValue.levels.low"
            class="btn btn-xs btn-ghost"
            @click="addLevel('low')"
          >
            Add
          </button>
          <button
            v-else
            class="btn btn-xs btn-error"
            @click="removeLevel('low')"
          >
            Remove
          </button>
        </div>
        <div v-if="modelValue.levels.low" class="ml-4 pl-4 border-l-2 border-error">
          <StepEditor
            :model-value="modelValue.levels.low"
            @update:model-value="updateLevel('low', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';
import StepEditor from '../StepEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'LodSwitch' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function addLevel(level: 'high' | 'medium' | 'low') {
  emit('update:modelValue', {
    ...props.modelValue,
    levels: {
      ...props.modelValue.levels,
      [level]: { kind: 'Wait', seconds: 0 },
    },
  });
}

function removeLevel(level: 'high' | 'medium' | 'low') {
  const updated = { ...props.modelValue };
  const newLevels = { ...updated.levels };
  delete newLevels[level];
  updated.levels = newLevels;
  emit('update:modelValue', updated);
}

function updateLevel(level: 'high' | 'medium' | 'low', step: ScrawlStep) {
  emit('update:modelValue', {
    ...props.modelValue,
    levels: {
      ...props.modelValue.levels,
      [level]: step,
    },
  });
}
</script>
