<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">Until Step</div>

    <!-- Event Name Input -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Event Name</span>
      </label>
      <input
        :value="modelValue.event || ''"
        type="text"
        class="input input-bordered input-sm"
        placeholder="e.g., mouse:up, custom_event"
        @input="updateEvent($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Event that terminates the loop
        </span>
      </label>
    </div>

    <!-- Timeout (optional) -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Timeout (optional)</span>
      </label>
      <input
        :value="modelValue.timeout || ''"
        type="number"
        class="input input-bordered input-sm"
        placeholder="Leave empty for no timeout"
        min="0"
        step="0.1"
        @input="updateTimeout($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Maximum duration in seconds (empty = no timeout)
        </span>
      </label>
    </div>

    <!-- Looped Step -->
    <div class="divider text-xs">Looped Step</div>
    <div class="ml-4 pl-4 border-l-2 border-warning space-y-2">
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
import type { ScrawlStep } from '@nimbus/shared';
import StepEditor from '../StepEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Until' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateEvent(event: Event) {
  const target = event.target as HTMLInputElement;
  const eventName = target.value;

  emit('update:modelValue', {
    ...props.modelValue,
    event: eventName,
  } as any);
}

function updateTimeout(event: Event) {
  const target = event.target as HTMLInputElement;
  const value = target.value;
  const timeout = value ? parseFloat(value) : undefined;

  emit('update:modelValue', {
    ...props.modelValue,
    timeout,
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
