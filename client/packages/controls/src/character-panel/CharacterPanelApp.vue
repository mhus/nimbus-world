<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-emerald-400">Character</h1>
            <p class="text-gray-400 text-sm mt-1">Profil bearbeiten</p>
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

      <!-- Title -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Anzeigename</h2>
        <div class="flex gap-2">
          <input
            v-model="title"
            type="text"
            placeholder="Dein Anzeigename"
            maxlength="50"
            class="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-emerald-400"
          />
          <button
            @click="saveTitle"
            :disabled="saving"
            class="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 disabled:bg-gray-600 rounded font-medium transition-colors"
          >
            {{ saving ? '...' : 'Speichern' }}
          </button>
        </div>
        <p class="text-xs text-gray-500 mt-1">Max. 50 Zeichen</p>
      </section>

      <!-- Gender -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Geschlecht</h2>
        <div class="flex gap-2">
          <select
            v-model="gender"
            @change="saveGender"
            class="flex-1 bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-emerald-400"
          >
            <option value="">Nicht angegeben</option>
            <option value="M">Maennlich</option>
            <option value="F">Weiblich</option>
            <option value="D">Divers</option>
          </select>
        </div>
      </section>

      <!-- Portrait -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <div class="flex items-center gap-4 mb-3">
          <img
            v-if="displayPortraitUrl"
            :src="displayPortraitUrl"
            alt="Aktuelles Portrait"
            class="w-16 h-16 rounded-lg object-cover border-2 border-gray-600"
          />
          <div>
            <h2 class="text-lg font-bold text-emerald-400">Portrait</h2>
            <p class="text-xs text-gray-500">{{ portraitPath ? 'Gewaehlt' : 'Standard' }}</p>
          </div>
        </div>

        <!-- Filter -->
        <div class="flex gap-2 mb-4">
          <button
            v-for="filter in portraitFilters"
            :key="filter.value"
            @click="portraitFilter = filter.value"
            :class="[
              'px-3 py-1 rounded text-sm font-medium transition-colors',
              portraitFilter === filter.value
                ? 'bg-emerald-600 text-white'
                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
            ]"
          >
            {{ filter.label }}
          </button>
        </div>

        <!-- Portrait Grid -->
        <div v-if="filteredPortraits.length > 0" class="flex flex-wrap gap-2">
          <div
            v-for="portrait in filteredPortraits"
            :key="portrait.path"
            @click="selectPortrait(portrait.path)"
            :class="[
              'relative w-16 h-16 rounded-lg overflow-hidden cursor-pointer transition-all',
              portraitPath === portrait.path
                ? 'ring-3 ring-emerald-400 ring-offset-2 ring-offset-gray-800 scale-105'
                : 'hover:ring-2 hover:ring-gray-500 hover:scale-102'
            ]"
          >
            <img
              :src="getPortraitUrl(portrait.path)"
              :alt="portrait.name"
              class="w-16 h-16 object-cover"
              loading="lazy"
              @error="onImageError($event)"
            />
            <div
              v-if="portraitPath === portrait.path"
              class="absolute inset-0 bg-emerald-400/20 flex items-end justify-center"
            >
              <svg class="w-5 h-5 text-emerald-400 mb-1" fill="currentColor" viewBox="0 0 20 20">
                <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
              </svg>
            </div>
          </div>
        </div>

        <p v-else class="text-gray-500 text-sm">Keine Portraits verfuegbar.</p>
      </section>

      <!-- Avatar Model -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Avatar Modell</h2>

        <!-- Filter -->
        <div class="flex gap-2 mb-4">
          <button
            v-for="filter in modelFilters"
            :key="filter.value"
            @click="modelFilter = filter.value"
            :class="[
              'px-3 py-1 rounded text-sm font-medium transition-colors',
              modelFilter === filter.value
                ? 'bg-emerald-600 text-white'
                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
            ]"
          >
            {{ filter.label }}
          </button>
        </div>

        <!-- Model Grid -->
        <div v-if="filteredModels.length > 0" class="flex flex-wrap gap-2">
          <button
            v-for="model in filteredModels"
            :key="model.id"
            @click="selectModel(model.id)"
            :class="[
              'px-3 py-2 rounded-lg text-sm font-medium transition-all',
              thirdPersonModelId === model.id
                ? 'bg-emerald-600 text-white ring-2 ring-emerald-400'
                : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
            ]"
          >
            {{ model.name }}
          </button>
        </div>

        <p v-else class="text-gray-500 text-sm">Keine Modelle verfuegbar.</p>

        <p v-if="thirdPersonModelId" class="text-xs text-gray-500 mt-2">
          Gewaehlt: {{ thirdPersonModelId }}
        </p>
      </section>

      <!-- Model Modifiers + Preview -->
      <section v-if="selectedModel" class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Anpassungen &amp; Vorschau</h2>
        <div class="flex gap-4" :class="currentModifierKeys.length > 0 ? 'flex-col lg:flex-row' : ''">
          <!-- Modifiers -->
          <div v-if="currentModifierKeys.length > 0" class="flex-1 space-y-3">
            <div v-for="key in currentModifierKeys" :key="key">
              <label class="block text-sm text-gray-400 mb-1">{{ MODIFIER_LABELS[key] || key }}</label>
              <input
                v-model="modelModifiers[key]"
                type="text"
                :placeholder="key"
                maxlength="100"
                class="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2 text-gray-100 focus:outline-none focus:border-emerald-400"
              />
            </div>
            <button
              @click="saveModifiers"
              :disabled="saving"
              class="w-full px-4 py-2 bg-emerald-600 hover:bg-emerald-500 disabled:bg-gray-600 rounded font-medium transition-colors"
            >
              {{ saving ? '...' : 'Anpassungen speichern' }}
            </button>
          </div>
          <!-- 3D Preview -->
          <div class="flex-1 min-h-[300px]">
            <ModelPreview
              v-if="previewModelUrl"
              :model-url="previewModelUrl"
              :modifier-mapping="selectedModel?.modifierMapping || {}"
              :modifier-values="modelModifiers"
              class="w-full h-[300px]"
            />
          </div>
        </div>
      </section>

      <!-- Success Message -->
      <div v-if="successMessage" class="bg-emerald-900/30 border border-emerald-700 rounded-lg p-3 text-center">
        <p class="text-emerald-300 text-sm">{{ successMessage }}</p>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';
