<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">If Condition Step</div>

    <!-- Condition -->
    <div>
      <label class="label py-1">
        <span class="label-text text-xs font-semibold">Condition</span>
      </label>
      <ConditionEditor
        :model-value="modelValue.cond"
        @update:model-value="updateCondition"
      />
    </div>

    <!-- Then Branch -->
    <div class="divider text-xs">Then (if true)</div>
    <div class="ml-4 pl-4 border-l-2 border-success">
      <StepEditor
        :model-value="modelValue.then"
        @update:model-value="updateThen"
      />
    </div>

    <!-- Else Branch -->
    <div class="divider text-xs">
      <span>Else (if false)</span>
      <button
        v-if="!modelValue.else"
        class="btn btn-xs btn-ghost ml-2"
        @click="addElse"
      >
        Add Else
      </button>
      <button
        v-else
        class="btn btn-xs btn-error ml-2"
        @click="removeElse"
      >
        Remove Else
      </button>
    </div>
    <div v-if="modelValue.else" class="ml-4 pl-4 border-l-2 border-error">
      <StepEditor
        :model-value="modelValue.else"
        @update:model-value="updateElse"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep, ScrawlCondition } from '@nimbus/shared';
import StepEditor from '../StepEditor.vue';
import ConditionEditor from '../ConditionEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'If' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateCondition(cond: ScrawlCondition) {
  emit('update:modelValue', {
    ...props.modelValue,
    cond,
  });
}

function updateThen(step: ScrawlStep) {
  emit('update:modelValue', {
    ...props.modelValue,
    then: step,
  });
}

function updateElse(step: ScrawlStep) {
  emit('update:modelValue', {
    ...props.modelValue,
    else: step,
  });
}

function addElse() {
  emit('update:modelValue', {
    ...props.modelValue,
    else: { kind: 'Wait', seconds: 0 },
  });
}

function removeElse() {
  const updated: any = { ...props.modelValue };
  delete updated.else;
  emit('update:modelValue', updated);
}
</script>
