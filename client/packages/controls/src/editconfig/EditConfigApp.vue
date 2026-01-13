<template>
  <div class="min-h-screen flex flex-col bg-base-200">
    <!-- Header (hidden in embedded mode) -->
    <header v-if="!isEmbedded()" class="navbar bg-base-300 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <h1 class="text-xl font-bold px-4">Edit Configuration</h1>
      </div>
      <div class="flex-none">
        <span class="text-sm text-base-content/70">Session: {{ sessionId }}</span>
      </div>
    </header>

    <!-- Main Content - Compact for iframe -->
    <main class="flex-1 px-1 py-2">
      <!-- Session ID Missing -->
      <div v-if="!sessionId" class="flex items-center justify-center min-h-[400px]">
        <div class="alert alert-warning max-w-md">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
          <span>No session found. Please provide sessionId parameter.</span>
        </div>
      </div>

      <!-- Main Editor -->
      <div v-else>
        <!-- Error Display -->
        <div v-if="error" class="alert alert-error alert-sm mb-2 text-xs">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-4 w-4" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{{ error }}</span>
        </div>

        <!-- Two column layout -->
        <div class="grid gap-1" style="grid-template-columns: 50% 50%;">
        <!-- Left Column: Edit Action, Navigator, Selected Block -->
        <div class="space-y-1">
          <!-- Edit Action Configuration - Compact -->
          <div class="card bg-base-100 shadow-sm">
            <div class="card-body p-2">
              <div class="flex justify-between items-center mb-2">
                <h2 class="card-title text-sm">Edit Action</h2>
                <button
                  @click="refreshAll"
                  class="btn btn-ghost btn-xs btn-circle"
                  title="Refresh all (layers, state, palette)"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                  </svg>
                </button>
              </div>

              <div class="form-control w-full">
                <select
                  v-model="currentEditAction"
                  class="select select-bordered select-sm w-full text-xs"
                  :disabled="saving"
                >
                  <option v-for="action in editActions" :key="action" :value="action">
                    {{ formatActionName(action) }}
                  </option>
                </select>
                <label class="label py-1">
                  <span class="label-text-alt text-xs">
                    {{ getActionDescription(currentEditAction) }}
                    <span v-if="saving" class="loading loading-spinner loading-xs ml-2"></span>
                  </span>
                </label>
              </div>
            </div>
          </div>

          <!-- Selected Block Display - Compact -->
          <div class="card bg-base-100 shadow-sm">
            <div class="card-body p-2">
              <h2 class="card-title text-sm mb-2">Selected Block</h2>

              <div v-if="selectedBlock" class="grid grid-cols-3 gap-2 text-center">
                <div class="bg-primary/10 rounded p-2">
                  <div class="text-xs text-base-content/70">X</div>
                  <div class="text-lg font-bold text-primary">{{ selectedBlock.x }}</div>
                </div>
                <div class="bg-secondary/10 rounded p-2">
                  <div class="text-xs text-base-content/70">Y</div>
                  <div class="text-lg font-bold text-secondary">{{ selectedBlock.y }}</div>
                </div>
                <div class="bg-accent/10 rounded p-2">
                  <div class="text-xs text-base-content/70">Z</div>
                  <div class="text-lg font-bold text-accent">{{ selectedBlock.z }}</div>
                </div>
              </div>

              <!-- Marked Block Display - Content -->
              <div v-if="markedBlockContent">
                <div class="bg-warning/10 rounded p-2">
                  <div class="flex items-center gap-2">
                    <!-- Block Icon/Texture -->
                    <div class="w-8 h-8 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
                      <img
                        v-if="markedBlockIcon"
                        :src="getTextureUrl(markedBlockIcon)"
                        :alt="markedBlockContent.name"
                        class="w-full h-full object-contain"
                        @error="markedBlockIcon = null"
                      />
                      <svg v-else class="w-4 h-4 text-base-content/30" fill="currentColor" viewBox="0 0 20 20">
                        <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z"/>
                      </svg>
                    </div>

                    <!-- Block Info -->
                    <div class="flex-1 min-w-0">
                      <div class="text-xs font-bold truncate" :title="markedBlockContent.name">
                        {{ markedBlockContent.name }}
                      </div>
                      <div class="text-xs text-base-content/50 font-mono truncate">
                        {{ markedBlockContent.blockTypeId }}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Block Palette - Collapsible -->
          <div class="collapse collapse-arrow bg-base-100 shadow-sm">
            <input type="checkbox" v-model="blocksPanelOpen" />
            <div class="collapse-title text-sm font-semibold p-2">
              Blocks ({{ palette.length }})
            </div>
            <div class="collapse-content">
              <div class="space-y-2">
                <!-- Add Marked Block Button -->
                <button
                  @click="addMarkedBlockToPalette"
                  :disabled="!markedBlockContent || addingToPalette"
                  class="btn btn-success btn-xs w-full"
                  title="Add currently marked block to palette"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                  </svg>
                  {{ addingToPalette ? 'Adding...' : 'Add Marked Block' }}
                </button>

                <!-- Palette Blocks List -->
                <div v-if="palette.length === 0" class="text-xs text-center text-base-content/50 py-4">
                  No blocks in palette. Mark a block and add it.
                </div>

                <div v-else class="space-y-1 max-h-64 overflow-y-auto">
                  <div
                    v-for="(paletteBlock, index) in palette"
                    :key="index"
                    class="relative group cursor-pointer"
                    @click="selectPaletteBlock(index)"
                  >
                    <div
                      class="bg-base-200 hover:bg-base-300 rounded p-2 transition-colors"
                      :class="{ 'ring-2 ring-primary': selectedPaletteIndex === index }"
                    >
                      <div class="flex items-center gap-2">
                        <!-- Block Icon/Texture -->
                        <div class="w-8 h-8 bg-base-300 rounded flex items-center justify-center flex-shrink-0">
                          <img
                            v-if="paletteBlock.icon"
                            :src="getTextureUrl(paletteBlock.icon)"
                            :alt="paletteBlock.name"
                            class="w-full h-full object-contain"
                            @error="handleImageError($event, index)"
                          />
                          <svg v-else class="w-4 h-4 text-base-content/30" fill="currentColor" viewBox="0 0 20 20">
                            <path d="M3 4a1 1 0 011-1h12a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V4zM3 10a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM14 9a1 1 0 00-1 1v6a1 1 0 001 1h2a1 1 0 001-1v-6a1 1 0 00-1-1h-2z"/>
                          </svg>
                        </div>

                        <!-- Block Info -->
                        <div class="flex-1 min-w-0">
                          <!-- Editable Name -->
                          <input
                            v-if="editingNameIndex === index"
                            v-model="editingName"
                            @click.stop
                            @blur="saveBlockName(index)"
                            @keyup.enter="saveBlockName(index)"
                            @keyup.esc="cancelEditName"
                            class="input input-xs input-bordered w-full text-xs font-bold"
                            autofocus
                          />
                          <div
                            v-else
                            class="text-xs font-bold truncate cursor-text"
                            :title="paletteBlock.name"
                            @dblclick.stop="startEditName(index)"
                          >
                            {{ paletteBlock.name }}
                          </div>

                          <!-- BlockType ID -->
                          <div class="text-xs text-base-content/50 font-mono truncate">
                            {{ paletteBlock.block.blockTypeId }}
                          </div>
                        </div>

                        <!-- Action Buttons -->
                        <div class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            @click.stop="startEditName(index)"
                            class="btn btn-xs btn-circle btn-ghost"
                            title="Edit name"
                          >
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                            </svg>
                          </button>
                          <button
                            @click.stop="removePaletteBlock(index)"
                            class="btn btn-xs btn-circle btn-error"
                            title="Remove from palette"
                          >
                            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Save Palette Button -->
                <button
                  v-if="palette.length > 0"
                  @click="savePalette"
                  :disabled="savingPalette"
                  class="btn btn-primary btn-xs w-full mt-2"
                >
                  <span v-if="savingPalette" class="loading loading-spinner loading-xs"></span>
                  <span v-else>💾 Save Palette</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Layer Selection, Edit Mode Control -->
        <div class="space-y-1">
          <!-- Layer Selection - NEW -->
          <div class="card bg-base-100 shadow-sm">
            <div class="card-body p-2">
              <div class="flex justify-between items-center mb-2">
                <h2 class="card-title text-sm">Layer Selection</h2>
                <div class="flex gap-1">
                  <a
                    :href="getLayerEditorUrl()"
                    target="_blank"
                    class="btn btn-primary btn-xs"
                    title="Open Layer Editor"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                    </svg>
                    Edit Layers
                  </a>
                  <button
                    @click="refreshAll"
                    class="btn btn-ghost btn-xs btn-circle"
                    title="Refresh all (layers, state, palette)"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                  </button>
                </div>
              </div>

              <div class="form-control w-full">
                <label class="label py-1">
                  <span class="label-text-alt text-xs">Edit Layer</span>
                </label>
                <select
                  v-model="editState.selectedLayer"
                  class="select select-bordered select-sm w-full text-xs"
                  :disabled="saving || editState.editMode"
                >
                  <option :value="null">All Layers (Legacy)</option>
                  <option v-for="layer in availableLayers" :key="layer.name" :value="layer.name">
                    {{ layer.name }} ({{ layer.layerType }}) - Order: {{ layer.order }}
                  </option>
                </select>
              </div>

              <!-- Model Selection for MODEL layers -->
              <div v-if="selectedLayerInfo?.layerType === 'MODEL'" class="mt-2">
                <label class="label py-1">
                  <span class="label-text-alt text-xs">Select Model</span>
                </label>
                <select
                  v-model="editState.selectedModelId"
                  class="select select-bordered select-sm w-full text-xs"
                  :disabled="saving || editState.editMode || loadingModels"
                >
                  <option :value="null">-- Select a model --</option>
                  <option v-for="model in availableModels" :key="model.id" :value="model.id">
                    {{ model.title || model.name || 'Unnamed' }} - Mount: ({{ model.mountX }}, {{ model.mountY }}, {{ model.mountZ }})
                  </option>
                </select>
                <label v-if="loadingModels" class="label py-1">
                  <span class="label-text-alt text-xs">
                    <span class="loading loading-spinner loading-xs"></span> Loading models...
                  </span>
                </label>
              </div>

              <!-- Group Selection -->
              <div class="form-control w-full mt-2">
                <label class="label py-1">
                  <span class="label-text-alt text-xs">Group</span>
                </label>
                <select
                  v-model.number="editState.selectedGroup"
                  class="select select-bordered select-sm w-full text-xs"
                  :disabled="saving || editState.editMode"
                >
                  <option :value="0">No Group (0)</option>
                  <option v-for="group in availableGroups" :key="group.id" :value="group.id">
                    {{ group.name }} ({{ group.id }})
                  </option>
                </select>
              </div>
            </div>
          </div>

          <!-- Edit Mode Control - NEW -->
          <div class="card bg-base-100 shadow-sm">
            <div class="card-body p-2">
              <h2 class="card-title text-sm mb-2">Edit Mode Control</h2>

              <!-- Status Display -->
              <div class="alert text-xs" :class="editState.editMode ? 'alert-success' : 'alert-info'">
                <div class="flex flex-col gap-1 w-full">
                  <span>
                    <strong>{{ editState.editMode ? '✓ ACTIVE' : '○ INACTIVE' }}</strong>
                  </span>
                  <span v-if="editState.editMode && editState.selectedLayer" class="text-xs opacity-80">
                    Layer: {{ editState.selectedLayer }}
                  </span>
                </div>
              </div>

              <!-- Activate Button (when inactive) -->
              <button
                v-if="!editState.editMode"
                @click="activateEditMode"
                :disabled="!editState.selectedLayer || activating"
                class="btn btn-primary btn-sm btn-block mt-2">
                <span v-if="activating" class="loading loading-spinner loading-xs"></span>
                <span v-else>Activate Edit Mode</span>
              </button>

              <!-- Action Buttons (when active) -->
              <div v-else class="flex gap-2 flex-col mt-2">
                <button @click="saveOverlays"
                        :disabled="saving"
                        class="btn btn-success btn-sm">
                  <span v-if="saving" class="loading loading-spinner loading-xs"></span>
                  <span v-else>💾 Save to Layer</span>
                </button>

                <button @click="changeLayer"
                        :disabled="changing"
                        class="btn btn-warning btn-sm">
                  <span v-if="changing" class="loading loading-spinner loading-xs"></span>
                  <span v-else>🔄 Change Layer</span>
                </button>

                <button @click="openDiscardModal"
                        :disabled="discarding"
                        class="btn btn-error btn-sm">
                  <span v-if="discarding" class="loading loading-spinner loading-xs"></span>
                  <span v-else>🗑️ Discard</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      </div><!-- end v-else -->
    </main>

    <!-- Footer (hidden in embedded mode) -->
    <footer v-if="!isEmbedded()" class="footer footer-center p-4 bg-base-300 text-base-content">
      <div>
        <p>Nimbus Edit Configuration v2.0.0</p>
      </div>
    </footer>

    <!-- Discard Confirmation Modal -->
    <dialog ref="discardModal" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">⚠️ Discard Changes?</h3>
        <div class="alert alert-warning my-4">
          <svg class="w-6 h-6 shrink-0" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd"/>
          </svg>
          <div>
            <p class="font-semibold">This will delete all cached changes for the current layer!</p>
            <p class="text-sm">Layer: {{ editState.selectedLayer }}</p>
            <p class="text-sm text-error">This action cannot be undone.</p>
          </div>
        </div>
        <div class="modal-action">
          <button @click="closeDiscardModal" class="btn btn-ghost">Cancel</button>
          <button @click="confirmDiscard" class="btn btn-error" :disabled="discarding">
            <span v-if="discarding" class="loading loading-spinner loading-xs"></span>
            <span v-else>Discard Changes</span>
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button>close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue';
import { useModal } from '@/composables/useModal';
import { EditAction, type PaletteBlockDefinition, type Block, type BlockType, getLogger } from '@nimbus/shared';
import { apiService } from '@/services/ApiService';

