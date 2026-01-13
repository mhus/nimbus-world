<template>
  <label class="label cursor-pointer justify-start gap-2" @click.prevent="cycle">
    <input
      ref="checkboxRef"
      type="checkbox"
      class="checkbox checkbox-sm tri-state-checkbox"
      :class="{
        'checkbox-undefined': state === undefined,
        'checkbox-checked': state === true,
        'checkbox-unchecked': state === false
      }"
      @click.prevent
      tabindex="-1"
    />
    <span class="label-text text-xs">
      <slot></slot>
      <span v-if="state === undefined" class="text-base-content/50 ml-1">(undefined)</span>
    </span>
  </label>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue';

interface Props {
  modelValue?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean | undefined): void;
}>();

// Internal state: undefined, true, false
const state = ref<boolean | undefined>(props.modelValue);
const checkboxRef = ref<HTMLInputElement | null>(null);

// Update visual state of checkbox
const updateCheckbox = () => {
  if (!checkboxRef.value) return;

  // Use nextTick to ensure DOM is updated
  nextTick(() => {
    if (!checkboxRef.value) return;

    if (state.value === undefined) {
      // Indeterminate state
      checkboxRef.value.checked = false;
      checkboxRef.value.indeterminate = true;
    } else if (state.value === true) {
      // Checked state
      checkboxRef.value.indeterminate = false;
      checkboxRef.value.checked = true;
    } else {
      // Unchecked state
      checkboxRef.value.indeterminate = false;
      checkboxRef.value.checked = false;
    }
  });
};

// Cycle through states: undefined -> true -> false -> undefined
const cycle = () => {
  if (state.value === undefined) {
    state.value = true;
  } else if (state.value === true) {
    state.value = false;
  } else {
    state.value = undefined;
  }

  emit('update:modelValue', state.value);
  updateCheckbox();
};

// Watch for external changes
watch(() => props.modelValue, (newValue) => {
  state.value = newValue;
  updateCheckbox();
}, { immediate: true });

// Initialize on mount
onMounted(() => {
  updateCheckbox();
});
</script>

<style scoped>
.tri-state-checkbox.checkbox-undefined {
  opacity: 0.6;
}
</style>
