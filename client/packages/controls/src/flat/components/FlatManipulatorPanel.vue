<template>
  <div class="p-4 bg-base-200 rounded-lg">
    <h3 class="text-lg font-semibold mb-4">Apply Manipulator</h3>

    <!-- Manipulator Selection -->
    <div class="form-control mb-4">
      <label class="label">
        <span class="label-text font-semibold">Select Manipulator</span>
      </label>
      <select
        v-model="selectedManipulator"
        class="select select-bordered"
        @change="resetParameters"
      >
        <option value="">Choose a manipulator...</option>
        <optgroup label="Terrain Generation">
          <option value="flat">Flat - Simple flat terrain</option>
          <option value="normal">Normal - Terrain with medium variation</option>
          <option value="hilly">Hilly - Terrain with hills</option>
        </optgroup>
        <optgroup label="Smoothing">
          <option value="soften">Soften - Smooth terrain by averaging</option>
          <option value="border-smooth">Border Smooth - Smooth edges to match borders</option>
          <option value="water-soften">Water Soften - Special smoothing for water areas</option>
          <option value="soften-raster">Soften Raster - Raster-based smoothing</option>
        </optgroup>
        <optgroup label="Effects">
          <option value="sharpen">Sharpen - Increase contrast</option>
          <option value="roughen">Roughen - Add random variation</option>
        </optgroup>
        <optgroup label="Landforms">
          <option value="mountain">Mountain - Fractal mountain ranges</option>
          <option value="sharp-peak">Sharp Peak - Conical mountains</option>
          <option value="islands">Islands - Archipelago generation</option>
          <option value="lakes">Lakes - Lake systems</option>
          <option value="crater">Crater - Crater with raised rim</option>
          <option value="spider">Spider - Branching patterns (rivers/canyons)</option>
          <option value="shaked-box">Shaked Box - Rectangles with irregular edges</option>
        </optgroup>
        <optgroup label="Special">
          <option value="random-pixels">Random Pixels - Random pixel placement</option>
          <option value="composition">Composition - Combine multiple manipulators</option>
        </optgroup>
      </select>
      <label v-if="selectedManipulator && manipulatorDescriptions[selectedManipulator]" class="label">
        <span class="label-text-alt text-base-content/70">{{ manipulatorDescriptions[selectedManipulator] }}</span>
      </label>
    </div>

    <!-- Parameter Forms -->
    <div v-if="selectedManipulator" class="space-y-4 mb-4">
      <!-- Flat Terrain -->
      <div v-if="selectedManipulator === 'flat'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Ground Level (0-255)</span></label>
          <input v-model.number="params.groundLevel" type="number" class="input input-bordered" min="0" max="255" />
        </div>
      </div>

      <!-- Normal Terrain -->
      <div v-if="selectedManipulator === 'normal'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Base Height (0-255)</span></label>
          <input v-model.number="params.baseHeight" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Height Variation (0-128)</span></label>
          <input v-model.number="params.heightVariation" type="number" class="input input-bordered" min="0" max="128" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Seed (optional)</span></label>
          <input v-model="params.seed" type="text" class="input input-bordered" placeholder="Leave empty for random" />
        </div>
      </div>

      <!-- Hilly Terrain -->
      <div v-if="selectedManipulator === 'hilly'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Base Height (0-255)</span></label>
          <input v-model.number="params.baseHeight" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Hill Height (0-128)</span></label>
          <input v-model.number="params.hillHeight" type="number" class="input input-bordered" min="0" max="128" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Seed (optional)</span></label>
          <input v-model="params.seed" type="text" class="input input-bordered" placeholder="Leave empty for random" />
        </div>
      </div>

      <!-- Soften -->
      <div v-if="selectedManipulator === 'soften'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Factor (0.0-1.0)</span></label>
          <input v-model.number="params.factor" type="number" class="input input-bordered" min="0" max="1" step="0.1" />
        </div>
      </div>

      <!-- Border Smooth -->
      <div v-if="selectedManipulator === 'border-smooth'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Depth (pixels from edge)</span></label>
          <input v-model.number="params.depth" type="number" class="input input-bordered" min="1" max="50" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Strength (0.0-1.0)</span></label>
          <input v-model.number="params.strength" type="number" class="input input-bordered" min="0" max="1" step="0.1" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Corner Depth</span></label>
          <input v-model.number="params.cornerDepth" type="number" class="input input-bordered" min="0" max="10" />
        </div>
      </div>

      <!-- Water Soften -->
      <div v-if="selectedManipulator === 'water-soften'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Passes</span></label>
          <input v-model.number="params.passes" type="number" class="input input-bordered" min="1" max="20" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Water Threshold</span></label>
          <input v-model.number="params.waterThreshold" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Probability (0.0-1.0)</span></label>
          <input v-model.number="params.probability" type="number" class="input input-bordered" min="0" max="1" step="0.1" />
        </div>
      </div>

      <!-- Sharpen -->
      <div v-if="selectedManipulator === 'sharpen'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Factor</span></label>
          <input v-model.number="params.factor" type="number" class="input input-bordered" min="0" max="5" step="0.1" />
        </div>
      </div>

      <!-- Roughen -->
      <div v-if="selectedManipulator === 'roughen'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Level (1-50)</span></label>
          <input v-model.number="params.level" type="number" class="input input-bordered" min="1" max="50" />
        </div>
      </div>

      <!-- Mountain -->
      <div v-if="selectedManipulator === 'mountain'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Peak Height (0-255)</span></label>
          <input v-model.number="params.peakHeight" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Base Height (0-255)</span></label>
          <input v-model.number="params.baseHeight" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Branches (2-8)</span></label>
          <input v-model.number="params.branches" type="number" class="input input-bordered" min="2" max="8" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Roughness (0.0-2.0)</span></label>
          <input v-model.number="params.roughness" type="number" class="input input-bordered" min="0" max="2" step="0.1" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Direction</span></label>
          <select v-model="params.direction" class="select select-bordered">
            <option value="center">Center</option>
            <option value="horizontal">Horizontal</option>
            <option value="vertical">Vertical</option>
          </select>
        </div>
      </div>

      <!-- Sharp Peak -->
      <div v-if="selectedManipulator === 'sharp-peak'" class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label"><span class="label-text">Center X</span></label>
            <input v-model.number="params.centerX" type="number" class="input input-bordered" min="0" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Center Z</span></label>
            <input v-model.number="params.centerZ" type="number" class="input input-bordered" min="0" />
          </div>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Radius</span></label>
          <input v-model.number="params.radius" type="number" class="input input-bordered" min="10" max="200" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Height (0-255)</span></label>
          <input v-model.number="params.height" type="number" class="input input-bordered" min="0" max="255" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Steepness (0.5-5.0)</span></label>
          <input v-model.number="params.steepness" type="number" class="input input-bordered" min="0.5" max="5" step="0.1" />
        </div>
      </div>

      <!-- Islands -->
      <div v-if="selectedManipulator === 'islands'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Main Island Size</span></label>
          <input v-model.number="params.mainIslandSize" type="number" class="input input-bordered" min="20" max="200" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Main Island Height</span></label>
          <input v-model.number="params.mainIslandHeight" type="number" class="input input-bordered" min="10" max="100" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Small Islands Count</span></label>
          <input v-model.number="params.smallIslands" type="number" class="input input-bordered" min="0" max="20" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Scatter Distance</span></label>
          <input v-model.number="params.scatterDistance" type="number" class="input input-bordered" min="50" max="300" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Underwater (0-255)</span></label>
          <input v-model.number="params.underwater" type="number" class="input input-bordered" min="0" max="255" />
        </div>
      </div>

      <!-- Crater -->
      <div v-if="selectedManipulator === 'crater'" class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label"><span class="label-text">Center X</span></label>
            <input v-model.number="params.centerX" type="number" class="input input-bordered" min="0" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Center Z</span></label>
            <input v-model.number="params.centerZ" type="number" class="input input-bordered" min="0" />
          </div>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Outer Radius</span></label>
          <input v-model.number="params.outerRadius" type="number" class="input input-bordered" min="10" max="200" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Inner Radius</span></label>
          <input v-model.number="params.innerRadius" type="number" class="input input-bordered" min="5" max="150" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Rim Height</span></label>
          <input v-model.number="params.rimHeight" type="number" class="input input-bordered" min="0" max="50" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Depth</span></label>
          <input v-model.number="params.depth" type="number" class="input input-bordered" min="0" max="100" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Small Craters</span></label>
          <input v-model.number="params.smallCraters" type="number" class="input input-bordered" min="0" max="10" />
        </div>
      </div>

      <!-- Generic fallback for other manipulators -->
      <div v-if="!hasCustomForm" class="form-control">
        <label class="label">
          <span class="label-text">Parameters (JSON format, optional)</span>
        </label>
        <textarea
          v-model="genericParams"
          class="textarea textarea-bordered font-mono text-sm"
          rows="4"
          placeholder='Example: {"height":"10","strength":"0.5"}'
        ></textarea>
        <label class="label">
          <span class="label-text-alt text-base-content/70">
            Leave empty to use default parameters
          </span>
        </label>
      </div>
    </div>

    <!-- Execute Button -->
    <button
      v-if="selectedManipulator"
      class="btn btn-primary"
      @click="executeManipulator"
      :disabled="executing"
    >
      <span v-if="executing" class="loading loading-spinner loading-sm"></span>
      {{ executing ? 'Starting Job...' : 'Apply Manipulator' }}
    </button>

    <!-- Error Display -->
    <div v-if="error" class="alert alert-error mt-4">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>
  </div>

  <!-- Job Watch Modal -->
  <JobWatch
    v-if="watchingJob"
    :worldId="worldId"
    :jobId="currentJobId"
    @close="handleJobWatchClose"
    @completed="handleJobCompleted"
    @failed="handleJobFailed"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useJobs, type JobCreateRequest, type Job } from '@/composables/useJobs';