const logger = getLogger('EditConfigApp');

// Get all edit actions from enum
const editActions = Object.values(EditAction);

// Modal composable for embedded detection
const { isEmbedded } = useModal();

// Get URL parameters
const params = new URLSearchParams(window.location.search);
const worldId = ref(params.get('worldId'));
const sessionId = ref(params.get('sessionId') || '');
const apiUrl = computed(() => apiService.getBaseUrl());

// Edit State (unified)
const editState = ref({
  editMode: false,
  editAction: EditAction.OPEN_CONFIG_DIALOG,
  selectedLayer: null as string | null,
  selectedModelId: null as string | null,
  mountX: 0,
  mountY: 0,
  mountZ: 0,
  selectedGroup: 0,
});

// Available layers
const availableLayers = ref<Array<{
  name: string;
  layerType: string;
  enabled: boolean;
  order: number;
  mountX: number;
  mountY: number;
  mountZ: number;
  groups: Record<string, number>;
  id: string;
  layerDataId: string;
}>>([]);

// Available models for selected layer
const availableModels = ref<Array<{
  id: string;
  name: string;
  title: string;
  mountX: number;
  mountY: number;
  mountZ: number;
  order: number;
  groups: Record<string, number>;
}>>([]);

// Available groups from selected model
const availableGroups = ref<Array<{
  name: string;
  id: number;
}>>([]);

