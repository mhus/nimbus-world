<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Emit Event Step</div>

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
    </div>

    <!-- Payload (optional) -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Payload (optional JSON)</span>
      </label>
      <textarea
        :value="payloadJson"
        class="textarea textarea-bordered textarea-sm font-mono text-xs"
        rows="2"
        placeholder='{}'
        @input="updatePayload($event)"
      ></textarea>
      <label v-if="payloadError" class="label py-0">
        <span class="label-text-alt text-xs text-error">{{ payloadError }}</span>
      </label>
    </div>

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>Emit: {{ modelValue.name }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'EmitEvent' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

const payloadError = ref<string | null>(null);

const payloadJson = computed(() => {
  return JSON.stringify(props.modelValue.payload || {}, null, 2);
});

function updateName(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    name: target.value,
  });
}

function updatePayload(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  try {
    const parsed = JSON.parse(target.value);
    payloadError.value = null;
    emit('update:modelValue', {
      ...props.modelValue,
      payload: parsed,
    });
  } catch (e: any) {
    payloadError.value = e.message;
  }
}
</script>
