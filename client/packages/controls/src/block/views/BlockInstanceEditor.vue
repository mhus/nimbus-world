<template>
  <div class="space-y-4">
    <!-- Loading State -->
    <div v-if="isLoading" class="flex justify-center py-12">
      <LoadingSpinner />
      <div class="ml-4 text-sm text-base-content/70">
        Loading block data...
      </div>
    </div>

    <!-- Error State -->
    <ErrorAlert v-else-if="error" :error="error" />

    <!-- No Coordinates -->
    <div v-else-if="!blockCoordinates" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No block coordinates specified</p>
      <p class="text-base-content/50 text-sm mt-2">
        Add <code>?block=x,y,z</code> to the URL
      </p>
    </div>

    <!-- Block Editor -->
    <div v-else class="space-y-4">
      <!-- Block Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between mb-4">
            <h2 class="card-title">
              Block at ({{ blockCoordinates.x }}, {{ blockCoordinates.y }}, {{ blockCoordinates.z }})
            </h2>
            <div class="flex gap-2">
              <div v-if="blockReadOnly" class="badge badge-warning" title="Block is read-only (no layer selected)">
                Read-Only
              </div>
              <div class="badge" :class="blockExists ? 'badge-primary' : 'badge-success'">
                {{ blockExists ? 'Existing Block' : 'New Block' }}
              </div>
            </div>
          </div>

          <!-- Two-column layout: BlockType/Status left (70%), Navigation right (30%) -->
          <div class="grid gap-2 mb-4" :style="showNavigator ? 'grid-template-columns: 70% 30%;' : 'grid-template-columns: 100%;'">
            <!-- Left Column: Block Type and Status -->
            <div class="space-y-4">
              <!-- Block Type Selection -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Block Type</span>
                  <div class="flex items-center gap-2">
                    <button
                      @click="showNavigator = !showNavigator"
                      class="btn btn-xs btn-ghost"
                      :title="showNavigator ? 'Hide Navigator' : 'Show Navigator'"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path v-if="!showNavigator" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
                        <path v-else stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
                      </svg>
                    </button>
                    <span class="label-text-alt text-error" v-if="!blockData.blockTypeId">Required</span>
                  </div>
                </label>

                <!-- Currently Selected Block Type -->
                <div
                  v-if="blockData.blockTypeId != '' && !showBlockTypeSearch"
                  class="p-3 bg-base-200 rounded-lg mb-2"
                >
                  <div class="mb-2">
                    <span class="font-mono font-bold">ID {{ blockData.blockTypeId }}</span>
                    <span class="mx-2">-</span>
                    <span v-if="selectedBlockType">{{ selectedBlockType.description || 'Unnamed' }}</span>
                    <span v-else class="text-base-content/50 italic">(BlockType details not loaded)</span>
                  </div>
                  <div class="flex gap-2">
                    <button class="btn btn-sm btn-primary" @click="clearBlockType">
                      Change
                    </button>
                    <a
                      :href="getBlockTypeEditorUrl(blockData.blockTypeId)"
                      target="_blank"
                      class="btn btn-sm btn-ghost"
                      title="Open BlockType in new tab"
                    >
                      Edit Type
                    </a>
                  </div>
                </div>

                <!-- Search Field (shown when changing or no block type selected) -->
                <div v-if="showBlockTypeSearch || blockData.blockTypeId === ''">
                  <SearchInput
                    v-model="blockTypeSearch"
                    placeholder="Search block types by ID or description..."
                    @search="handleBlockTypeSearch"
                  />

                  <!-- Search Results (shown when searching) -->
                  <div
                    v-if="blockTypeSearch && blockTypeSearchResults.length > 0"
                    class="mt-2 border border-base-300 rounded-lg max-h-60 overflow-y-auto bg-base-100"
                  >
                    <div
                      v-for="blockType in blockTypeSearchResults"
                      :key="blockType.id"
                      class="p-3 hover:bg-base-200 cursor-pointer border-b border-base-300 last:border-b-0"
                      @click="selectBlockType(blockType)"
                    >
                      <span class="font-mono font-bold">ID {{ blockType.id }}</span>
                      <span class="mx-2">-</span>
                      <span>{{ blockType.description || 'Unnamed' }}</span>
                    </div>
                  </div>

                  <!-- No Results -->
                  <div
                    v-else-if="hasSearched && blockTypeSearch && blockTypeSearchResults.length === 0 && !loadingBlockTypes"
                    class="mt-2 p-4 text-center text-base-content/50 text-sm"
                  >
                    No block types found for "{{ blockTypeSearch }}"
                  </div>
                </div>
              </div>

              <!-- Status and Level in same row -->
              <div class="grid grid-cols-2 gap-4">
                <!-- Status -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Status</span>
                    <span class="label-text-alt">0-255</span>
                  </label>
                  <input
                    v-model.number="blockData.status"
                    type="number"
                    min="0"
                    max="255"
                    class="input input-bordered"
                    placeholder="0"
                  />
                </div>

                <!-- Level (Wind Height) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Level</span>
                    <span class="label-text-alt">Wind height</span>
                  </label>
                  <input
                    v-model.number="levelValue"
                    type="number"
                    step="1"
                    class="input input-bordered"
                    placeholder="-1 (not set)"
                  />
                </div>
              </div>

              <!-- Help text for Level -->
              <div class="text-xs text-base-content/60 -mt-2">
                Integer multiplier for wind lever values. -1 or &lt; 0 = not set. 0 = no multiplier.
              </div>
            </div>

            <!-- Right Column: Navigate Component -->
            <div v-if="showNavigator" class="flex items-start justify-center">
              <NavigateSelectedBlockComponent
                :selected-block="blockCoordinates"
                :step="1"
                :size="140"
                :show-execute-button="false"
                @navigate="handleNavigate"
              />
            </div>
          </div>

          <!-- Geometry Offsets Section -->
          <CollapsibleSection
            title="Geometry Offsets"
            :model-value="hasOffsets"
            @update:model-value="toggleOffsets"
          >
            <OffsetsEditor
              v-model="blockData.offsets"
              :shape="currentShape"
            />
          </CollapsibleSection>

          <!-- Face Visibility Section -->
          <CollapsibleSection
            title="Face Visibility"
            :model-value="hasFaceVisibility"
            @update:model-value="toggleFaceVisibility"
          >
            <div class="space-y-3 pt-2">
              <p class="text-sm text-base-content/70">Control which faces are rendered</p>

              <!-- Face checkboxes in a grid -->
              <div class="grid grid-cols-3 gap-2">
                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(1)"
                    @change="toggleFace(1)"
                  />
                  <span class="label-text">Top</span>
                </label>

                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(2)"
                    @change="toggleFace(2)"
                  />
                  <span class="label-text">Bottom</span>
                </label>

                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(4)"
                    @change="toggleFace(4)"
                  />
                  <span class="label-text">Left</span>
                </label>

                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(8)"
                    @change="toggleFace(8)"
                  />
                  <span class="label-text">Right</span>
                </label>

                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(16)"
                    @change="toggleFace(16)"
                  />
                  <span class="label-text">Front</span>
                </label>

                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    class="checkbox checkbox-sm"
                    :checked="isFaceVisible(32)"
                    @change="toggleFace(32)"
                  />
                  <span class="label-text">Back</span>
                </label>
              </div>

              <!-- Fixed/Auto mode -->
              <div class="divider divider-start text-xs">Mode</div>
              <label class="label cursor-pointer justify-start gap-2">
                <input
                  type="checkbox"
                  class="checkbox checkbox-sm"
                  :checked="isFixedMode"
                  @change="toggleFixedMode"
                />
                <span class="label-text">Fixed Mode (disable auto-calculation)</span>
              </label>

              <!-- Current value display -->
              <div class="text-xs text-base-content/50">
                Bitfield value: {{ typeof blockData.faceVisibility === 'number' ? blockData.faceVisibility : 0 }}
              </div>
            </div>
          </CollapsibleSection>

          <!-- Rotation Section -->
          <CollapsibleSection
            title="Rotation"
            :model-value="hasRotation"
            @update:model-value="toggleRotation"
          >
            <div class="space-y-2 pt-2">
              <div class="grid grid-cols-2 gap-2">
                <div class="form-control">
                  <label class="label py-0">
                    <span class="label-text text-xs">Rotation X</span>
                  </label>
                  <input
                    v-model.number="rotationX"
                    type="number"
                    step="1"
                    class="input input-bordered input-sm"
                    placeholder="0"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-0">
                    <span class="label-text text-xs">Rotation Y</span>
                  </label>
                  <input
                    v-model.number="rotationY"
                    type="number"
                    step="1"
                    class="input input-bordered input-sm"
                    placeholder="0"
                  />
                </div>
              </div>
              <label class="label">
                <span class="label-text-alt">
                  Rotation around X/Y axes in degrees. Leave disabled to use default rotation.
                </span>
              </label>
            </div>
          </CollapsibleSection>

          <!-- Metadata Section -->
          <CollapsibleSection
            title="Metadata"
            :model-value="hasMetadata"
            :default-open="false"
            @update:model-value="toggleMetadata"
          >
            <div class="space-y-3">
              <!-- ID (full width) -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">ID</span>
                  <span class="label-text-alt">Unique identifier</span>
                </label>
                <input
                  v-model="blockData.metadata.id"
                  type="text"
                  class="input input-bordered input-sm"
                  placeholder="Optional ID"
                />
              </div>

              <!-- Title (full width) -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">Title</span>
                  <span class="label-text-alt">Display name</span>
                </label>
                <input
                  v-model="blockData.metadata.title"
                  type="text"
                  class="input input-bordered input-sm"
                  placeholder="Optional title"
                />
              </div>

              <!-- Group ID -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">Group ID</span>
                  <span class="label-text-alt">Group identifier</span>
                </label>
                <input
                  v-model="blockData.metadata.groupId"
                  type="text"
                  class="input input-bordered input-sm"
                  placeholder="Optional group ID"
                />
              </div>

              <!-- Server Metadata -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Server Properties</span>
                  <span class="label-text-alt">Not sent to client</span>
                </label>

                <!-- Existing server properties -->
                <div v-if="blockData.metadata.server && Object.keys(blockData.metadata.server).length > 0" class="space-y-2">
                  <div
                    v-for="(value, key) in blockData.metadata.server"
                    :key="`server-${key}`"
                    class="flex gap-2 items-center"
                  >
                    <input
                      :value="key"
                      @blur="updateServerKey(key, ($event.target as HTMLInputElement).value, value)"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Key"
                    />
                    <input
                      v-model="blockData.metadata.server[key]"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Value"
                    />
                    <button
                      @click="deleteServerProperty(key)"
                      class="btn btn-xs btn-error btn-ghost"
                      title="Delete property"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>

                <!-- Add new server property -->
                <button
                  @click="addServerProperty"
                  class="btn btn-xs btn-outline w-full mt-2"
                >
                  + Add Server Property
                </button>
              </div>

              <!-- Client Metadata -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Client Properties</span>
                  <span class="label-text-alt">Block properties for client</span>
                </label>

                <!-- Existing client properties -->
                <div v-if="blockData.metadata.client && Object.keys(blockData.metadata.client).length > 0" class="space-y-2">
                  <div
                    v-for="(value, key) in blockData.metadata.client"
                    :key="`client-${key}`"
                    class="flex gap-2 items-center"
                  >
                    <input
                      :value="key"
                      @blur="updateClientKey(key, ($event.target as HTMLInputElement).value, value)"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Key"
                    />
                    <input
                      v-model="blockData.metadata.client[key]"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Value"
                    />
                    <button
                      @click="deleteClientProperty(key)"
                      class="btn btn-xs btn-error btn-ghost"
                      title="Delete property"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                </div>

                <!-- Add new client property -->
                <button
                  @click="addClientProperty"
                  class="btn btn-xs btn-outline w-full mt-2"
                >
                  + Add Client Property
                </button>
              </div>
            </div>
          </CollapsibleSection>

          <!-- Modifiers Section -->
          <div class="divider">Modifiers (per Status)</div>

          <div class="space-y-2">
            <div
              v-for="(modifier, status) in blockData.modifiers"
              :key="status"
              class="flex items-center gap-2 p-2 bg-base-200 rounded"
            >
              <span class="font-mono text-sm">Status {{ status }}:</span>
              <span class="flex-1 text-sm text-base-content/70">Modifier defined</span>
              <button class="btn btn-xs btn-ghost" @click="editModifier(Number(status))">
                Edit
              </button>
              <button class="btn btn-xs btn-error btn-ghost" @click="deleteModifier(Number(status))">
                Delete
              </button>
            </div>

            <button class="btn btn-sm btn-outline w-full" @click="addModifier">
              + Add Modifier for Status {{ blockData.status ?? 0 }}
            </button>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <!-- Button Groups: Two rows -->
          <div class="space-y-2">
            <!-- Row 1: Utility buttons -->
            <div class="flex gap-2 flex-wrap">
              <button class="btn btn-outline btn-xs" @click="showJsonEditor = true">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
                </svg>
                Source
              </button>
              <button
                class="btn btn-outline btn-xs btn-secondary"
                @click="showBlockOrigin"
                :disabled="!blockExists || loadingOrigin"
                title="Show where this block comes from (layer, terrain, model)"
              >
                <span v-if="loadingOrigin" class="loading loading-spinner loading-xs"></span>
                <svg v-else class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                Origin
              </button>
              <button
                class="btn btn-outline btn-xs btn-info"
                @click="openSaveAsBlockTypeDialog"
                :disabled="!isValid || saving"
                title="Save this custom block as a new BlockType template"
              >
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7H5a2 2 0 00-2 2v9a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-3m-1 4l-3 3m0 0l-3-3m3 3V4" />
                </svg>
                Save as Type
              </button>
            </div>

            <!-- Row 2: Main action buttons -->
            <div class="flex justify-between gap-2">
              <button
                class="btn btn-error btn-sm"
                @click="handleDelete"
                :disabled="!blockExists || saving"
              >
                Delete Block
              </button>

              <div class="flex gap-2">
                <button class="btn btn-ghost" @click="handleCancel" :disabled="saving">
                  Cancel
                </button>
              <button
                class="btn btn-primary"
                @click="handleApply"
                :disabled="!isValid || saving || !hasChanges || blockReadOnly"
                :title="blockReadOnly ? 'Block is read-only (no layer selected)' : ''"
              >
                {{ saving ? 'Saving...' : 'Apply' }}
              </button>
              <button
                class="btn btn-success"
                @click="handleSave"
                :disabled="!isValid || saving || !hasChanges || blockReadOnly"
                :title="blockReadOnly ? 'Block is read-only (no layer selected)' : ''"
              >
                {{ saving ? 'Saving...' : 'Save & Close' }}
              </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Modifier Editor Dialog -->
    <ModifierEditorDialog
      v-if="showModifierDialog"
      :modifier="editingModifier!"
      :status-number="editingStatus!"
      :world-id="worldId"
      @close="showModifierDialog = false"
      @save="handleModifierSave"
    />

    <!-- JSON Editor Dialog -->
    <JsonEditorDialog
      v-model:is-open="showJsonEditor"
      :model-value="blockData"
      @apply="handleJsonApply"
    />

    <!-- Confirm Dialog -->
    <dialog ref="confirmDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">{{ confirmTitle }}</h3>
        <p class="py-4">{{ confirmMessage }}</p>
        <div class="modal-action">
          <button class="btn" @click="handleConfirmCancel">Cancel</button>
          <button class="btn btn-error" @click="handleConfirmOk">{{ confirmOkText }}</button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="handleConfirmCancel">close</button>
      </form>
    </dialog>

    <!-- Save as BlockType Dialog -->
    <dialog ref="saveAsBlockTypeDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Save as BlockType</h3>
        <p class="py-4 text-sm text-base-content/70">
          Convert this custom block instance into a reusable BlockType template.
        </p>

        <div class="form-control">
          <label class="label">
            <span class="label-text font-semibold">BlockType ID</span>
            <span class="label-text-alt text-error" v-if="!newBlockTypeId">Required</span>
          </label>
          <input
            v-model="newBlockTypeId"
            type="text"
            class="input input-bordered"
            placeholder="e.g., custom:my-block or w/123"
            @keyup.enter="handleSaveAsBlockType"
          />
          <label class="label">
            <span class="label-text-alt">Use format: group:name or group/name (e.g., custom:stone, w/123)</span>
          </label>
        </div>

        <div v-if="saveAsBlockTypeError" class="alert alert-error mt-4">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>{{ saveAsBlockTypeError }}</span>
        </div>

        <div class="modal-action">
          <button class="btn" @click="closeSaveAsBlockTypeDialog" :disabled="savingAsBlockType">Cancel</button>
          <button
            class="btn btn-primary"
            @click="handleSaveAsBlockType"
            :disabled="!newBlockTypeId || savingAsBlockType"
          >
            {{ savingAsBlockType ? 'Saving...' : 'Save as BlockType' }}
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="closeSaveAsBlockTypeDialog">close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { Block, BlockModifier, BlockType } from '@nimbus/shared';
import { useModal } from '@/composables/useModal';
import { useBlockTypes } from '@/composables/useBlockTypes';
import LoadingSpinner from '@/components/LoadingSpinner.vue';
import ErrorAlert from '@/components/ErrorAlert.vue';
import SearchInput from '@/components/SearchInput.vue';
import CollapsibleSection from '@/components/CollapsibleSection.vue';
import OffsetsEditor from '@editors/OffsetsEditor.vue';
import ModifierEditorDialog from '@/components/ModifierEditorDialog.vue';
import NavigateSelectedBlockComponent from '@/components/NavigateSelectedBlockComponent.vue';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import { saveBlockAsBlockType, getBlockTypeEditorUrl as getBlockTypeEditorUrlHelper } from './BlockInstanceEditor_SaveAsBlockType';
import { blockService } from '@/services/BlockService';
import { apiService } from '@/services/ApiService';
import type { BlockOriginDto } from '@nimbus/shared';

