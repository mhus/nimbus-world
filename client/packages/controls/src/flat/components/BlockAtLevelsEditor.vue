<template>
  <div class="space-y-3">
    <div class="text-sm font-medium text-gray-700">
      Block At Levels
      <span class="text-xs text-gray-500">(Height-specific block overrides)</span>
    </div>

    <!-- Existing entries table -->
    <div v-if="sortedEntries.length > 0" class="overflow-x-auto">
      <table class="table table-sm w-full">
        <thead>
          <tr>
            <th class="w-32">Height Offset</th>
            <th>Block Definition</th>
            <th class="w-16"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in sortedEntries" :key="entry.key">
            <td>{{ entry.key }}</td>
            <td class="font-mono text-sm">{{ entry.value }}</td>
            <td>
              <button
                type="button"
                class="btn btn-ghost btn-xs text-error"
                @click="removeEntry(entry.key)"
                title="Delete entry"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty state -->
    <div v-else class="text-sm text-gray-500 italic py-2">
      No height-specific blocks defined
    </div>

    <!-- Add new entry form -->
    <div class="flex gap-2 items-start">
      <div class="form-control flex-none w-32">
        <label class="label py-0">
          <span class="label-text text-xs">Height</span>
        </label>
        <input
          v-model.number="newKey"
          type="number"
          min="0"
          max="255"
          class="input input-sm input-bordered w-full"
          :class="{ 'input-error': newKeyError }"
          placeholder="0-255"
        />
        <label v-if="newKeyError" class="label py-0">
          <span class="label-text-alt text-error">{{ newKeyError }}</span>
        </label>
      </div>

      <div class="form-control flex-1">
        <label class="label py-0">
          <span class="label-text text-xs">Block Definition</span>
        </label>
        <input
          v-model="newValue"
          type="text"
          class="input input-sm input-bordered w-full font-mono"
          :class="{ 'input-error': newValueError }"
          placeholder="n:stone@s:default"
        />
        <label v-if="newValueError" class="label py-0">
          <span class="label-text-alt text-error">{{ newValueError }}</span>
        </label>
      </div>

      <button
        type="button"
        class="btn btn-sm btn-primary mt-6"
        @click="addEntry"
        :disabled="!canAdd"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        Add
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';

interface Props {
  modelValue: Record<number, string>;
}

interface Emits {
  (e: 'update:modelValue', value: Record<number, string>): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// New entry form
const newKey = ref<number | ''>('');
const newValue = ref('');

// Validation
const blockDefPattern = /^[nw]:[a-zA-Z0-9_]+(@s:[a-zA-Z0-9_]+)?$/;

const newKeyError = computed(() => {
  if (newKey.value === '') return '';
  if (typeof newKey.value !== 'number' || newKey.value < 0 || newKey.value > 255) {
    return 'Must be 0-255';
  }
  if (newKey.value in props.modelValue) {
    return 'Height already exists';
  }
  return '';
});

const newValueError = computed(() => {
  if (!newValue.value) return '';
  if (!blockDefPattern.test(newValue.value)) {
    return 'Format: n:blockid@s:state or w:blockid@s:state';
  }
  return '';
});

const canAdd = computed(() => {
  return newKey.value !== '' &&
         newValue.value !== '' &&
         !newKeyError.value &&
         !newValueError.value;
});

// Sorted entries for display
const sortedEntries = computed(() => {
  return Object.entries(props.modelValue)
    .map(([key, value]) => ({ key: Number(key), value }))
    .sort((a, b) => a.key - b.key);
});

// Add entry
const addEntry = () => {
  if (!canAdd.value || newKey.value === '') return;

  const updated = { ...props.modelValue };
  updated[newKey.value] = newValue.value;
  emit('update:modelValue', updated);

  // Reset form
  newKey.value = '';
  newValue.value = '';
};

// Remove entry
const removeEntry = (key: number) => {
  const updated = { ...props.modelValue };
  delete updated[key];
  emit('update:modelValue', updated);
};

// Reset form when modelValue changes externally
watch(() => props.modelValue, () => {
  newKey.value = '';
  newValue.value = '';
}, { deep: true });
</script>
