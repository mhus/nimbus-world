<template>
  <div class="space-y-4">
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
        {{ isNew ? 'Create New Backdrop' : 'Edit Backdrop' }}
      </h2>
    </div>

    <div v-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <div class="space-y-4">
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <form @submit.prevent="handleSave" class="space-y-4">
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Backdrop ID</span>
              </label>
              <input
                v-model="formData.backdropId"
                type="text"
                placeholder="Enter unique backdrop ID"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
            </div>

            <div v-if="!isNew" class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Enabled</span>
                <input v-model="formData.enabled" type="checkbox" class="toggle toggle-success" />
              </label>
            </div>

            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">Cancel</button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Backdrop Properties Card -->
      <div v-if="!isNew && backdropData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Backdrop Properties</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Type</span>
              </label>
              <select v-model="backdropData.type" class="select select-bordered select-sm">
                <option value="none">None</option>
                <option value="texture">Texture</option>
                <option value="solid">Solid</option>
                <option value="fog">Fog</option>
                <option value="fadeout">Fadeout</option>
              </select>
            </div>

            <!-- ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Backdrop ID Reference</span>
              </label>
              <input
                v-model="backdropData.id"
                type="text"
                class="input input-bordered input-sm"
                placeholder="fog1, stone, etc."
              />
            </div>

            <!-- Texture -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Texture Path</span>
              </label>
              <input
                v-model="backdropData.texture"
                type="text"
                class="input input-bordered input-sm"
                placeholder="textures/backdrop/hills.png"
              />
            </div>

            <!-- Noise Texture -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Noise Texture</span>
              </label>
              <input
                v-model="backdropData.noiseTexture"
                type="text"
                class="input input-bordered input-sm"
                placeholder="textures/noise/perlin.png"
              />
            </div>

            <!-- Color -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Color (Hex)</span>
              </label>
              <input
                v-model="backdropData.color"
                type="text"
                class="input input-bordered input-sm"
                placeholder="#808080"
              />
            </div>

            <!-- Alpha -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Alpha (0-1)</span>
              </label>
              <input
                v-model.number="backdropData.alpha"
                type="number"
                step="0.1"
                min="0"
                max="1"
                class="input input-bordered input-sm"
                placeholder="0.5"
              />
            </div>
          </div>

          <!-- Dimensions -->
          <div class="mt-4">
            <label class="label">
              <span class="label-text font-medium">Dimensions</span>
            </label>
            <div class="grid grid-cols-2 md:grid-cols-4 gap-2">
              <div class="form-control">
                <label class="label py-1">
                  <span class="label-text-alt">Left</span>
                </label>
                <input
                  v-model.number="backdropData.left"
                  type="number"
                  min="0"
                  max="16"
                  class="input input-bordered input-sm"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-1">
                  <span class="label-text-alt">Width</span>
                </label>
                <input
                  v-model.number="backdropData.width"
                  type="number"
                  min="0"
                  max="16"
                  class="input input-bordered input-sm"
                  placeholder="16"
                />
              </div>
              <div class="form-control">
                <label class="label py-1">
                  <span class="label-text-alt">Y Base</span>
                </label>
                <input
                  v-model.number="backdropData.yBase"
                  type="number"
                  class="input input-bordered input-sm"
                  placeholder="0"
                />
              </div>
              <div class="form-control">
                <label class="label py-1">
                  <span class="label-text-alt">Height</span>
                </label>
                <input
                  v-model.number="backdropData.height"
                  type="number"
                  class="input input-bordered input-sm"
                  placeholder="60"
                />
              </div>
            </div>
          </div>

          <!-- Depth -->
          <div class="form-control mt-4">
            <label class="label">
              <span class="label-text font-medium">Depth (0=plane, >0=box)</span>
            </label>
            <input
              v-model.number="backdropData.depth"
              type="number"
              min="0"
              class="input input-bordered input-sm"
              placeholder="0"
            />
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

      <!-- Advanced JSON Editor (Collapsible) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between">
            <h3 class="card-title">Advanced (JSON)</h3>
            <button
              type="button"
              class="btn btn-sm btn-ghost"
              @click="showJsonEditor = !showJsonEditor"
            >
              {{ showJsonEditor ? 'Hide' : 'Show' }}
            </button>
          </div>
          <div v-if="showJsonEditor" class="mt-4">
            <textarea
              v-model="publicDataJson"
              class="textarea textarea-bordered font-mono text-sm w-full"
              rows="15"
              placeholder="Full backdrop JSON"
            ></textarea>
            <div class="card-actions justify-end mt-4">
              <button
                type="button"
                class="btn btn-sm btn-warning"
                @click="handleSaveJson"
                :disabled="saving"
              >
                <span v-if="saving" class="loading loading-spinner loading-xs"></span>
                <span v-else>Save from JSON (Advanced)</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

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
import { backdropService, type BackdropData } from '../services/BackdropService';

