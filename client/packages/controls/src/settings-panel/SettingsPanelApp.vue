<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-emerald-400">Settings</h1>
            <p class="text-gray-400 text-sm mt-1">Einstellungen anpassen</p>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="loadData"
              :disabled="loading"
              class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors"
              title="Aktualisieren"
            >
              <svg class="w-6 h-6" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
            <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Zurueck">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading && !hasData" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-emerald-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Laden...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
        <h2 class="text-xl font-bold text-red-400 mb-2">Fehler</h2>
        <p class="text-red-300">{{ error }}</p>
      </div>
    </main>

    <!-- Main Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-6 space-y-4">

      <!-- Client Type Selector -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <div class="flex items-center gap-4">
          <label class="text-sm text-gray-400 whitespace-nowrap">Client Type:</label>
          <select
            v-model="selectedClientType"
            @change="loadData"
            class="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-emerald-400"
          >
            <option value="web">Web</option>
            <option value="xbox">Xbox</option>
            <option value="mobile">Mobile</option>
            <option value="desktop">Desktop</option>
          </select>
        </div>
      </section>

      <!-- Audio Settings -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Audio</h2>
        <div class="space-y-4">
          <SliderSetting
            label="Ambient Lautstaerke"
            :value="getProperty('audioAmbientVolume', '5')"
            :min="0" :max="10" :step="1"
            @update="setProperty('audioAmbientVolume', $event)"
          />
          <SliderSetting
            label="Effekt Lautstaerke"
            :value="getProperty('audioEffectVolume', '5')"
            :min="0" :max="10" :step="1"
            @update="setProperty('audioEffectVolume', $event)"
          />
          <SliderSetting
            label="Sprache Lautstaerke"
            :value="getProperty('speechVolume', '5')"
            :min="0" :max="10" :step="1"
            @update="setProperty('speechVolume', $event)"
          />
          <SliderSetting
            label="Sprache Geschwindigkeit"
            :value="getProperty('speechSpeed', '5')"
            :min="0" :max="10" :step="1"
            @update="setProperty('speechSpeed', $event)"
          />
          <ToggleSetting
            label="Dialog automatisch vorlesen"
            :value="getProperty('autoSpeech', 'false')"
            @update="setProperty('autoSpeech', $event)"
          />
        </div>
      </section>

      <!-- Graphics Settings -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Grafik</h2>
        <div class="space-y-4">
          <SliderSetting
            label="Bildqualitaet"
            :value="getProperty('screenQuality', '5')"
            :min="0" :max="10" :step="1"
            @update="setProperty('screenQuality', $event)"
          />
          <ToggleSetting
            label="Qualitaet im Leerlauf reduzieren"
            :value="getProperty('screenQualityIdle', 'true')"
            @update="setProperty('screenQualityIdle', $event)"
          />
          <SelectSetting
            label="Sichtweite"
            :value="getProperty('viewRange', '3')"
            :options="viewRangeOptions"
            @update="setProperty('viewRange', $event)"
          />
        </div>
      </section>

      <!-- Gameplay Settings -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Gameplay</h2>
        <div class="space-y-4">
          <ToggleSetting
            label="Start in Ego-Perspektive"
            :value="getProperty('startInEgoPerspective', 'false')"
            @update="setProperty('startInEgoPerspective', $event)"
          />
        </div>
      </section>

      <!-- Save Button -->
      <div class="flex justify-end">
        <button
          @click="saveSettings"
          :disabled="saving || !dirty"
          class="px-6 py-3 bg-emerald-600 hover:bg-emerald-500 rounded-lg text-white font-bold transition-colors disabled:opacity-50"
        >
          {{ saving ? 'Speichern...' : 'Einstellungen speichern' }}
        </button>
      </div>

      <!-- Success Message -->
      <div v-if="successMessage" class="bg-emerald-900/30 border border-emerald-700 rounded-lg p-4 text-center text-emerald-300">
        {{ successMessage }}
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineComponent, h } from 'vue';
import { apiService } from '@/services/ApiService';

// --- Sub-components defined inline ---

