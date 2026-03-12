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
      <div class="flex gap-2">
        <button
          v-if="!isNew && modelData"
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
          </div>
        </div>
      </div>

      <!-- Model Properties Card -->
      <div v-if="modelData" class="card bg-base-100 shadow-xl">
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

            <!-- Gender -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Gender</span>
              </label>
              <select v-model="modelData.gender" class="select select-bordered select-sm">
                <option value="">None</option>
                <option value="M">M (Male)</option>
                <option value="F">F (Female)</option>
                <option value="D">D (Diverse)</option>
              </select>
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
        </div>
      </div>

      <!-- Pose Mapping Card -->
      <div v-if="modelData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Pose Mapping</h3>
          <p class="text-sm text-base-content/70 mb-4">Map entity poses to animation configurations</p>

          <div class="space-y-3">
            <div
              v-for="pose in poseMappingList"
              :key="pose"
              class="border border-base-300 rounded-lg p-3"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="badge badge-primary">{{ pose }}</span>
                <button
                  type="button"
                  class="btn btn-ghost btn-square btn-xs text-error"
                  @click="removePose(pose)"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-3 gap-2">
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Animation Name</span>
                  </label>
                  <input
                    v-model="modelData.poseMapping[pose].animationName"
                    type="text"
                    class="input input-bordered input-sm"
                    placeholder="e.g. idle_anim"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Speed Multiplier</span>
                  </label>
                  <input
                    v-model.number="modelData.poseMapping[pose].speedMultiplier"
                    type="number"
                    step="0.1"
                    class="input input-bordered input-sm"
                    placeholder="1.0"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Loop</span>
                  </label>
                  <input
                    v-model="modelData.poseMapping[pose].loop"
                    type="checkbox"
                    class="toggle toggle-sm toggle-success mt-1"
                  />
                </div>
              </div>
            </div>
          </div>

          <!-- Add Pose -->
          <div class="flex gap-2 mt-4">
            <select v-model="newPoseKey" class="select select-bordered select-sm flex-1">
              <option value="">-- Add Pose --</option>
              <option
                v-for="pose in availablePoses"
                :key="pose"
                :value="pose"
              >
                {{ pose }}
              </option>
            </select>
            <button
              type="button"
              class="btn btn-outline btn-sm"
              :disabled="!newPoseKey"
              @click="addPose"
            >
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add
            </button>
          </div>
        </div>
      </div>

      <!-- Model Modifier Mapping Card -->
      <div v-if="modelData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Model Modifier Mapping</h3>
          <p class="text-sm text-base-content/70 mb-2">
            Key = semantic name, Value = target descriptor: <code class="text-xs bg-base-300 px-1 rounded">category:targetName:property</code>
          </p>
          <p class="text-xs text-base-content/50 mb-4">
            Bone scale: <code class="bg-base-300 px-1 rounded">bone:Head:scale</code> &mdash;
            Color tint: <code class="bg-base-300 px-1 rounded">color:Skin:tint</code> &mdash;
            Color replace: <code class="bg-base-300 px-1 rounded">color:Main:baseColor</code> &mdash;
            Multi-target: separate with <code class="bg-base-300 px-1 rounded">;</code>
          </p>

          <!-- Preset buttons -->
          <div v-if="availableModifierPresets.length > 0" class="flex flex-wrap gap-1 mb-4">
            <button
              v-for="preset in availableModifierPresets"
              :key="preset"
              type="button"
              class="btn btn-outline btn-xs"
              @click="addModifierPreset(preset)"
            >
              + {{ preset }}
            </button>
          </div>

          <div class="space-y-2">
            <div
              v-for="(value, key) in modelData.modelModifierMapping"
              :key="key"
              class="flex items-center gap-2"
            >
              <span class="badge badge-neutral badge-sm min-w-24 justify-center">{{ key }}</span>
              <input
                v-model="modelData.modelModifierMapping[key]"
                type="text"
                class="input input-bordered input-sm flex-1"
                :placeholder="getModifierPlaceholder(key as string)"
              />
              <button
                type="button"
                class="btn btn-ghost btn-square btn-xs text-error"
                @click="removeModifierMapping(key as string)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>

          <!-- Add custom modifier -->
          <div class="flex gap-2 mt-4">
            <input
              v-model="newModifierKey"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Custom key"
              @keyup.enter="addModifierMapping"
            />
            <input
              v-model="newModifierValue"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="category:targetName:property"
              @keyup.enter="addModifierMapping"
            />
            <button
              type="button"
              class="btn btn-outline btn-sm"
              :disabled="!newModifierKey"
              @click="addModifierMapping"
            >
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add
            </button>
          </div>
        </div>
      </div>

      <!-- Dimensions Card -->
      <div v-if="modelData" class="card bg-base-100 shadow-xl">
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
        </div>
      </div>

      <!-- Audio Definitions Card -->
      <div v-if="modelData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Audio Definitions</h3>
          <p class="text-sm text-base-content/70 mb-4">Default audio for all instances of this entity model</p>

          <div class="space-y-3">
            <div
              v-for="(audio, index) in (modelData.audio || [])"
              :key="index"
              class="border border-base-300 rounded-lg p-3"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="badge badge-secondary">{{ audio.type || 'untitled' }}</span>
                <button
                  type="button"
                  class="btn btn-ghost btn-square btn-xs text-error"
                  @click="removeAudio(index)"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-2">
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Type</span>
                  </label>
                  <input
                    v-model="audio.type"
                    type="text"
                    class="input input-bordered input-sm"
                    placeholder="e.g. idle, attack, hurt"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Path</span>
                  </label>
                  <input
                    v-model="audio.path"
                    type="text"
                    class="input input-bordered input-sm"
                    placeholder="audio/entity/..."
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Volume (0-1)</span>
                  </label>
                  <input
                    v-model.number="audio.volume"
                    type="number"
                    step="0.1"
                    min="0"
                    max="1"
                    class="input input-bordered input-sm"
                    placeholder="1.0"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1">
                    <span class="label-text-alt">Max Distance</span>
                  </label>
                  <input
                    v-model.number="audio.maxDistance"
                    type="number"
                    step="1"
                    min="0"
                    class="input input-bordered input-sm"
                    placeholder="15"
                  />
                </div>
                <div class="form-control">
                  <label class="label py-1 cursor-pointer justify-start gap-2">
                    <span class="label-text-alt">Loop</span>
                    <input v-model="audio.loop" type="checkbox" class="toggle toggle-sm toggle-success" />
                  </label>
                </div>
                <div class="form-control">
                  <label class="label py-1 cursor-pointer justify-start gap-2">
                    <span class="label-text-alt">Enabled</span>
                    <input v-model="audio.enabled" type="checkbox" class="toggle toggle-sm toggle-success" />
                  </label>
                </div>
              </div>
            </div>
          </div>

          <!-- Add Audio -->
          <button
            type="button"
            class="btn btn-outline btn-sm mt-4"
            @click="addAudio"
          >
            <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            Add Audio
          </button>
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
      :model-value="modelData"
      @apply="handleJsonApply"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { entityModelService, type EntityModelData } from '../services/EntityModelService';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';

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
const showJsonEditor = ref(false);
const newPoseKey = ref('');
const newModifierKey = ref('');
const newModifierValue = ref('');

