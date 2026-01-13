<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-2xl">
      <h3 class="font-bold text-lg mb-4">Create New Flat</h3>

      <!-- Job Type Selection -->
      <div class="form-control mb-4">
        <label class="label">
          <span class="label-text font-semibold">Creation Method</span>
        </label>
        <select v-model="jobType" class="select select-bordered">
          <option value="flat-import">Import from Layer</option>
          <option value="flat-create">Create Empty with BEDROCK</option>
          <option value="flat-create-hexgrid">Create HexGrid (BEDROCK inside)</option>
          <option value="flat-import-hexgrid">Import HexGrid (from Layer)</option>
        </select>
        <label class="label">
          <span class="label-text-alt text-base-content/70">{{ getJobTypeDescription(jobType) }}</span>
        </label>
      </div>

      <!-- Common Parameters -->
      <div class="space-y-3">
        <div class="form-control">
          <label class="label">
            <span class="label-text">Title</span>
          </label>
          <input
            v-model="title"
            type="text"
            class="input input-bordered"
            placeholder="e.g., Main Island"
          />
        </div>

        <div class="form-control">
          <label class="label">
            <span class="label-text">Description</span>
          </label>
          <textarea
            v-model="description"
            class="textarea textarea-bordered"
            rows="2"
            placeholder="Optional description for this flat"
          ></textarea>
        </div>

        <div class="form-control">
          <label class="label">
            <span class="label-text">Layer Name *</span>
          </label>
          <select
            v-model="layerName"
            class="select select-bordered"
            required
          >
            <option value="" disabled>Select a layer...</option>
            <option
              v-for="layer in groundLayers"
              :key="layer.id"
              :value="layer.name"
            >
              {{ layer.name }}
            </option>
          </select>
          <label v-if="loadingLayers" class="label">
            <span class="label-text-alt">Loading layers...</span>
          </label>
          <label v-else-if="groundLayers.length === 0" class="label">
            <span class="label-text-alt text-warning">No GROUND layers found</span>
          </label>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size X (50-800) *</span>
            </label>
            <input
              v-model.number="sizeX"
              type="number"
              class="input input-bordered"
              min="50"
              max="800"
              required
            />
          </div>

          <div class="form-control">
            <label class="label">
              <span class="label-text">Size Z (50-800) *</span>
            </label>
            <input
              v-model.number="sizeZ"
              type="number"
              class="input input-bordered"
              min="50"
              max="800"
              required
            />
          </div>
        </div>

        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Mount X *</span>
            </label>
            <input
              v-model.number="mountX"
              type="number"
              class="input input-bordered"
              required
            />
          </div>

          <div class="form-control">
            <label class="label">
              <span class="label-text">Mount Z *</span>
            </label>
            <input
              v-model.number="mountZ"
              type="number"
              class="input input-bordered"
              required
            />
          </div>
        </div>

        <!-- HexGrid Parameters (only for hexgrid types) -->
        <div v-if="isHexGridType" class="space-y-3 pt-3 border-t">
          <div class="text-sm font-semibold">HexGrid Coordinates</div>
          <div class="grid grid-cols-2 gap-3">
            <div class="form-control">
              <label class="label">
                <span class="label-text">Hex Q *</span>
              </label>
              <input
                v-model.number="hexQ"
                type="number"
                class="input input-bordered"
                required
              />
            </div>

            <div class="form-control">
              <label class="label">
                <span class="label-text">Hex R *</span>
              </label>
              <input
                v-model.number="hexR"
                type="number"
                class="input input-bordered"
                required
              />
            </div>
          </div>
        </div>

        <!-- Optional Parameters -->
        <div class="collapse collapse-arrow bg-base-200">
          <input type="checkbox" />
          <div class="collapse-title font-medium">
            Optional Parameters
          </div>
          <div class="collapse-content space-y-3">
            <div class="form-control">
              <label class="label">
                <span class="label-text">Palette Name</span>
              </label>
              <select v-model="paletteName" class="select select-bordered">
                <option value="">None</option>
                <option value="nimbus">Nimbus</option>
                <option value="legacy">Legacy</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <!-- Error Display -->
      <div v-if="error" class="alert alert-error mt-4">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ error }}</span>
      </div>

      <!-- Actions -->
      <div class="modal-action">
        <button
          class="btn"
          @click="$emit('close')"
          :disabled="creating"
        >
          Cancel
        </button>
        <button
          class="btn btn-primary"
          @click="handleCreate"
          :disabled="creating || !isValid"
        >
          <span v-if="creating" class="loading loading-spinner loading-sm"></span>
          {{ creating ? 'Creating...' : 'Create Flat' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useJobs, type JobCreateRequest } from '@/composables/useJobs';
import { useLayers } from '@/composables/useLayers';

const props = defineProps<{
  worldId: string;
}>();

const emit = defineEmits<{
  close: [];
  jobCreated: [jobId: string];
}>();

const { createJob } = useJobs(props.worldId);
const { layers: availableLayers, loading: loadingLayers, loadLayers } = useLayers(props.worldId);

// Form state
const jobType = ref<string>('flat-import');
const title = ref('');
const description = ref('');
const layerName = ref('');
const sizeX = ref<number>(50);
const sizeZ = ref<number>(50);
const mountX = ref<number>(0);
const mountZ = ref<number>(0);
const hexQ = ref<number>(0);
const hexR = ref<number>(0);
const paletteName = ref('');

const creating = ref(false);
const error = ref<string | null>(null);

// Load layers on mount
onMounted(async () => {
  await loadLayers();
});

// Filter only GROUND layers for flat creation
const groundLayers = computed(() => {
  return availableLayers.value.filter(layer => layer.layerType === 'GROUND');
});

const isHexGridType = computed(() => {
  return jobType.value === 'flat-create-hexgrid' || jobType.value === 'flat-import-hexgrid';
});

const isValid = computed(() => {
  if (!layerName.value) return false;
  if (!sizeX.value || sizeX.value < 50 || sizeX.value > 800) return false;
  if (!sizeZ.value || sizeZ.value < 50 || sizeZ.value > 800) return false;
  if (mountX.value === undefined || mountZ.value === undefined) return false;
  if (isHexGridType.value && (hexQ.value === undefined || hexR.value === undefined)) return false;
  return true;
});

const getJobTypeDescription = (type: string): string => {
  switch (type) {
    case 'flat-import':
      return 'Import complete layer data into a new flat';
    case 'flat-create':
      return 'Create empty flat filled with BEDROCK, import border from layer';
    case 'flat-create-hexgrid':
      return 'Create flat with BEDROCK inside HexGrid area, import outside from layer';
    case 'flat-import-hexgrid':
      return 'Import layer data, protect areas outside HexGrid';
    default:
      return '';
  }
};

const handleCreate = async () => {
  error.value = null;
  creating.value = true;

  try {
    // Build parameters
    const parameters: Record<string, string> = {
      layerName: layerName.value,
      sizeX: sizeX.value.toString(),
      sizeZ: sizeZ.value.toString(),
      mountX: mountX.value.toString(),
      mountZ: mountZ.value.toString(),
    };

    // Add optional title
    if (title.value) {
      parameters.title = title.value;
    }

    // Add optional description
    if (description.value) {
      parameters.description = description.value;
    }

    // Add hexgrid coordinates if needed
    if (isHexGridType.value) {
      parameters.hexQ = hexQ.value.toString();
      parameters.hexR = hexR.value.toString();
    }

    // Add optional palette
    if (paletteName.value) {
      parameters.paletteName = paletteName.value;
    }

    // Create job request
    const jobRequest: JobCreateRequest = {
      executor: jobType.value,
      parameters: parameters,
      priority: 5,
      maxRetries: 3,
    };

    // Create job via API
    const createdJob = await createJob(jobRequest);

    // Emit the created job ID for monitoring
    emit('jobCreated', createdJob.id);
    emit('close');
  } catch (err: any) {
    console.error('[FlatCreateDialog] Failed to create job:', err);
    error.value = err.message || 'Failed to create flat job';
  } finally {
    creating.value = false;
  }
};
</script>