const props = defineProps<{ backdrop: BackdropData | 'new' }>();
const emit = defineEmits<{ back: []; saved: [] }>();
const { currentWorldId } = useWorld();
const isNew = computed(() => props.backdrop === 'new');

const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);
const formData = ref({ backdropId: '', enabled: true });
const backdropData = ref<any>(null);
const publicDataJson = ref('{}');
const showJsonEditor = ref(false);

const loadBackdrop = async () => {
  if (isNew.value) {
    formData.value = { backdropId: '', enabled: true };
    backdropData.value = {
      type: 'fog',
      id: '',
      left: 0,
      width: 16,
      yBase: 0,
      height: 60,
      depth: 0,
      texture: '',
      noiseTexture: '',
      color: '#808080',
      alpha: 0.5,
    };
    publicDataJson.value = JSON.stringify(backdropData.value, null, 2);
    return;
  }

  // Load fresh data from server
  const backdrop = props.backdrop as BackdropData;
  if (!currentWorldId.value) {
    error.value = 'No world selected';
    return;
  }

  try {
    const freshData = await backdropService.getBackdrop(currentWorldId.value, backdrop.backdropId);
    formData.value = { backdropId: backdrop.backdropId, enabled: backdrop.enabled };
    backdropData.value = freshData || {};
    publicDataJson.value = JSON.stringify(freshData, null, 2);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load backdrop';
    console.error('Failed to load backdrop:', e);
  }
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
      const publicData = JSON.parse(publicDataJson.value);
      await backdropService.createBackdrop(currentWorldId.value, {
        backdropId: formData.value.backdropId,
        publicData,
      });
      successMessage.value = 'Backdrop created successfully';
    } else {
      await backdropService.updateBackdrop(currentWorldId.value, formData.value.backdropId, {
        enabled: formData.value.enabled,
      });
      successMessage.value = 'Backdrop updated successfully';
    }

    setTimeout(() => emit('saved'), 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save backdrop';
  } finally {
    saving.value = false;
  }
};

const handleSavePublicData = async () => {
  if (!currentWorldId.value) return;

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    // Update publicData from form fields
    await backdropService.updateBackdrop(currentWorldId.value, formData.value.backdropId, {
      publicData: backdropData.value,
    });
    // Sync JSON view
    publicDataJson.value = JSON.stringify(backdropData.value, null, 2);
    successMessage.value = 'Backdrop properties updated successfully';
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save properties';
  } finally {
    saving.value = false;
  }
};

const handleSaveJson = async () => {
  if (!currentWorldId.value) return;

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    const publicData = JSON.parse(publicDataJson.value);
    await backdropService.updateBackdrop(currentWorldId.value, formData.value.backdropId, {
      publicData,
    });
    // Sync form fields
    backdropData.value = publicData;
    successMessage.value = 'Backdrop data updated from JSON successfully';
  } catch (e) {
    error.value = e instanceof SyntaxError ? 'Invalid JSON format' : (e instanceof Error ? e.message : 'Failed to save');
  } finally {
    saving.value = false;
  }
};

const handleBack = () => emit('back');

onMounted(() => {
  // Note: WorldSelector in BackdropApp loads worlds with 'withCollections' filter
  loadBackdrop();
});
</script>