import ModelPreview from './ModelPreview.vue';

interface Portrait {
  path: string;
  name: string;
  category: string;
}

interface AvatarModel {
  id: string;
  name: string;
  gender: string;
  modelPath: string;
  modifierKeys: string[];
  modifierMapping: Record<string, string>;
}

const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const hasData = ref(false);
const successMessage = ref('');

const title = ref('');
const gender = ref('');
const portraitPath = ref('');
const portraits = ref<Portrait[]>([]);
const defaultPortrait = ref('');
const assetPrefix = ref('p:');
const portraitFilter = ref('auto');

const thirdPersonModelId = ref('');
const models = ref<AvatarModel[]>([]);
const modelFilter = ref('auto');
const modelModifiers = ref<Record<string, string>>({});


const portraitFilters = [
  { value: 'auto', label: 'Passend' },
  { value: 'all', label: 'Alle' },
  { value: 'male', label: 'Maennlich' },
  { value: 'female', label: 'Weiblich' },
];

const modelFilters = [
  { value: 'auto', label: 'Passend' },
  { value: 'all', label: 'Alle' },
  { value: 'male', label: 'Maennlich' },
  { value: 'female', label: 'Weiblich' },
];

function filterPortraitsByGender(items: Portrait[], filter: string, genderVal: string): Portrait[] {
  const isCommon = (item: Portrait) => item.category === 'common';
  if (filter === 'all') return items;
  if (filter === 'male') return items.filter(i => i.category === 'male' || isCommon(i));
  if (filter === 'female') return items.filter(i => i.category === 'female' || isCommon(i));
  // auto
  if (genderVal === 'M') return items.filter(i => i.category === 'male' || isCommon(i));
  if (genderVal === 'F') return items.filter(i => i.category === 'female' || isCommon(i));
  return items;
}

function filterModelsByGender(items: AvatarModel[], filter: string, genderVal: string): AvatarModel[] {
  const noGender = (item: AvatarModel) => !item.gender || item.gender === '';
  if (filter === 'all') return items;
  if (filter === 'male') return items.filter(i => i.gender === 'M' || noGender(i));
  if (filter === 'female') return items.filter(i => i.gender === 'F' || noGender(i));
  // auto
  if (genderVal === 'M') return items.filter(i => i.gender === 'M' || noGender(i));
  if (genderVal === 'F') return items.filter(i => i.gender === 'F' || noGender(i));
  return items;
}

const filteredPortraits = computed(() => filterPortraitsByGender(portraits.value, portraitFilter.value, gender.value));
const filteredModels = computed(() => filterModelsByGender(models.value, modelFilter.value, gender.value));

