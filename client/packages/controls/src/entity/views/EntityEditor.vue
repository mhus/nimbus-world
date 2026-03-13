<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button class="btn btn-ghost gap-2" @click="handleBack">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to List
        </button>
      </div>
      <h2 class="text-2xl font-bold">
        {{ isNew ? 'Create New Entity' : 'Edit Entity' }}
      </h2>
      <div class="flex gap-2">
        <button
          v-if="!isNew && entityData"
          type="button"
          class="btn btn-outline btn-sm"
          @click="showJsonEditor = true"
        >
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
          Source
        </button>
        <button type="button" class="btn btn-ghost" @click="handleBack">
          Cancel
        </button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="handleSave">
          <span v-if="saving" class="loading loading-spinner loading-sm"></span>
          <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
        </button>
      </div>
    </div>

    <!-- Error State -->
    <div v-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Edit Form -->
    <div v-else class="space-y-4">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <div class="space-y-4">
            <!-- Entity ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Entity ID</span>
              </label>
              <input
                v-model="formData.entityId"
                type="text"
                placeholder="Enter unique entity ID"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique identifier for this entity</span>
              </label>
            </div>

            <!-- Model ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Model ID</span>
              </label>
              <input
                v-model="formData.modelId"
                type="text"
                placeholder="Enter entity model ID"
                class="input input-bordered w-full"
                required
              />
              <label class="label">
                <span class="label-text-alt">Reference to entity model definition</span>
              </label>
            </div>

            <!-- Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Type</span>
              </label>
              <select
                v-model="formData.type"
                class="select select-bordered w-full"
              >
                <option value="OTHER">Other</option>
                <option value="ANIMAL">Animal</option>
                <option value="NPC">NPC</option>
                <option value="PLAYER">Player</option>
              </select>
              <label class="label">
                <span class="label-text-alt">Classification of this entity</span>
              </label>
            </div>

            <!-- Portrait Path -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Portrait Path</span>
              </label>
              <input
                v-model="formData.portraitPath"
                type="text"
                placeholder="Path to portrait image"
                class="input input-bordered w-full"
              />
              <label class="label">
                <span class="label-text-alt">Image path for dialog UI portrait</span>
              </label>
            </div>

            <!-- Enabled Status -->
            <div v-if="!isNew" class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Enabled</span>
                <input
                  v-model="formData.enabled"
                  type="checkbox"
                  class="toggle toggle-success"
                />
              </label>
            </div>

            <!-- Epoches -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Epoches</span>
              </label>
              <input
                v-model="epochesText"
                type="text"
                placeholder="Comma-separated epoch numbers, e.g. 0,1,2"
                class="input input-bordered w-full"
              />
              <label class="label">
                <span class="label-text-alt">Comma-separated list of epoch numbers, e.g. 0,1,2 (empty = not visible in any epoch)</span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Entity Properties Card -->
      <div v-if="entityData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Entity Properties</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Name</span>
              </label>
              <input
                v-model="entityData.name"
                type="text"
                class="input input-bordered input-sm"
                placeholder="Entity display name"
              />
            </div>

            <!-- Gender -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Gender</span>
              </label>
              <select v-model="entityData.gender" class="select select-bordered select-sm">
                <option value="">None</option>
                <option value="M">M (Male)</option>
                <option value="W">W (Female)</option>
                <option value="D">D (Diverse)</option>
              </select>
            </div>

            <!-- Movement Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Movement Type</span>
              </label>
              <select v-model="entityData.movementType" class="select select-bordered select-sm">
                <option value="static">Static</option>
                <option value="passive">Passive</option>
                <option value="slow">Slow</option>
                <option value="dynamic">Dynamic</option>
              </select>
            </div>

            <!-- Controlled By -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Controlled By</span>
              </label>
              <select v-model="entityData.controlledBy" class="select select-bordered select-sm">
                <option value="player">Player</option>
                <option value="server">Server</option>
                <option value="ai">AI</option>
                <option value="client">Client</option>
              </select>
            </div>

            <!-- Health Max -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Max Health</span>
              </label>
              <input
                v-model.number="entityData.healthMax"
                type="number"
                class="input input-bordered input-sm"
                placeholder="100"
              />
            </div>
          </div>

          <!-- Checkboxes -->
          <div class="grid grid-cols-2 md:grid-cols-3 gap-3 mt-4">
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.solid" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Solid</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.interactive" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Interactive</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.physics" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Physics</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.clientPhysics" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Client Physics</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.notifyOnCollision" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Notify Collision</span>
            </label>
          </div>
        </div>
      </div>

      <!-- 3D Model Preview (sticky) -->
      <div v-if="entityModelData && entityModelData.modelPath" class="card bg-base-100 shadow-xl sticky top-2 z-10">
        <div class="card-body p-3">
          <ModelPreview
            ref="modelPreviewRef"
            :model-url="previewModelUrl"
            :modifier-mapping="modifierMapping"
            :modifier-values="entityData?.modelModifier || {}"
            class="w-full h-[300px]"
          />
        </div>
      </div>

      <!-- Model Modifier Card -->
      <div v-if="entityData && modifierKeys.length > 0" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Model Modifier</h3>
          <p class="text-sm text-base-content/70 mb-4">Visual modifications for this entity instance</p>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div
              v-for="key in modifierKeys"
              :key="key"
              class="form-control"
            >
              <label class="label py-1">
                <span class="label-text font-medium">{{ key }}</span>
              </label>
              <div class="flex items-center gap-2">
                <input
                  v-if="isColorModifier(key)"
                  type="color"
                  :value="entityData.modelModifier[key] || '#ffffff'"
                  class="w-10 h-10 rounded cursor-pointer border border-base-300"
                  @input="entityData.modelModifier[key] = ($event.target as HTMLInputElement).value"
                />
                <input
                  v-model="entityData.modelModifier[key]"
                  type="text"
                  class="input input-bordered input-sm flex-1"
                  :placeholder="isColorModifier(key) ? '#ffffff' : '1.0'"
                />
                <button
                  v-if="entityData.modelModifier[key]"
                  type="button"
                  class="btn btn-ghost btn-square btn-xs text-error"
                  title="Clear"
                  @click="delete entityData.modelModifier[key]"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Parameters Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between">
            <h3 class="card-title">Server Parameters</h3>
            <button type="button" class="btn btn-ghost btn-sm" @click="addParameter">
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add
            </button>
          </div>
          <div v-if="parameterEntries.length === 0" class="text-sm text-base-content/50 py-2">
            No server parameters defined
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="(entry, index) in parameterEntries"
              :key="index"
              class="flex items-center gap-2"
            >
              <input
                v-model="entry.key"
                type="text"
                placeholder="Key"
                class="input input-bordered input-sm flex-1"
              />
              <input
                v-model="entry.value"
                type="text"
                placeholder="Value"
                class="input input-bordered input-sm flex-[2]"
              />
              <button
                type="button"
                class="btn btn-ghost btn-sm btn-square text-error"
                @click="removeParameter(index)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Success Message -->
    <div v-if="successMessage" class="alert alert-success">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
      <span>{{ successMessage }}</span>
    </div>

    <!-- JSON Editor Dialog -->
    <JsonEditorDialog
      v-model:is-open="showJsonEditor"
      :model-value="entityData"
      @apply="handleJsonApply"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { entityService, type EntityData, type EntityType } from '../services/EntityService';
