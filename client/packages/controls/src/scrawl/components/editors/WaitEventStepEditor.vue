<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Wait Event Step</div>

    <!-- Event Name -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Event Name</span>
      </label>
      <input
        :value="modelValue.name"
        type="text"
        class="input input-bordered input-sm"
        placeholder="event_name"
        @input="updateName($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Wait for this event to be emitted
        </span>
      </label>
    </div>

    <!-- Timeout -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Timeout (seconds)</span>
      </label>
      <input
        :value="modelValue.timeout || 0"
        type="number"
        class="input input-bordered input-sm"
        placeholder="0"
        min="0"
        step="0.1"
        @input="updateTimeout($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          0 = wait forever
        </span>
      </label>
    </div>

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>Wait for: {{ modelValue.name }}{{ modelValue.timeout ? ` (timeout: ${modelValue.timeout}s)` : '' }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'WaitEvent' };
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

function updateTimeout(event: Event) {
  const target = event.target as HTMLInputElement;
  const timeout = parseFloat(target.value) || 0;
  emit('update:modelValue', {
    ...props.modelValue,
    timeout,
  });
}
</script>
