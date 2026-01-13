<template>
  <div class="space-y-3 pt-2">
    <!-- Color & Strength Row -->
    <div class="grid grid-cols-2 gap-2">
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Color</span>
        </label>
        <input
          v-model="localValue.color"
          type="text"
          class="input input-bordered input-sm"
          placeholder="#RRGGBB"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Strength</span>
        </label>
        <input
          v-model.number="localValue.strength"
          type="number"
          step="0.1"
          min="0"
          class="input input-bordered input-sm"
          placeholder="0.0"
        />
      </div>
    </div>
    <label class="label">
      <span class="label-text-alt">Color: light color (e.g., #FFFFFF, red) | Strength: light intensity</span>
    </label>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { IlluminationModifier } from '@nimbus/shared';

interface Props {
  modelValue?: IlluminationModifier;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: IlluminationModifier | undefined): void;
}>();

const localValue = ref<IlluminationModifier>(
  props.modelValue ? JSON.parse(JSON.stringify(props.modelValue)) : {}
);

watch(localValue, (newValue) => {
  emit('update:modelValue', newValue);
}, { deep: true });
</script>