// Parse URL parameters
function parseBlockCoordinates(): { x: number; y: number; z: number } | null {
  const params = new URLSearchParams(window.location.search);

  // Support two formats:
  // 1. ?block=x,y,z (comma-separated)
  // 2. ?x=10&y=64&z=5 (separate parameters, used by client)
  const blockParam = params.get('block');

  if (blockParam) {
    // Format 1: comma-separated
    const parts = blockParam.split(',').map(Number);
    if (parts.length !== 3 || parts.some(isNaN)) {
      return null;
    }
    return { x: parts[0], y: parts[1], z: parts[2] };
  }

  // Format 2: separate parameters
  const x = params.get('x');
  const y = params.get('y');
  const z = params.get('z');

  if (x && y && z) {
    const coords = { x: Number(x), y: Number(y), z: Number(z) };
    if (!isNaN(coords.x) && !isNaN(coords.y) && !isNaN(coords.z)) {
      return coords;
    }
  }

  return null;
}

// Block coordinates (reactive ref, not computed)
const blockCoordinates = ref<{ x: number; y: number; z: number } | null>(parseBlockCoordinates());

// Get worldId from URL (once, not reactive - needed for composables)
const params = new URLSearchParams(window.location.search);
const worldId = params.get('world');

// Modal composable
const {
  isEmbedded,
  closeModal,
  sendNotification,
  notifyReady,
} = useModal();

