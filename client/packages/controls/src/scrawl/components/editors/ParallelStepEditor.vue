<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">Parallel Step</div>
    <div class="text-xs opacity-60">Execute steps concurrently</div>

    <!-- Nested Steps -->
    <div class="ml-4 pl-4 border-l-2 border-warning">
      <StepList
        :model-value="modelValue.steps"
        @update:model-value="updateSteps"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';
import StepList from '../StepList.vue';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Parallel' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateSteps(steps: ScrawlStep[]) {
  emit('update:modelValue', {
    ...props.modelValue,
    steps,
  });
}
</script>
