<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-cyan-400">Skills</h1>
            <p class="text-gray-400 text-sm mt-1">Verwalte deine Faehigkeiten</p>
          </div>
          <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Back to Panels">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </a>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-cyan-400 mx-auto"></div>
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
    <main v-else class="flex-1 container mx-auto px-4 py-6">
      <!-- Action Message -->
      <div v-if="actionMessage" class="mb-4 p-3 rounded-lg text-center text-sm font-medium transition-all"
           :class="actionMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ actionMessage.text }}
      </div>

      <!-- Experience & Skill Points Bar -->
      <div class="bg-gray-800 rounded-lg shadow-md p-4 border border-gray-700 mb-6">
        <div class="flex flex-wrap items-center gap-6">
          <!-- Skill Points -->
          <div class="flex items-center gap-2">
            <span class="text-gray-400 text-sm">Skill-Punkte:</span>
            <span class="text-2xl font-bold text-cyan-400">{{ availableSkillPoints }}</span>
            <span v-if="pointGainAnimation" class="text-lg font-bold text-green-400 animate-bounce">+ 1</span>
          </div>

          <!-- Experience Bar -->
          <div class="flex-1 min-w-[200px]">
            <div class="flex items-center justify-between text-sm text-gray-400 mb-1">
              <span>Erfahrung</span>
              <span>{{ skillExperience }} / {{ experienceToNext }}</span>
            </div>
            <div class="w-full bg-gray-700 rounded-full h-3 overflow-hidden">
              <div
                class="bg-cyan-500 h-3 rounded-full transition-all duration-500"
                :style="{ width: experiencePercent + '%' }"
              ></div>
            </div>
          </div>

          <!-- Convert Button -->
          <button
            @click="convertExperience"
            :disabled="converting || skillExperience < experienceToNext"
            class="px-4 py-2 rounded-lg font-semibold text-sm transition-all"
            :class="skillExperience >= experienceToNext
              ? 'bg-cyan-600 hover:bg-cyan-500 text-white cursor-pointer'
              : 'bg-gray-700 text-gray-500 cursor-not-allowed'"
          >
            {{ converting ? 'Umwandeln...' : 'Umwandeln' }}
          </button>
        </div>
      </div>

      <!-- Pending Changes Bar -->
      <div v-if="hasPendingChanges" class="bg-gray-800 rounded-lg shadow-md p-4 border border-cyan-700 mb-6">
        <div class="flex items-center justify-between">
          <div class="text-sm text-gray-400">
            <span class="text-cyan-400 font-semibold">{{ totalPendingPoints }}</span> Punkt(e) verteilt
            ({{ availableSkillPoints - totalPendingPoints }} verbleibend)
          </div>
          <div class="flex gap-2">
            <button
              @click="resetPending"
              class="px-4 py-2 rounded-lg font-semibold text-sm bg-gray-700 hover:bg-gray-600 text-gray-300 transition-all"
            >Zuruecksetzen</button>
            <button
              @click="confirmSave"
              :disabled="saving"
              class="px-4 py-2 rounded-lg font-semibold text-sm bg-cyan-600 hover:bg-cyan-500 text-white transition-all"
            >{{ saving ? 'Speichern...' : 'Speichern' }}</button>
          </div>
        </div>
      </div>

      <!-- Confirm Dialog -->
      <div v-if="showConfirm" class="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
        <div class="bg-gray-800 rounded-lg shadow-xl p-6 border border-gray-700 max-w-sm w-full mx-4">
          <h3 class="text-lg font-bold text-cyan-400 mb-3">Punkte verteilen?</h3>
          <div class="text-sm text-gray-300 mb-4 space-y-1">
            <div v-for="(points, skillName) in pendingAllocations" :key="skillName">
              <span class="text-cyan-300">+{{ points }}</span> {{ getSkillTitle(skillName) }}
            </div>
          </div>
          <div class="flex gap-2 justify-end">
            <button @click="showConfirm = false" class="px-4 py-2 rounded-lg text-sm bg-gray-700 hover:bg-gray-600 text-gray-300">Abbrechen</button>
            <button @click="doSave" class="px-4 py-2 rounded-lg text-sm bg-cyan-600 hover:bg-cyan-500 text-white font-semibold">Bestaetigen</button>
          </div>
        </div>
      </div>

      <!-- Skill Groups -->
      <div v-for="group in skillGroups" :key="group.name" class="mb-6">
        <h2 class="text-lg font-bold text-cyan-400 mb-3">{{ group.name }}</h2>
        <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 divide-y divide-gray-700">
          <div
            v-for="skill in group.skills"
            :key="skill.name"
            class="p-4 flex items-center gap-4"
          >
            <!-- Skill Info -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <h3 class="font-semibold text-gray-100">{{ skill.title }}</h3>
                <span v-if="!skill.free" class="text-gray-500" title="Nicht frei verteilbar">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
                  </svg>
                </span>
              </div>
              <p class="text-xs text-gray-500 mt-0.5">{{ skill.description }}</p>
            </div>

            <!-- Level Display & Controls -->
            <div class="flex items-center gap-2 flex-shrink-0">
              <!-- Minus Button (undo pending) -->
              <button
                v-if="skill.free && (pendingAllocations[skill.name] || 0) > 0"
                @click="decrementPending(skill.name)"
                class="w-8 h-8 rounded-full bg-gray-700 hover:bg-gray-600 text-gray-300 flex items-center justify-center text-lg font-bold transition-all"
              >-</button>
              <div v-else class="w-8"></div>

              <!-- Current Level -->
              <div class="text-center min-w-[60px]">
                <span class="text-xl font-bold" :class="(pendingAllocations[skill.name] || 0) > 0 ? 'text-cyan-300' : 'text-gray-200'">
                  {{ skill.current + (pendingAllocations[skill.name] || 0) }}
                </span>
                <span v-if="(pendingAllocations[skill.name] || 0) > 0" class="text-xs text-cyan-400 block">
                  (+{{ pendingAllocations[skill.name] }})
                </span>
                <span class="text-xs text-gray-500 block">/ {{ skill.max }}</span>
              </div>

              <!-- Plus Button -->
              <button
                v-if="skill.free && canIncrementSkill(skill)"
                @click="incrementPending(skill.name)"
                class="w-8 h-8 rounded-full bg-cyan-700 hover:bg-cyan-600 text-white flex items-center justify-center text-lg font-bold transition-all"
              >+</button>
              <div v-else-if="skill.free" class="w-8 h-8 rounded-full bg-gray-700 text-gray-600 flex items-center justify-center text-lg font-bold">+</div>
              <div v-else class="w-8 h-8 rounded-full bg-gray-800 text-gray-600 flex items-center justify-center">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>
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
  start: number;
  min: number;
  max: number;
  current: number;
}

