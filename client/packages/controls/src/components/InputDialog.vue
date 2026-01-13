<template>
  <Teleport to="body" v-if="isOpen">
    <div class="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black bg-opacity-50"></div>

      <!-- Dialog Content -->
      <div class="relative w-full max-w-md bg-base-100 rounded-lg shadow-2xl p-6">
        <h3 class="text-lg font-bold mb-4">{{ title }}</h3>

        <p v-if="message" class="text-sm text-base-content/70 mb-4">{{ message }}</p>

        <input
          v-model="inputValue"
          type="text"
          class="input input-bordered w-full mb-4"
          :placeholder="placeholder"
          @keyup.enter="handleOk"
          @keyup.esc="handleCancel"
          ref="inputEl"
        />

        <div class="flex justify-end gap-2">
          <button class="btn btn-ghost" @click="handleCancel">
            Cancel
          </button>
          <button class="btn btn-primary" @click="handleOk">
            OK
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';

interface Props {
  isOpen: boolean;
  title: string;
  message?: string;
  defaultValue?: string;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  message: '',
  defaultValue: '',
  placeholder: ''
});

const emit = defineEmits<{
  (e: 'update:isOpen', value: boolean): void;
  (e: 'ok', value: string): void;
  (e: 'cancel'): void;
}>();

const inputValue = ref('');
const inputEl = ref<HTMLInputElement | null>(null);

// Initialize input value when dialog opens
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    inputValue.value = props.defaultValue;
    nextTick(() => {
      inputEl.value?.focus();
      inputEl.value?.select();
    });
  }
});

const handleOk = () => {
  emit('ok', inputValue.value);
  emit('update:isOpen', false);
};

const handleCancel = () => {
  emit('cancel');
  emit('update:isOpen', false);
};
</script>
