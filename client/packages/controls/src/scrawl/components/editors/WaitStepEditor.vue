<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Wait Step</div>

    <!-- Seconds Input -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Duration (seconds)</span>
      </label>
      <input
        :value="modelValue.seconds"
        type="number"
        class="input input-bordered input-sm"
        placeholder="1.0"
        min="0"
        step="0.1"
        @input="updateSeconds($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          How long to wait before continuing
        </span>
      </label>
    </div>

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>Wait {{ modelValue.seconds }}s</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Wait' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

function updateSeconds(event: Event) {
  const target = event.target as HTMLInputElement;
  const seconds = parseFloat(target.value) || 0;

  emit('update:modelValue', {
    ...props.modelValue,
    seconds,
  });
}
</script>
