<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">{{ modelValue.kind }} Step</div>
    <div class="text-xs opacity-50 mb-2">
      Editor for this step type not yet implemented. Edit as JSON:
    </div>

    <!-- JSON Editor -->
    <textarea
      :value="jsonValue"
      class="textarea textarea-bordered textarea-sm w-full font-mono text-xs"
      rows="6"
      @input="updateFromJson"
    ></textarea>

    <div v-if="jsonError" class="alert alert-error alert-sm">
      <span class="text-xs">Invalid JSON: {{ jsonError }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep;
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

const jsonError = ref<string | null>(null);

const jsonValue = computed(() => {
  return JSON.stringify(props.modelValue, null, 2);
});

function updateFromJson(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  try {
    const parsed = JSON.parse(target.value);
    jsonError.value = null;
    emit('update:modelValue', parsed);
  } catch (e: any) {
    jsonError.value = e.message;
  }
}
</script>
