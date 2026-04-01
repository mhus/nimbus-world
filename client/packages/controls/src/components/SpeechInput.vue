<template>
  <button
    v-if="available"
    @click="toggle"
    class="p-2 rounded-lg transition-colors"
    :class="listening
      ? 'bg-red-600 hover:bg-red-500 text-white animate-pulse'
      : 'bg-gray-700 hover:bg-gray-600 text-gray-300 hover:text-amber-400'"
    :title="listening ? 'Aufnahme stoppen' : 'Spracheingabe'"
  >
    <!-- Microphone icon -->
    <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
      <path d="M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z" />
    </svg>
  </button>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue';
import { SpeechRecognizer, isSpeechRecognitionAvailable } from '@/utils/SpeechRecognizer';

const props = defineProps<{
  lang?: string;
}>();

const emit = defineEmits<{
  (e: 'result', text: string, isFinal: boolean): void;
}>();

const available = ref(isSpeechRecognitionAvailable());
const listening = ref(false);
const recognizer = new SpeechRecognizer(props.lang || 'de-DE');

function toggle() {
  recognizer.toggle(
    (text: string, isFinal: boolean) => {
      emit('result', text, isFinal);
    },
    (isListening: boolean) => {
      listening.value = isListening;
    },
    (_error: string) => {
      // Fatal error (not-allowed, network, service blocked) — hide button permanently
      available.value = false;
    }
  );
}

function stop() {
  recognizer.stop();
  listening.value = false;
}

defineExpose({ stop, listening });

onBeforeUnmount(() => {
  recognizer.stop();
});
</script>
