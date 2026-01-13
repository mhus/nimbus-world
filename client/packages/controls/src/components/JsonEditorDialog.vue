<template>
  <!-- Custom Modal (not Headless UI Dialog to avoid nesting issues) -->
  <Teleport to="body" v-if="isOpen">
    <div class="fixed inset-0 z-[70] flex items-center justify-center p-4" @click.self="handleCancel">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black bg-opacity-50" @click="handleCancel"></div>

      <!-- Modal Content -->
      <div class="relative w-full max-w-4xl bg-base-100 rounded-2xl shadow-2xl p-6 max-h-[90vh] overflow-hidden flex flex-col">
        <h2 class="text-2xl font-bold mb-4">
          JSON Source Editor
        </h2>

        <p class="text-sm text-base-content/70 mb-4">
          Edit the JSON source directly. Changes will be validated before applying.
        </p>

        <!-- Editor Content -->
        <div class="space-y-4 overflow-y-auto pr-2 flex-1">
          <textarea
            ref="editorTextarea"
            v-model="jsonText"
            class="textarea textarea-bordered w-full font-mono text-sm"
            :class="{ 'textarea-error': validationError }"
            placeholder="Enter JSON here..."
            spellcheck="false"
            rows="20"
          ></textarea>

          <!-- Validation Error -->
          <div v-if="validationError" class="alert alert-error">
            <svg class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <div>
              <div class="font-semibold">Invalid JSON</div>
              <div class="text-xs">{{ validationError }}</div>
            </div>
          </div>
        </div>

        <!-- Footer Actions -->
        <div class="mt-6 flex justify-between items-center border-t pt-4">
          <div class="text-sm text-base-content/70">
            <span class="font-mono">{{ characterCount }}</span> characters
          </div>
          <div class="flex gap-2">
            <button class="btn btn-ghost" @click="handleCancel">
              Cancel
            </button>
            <button class="btn btn-primary" @click="handleApply">
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
              </svg>
              Apply
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';

interface Props {
  modelValue: any;
  isOpen: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:isOpen', value: boolean): void;
  (e: 'apply', value: any): void;
  (e: 'cancel'): void;
}>();

const editorTextarea = ref<HTMLTextAreaElement | null>(null);
const jsonText = ref('');
const validationError = ref('');

// Character count
const characterCount = computed(() => jsonText.value.length);

// Initialize JSON text when dialog opens
watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    try {
      jsonText.value = JSON.stringify(props.modelValue, null, 2);
      validationError.value = '';
      // Focus the textarea after it's rendered
      nextTick(() => {
        editorTextarea.value?.focus();
      });
    } catch (error) {
      jsonText.value = '{}';
      validationError.value = 'Failed to serialize current data to JSON';
    }
  }
});

// Validate and apply JSON
const handleApply = () => {
  try {
    const parsed = JSON.parse(jsonText.value);
    validationError.value = '';
    emit('apply', parsed);
    emit('update:isOpen', false);
  } catch (error) {
    if (error instanceof Error) {
      validationError.value = error.message;
    } else {
      validationError.value = 'Unknown error occurred while parsing JSON';
    }
  }
};

// Cancel and close dialog
const handleCancel = () => {
  validationError.value = '';
  emit('cancel');
  emit('update:isOpen', false);
};

// Clear validation error when user types
watch(jsonText, () => {
  if (validationError.value) {
    validationError.value = '';
  }
});
</script>

<style scoped>
/* Better monospace font rendering */
textarea.font-mono {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 'source-code-pro', monospace;
  line-height: 1.5;
  tab-size: 2;
}
</style>
