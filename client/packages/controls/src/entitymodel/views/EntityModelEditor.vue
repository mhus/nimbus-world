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
        {{ isNew ? 'Create New Entity Model' : 'Edit Entity Model' }}
      </h2>
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
          <form @submit.prevent="handleSave" class="space-y-4">
            <!-- Model ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Model ID</span>
              </label>
              <input
                v-model="formData.modelId"
                type="text"
                placeholder="Enter unique model ID"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique identifier for this entity model</span>
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

            <!-- Action Buttons -->
            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Model Properties Card -->
      <div v-if="!isNew && modelData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Model Properties</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Type</span>
              </label>
              <input
                v-model="modelData.type"
                type="text"
                class="input input-bordered input-sm"
                placeholder="Entity type"
              />
            </div>

            <!-- Model Path -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Model Path</span>
              </label>
              <input
                v-model="modelData.modelPath"
                type="text"
                class="input input-bordered input-sm"
                placeholder="models/entity/..."
              />
            </div>

            <!-- Pose Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Pose Type</span>
              </label>
              <select v-model="modelData.poseType" class="select select-bordered select-sm">
                <option value="2-Legs">2-Legs</option>
                <option value="4-Legs">4-Legs</option>
                <option value="6-Legs">6-Legs</option>
                <option value="Wings">Wings</option>
                <option value="Fish">Fish</option>
                <option value="Snake">Snake</option>
                <option value="Humanoid">Humanoid</option>
                <option value="Slime">Slime</option>
              </select>
            </div>

            <!-- Max Pitch -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Max Pitch</span>
              </label>
              <input
                v-model.number="modelData.maxPitch"
                type="number"
                class="input input-bordered input-sm"
                placeholder="90"
              />
            </div>
          </div>

          <!-- Scale (Vector3) -->
          <div class="mt-4">
            <label class="label">
              <span class="label-text font-medium">Scale</span>
            </label>
            <div class="grid grid-cols-3 gap-2">
              <input
                v-model.number="modelData.scale.x"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="X"
              />
              <input
                v-model.number="modelData.scale.y"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="Y"
              />
              <input
                v-model.number="modelData.scale.z"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="Z"
              />
            </div>
          </div>

          <!-- Position Offset (Vector3) -->
          <div class="mt-4">
            <label class="label">
              <span class="label-text font-medium">Position Offset</span>
            </label>
            <div class="grid grid-cols-3 gap-2">
              <input
                v-model.number="modelData.positionOffset.x"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="X"
              />
              <input
                v-model.number="modelData.positionOffset.y"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="Y"
              />
              <input
                v-model.number="modelData.positionOffset.z"
                type="number"
                step="0.1"
                class="input input-bordered input-sm"
                placeholder="Z"
              />
            </div>
          </div>

          <!-- Rotation Offset (Vector3) -->
          <div class="mt-4">
            <label class="label">
              <span class="label-text font-medium">Rotation Offset</span>
            </label>
            <div class="grid grid-cols-3 gap-2">
              <input
                v-model.number="modelData.rotationOffset.x"
                type="number"
                step="1"
                class="input input-bordered input-sm"
                placeholder="X (degrees)"
              />
              <input
                v-model.number="modelData.rotationOffset.y"
                type="number"
                step="1"
                class="input input-bordered input-sm"
                placeholder="Y (degrees)"
              />
              <input
                v-model.number="modelData.rotationOffset.z"
                type="number"
                step="1"
                class="input input-bordered input-sm"
                placeholder="Z (degrees)"
              />
            </div>
          </div>

          <div class="card-actions justify-end mt-4">
            <button
              type="button"
              class="btn btn-sm btn-primary"
              @click="handleSavePublicData"
              :disabled="saving"
            >
              <span v-if="saving" class="loading loading-spinner loading-xs"></span>
              <span v-else>Save Properties</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Dimensions Card -->
      <div v-if="!isNew && modelData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Collision Dimensions</h3>
          <p class="text-sm text-base-content/70 mb-4">Define collision box dimensions for different movement states</p>

          <div class="space-y-4">
            <!-- Walk Dimensions -->
            <div class="border border-base-300 rounded-lg p-3">
              <h4 class="font-semibold text-sm mb-2">Walk</h4>
              <div class="grid grid-cols-3 gap-2">
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Height</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('walk').height"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="1.8"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Width</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('walk').width"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Footprint</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('walk').footprint"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
              </div>
            </div>

            <!-- Crouch Dimensions -->
            <div class="border border-base-300 rounded-lg p-3">
              <h4 class="font-semibold text-sm mb-2">Crouch</h4>
              <div class="grid grid-cols-3 gap-2">
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Height</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('crouch').height"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="1.2"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Width</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('crouch').width"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Footprint</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('crouch').footprint"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
              </div>
            </div>

            <!-- Swim Dimensions -->
            <div class="border border-base-300 rounded-lg p-3">
              <h4 class="font-semibold text-sm mb-2">Swim</h4>
              <div class="grid grid-cols-3 gap-2">
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Height</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('swim').height"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Width</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('swim').width"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Footprint</span>
                  </label>
                  <input
                    v-model.number="ensureDimension('swim').footprint"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="0.6"
                  />
                </div>
              </div>
            </div>
          </div>

          <div class="card-actions justify-end mt-4">
            <button
              type="button"
              class="btn btn-sm btn-primary"
              @click="handleSavePublicData"
              :disabled="saving"
            >
              <span v-if="saving" class="loading loading-spinner loading-xs"></span>
              <span v-else>Save All Properties</span>
            </button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { entityModelService, type EntityModelData } from '../services/EntityModelService';

