<template>
  <div class="min-h-screen flex flex-col bg-gray-50">
    <!-- Header -->
    <header class="bg-blue-600 text-white shadow-lg">
      <div class="container mx-auto px-4 py-6">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-3xl font-bold">Editor Shortcut Panel</h1>
            <p class="text-blue-100 mt-2">Manage editor shortcuts for player</p>
          </div>
          <div class="flex gap-2">
            <a href="/controls/panels.html" class="p-2 rounded bg-blue-700 hover:bg-blue-800 transition-colors" title="Back to Panels">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <span class="loading loading-spinner loading-lg"></span>
    </main>

    <!-- Error State -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
        <svg class="w-16 h-16 mx-auto text-red-600 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <h2 class="text-xl font-bold text-red-900 mb-2">Error</h2>
        <p class="text-red-700">{{ error }}</p>
      </div>
    </main>

    <!-- Main Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-8">
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <!-- Available Templates (WAnything) -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">Available Templates</h2>
          <p class="text-sm text-gray-600 mb-4">
            Templates from collection 'editorShortcuts' for region {{ regionId }}
          </p>

          <div v-if="loadingTemplates" class="flex justify-center py-8">
            <span class="loading loading-spinner loading-md"></span>
          </div>

          <div v-else-if="templates.length === 0" class="text-center py-8 text-gray-500">
            No templates available
          </div>

          <div v-else class="space-y-2 max-h-96 overflow-y-auto">
            <div
              v-for="template in templates"
              :key="template.id"
              class="border border-gray-200 rounded p-3 hover:bg-gray-50 cursor-pointer transition-colors"
              @click="selectTemplate(template)"
            >
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <h3 class="font-semibold text-gray-800">{{ template.name }}</h3>
                  <p v-if="template.description" class="text-sm text-gray-600 mt-1">{{ template.description }}</p>
                  <div v-if="template.data" class="mt-2">
                    <span class="text-xs font-mono bg-gray-100 px-2 py-1 rounded">
                      {{ template.data.type || 'none' }}
                    </span>
                  </div>
                </div>
                <svg class="w-5 h-5 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <!-- Player Shortcut Slots -->
        <div class="bg-white rounded-lg shadow-md p-6">
          <h2 class="text-2xl font-bold text-gray-800 mb-4">Player Shortcut Slots</h2>
          <p class="text-sm text-gray-600 mb-4">
            Editor shortcuts for player {{ playerId }}
          </p>

          <div v-if="loadingPlayer" class="flex justify-center py-8">
            <span class="loading loading-spinner loading-md"></span>
          </div>

          <div v-else class="space-y-2 max-h-96 overflow-y-auto">
            <div
              v-for="(slot, slotKey) in editorShortcuts"
              :key="slotKey"
              class="border border-gray-200 rounded p-3"
            >
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="font-mono text-sm font-semibold text-blue-600">{{ slotKey }}</span>
                    <span v-if="slot" class="text-xs bg-green-100 text-green-800 px-2 py-1 rounded">Assigned</span>
                    <span v-else class="text-xs bg-gray-100 text-gray-600 px-2 py-1 rounded">Empty</span>
                  </div>

                  <div v-if="slot" class="mt-2">
                    <p class="text-sm font-medium text-gray-800">{{ slot.name || 'Unnamed' }}</p>
                    <p v-if="slot.description" class="text-xs text-gray-600 mt-1">{{ slot.description }}</p>
                    <div class="mt-2 flex gap-2 text-xs">
                      <span class="font-mono bg-gray-100 px-2 py-1 rounded">{{ slot.type }}</span>
                      <span v-if="slot.wait" class="font-mono bg-gray-100 px-2 py-1 rounded">wait: {{ slot.wait }}ms</span>
                    </div>
                  </div>
                  <div v-else class="text-sm text-gray-500 italic">
                    Click a template to assign
                  </div>
                </div>

                <button
                  v-if="slot"
                  @click="clearSlot(slotKey)"
                  class="p-2 text-red-600 hover:bg-red-50 rounded transition-colors"
                  title="Clear slot"
                >
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <div v-if="!loadingPlayer && Object.keys(editorShortcuts).length === 0" class="text-center py-8 text-gray-500">
            No shortcut slots available
          </div>

          <!-- Save Button -->
          <div v-if="!loadingPlayer && hasChanges" class="mt-6">
            <button
              @click="saveShortcuts"
              :disabled="saving"
              class="w-full bg-blue-600 text-white px-6 py-3 rounded-lg hover:bg-blue-700 disabled:bg-gray-400 transition-colors font-semibold"
            >
              <span v-if="saving">Saving...</span>
              <span v-else>Save Changes</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Selected Template Modal -->
      <div v-if="selectedTemplate" class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50" @click.self="selectedTemplate = null">
        <div class="bg-white rounded-lg p-6 max-w-md w-full mx-4">
          <h3 class="text-xl font-bold text-gray-800 mb-4">Assign Template to Slot</h3>
          <p class="text-sm text-gray-600 mb-4">
            Template: <strong>{{ selectedTemplate.name }}</strong>
          </p>

          <div class="space-y-2 mb-6 max-h-60 overflow-y-auto">
            <button
              v-for="slotKey in availableSlotKeys"
              :key="slotKey"
              @click="assignToSlot(slotKey)"
              class="w-full text-left px-4 py-2 border border-gray-200 rounded hover:bg-blue-50 hover:border-blue-300 transition-colors"
            >
              <span class="font-mono text-sm font-semibold text-blue-600">{{ slotKey }}</span>
              <span v-if="editorShortcuts[slotKey]" class="ml-2 text-xs text-gray-500">(will be replaced)</span>
            </button>
          </div>

          <button
            @click="selectedTemplate = null"
            class="w-full bg-gray-200 text-gray-800 px-6 py-2 rounded hover:bg-gray-300 transition-colors"
          >
            Cancel
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { authService } from '../home/services/AuthService';
import { apiService } from '@/services/ApiService';
import type { PlayerInfo } from '@nimbus/shared/types/PlayerInfo';
import type { ShortcutDefinition } from '@nimbus/shared/types/ShortcutDefinition';