// Legacy state refs
const currentEditAction = ref<EditAction>(EditAction.OPEN_CONFIG_DIALOG);
const savedEditAction = ref<EditAction>(EditAction.OPEN_CONFIG_DIALOG);
const selectedBlock = ref<{ x: number; y: number; z: number } | null>(null);
const markedBlock = ref<{ x: number; y: number; z: number } | null>(null);
const error = ref<string | null>(null);
const saving = ref(false);

// Modal state
const discardModal = ref<HTMLDialogElement | null>(null);

// Edit mode control state
const activating = ref(false);
const discarding = ref(false);
const changing = ref(false);
const loadingModels = ref(false);

// Block Palette state
const palette = ref<PaletteBlockDefinition[]>([]);
const selectedPaletteIndex = ref<number | null>(null);
const blocksPanelOpen = ref(false);
const addingToPalette = ref(false);
const savingPalette = ref(false);
const editingNameIndex = ref<number | null>(null);
const editingName = ref('');

// Marked Block Content state
const markedBlockContent = ref<{ name: string; blockTypeId: string; block: Block } | null>(null);
const markedBlockIcon = ref<string | null>(null);
let markedBlockPollingInterval: number | null = null;

// Format action name for display
function formatActionName(action: EditAction): string {
  return action.replace(/_/g, ' ').toLowerCase()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

// Get action description
function getActionDescription(action: EditAction): string {
  switch (action) {
    case EditAction.OPEN_CONFIG_DIALOG:
      return 'Opens config dialog on block select';
    case EditAction.OPEN_EDITOR:
      return 'Opens block editor on select';
    case EditAction.MARK_BLOCK:
      return 'Marks block for paste';
    case EditAction.PASTE_BLOCK:
      return 'Pastes marked block to position';
    case EditAction.DELETE_BLOCK:
      return 'Deletes block at position';
    case EditAction.SMOOTH_BLOCKS:
      return 'Applies smoothing to block geometry';
    case EditAction.ROUGH_BLOCKS:
      return 'Applies rough geometry to blocks';
    case EditAction.CLONE_BLOCK:
      return 'Clones existing block from layer to position';
    default:
      return '';
  }
}

// Fetch available layers from API
async function fetchLayers() {
  try {
    const response = await fetch(`${apiUrl.value}/control/editor/${worldId.value}/layers`, {
      credentials: 'include'
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch layers: ${response.statusText}`);
    }

    const data = await response.json();
    availableLayers.value = data.layers || [];
  } catch (err) {
    console.error('Failed to fetch layers:', err);
    // Don't set error here, not critical for edit mode
  }
}

// Fetch available models for selected layer
async function fetchModels() {
  const layerInfo = selectedLayerInfo.value;
  console.log('[Models] fetchModels called', {
    hasLayerInfo: !!layerInfo,
    layerType: layerInfo?.layerType,
    layerId: layerInfo?.id,
    layerName: layerInfo?.name
  });

  if (!layerInfo || layerInfo.layerType !== 'MODEL' || !layerInfo.id) {
    console.log('[Models] Not fetching - layer not MODEL type or missing id');
    availableModels.value = [];
    return;
  }

  loadingModels.value = true;
  try {
    const url = `${apiUrl.value}/control/worlds/${worldId.value}/layers/${layerInfo.id}/models`;
    console.log('[Models] Fetching from:', url);

    const response = await fetch(url, { credentials: 'include' });

    console.log('[Models] Response status:', response.status);

    if (!response.ok) {
      const errorText = await response.text();
      console.error('[Models] Error response:', errorText);
      throw new Error(`Failed to fetch models: ${response.statusText}`);
    }

    const data = await response.json();
    console.log('[Models] Response data:', data);
    availableModels.value = data.models || [];
    console.log('[Models] Loaded', availableModels.value.length, 'models for layer', layerInfo.name);
  } catch (err) {
    console.error('[Models] Failed to fetch models:', err);
    availableModels.value = [];
  } finally {
    loadingModels.value = false;
  }
}

// Helper function to check if two values are deeply equal
function isEqual(a: any, b: any): boolean {
  if (a === b) return true;
  if (a == null || b == null) return false;
  if (typeof a !== 'object' || typeof b !== 'object') return false;

  const keysA = Object.keys(a);
  const keysB = Object.keys(b);

  if (keysA.length !== keysB.length) return false;

  for (const key of keysA) {
    if (!keysB.includes(key)) return false;
    if (!isEqual(a[key], b[key])) return false;
  }

  return true;
}

// Fetch edit state from API (NEW unified endpoint)
async function fetchEditState() {
  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/edit`,
      {
        credentials: 'include'
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to fetch edit state: ${response.statusText}`);
    }

    const data = await response.json();

    // Create new state object
    const newState = {
      editMode: data.editMode || false,
      editAction: (data.editAction as EditAction) || EditAction.OPEN_CONFIG_DIALOG,
      selectedLayer: data.selectedLayer || null,
      selectedModelId: data.selectedModelId || null,
      mountX: data.mountX || 0,
      mountY: data.mountY || 0,
      mountZ: data.mountZ || 0,
      selectedGroup: data.selectedGroup || 0,
    };

    // Only update if state actually changed
    if (!isEqual(editState.value, newState)) {
      editState.value = newState;

      // Update legacy refs for backward compatibility
      currentEditAction.value = editState.value.editAction;
      savedEditAction.value = editState.value.editAction;
    }

    // Update selected block only if changed
    const newSelectedBlock = data.selectedBlock || null;
    if (!isEqual(selectedBlock.value, newSelectedBlock)) {
      selectedBlock.value = newSelectedBlock;
    }

    error.value = null;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
    console.error('Failed to fetch edit state:', err);
  }
}

// Save edit state to API (NEW unified endpoint)
async function saveEditState() {
  saving.value = true;
  error.value = null;

  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/edit`,
      {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(editState.value),
        credentials: 'include'
      }
    );

    if (!response.ok) {
      throw new Error(`Failed to save edit state: ${response.statusText}`);
    }

    const data = await response.json();
    // Update saved state
    savedEditAction.value = data.editAction as EditAction;
    error.value = null;
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
    console.error('Failed to save edit state:', err);
  } finally {
    saving.value = false;
  }
}

// Computed: Get selected layer info
const selectedLayerInfo = computed(() => {
  if (!editState.value.selectedLayer) return null;
  return availableLayers.value.find(l => l.name === editState.value.selectedLayer);
});

// Generate Layer Editor URL with preserved parameters
function getLayerEditorUrl(): string {
  const params = new URLSearchParams(window.location.search);

  // Use relative path to layer-editor.html
  const baseUrl = 'layer-editor.html';

  // Preserve worldId and sessionId parameters
  const newParams = new URLSearchParams();
  if (params.get('worldId')) newParams.set('worldId', params.get('worldId')!);
  if (params.get('sessionId')) newParams.set('sessionId', params.get('sessionId')!);

  return `${baseUrl}?${newParams.toString()}`;
}

// Refresh all data (layers, edit state, palette)
async function refreshAll() {
  console.log('[Refresh] Reloading all data...');
  await Promise.all([
    fetchLayers(),
    fetchEditState(),
    loadEditSettings()
  ]);
  console.log('[Refresh] All data reloaded');
}

// Manual refresh - no automatic polling

// Watch for edit state changes and auto-save (with debounce)
let saveTimeout: number | null = null;
watch(editState, () => {
  if (saveTimeout) clearTimeout(saveTimeout);
  saveTimeout = window.setTimeout(async () => {
    await saveEditState();
  }, 500);
}, { deep: true });

// Watch currentEditAction for backward compatibility
watch(currentEditAction, (newAction) => {
  editState.value.editAction = newAction;
});

// Watch selectedLayer and load models for MODEL layers
watch(() => editState.value.selectedLayer, async (newLayer) => {
  // Clear model selection when layer changes
  editState.value.selectedModelId = null;
  availableGroups.value = [];

  if (newLayer) {
    await fetchModels();
  } else {
    availableModels.value = [];
  }
});

// Watch selectedModelId and load groups from model
watch(() => editState.value.selectedModelId, (newModelId) => {
  if (!newModelId) {
    availableGroups.value = [];
    editState.value.selectedGroup = 0;
    return;
  }

  // Find selected model and extract groups
  const model = availableModels.value.find(m => m.id === newModelId);
  if (model && model.groups) {
    // Convert Record<string, number> to Array<{name, id}>
    availableGroups.value = Object.entries(model.groups).map(([name, id]) => ({
      name,
      id
    }));
    console.log('[Groups] Loaded', availableGroups.value.length, 'groups from model');
  } else {
    availableGroups.value = [];
  }

  // Reset selected group when model changes
  editState.value.selectedGroup = 0;
});

// Watch selectedLayer and load groups for GROUND layers from WLayer
watch(() => editState.value.selectedLayer, (newLayer) => {
  const layerInfo = selectedLayerInfo.value;

  // If it's a GROUND layer, load groups from WLayer
  if (layerInfo && layerInfo.layerType === 'GROUND' && layerInfo.groups) {
    // Convert Record<string, number> to Array<{name, id}>
    availableGroups.value = Object.entries(layerInfo.groups).map(([name, id]) => ({
      name,
      id
    }));
    console.log('[Groups] Loaded', availableGroups.value.length, 'groups from GROUND layer');
  } else if (layerInfo && layerInfo.layerType === 'MODEL') {
    // For MODEL layers, groups will be loaded from the selected model (see watch above)
    // Clear groups here, they'll be populated when model is selected
    availableGroups.value = [];
  } else {
    // No layer or no groups
    availableGroups.value = [];
  }

  // Reset selected group when layer changes
  editState.value.selectedGroup = 0;
});

// Lifecycle hooks
onMounted(async () => {
  if (!sessionId.value) {
    // Show input form instead of error
    return;
  }

  await fetchLayers();
  await fetchEditState();
  await loadEditSettings();

  // Load models if a MODEL layer is already selected
  if (editState.value.selectedLayer) {
    await fetchModels();
  }

  // Start marked block content polling
  startMarkedBlockPolling();
});

// Cleanup on unmount
onBeforeUnmount(() => {
  stopMarkedBlockPolling();
});

// ===== EDIT MODE CONTROL FUNCTIONS =====

async function activateEditMode() {
  if (!editState.value.selectedLayer) {
    error.value = 'Please select a layer first';
    return;
  }

  activating.value = true;
  error.value = null;

  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/activate`,
      { method: 'POST', credentials: 'include' }
    );

    if (!response.ok) {
      const data = await response.json();
      throw new Error(data.error || 'Activation failed');
    }

    await fetchEditState(); // Refresh

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
  } finally {
    activating.value = false;
  }
}

function openDiscardModal() {
  discardModal.value?.showModal();
}

function closeDiscardModal() {
  discardModal.value?.close();
}

async function confirmDiscard() {
  discarding.value = true;

  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/discard`,
      { method: 'POST', credentials: 'include' }
    );

    if (!response.ok) throw new Error('Discard failed');

    closeDiscardModal();
    await fetchEditState(); // Refresh

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
  } finally {
    discarding.value = false;
  }
}

async function changeLayer() {
  changing.value = true;
  error.value = null;

  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/change`,
      { method: 'POST', credentials: 'include' }
    );

    if (!response.ok) {
      const data = await response.json();
      throw new Error(data.error || 'Change layer failed');
    }

    await fetchEditState(); // Refresh - edit mode will be false, layer/model cleared

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
  } finally {
    changing.value = false;
  }
}

async function saveOverlays() {
  saving.value = true;
  error.value = null;

  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/save`,
      { method: 'POST', headers: { 'Accept': 'application/json' }, credentials: 'include' }
    );

    if (!response.ok) throw new Error('Save failed');

    const data = await response.json();
    console.log('Save started:', data.message);
    // Optional: Show success notification

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error';
  } finally {
    saving.value = false;
  }
}