const SliderSetting = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
    min: { type: Number, required: true },
    max: { type: Number, required: true },
    step: { type: Number, required: true },
  },
  emits: ['update'],
  setup(props, { emit }) {
    return () => h('div', { class: 'flex items-center gap-4' }, [
      h('label', { class: 'text-sm text-gray-300 w-48 flex-shrink-0' }, props.label),
      h('input', {
        type: 'range',
        min: props.min,
        max: props.max,
        step: props.step,
        value: parseFloat(props.value),
        class: 'flex-1 accent-emerald-400',
        onInput: (e: Event) => {
          const val = (e.target as HTMLInputElement).value;
          emit('update', val);
        },
      }),
      h('span', { class: 'text-sm text-gray-400 w-12 text-right' }, parseFloat(props.value).toFixed(props.step < 1 ? (props.step < 0.1 ? 2 : 1) : 0)),
    ]);
  },
});

const ToggleSetting = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
  },
  emits: ['update'],
  setup(props, { emit }) {
    const isOn = () => props.value === 'true';
    return () => h('div', { class: 'flex items-center gap-4' }, [
      h('label', { class: 'text-sm text-gray-300 w-48 flex-shrink-0' }, props.label),
      h('button', {
        class: [
          'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
          isOn() ? 'bg-emerald-500' : 'bg-gray-600',
        ],
        onClick: () => emit('update', isOn() ? 'false' : 'true'),
      }, [
        h('span', {
          class: [
            'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
            isOn() ? 'translate-x-6' : 'translate-x-1',
          ],
        }),
      ]),
      h('span', { class: 'text-sm text-gray-400' }, isOn() ? 'An' : 'Aus'),
    ]);
  },
});

const SelectSetting = defineComponent({
  props: {
    label: { type: String, required: true },
    value: { type: String, required: true },
    options: { type: Array as () => { value: string; label: string }[], required: true },
  },
  emits: ['update'],
  setup(props, { emit }) {
    return () => h('div', { class: 'flex items-center gap-4' }, [
      h('label', { class: 'text-sm text-gray-300 w-48 flex-shrink-0' }, props.label),
      h('select', {
        value: props.value,
        class: 'flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-emerald-400',
        onChange: (e: Event) => emit('update', (e.target as HTMLSelectElement).value),
      }, props.options.map(opt =>
        h('option', { value: opt.value, selected: opt.value === props.value }, opt.label)
      )),
    ]);
  },
});

const viewRangeOptions = [
  { value: '2', label: 'Nah' },
  { value: '3', label: 'Normal' },
  { value: '4', label: 'Weit' },
];

// --- Interfaces ---

interface SettingsResponse {
  settings: {
    name: string;
    inputController: string;
    inputMappings: Record<string, string>;
    properties: Record<string, string>;
  } | null;
}

// --- State ---

const loading = ref(true);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);
const selectedClientType = ref('web');

const properties = ref<Record<string, string>>({});
const dirty = ref(false);

const hasData = computed(() => Object.keys(properties.value).length > 0);

// --- Property access ---

const getProperty = (key: string, defaultValue: string): string => {
  return properties.value[key] ?? defaultValue;
};

const setProperty = (key: string, value: string) => {
  properties.value[key] = value;
  dirty.value = true;
};

// --- API ---

const loadData = async () => {
  loading.value = true;
  error.value = null;
  successMessage.value = null;
  try {
    const response = await apiService.get<SettingsResponse>(`/control/player/settings?client=${selectedClientType.value}`);
    properties.value = response.settings?.properties ? { ...response.settings.properties } : {};
    dirty.value = false;
  } catch (err) {
    console.error('[SettingsPanel] Failed to load data:', err);
    error.value = 'Einstellungen konnten nicht geladen werden.';
  } finally {
    loading.value = false;
  }
};

const saveSettings = async () => {
  saving.value = true;
  successMessage.value = null;
  try {
    await apiService.put(`/control/player/settings?client=${selectedClientType.value}`, {
      properties: properties.value,
    });
    dirty.value = false;
    successMessage.value = 'Einstellungen gespeichert.';
    setTimeout(() => { successMessage.value = null; }, 3000);
  } catch (err) {
    console.error('[SettingsPanel] Failed to save settings:', err);
    error.value = 'Einstellungen konnten nicht gespeichert werden.';
  } finally {
    saving.value = false;
  }
};

// --- Lifecycle ---

onMounted(() => loadData());
</script>