interface AnythingEntity {
  id: string;
  regionId: string;
  worldId: string;
  collection: string;
  name: string;
  description?: string;
  type?: string;
  data: any;
  enabled: boolean;
}

const loading = ref(true);
const loadingTemplates = ref(false);
const loadingPlayer = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);

const worldId = ref<string>('');
const playerId = ref<string>('');
const regionId = ref<string>('');
const templates = ref<AnythingEntity[]>([]);
const playerInfo = ref<PlayerInfo | null>(null);
const editorShortcuts = ref<Record<string, ShortcutDefinition>>({});
const originalShortcuts = ref<Record<string, ShortcutDefinition>>({});
const selectedTemplate = ref<AnythingEntity | null>(null);

const hasChanges = computed(() => {
  return JSON.stringify(editorShortcuts.value) !== JSON.stringify(originalShortcuts.value);
});

const availableSlotKeys = computed(() => {
  return Object.keys(editorShortcuts.value).sort();
});

/**
 * Load authentication status and extract player info
 */
const loadAuthStatus = async () => {
  try {
    const authStatus = await authService.getStatus();

    if (!authStatus.authenticated) {
      error.value = 'Not authenticated. Please log in.';
      return false;
    }

    if (!authStatus.userId || !authStatus.characterId || !authStatus.worldId) {
      error.value = 'Missing player information in session.';
      return false;
    }

    // Extract regionId from worldId (format: regionId:worldName)
    const worldParts = authStatus.worldId.split(':');
    if (worldParts.length < 1) {
      error.value = 'Invalid worldId format.';
      return false;
    }

    worldId.value = authStatus.worldId;
    regionId.value = worldParts[0];
    playerId.value = `${authStatus.userId}:${authStatus.characterId}`;

    return true;
  } catch (err) {
    console.error('[EditorShortcutPanel] Failed to load auth status:', err);
    error.value = 'Failed to load authentication status.';
    return false;
  }
};