// ===== BLOCK PALETTE FUNCTIONS =====

// Load edit settings (palette) from API
async function loadEditSettings() {
  try {
    const response = await fetch(
      `${apiUrl.value}/control/editor/settings/worlds/${worldId.value}/editsettings?sessionId=${sessionId.value}`,
      {
        credentials: 'include'
      }
    );

    if (!response.ok) {
      console.log('[Palette] Failed to load settings (status not ok), will create on first save');
      palette.value = [];
      return;
    }

    const data = await response.json();

    // Check if response contains an error
    if (data.error) {
      console.log('[Palette] No existing settings found, will create on first save');
      palette.value = [];
      return;
    }

    palette.value = data.palette || [];
    console.log('[Palette] Loaded', palette.value.length, 'blocks from palette');
  } catch (err) {
    console.log('[Palette] Error loading settings:', err, '- will create on first save');
    // Don't show error to user, palette is optional and will be created on first save
    palette.value = [];
  }
}

// Get texture URL for block icon
function getTextureUrl(icon: string): string {
  return `${apiUrl.value}/control/worlds/${worldId.value}/assets/${icon}`;
}

// Handle image load error (fallback to placeholder)
function handleImageError(event: Event, index: number) {
  const target = event.target as HTMLImageElement;
  target.style.display = 'none';
}

