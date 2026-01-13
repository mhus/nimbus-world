<template>
  <div class="space-y-2">
    <!-- Step Items -->
    <div
      v-for="(step, index) in modelValue"
      :key="index"
      class="border border-base-300 rounded-lg p-3 bg-base-100 hover:shadow-md transition-shadow"
    >
      <!-- Step Header with Controls -->
      <div class="flex items-center gap-2 mb-2">
        <span class="badge badge-sm" :class="getStepBadgeClass(step)">
          {{ step.kind }}
        </span>

        <div class="flex-1"></div>

        <!-- Reorder Buttons -->
        <button
          v-if="index > 0"
          class="btn btn-xs btn-ghost"
          title="Move Up"
          @click="moveUp(index)"
        >
          ↑
        </button>
        <button
          v-if="index < modelValue.length - 1"
          class="btn btn-xs btn-ghost"
          title="Move Down"
          @click="moveDown(index)"
        >
          ↓
        </button>

        <!-- Delete Button -->
        <button
          class="btn btn-xs btn-error"
          title="Delete"
          @click="deleteStep(index)"
        >
          ✕
        </button>
      </div>

      <!-- Step Editor -->
      <StepEditor
        :model-value="step"
        @update:model-value="updateStep(index, $event)"
      />
    </div>

    <!-- Add Button -->
    <div class="flex gap-2">
      <button
        class="btn btn-sm btn-primary w-full"
        @click="showAddMenu = !showAddMenu"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        Add Step
      </button>
    </div>

    <!-- Add Menu -->
    <div v-if="showAddMenu" class="card bg-base-200 shadow-lg p-4 mt-2">
      <h4 class="font-semibold mb-2">Select Step Type:</h4>
      <div class="grid grid-cols-2 gap-2">
        <button
          v-for="stepType in availableStepTypes"
          :key="stepType"
          class="btn btn-sm btn-outline"
          @click="addStep(stepType)"
        >
          {{ stepType }}
        </button>
      </div>
      <button class="btn btn-sm btn-ghost mt-2" @click="showAddMenu = false">
        Cancel
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';
import StepEditor from './StepEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep[];
}>();

const emit = defineEmits<{
  'update:modelValue': [steps: ScrawlStep[]];
}>();

const showAddMenu = ref(false);

// Available step types (will be loaded from step-definitions.json later)
const availableStepTypes = ['Wait', 'Play', 'Cmd', 'Sequence', 'Parallel', 'Repeat', 'While', 'Until', 'ForEach', 'If', 'Call', 'SetVar', 'EmitEvent', 'WaitEvent', 'LodSwitch'];

function getStepBadgeClass(step: ScrawlStep): string {
  switch (step.kind) {
    case 'Play':
      return 'badge-primary';
    case 'Wait':
      return 'badge-secondary';
    case 'Cmd':
      return 'badge-accent';
    case 'Sequence':
    case 'Parallel':
      return 'badge-info';
    case 'Repeat':
    case 'While':
    case 'Until':
    case 'ForEach':
      return 'badge-success';
    case 'If':
      return 'badge-warning';
    default:
      return 'badge-ghost';
  }
}

function addStep(stepType: string) {
  const newSteps = [...props.modelValue];

  // Create template based on type
  let newStep: ScrawlStep;
  switch (stepType) {
    case 'Wait':
      newStep = { kind: 'Wait', seconds: 1 };
      break;
    case 'Play':
      newStep = { kind: 'Play', effectId: '', ctx: {} };
      break;
    case 'Cmd':
      newStep = { kind: 'Cmd', cmd: '', parameters: [] };
      break;
    case 'Sequence':
      newStep = { kind: 'Sequence', steps: [] };
      break;
    case 'Parallel':
      newStep = { kind: 'Parallel', steps: [] };
      break;
    case 'Repeat':
      newStep = { kind: 'Repeat', times: 3, step: { kind: 'Wait', seconds: 1 } };
      break;
    case 'While':
      newStep = { kind: 'While', taskId: 'task_1', step: { kind: 'Wait', seconds: 0.5 } };
      break;
    case 'Until':
      newStep = { kind: 'Until', event: 'stop_event', step: { kind: 'Wait', seconds: 0.5 } };
      break;
    case 'ForEach':
      newStep = { kind: 'ForEach', collection: '$patients', itemVar: '$patient', step: { kind: 'Wait', seconds: 0.1 } };
      break;
    case 'If':
      newStep = { kind: 'If', cond: { kind: 'VarExists', name: 'variable' }, then: { kind: 'Wait', seconds: 0 } };
      break;
    case 'Call':
      newStep = { kind: 'Call', scriptId: '', args: {} };
      break;
    case 'SetVar':
      newStep = { kind: 'SetVar', name: '', value: '' };
      break;
    case 'EmitEvent':
      newStep = { kind: 'EmitEvent', name: '' };
      break;
    case 'WaitEvent':
      newStep = { kind: 'WaitEvent', name: '', timeout: 0 };
      break;
    case 'LodSwitch':
      newStep = { kind: 'LodSwitch', levels: {} };
      break;
    default:
      newStep = { kind: 'Wait', seconds: 1 };
  }

  newSteps.push(newStep);
  emit('update:modelValue', newSteps);
  showAddMenu.value = false;
}

function deleteStep(index: number) {
  const newSteps = [...props.modelValue];
  newSteps.splice(index, 1);
  emit('update:modelValue', newSteps);
}

function moveUp(index: number) {
  if (index === 0) return;
  const newSteps = [...props.modelValue];
  [newSteps[index - 1], newSteps[index]] = [newSteps[index], newSteps[index - 1]];
  emit('update:modelValue', newSteps);
}

function moveDown(index: number) {
  if (index >= props.modelValue.length - 1) return;
  const newSteps = [...props.modelValue];
  [newSteps[index], newSteps[index + 1]] = [newSteps[index + 1], newSteps[index]];
  emit('update:modelValue', newSteps);
}

function updateStep(index: number, step: ScrawlStep) {
  const newSteps = [...props.modelValue];
  newSteps[index] = step;
  emit('update:modelValue', newSteps);
}
</script>
