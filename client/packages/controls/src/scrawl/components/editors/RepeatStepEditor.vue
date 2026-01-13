<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">Repeat Step</div>

    <!-- Repeat Mode Selection -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Repeat Mode</span>
      </label>
      <select
        :value="repeatMode"
        class="select select-bordered select-sm"
        @change="changeRepeatMode($event)"
      >
        <option value="times">N Times</option>
        <option value="until">Until Event</option>
      </select>
    </div>

    <!-- Times Input (if mode = times) -->
    <div v-if="repeatMode === 'times'" class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Times</span>
      </label>
      <input
        :value="modelValue.times || 1"
        type="number"
        class="input input-bordered input-sm"
        placeholder="3"
        min="1"
        @input="updateTimes($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          How many times to repeat
        </span>
      </label>
    </div>

    <!-- Until Event Input (if mode = until) -->
    <div v-if="repeatMode === 'until'" class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Until Event</span>
      </label>
      <input
        :value="modelValue.untilEvent || ''"
        type="text"
        class="input input-bordered input-sm"
        placeholder="stop_event"
        @input="updateUntilEvent($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Event name to stop repeating
        </span>
      </label>
    </div>

    <!-- Repeated Step -->
    <div class="divider text-xs">Repeated Step</div>
    <div class="ml-4 pl-4 border-l-2 border-success space-y-2">
      <!-- Step Type Selector -->
      <div class="flex items-center gap-2">
        <span class="text-xs opacity-70">Step Type:</span>
        <select
          :value="modelValue.step.kind"
          class="select select-bordered select-xs"
          @change="changeStepType($event)"
        >
          <option value="Wait">Wait</option>
          <option value="Play">Play</option>
          <option value="Cmd">Cmd</option>
          <option value="Sequence">Sequence</option>
          <option value="Parallel">Parallel</option>
          <option value="Repeat">Repeat</option>
          <option value="ForEach">ForEach</option>
          <option value="If">If</option>
          <option value="Call">Call</option>
          <option value="SetVar">SetVar</option>
          <option value="EmitEvent">EmitEvent</option>
          <option value="WaitEvent">WaitEvent</option>
        </select>
      </div>

      <StepEditor
        :model-value="modelValue.step"
        @update:model-value="updateStep"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';
import StepEditor from '../StepEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Repeat' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

const repeatMode = computed(() => {
  if (props.modelValue.times != null) return 'times';
  if (props.modelValue.untilEvent) return 'until';
  return 'times';
});

function changeRepeatMode(event: Event) {
  const target = event.target as HTMLSelectElement;
  const mode = target.value;

  const updated: any = {
    ...props.modelValue,
  };

  if (mode === 'times') {
    updated.times = 3;
    delete updated.untilEvent;
  } else {
    updated.untilEvent = 'stop';
    delete updated.times;
  }

  emit('update:modelValue', updated);
}

function updateTimes(event: Event) {
  const target = event.target as HTMLInputElement;
  const times = parseInt(target.value) || 1;

  emit('update:modelValue', {
    ...props.modelValue,
    times,
    untilEvent: undefined,
  } as any);
}

function updateUntilEvent(event: Event) {
  const target = event.target as HTMLInputElement;
  const untilEvent = target.value;

  emit('update:modelValue', {
    ...props.modelValue,
    untilEvent,
    times: undefined,
  } as any);
}

function changeStepType(event: Event) {
  const target = event.target as HTMLSelectElement;
  const newKind = target.value;

  // Create template for new step type
  let newStep: ScrawlStep;
  switch (newKind) {
    case 'Wait':
      newStep = { kind: 'Wait', seconds: 1 };
      break;
    case 'Play':
      newStep = { kind: 'Play', effectId: '', source: '$source', target: '$target', ctx: {} };
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
    case 'ForEach':
      newStep = { kind: 'ForEach', collection: '$patients', itemVar: '$patient', step: { kind: 'Wait', seconds: 0.1 } };
      break;
    case 'If':
      newStep = { kind: 'If', cond: { kind: 'VarExists', name: 'variable' }, then: { kind: 'Wait', seconds: 0 } };
      break;
    case 'Call':
      newStep = { kind: 'Call', scriptId: '' };
      break;
    case 'SetVar':
      newStep = { kind: 'SetVar', name: '', value: '' };
      break;
    case 'EmitEvent':
      newStep = { kind: 'EmitEvent', name: '' };
      break;
    case 'WaitEvent':
      newStep = { kind: 'WaitEvent', name: '' };
      break;
    default:
      newStep = { kind: 'Wait', seconds: 1 };
  }

  emit('update:modelValue', {
    ...props.modelValue,
    step: newStep,
  } as any);
}

function updateStep(step: ScrawlStep) {
  emit('update:modelValue', {
    ...props.modelValue,
    step,
  });
}
</script>