// Add marked block to palette
async function addMarkedBlockToPalette() {
  console.log('[Palette] Add marked block clicked', {
    hasMarkedBlockContent: !!markedBlockContent.value,
    markedBlockContent: markedBlockContent.value
  });

  if (!markedBlockContent.value) {
    error.value = 'No block is currently marked';
    console.error('[Palette] Cannot add - no marked block content');
    return;
  }

  addingToPalette.value = true;
  error.value = null;

  try {
    // Use already loaded marked block content
    const blockData = markedBlockContent.value.block;
    const blockTypeName = markedBlockContent.value.name;
    const icon = markedBlockIcon.value || undefined;

    console.log('[Palette] Creating palette entry:', {
      name: blockTypeName,
      blockTypeId: blockData.blockTypeId,
      hasIcon: !!icon
    });

    // Create palette entry
    const paletteBlock: PaletteBlockDefinition = {
      block: blockData,
      name: blockTypeName,
      icon,
    };

    // Add to palette
    palette.value.push(paletteBlock);
    console.log('[Palette] Added to palette, new size:', palette.value.length);

    // Auto-save palette
    await savePalette();
    console.log('[Palette] Block successfully added and saved');

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to add block to palette';
    console.error('[Palette] Failed to add marked block to palette:', err);
  } finally {
    addingToPalette.value = false;
  }
}

