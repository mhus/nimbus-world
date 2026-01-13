<template>
  <div class="space-y-3 pt-2">
    <!-- Row 1: Leafiness & Stability -->
    <div class="grid grid-cols-2 gap-2">
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Leafiness</span>
        </label>
        <input
          v-model.number="localValue.leafiness"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="0.0 - 1.0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Stability</span>
        </label>
        <input
          v-model.number="localValue.stability"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="0.0 - 1.0"
        />
      </div>
    </div>
    <label class="label">
      <span class="label-text-alt">Leafiness: leaf-like wind effect | Stability: rigidity/resistance</span>
    </label>

    <!-- Row 2: Lever Up & Lever Down -->
    <div class="grid grid-cols-2 gap-2">
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Lever Up</span>
        </label>
        <input
          v-model.number="localValue.leverUp"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Upper lever"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Lever Down</span>
        </label>
        <input
          v-model.number="localValue.leverDown"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Lower lever"
        />
      </div>
    </div>
    <label class="label">
      <span class="label-text-alt">Lever arms control wind movement amplitude</span>
    </label>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { WindModifier } from '@nimbus/shared';

interface Props {
  modelValue?: WindModifier;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: WindModifier | undefined): void;
}>();

const localValue = ref<WindModifier>(
  props.modelValue ? JSON.parse(JSON.stringify(props.modelValue)) : {}
);

watch(localValue, (newValue) => {
  emit('update:modelValue', newValue);
}, { deep: true });
</script>
