<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Loading State -->
    <main v-if="state === 'LOADING'" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading dialog...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="state === 'ERROR'" class="flex-1 flex items-center justify-center p-4">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center max-w-md">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
        <button @click="closeWidget" class="mt-4 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">
          Close
        </button>
      </div>
    </main>

    <!-- Dialog Content -->
    <main v-else-if="state === 'ACTIVE' && dialog" class="flex-1 flex flex-col max-w-2xl mx-auto w-full p-4">
      <!-- NPC Header -->
      <div class="flex items-center gap-4 mb-4 bg-gray-800 rounded-lg p-4 border border-gray-700">
        <div v-if="dialog.npcPortrait" class="flex-shrink-0">
          <img
            :src="portraitUrl"
            :alt="dialog.npcTitle"
            class="w-16 h-16 rounded-lg object-cover border border-gray-600"
            @error="($event.target as HTMLImageElement).style.display = 'none'"
          />
        </div>
        <div v-else class="w-16 h-16 rounded-lg bg-gray-700 flex items-center justify-center flex-shrink-0">
          <span class="text-2xl text-gray-500">?</span>
        </div>
        <div class="flex-1">
          <h1 class="text-xl font-bold text-amber-400">{{ dialog.npcTitle }}</h1>
        </div>
        <SpeechPlayer
          ref="speechPlayerRef"
          :text="dialog.text || ''"
          :voice="dialog.voice"
          :auto-play="autoSpeech"
          :settings-volume="speechVolume"
          :settings-speed="speechSpeed"
        />
      </div>

      <!-- NPC Text -->
      <div class="bg-gray-800 rounded-lg p-5 border border-gray-700 mb-4 min-h-[100px]">
        <p class="text-gray-200 leading-relaxed whitespace-pre-wrap">{{ displayedText }}</p>
        <span v-if="isTyping" class="inline-block w-1 h-4 bg-amber-400 animate-pulse ml-0.5"></span>
      </div>

      <!-- Options -->
      <div v-if="!submitting" class="flex flex-col gap-2 mb-4">
        <button
          v-for="option in dialog.options"
          :key="option.index"
          @click="selectOption(option.index)"
          class="w-full text-left px-4 py-3 bg-gray-800 hover:bg-amber-400/10 border border-gray-700
                 hover:border-amber-400/50 rounded-lg transition-colors duration-150 text-gray-200"
        >
          <span class="text-amber-400 mr-2">&gt;</span>{{ option.text }}
        </button>

        <!-- Close button when no options -->
        <button
          v-if="dialog.options.length === 0 && !dialog.freeTextEnabled"
          @click="closeWidget"
          class="w-full text-center px-4 py-3 bg-gray-800 hover:bg-amber-400/10 border border-gray-700
                 hover:border-amber-400/50 rounded-lg transition-colors duration-150 text-gray-400"
        >
          Close
        </button>
      </div>

      <!-- Free Text Input -->
      <div v-if="dialog.freeTextEnabled && !submitting" class="mt-auto">
        <div class="flex gap-2 items-center">
          <input
            v-model="freeTextInput"
            @keyup.enter="sendFreeText"
            type="text"
            placeholder="Was moechtest du sagen?"
            maxlength="500"
            :disabled="freeTextDisabled"
            class="flex-1 px-4 py-3 bg-gray-800 border border-gray-700 rounded-lg text-gray-200
                   placeholder-gray-500 focus:border-amber-400 focus:outline-none disabled:opacity-50"
          />
          <SpeechInput
            :lang="speechLang"
            @result="onDialogSpeechResult"
          />
          <button
            @click="sendFreeText"
            :disabled="!freeTextInput.trim() || freeTextDisabled"
            class="px-6 py-3 bg-amber-500 hover:bg-amber-400 text-gray-900 font-semibold rounded-lg
                   transition-colors duration-150 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Senden
          </button>
        </div>
        <p v-if="freeTextDisabled" class="text-xs text-gray-500 mt-1">
          Freitext gerade nicht verfuegbar
        </p>
      </div>

      <!-- Submitting indicator -->
      <div v-if="submitting" class="flex items-center justify-center py-4">
        <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-amber-400 mr-3"></div>
        <span class="text-gray-400">...</span>
      </div>
    </main>

    <!-- Finished State -->
    <main v-else-if="state === 'FINISHED'" class="flex-1 flex items-center justify-center p-4">
      <div class="text-center text-gray-400">
        <p class="mb-4">Dialog beendet.</p>
        <button @click="closeWidget" class="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">
          Close
        </button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { ApiService } from '@/services/ApiService';
import { type VoiceInfo } from '@/utils/VoiceSpeaker';
import SpeechPlayer from '@/components/SpeechPlayer.vue';
import SpeechInput from '@/components/SpeechInput.vue';

const apiService = new ApiService();

// State
type WidgetState = 'LOADING' | 'ERROR' | 'ACTIVE' | 'FINISHED';
const state = ref<WidgetState>('LOADING');
const error = ref('');
const submitting = ref(false);
const freeTextInput = ref('');
const freeTextDisabled = ref(false);

// Dialog data
interface OptionView {
  index: number;
  text: string;
}