// Block types composable
const { blockTypes, loading: loadingBlockTypes, searchBlockTypes, getBlockType } = useBlockTypes(worldId);

// BlockType search state
const blockTypeSearch = ref('');
const blockTypeSearchResults = ref<BlockType[]>([]);
const loadedBlockType = ref<BlockType | null>(null);
const showBlockTypeSearch = ref(false);
const hasSearched = ref(false); // Track if search has been executed

// State
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const blockExists = ref(false);
const blockReadOnly = ref(false);
const originalBlock = ref<Block | null>(null);
const blockData = ref<Block>({
  position: { x: 0, y: 0, z: 0 },
  blockTypeId: '0',
  status: 0,
  metadata: {},
});

// Modifier dialog state
const showModifierDialog = ref(false);
const showJsonEditor = ref(false);
const editingModifier = ref<BlockModifier | null>(null);
const editingStatus = ref<number | null>(null);

// Confirm dialog state
const confirmDialog = ref<HTMLDialogElement | null>(null);
const confirmTitle = ref('');
const confirmMessage = ref('');
const confirmOkText = ref('OK');
const confirmResolve = ref<((value: boolean) => void) | null>(null);

// Block origin
const loadingOrigin = ref(false);

// Navigator visibility (default hidden to save space)
const showNavigator = ref(false);