const selectedModel = computed(() => models.value.find(m => m.id === thirdPersonModelId.value));
const currentModifierKeys = computed(() => selectedModel.value?.modifierKeys || []);
const previewModelUrl = computed(() => {
  const model = selectedModel.value;
  if (!model?.modelPath) return '';
  // modelPath already includes prefix like "n:models/avatars/male/Worker.glb"
  return apiService.getBaseUrl() + '/control/player/assets/' + model.modelPath;
});

const MODIFIER_LABELS: Record<string, string> = {
  headSize: 'Kopfgroesse',
  bodySize: 'Koerpergroesse',
  skinColor: 'Hautfarbe',
  hairColor: 'Haarfarbe',
  eyeColor: 'Augenfarbe',
  clothingColor: 'Kleidungsfarbe',
  mainColor: 'Hauptfarbe',
  secondaryColor: 'Zweitfarbe',
  tailSize: 'Schwanzgroesse',
  wingSize: 'Fluegelgroesse',
  bodyColor: 'Koerperfarbe',
  stripeColor: 'Streifenfarbe',
};

const displayPortraitUrl = computed(() => {
  const path = portraitPath.value || defaultPortrait.value;
  return path ? getPortraitUrl(path) : '';
});

let successTimeout: ReturnType<typeof setTimeout> | null = null;

function showSuccess(msg: string) {
  successMessage.value = msg;
  if (successTimeout) clearTimeout(successTimeout);
  successTimeout = setTimeout(() => { successMessage.value = ''; }, 3000);
}

function getPortraitUrl(path: string): string {
  return apiService.getBaseUrl() + '/control/worlds/@shared:p/assets/' + assetPrefix.value + path;
}

function onImageError(event: Event) {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const [charResponse, portraitResponse, modelResponse] = await Promise.all([
      apiService.get<{ title: string; gender: string; portraitPath: string; thirdPersonModelId: string; thirdPersonModelModifiers: Record<string, string> }>('/control/player/character'),
      apiService.get<{ portraits: Portrait[]; defaultPortrait: string; assetPrefix: string }>('/control/player/character/portraits'),
      apiService.get<{ models: AvatarModel[] }>('/control/player/character/models'),
    ]);
    title.value = charResponse.title || '';
    gender.value = charResponse.gender || '';
    portraitPath.value = charResponse.portraitPath || '';
    thirdPersonModelId.value = charResponse.thirdPersonModelId || '';
    modelModifiers.value = charResponse.thirdPersonModelModifiers || {};
    portraits.value = portraitResponse.portraits || [];
    defaultPortrait.value = portraitResponse.defaultPortrait || '';
    assetPrefix.value = portraitResponse.assetPrefix || 'p:';
    models.value = modelResponse.models || [];
    hasData.value = true;
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Laden fehlgeschlagen';
  } finally {
    loading.value = false;
  }
}

async function saveTitle() {
  saving.value = true;
  try {
    await apiService.put('/control/player/character/title', { title: title.value });
    showSuccess('Anzeigename gespeichert');
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Speichern fehlgeschlagen';
  } finally {
    saving.value = false;
  }
}

async function saveGender() {
  saving.value = true;
  try {
    await apiService.put('/control/player/character/gender', { gender: gender.value });
    showSuccess('Geschlecht gespeichert');
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Speichern fehlgeschlagen';
  } finally {
    saving.value = false;
  }
}

async function selectPortrait(path: string) {
  const newPath = portraitPath.value === path ? '' : path;
  saving.value = true;
  try {
    await apiService.put('/control/player/character/portrait', { portraitPath: newPath });
    portraitPath.value = newPath;
    showSuccess(newPath ? 'Portrait gespeichert' : 'Portrait entfernt');
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Speichern fehlgeschlagen';
  } finally {
    saving.value = false;
  }
}

async function selectModel(id: string) {
  const newId = thirdPersonModelId.value === id ? '' : id;
  saving.value = true;
  try {
    await apiService.put('/control/player/character/model', { thirdPersonModelId: newId });
    thirdPersonModelId.value = newId;
    // Reset modifiers when model changes
    if (newId) {
      const model = models.value.find(m => m.id === newId);
      const newModifiers: Record<string, string> = {};
      for (const key of model?.modifierKeys || []) {
        newModifiers[key] = modelModifiers.value[key] || '';
      }
      modelModifiers.value = newModifiers;
    } else {
      modelModifiers.value = {};
    }
    showSuccess(newId ? 'Modell gespeichert' : 'Modell entfernt');
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Speichern fehlgeschlagen';
  } finally {
    saving.value = false;
  }
}

async function saveModifiers() {
  saving.value = true;
  try {
    await apiService.put('/control/player/character/modifiers', { modifiers: modelModifiers.value });
    showSuccess('Anpassungen gespeichert');
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Speichern fehlgeschlagen';
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>
