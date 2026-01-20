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
          <option value="hex-grid">Hex Grid - Generate terrain based on hex grid configuration</option>
          <option value="hex-grid-expand">Hex Grid Expand - Expand editable hex grid area</option>
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
        <div class="form-control">
          <label class="label"><span class="label-text">Radius (1-5)</span></label>
          <input v-model.number="params.radius" type="number" class="input input-bordered" min="1" max="5" />
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

      <!-- Spider Pattern -->
      <div v-if="selectedManipulator === 'spider'" class="space-y-3">
        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label"><span class="label-text">Center X (optional)</span></label>
            <input v-model.number="params.centerX" type="number" class="input input-bordered" min="0" placeholder="Auto-center" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Center Z (optional)</span></label>
            <input v-model.number="params.centerZ" type="number" class="input input-bordered" min="0" placeholder="Auto-center" />
          </div>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Branches (3-12)</span></label>
          <input v-model.number="params.branches" type="number" class="input input-bordered" min="3" max="12" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Length (10-200)</span></label>
          <input v-model.number="params.length" type="number" class="input input-bordered" min="10" max="200" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Height Delta (-50 to 50)</span></label>
          <input v-model.number="params.heightDelta" type="number" class="input input-bordered" min="-50" max="50" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Negative = carve (rivers), Positive = raise (ridges)</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Sub-Branches (0-5)</span></label>
          <input v-model.number="params.subBranches" type="number" class="input input-bordered" min="0" max="5" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Recursion Depth (1-4)</span></label>
          <input v-model.number="params.recursionDepth" type="number" class="input input-bordered" min="1" max="4" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Seed (optional)</span></label>
          <input v-model="params.seed" type="text" class="input input-bordered" placeholder="Leave empty for random" />
        </div>
      </div>

      <!-- Shaked Box -->
      <div v-if="selectedManipulator === 'shaked-box'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Border Width (1-10, -1 for entire area)</span></label>
          <input v-model.number="params.borderWidth" type="number" class="input input-bordered" min="-1" max="10" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Width of border area where pixels are removed</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Probability (0.0-1.0)</span></label>
          <input v-model.number="params.probability" type="number" class="input input-bordered" min="0" max="1" step="0.1" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Probability that a border pixel is removed</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Target Height (0-255)</span></label>
          <input v-model.number="params.targetHeight" type="number" class="input input-bordered" min="0" max="255" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Height for non-removed pixels</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Seed (optional)</span></label>
          <input v-model="params.seed" type="text" class="input input-bordered" placeholder="Leave empty for random" />
        </div>
      </div>

      <!-- Lakes -->
      <div v-if="selectedManipulator === 'lakes'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Main Lake Radius (10-200)</span></label>
          <input v-model.number="params.mainLakeRadius" type="number" class="input input-bordered" min="10" max="200" />
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Main Lake Depth (5-50)</span></label>
          <input v-model.number="params.mainLakeDepth" type="number" class="input input-bordered" min="5" max="50" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Depth below ocean level</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Small Lakes Count (2-15)</span></label>
          <input v-model.number="params.smallLakes" type="number" class="input input-bordered" min="2" max="15" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label"><span class="label-text">Small Min Radius (5-50)</span></label>
            <input v-model.number="params.smallLakeMinRadius" type="number" class="input input-bordered" min="5" max="50" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Small Max Radius (5-100)</span></label>
            <input v-model.number="params.smallLakeMaxRadius" type="number" class="input input-bordered" min="5" max="100" />
          </div>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Scatter Distance (10-300)</span></label>
          <input v-model.number="params.scatterDistance" type="number" class="input input-bordered" min="10" max="300" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">How far small lakes scatter from main lake</span>
          </label>
        </div>
        <div class="form-control">
          <label class="label"><span class="label-text">Seed (optional)</span></label>
          <input v-model="params.seed" type="text" class="input input-bordered" placeholder="Leave empty for random" />
        </div>
      </div>

      <!-- Set Material -->
      <div v-if="selectedManipulator === 'set-material'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Material ID (required)</span></label>
          <input v-model.number="params.material" type="number" class="input input-bordered" min="0" max="255" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Material to set for positions in level range</span>
          </label>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="form-control">
            <label class="label"><span class="label-text">From Level (required)</span></label>
            <input v-model.number="params.fromLevel" type="number" class="input input-bordered" min="0" max="255" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">To Level (required)</span></label>
            <input v-model.number="params.toLevel" type="number" class="input input-bordered" min="0" max="255" />
          </div>
        </div>
        <label class="label">
          <span class="label-text-alt text-base-content/70">Sets material for all positions with level between fromLevel and toLevel (inclusive)</span>
        </label>
      </div>

      <!-- Hex Grid -->
      <div v-if="selectedManipulator === 'hex-grid'" class="space-y-3">
        <div class="alert alert-info">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" class="stroke-current shrink-0 w-6 h-6"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
          <div>
            <div class="font-bold">Hex Grid Configuration Required</div>
            <div class="text-sm mt-1">This manipulator generates terrain based on the hex grid configuration assigned to this flat.</div>
            <div class="text-sm mt-2">
              <strong>Required:</strong> The hex grid must have the parameter <code class="bg-base-300 px-1 rounded">g.type</code> set to a scenario type (ocean, island, coast, heath, hills, mountains, plains, dessert, forest, swamp, city, village).
            </div>
            <div class="text-sm mt-2">
              <strong>Optional:</strong> Additional parameters with prefix <code class="bg-base-300 px-1 rounded">gf.*</code> can be configured in the hex grid for scenario-specific options.
            </div>
          </div>
        </div>
        <div class="alert alert-warning">
          <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" /></svg>
          <div>
            <div class="font-bold">No Parameters Needed</div>
            <div class="text-sm">All configuration is taken from the hex grid. Configure the hex grid in the hex editor, then run this manipulator.</div>
          </div>
        </div>
      </div>

      <!-- Hex Grid Expand -->
      <div v-if="selectedManipulator === 'hex-grid-expand'" class="space-y-3">
        <div class="form-control">
          <label class="label"><span class="label-text">Expand By (pixels)</span></label>
          <input v-model.number="params.expandBy" type="number" class="input input-bordered" min="0" max="50" placeholder="5" />
          <label class="label">
            <span class="label-text-alt text-base-content/70">Number of pixels to expand the editable hex grid area (default: 5)</span>
          </label>
        </div>
        <div class="alert alert-info">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" class="stroke-current shrink-0 w-6 h-6"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
          <div>
            <div class="font-bold">Expands Editable Area</div>
            <div class="text-sm mt-1">This manipulator expands the editable area of the hex grid by marking additional positions around the current hex as editable (material 255).</div>
            <div class="text-sm mt-2">Only positions that are currently protected (material 0) will be changed to editable (material 255).</div>
            <div class="text-sm mt-2">Note: This does not change the world's hex grid size, only the editable area in this flat.</div>
          </div>
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
  'soften': 'Smooths terrain by averaging neighbor heights within specified radius',
  'border-smooth': 'Smooths edges to match border heights',
  'water-soften': 'Special smoothing for water areas and coastlines',
  'soften-raster': 'Performance-optimized smoothing with raster pattern',
  'sharpen': 'Increases terrain contrast by accentuating height differences',
  'roughen': 'Adds random height variation for natural look',
  'mountain': 'Creates fractal mountain ranges with recursive branching',
  'sharp-peak': 'Generates conical mountains with exponential falloff',
  'islands': 'Creates archipelago with main and small islands',
  'lakes': 'Creates a main lake with multiple smaller lakes using quadratic depth falloff',
  'crater': 'Creates craters with raised rim',
  'spider': 'Creates recursive branching patterns radiating from center (rivers, canyons, or ridges)',
  'shaked-box': 'Creates rectangles with randomly removed pixels at borders for natural irregular edges',
  'set-material': 'Sets material for all positions within a specified level range',
  'hex-grid': 'Generates terrain based on hex grid configuration and scenario type (ocean, island, plains, etc.) with neighbor-aware transitions',
  'hex-grid-expand': 'Expands the editable hex grid area by marking additional positions around the current hex as editable',
  'random-pixels': 'Places random pixels for texture',
  'composition': 'Combines multiple manipulators sequentially',
};

// Check if selected manipulator has custom form
const hasCustomForm = computed(() => {
  const customForms = [
    'flat', 'normal', 'hilly', 'soften', 'border-smooth', 'water-soften',
    'sharpen', 'roughen', 'mountain', 'sharp-peak', 'islands', 'crater', 'spider', 'shaked-box', 'lakes', 'set-material', 'hex-grid', 'hex-grid-expand'
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