// Save as BlockType dialog state
const showSaveAsBlockTypeDialog = ref(false);
const saveAsBlockTypeDialog = ref<HTMLDialogElement | null>(null);
const newBlockTypeId = ref('');
const savingAsBlockType = ref(false);
const saveAsBlockTypeError = ref<string | null>(null);

// Computed
const isLoading = computed(() => {
  return loading.value || loadingBlockTypes.value;
});

const selectedBlockType = computed(() => {
  if (!blockData.value.blockTypeId) return null;
  return loadedBlockType.value;
});

const isValid = computed(() => {
  // blockTypeId is a string; treat any non-empty, non-'0' value as valid
  const id = blockData.value.blockTypeId;
  return id !== '' && id !== '0';
});

const hasChanges = computed(() => {
  if (!originalBlock.value) return true; // New block
  return JSON.stringify(blockData.value) !== JSON.stringify(originalBlock.value);
});

const hasOffsets = computed(() => {
  return blockData.value.offsets &&
         blockData.value.offsets.length > 0 &&
         blockData.value.offsets.some(v => v !== null && v !== undefined && v !== 0);
});

const toggleOffsets = (enabled: boolean) => {
  if (!enabled) {
    blockData.value.offsets = undefined;
  } else if (!blockData.value.offsets) {
    blockData.value.offsets = [];
  }
};

const hasFaceVisibility = computed(() => {
  return blockData.value.faceVisibility !== undefined;
});

const toggleFaceVisibility = (enabled: boolean) => {
  if (!enabled) {
    blockData.value.faceVisibility = undefined;
  } else if (blockData.value.faceVisibility === undefined || blockData.value.faceVisibility === null) {
    // All faces visible (6 bits set), FIXED flag off by default
    blockData.value.faceVisibility = 63;
  }
};

const isFixedMode = computed(() => {
  if (typeof blockData.value.faceVisibility !== 'number') return false;
  return (blockData.value.faceVisibility & 64) !== 0; // FIXED flag
});

const currentShape = computed(() => {
  // Try to get shape from block's own modifiers first
  const status = blockData.value.status ?? 0;
  if (blockData.value.modifiers?.[status]?.visibility?.shape) {
    return blockData.value.modifiers[status].visibility!.shape;
  }

  // Fallback to loaded block type
  if (selectedBlockType.value?.modifiers?.[status]?.visibility?.shape) {
    return selectedBlockType.value.modifiers[status].visibility!.shape;
  }

  // Default to CUBE
  return 1;
});