// Select a palette block (sets as current marked block in Redis)
async function selectPaletteBlock(index: number) {
  selectedPaletteIndex.value = index;
  const paletteBlock = palette.value[index];

  console.log('[Palette] Selecting block:', paletteBlock.name);

  try {
    // Send block to Redis as marked block (will trigger polling update)
    const response = await fetch(
      `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/blockRegister`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(paletteBlock.block),
        credentials: 'include'
      }
    );

    if (!response.ok) {
      const errorText = await response.text();
      console.error('[Palette] Failed to set marked block:', errorText);
      throw new Error('Failed to set marked block');
    }

    const result = await response.json();
    console.log('[Palette] Marked block set successfully:', result);

    // Marked block content will be updated by polling within 2 seconds

  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to select block';
    console.error('[Palette] Failed to select palette block:', err);
  }
}

// Start editing block name
function startEditName(index: number) {
  editingNameIndex.value = index;
  editingName.value = palette.value[index].name;
}

// Cancel editing block name
function cancelEditName() {
  editingNameIndex.value = null;
  editingName.value = '';
}

// Save edited block name
function saveBlockName(index: number) {
  if (editingNameIndex.value === null) return;

  const newName = editingName.value.trim();
  if (newName) {
    palette.value[index].name = newName;
    console.log('[Palette] Name updated:', newName);
    // Auto-save after name change
    savePalette();
  }

  editingNameIndex.value = null;
  editingName.value = '';
}

