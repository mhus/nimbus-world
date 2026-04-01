<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-xl font-bold text-orange-400">Werkstatt</h1>
            <p class="text-gray-400 text-sm">{{ categoryLabel }}</p>
          </div>
          <button
            @click="closeWidget"
            class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors"
            title="Schliessen"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>
    </header>

    <!-- Loading -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-orange-400"></div>
    </main>

    <!-- Error -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
        <p class="text-red-300">{{ error }}</p>
      </div>
    </main>

    <!-- Crafting UI -->
    <main v-else class="flex-1 container mx-auto px-4 py-4 space-y-4">

      <!-- Material Slots -->
      <section class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-lg font-bold text-orange-400 mb-3">Materialien</h2>
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-2">
          <div
            v-for="(slot, idx) in materialSlots"
            :key="idx"
            class="bg-gray-700/50 border-2 rounded-lg p-3 min-h-[80px] flex flex-col items-center justify-center cursor-pointer transition-all"
            :class="slot.itemId
              ? 'border-orange-600 bg-orange-900/20'
              : 'border-gray-600 border-dashed hover:border-gray-500'"
            @click="openSlotPicker(idx)"
          >
            <template v-if="slot.itemId">
              <span class="text-sm font-medium text-orange-300">{{ slot.itemId }}</span>
              <span class="text-xs text-gray-400">x{{ slot.amount }}</span>
              <button
                class="text-xs text-red-400 hover:text-red-300 mt-1"
                @click.stop="clearSlot(idx)"
              >
                Entfernen
              </button>
            </template>
            <template v-else>
              <span class="text-gray-500 text-sm">Leer</span>
            </template>
          </div>
        </div>
      </section>

      <!-- Item Picker (inline) -->
      <section v-if="showPicker" class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <h2 class="text-sm font-bold text-gray-400 mb-2">Item waehlen (Rucksack)</h2>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="(amount, itemId) in backpackItems"
            :key="itemId"
            class="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm transition-colors"
            @click="selectItem(itemId as string, amount as number)"
          >
            {{ itemId }} <span class="text-gray-400">({{ amount }})</span>
          </button>
        </div>
        <button class="text-xs text-gray-500 hover:text-gray-400 mt-2" @click="showPicker = false">Abbrechen</button>
      </section>

      <!-- Try Recipe Result -->
      <section v-if="matchResult" class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <div v-if="matchResult.found" class="text-center">
          <p class="text-emerald-400 font-bold text-lg mb-2">Rezept gefunden!</p>
          <p class="text-gray-300">
            Ergebnis: <span class="text-orange-300 font-medium">{{ matchResult.resultAmount }}x {{ matchResult.resultItemId }}</span>
          </p>

          <!-- Spell Word Selection -->
          <div v-if="allowSpells && matchResult.allowSpells && spellWords.length > 0" class="mt-4">
            <h3 class="text-sm font-bold text-purple-400 mb-2">Zauberworte (optional)</h3>
            <div class="flex flex-wrap gap-2 justify-center">
              <button
                v-for="word in availableSpellWords"
                :key="word.name"
                @click="toggleSpellWord(word.name)"
                :class="[
                  'px-3 py-1.5 rounded text-sm font-medium transition-colors border',
                  selectedSpellWords.includes(word.name)
                    ? 'bg-purple-700 border-purple-500 text-purple-100'
                    : 'bg-gray-700 border-gray-600 text-gray-300 hover:bg-gray-600'
                ]"
              >
                {{ word.title || word.name }}
                <span class="text-xs opacity-70">(Lv{{ word.level }})</span>
              </button>
            </div>
            <p v-if="selectedSpellWords.length > 0" class="text-xs text-gray-500 mt-2">
              {{ selectedSpellWords.length }}/3 Worte gewaehlt
            </p>
          </div>

          <!-- Craft Button -->
          <button
            class="mt-4 px-6 py-3 bg-orange-600 hover:bg-orange-500 disabled:bg-gray-600 rounded-lg font-bold text-lg transition-colors"
            :disabled="crafting"
            @click="doCraft"
          >
            {{ crafting ? 'Herstellen...' : 'Herstellen!' }}
          </button>
        </div>
        <div v-else class="text-center text-gray-500">
          Diese Kombination ergibt nichts.
        </div>
      </section>

      <!-- Craft Result -->
      <section v-if="craftResult" class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
        <div v-if="craftResult.success" class="text-center">
          <p class="text-emerald-400 font-bold text-lg">Hergestellt!</p>
          <p class="text-gray-300 mt-1">{{ craftResult.resultItemId }}</p>
        </div>
        <div v-else class="text-center text-red-400">
          {{ craftResult.message || 'Herstellung fehlgeschlagen' }}
        </div>
      </section>

      <!-- Try Button -->
      <div class="flex justify-center">
        <button
          class="px-6 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition-colors"
          :disabled="filledSlotCount === 0 || trying"
          @click="tryRecipe"
        >
          {{ trying ? 'Pruefen...' : 'Rezept pruefen' }}
        </button>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface MaterialSlot {
  itemId: string | null;
  amount: number;
}

interface SpellWord {
  name: string;
  title: string;
  category: string;
  level: number;
}