// Generate BlockTypeEditor URL with blockTypeId parameter
function getBlockTypeEditorUrl(blockTypeId: string): string {
  const params = new URLSearchParams(window.location.search);

  // Use relative path to blocktype-editor.html
  const baseUrl = 'blocktype-editor.html';

  // Preserve world and sessionId parameters, add blockTypeId
  const newParams = new URLSearchParams();
  if (params.get('world')) newParams.set('world', params.get('world')!);
  if (params.get('sessionId')) newParams.set('sessionId', params.get('sessionId')!);
  newParams.set('id', blockTypeId.toString());

  return `${baseUrl}?${newParams.toString()}`;
}

// Handle navigation from NavigateSelectedBlockComponent
async function handleNavigate(position: { x: number; y: number; z: number }) {
  // Update URL parameters
  const params = new URLSearchParams(window.location.search);
  params.set('x', position.x.toString());
  params.set('y', position.y.toString());
  params.set('z', position.z.toString());

  // Update URL without page reload
  const newUrl = `${window.location.pathname}?${params.toString()}`;
  window.history.pushState({}, '', newUrl);

  // Update blockCoordinates ref to trigger reactivity
  // This will automatically trigger the watcher which calls loadBlock()
  blockCoordinates.value = { ...position };

  // Also notify server to update selection highlight in 3D client
  try {
    const sessionId = params.get('sessionId');
    if (sessionId) {
      const apiUrl = apiService.getBaseUrl();
      const response = await fetch(
        `${apiUrl}/control/worlds/${worldId}/session/${sessionId}/selectedEditBlock/navigate`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(position),
          credentials: 'include'
        }
      );

      if (!response.ok) {
        console.warn('Failed to update server selection:', response.statusText);
      }
    }
  } catch (err) {
    console.warn('Failed to notify server about navigation:', err);
    // Don't block the UI if server notification fails
  }
}

// Confirm dialog helpers
function showConfirm(title: string, message: string, okText: string = 'OK'): Promise<boolean> {
  // Use native confirm if not embedded
  if (!isEmbedded()) {
    return Promise.resolve(window.confirm(message));
  }

  // Use custom modal if embedded
  return new Promise((resolve) => {
    confirmTitle.value = title;
    confirmMessage.value = message;
    confirmOkText.value = okText;
    confirmResolve.value = resolve;
    confirmDialog.value?.showModal();
  });
}

function handleConfirmOk() {
  confirmDialog.value?.close();
  confirmResolve.value?.(true);
  confirmResolve.value = null;
}

function handleConfirmCancel() {
  confirmDialog.value?.close();
  confirmResolve.value?.(false);
  confirmResolve.value = null;
}

// BlockType search and selection
async function handleBlockTypeSearch(query: string) {
  console.log('[BlockInstanceEditor] handleBlockTypeSearch called', { query, trimmed: query?.trim() });

  if (!query || query.trim().length === 0) {
    console.log('[BlockInstanceEditor] Empty query, clearing results');
    blockTypeSearchResults.value = [];
    hasSearched.value = false;
    return;
  }

  try {
    console.log('[BlockInstanceEditor] Calling searchBlockTypes with query:', query);
    await searchBlockTypes(query);
    hasSearched.value = true; // Mark that search has been executed
    console.log('[BlockInstanceEditor] Search completed, blockTypes.value:', blockTypes.value);
    console.log('[BlockInstanceEditor] blockTypes.value.length:', blockTypes.value.length);
    blockTypeSearchResults.value = blockTypes.value.slice(0, 20); // Limit to 20 results
    console.log('[BlockInstanceEditor] Search results:', blockTypeSearchResults.value.length, 'results');
  } catch (err) {
    console.error('[BlockInstanceEditor] Failed to search block types:', err);
  }
}

function selectBlockType(blockType: BlockType) {
  blockData.value.blockTypeId = blockType.id;
  loadedBlockType.value = blockType;
  blockTypeSearch.value = '';
  blockTypeSearchResults.value = [];
  showBlockTypeSearch.value = false; // Hide search after selection
}

function clearBlockType() {
  blockData.value.blockTypeId = '';
  loadedBlockType.value = null;
  blockTypeSearch.value = '';
  blockTypeSearchResults.value = [];
  hasSearched.value = false; // Reset search state
  showBlockTypeSearch.value = true; // Show search when changing
}

// Load BlockType details
async function loadBlockTypeDetails(blockTypeId: number) {
  try {
    const blockType = await getBlockType(blockTypeId);
    if (blockType) {
      loadedBlockType.value = blockType;
    } else {
      console.warn(`BlockType ${blockTypeId} not found`);
      loadedBlockType.value = null;
    }
  } catch (err) {
    console.error('Failed to load BlockType details:', err);
    loadedBlockType.value = null;
  }
}

// Set marker in world-control (async, non-blocking)
async function setMarker(x: number, y: number, z: number, sessionId: string) {
  try {
    const apiUrl = apiService.getBaseUrl();
    const url = `${apiUrl}/control/worlds/${worldId}/session/${sessionId}/marker/${x}/${y}/${z}`;

    // Fire and forget - don't wait for response
    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'include'
    }).catch(err => {
      // Log error but don't block UI
      console.warn('Failed to set marker:', err);
    });
  } catch (err) {
    console.warn('Failed to set marker:', err);
  }
}

