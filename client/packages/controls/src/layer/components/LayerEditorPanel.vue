<template>
  <div class="modal modal-open" @click.self="emit('close')">
    <div class="modal-box max-w-4xl" @click.stop>
      <h3 class="font-bold text-lg mb-4">
        {{ isEditMode ? 'Edit Layer' : 'Create Layer' }}
      </h3>

      <!-- Error Alert -->
      <ErrorAlert v-if="errorMessage" :message="errorMessage" class="mb-4" />

      <form @submit.prevent="handleSave" class="space-y-4">
        <!-- Name -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Name *</span>
          </label>
          <input
            v-model="formData.name"
            type="text"
            class="input input-bordered"
            placeholder="Enter layer name"
            required
          />
        </div>

        <!-- Layer Type -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Layer Type *</span>
          </label>
          <select
            v-model="formData.layerType"
            class="select select-bordered"
            :disabled="isEditMode"
            required
          >
            <option value="">Select type</option>
            <option value="GROUND">Ground</option>
            <option value="MODEL">Model</option>
          </select>
          <label class="label">
            <span class="label-text-alt text-warning">Layer type cannot be changed after creation</span>
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
            placeholder="Layer order (lower values render first)"
            required
          />
          <label class="label">
            <span class="label-text-alt">Lower values are rendered first (bottom), higher values on top</span>
          </label>
        </div>


        <!-- Enabled -->
        <div class="form-control">
          <label class="label cursor-pointer">
            <span class="label-text">Enabled</span>
            <input
              v-model="formData.enabled"
              type="checkbox"
              class="checkbox checkbox-primary"
            />
          </label>
          <label class="label">
            <span class="label-text-alt">Layer enabled flag (soft delete)</span>
          </label>
        </div>

        <!-- Base Ground -->
        <div class="form-control">
          <label class="label cursor-pointer">
            <span class="label-text">Base Ground Layer</span>
            <input
              v-model="formData.baseGround"
              type="checkbox"
              class="checkbox checkbox-primary"
            />
          </label>
          <label class="label">
            <span class="label-text-alt">If true, this is the base ground layer for the world</span>
          </label>
        </div>

        <!-- All Chunks -->
        <div class="form-control">
          <label class="label cursor-pointer">
            <span class="label-text">Affects All Chunks</span>
            <input
              v-model="formData.allChunks"
              type="checkbox"
              class="checkbox checkbox-primary"
            />
          </label>
          <label class="label">
            <span class="label-text-alt">If true, this layer affects all chunks in the world</span>
          </label>
        </div>

        <!-- Groups (only for GROUND layers) -->
        <div v-if="formData.layerType === 'GROUND'" class="form-control">
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
            <span class="label-text-alt">Group mapping: name → ID for organized block management in GROUND layers</span>
          </label>
        </div>

        <!-- Affected Chunks (only if not allChunks) -->
        <div v-if="!formData.allChunks" class="form-control">
          <label class="label">
            <span class="label-text">Affected Chunks</span>
          </label>
          <textarea
            v-model="affectedChunksText"
            class="textarea textarea-bordered"
            placeholder="Enter chunk keys (format: cx:cz), one per line. Example: 0:0"
            rows="4"
          ></textarea>
          <label class="label">
            <span class="label-text-alt">List of chunk keys affected by this layer</span>
          </label>
        </div>

        <!-- Model Management (only for MODEL layers and edit mode) -->
        <div v-if="isEditMode && formData.layerType === 'MODEL'" class="divider">Layer Models</div>

        <div v-if="isEditMode && formData.layerType === 'MODEL'" class="space-y-4">
          <div class="flex justify-between items-center">
            <h4 class="font-semibold">Models in this Layer</h4>
            <button
              type="button"
              class="btn btn-sm btn-primary"
              @click="openCreateModelDialog"
            >
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              New Model
            </button>
          </div>

          <LoadingSpinner v-if="loadingModels" />
          <ModelList
            v-else
            :models="models"
            @edit="openEditModelDialog"
            @delete="handleDeleteModel"
          />
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
            Open Grid Editor (Terrain)
          </button>
          <button
            v-if="isEditMode"
            type="button"
            class="btn btn-warning"
            :disabled="regenerating"
            @click="handleRegenerate"
          >
            <span v-if="regenerating" class="loading loading-spinner"></span>
            <svg v-else class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ regenerating ? 'Regenerating...' : 'Regenerate Layer' }}
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
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import type { WLayer, LayerModelDto, CreateLayerRequest, UpdateLayerRequest } from '@nimbus/shared';
import ErrorAlert from '@components/ErrorAlert.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ModelList from '@layer/components/ModelList.vue';
import ModelEditorPanel from '@layer/components/ModelEditorPanel.vue';
import { useLayers } from '@/composables/useLayers';
import { layerModelService } from '@/services/LayerModelService';
import { layerService } from '@/services/LayerService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('LayerEditorPanel');

interface Props {
  layer: WLayer | null;
  worldId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved', layer: WLayer): void;
  (e: 'openModelEditor', layerId: string, layerDataId: string, model: LayerModelDto | null): void;
  (e: 'openGridEditor', params: {
    sourceType: 'terrain' | 'model';
    layerId?: string;
    layerName?: string;
    modelId?: string;
    modelName?: string;
  }): void;
}>();

const isEditMode = computed(() => !!props.layer);

// Use layers composable for API calls
const { createLayer, updateLayer } = useLayers(computed(() => props.worldId).value);

