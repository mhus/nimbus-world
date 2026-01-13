<template>
  <div class="space-y-3">
    <div class="text-sm font-semibold opacity-70">For Each Step</div>
    <div class="text-xs opacity-60">Iterate over a collection</div>

    <!-- Collection Input -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Collection</span>
      </label>
      <input
        :value="modelValue.collection"
        type="text"
        class="input input-bordered input-sm font-mono"
        placeholder="$patients"
        @input="updateCollection($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Variable reference (e.g., $patients, $targets)
        </span>
      </label>
    </div>

    <!-- Item Variable Input -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Loop Variable</span>
      </label>
      <input
        :value="modelValue.itemVar"
        type="text"
        class="input input-bordered input-sm font-mono"
        placeholder="$patient"
        @input="updateItemVar($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Current item in loop (e.g., $patient, $target)
        </span>
      </label>
    </div>

    <!-- Common Presets -->
    <div class="flex gap-2">
      <button
        class="btn btn-xs btn-outline"
        @click="applyPreset('$patients', '$patient')"
      >
        Patients
      </button>
      <button
        class="btn btn-xs btn-outline"
        @click="applyPreset('$targets', '$target')"
      >
        Targets
      </button>
      <button
        class="btn btn-xs btn-outline"
        @click="applyPreset('$items', '$item')"
      >
        Items
      </button>
    </div>

    <!-- Loop Step -->
    <div class="divider text-xs">Step for Each Item</div>
    <div class="ml-4 pl-4 border-l-2 border-primary">
      <div class="text-xs opacity-60 mb-2">
        This step will execute once per item in <code>{{ modelValue.collection }}</code>
        <br>
        Current item available as <code>{{ modelValue.itemVar }}</code>
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
  modelValue: ScrawlStep & { kind: 'ForEach' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateCollection(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    collection: target.value,
  });
}

function updateItemVar(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    itemVar: target.value,
  });
}

function applyPreset(collection: string, itemVar: string) {
  emit('update:modelValue', {
    ...props.modelValue,
    collection,
    itemVar,
  });
}

function updateStep(step: ScrawlStep) {
  emit('update:modelValue', {
    ...props.modelValue,
    step,
  });
}
</script>
