<template>
  <TransitionRoot :show="true" as="template">
    <Dialog as="div" class="relative z-50" @close="handleDialogClose">
      <TransitionChild
        as="template"
        enter="ease-out duration-300"
        enter-from="opacity-0"
        enter-to="opacity-100"
        leave="ease-in duration-200"
        leave-from="opacity-100"
        leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-black bg-opacity-25" />
      </TransitionChild>

      <div class="fixed inset-0 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="ease-out duration-300"
            enter-from="opacity-0 scale-95"
            enter-to="opacity-100 scale-100"
            leave="ease-in duration-200"
            leave-from="opacity-100 scale-100"
            leave-to="opacity-0 scale-95"
          >
            <DialogPanel class="w-full max-w-3xl transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                {{ isEditMode ? 'Edit Hex Grid' : 'Create Hex Grid' }}
              </DialogTitle>

              <!-- Form -->
              <form @submit.prevent="handleSave" class="space-y-4">
        <!-- Position -->
        <div class="grid grid-cols-2 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Position Q</span>
            </label>
            <input
              v-model.number="formData.position.q"
              type="number"
              class="input input-bordered"
              :disabled="isEditMode"
              required
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Position R</span>
            </label>
            <input
              v-model.number="formData.position.r"
              type="number"
              class="input input-bordered"
              :disabled="isEditMode"
              required
            />
          </div>
        </div>

        <!-- Name -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Name</span>
          </label>
          <input
            v-model="formData.name"
            type="text"
            class="input input-bordered"
            placeholder="Enter hex grid name (technical identifier)"
            required
          />
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
            placeholder="Enter hex grid title (display name)"
          />
        </div>

        <!-- Description -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Description</span>
          </label>
          <textarea
            v-model="formData.description"
            class="textarea textarea-bordered"
            rows="3"
            placeholder="Enter description"
            required
          />
        </div>

        <!-- Icon -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Icon (optional)</span>
          </label>
          <input
            v-model="formData.icon"
            type="text"
            class="input input-bordered"
            placeholder="Enter icon (e.g., emoji or identifier)"
          />
        </div>

        <!-- Splash Screen -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Splash Screen (optional)</span>
          </label>
          <input
            v-model="formData.splashScreen"
            type="text"
            class="input input-bordered"
            placeholder="Enter splash screen path or asset reference"
          />
          <label class="label">
            <span class="label-text-alt">Asset path for splash screen image displayed when entering this hex grid</span>
          </label>
        </div>

        <!-- Splash Screen Audio -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Splash Screen Audio (optional)</span>
          </label>
          <input
            v-model="formData.splashScreenAudio"
            type="text"
            class="input input-bordered"
            placeholder="Enter splash screen audio path or asset reference"
          />
          <label class="label">
            <span class="label-text-alt">Asset path for audio played when entering this hex grid</span>
          </label>
        </div>

        <!-- Entry Point Position -->
        <div class="divider">Entry Point (optional)</div>
        <div class="grid grid-cols-3 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Entry X</span>
            </label>
            <input
              v-model.number="formData.entryPoint.position.x"
              type="number"
              class="input input-bordered"
              placeholder="0"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Entry Y</span>
            </label>
            <input
              v-model.number="formData.entryPoint.position.y"
              type="number"
              class="input input-bordered"
              placeholder="0"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Entry Z</span>
            </label>
            <input
              v-model.number="formData.entryPoint.position.z"
              type="number"
              class="input input-bordered"
              placeholder="0"
            />
          </div>
        </div>

        <!-- Entry Point Size -->
        <div class="grid grid-cols-3 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size X</span>
            </label>
            <input
              v-model.number="formData.entryPoint.size.x"
              type="number"
              class="input input-bordered"
              placeholder="10"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size Y</span>
            </label>
            <input
              v-model.number="formData.entryPoint.size.y"
              type="number"
              class="input input-bordered"
              placeholder="10"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size Z</span>
            </label>
            <input
              v-model.number="formData.entryPoint.size.z"
              type="number"
              class="input input-bordered"
              placeholder="10"
            />
          </div>
        </div>

        <!-- Enabled -->
        <div class="form-control">
          <label class="label cursor-pointer justify-start gap-4">
            <input
              v-model="formData.enabled"
              type="checkbox"
              class="checkbox"
            />
            <span class="label-text">Enabled</span>
          </label>
        </div>

        <!-- Parameters -->
        <div class="divider">Parameters (Generator Configuration)</div>
        <div class="space-y-2">
          <div v-for="(value, key) in formData.parameters" :key="key" class="flex gap-2">
            <input
              :value="key"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Key"
              readonly
            />
            <input
              v-model="formData.parameters[key]"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Value"
            />
            <button
              type="button"
              class="btn btn-sm btn-ghost btn-square"
              @click="removeParameter(key)"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="flex gap-2">
            <input
              v-model="newParamKey"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="New parameter key"
              @keyup.enter="addParameter"
            />
            <input
              v-model="newParamValue"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="New parameter value"
              @keyup.enter="addParameter"
            />
            <button
              type="button"
              class="btn btn-sm btn-primary"
              @click="addParameter"
              :disabled="!newParamKey || !newParamValue"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Areas -->
        <div class="divider">Areas (Regional Configuration)</div>
        <div class="space-y-2">
          <div class="text-sm text-base-content/70 mb-2">
            Configure regional parameters for specific areas within the hex grid.
          </div>

          <!-- Areas List -->
          <div v-for="(params, areaKey) in formData.areas" :key="areaKey" class="card bg-base-200 p-3">
            <div class="flex justify-between items-center">
              <div class="flex-1">
                <div class="font-semibold text-sm">{{ parseAreaKey(areaKey).display }}</div>
                <div class="text-xs text-base-content/60">
                  {{ Object.keys(params).length }} parameter(s)
                </div>
              </div>
              <div class="flex gap-1">
                <button
                  type="button"
                  class="btn btn-xs btn-ghost"
                  @click="editArea(areaKey)"
                  title="Edit area"
                >
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  type="button"
                  class="btn btn-xs btn-ghost"
                  @click="removeArea(areaKey)"
                  title="Delete area"
                >
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- Add Area Button -->
          <button
            type="button"
            class="btn btn-sm btn-outline w-full"
            @click="addNewArea"
          >
            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Add Area
          </button>
        </div>

        <!-- Error Display -->
        <ErrorAlert v-if="saveError" :message="saveError" />

        <!-- Actions -->
        <div class="mt-6 flex justify-between gap-2">
          <div class="flex gap-2">
            <button
              type="button"
              class="btn btn-outline btn-sm"
              @click="showJsonEditor = true"
            >
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
              </svg>
              JSON
            </button>
          </div>
          <div class="flex gap-2">
            <button
              type="button"
              class="btn"
              @click="$emit('close')"
              :disabled="saving"
            >
              Cancel
            </button>
            <button
              v-if="isEditMode"
              type="button"
              class="btn btn-warning"
              @click="handleMarkDirty"
              :disabled="markingDirty"
            >
              <span v-if="markingDirty" class="loading loading-spinner loading-sm"></span>
              <svg v-else class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              {{ markingDirty ? 'Marking...' : 'Mark Chunks Dirty' }}
            </button>
            <button
              type="submit"
              class="btn btn-primary"
              :disabled="saving || !isFormValid"
            >
              <span v-if="saving" class="loading loading-spinner loading-sm"></span>
              {{ saving ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </div>
      </form>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>

  <!-- JSON Editor Dialog -->
  <JsonEditorDialog
    v-model:is-open="showJsonEditor"
    :model-value="formData"
    @apply="handleJsonApply"
  />

  <!-- Area Editor Dialog -->
  <AreaEditorDialog
    v-model:is-open="showAreaEditor"
    :area="editingArea"
    @save="handleAreaSave"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { useHexGrids, type HexGridWithId } from '@/composables/useHexGrids';
import { apiService } from '@/services/ApiService';
import ErrorAlert from '@components/ErrorAlert.vue';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import AreaEditorDialog, { type AreaData } from './AreaEditorDialog-simple.vue';

const props = defineProps<{
  hexGrid: HexGridWithId | null;
  worldId: string;
}>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const { createHexGrid, updateHexGrid } = useHexGrids(props.worldId);

const isEditMode = computed(() => props.hexGrid !== null);

// Form data
const formData = ref({
  position: { q: 0, r: 0 },
  name: '',
  title: '',
  description: '',
  icon: '',
  splashScreen: '',
  splashScreenAudio: '',
  enabled: true,
  entryPoint: {
    position: { x: 0, y: 0, z: 0 },
    size: { x: 10, y: 10, z: 10 }
  },
  parameters: {} as Record<string, string>,
  areas: {} as Record<string, Record<string, string>>
});

const saving = ref(false);
const markingDirty = ref(false);
const saveError = ref<string | null>(null);
const showJsonEditor = ref(false);
const showAreaEditor = ref(false);

// Parameters editing
const newParamKey = ref('');
const newParamValue = ref('');

// Area editing
const editingArea = ref<AreaData | null>(null);
const editingAreaKey = ref<string | null>(null);

// Initialize form with existing data
watch(() => props.hexGrid, (hexGrid) => {
  if (hexGrid) {
    formData.value = {
      position: { ...hexGrid.publicData.position },
      name: hexGrid.publicData.name || '',
      title: hexGrid.publicData.title || '',
      description: hexGrid.publicData.description || '',
      icon: hexGrid.publicData.icon || '',
      splashScreen: hexGrid.publicData.splashScreen || '',
      splashScreenAudio: hexGrid.publicData.splashScreenAudio || '',
      enabled: hexGrid.enabled ?? true,
      entryPoint: hexGrid.publicData.entryPoint ? {
        position: { ...hexGrid.publicData.entryPoint.position },
        size: { ...hexGrid.publicData.entryPoint.size }
      } : {
        position: { x: 0, y: 0, z: 0 },
        size: { x: 10, y: 10, z: 10 }
      },
      parameters: hexGrid.parameters ? { ...hexGrid.parameters } : {},
      areas: hexGrid.areas ? JSON.parse(JSON.stringify(hexGrid.areas)) : {}
    };
  } else {
    // Reset for create mode
    formData.value = {
      position: { q: 0, r: 0 },
      name: '',
      title: '',
      description: '',
      icon: '',
      splashScreen: '',
      splashScreenAudio: '',
      enabled: true,
      entryPoint: {
        position: { x: 0, y: 0, z: 0 },
        size: { x: 10, y: 10, z: 10 }
      },
      parameters: {},
      areas: {}
    };
  }
}, { immediate: true });

/**
 * Validate form
 */
const isFormValid = computed(() => {
  return (
    formData.value.name.trim() !== '' &&
    formData.value.description.trim() !== ''
  );
});

/**
 * Check if entry point has any non-zero values
 * Entry point is only valid if at least one position or size value is non-default
 */
const hasEntryPoint = (): boolean => {
  const ep = formData.value.entryPoint;
  // Check if any position value is non-zero OR any size value is non-default (not 10)
  const hasNonZeroPosition = ep.position.x !== 0 || ep.position.y !== 0 || ep.position.z !== 0;
  const hasNonDefaultSize = ep.size.x !== 10 || ep.size.y !== 10 || ep.size.z !== 10;
  return hasNonZeroPosition || hasNonDefaultSize;
};

/**
 * Handle save
 */
const handleSave = async () => {
  if (!isFormValid.value) {
    return;
  }

  saving.value = true;
  saveError.value = null;

  try {
    // Build publicData
    const publicData: any = {
      position: formData.value.position,
      name: formData.value.name,
      description: formData.value.description,
    };

    // Add optional fields
    if (formData.value.title) {
      publicData.title = formData.value.title;
    }
    if (formData.value.icon) {
      publicData.icon = formData.value.icon;
    }
    if (formData.value.splashScreen) {
      publicData.splashScreen = formData.value.splashScreen;
    }
    if (formData.value.splashScreenAudio) {
      publicData.splashScreenAudio = formData.value.splashScreenAudio;
    }
    if (hasEntryPoint()) {
      publicData.entryPoint = formData.value.entryPoint;
    }

    // Build request payload matching backend DTO
    const payload = {
      publicData: publicData,
      enabled: formData.value.enabled,
      parameters: formData.value.parameters,
      areas: formData.value.areas
    };

    if (isEditMode.value) {
      await updateHexGrid(
        formData.value.position.q,
        formData.value.position.r,
        payload
      );
    } else {
      await createHexGrid(payload);
    }

    emit('saved');
  } catch (err) {
    saveError.value = `Failed to save hex grid: ${(err as Error).message}`;
  } finally {
    saving.value = false;
  }
};

/**
 * Handle mark chunks dirty
 */
const handleMarkDirty = async () => {
  if (!isEditMode.value || !props.hexGrid) return;

  const confirmed = confirm(
    `This will mark all chunks affected by this hex grid as dirty for regeneration.\n\n` +
    `Position: ${formData.value.position.q}:${formData.value.position.r}\n\n` +
    `Continue?`
  );

  if (!confirmed) return;

  markingDirty.value = true;

  try {
    const response = await apiService.post(
      `/control/worlds/${props.worldId}/hexgrid/${formData.value.position.q}/${formData.value.position.r}/dirty`,
      {}
    );

    alert(`Successfully marked ${response.chunksMarked} chunks as dirty!`);
  } catch (err) {
    alert(`Failed to mark chunks dirty: ${(err as Error).message}`);
  } finally {
    markingDirty.value = false;
  }
};

/**
 * Handle JSON apply from JSON editor
 */
const handleJsonApply = (jsonData: any) => {
  formData.value = jsonData;
};

/**
 * Add parameter
 */
const addParameter = () => {
  if (!newParamKey.value || !newParamValue.value) return;

  formData.value.parameters[newParamKey.value] = newParamValue.value;
  newParamKey.value = '';
  newParamValue.value = '';
};

/**
 * Remove parameter
 */
const removeParameter = (key: string) => {
  delete formData.value.parameters[key];
};

/**
 * Handle dialog close - prevent closing if area editor is open
 */
const handleDialogClose = () => {
  if (showAreaEditor.value) {
    // Don't close main dialog if area editor is open
    return;
  }
  emit('close');
};

/**
 * Parse area key for display
 */
const parseAreaKey = (key: string): { display: string } => {
  try {
    const [pos, size] = key.split('+');
    const [x, z] = pos.split(',').map(Number);
    const [sizeX, sizeZ] = size.split('x').map(Number);
    return {
      display: `Position (${x}, ${z}) | Size ${sizeX} x ${sizeZ}`
    };
  } catch {
    return { display: key };
  }
};

/**
 * Format area key: "x,z+sizeX'x'sizeZ"
 */
const formatAreaKey = (x: number, z: number, sizeX: number, sizeZ: number): string => {
  return `${x},${z}+${sizeX}x${sizeZ}`;
};

/**
 * Parse area key to AreaData
 */
const parseAreaKeyToData = (key: string, parameters: Record<string, string>): AreaData => {
  try {
    const [pos, size] = key.split('+');
    const [x, z] = pos.split(',').map(Number);
    const [sizeX, sizeZ] = size.split('x').map(Number);
    return { x, z, sizeX, sizeZ, parameters };
  } catch {
    return { x: 0, z: 0, sizeX: 16, sizeZ: 16, parameters };
  }
};

/**
 * Add new area
 */
const addNewArea = () => {
  editingArea.value = null;
  editingAreaKey.value = null;
  showAreaEditor.value = true;
};

/**
 * Edit area
 */
const editArea = (areaKey: string) => {
  const params = formData.value.areas[areaKey] || {};
  editingArea.value = parseAreaKeyToData(areaKey, params);
  editingAreaKey.value = areaKey;
  showAreaEditor.value = true;
};

/**
 * Remove area
 */
const removeArea = (areaKey: string) => {
  if (confirm(`Remove area ${parseAreaKey(areaKey).display}?`)) {
    delete formData.value.areas[areaKey];
  }
};

/**
 * Handle area save
 */
const handleAreaSave = (area: AreaData) => {
  const newKey = formatAreaKey(area.x, area.z, area.sizeX, area.sizeZ);

  // If editing and key changed, remove old key
  if (editingAreaKey.value && editingAreaKey.value !== newKey) {
    delete formData.value.areas[editingAreaKey.value];
  }

  // Save with new/same key
  formData.value.areas[newKey] = area.parameters;
};
</script>
