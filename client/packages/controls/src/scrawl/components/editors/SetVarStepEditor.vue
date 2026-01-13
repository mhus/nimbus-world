<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Set Variable Step</div>

    <!-- Variable Name -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Variable Name</span>
      </label>
      <input
        :value="modelValue.name"
        type="text"
        class="input input-bordered input-sm"
        placeholder="variableName"
        @input="updateName($event)"
      />
    </div>

    <!-- Value -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Value</span>
      </label>
      <input
        :value="modelValue.value"
        type="text"
        class="input input-bordered input-sm"
        placeholder="value"
        @input="updateValue($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Can be string, number, or JSON value
        </span>
      </label>
    </div>

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>{{ modelValue.name }} = {{ JSON.stringify(modelValue.value) }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'SetVar' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateName(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    name: target.value,
  });
}

function updateValue(event: Event) {
  const target = event.target as HTMLInputElement;
  let value: any = target.value;

  // Try to parse as JSON
  try {
    value = JSON.parse(target.value);
  } catch {
    // Keep as string if not valid JSON
  }

  emit('update:modelValue', {
    ...props.modelValue,
    value,
  });
}
</script>