/**
 * Load templates from WAnything
 */
const loadTemplates = async () => {
  loadingTemplates.value = true;
  try {
    const response = await apiService.get<{ entities: AnythingEntity[] }>(
      `/control/anything/list?collection=editorShortcuts&regionId=${regionId.value}&enabledOnly=true`
    );

    templates.value = response.entities || [];
  } catch (err) {
    console.error('[EditorShortcutPanel] Failed to load templates:', err);
    error.value = 'Failed to load shortcut templates.';
  } finally {
    loadingTemplates.value = false;
  }
};

/**
 * Load player info and shortcuts
 */
const loadPlayerInfo = async () => {
  loadingPlayer.value = true;
  try {
    const response = await apiService.get<PlayerInfo>(`/control/player/playerinfo/${worldId.value}/${playerId.value}`);

    playerInfo.value = response;

    // Initialize editorShortcuts with default slots if not present
    if (!response.editorShortcuts) {
      editorShortcuts.value = {
        'slot0': null as any,
        'slot1': null as any,
        'slot2': null as any,
        'slot3': null as any,
        'slot4': null as any,
        'slot5': null as any,
        'slot6': null as any,
        'slot7': null as any,
        'slot8': null as any,
        'slot9': null as any,
        '0': null as any,
        '1': null as any,
        '2': null as any,
        '3': null as any,
        '4': null as any,
        '5': null as any,
        '6': null as any,
        '7': null as any,
        '8': null as any,
        '9': null as any,
        'click0': null as any,
        'click1': null as any,
        'click2': null as any,
        'click3': null as any,
        'click4': null as any,
        'click5': null as any,
        'click6': null as any,
        'click7': null as any,
        'click8': null as any,
        'click9': null as any,
      };
    } else {
      editorShortcuts.value = { ...response.editorShortcuts };
    }

    originalShortcuts.value = JSON.parse(JSON.stringify(editorShortcuts.value));
  } catch (err) {
    console.error('[EditorShortcutPanel] Failed to load player info:', err);
    error.value = 'Failed to load player information.';
  } finally {
    loadingPlayer.value = false;
  }
};

/**
 * Select a template
 */
const selectTemplate = (template: AnythingEntity) => {
  selectedTemplate.value = template;
};

/**
 * Assign template to a slot
 */
const assignToSlot = (slotKey: string) => {
  if (!selectedTemplate.value) return;

  // The template.data should have the structure of ShortcutDefinition
  const shortcut: ShortcutDefinition = selectedTemplate.value.data;

  editorShortcuts.value[slotKey] = shortcut;
  selectedTemplate.value = null;
};

/**
 * Clear a slot
 */
const clearSlot = (slotKey: string) => {
  editorShortcuts.value[slotKey] = null as any;
};

/**
 * Save shortcuts
 */
const saveShortcuts = async () => {
  saving.value = true;
  try {
    if (!playerInfo.value) {
      error.value = 'No player info loaded.';
      return;
    }

    // Update playerInfo with new shortcuts
    const updatedPlayerInfo: PlayerInfo = {
      ...playerInfo.value,
      editorShortcuts: editorShortcuts.value,
    };

    await apiService.put(`/control/player/playerinfo/${worldId.value}/${playerId.value}`, updatedPlayerInfo);

    originalShortcuts.value = JSON.parse(JSON.stringify(editorShortcuts.value));

    alert('Shortcuts saved successfully!');
  } catch (err) {
    console.error('[EditorShortcutPanel] Failed to save shortcuts:', err);
    alert('Failed to save shortcuts. Please try again.');
  } finally {
    saving.value = false;
  }
};

/**
 * Initialize component
 */
onMounted(async () => {
  loading.value = true;

  const authOk = await loadAuthStatus();
  if (!authOk) {
    loading.value = false;
    return;
  }

  await Promise.all([
    loadTemplates(),
    loadPlayerInfo(),
  ]);

  loading.value = false;
});
</script>