const formData = ref<Partial<WLayer>>({
  name: '',
  layerType: undefined,
  order: 0,
  enabled: true,
  baseGround: false,
  allChunks: true,
  affectedChunks: [],
  groups: {}
});

const affectedChunksText = ref('');
const errorMessage = ref('');
const saving = ref(false);
const regenerating = ref(false);

// Model management
const models = ref<LayerModelDto[]>([]);
const loadingModels = ref(false);

// Grid editor state
// Removed: selectedModelForGrid - no longer needed
const showGridEditor = ref(false);

// Initialize form data
if (props.layer) {
  formData.value = { ...props.layer };
  affectedChunksText.value = (props.layer.affectedChunks || []).join('\n');
}

// Watch affectedChunksText and update formData
watch(affectedChunksText, (newValue) => {
  formData.value.affectedChunks = newValue
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0);
});

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
 * Load models for this layer
 */
const loadModels = async () => {
  if (!props.layer?.id || !props.worldId) return;

  loadingModels.value = true;
  try {
    const response = await layerModelService.getModels(props.worldId, props.layer.id);
    models.value = response.models;
    logger.info('Loaded models', {
      count: models.value.length,
      layerId: props.layer.id
    });
  } catch (error: any) {
    logger.error('Failed to load models', { layerId: props.layer.id }, error);
    errorMessage.value = `Failed to load models: ${error.message}`;
  } finally {
    loadingModels.value = false;
  }
};

/**
 * Open create model dialog
 */
const openCreateModelDialog = () => {
  if (!props.layer?.id || !props.layer?.layerDataId) return;
  emit('openModelEditor', props.layer.id, props.layer.layerDataId, null);
};

/**
 * Open edit model dialog
 */
const openEditModelDialog = (model: LayerModelDto) => {
  if (!props.layer?.id || !props.layer?.layerDataId) return;
  emit('openModelEditor', props.layer.id, props.layer.layerDataId, model);
};

/**
 * Handle delete model
 */
const handleDeleteModel = async (model: LayerModelDto) => {
  if (!confirm(`Are you sure you want to delete model "${model.title || model.name || 'Unnamed'}"?`)) {
    return;
  }

  if (!props.layer?.id || !model.id) return;

  try {
    await layerModelService.deleteModel(props.worldId, props.layer.id, model.id);
    logger.info('Deleted model', { modelId: model.id });
    await loadModels();
  } catch (error: any) {
    logger.error('Failed to delete model', { modelId: model.id }, error);
    errorMessage.value = `Failed to delete model: ${error.message}`;
  }
};

/**
 * Open grid editor for TERRAIN layer
 */
/**
 * Open grid editor - always opens terrain view for this layer
 */
const openGridEditor = () => {
  emit('openGridEditor', {
    sourceType: 'terrain',
    layerId: props.layer?.id,
    layerName: props.layer?.name
  });
};

// Load models when layer is MODEL type
onMounted(() => {
  if (isEditMode.value && props.layer?.layerType === 'MODEL' && props.layer?.layerDataId) {
    loadModels();
  }
});

// Expose loadModels for parent component
defineExpose({
  loadModels
});

/**
 * Handle save
 */
const handleSave = async () => {
  errorMessage.value = '';
  saving.value = true;

  try {
    // Validate
    if (!formData.value.name?.trim()) {
      throw new Error('Name is required');
    }
    if (!formData.value.layerType) {
      throw new Error('Layer type is required');
    }
    if (formData.value.order === undefined) {
      throw new Error('Order is required');
    }

    if (isEditMode.value && props.layer?.id) {
      // Update existing layer
      const updateData: UpdateLayerRequest = {
        name: formData.value.name.trim(),
        allChunks: formData.value.allChunks,
        affectedChunks: formData.value.affectedChunks,
        order: formData.value.order,
        enabled: formData.value.enabled,
        baseGround: formData.value.baseGround,
        groups: formData.value.groups
      };
      const success = await updateLayer(props.layer.id, updateData);
      if (!success) {
        throw new Error('Failed to update layer');
      }
    } else {
      // Create new layer
      const createData: CreateLayerRequest = {
        name: formData.value.name.trim(),
        layerType: formData.value.layerType,
        allChunks: formData.value.allChunks,
        affectedChunks: formData.value.affectedChunks,
        order: formData.value.order,
        enabled: formData.value.enabled,
        baseGround: formData.value.baseGround,
        groups: formData.value.groups
      };
      const id = await createLayer(createData);
      if (!id) {
        throw new Error('Failed to create layer');
      }
    }

    emit('saved', formData.value as WLayer);
  } catch (error: any) {
    errorMessage.value = error.message || 'Failed to save layer';
  } finally {
    saving.value = false;
  }
};

/**
 * Handle layer regeneration
 */
const handleRegenerate = async () => {
  if (!props.layer?.id) return;

  const confirmed = confirm(
    'This will completely regenerate the layer.\n\n' +
    '- For MODEL layers: Creates a job to recreate terrain from all models.\n' +
    '- For GROUND layers: Marks all affected chunks as dirty.\n\n' +
    'Continue?'
  );

  if (!confirmed) return;

  errorMessage.value = '';
  regenerating.value = true;

  try {
    await layerService.regenerate(props.worldId, props.layer.id);
    logger.info('Layer regeneration triggered', { layerId: props.layer.id });

    alert('Layer regeneration triggered successfully!');
    emit('saved', formData.value as WLayer);
  } catch (error: any) {
    logger.error('Failed to trigger regeneration', {}, error);
    errorMessage.value = error.message || 'Failed to trigger layer regeneration';
  } finally {
    regenerating.value = false;
  }
};
</script>