interface SkillGroup {
  name: string;
  skills: SkillDef[];
}

interface SkillsResponse {
  skillPoints: number;
  skillExperience: number;
  experienceToNext: number;
  totalSkillPoints: number;
  skills: SkillDef[];
}

interface ConvertResponse {
  converted: boolean;
  skillPoints: number;
  skillExperience: number;
  experienceToNext: number;
}

const loading = ref(true);
const error = ref<string | null>(null);
const actionMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const availableSkillPoints = ref(0);
const skillExperience = ref(0);
const experienceToNext = ref(100);
const skills = ref<SkillDef[]>([]);
const pendingAllocations = ref<Record<string, number>>({});
const converting = ref(false);
const saving = ref(false);
const showConfirm = ref(false);
const pointGainAnimation = ref(false);

const experiencePercent = computed(() => {
  if (experienceToNext.value <= 0) return 0;
  return Math.min(100, (skillExperience.value / experienceToNext.value) * 100);
});

const skillGroups = computed<SkillGroup[]>(() => {
  const groups: Record<string, SkillDef[]> = {};
  for (const skill of skills.value) {
    if (!groups[skill.group]) groups[skill.group] = [];
    groups[skill.group].push(skill);
  }
  return Object.entries(groups).map(([name, skills]) => ({ name, skills }));
});