// Load block data
async function loadBlock() {
  if (!blockCoordinates.value) {
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    const { x, y, z } = blockCoordinates.value;
    const apiUrl = apiService.getBaseUrl();

    // Get sessionId from URL
    const params = new URLSearchParams(window.location.search);
    const sessionId = params.get('sessionId');

    if (!sessionId) {
      throw new Error('Session ID required for block loading');
    }

    // Set marker asynchronously (fire and forget)
    setMarker(x, y, z, sessionId);

    const url = `${apiUrl}/control/worlds/${worldId}/session/${sessionId}/block/${x}/${y}/${z}`;

    // Fetch with timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    const response = await fetch(url, {
      signal: controller.signal,
      headers: {
        'Accept': 'application/json',
      },
      credentials: 'include'
    }).finally(() => clearTimeout(timeoutId));

    if (response.status === 404) {
      // Block doesn't exist yet - create new
      blockExists.value = false;
      blockData.value = {
        position: blockCoordinates.value,
        blockTypeId: '',
        status: 0,
        metadata: {},
      };
      originalBlock.value = null;
      loadedBlockType.value = null;
    } else if (response.ok) {
      // Parse BlockInfo response (new format)
      const blockInfo = await response.json();
      blockExists.value = blockInfo.block && blockInfo.block.blockTypeId !== 'air';
      blockReadOnly.value = blockInfo.readOnly || false;

      // Extract block from BlockInfo
      const block = blockInfo.block || {};

      // Ensure metadata is always an object
      if (!block.metadata) {
        block.metadata = {};
      }

      // Old faceVisibility object format { value } is no longer used,
      // so we assume faceVisibility is already a numeric bitfield if present.

      blockData.value = block;
      originalBlock.value = JSON.parse(JSON.stringify(block));

      // Load BlockType details if blockTypeId is set
      if (block.blockTypeId && block.blockTypeId > 0) {
        await loadBlockTypeDetails(block.blockTypeId);
      } else {
        loadedBlockType.value = null;
      }
    } else {
      throw new Error(`Failed to load block: ${response.statusText}`);
    }
  } catch (err) {
    if (err instanceof Error) {
      if (err.name === 'AbortError') {
        error.value = 'Request timeout - is the server running?';
      } else {
        error.value = err.message;
      }
    } else {
      error.value = 'Failed to load block';
    }
  } finally {
    loading.value = false;
  }
}

// Modifier management
function addModifier() {
  const status = blockData.value.status ?? 0;
  const modifier: BlockModifier = {
    visibility: { shape: 1, textures: {} },
  };

  editingModifier.value = modifier;
  editingStatus.value = status;
  showModifierDialog.value = true;
}

function editModifier(status: number) {
  const modifier = blockData.value.modifiers?.[status];
  if (!modifier) return;

  editingModifier.value = JSON.parse(JSON.stringify(modifier));
  editingStatus.value = status;
  showModifierDialog.value = true;
}

function deleteModifier(status: number) {
  if (blockData.value.modifiers) {
    delete blockData.value.modifiers[status];

    // Clean up modifiers object if empty
    if (Object.keys(blockData.value.modifiers).length === 0) {
      blockData.value.modifiers = undefined;
    }
  }
}

function handleModifierSave(modifier: BlockModifier) {
  if (editingStatus.value === null) return;

  if (!blockData.value.modifiers) {
    blockData.value.modifiers = {};
  }

  blockData.value.modifiers[editingStatus.value] = modifier;
  showModifierDialog.value = false;
}

// Check if specific face is visible
function isFaceVisible(faceFlag: number): boolean {
  if (typeof blockData.value.faceVisibility !== 'number') return false;
  return (blockData.value.faceVisibility & faceFlag) !== 0;
}

// Toggle specific face
function toggleFace(faceFlag: number) {
  if (typeof blockData.value.faceVisibility !== 'number') {
    blockData.value.faceVisibility = 0;
  }
  blockData.value.faceVisibility ^= faceFlag; // XOR to toggle
}

// Toggle fixed mode
function toggleFixedMode() {
  if (typeof blockData.value.faceVisibility !== 'number') {
    blockData.value.faceVisibility = 0;
  }
  blockData.value.faceVisibility ^= 64; // Toggle FIXED flag (bit 6)
}

// Save/Delete operations
async function saveBlock(closeAfter: boolean = false) {
  if (!blockCoordinates.value || !isValid.value) return;

  saving.value = true;
  error.value = null;

  try {
    const { x, y, z } = blockCoordinates.value;
    const apiUrl = apiService.getBaseUrl();

    // Get sessionId from URL
    const params = new URLSearchParams(window.location.search);
    const sessionId = params.get('sessionId');

    if (!sessionId) {
      throw new Error('Session ID required for block editing');
    }

    // Build complete block object (from Block.ts type)
    const block = {
      position: {
        x: x,
        y: y,
        z: z,
      },
      blockTypeId: blockData.value.blockTypeId?.toString() || '0',
      offsets: blockData.value.offsets && blockData.value.offsets.length > 0
        ? blockData.value.offsets
        : undefined,
      cornerHeights: blockData.value.cornerHeights && blockData.value.cornerHeights.length > 0
        ? blockData.value.cornerHeights
        : undefined,
      rotation: blockData.value.rotation || undefined,
      level: blockData.value.level !== undefined && blockData.value.level >= 0
        ? blockData.value.level
        : undefined,
      status: blockData.value.status || 0,
      modifiers: blockData.value.modifiers || undefined,
      // faceVisibility is now always stored as a numeric bitfield on the block
      faceVisibility: blockData.value.faceVisibility,
      metadata: blockData.value.metadata && Object.keys(blockData.value.metadata).length > 0
        ? blockData.value.metadata
        : undefined,
    };

    // Send complete block as JSON string
    const response = await fetch(`${apiUrl}/control/editor/${worldId}/session/${sessionId}/block`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(block),
      credentials: 'include'
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }));
      throw new Error(errorData.error || 'Failed to save block');
    }

//    const result = await response.json();
    blockExists.value = true;

    // Reload block to get updated info
    await loadBlock();

    // Send notification
    if (isEmbedded()) {
      sendNotification('0', 'Block Editor', 'Block saved successfully');
    }

    if (closeAfter && isEmbedded()) {
      closeModal('saved');
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to save block';
    console.error('Failed to save block:', err);

    if (isEmbedded()) {
      sendNotification('1', 'Block Editor', `Error: ${error.value}`);
    }
  } finally {
    saving.value = false;
  }
}

