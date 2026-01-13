<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Call Script Step</div>

    <!-- Script ID -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Script ID</span>
      </label>
      <input
        :value="modelValue.scriptId"
        type="text"
        class="input input-bordered input-sm"
        placeholder="sub_script_name"
        @input="updateScriptId($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Name of script or sequence to call
        </span>
      </label>
    </div>

    <!-- Arguments -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Arguments (JSON Object)</span>
      </label>
      <textarea
        :value="argsJson"
        class="textarea textarea-bordered textarea-sm font-mono text-xs"
        rows="3"
        placeholder='{"param1": "value1"}'
        @input="updateArgs($event)"
      ></textarea>
      <label v-if="argsError" class="label py-0">
        <span class="label-text-alt text-xs text-error">{{ argsError }}</span>
      </label>
    </div>

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>Call: {{ modelValue.scriptId }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Call' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

const argsError = ref<string | null>(null);

const argsJson = computed(() => {
  return JSON.stringify(props.modelValue.args || {}, null, 2);
});

function updateScriptId(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    scriptId: target.value,
  });
}

function updateArgs(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  try {
    const parsed = JSON.parse(target.value);
    if (typeof parsed !== 'object' || Array.isArray(parsed)) {
      argsError.value = 'Arguments must be an object';
      return;
    }
    argsError.value = null;
    emit('update:modelValue', {
      ...props.modelValue,
      args: parsed,
    });
  } catch (e: any) {
    argsError.value = e.message;
  }
}
</script>
