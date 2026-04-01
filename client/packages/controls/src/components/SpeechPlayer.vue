<template>
  <div v-if="voice && ttsAvailable" class="flex items-center gap-1">
    <!-- Play/Pause Button -->
    <button
      @click="togglePlayback"
      class="p-2 rounded-lg bg-gray-700 hover:bg-gray-600 transition-colors text-gray-300 hover:text-amber-400"
      :title="playing ? 'Pause' : 'Vorlesen'"
    >
      <!-- Play icon -->
      <svg v-if="!playing" class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
        <path d="M8 5v14l11-7z" />
      </svg>
      <!-- Pause icon -->
      <svg v-else class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
        <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" />
      </svg>
    </button>

    <!-- Volume Button (toggles volume popup) -->
    <div class="relative">
      <button
        @click="showVolumePopup = !showVolumePopup"
        class="p-2 rounded-lg bg-gray-700 hover:bg-gray-600 transition-colors text-gray-300 hover:text-amber-400"
        title="Lautstaerke"
      >
        <!-- Volume icon -->
        <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24">
          <path v-if="volume > 5" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z" />
          <path v-else-if="volume > 0" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z" />
          <path v-else d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z" />
        </svg>
      </button>

      <!-- Volume Popup -->
      <div
        v-if="showVolumePopup"
        class="absolute right-0 top-full mt-1 bg-gray-800 border border-gray-600 rounded-lg p-3 shadow-lg z-10 w-48"
      >
        <!-- Volume Slider -->
        <div class="flex items-center gap-2 mb-2">
          <span class="text-xs text-gray-400 w-6">{{ volume }}</span>
          <input
            type="range"
            min="0"
            max="10"
            step="1"
            :value="volume"
            @input="onVolumeChange(($event.target as HTMLInputElement).value)"
            class="flex-1 h-1 accent-amber-400 cursor-pointer"
          />
        </div>

        <!-- Speed Slider -->
        <div class="flex items-center gap-2 mb-2">
          <span class="text-xs text-gray-400 w-6">{{ speed }}x</span>
          <input
            type="range"
            min="5"
            max="20"
            step="1"
            :value="speed"
            @input="onSpeedChange(($event.target as HTMLInputElement).value)"
            class="flex-1 h-1 accent-amber-400 cursor-pointer"
            title="Geschwindigkeit"
          />
        </div>

        <!-- Replay Button -->
        <button
          @click="replay"
          class="w-full flex items-center gap-2 px-2 py-1.5 rounded text-sm text-gray-300 hover:bg-gray-700 hover:text-amber-400 transition-colors"
        >
          <!-- Reload icon -->
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Von vorne abspielen
        </button>

        <!-- Voice Info -->
        <div class="mt-2 pt-2 border-t border-gray-700 text-xs text-gray-500">
          <div>Sprache: {{ voiceInfoText.lang }}</div>
          <div>Stimme: {{ voiceInfoText.name }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount, onMounted } from 'vue';
import { VoiceSpeaker, selectVoice, type VoiceInfo } from '@/utils/VoiceSpeaker';

const props = defineProps<{
  text: string;
  voice: VoiceInfo | null;
  autoPlay?: boolean;
  /** speechVolume from user settings (0-10 scale) */
  settingsVolume?: number;
  /** speechSpeed from user settings (0-10 scale, 5=normal) */
  settingsSpeed?: number;
}>();

const speaker = new VoiceSpeaker();
const playing = ref(false);
const volume = ref(props.settingsVolume ?? 5);
const speed = ref(props.settingsSpeed ?? 10); // 5-20 scale, 10=1.0x
const showVolumePopup = ref(false);
const ttsAvailable = ref(false);

// Check if TTS is available (voices loaded)
function checkTtsAvailable() {
  if (!window.speechSynthesis) return;
  const voices = window.speechSynthesis.getVoices();
  ttsAvailable.value = voices.length > 0;
}

if (typeof window !== 'undefined' && window.speechSynthesis) {
  checkTtsAvailable();
  // Chrome loads voices async
  window.speechSynthesis.onvoiceschanged = () => checkTtsAvailable();
  // Retry after short delay (Safari sometimes needs it)
  setTimeout(checkTtsAvailable, 500);
}

function getEffectiveVoice(): VoiceInfo | null {
  if (!props.voice) return null;
  // speed slider: 5=0.5x, 10=1.0x, 20=2.0x
  const speedMultiplier = speed.value / 10;
  return {
    ...props.voice,
    rate: props.voice.rate * speedMultiplier,
  };
}

function getVolume(): number {
  return Math.max(0, Math.min(1, volume.value / 10));
}

async function togglePlayback() {
  if (playing.value) {
    speaker.stop();
    playing.value = false;
  } else {
    await play();
  }
}

async function play() {
  const effectiveVoice = getEffectiveVoice();
  if (!effectiveVoice || !props.text) return;

  playing.value = true;
  await speaker.speak(props.text, effectiveVoice, getVolume());
  playing.value = false;
}

async function replay() {
  showVolumePopup.value = false;
  speaker.stop();
  playing.value = false;
  await play();
}

function onVolumeChange(val: string) {
  volume.value = parseInt(val) || 5;
  if (playing.value) replay();
}

function onSpeedChange(val: string) {
  speed.value = parseInt(val) || 10;
  if (playing.value) replay();
}

const voiceInfoText = computed(() => {
  const effectiveVoice = getEffectiveVoice();
  if (!effectiveVoice) return { lang: '-', name: '-' };
  const selected = selectVoice(effectiveVoice);
  return {
    lang: selected?.lang || effectiveVoice.lang || '-',
    name: selected?.name || 'Standard',
  };
});

// Close popup on outside click
function onDocumentClick(e: MouseEvent) {
  const target = e.target as HTMLElement;
  if (showVolumePopup.value && !target.closest('.relative')) {
    showVolumePopup.value = false;
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick);
});

// Auto-play when text changes (if autoPlay enabled)
watch(() => props.text, (newText) => {
  if (props.autoPlay && newText && props.voice) {
    play();
  }
});

// Auto-play on mount if enabled
if (props.autoPlay && props.text && props.voice) {
  play();
}

function stop() {
  speaker.stop();
  playing.value = false;
}

defineExpose({ stop, play });

onBeforeUnmount(() => {
  speaker.stop();
  document.removeEventListener('click', onDocumentClick);
});
</script>