import { entityModelService } from '../../entitymodel/services/EntityModelService';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import ModelPreview from '@/character-panel/ModelPreview.vue';
import { apiService } from '@/services/ApiService';

const props = defineProps<{
  entity: EntityData | 'new';
  currentEpoch?: number;
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentWorldId, loadWorlds } = useWorld();

const isNew = computed(() => props.entity === 'new');

const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  entityId: '',
  modelId: '',
  type: 'OTHER' as EntityType,
  portraitPath: '',
  enabled: true,
});

const entityData = ref<any>(null);
const showJsonEditor = ref(false);
const parameterEntries = ref<{ key: string; value: string }[]>([]);
const epochesText = ref('');

// Model preview
const modelPreviewRef = ref<InstanceType<typeof ModelPreview> | null>(null);
const entityModelData = ref<any>(null);
const loadingModel = ref(false);

const modifierMapping = computed<Record<string, string>>(() => {
  return entityModelData.value?.modelModifierMapping || {};
});

const modifierKeys = computed<string[]>(() => {
  return Object.keys(modifierMapping.value);
});

const previewModelUrl = computed(() => {
  const mp = entityModelData.value?.modelPath;
  if (!mp || !currentWorldId.value) return '';
  return apiService.getBaseUrl() + '/control/worlds/' + currentWorldId.value + '/assets/' + mp;
});

