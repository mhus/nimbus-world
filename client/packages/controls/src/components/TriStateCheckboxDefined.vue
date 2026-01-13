<template>
  <label class="label cursor-pointer justify-start gap-2" @click="cycle">
    <div
      class="checkbox checkbox-sm tri-state-checkbox"
      :class="{
        'opacity-60': !isDefined
      }"
      role="checkbox"
      :aria-checked="isDefined ? (value ? 'true' : 'false') : 'mixed'"
      tabindex="0"
      @keydown.space.prevent="cycle"
      @keydown.enter.prevent="cycle"
    >
      <!-- Custom icon based on state -->
      <svg
        v-if="!isDefined"
        class="w-3 h-3"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <!-- Horizontal line for indeterminate -->
        <path d="M3 8h10" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <svg
        v-else-if="value"
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
      <span v-if="!isDefined" class="text-base-content/50 ml-1">(undefined)</span>
    </span>
  </label>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';

interface Props {
  modelObject: any;
  propertyName: string;
}

const props = defineProps<Props>();

const definedKey = computed(() => `_${props.propertyName}Defined`);

const isDefined = computed(() => {
  const result = props.modelObject[definedKey.value] === true;
  return result;
});

const value = computed(() => {
  const result = props.modelObject[props.propertyName] === true;
  return result;
});

onMounted(() => {
  console.log(`[TriStateCheckbox MOUNTED] ${props.propertyName}:`, {
    _defined: props.modelObject[definedKey.value],
    value: props.modelObject[props.propertyName],
    isDefined: isDefined.value,
    computedValue: value.value,
    fullObject: props.modelObject
  });
});

// Cycle through states: undefined -> true -> false -> undefined
const cycle = () => {
  // Read current state
  const currentDefined = props.modelObject[definedKey.value] === true;
  const currentValue = props.modelObject[props.propertyName] === true;

  if (!currentDefined) {
    // undefined -> true
    props.modelObject[definedKey.value] = true;
    props.modelObject[props.propertyName] = true;
  } else if (currentValue) {
    // true -> false
    props.modelObject[definedKey.value] = true;
    props.modelObject[props.propertyName] = false;
  } else {
    // false -> undefined
    props.modelObject[definedKey.value] = false;
    props.modelObject[props.propertyName] = false;
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