async function deleteBlock() {
  if (!blockCoordinates.value || !blockExists.value) return;

  const confirmed = await showConfirm(
    'Delete Block',
    'Are you sure you want to delete this block?',
    'Delete'
  );

  if (!confirmed) {
    return;
  }

  saving.value = true;
  error.value = null;

  try {
    const { x, y, z } = blockCoordinates.value;
    const apiUrl = apiService.getBaseUrl();

    // Get sessionId from URL
    const params = new URLSearchParams(window.location.search);
    const sessionId = params.get('sessionId');

    if (!sessionId) {
      throw new Error('Session ID required for block deletion');
    }

    // Delete = set to air block via editor endpoint
    const response = await fetch(`${apiUrl}/control/editor/${worldId}/session/${sessionId}/block`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        blockId: 'air',
        meta: null,
      }),
      credentials: 'include'
    });

    if (!response.ok && response.status !== 404) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }));
      throw new Error(errorData.error || 'Failed to delete block');
    }

    // Update local state to air
    blockExists.value = false;
    blockData.value = {
      position: blockCoordinates.value,
      blockTypeId: 'air',
      status: 0,
      metadata: {},
    };
    originalBlock.value = null;

    // Send notification
    if (isEmbedded()) {
      sendNotification('0', 'Block Editor', 'Block deleted successfully');
      closeModal('deleted');
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to delete block';
    console.error('Failed to delete block:', err);

    if (isEmbedded()) {
      sendNotification('1', 'Block Editor', `Error: ${error.value}`);
    }
  } finally {
    saving.value = false;
  }
}

// Button handlers
async function handleSave() {
  await saveBlock(true); // Save and close
}

async function handleApply() {
  await saveBlock(false); // Save but keep open
}

async function handleCancel() {
  if (hasChanges.value) {
    const confirmed = await showConfirm(
      'Discard Changes',
      'Discard unsaved changes?',
      'Discard'
    );

    if (!confirmed) {
      return;
    }
  }

  if (isEmbedded()) {
    closeModal('cancelled');
  }
}

async function handleDelete() {
  await deleteBlock();
}

// Lifecycle
onMounted(async () => {
  if (isEmbedded()) {
    notifyReady();
  }

  await loadBlock();
});

// Watch for block type changes to reload
watch(() => blockCoordinates.value, () => {
  if (blockCoordinates.value) {
    loadBlock();
  }
});

// Handle JSON apply from JSON editor
const handleJsonApply = (jsonData: any) => {
  // Merge JSON data into blockData, preserving reactive properties
  Object.assign(blockData.value, jsonData);
};

// Rotation state
const hasRotation = computed(() => {
  return blockData.value.rotation !== undefined;
});

const toggleRotation = (enabled: boolean) => {
  if (!enabled) {
    blockData.value.rotation = undefined;
  } else if (!blockData.value.rotation) {
    blockData.value.rotation = { x: 0, y: 0 };
  }
};

// Metadata state
const hasMetadata = computed(() => {
  return blockData.value.metadata !== undefined;
});

const toggleMetadata = (enabled: boolean) => {
  if (!enabled) {
    blockData.value.metadata = undefined;
  } else if (!blockData.value.metadata) {
    blockData.value.metadata = {};
  }
};

const rotationX = computed({
  get: () => blockData.value.rotation?.x ?? 0,
  set: (value: number) => {
    if (!blockData.value.rotation) {
      blockData.value.rotation = { x: 0, y: 0 };
    }
    blockData.value.rotation.x = value;
  },
});

const rotationY = computed({
  get: () => blockData.value.rotation?.y ?? 0,
  set: (value: number) => {
    if (!blockData.value.rotation) {
      blockData.value.rotation = { x: 0, y: 0 };
    }
    blockData.value.rotation.y = value;
  },
});

// Level (wind height) state - always integer
// Default is -1 to indicate undefined (0 is a valid value)
const levelValue = computed({
  get: () => blockData.value.level ?? -1,
  set: (value: number) => {
    // If value is undefined, NaN, or < 0, set to undefined
    if (value === undefined || isNaN(value) || value < 0) {
      blockData.value.level = undefined;
    } else {
      // Ensure integer value
      blockData.value.level = Math.floor(value);
    }
  },
});

// Save as BlockType functionality
function openSaveAsBlockTypeDialog() {
  newBlockTypeId.value = '';
  saveAsBlockTypeError.value = null;
  saveAsBlockTypeDialog.value?.showModal();
}

function closeSaveAsBlockTypeDialog() {
  saveAsBlockTypeDialog.value?.close();
  newBlockTypeId.value = '';
  saveAsBlockTypeError.value = null;
}

async function handleSaveAsBlockType() {
  if (!newBlockTypeId.value || savingAsBlockType.value) {
    return;
  }

  savingAsBlockType.value = true;
  saveAsBlockTypeError.value = null;

  try {
    const result = await saveBlockAsBlockType(
      worldId,
      newBlockTypeId.value,
      blockData.value
    );

    if (result.success) {
      // Close dialog
      closeSaveAsBlockTypeDialog();

      // Show success notification with link
      const params = new URLSearchParams(window.location.search);
      const sessionId = params.get('sessionId');
      const editorUrl = getBlockTypeEditorUrlHelper(newBlockTypeId.value, worldId, sessionId || undefined);

      if (isEmbedded()) {
        sendNotification(
          '0',
          'BlockType Created',
          `BlockType "${newBlockTypeId.value}" created successfully. <a href="${editorUrl}" target="_blank">Open in editor</a>`
        );
      } else {
        alert(`BlockType "${newBlockTypeId.value}" created successfully!\n\nOpen editor: ${editorUrl}`);
      }

      // Optionally open in new tab
      window.open(editorUrl, '_blank');
    } else {
      saveAsBlockTypeError.value = result.error || 'Failed to save BlockType';
    }
  } catch (err) {
    saveAsBlockTypeError.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    savingAsBlockType.value = false;
  }
}

/**
 * Show block origin information
 */