const isColorModifier = (key: string): boolean => {
  const mapping = modifierMapping.value[key];
  if (!mapping) return false;
  return mapping.split(';').some((p: string) => p.trim().startsWith('color:'));
};

const loadEntityModel = async (modelId: string) => {
  if (!modelId || !currentWorldId.value) {
    entityModelData.value = null;
    return;
  }
  loadingModel.value = true;
  try {
    const data = await entityModelService.getEntityModel(currentWorldId.value, modelId);
    entityModelData.value = data;
  } catch (e) {
    console.error('[EntityEditor] Failed to load entity model:', e);
    entityModelData.value = null;
  } finally {
    loadingModel.value = false;
  }
};

// Watch modelId changes to reload entity model
watch(() => formData.value.modelId, (newModelId) => {
  loadEntityModel(newModelId);
});

const addParameter = () => {
  parameterEntries.value.push({ key: '', value: '' });
};

const removeParameter = (index: number) => {
  parameterEntries.value.splice(index, 1);
};

const parseEpoches = (): number[] => {
  return epochesText.value
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0 && !isNaN(Number(s)))
    .map(s => Number(s));
};

const parametersToMap = (): Record<string, string> => {
  const map: Record<string, string> = {};
  for (const entry of parameterEntries.value) {
    if (entry.key.trim()) {
      map[entry.key.trim()] = entry.value;
    }
  }
  return map;
};

const loadParametersFromMap = (params: Record<string, string> | null | undefined) => {
  if (!params || Object.keys(params).length === 0) {
    parameterEntries.value = [];
    return;
  }
  parameterEntries.value = Object.entries(params).map(([key, value]) => ({ key, value: value ?? '' }));
};

const loadEntity = () => {
  if (isNew.value) {
    formData.value = {
      entityId: '',
      modelId: '',
      type: 'OTHER' as EntityType,
      portraitPath: '',
      enabled: true,
    };
    entityData.value = {
      id: '',
      name: '',
      model: '',
      modelModifier: {},
      movementType: 'static',
      controlledBy: 'server',
      solid: false,
      interactive: false,
      physics: false,
      clientPhysics: false,
      notifyOnCollision: false,
      healthMax: 100,
    };
    parameterEntries.value = [];
    if (props.currentEpoch !== undefined) {
      epochesText.value = String(props.currentEpoch);
    }
    return;
  }

  const entity = props.entity as EntityData;
  formData.value = {
    entityId: entity.entityId,
    modelId: entity.modelId,
    type: entity.type || 'OTHER',
    portraitPath: entity.portraitPath || '',
    enabled: entity.enabled,
  };
  entityData.value = entity.publicData || {};
  if (!entityData.value.modelModifier) entityData.value.modelModifier = {};
  loadParametersFromMap(entity.server);
  epochesText.value = (entity.epoches || []).join(',');
};

const handleSave = async () => {
  if (!currentWorldId.value) {
    error.value = 'No world selected';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    const params = parametersToMap();
    if (isNew.value) {
      await entityService.createEntity(currentWorldId.value, {
        entityId: formData.value.entityId,
        publicData: entityData.value,
        modelId: formData.value.modelId,
        type: formData.value.type,
        portraitPath: formData.value.portraitPath || undefined,
        server: params,
        epoches: parseEpoches(),
      });
      successMessage.value = 'Entity created successfully';
    } else {
      await entityService.updateEntity(currentWorldId.value, formData.value.entityId, {
        modelId: formData.value.modelId,
        enabled: formData.value.enabled,
        type: formData.value.type,
        portraitPath: formData.value.portraitPath || undefined,
        publicData: entityData.value,
        server: params,
        epoches: parseEpoches(),
      });
      successMessage.value = 'Entity saved successfully';
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save entity';
    console.error('[EntityEditor] Failed to save entity:', e);
  } finally {
    saving.value = false;
  }
};

const handleJsonApply = (jsonData: any) => {
  entityData.value = jsonData;
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  // Load worlds with mainWorldsAndInstances filter for entity editor
  loadWorlds('mainWorldsAndInstances');
  loadEntity();
  // Load entity model for preview
  if (formData.value.modelId) {
    loadEntityModel(formData.value.modelId);
  }
});
</script>
