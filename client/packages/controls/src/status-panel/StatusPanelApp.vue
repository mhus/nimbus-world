<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-emerald-400">Status</h1>
            <p class="text-gray-400 text-sm mt-1">Charakter-Uebersicht</p>
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
    <main v-else class="flex-1 container mx-auto px-4 py-6 space-y-6">

      <!-- Vitals -->
      <section v-if="vitals.length > 0">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Vitalwerte</h2>
        <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 divide-y divide-gray-700">
          <div v-for="vital in vitals" :key="vital.type" class="p-4">
            <div class="flex items-center justify-between mb-1">
              <span class="font-semibold text-gray-200">{{ vital.displayName }}</span>
              <span class="text-sm text-gray-400">
                {{ formatNum(vital.current) }} / {{ formatNum(vital.effectiveMax) }}
                <span v-if="vital.effectiveRegenRate !== 0" class="ml-2" :class="vital.effectiveRegenRate > 0 ? 'text-green-400' : 'text-red-400'">
                  ({{ vital.effectiveRegenRate > 0 ? '+' : '' }}{{ formatNum(vital.effectiveRegenRate) }}/s)
                </span>
              </span>
            </div>
            <div class="w-full bg-gray-700 rounded-full h-3 overflow-hidden">
              <div
                class="h-3 rounded-full transition-all duration-300"
                :style="{ width: vitalPercent(vital) + '%', backgroundColor: vital.color }"
              ></div>
            </div>
          </div>
        </div>
      </section>

      <!-- Combat Stats (Defense) -->
      <section v-if="defenseStats.length > 0">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Passive Verteidigung</h2>
        <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-0 divide-y sm:divide-y-0 sm:divide-x divide-gray-700">
            <div v-for="stat in defenseStats" :key="stat.type" class="p-4">
              <div class="flex items-center justify-between">
                <span class="text-gray-300 text-sm">{{ formatStatName(stat.type) }}</span>
                <div class="text-right">
                  <span class="font-bold text-gray-100">{{ formatNum(stat.effective) }}</span>
                  <span v-if="stat.buffFlat !== 0 || stat.buffPercent !== 0" class="text-xs text-emerald-400 ml-1">
                    ({{ formatNum(stat.base) }}
                    <span v-if="stat.buffFlat > 0">+{{ formatNum(stat.buffFlat) }}</span>
                    <span v-if="stat.buffPercent !== 0"> x{{ formatNum(1 + stat.buffPercent) }}</span>)
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Skills grouped -->
      <section v-for="group in skillGroups" :key="group.name">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">{{ group.name }}</h2>
        <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 divide-y divide-gray-700">
          <div v-for="skill in group.skills" :key="skill.name" class="p-4 flex items-center justify-between">
            <div class="min-w-0">
              <div class="flex items-center gap-2">
                <span class="font-semibold text-gray-200">{{ skill.title }}</span>
                <span v-if="!skill.free" class="text-gray-500" title="Nicht frei verteilbar">
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </span>
              </div>
              <p class="text-xs text-gray-500">{{ skill.description }}</p>
            </div>
            <div class="text-right flex-shrink-0 ml-4">
              <span class="text-xl font-bold text-gray-100">{{ skill.current }}</span>
              <span class="text-xs text-gray-500"> / {{ skill.max }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- Constitution -->
      <section v-if="constitution.length > 0">
        <h2 class="text-lg font-bold text-emerald-400 mb-3">Zustand</h2>
        <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 divide-y divide-gray-700">
          <div v-for="con in constitution" :key="con.category" class="p-4">
            <div class="flex items-center justify-between mb-1">
              <span class="font-semibold text-gray-200 capitalize">{{ con.category }}</span>
              <span class="text-sm" :class="conColor(con.value)">{{ con.percent }}%</span>
            </div>
            <div class="w-full bg-gray-700 rounded-full h-2 overflow-hidden">
              <div
                class="h-2 rounded-full transition-all duration-300"
                :class="conBarColor(con.value)"
                :style="{ width: con.percent + '%' }"
              ></div>
            </div>
          </div>
        </div>
      </section>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface SkillDef {
  name: string;
  title: string;
  description: string;
  group: string;
  free: boolean;
  current: number;
  min: number;
  max: number;
}

interface SkillGroup {
  name: string;
  skills: SkillDef[];
}

interface VitalInfo {
  type: string;
  displayName: string;
  current: number;
  base: number;
  effectiveMax: number;
  baseRegenRate: number;
  effectiveRegenRate: number;
  color: string;
  order: number;
  options?: string;
}

interface CombatStatInfo {
  type: string;
  base: number;
  effective: number;
  buffFlat: number;
  buffPercent: number;
}

interface ConstitutionInfo {
  category: string;
  value: number;
  percent: number;
}

interface StatusResponse {
  skills: SkillDef[];
  skillPoints: number;
  skillExperience: number;
  constitution: ConstitutionInfo[];
  vitals: VitalInfo[];
  combatStats: CombatStatInfo[];
}

const loading = ref(true);
const error = ref<string | null>(null);
const skills = ref<SkillDef[]>([]);
const constitution = ref<ConstitutionInfo[]>([]);
const vitals = ref<VitalInfo[]>([]);
const combatStats = ref<CombatStatInfo[]>([]);

const hasData = computed(() => skills.value.length > 0 || vitals.value.length > 0);

const skillGroups = computed<SkillGroup[]>(() => {
  const groups: Record<string, SkillDef[]> = {};
  for (const skill of skills.value) {
    if (!groups[skill.group]) groups[skill.group] = [];
    groups[skill.group].push(skill);
  }
  return Object.entries(groups).map(([name, skills]) => ({ name, skills }));
});

const defenseStats = computed(() =>
  combatStats.value.filter(s =>
    s.type.includes('defense') || s.type.includes('evasion')
  )
);

const STAT_NAMES: Record<string, string> = {
  'physical.defense': 'Physische Verteidigung',
  'physical.evasion': 'Physisches Ausweichen',
  'magical.defense': 'Magische Verteidigung',
  'magical.evasion': 'Magisches Ausweichen',
  'physical.damage': 'Physischer Schaden',
  'physical.accuracy': 'Physische Treffsicherheit',
  'magical.damage': 'Magischer Schaden',
  'magical.accuracy': 'Magische Treffsicherheit',
  'attackSpeed': 'Angriffsgeschwindigkeit',
  'critChance': 'Kritische Trefferchance',
  'critMultiplier': 'Kritischer Multiplikator',
};

const formatStatName = (type: string): string => STAT_NAMES[type] || type;

const formatNum = (v: number): string => {
  if (Number.isInteger(v)) return v.toString();
  return v.toFixed(1);
};

const vitalPercent = (vital: VitalInfo): number => {
  if (vital.effectiveMax <= 0) return 0;
  return Math.min(100, (vital.current / vital.effectiveMax) * 100);
};

const conColor = (value: number): string => {
  if (value >= 0.7) return 'text-green-400';
  if (value >= 0.3) return 'text-yellow-400';
  return 'text-red-400';
};

const conBarColor = (value: number): string => {
  if (value >= 0.7) return 'bg-green-500';
  if (value >= 0.3) return 'bg-yellow-500';
  return 'bg-red-500';
};

const loadData = async () => {
  loading.value = true;
  error.value = null;
  try {
    const response = await apiService.get<StatusResponse>('/control/player/status');
    skills.value = response.skills || [];
    constitution.value = response.constitution || [];
    vitals.value = response.vitals || [];
    combatStats.value = response.combatStats || [];
  } catch (err) {
    console.error('[StatusPanel] Failed to load data:', err);
    error.value = 'Daten konnten nicht geladen werden.';
  } finally {
    loading.value = false;
  }
};

onMounted(() => loadData());
</script>