// Remove block from palette
function removePaletteBlock(index: number) {
  palette.value.splice(index, 1);

  // Clear selection if removed block was selected
  if (selectedPaletteIndex.value === index) {
    selectedPaletteIndex.value = null;
  } else if (selectedPaletteIndex.value !== null && selectedPaletteIndex.value > index) {
    // Adjust selection index if block before selected was removed
    selectedPaletteIndex.value--;
  }

  // Auto-save after removal
  savePalette();
}

// Save palette to server
async function savePalette() {
  console.log('[Palette] Saving palette with', palette.value.length, 'blocks');
  savingPalette.value = true;
  error.value = null;

  try {
    const url = `${apiUrl.value}/control/editor/settings/worlds/${worldId.value}/editsettings/palette?sessionId=${sessionId.value}`;
    console.log('[Palette] POST to:', url);
    console.log('[Palette] Payload:', palette.value);

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(palette.value),
      credentials: 'include'
    });

    console.log('[Palette] Save response status:', response.status);

    if (!response.ok) {
      const errorData = await response.text();
      console.error('[Palette] Save failed with response:', errorData);
      throw new Error(`Failed to save palette: ${response.status} ${response.statusText}`);
    }

    const result = await response.json();
    console.log('[Palette] Save successful, response:', result);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to save palette';
    console.error('[Palette] Save error:', err);
  } finally {
    savingPalette.value = false;
  }
}

// ===== MARKED BLOCK POLLING =====