const props = defineProps<{
  entityModel: EntityModelData | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentWorldId } = useWorld();

const isNew = computed(() => props.entityModel === 'new');

const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  modelId: '',
  enabled: true,
});

const modelData = ref<any>(null);

const loadEntityModel = () => {
  if (isNew.value) {
    formData.value = {
      modelId: '',
      enabled: true,
    };
    modelData.value = {
      id: '',
      type: '',
      modelPath: '',
      positionOffset: { x: 0, y: 0, z: 0 },
      rotationOffset: { x: 0, y: 0, z: 0 },
      scale: { x: 1, y: 1, z: 1 },
      maxPitch: 90,
      poseType: 'Humanoid',
      poseMapping: {},
      modelModifierMapping: {},
      dimensions: {},
    };
    return;
  }

  const model = props.entityModel as EntityModelData;
  formData.value = {
    modelId: model.modelId,
    enabled: model.enabled,
  };
  modelData.value = model.publicData || {};
  // Ensure Vector3 objects exist
  if (!modelData.value.scale) modelData.value.scale = { x: 1, y: 1, z: 1 };
  if (!modelData.value.positionOffset) modelData.value.positionOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.rotationOffset) modelData.value.rotationOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.dimensions) modelData.value.dimensions = {};
};

const ensureDimension = (state: string) => {
  if (!modelData.value.dimensions) {
    modelData.value.dimensions = {};
  }
  if (!modelData.value.dimensions[state]) {
    modelData.value.dimensions[state] = { height: 1.8, width: 0.6, footprint: 0.6 };
  }
  return modelData.value.dimensions[state];
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
    if (isNew.value) {
      let publicData;
      try {
        publicData = JSON.parse(publicDataJson.value);
      } catch {
        publicData = { id: formData.value.modelId, displayName: formData.value.modelId };
      }

      await entityModelService.createEntityModel(currentWorldId.value, {
        modelId: formData.value.modelId,
        publicData,
      });
      successMessage.value = 'Entity model created successfully';
    } else {
      await entityModelService.updateEntityModel(currentWorldId.value, formData.value.modelId, {
        enabled: formData.value.enabled,
      });
      successMessage.value = 'Entity model updated successfully';
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save entity model';
    console.error('[EntityModelEditor] Failed to save entity model:', e);
  } finally {
    saving.value = false;
  }
};

const handleSavePublicData = async () => {
  if (!currentWorldId.value) {
    error.value = 'No world selected';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    // Update publicData from form fields
    await entityModelService.updateEntityModel(currentWorldId.value, formData.value.modelId, {
      publicData: modelData.value,
    });
    successMessage.value = 'Model properties updated successfully';
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save properties';
    console.error('[EntityModelEditor] Failed to save properties:', e);
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  // Note: WorldSelector in EntityModelApp loads worlds with 'withCollections' filter
  loadEntityModel();
});
</script>
