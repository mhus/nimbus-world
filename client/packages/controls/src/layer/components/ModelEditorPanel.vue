<template>
  <div class="modal modal-open" @click.self="emit('close')">
    <div class="modal-box max-w-4xl" @click.stop>
      <h3 class="font-bold text-lg mb-4">
        {{ isEditMode ? 'Edit Layer Model' : 'Create Layer Model' }}
      </h3>

      <!-- Error Alert -->
      <ErrorAlert v-if="errorMessage" :message="errorMessage" class="mb-4" />

      <form @submit.prevent="handleSave" class="space-y-4">
        <!-- Name -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Name</span>
          </label>
          <input
            v-model="formData.name"
            type="text"
            class="input input-bordered"
            placeholder="Technical name (optional)"
          />
          <label class="label">
            <span class="label-text-alt">Technical identifier for this model</span>
          </label>
        </div>

        <!-- Title -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Title</span>
          </label>
          <input
            v-model="formData.title"
            type="text"
            class="input input-bordered"
            placeholder="Display title (optional)"
          />
          <label class="label">
            <span class="label-text-alt">Human-readable display name</span>
          </label>
        </div>

        <!-- Mount Point -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Mount Point *</span>
          </label>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <input
                v-model.number="formData.mountX"
                type="number"
                class="input input-bordered w-full"
                placeholder="X"
                required
              />
            </div>
            <div>
              <input
                v-model.number="formData.mountY"
                type="number"
                class="input input-bordered w-full"
                placeholder="Y"
                required
              />
            </div>
            <div>
              <input
                v-model.number="formData.mountZ"
                type="number"
                class="input input-bordered w-full"
                placeholder="Z"
                required
              />
            </div>
          </div>
          <label class="label">
            <span class="label-text-alt">World coordinates where this model will be placed</span>
          </label>
        </div>

        <!-- Rotation -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Rotation</span>
          </label>
          <select
            v-model.number="formData.rotation"
            class="select select-bordered"
          >
            <option :value="0">0° (No rotation)</option>
            <option :value="1">90° (Clockwise)</option>
            <option :value="2">180°</option>
            <option :value="3">270° (Counter-clockwise)</option>
          </select>
          <label class="label">
            <span class="label-text-alt">Rotation in 90 degree steps</span>
          </label>
        </div>

        <!-- Order -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Order *</span>
          </label>
          <input
            v-model.number="formData.order"
            type="number"
            class="input input-bordered"
            placeholder="Render order"
            required
          />
          <label class="label">
            <span class="label-text-alt">Lower values are rendered first (bottom), higher values on top</span>
          </label>
        </div>

        <!-- Reference Model ID -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Reference Model ID</span>
          </label>
          <div class="join w-full">
            <input
              v-model="formData.referenceModelId"
              type="text"
              class="input input-bordered join-item flex-1"
              placeholder="Format: worldId/modelName (e.g., earth616/TownHall)"
            />
            <button
              type="button"
              class="btn btn-outline join-item"
              @click="openReferenceSearchDialog"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </button>
          </div>
          <label class="label">
            <span class="label-text-alt">Format: worldId/modelName. Referenced model will be rendered first, then this model on top. Max depth: 10</span>
          </label>
        </div>

        <!-- Groups -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Groups</span>
          </label>
          <div class="space-y-2">
            <div v-for="(groupId, groupName) in formData.groups" :key="groupName" class="flex gap-2">
              <input
                :value="groupName"
                type="text"
                class="input input-bordered flex-1"
                placeholder="Group name"
                @input="updateGroupName(groupName as string, ($event.target as HTMLInputElement).value)"
              />
              <input
                :value="groupId"
                type="number"
                class="input input-bordered w-24"
                placeholder="ID"
                @input="updateGroupId(groupName as string, parseInt(($event.target as HTMLInputElement).value))"
              />
              <button
                type="button"
                class="btn btn-ghost btn-square"
                @click="removeGroup(groupName as string)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="addGroup"
            >
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add Group
            </button>
          </div>
          <label class="label">
            <span class="label-text-alt">Group mapping: name → ID for organized block management</span>
          </label>
        </div>

        <!-- Action Buttons -->
        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="emit('close')"
          >
            Cancel
          </button>
          <button
            v-if="isEditMode"
            type="button"
            class="btn btn-info btn-outline"
            @click="openGridEditor"
          >
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 5a1 1 0 011-1h4a1 1 0 011 1v7a1 1 0 01-1 1H5a1 1 0 01-1-1V5zM14 5a1 1 0 011-1h4a1 1 0 011 1v7a1 1 0 01-1 1h-4a1 1 0 01-1-1V5zM4 16a1 1 0 011-1h4a1 1 0 011 1v3a1 1 0 01-1 1H5a1 1 0 01-1-1v-3zM14 16a1 1 0 011-1h4a1 1 0 011 1v3a1 1 0 01-1 1h-4a1 1 0 01-1-1v-3z" />
            </svg>
            Open Grid Editor
          </button>

          <!-- Transform Dropdown -->
          <details v-if="isEditMode" class="dropdown dropdown-top">
            <summary class="btn btn-outline" :class="{ 'btn-disabled': transforming }">
              <span v-if="transforming" class="loading loading-spinner"></span>
              <svg v-else class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
              </svg>
              {{ transforming ? 'Transforming...' : 'Transform' }}
              <svg class="w-4 h-4 ml-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
              </svg>
            </summary>
            <ul class="dropdown-content z-[1] menu p-2 shadow bg-base-100 rounded-box w-64 mb-2">
              <li>
                <a @click="handleTransformAutoAdjustCenter">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
                  </svg>
                  Auto Adjust Center
                </a>
              </li>
              <li>
                <a @click="openManualAdjustDialog">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6V4m0 2a2 2 0 100 4m0-4a2 2 0 110 4m-6 8a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4m6 6v10m6-2a2 2 0 100-4m0 4a2 2 0 110-4m0 4v2m0-6V4" />
                  </svg>
                  Manual Adjust Center
                </a>
              </li>
              <li>
                <a @click="openMoveDialog">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16V4m0 0L3 8m4-4l4 4m6 0v12m0 0l4-4m-4 4l-4-4" />
                  </svg>
                  Move
                </a>
              </li>
              <li>
                <a @click="openCopyDialog">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                  </svg>
                  Copy to Layer
                </a>
              </li>
            </ul>
          </details>

          <button
            v-if="isEditMode"
            type="button"
            class="btn btn-secondary"
            :disabled="syncing"
            @click="handleSync"
          >
            <span v-if="syncing" class="loading loading-spinner"></span>
            <svg v-else class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ syncing ? 'Syncing...' : 'Sync to Terrain' }}
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="saving"
          >
            <span v-if="saving" class="loading loading-spinner"></span>
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- Move Dialog -->
  <div v-if="showMoveDialog" class="modal modal-open" @click.self="closeMoveDialog">
    <div class="modal-box" @click.stop>
      <h3 class="font-bold text-lg mb-4">Move Model</h3>
      <p class="text-sm text-base-content/70 mb-4">
        Enter the offset values to move all blocks. The mount point stays the same.
        Model will be automatically synced to terrain.
      </p>

      <form @submit.prevent="handleTransformMove" class="space-y-4">
        <div class="form-control">
          <label class="label">
            <span class="label-text">Move Offset</span>
          </label>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">
                <span class="label-text-alt">X</span>
              </label>
              <input
                v-model.number="moveOffset.x"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Y</span>
              </label>
              <input
                v-model.number="moveOffset.y"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Z</span>
              </label>
              <input
                v-model.number="moveOffset.z"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
          </div>
          <label class="label">
            <span class="label-text-alt">Blocks will be moved by this offset</span>
          </label>
        </div>

        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="closeMoveDialog"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="transforming"
          >
            <span v-if="transforming" class="loading loading-spinner"></span>
            {{ transforming ? 'Moving...' : 'Move & Sync' }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- Manual Adjust Center Dialog -->
  <div v-if="showManualAdjustDialog" class="modal modal-open" @click.self="closeManualAdjustDialog">
    <div class="modal-box" @click.stop>
      <h3 class="font-bold text-lg mb-4">Manual Adjust Center</h3>
      <p class="text-sm text-base-content/70 mb-4">
        Enter the offset values to shift all block coordinates. The mount point will be adjusted in the opposite direction.
      </p>

      <form @submit.prevent="handleTransformManualAdjustCenter" class="space-y-4">
        <div class="form-control">
          <label class="label">
            <span class="label-text">Offset</span>
          </label>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <label class="label">
                <span class="label-text-alt">X</span>
              </label>
              <input
                v-model.number="manualAdjustOffset.x"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Y</span>
              </label>
              <input
                v-model.number="manualAdjustOffset.y"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Z</span>
              </label>
              <input
                v-model.number="manualAdjustOffset.z"
                type="number"
                class="input input-bordered w-full"
                placeholder="0"
              />
            </div>
          </div>
          <label class="label">
            <span class="label-text-alt">Blocks will be shifted by -offset, mount point by +offset</span>
          </label>
        </div>

        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="closeManualAdjustDialog"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="transforming"
          >
            <span v-if="transforming" class="loading loading-spinner"></span>
            {{ transforming ? 'Transforming...' : 'Apply' }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- Copy Model Dialog -->
  <div v-if="showCopyDialog" class="modal modal-open" @click.self="closeCopyDialog">
    <div class="modal-box max-w-2xl" @click.stop>
      <h3 class="font-bold text-lg mb-4">Copy Model to Another Layer</h3>
      <p class="text-sm text-base-content/70 mb-4">
        Copy this model to a different MODEL layer, possibly in another world.
      </p>

      <!-- Error Alert -->
      <ErrorAlert v-if="copyError" :message="copyError" class="mb-4" />

      <form @submit.prevent="handleCopyModel" class="space-y-4">
        <!-- Target World Selection -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Target World *</span>
          </label>
          <select
            v-model="copyFormData.targetWorldId"
            class="select select-bordered"
            required
            @change="handleCopyWorldChange"
          >
            <option value="" disabled>Select target world...</option>
            <option v-if="loadingCopyWorlds" disabled>Loading worlds...</option>
            <option
              v-for="world in copyAvailableWorlds"
              :key="world.worldId"
              :value="world.worldId"
            >
              {{ world.name }} ({{ world.worldId }})
            </option>
          </select>
          <label class="label">
            <span class="label-text-alt">Select the world to copy to (includes collections)</span>
          </label>
        </div>

        <!-- Target Layer Selection -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Target Layer *</span>
          </label>
          <div class="join w-full">
            <select
              v-if="copyAvailableLayers.length > 0"
              v-model="copyFormData.targetLayerId"
              class="select select-bordered join-item flex-1"
              required
            >
              <option value="" disabled>Select target layer...</option>
              <option v-if="loadingCopyLayers" disabled>Loading layers...</option>
              <option
                v-for="layer in copyAvailableLayers"
                :key="layer.id"
                :value="layer.id"
              >
                {{ layer.name }} ({{ layer.layerType }})
              </option>
            </select>
            <input
              v-else
              v-model="copyFormData.targetLayerId"
              type="text"
              class="input input-bordered join-item flex-1"
              placeholder="Enter layer ID manually"
              required
            />
            <button
              v-if="copyFormData.targetWorldId"
              type="button"
              class="btn btn-outline join-item"
              :disabled="loadingCopyLayers"
              @click="loadLayersForCopyWorld"
            >
              <span v-if="loadingCopyLayers" class="loading loading-spinner loading-sm"></span>
              <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>
          <label class="label">
            <span class="label-text-alt">Only MODEL type layers are shown. Click refresh to load layers.</span>
          </label>
        </div>

        <!-- New Name (Optional) -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">New Name (Optional)</span>
          </label>
          <input
            v-model="copyFormData.newName"
            type="text"
            class="input input-bordered"
            placeholder="Leave empty to keep original name"
          />
          <label class="label">
            <span class="label-text-alt">If empty, the original name will be used</span>
          </label>
        </div>

        <!-- Info Box -->
        <div class="alert alert-info">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div class="text-sm">
            <p>The worldId and layerDataId will be automatically taken from the target layer.</p>
            <p>All blocks, groups, and settings will be copied.</p>
          </div>
        </div>

        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="closeCopyDialog"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="copying || !copyFormData.targetLayerId"
          >
            <span v-if="copying" class="loading loading-spinner"></span>
            {{ copying ? 'Copying...' : 'Copy Model' }}
          </button>
        </div>
      </form>
    </div>
  </div>

  <!-- Reference Search Dialog -->
  <div v-if="showReferenceSearchDialog" class="modal modal-open" @click.self="closeReferenceSearchDialog">
    <div class="modal-box max-w-2xl" @click.stop>
      <h3 class="font-bold text-lg mb-4">Select Reference Model</h3>
      <p class="text-sm text-base-content/70 mb-4">
        Select a world and model to reference. Format: worldId/modelName
      </p>

      <!-- Error Alert -->
      <ErrorAlert v-if="referenceSearchError" :message="referenceSearchError" class="mb-4" />

      <form @submit.prevent="handleSelectReference" class="space-y-4">
        <!-- World Selection -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">World *</span>
          </label>
          <select
            v-model="referenceSearch.worldId"
            class="select select-bordered"
            required
            @change="handleWorldIdChange"
          >
            <option value="" disabled>Select a world...</option>
            <option v-if="loadingWorlds" disabled>Loading worlds...</option>
            <option
              v-for="world in availableWorlds"
              :key="world.worldId"
              :value="world.worldId"
            >
              {{ world.name }} ({{ world.worldId }})
            </option>
          </select>
          <label class="label">
            <span class="label-text-alt">Select the world where the reference model exists (includes collections)</span>
          </label>
        </div>

        <!-- Model Name/Layer Selection -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Model Name *</span>
          </label>
          <div class="join w-full">
            <select
              v-if="availableModels.length > 0"
              v-model="referenceSearch.modelName"
              class="select select-bordered join-item flex-1"
              required
            >
              <option value="" disabled>Select a model...</option>
              <option v-if="loadingModels" disabled>Loading models...</option>
              <option
                v-for="model in availableModels"
                :key="model.id"
                :value="model.name"
              >
                {{ model.title || model.name || 'Unnamed' }} ({{ model.name }})
              </option>
            </select>
            <input
              v-else
              v-model="referenceSearch.modelName"
              type="text"
              class="input input-bordered join-item flex-1"
              placeholder="Enter model name manually"
              required
            />
            <button
              v-if="referenceSearch.worldId"
              type="button"
              class="btn btn-outline join-item"
              :disabled="loadingModels"
              @click="loadModelsForWorld"
            >
              <span v-if="loadingModels" class="loading loading-spinner loading-sm"></span>
              <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
            </button>
          </div>
          <label class="label">
            <span class="label-text-alt">The technical name of the model (WLayerModel.name). Click refresh to load available models.</span>
          </label>
        </div>

        <!-- Preview -->
        <div v-if="referenceSearch.worldId && referenceSearch.modelName" class="alert alert-info">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div class="text-sm">
            <p><strong>Reference ID:</strong> {{ referenceSearch.worldId }}/{{ referenceSearch.modelName }}</p>
            <p class="text-xs mt-1">This reference will be resolved when syncing to terrain</p>
          </div>
        </div>

        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="closeReferenceSearchDialog"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
          >
            Select Reference
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { LayerModelDto, CreateLayerModelRequest, UpdateLayerModelRequest, WorldInfo } from '@nimbus/shared';
import ErrorAlert from '@components/ErrorAlert.vue';
import { layerModelService } from '@/services/LayerModelService';
import { worldService } from '@/services/WorldService';
import { layerService } from '@/services/LayerService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ModelEditorPanel');

interface Props {
  model: LayerModelDto | null;
  layerDataId: string;
  worldId: string;
  layerId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved', model: LayerModelDto): void;
  (e: 'openGridEditor', params: {
    sourceType: 'model';
    layerId: string;
    layerName?: string;
    modelId: string;
    modelName?: string;
  }): void;
}>();

const isEditMode = computed(() => !!props.model);

const formData = ref<Partial<LayerModelDto>>({
  name: '',
  title: '',
  mountX: 0,
  mountY: 0,
  mountZ: 0,
  rotation: 0,
  referenceModelId: undefined,
  order: 100,
  groups: {}
});

const errorMessage = ref('');
const saving = ref(false);
const syncing = ref(false);
const transforming = ref(false);
const showManualAdjustDialog = ref(false);
const manualAdjustOffset = ref({ x: 0, y: 0, z: 0 });
const showMoveDialog = ref(false);
const moveOffset = ref({ x: 0, y: 0, z: 0 });
const showCopyDialog = ref(false);
const copying = ref(false);
const copyError = ref('');
const copyFormData = ref({ targetWorldId: '', targetLayerId: '', newName: '' });
const copyAvailableWorlds = ref<WorldInfo[]>([]);
const loadingCopyWorlds = ref(false);
const copyAvailableLayers = ref<any[]>([]);
const loadingCopyLayers = ref(false);
const showReferenceSearchDialog = ref(false);
const referenceSearchError = ref('');
const referenceSearch = ref({ worldId: '', modelName: '' });
const availableWorlds = ref<WorldInfo[]>([]);
const loadingWorlds = ref(false);
const availableModels = ref<LayerModelDto[]>([]);
const loadingModels = ref(false);

// Initialize form data
if (props.model) {
  formData.value = { ...props.model };
}

/**
 * Add a new group
 */
const addGroup = () => {
  const groups = formData.value.groups || {};
  const maxId = Math.max(0, ...Object.values(groups).map(v => typeof v === 'number' ? v : 0));
  const newId = maxId + 1;
  formData.value.groups = {
    ...groups,
    [`group${newId}`]: newId
  };
};

/**
 * Remove a group
 */
const removeGroup = (groupName: string) => {
  const groups = { ...formData.value.groups };
  delete groups[groupName];
  formData.value.groups = groups;
};

/**
 * Update group name
 */
const updateGroupName = (oldName: string, newName: string) => {
  if (oldName === newName) return;
  const groups = { ...formData.value.groups };
  const groupId = groups[oldName];
  delete groups[oldName];
  groups[newName] = groupId;
  formData.value.groups = groups;
};

/**
 * Update group ID
 */
const updateGroupId = (groupName: string, newId: number) => {
  formData.value.groups = {
    ...formData.value.groups,
    [groupName]: newId
  };
};

/**
 * Handle save
 */
const handleSave = async () => {
  errorMessage.value = '';
  saving.value = true;

  try {
    // Validate
    if (formData.value.mountX === undefined) {
      throw new Error('Mount X is required');
    }
    if (formData.value.mountY === undefined) {
      throw new Error('Mount Y is required');
    }
    if (formData.value.mountZ === undefined) {
      throw new Error('Mount Z is required');
    }
    if (formData.value.order === undefined) {
      throw new Error('Order is required');
    }

    if (isEditMode.value && props.model?.id) {
      // Update existing model
      const updateData: UpdateLayerModelRequest = {
        name: formData.value.name || undefined,
        title: formData.value.title || undefined,
        mountX: formData.value.mountX,
        mountY: formData.value.mountY,
        mountZ: formData.value.mountZ,
        rotation: formData.value.rotation,
        referenceModelId: formData.value.referenceModelId || undefined,
        order: formData.value.order,
        groups: formData.value.groups
      };
      await layerModelService.updateModel(props.worldId, props.layerId, props.model.id, updateData);
      logger.info('Updated model', { modelId: props.model.id });
    } else {
      // Create new model
      const createData: CreateLayerModelRequest = {
        name: formData.value.name || undefined,
        title: formData.value.title || undefined,
        layerDataId: props.layerDataId,
        mountX: formData.value.mountX,
        mountY: formData.value.mountY,
        mountZ: formData.value.mountZ,
        rotation: formData.value.rotation,
        referenceModelId: formData.value.referenceModelId || undefined,
        order: formData.value.order,
        groups: formData.value.groups
      };
      const id = await layerModelService.createModel(props.worldId, props.layerId, createData);
      logger.info('Created model', { modelId: id });
    }

    emit('saved', formData.value as LayerModelDto);
  } catch (error: any) {
    logger.error('Failed to save model', {}, error);
    errorMessage.value = error.message || 'Failed to save model';
  } finally {
    saving.value = false;
  }
};

/**
 * Handle manual sync to terrain
 */
/**
 * Open grid editor for this model
 */
const openGridEditor = () => {
  if (!props.model?.id) {
    errorMessage.value = 'Model not saved yet';
    return;
  }

  emit('openGridEditor', {
    sourceType: 'model',
    layerId: props.layerId,
    modelId: props.model.id,
    modelName: formData.value.title || formData.value.name || 'Unnamed Model'
  });
};

/**
 * Sync model to terrain
 */
const handleSync = async () => {
  if (!props.model?.id) return;

  errorMessage.value = '';
  syncing.value = true;

  try {
    await layerModelService.syncToTerrain(props.worldId, props.layerId, props.model.id);
    logger.info('Synced model to terrain', { modelId: props.model.id });

    // Show success message (optional)
    alert('Model synced to terrain successfully!');
  } catch (error: any) {
    logger.error('Failed to sync model', {}, error);
    errorMessage.value = error.message || 'Failed to sync model to terrain';
  } finally {
    syncing.value = false;
  }
};

/**
 * Transform model by automatically adjusting center
 */
const handleTransformAutoAdjustCenter = async () => {
  if (!props.model?.id) return;

  if (!confirm('This will automatically adjust the center point of the model to the average position of all blocks. Continue?')) {
    return;
  }

  errorMessage.value = '';
  transforming.value = true;

  try {
    const updatedModel = await layerModelService.transformAutoAdjustCenter(props.worldId, props.layerId, props.model.id);
    logger.info('Transformed model (auto adjust center)', { modelId: props.model.id });

    // Update form data with new values
    formData.value = { ...updatedModel };

    // Show success message
    alert('Model center auto-adjusted successfully! Mount point: (' + updatedModel.mountX + ', ' + updatedModel.mountY + ', ' + updatedModel.mountZ + ')');

    // Emit saved event to reload model in parent
    emit('saved', updatedModel);
  } catch (error: any) {
    logger.error('Failed to transform model', {}, error);
    errorMessage.value = error.message || 'Failed to transform model';
  } finally {
    transforming.value = false;
  }
};

/**
 * Open manual adjust center dialog
 */
const openManualAdjustDialog = () => {
  manualAdjustOffset.value = { x: 0, y: 0, z: 0 };
  showManualAdjustDialog.value = true;
};

/**
 * Close manual adjust center dialog
 */
const closeManualAdjustDialog = () => {
  showManualAdjustDialog.value = false;
  manualAdjustOffset.value = { x: 0, y: 0, z: 0 };
};

/**
 * Transform model by manually adjusting center
 */
const handleTransformManualAdjustCenter = async () => {
  if (!props.model?.id) return;

  const { x, y, z } = manualAdjustOffset.value;

  errorMessage.value = '';
  transforming.value = true;

  try {
    const updatedModel = await layerModelService.transformManualAdjustCenter(
      props.worldId,
      props.layerId,
      props.model.id,
      x,
      y,
      z
    );
    logger.info('Transformed model (manual adjust center)', {
      modelId: props.model.id,
      offset: { x, y, z }
    });

    // Update form data with new values
    formData.value = { ...updatedModel };

    // Close dialog
    closeManualAdjustDialog();

    // Show success message
    alert('Model center manually adjusted successfully! Mount point: (' + updatedModel.mountX + ', ' + updatedModel.mountY + ', ' + updatedModel.mountZ + ')');

    // Emit saved event to reload model in parent
    emit('saved', updatedModel);
  } catch (error: any) {
    logger.error('Failed to transform model', {}, error);
    errorMessage.value = error.message || 'Failed to transform model';
  } finally {
    transforming.value = false;
  }
};

/**
 * Open move dialog
 */
const openMoveDialog = () => {
  moveOffset.value = { x: 0, y: 0, z: 0 };
  showMoveDialog.value = true;
};

/**
 * Close move dialog
 */
const closeMoveDialog = () => {
  showMoveDialog.value = false;
  moveOffset.value = { x: 0, y: 0, z: 0 };
};

/**
 * Transform model by moving all blocks
 */
const handleTransformMove = async () => {
  if (!props.model?.id) return;

  const { x, y, z } = moveOffset.value;

  errorMessage.value = '';
  transforming.value = true;

  try {
    const result = await layerModelService.transformMove(
      props.worldId,
      props.layerId,
      props.model.id,
      x,
      y,
      z
    );
    logger.info('Transformed model (move)', {
      modelId: props.model.id,
      offset: { x, y, z },
      chunksAffected: result.chunksAffected
    });

    // Update form data with new values
    formData.value = { ...result.model };

    // Close dialog
    closeMoveDialog();

    // Show success message
    alert('Model moved and synced successfully! Offset: (' + x + ', ' + y + ', ' + z + '), Chunks affected: ' + result.chunksAffected);

    // Emit saved event to reload model in parent
    emit('saved', result.model);
  } catch (error: any) {
    logger.error('Failed to transform model', {}, error);
    errorMessage.value = error.message || 'Failed to transform model';
  } finally {
    transforming.value = false;
  }
};

/**
 * Load available worlds for copy (with collections)
 */
const loadCopyWorlds = async () => {
  loadingCopyWorlds.value = true;
  copyError.value = '';

  try {
    copyAvailableWorlds.value = await worldService.getWorlds('withCollections');
    logger.info('Loaded worlds for copy', { count: copyAvailableWorlds.value.length });
  } catch (error: any) {
    logger.error('Failed to load worlds for copy', {}, error);
    copyError.value = 'Failed to load worlds: ' + (error.message || 'Unknown error');
  } finally {
    loadingCopyWorlds.value = false;
  }
};

/**
 * Load layers for selected copy world (MODEL type only)
 */
const loadLayersForCopyWorld = async () => {
  if (!copyFormData.value.targetWorldId) {
    return;
  }

  loadingCopyLayers.value = true;
  copyError.value = '';
  copyAvailableLayers.value = [];

  try {
    const layersResponse = await layerService.getLayers(copyFormData.value.targetWorldId, { limit: 1000 });

    // Filter only MODEL type layers
    const modelLayers = layersResponse.layers.filter(layer => layer.layerType === 'MODEL');

    copyAvailableLayers.value = modelLayers;
    logger.info('Loaded MODEL layers for copy world', {
      worldId: copyFormData.value.targetWorldId,
      count: modelLayers.length
    });
  } catch (error: any) {
    logger.error('Failed to load layers for copy', {}, error);
    copyError.value = 'Failed to load layers: ' + (error.message || 'Unknown error');
  } finally {
    loadingCopyLayers.value = false;
  }
};

/**
 * Handle copy world change
 */
const handleCopyWorldChange = () => {
  // Clear layer selection and error when world changes
  copyError.value = '';
  copyAvailableLayers.value = [];
  copyFormData.value.targetLayerId = '';
};

/**
 * Open copy dialog
 */
const openCopyDialog = async () => {
  copyFormData.value = { targetWorldId: '', targetLayerId: '', newName: '' };
  copyError.value = '';
  copyAvailableLayers.value = [];

  // Load available worlds
  await loadCopyWorlds();

  showCopyDialog.value = true;
};

/**
 * Close copy dialog
 */
const closeCopyDialog = () => {
  showCopyDialog.value = false;
  copyFormData.value = { targetWorldId: '', targetLayerId: '', newName: '' };
  copyError.value = '';
  copyAvailableWorlds.value = [];
  copyAvailableLayers.value = [];
};

/**
 * Copy model to another layer
 */
const handleCopyModel = async () => {
  if (!props.model?.id) return;

  const { targetLayerId, newName } = copyFormData.value;

  if (!targetLayerId) {
    copyError.value = 'Target layer is required';
    return;
  }

  copyError.value = '';
  copying.value = true;

  try {
    const result = await layerModelService.copyModel(
      props.worldId,
      props.layerId,
      props.model.id,
      targetLayerId,
      newName || undefined
    );
    logger.info('Copied model', {
      sourceModelId: props.model.id,
      targetLayerId,
      newModelId: result.id,
      newName
    });

    // Close dialog
    closeCopyDialog();

    // Show success message
    alert('Model copied successfully! New ID: ' + result.id + (newName ? ', Name: ' + result.model.name : ''));
  } catch (error: any) {
    logger.error('Failed to copy model', {}, error);
    copyError.value = error.message || 'Failed to copy model';
  } finally {
    copying.value = false;
  }
};

/**
 * Load available worlds (with collections)
 */
const loadAvailableWorlds = async () => {
  loadingWorlds.value = true;
  referenceSearchError.value = '';

  try {
    availableWorlds.value = await worldService.getWorlds('withCollections');
    logger.info('Loaded worlds for reference', { count: availableWorlds.value.length });
  } catch (error: any) {
    logger.error('Failed to load worlds', {}, error);
    referenceSearchError.value = 'Failed to load worlds: ' + (error.message || 'Unknown error');
  } finally {
    loadingWorlds.value = false;
  }
};

/**
 * Load models for selected world
 */
const loadModelsForWorld = async () => {
  if (!referenceSearch.value.worldId) {
    return;
  }

  loadingModels.value = true;
  referenceSearchError.value = '';
  availableModels.value = [];

  try {
    // Get all layers for the world
    const layersResponse = await layerService.getLayers(referenceSearch.value.worldId, { limit: 1000 });

    // Filter MODEL type layers and load their models
    const modelLayers = layersResponse.layers.filter(layer => layer.layerType === 'MODEL');

    const allModels: LayerModelDto[] = [];
    for (const layer of modelLayers) {
      try {
        const modelsResponse = await layerModelService.getModels(referenceSearch.value.worldId, layer.id);
        allModels.push(...modelsResponse.models.filter(m => m.name)); // Only models with names
      } catch (error) {
        logger.warn('Failed to load models for layer', { layerId: layer.id });
      }
    }

    availableModels.value = allModels;
    logger.info('Loaded models for world', {
      worldId: referenceSearch.value.worldId,
      count: allModels.length
    });
  } catch (error: any) {
    logger.error('Failed to load models', {}, error);
    referenceSearchError.value = 'Failed to load models: ' + (error.message || 'Unknown error');
  } finally {
    loadingModels.value = false;
  }
};

/**
 * Open reference search dialog
 */
const openReferenceSearchDialog = async () => {
  // Parse existing referenceModelId if present
  if (formData.value.referenceModelId) {
    const slashIndex = formData.value.referenceModelId.lastIndexOf('/');
    if (slashIndex > 0) {
      referenceSearch.value = {
        worldId: formData.value.referenceModelId.substring(0, slashIndex),
        modelName: formData.value.referenceModelId.substring(slashIndex + 1)
      };
    } else {
      referenceSearch.value = { worldId: '', modelName: '' };
    }
  } else {
    referenceSearch.value = { worldId: '', modelName: '' };
  }
  referenceSearchError.value = '';
  availableModels.value = [];

  // Load available worlds
  await loadAvailableWorlds();

  showReferenceSearchDialog.value = true;
};

/**
 * Close reference search dialog
 */
const closeReferenceSearchDialog = () => {
  showReferenceSearchDialog.value = false;
  referenceSearchError.value = '';
  referenceSearch.value = { worldId: '', modelName: '' };
  availableWorlds.value = [];
  availableModels.value = [];
};

/**
 * Handle world ID change
 */
const handleWorldIdChange = () => {
  // Clear error and models when world changes
  referenceSearchError.value = '';
  availableModels.value = [];
  referenceSearch.value.modelName = '';
};

/**
 * Handle select reference
 */
const handleSelectReference = () => {
  const { worldId, modelName } = referenceSearch.value;

  if (!worldId || !modelName) {
    referenceSearchError.value = 'Both world ID and model name are required';
    return;
  }

  // Build reference ID in format "worldId/modelName"
  const referenceId = `${worldId}/${modelName}`;
  formData.value.referenceModelId = referenceId;

  logger.info('Selected reference model', { referenceId });

  // Close dialog
  closeReferenceSearchDialog();
};
</script>