const showBlockOrigin = async () => {
  if (!blockCoordinates.value || !worldId) return;

  loadingOrigin.value = true;

  try {
    const response = await blockService.findOrigin(
      worldId,
      blockCoordinates.value.x,
      blockCoordinates.value.y,
      blockCoordinates.value.z
    );

    if (!response.found || !response.origin) {
      console.info('Block not found in any layer');
      // Don't set error, just log - block might not be in a layer
      return;
    }

    const origin = response.origin;

    // Log full details to console
    console.group('🔍 Block Origin Information');
    console.log('Layer:', origin.layerName || 'Unnamed', `(${origin.layerType})`);
    console.log('Layer ID:', origin.layerId);
    console.log('Layer Order:', origin.layerOrder);
    console.log('Terrain Chunk:', origin.terrainChunkKey);
    console.log('Terrain ID:', origin.terrainId);

    if (origin.modelId) {
      console.log('\nModel:', origin.modelName || origin.modelTitle || 'Unnamed');
      console.log('Model ID:', origin.modelId);
      console.log('Mount Point:', `(${origin.mountX}, ${origin.mountY}, ${origin.mountZ})`);
    }

    console.log('\nBlock Properties:');
    console.log('Override:', origin.override);
    if (origin.group && origin.group > 0) {
      console.log('Group:', origin.group, origin.groupName ? `(${origin.groupName})` : '');
    }
    if (origin.weight && origin.weight > 0) {
      console.log('Weight:', origin.weight);
    }
    if (origin.metadata) {
      console.log('Metadata:', origin.metadata);
    }
    console.groupEnd();

    // Build detailed message for dialog
    let message = `Layer: ${origin.layerName || 'Unnamed'} (${origin.layerType})\n`;
    message += `Layer Order: ${origin.layerOrder}\n`;

    if (origin.terrainChunkKey) {
      message += `Terrain Chunk: ${origin.terrainChunkKey}\n`;
    } else {
      message += `Terrain: Not synced yet\n`;
    }
    message += `\n`;

    if (origin.modelId) {
      message += `Model: ${origin.modelName || origin.modelTitle || 'Unnamed'}\n`;
      message += `Model ID: ${origin.modelId}\n`;
      message += `Mount Point: (${origin.mountX}, ${origin.mountY}, ${origin.mountZ})\n`;
      message += `\n`;
    }

    message += `Block Properties:\n`;
    message += `Override: ${origin.override ? 'Yes' : 'No'}\n`;

    if (origin.group && origin.group > 0) {
      message += `Group: ${origin.group}`;
      if (origin.groupName) {
        message += ` (${origin.groupName})`;
      }
      message += `\n`;
    }

    if (origin.weight && origin.weight > 0) {
      message += `Weight: ${origin.weight}\n`;
    }

    if (origin.metadata) {
      message += `Metadata: ${origin.metadata}\n`;
    }

    // Show in dialog (OK only, no cancel)
    confirmTitle.value = 'Block Origin Information';
    confirmMessage.value = message;
    confirmOkText.value = 'OK';
    confirmDialog.value?.showModal();

    // Wait for user to close dialog
    await new Promise<void>((resolve) => {
      const checkClosed = setInterval(() => {
        if (!confirmDialog.value?.open) {
          clearInterval(checkClosed);
          resolve();
        }
      }, 100);
    });

  } catch (err: any) {
    console.error('Failed to load block origin', err);
    // Don't show error in UI, just log to console
  } finally {
    loadingOrigin.value = false;
  }
};

/**
 * Server property management
 */
function addServerProperty() {
  if (!blockData.value.metadata) {
    blockData.value.metadata = {};
  }
  if (!blockData.value.metadata.server) {
    blockData.value.metadata.server = {};
  }

  // Find unique key name
  let counter = 1;
  let newKey = 'key';
  while (blockData.value.metadata.server[newKey]) {
    newKey = `key${counter}`;
    counter++;
  }

  blockData.value.metadata.server[newKey] = '';
}

function deleteServerProperty(key: string) {
  if (blockData.value.metadata?.server) {
    delete blockData.value.metadata.server[key];

    // Clean up empty object
    if (Object.keys(blockData.value.metadata.server).length === 0) {
      blockData.value.metadata.server = undefined;
    }
  }
}

function updateServerKey(oldKey: string, newKey: string, value: string) {
  if (!blockData.value.metadata?.server || oldKey === newKey) {
    return;
  }

  // Check if new key already exists
  if (blockData.value.metadata.server[newKey]) {
    console.warn('Key already exists:', newKey);
    return;
  }

  // Delete old key and add new one
  delete blockData.value.metadata.server[oldKey];
  blockData.value.metadata.server[newKey] = value;
}

/**
 * Client property management
 */
function addClientProperty() {
  if (!blockData.value.metadata) {
    blockData.value.metadata = {};
  }
  if (!blockData.value.metadata.client) {
    blockData.value.metadata.client = {};
  }

  // Find unique key name
  let counter = 1;
  let newKey = 'key';
  while (blockData.value.metadata.client[newKey]) {
    newKey = `key${counter}`;
    counter++;
  }

  blockData.value.metadata.client[newKey] = '';
}

function deleteClientProperty(key: string) {
  if (blockData.value.metadata?.client) {
    delete blockData.value.metadata.client[key];

    // Clean up empty object
    if (Object.keys(blockData.value.metadata.client).length === 0) {
      blockData.value.metadata.client = undefined;
    }
  }
}

function updateClientKey(oldKey: string, newKey: string, value: string) {
  if (!blockData.value.metadata?.client || oldKey === newKey) {
    return;
  }

  // Check if new key already exists
  if (blockData.value.metadata.client[newKey]) {
    console.warn('Key already exists:', newKey);
    return;
  }

  // Delete old key and add new one
  delete blockData.value.metadata.client[oldKey];
  blockData.value.metadata.client[newKey] = value;
}
</script>