interface DialogNodeResponse {
  progressId: string;
  npcTitle: string;
  npcPortrait: string | null;
  text: string;
  options: OptionView[];
  freeTextEnabled: boolean;
  finished: boolean;
  voice: VoiceInfo | null;
}

const dialog = ref<DialogNodeResponse | null>(null);
const progressId = ref('');
const speechPlayerRef = ref<InstanceType<typeof SpeechPlayer> | null>(null);

// User speech settings
const autoSpeech = ref(false);
const speechVolume = ref(5);
const speechSpeed = ref(5);

// Speech recognition language (derived from user language or voice lang)
const speechLang = ref('de-DE');

function onDialogSpeechResult(text: string, isFinal: boolean) {
  if (isFinal) {
    freeTextInput.value = (freeTextInput.value + ' ' + text).trim();
  }
}

// Typewriter effect
const displayedText = ref('');
const isTyping = ref(false);
let typewriterTimer: ReturnType<typeof setTimeout> | null = null;

const portraitUrl = computed(() => {
  if (!dialog.value?.npcPortrait) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${dialog.value.npcPortrait}`;
});

function startTypewriter(fullText: string) {
  if (typewriterTimer) clearTimeout(typewriterTimer);
  displayedText.value = '';
  isTyping.value = true;

  let index = 0;
  const speed = 30; // ms per character

  function tick() {
    if (index < fullText.length) {
      displayedText.value = fullText.substring(0, index + 1);
      index++;
      typewriterTimer = setTimeout(tick, speed);
    } else {
      isTyping.value = false;
    }
  }

  tick();
}

function skipTypewriter() {
  if (typewriterTimer) clearTimeout(typewriterTimer);
  if (dialog.value?.text) {
    displayedText.value = dialog.value.text;
  }
  isTyping.value = false;
}

function applyResponse(response: DialogNodeResponse) {
  dialog.value = response;

  if (response.finished) {
    speechPlayerRef.value?.stop();
    state.value = 'FINISHED';
    return;
  }

  state.value = 'ACTIVE';

  if (response.text) {
    startTypewriter(response.text);
    // SpeechPlayer handles auto-play via its autoPlay prop + watch on text
  } else {
    displayedText.value = '';
    isTyping.value = false;
  }
}

// API calls
async function loadDialog() {
  try {
    const response = await apiService.get<DialogNodeResponse>(
      `/control/player/dialog?progressId=${encodeURIComponent(progressId.value)}`
    );
    applyResponse(response);
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Failed to load dialog';
    state.value = 'ERROR';
  }
}

async function selectOption(optionIndex: number) {
  if (submitting.value) return;
  speechPlayerRef.value?.stop();
  if (isTyping.value) {
    skipTypewriter();
  }

  submitting.value = true;
  try {
    const response = await apiService.post<DialogNodeResponse>(
      '/control/player/dialog',
      { progressId: progressId.value, optionIndex }
    );
    applyResponse(response);
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Failed to process option';
    state.value = 'ERROR';
  } finally {
    submitting.value = false;
  }
}

async function sendFreeText() {
  const text = freeTextInput.value.trim();
  if (!text || submitting.value || isTyping.value) return;

  submitting.value = true;
  freeTextInput.value = '';

  try {
    const response = await apiService.post<DialogNodeResponse>(
      '/control/player/dialog',
      { progressId: progressId.value, freeText: text }
    );
    applyResponse(response);
  } catch (e: any) {
    if (e.response?.status === 503) {
      freeTextDisabled.value = true;
      // Still show options
      submitting.value = false;
      return;
    }
    error.value = e.response?.data?.error || e.message || 'Failed to process input';
    state.value = 'ERROR';
  } finally {
    submitting.value = false;
  }
}

function closeWidget() {
  speechPlayerRef.value?.stop();
  notifyDialogClose();
  window.close();
}

/** Notify server that dialog is closed so NPC can resume movement. */
function notifyDialogClose() {
  if (!progressId.value) return;
  const url = `${apiService.getBaseUrl()}/control/player/dialog/close?progressId=${encodeURIComponent(progressId.value)}`;
  // sendBeacon is reliable during page unload (sends POST)
  navigator.sendBeacon(url, '');
}

function handleBeforeUnload() {
  notifyDialogClose();
}

function handlePageHide() {
  notifyDialogClose();
}

// Initialize
onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const pid = params.get('progressId');

  if (!pid) {
    error.value = 'No progressId provided';
    state.value = 'ERROR';
    return;
  }

  progressId.value = pid;

  // Load user speech settings
  try {
    const settingsRes = await apiService.get<any>('/control/player/settings?client=web');
    const props = settingsRes?.settings?.properties;
    if (props) {
      autoSpeech.value = props['autoSpeech'] === 'true';
      speechVolume.value = parseInt(props['speechVolume']) || 5;
      speechSpeed.value = parseInt(props['speechSpeed']) || 5;
    }
  } catch { /* use defaults */ }

  loadDialog();

  // Listen for window/iframe close — multiple events for reliability
  window.addEventListener('beforeunload', handleBeforeUnload);
  window.addEventListener('pagehide', handlePageHide);
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload);
  window.removeEventListener('pagehide', handlePageHide);
  notifyDialogClose();
});
</script>
