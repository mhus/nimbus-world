<template>
  <label class="label cursor-pointer justify-start gap-2" @click="cycle">
    <div
      class="checkbox checkbox-sm tri-state-checkbox"
      :class="{
        'opacity-60': currentValue === undefined
      }"
      role="checkbox"
      :aria-checked="currentValue === undefined ? 'mixed' : (currentValue ? 'true' : 'false')"
      tabindex="0"
      @keydown.space.prevent="cycle"
      @keydown.enter.prevent="cycle"
    >
      <!-- Custom icon based on state -->
      <svg
        v-if="currentValue === undefined"
        class="w-3 h-3"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <!-- Horizontal line for undefined -->
        <path d="M3 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <svg
        v-else-if="currentValue === true"
        class="w-3 h-3"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <!-- Checkmark for true -->
        <path d="M3 8l3 3 7-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </div>
    <span class="label-text text-xs">
      <slot></slot>
      <span v-if="currentValue === undefined" class="text-base-content/50 ml-1">(undefined)</span>
    </span>
  </label>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  modelValue?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean | undefined): void;
}>();

const currentValue = computed(() => props.modelValue);

// Cycle through states: undefined -> true -> false -> undefined
const cycle = () => {
  if (currentValue.value === undefined) {
    // undefined -> true
    emit('update:modelValue', true);
  } else if (currentValue.value === true) {
    // true -> false
    emit('update:modelValue', false);
  } else {
    // false -> undefined
    emit('update:modelValue', undefined);
  }
};
</script>

<style scoped>
.tri-state-checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  cursor: pointer;
}

.tri-state-checkbox svg {
  pointer-events: none;
}
</style>