// Poll marked block content
async function pollMarkedBlockContent() {
  logger.debug('[Polling] Checking marked block...', {
    markedBlockPosition: markedBlock.value,
    worldId: worldId.value,
    sessionId: sessionId.value
  });

  try {
    // Always try to fetch - server will return 404 if no block marked
    const url = `${apiUrl.value}/control/editor/${worldId.value}/session/${sessionId.value}/blockRegister`;
    logger.debug('[Polling] Fetching:', url);

    const response = await fetch(url, {
      credentials: 'include'
    });

    logger.debug('[Polling] Response status:', response.status);

    if (!response.ok) {
      // No marked block or error - clear content
      if (markedBlockContent.value !== null) {
        logger.debug('[Polling] Clearing marked block content (status not ok)');
        markedBlockContent.value = null;
        markedBlockIcon.value = null;
      }
      return;
    }

    const responseData = await response.json();
    logger.debug('[Polling] Received response data:', responseData);

    // Check if response contains an error
    if (responseData.error) {
      logger.debug('[Polling] Server returned error:', responseData.error);
      // Clear content when error is present
      if (markedBlockContent.value !== null) {
        logger.debug('[Polling] Clearing marked block content (error in response)');
        markedBlockContent.value = null;
        markedBlockIcon.value = null;
      }
      return;
    }

    const blockData: Block = responseData;

    logger.debug('[Polling] Full block data received:', JSON.stringify(blockData, null, 2));

    // Check if content changed (compare blockTypeId)
    if (markedBlockContent.value?.blockTypeId === blockData.blockTypeId) {
      logger.debug('[Polling] Content unchanged, skipping update');
      return;
    }

    let blockTypeName = `Block ${blockData.blockTypeId}`;
    let icon: string | null = null;

    // First, try to get texture directly from block modifiers
    const firstBlockModifier = blockData.modifiers?.[0];
    if (firstBlockModifier) {
      logger.debug('[Polling] Block has modifier[0], checking:', JSON.stringify(firstBlockModifier, null, 2));

      if (firstBlockModifier.visibility?.textures) {
        const textures = firstBlockModifier.visibility.textures;
        logger.debug('[Polling] Found textures in block modifier:', JSON.stringify(textures, null, 2));

        // Get first available texture from numeric keys (0, 1, 2, ...)
        for (let i = 0; i < 6; i++) {
          const texture = textures[i];
          if (texture) {
            // If texture is a string, use it directly, otherwise get path from TextureDefinition
            icon = typeof texture === 'string' ? texture : texture.path;
            logger.debug('[Polling] Selected icon from block modifier key', i, ':', icon);
            break;
          }
        }
      }
    }

    // Only fetch BlockType if we don't have an icon yet
    if (!icon) {
      logger.debug('[Polling] No texture in block, fetching BlockType...');

      try {
        const blockTypeUrl = `${apiUrl.value}/control/worlds/${worldId.value}/blocktypes/type/${blockData.blockTypeId}`;
        logger.debug('[Polling] Fetching BlockType from:', blockTypeUrl);

        const blockTypeResponse = await fetch(blockTypeUrl, {
          credentials: 'include'
        });

        logger.debug('[Polling] BlockType response status:', blockTypeResponse.status);

        if (blockTypeResponse.ok) {
          const blockType: BlockType = await blockTypeResponse.json();
          logger.debug('[Polling] BlockType data:', JSON.stringify(blockType, null, 2));
          blockTypeName = blockType.description || blockTypeName;

          // Try to get texture from first modifier's visibility
          const firstModifier = blockType.modifiers?.[0];
          logger.debug('[Polling] First modifier:', JSON.stringify(firstModifier, null, 2));

          if (firstModifier?.visibility?.textures) {
            const textures = firstModifier.visibility.textures;
            logger.debug('[Polling] Textures object:', JSON.stringify(textures, null, 2));
            logger.debug('[Polling] Textures type:', typeof textures);
            logger.debug('[Polling] Textures keys:', Object.keys(textures));

            // Get first available texture from numeric keys (0, 1, 2, ...)
            for (let i = 0; i < 6; i++) {
              const texture = textures[i];
              if (texture) {
                // If texture is a string, use it directly, otherwise get path from TextureDefinition
                icon = typeof texture === 'string' ? texture : texture.path;
                logger.debug('[Polling] Selected icon from BlockType key', i, ':', icon);
                break;
              }
            }

            if (!icon) {
              logger.debug('[Polling] No texture found in numeric keys');
            }
          } else {
            logger.debug('[Polling] No textures found in first modifier');
          }
        } else {
          logger.debug('[Polling] BlockType fetch failed with status:', blockTypeResponse.status);
          const errorText = await blockTypeResponse.text();
          logger.debug('[Polling] Error response:', errorText);
        }
      } catch (err) {
        logger.error('[Polling] Failed to fetch BlockType details for marked block:', err);
      }
    } else {
      logger.debug('[Polling] Using texture from block data, skipping BlockType fetch');
    }

    // Truncate name if too long
    if (blockTypeName.length > 40) {
      blockTypeName = blockTypeName.substring(0, 37) + '...';
    }

    // Update content
    markedBlockContent.value = {
      name: blockTypeName,
      blockTypeId: blockData.blockTypeId,
      block: blockData,
    };
    markedBlockIcon.value = icon;

    logger.debug('Marked block content updated:', blockTypeName);

  } catch (err) {
    // Silent fail - polling will retry
    logger.debug('Failed to poll marked block content:', err);
  }
}

// Start marked block polling
function startMarkedBlockPolling() {
  if (markedBlockPollingInterval !== null) {
    logger.debug('[Polling] Already running, skipping start');
    return;
  }

  logger.debug('[Polling] Starting marked block polling (interval: 2s)');

  // Initial poll
  pollMarkedBlockContent();

  // Poll every 2 seconds
  markedBlockPollingInterval = window.setInterval(() => {
    pollMarkedBlockContent();
  }, 2000);
}

// Stop marked block polling
function stopMarkedBlockPolling() {
  if (markedBlockPollingInterval !== null) {
    clearInterval(markedBlockPollingInterval);
    markedBlockPollingInterval = null;
  }
}
</script>