interface MatchResult {
  found: boolean;
  recipeName?: string;
  resultItemId?: string;
  resultAmount?: number;
  allowSpells?: boolean;
  allowedSpellWords?: string[];
}

interface CraftResult {
  success: boolean;
  resultItemId?: string;
  message?: string;
  backpackItems?: Record<string, number>;
}

const progressId = new URLSearchParams(window.location.search).get('progressId') || '';

const loading = ref(true);
const error = ref<string | null>(null);
const category = ref('');
const slots = ref(4);
const allowSpells = ref(false);
const craftingLevel = ref(0);
const backpackItems = ref<Record<string, number>>({});
const spellWords = ref<SpellWord[]>([]);

const materialSlots = ref<MaterialSlot[]>([]);
const showPicker = ref(false);
const pickerSlotIdx = ref(0);
const matchResult = ref<MatchResult | null>(null);
const craftResult = ref<CraftResult | null>(null);
const selectedSpellWords = ref<string[]>([]);
const trying = ref(false);
const crafting = ref(false);

const categoryLabels: Record<string, string> = {
  smithing: 'Schmiede',
  weaving: 'Webstuhl',
  alchemy: 'Alchemie-Tisch',
  writing: 'Schreibtisch',
  woodworking: 'Werkbank',
};

const categoryLabel = computed(() => categoryLabels[category.value] || category.value);

const filledSlotCount = computed(() => materialSlots.value.filter(s => s.itemId).length);

const availableSpellWords = computed(() => {
  if (!matchResult.value?.allowedSpellWords?.length) return spellWords.value;
  const allowed = new Set(matchResult.value.allowedSpellWords);
  return spellWords.value.filter(w => allowed.has(w.name));
});

function initSlots(count: number) {
  materialSlots.value = Array.from({ length: count }, () => ({ itemId: null, amount: 0 }));
}

function openSlotPicker(idx: number) {
  pickerSlotIdx.value = idx;
  showPicker.value = true;
}

function selectItem(itemId: string, maxAmount: number) {
  const slot = materialSlots.value[pickerSlotIdx.value];
  slot.itemId = itemId;
  slot.amount = 1;
  showPicker.value = false;
  matchResult.value = null;
  craftResult.value = null;
}

function clearSlot(idx: number) {
  materialSlots.value[idx] = { itemId: null, amount: 0 };
  matchResult.value = null;
  craftResult.value = null;
}

function toggleSpellWord(word: string) {
  const idx = selectedSpellWords.value.indexOf(word);
  if (idx >= 0) {
    selectedSpellWords.value.splice(idx, 1);
  } else if (selectedSpellWords.value.length < 3) {
    selectedSpellWords.value.push(word);
  }
}

function buildMaterialsMap(): Record<string, number> {
  const materials: Record<string, number> = {};
  for (const slot of materialSlots.value) {
    if (slot.itemId) {
      materials[slot.itemId] = (materials[slot.itemId] || 0) + slot.amount;
    }
  }
  return materials;
}

async function tryRecipe() {
  trying.value = true;
  matchResult.value = null;
  craftResult.value = null;
  selectedSpellWords.value = [];

  try {
    const materials = buildMaterialsMap();
    matchResult.value = await apiService.post<MatchResult>(
      `/control/player/crafting-widget/try?progressId=${encodeURIComponent(progressId)}`,
      materials
    );
  } catch (e: any) {
    error.value = e.message || 'Fehler beim Pruefen';
  } finally {
    trying.value = false;
  }
}

async function doCraft() {
  if (!matchResult.value?.recipeName) return;
  crafting.value = true;
  craftResult.value = null;

  try {
    const result = await apiService.post<CraftResult>(
      `/control/player/crafting-widget/craft?progressId=${encodeURIComponent(progressId)}`,
      {
        recipeName: matchResult.value.recipeName,
        spellWords: selectedSpellWords.value.length > 0 ? selectedSpellWords.value : null,
      }
    );

    craftResult.value = result;

    if (result.success && result.backpackItems) {
      backpackItems.value = result.backpackItems;
    }

    // Reset slots after successful craft
    if (result.success) {
      initSlots(slots.value);
      matchResult.value = null;
      selectedSpellWords.value = [];
    }
  } catch (e: any) {
    craftResult.value = { success: false, message: e.message || 'Fehler beim Herstellen' };
  } finally {
    crafting.value = false;
  }
}

function closeWidget() {
  window.close();
}

onMounted(async () => {
  if (!progressId) {
    error.value = 'Kein progressId angegeben';
    loading.value = false;
    return;
  }

  try {
    const data = await apiService.get<{
      category: string;
      slots: number;
      allowSpells: boolean;
      craftingLevel: number;
      backpackItems: Record<string, number>;
      spellWords: SpellWord[];
    }>(`/control/player/crafting-widget?progressId=${encodeURIComponent(progressId)}`);

    category.value = data.category;
    slots.value = data.slots;
    allowSpells.value = data.allowSpells;
    craftingLevel.value = data.craftingLevel;
    backpackItems.value = data.backpackItems || {};
    spellWords.value = data.spellWords || [];
    initSlots(data.slots);
  } catch (e: any) {
    error.value = e.message || 'Station konnte nicht geladen werden';
  } finally {
    loading.value = false;
  }
});
</script>