const totalPendingPoints = computed(() => {
  return Object.values(pendingAllocations.value).reduce((sum, v) => sum + v, 0);
});

const hasPendingChanges = computed(() => totalPendingPoints.value > 0);

const remainingPoints = computed(() => availableSkillPoints.value - totalPendingPoints.value);

const canIncrementSkill = (skill: SkillDef): boolean => {
  if (remainingPoints.value <= 0) return false;
  const pending = pendingAllocations.value[skill.name] || 0;
  return skill.current + pending < skill.max;
};

const getSkillTitle = (skillName: string): string => {
  return skills.value.find(s => s.name === skillName)?.title || skillName;
};

const incrementPending = (skillName: string) => {
  if (remainingPoints.value <= 0) return;
  pendingAllocations.value[skillName] = (pendingAllocations.value[skillName] || 0) + 1;
};

const decrementPending = (skillName: string) => {
  const current = pendingAllocations.value[skillName] || 0;
  if (current <= 1) {
    delete pendingAllocations.value[skillName];
  } else {
    pendingAllocations.value[skillName] = current - 1;
  }
};

const resetPending = () => {
  pendingAllocations.value = {};
};

const confirmSave = () => {
  showConfirm.value = true;
};

const doSave = async () => {
  showConfirm.value = false;
  saving.value = true;
  try {
    const result = await apiService.post<{ success: boolean; spent: number; message?: string }>(
      '/control/player/skills/spend',
      { allocations: pendingAllocations.value }
    );
    if (result.success) {
      showMessage(`${result.spent} Punkt(e) verteilt!`, 'success');
      pendingAllocations.value = {};
      await loadData();
    } else {
      showMessage(result.message || 'Fehler beim Speichern', 'error');
    }
  } catch (err) {
    console.error('[SkillPanel] Failed to save:', err);
    showMessage('Fehler beim Speichern', 'error');
  } finally {
    saving.value = false;
  }
};

const convertExperience = async () => {
  converting.value = true;
  try {
    const result = await apiService.post<ConvertResponse>('/control/player/skills/convert-experience', {});
    if (result.converted) {
      availableSkillPoints.value = result.skillPoints;
      skillExperience.value = result.skillExperience;
      experienceToNext.value = result.experienceToNext;

      // Show +1 animation
      pointGainAnimation.value = true;
      setTimeout(() => {
        pointGainAnimation.value = false;
      }, 800);

      // Try again after a short delay (multiple points possible)
      setTimeout(async () => {
        if (skillExperience.value >= experienceToNext.value) {
          await convertExperience();
        }
      }, 900);
    } else {
      availableSkillPoints.value = result.skillPoints;
      skillExperience.value = result.skillExperience;
      experienceToNext.value = result.experienceToNext;
    }
  } catch (err) {
    console.error('[SkillPanel] Failed to convert experience:', err);
    showMessage('Fehler beim Umwandeln', 'error');
  } finally {
    converting.value = false;
  }
};

const loadData = async () => {
  try {
    const response = await apiService.get<SkillsResponse>('/control/player/skills');
    availableSkillPoints.value = response.skillPoints;
    skillExperience.value = response.skillExperience;
    experienceToNext.value = response.experienceToNext;
    skills.value = response.skills;
  } catch (err) {
    console.error('[SkillPanel] Failed to load data:', err);
    error.value = 'Daten konnten nicht geladen werden.';
  }
};

const showMessage = (text: string, type: 'success' | 'error') => {
  actionMessage.value = { text, type };
  setTimeout(() => { actionMessage.value = null; }, 3000);
};

onMounted(async () => {
  loading.value = true;
  await loadData();
  loading.value = false;
});
</script>