import JobWatch from './JobWatch.vue';

const props = defineProps<{
  flatId: string;
  worldId: string;
}>();

const emit = defineEmits<{
  manipulatorCompleted: [];
}>();

const { createJob } = useJobs(props.worldId);

// State
const selectedManipulator = ref('');
const params = ref<Record<string, any>>({});
const genericParams = ref('');
const executing = ref(false);
const error = ref<string | null>(null);
const watchingJob = ref(false);
const currentJobId = ref('');

// Manipulator descriptions
const manipulatorDescriptions: Record<string, string> = {
  'flat': 'Creates simple flat terrain at specified height',
  'normal': 'Generates terrain with medium variation using multi-octave noise',
  'hilly': 'Creates hilly terrain with high variation',
  'soften': 'Smooths terrain by averaging neighbor heights',
  'border-smooth': 'Smooths edges to match border heights',
  'water-soften': 'Special smoothing for water areas and coastlines',
  'soften-raster': 'Performance-optimized smoothing with raster pattern',
  'sharpen': 'Increases terrain contrast by accentuating height differences',
  'roughen': 'Adds random height variation for natural look',
  'mountain': 'Creates fractal mountain ranges with recursive branching',
  'sharp-peak': 'Generates conical mountains with exponential falloff',
  'islands': 'Creates archipelago with main and small islands',
  'lakes': 'Generates lake systems with quadratic depression',
  'crater': 'Creates craters with raised rim',
  'spider': 'Generates branching patterns for rivers or canyons',
  'shaked-box': 'Creates rectangles with natural irregular edges',
  'random-pixels': 'Places random pixels for texture',
  'composition': 'Combines multiple manipulators sequentially',
};