const ALL_POSES = [
  'IDLE', 'WALK', 'RUN', 'SPRINT', 'CROUCH', 'JUMP', 'SWIM', 'FLY', 'DEATH',
  'WALK_SLOW', 'CLAPPING', 'ROLL', 'ATTACK', 'OUT_OF_WATER', 'SWIMMING_FAST',
  'SWIMMING_IMPULSIVE', 'SWIMMING', 'HIT_RECEIVED', 'HIT_RECEIVED_STRONG',
  'KICK_LEFT', 'KICK_RIGHT', 'PUNCH_LEFT', 'PUNCH_RIGHT',
  'RUN_BACKWARD', 'RUN_LEFT', 'RUN_RIGHT', 'WAVE', 'FALL',
];

const poseMappingList = computed(() => {
  if (!modelData.value?.poseMapping) return [];
  return Object.keys(modelData.value.poseMapping).sort();
});

const availablePoses = computed(() => {
  const used = new Set(poseMappingList.value);
  return ALL_POSES.filter(p => !used.has(p));
});

const loadEntityModel = () => {
  if (isNew.value) {
    formData.value = {
      modelId: '',
      enabled: true,
    };
    modelData.value = {
      id: '',
      type: '',
      gender: '',
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
  // Ensure select fields have string values (null/undefined won't match <option value="">)
  if (modelData.value.gender == null) modelData.value.gender = '';
  if (modelData.value.poseType == null) modelData.value.poseType = 'Humanoid';
  if (modelData.value.type == null) modelData.value.type = '';
  // Ensure Vector3 objects exist
  if (!modelData.value.scale) modelData.value.scale = { x: 1, y: 1, z: 1 };
  if (!modelData.value.positionOffset) modelData.value.positionOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.rotationOffset) modelData.value.rotationOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.poseMapping) modelData.value.poseMapping = {};
  if (!modelData.value.modelModifierMapping) modelData.value.modelModifierMapping = {};
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

const addPose = () => {
  if (!newPoseKey.value || !modelData.value) return;
  if (!modelData.value.poseMapping) modelData.value.poseMapping = {};
  modelData.value.poseMapping[newPoseKey.value] = {
    animationName: '',
    speedMultiplier: 1.0,
    loop: true,
  };
  newPoseKey.value = '';
};

const removePose = (pose: string) => {
  if (!modelData.value?.poseMapping) return;
  delete modelData.value.poseMapping[pose];
};

// Modifier presets per poseType
const MODIFIER_PRESETS: Record<string, string[]> = {
  'Humanoid': ['headSize', 'bodySize', 'skinColor', 'hairColor', 'eyeColor', 'clothingColor'],
  '2-Legs':   ['headSize', 'bodySize', 'skinColor', 'hairColor', 'eyeColor', 'clothingColor'],
  '4-Legs':   ['headSize', 'bodySize', 'mainColor', 'secondaryColor', 'tailSize'],
  '6-Legs':   ['headSize', 'bodySize', 'mainColor', 'secondaryColor'],
  'Wings':    ['headSize', 'bodySize', 'wingSize', 'mainColor', 'eyeColor'],
  'Fish':     ['bodySize', 'bodyColor', 'stripeColor', 'tailSize'],
  'Snake':    ['headSize', 'bodySize', 'mainColor', 'patternColor'],
  'Slime':    ['bodySize', 'mainColor'],
};

const MODIFIER_PLACEHOLDERS: Record<string, string> = {
  headSize:       'bone:Head:scale',
  bodySize:       'bone:Torso:scale',
  wingSize:       'bone:Wing1.L:scale;bone:Wing1.R:scale',
  tailSize:       'bone:Tail1:scale',
  skinColor:      'color:Skin:tint',
  hairColor:      'color:Hair:baseColor',
  eyeColor:       'color:Eye:baseColor',
  clothingColor:  'color:Green:baseColor;color:LightGreen:baseColor',
  mainColor:      'color:Main:tint',
  secondaryColor: 'color:Main_Light:tint',
  bodyColor:      'color:Body:tint',
  stripeColor:    'color:Stripes:tint',
  patternColor:   'color:Main_Light:tint',
};

const availableModifierPresets = computed(() => {
  if (!modelData.value) return [];
  const poseType = modelData.value.poseType || '';
  const presets = MODIFIER_PRESETS[poseType] || [];
  const existing = new Set(Object.keys(modelData.value.modelModifierMapping || {}));
  return presets.filter(p => !existing.has(p));
});

const addModifierPreset = (key: string) => {
  if (!modelData.value) return;
  if (!modelData.value.modelModifierMapping) modelData.value.modelModifierMapping = {};
  modelData.value.modelModifierMapping[key] = '';
};

const getModifierPlaceholder = (key: string): string => {
  return MODIFIER_PLACEHOLDERS[key] || 'category:targetName:property';
};

const addModifierMapping = () => {
  if (!newModifierKey.value || !modelData.value) return;
  if (!modelData.value.modelModifierMapping) modelData.value.modelModifierMapping = {};
  modelData.value.modelModifierMapping[newModifierKey.value] = newModifierValue.value;
  newModifierKey.value = '';
  newModifierValue.value = '';
};

const removeModifierMapping = (key: string) => {
  if (!modelData.value?.modelModifierMapping) return;
  delete modelData.value.modelModifierMapping[key];
};

const addAudio = () => {
  if (!modelData.value) return;
  if (!modelData.value.audio) modelData.value.audio = [];
  modelData.value.audio.push({
    type: '',
    path: '',
    volume: 1.0,
    loop: false,
    enabled: true,
    maxDistance: 15,
  });
};

const removeAudio = (index: number) => {
  if (!modelData.value?.audio) return;
  modelData.value.audio.splice(index, 1);
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
      await entityModelService.createEntityModel(currentWorldId.value, {
        modelId: formData.value.modelId,
        publicData: modelData.value,
      });
      successMessage.value = 'Entity model created successfully';
    } else {
      await entityModelService.updateEntityModel(currentWorldId.value, formData.value.modelId, {
        enabled: formData.value.enabled,
        publicData: modelData.value,
      });
      successMessage.value = 'Entity model saved successfully';
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

const handleJsonApply = (jsonData: any) => {
  modelData.value = jsonData;
  // Ensure required objects exist after JSON edit
  if (!modelData.value.scale) modelData.value.scale = { x: 1, y: 1, z: 1 };
  if (!modelData.value.positionOffset) modelData.value.positionOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.rotationOffset) modelData.value.rotationOffset = { x: 0, y: 0, z: 0 };
  if (!modelData.value.poseMapping) modelData.value.poseMapping = {};
  if (!modelData.value.modelModifierMapping) modelData.value.modelModifierMapping = {};
  if (!modelData.value.dimensions) modelData.value.dimensions = {};
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  // Note: WorldSelector in EntityModelApp loads worlds with 'withCollections' filter
  loadEntityModel();
});
</script>
