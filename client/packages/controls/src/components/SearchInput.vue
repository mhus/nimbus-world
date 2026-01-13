<template>
  <div class="form-control">
    <div class="flex gap-0">
      <input
        type="text"
        :placeholder="placeholder"
        :value="modelValue"
        @input="handleInput"
        @keyup.enter="handleSearch"
        class="input input-bordered flex-1"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

interface Props {
  modelValue?: string;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: 'Search...',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'search', value: string): void;
}>();

// Track the current input value
const currentValue = ref(props.modelValue);

const handleInput = (event: Event) => {
  const value = (event.target as HTMLInputElement).value;
  currentValue.value = value;
  emit('update:modelValue', value);
};

const handleSearch = () => {
  console.log('[SearchInput] Enter pressed, emitting search event with value:', currentValue.value);
  emit('search', currentValue.value);
};
</script>