// Check if selected manipulator has custom form
const hasCustomForm = computed(() => {
  const customForms = [
    'flat', 'normal', 'hilly', 'soften', 'border-smooth', 'water-soften',
    'sharpen', 'roughen', 'mountain', 'sharp-peak', 'islands', 'crater'
  ];
  return customForms.includes(selectedManipulator.value);
});

/**
 * Reset parameters when manipulator changes
 */
const resetParameters = () => {
  params.value = {};
  genericParams.value = '';
  error.value = null;
};

/**
 * Execute selected manipulator
 */
const executeManipulator = async () => {
  if (!selectedManipulator.value) return;

  executing.value = true;
  error.value = null;

  try {
    // Build job parameters
    const jobParams: Record<string, string> = {
      flatId: props.flatId,
    };

    // Convert params to parameters JSON if we have custom form
    if (hasCustomForm.value && Object.keys(params.value).length > 0) {
      // Filter out empty/undefined values
      const filteredParams: Record<string, string> = {};
      for (const [key, value] of Object.entries(params.value)) {
        if (value !== null && value !== undefined && value !== '') {
          filteredParams[key] = String(value);
        }
      }
      if (Object.keys(filteredParams).length > 0) {
        jobParams.parameters = JSON.stringify(filteredParams);
      }
    } else if (genericParams.value.trim()) {
      // Use generic params for manipulators without custom forms
      try {
        JSON.parse(genericParams.value);
        jobParams.parameters = genericParams.value.trim();
      } catch (jsonError) {
        error.value = 'Invalid JSON in parameters field';
        executing.value = false;
        return;
      }
    }

    // Create job
    const jobRequest: JobCreateRequest = {
      executor: 'flat-manipulate',
      type: selectedManipulator.value,
      parameters: jobParams,
      priority: 5,
      maxRetries: 3,
    };

    console.log('[FlatManipulatorPanel] Creating job:', jobRequest);
    const createdJob = await createJob(jobRequest);

    console.log('[FlatManipulatorPanel] Job created:', createdJob.id);

    // Show job watch
    currentJobId.value = createdJob.id;
    watchingJob.value = true;
  } catch (e: any) {
    console.error('[FlatManipulatorPanel] Failed to create job:', e);
    error.value = e.message || 'Failed to start manipulator job';
  } finally {
    executing.value = false;
  }
};

/**
 * Handle job watch close
 */
const handleJobWatchClose = () => {
  watchingJob.value = false;
};

/**
 * Handle job completed
 */
const handleJobCompleted = async (job: Job) => {
  console.log('[FlatManipulatorPanel] Job completed:', job.id);
  watchingJob.value = false;

  // Reset state
  selectedManipulator.value = '';
  params.value = {};
  genericParams.value = '';

  // Emit event so parent can reload flat
  emit('manipulatorCompleted');
};

/**
 * Handle job failed
 */
const handleJobFailed = (job: Job) => {
  console.error('[FlatManipulatorPanel] Job failed:', job.id, job.errorMessage);
  watchingJob.value = false;
  error.value = `Manipulator job failed: ${job.errorMessage || 'Unknown error'}`;
};
</script>
