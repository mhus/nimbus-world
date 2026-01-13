<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-5xl">
      <!-- Header -->
      <div class="flex justify-between items-center mb-4">
        <div class="flex-1">
          <h2 class="text-2xl font-bold">{{ flat?.flatId || 'Loading...' }}</h2>
          <p v-if="flat?.title" class="text-sm text-base-content/70">{{ flat.title }}</p>
        </div>
        <button class="btn btn-sm btn-circle btn-ghost" @click="$emit('close')">✕</button>
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-12">
        <span class="loading loading-spinner loading-lg"></span>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="alert alert-error">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
        <span>{{ error }}</span>
      </div>

      <!-- Flat Details -->
      <template v-else-if="flat">
        <!-- Tab Navigation -->
        <div class="tabs tabs-boxed mb-6">
          <button
            class="tab"
            :class="{ 'tab-active': activeTab === 'details' }"
            @click="activeTab = 'details'"
          >
            Details
          </button>
          <button
            class="tab"
            :class="{ 'tab-active': activeTab === 'visualizations' }"
            @click="activeTab = 'visualizations'"
          >
            Visualizations
          </button>
          <button
            class="tab"
            :class="{ 'tab-active': activeTab === 'materials' }"
            @click="activeTab = 'materials'"
          >
            Materials
          </button>
        </div>

        <!-- Details Tab Content -->
        <div v-show="activeTab === 'details'" class="space-y-6">
        <!-- Title and Description -->
        <div class="p-4 bg-base-200 rounded-lg">
          <div class="flex justify-between items-center mb-3">
            <h3 class="text-lg font-semibold">Details</h3>
            <button
              v-if="!editingMetadata"
              class="btn btn-sm btn-ghost"
              @click="startEditMetadata"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
              Edit
            </button>
          </div>

          <div v-if="!editingMetadata" class="space-y-2">
            <div>
              <span class="font-semibold">Title:</span>
              <span class="ml-2">{{ flat.title || '(none)' }}</span>
            </div>
            <div>
              <span class="font-semibold">Description:</span>
              <p class="mt-1 text-sm text-base-content/70">{{ flat.description || '(none)' }}</p>
            </div>
          </div>

          <div v-else class="space-y-3">
            <div class="form-control">
              <label class="label">
                <span class="label-text">Title</span>
              </label>
              <input
                v-model="editTitle"
                type="text"
                class="input input-bordered"
                placeholder="Enter title"
              />
            </div>
            <div class="form-control">
              <label class="label">
                <span class="label-text">Description</span>
              </label>
              <textarea
                v-model="editDescription"
                class="textarea textarea-bordered"
                rows="3"
                placeholder="Enter description"
              ></textarea>
            </div>
            <div class="flex gap-2">
              <button
                class="btn btn-primary btn-sm"
                @click="saveMetadata"
                :disabled="savingMetadata"
              >
                <span v-if="savingMetadata" class="loading loading-spinner loading-xs"></span>
                {{ savingMetadata ? 'Saving...' : 'Save' }}
              </button>
              <button
                class="btn btn-ghost btn-sm"
                @click="cancelEditMetadata"
                :disabled="savingMetadata"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>

        <!-- Metadata -->
        <div class="grid grid-cols-2 gap-4 mb-6 p-4 bg-base-200 rounded-lg">
          <div>
            <span class="font-semibold">Size:</span>
            <span class="ml-2">{{ flat.sizeX }}x{{ flat.sizeZ }}</span>
          </div>
          <div>
            <span class="font-semibold">Mount:</span>
            <span class="ml-2">({{ flat.mountX }}, {{ flat.mountZ }})</span>
          </div>
          <div>
            <span class="font-semibold">Ocean Level:</span>
            <span class="ml-2">{{ flat.oceanLevel }}</span>
          </div>
          <div>
            <span class="font-semibold">Ocean Block:</span>
            <span class="ml-2">{{ flat.oceanBlockId }}</span>
          </div>
          <div>
            <span class="font-semibold">Layer Data ID:</span>
            <span class="ml-2">{{ flat.layerDataId }}</span>
          </div>
          <div>
            <span class="font-semibold">Unknown Protected:</span>
            <span class="ml-2">{{ flat.unknownProtected ? 'Yes' : 'No' }}</span>
          </div>
        </div>

        <!-- Actions -->
        <div class="flex gap-2 mb-6">
          <a
            :href="exportUrl"
            class="btn btn-primary"
            download
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            Export Flat Data
          </a>

          <button
            class="btn btn-secondary"
            @click="triggerImport"
            :disabled="importing"
          >
            <svg v-if="!importing" class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
            </svg>
            <span v-if="importing" class="loading loading-spinner loading-sm"></span>
            {{ importing ? 'Importing...' : 'Import Flat Data' }}
          </button>

          <button
            class="btn btn-accent"
            @click="showExportConfirmation"
            :disabled="exporting"
          >
            <svg v-if="!exporting" class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
            <span v-if="exporting" class="loading loading-spinner loading-sm"></span>
            {{ exporting ? 'Exporting...' : 'Export to Layer' }}
          </button>

          <input
            ref="fileInput"
            type="file"
            accept=".json"
            class="hidden"
            @change="handleImport"
          />
        </div>

        <!-- Import Success Message -->
        <div v-if="importSuccess" class="alert alert-success mb-6">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <span>Flat data imported successfully!</span>
        </div>

        <!-- Import Error Message -->
        <div v-if="importError" class="alert alert-error mb-6">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
          <span>{{ importError }}</span>
        </div>

        <!-- Manipulators Section -->
        <FlatManipulatorPanel
          v-if="flat"
          :flatId="flat.flatId"
          :worldId="flat.worldId"
          @manipulator-completed="handleManipulatorCompleted"
        />
        </div><!-- End Details Tab -->

        <!-- Visualizations Tab Content -->
        <div v-show="activeTab === 'visualizations'" class="space-y-6">
          <!-- Height Map -->
          <div class="bg-base-100 p-4 rounded-lg border border-base-300">
            <div class="flex justify-between items-center mb-3">
              <div>
                <h3 class="text-lg font-semibold">Height Map</h3>
                <p class="text-sm text-base-content/70">Blue (low) → Green (mid) → Red (high)</p>
              </div>
              <button
                @click="heightMapZoom = !heightMapZoom"
                class="btn btn-sm btn-circle"
                :class="{ 'btn-primary': heightMapZoom, 'btn-ghost': !heightMapZoom }"
                title="Toggle zoom"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
                </svg>
              </button>
            </div>
            <div class="overflow-auto max-h-[600px]">
              <div
                class="flex justify-center transition-all duration-200"
                :style="{
                  width: heightMapZoom ? '200%' : '100%',
                  margin: '0 auto'
                }"
              >
                <img
                  :src="heightMapUrl"
                  :alt="`Height map for ${flat.flatId}`"
                  class="border border-base-300 bg-white"
                  :style="{
                    'image-rendering': 'pixelated',
                    width: heightMapZoom ? '100%' : 'auto',
                    maxWidth: heightMapZoom ? 'none' : '100%'
                  }"
                />
              </div>
            </div>
          </div>

          <!-- Block Map -->
          <div class="bg-base-100 p-4 rounded-lg border border-base-300">
            <div class="flex justify-between items-center mb-3">
              <div>
                <h3 class="text-lg font-semibold">Block Map</h3>
                <p class="text-sm text-base-content/70">Each color represents a different block type</p>
              </div>
              <button
                @click="blockMapZoom = !blockMapZoom"
                class="btn btn-sm btn-circle"
                :class="{ 'btn-primary': blockMapZoom, 'btn-ghost': !blockMapZoom }"
                title="Toggle zoom"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
                </svg>
              </button>
            </div>
            <div class="overflow-auto max-h-[600px]">
              <div
                class="flex justify-center transition-all duration-200"
                :style="{
                  width: blockMapZoom ? '200%' : '100%',
                  margin: '0 auto'
                }"
              >
                <img
                  :src="blockMapUrl"
                  :alt="`Block map for ${flat.flatId}`"
                  class="border border-base-300 bg-white"
                  :style="{
                    'image-rendering': 'pixelated',
                    width: blockMapZoom ? '100%' : 'auto',
                    maxWidth: blockMapZoom ? 'none' : '100%'
                  }"
                />
              </div>
            </div>
          </div>
        </div><!-- End Visualizations Tab -->

        <!-- Materials Tab Content -->
        <div v-show="activeTab === 'materials'">
          <MaterialEditor :flatId="props.flatId" />
        </div><!-- End Materials Tab -->

      </template>
    </div>
    <div class="modal-backdrop" @click="$emit('close')"></div>
  </div>

  <!-- Export Confirmation Dialog -->
  <div v-if="showingExportConfirmation" class="modal modal-open">
    <div class="modal-box">
      <h3 class="font-bold text-lg mb-4">Export Flat to Layer</h3>

      <p class="mb-4">
        This will export the flat terrain back to the layer <strong>{{ flat?.layerDataId }}</strong>.
        All changes made in the flat will be written to the layer.
      </p>

      <div class="alert alert-warning mb-4">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
        <span>This operation cannot be undone. Make sure you have a backup if needed.</span>
      </div>

      <div class="modal-action">
        <button
          class="btn"
          @click="cancelExportConfirmation"
          :disabled="exporting"
        >
          Cancel
        </button>
        <button
          class="btn btn-accent"
          @click="startExport"
          :disabled="exporting"
        >
          <span v-if="exporting" class="loading loading-spinner loading-sm"></span>
          {{ exporting ? 'Starting Export...' : 'Export Now' }}
        </button>
      </div>
    </div>
    <div class="modal-backdrop" @click="cancelExportConfirmation"></div>
  </div>

  <!-- Export Job Watch Modal -->
  <JobWatch
    v-if="watchingExportJob"
    :worldId="flat?.worldId || ''"
    :jobId="exportJobId"
    @close="handleExportJobClose"
    @completed="handleExportJobCompleted"
    @failed="handleExportJobFailed"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { flatService, type FlatDetail } from '@/services/FlatService';
import { apiService } from '@/services/ApiService';
import { useJobs, type JobCreateRequest, type Job } from '@/composables/useJobs';
import FlatManipulatorPanel from './FlatManipulatorPanel.vue';
import MaterialEditor from './MaterialEditor.vue';
import JobWatch from './JobWatch.vue';

const props = defineProps<{
  flatId: string;
}>();

defineEmits<{
  close: [];
}>();

const flat = ref<FlatDetail | null>(null);
const loading = ref(false);
const error = ref<string | null>(null);
const importing = ref(false);
const importSuccess = ref(false);
const importError = ref<string | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const editingMetadata = ref(false);
const editTitle = ref('');
const editDescription = ref('');
const savingMetadata = ref(false);

// Tab navigation
const activeTab = ref<'details' | 'visualizations' | 'materials'>('details');

// Visualization zoom state
const heightMapZoom = ref(false);
const blockMapZoom = ref(false);

// Export state
const showingExportConfirmation = ref(false);
const exporting = ref(false);
const watchingExportJob = ref(false);
const exportJobId = ref('');

/**
 * Computed URLs for images
 */
const heightMapUrl = computed(() => {
  if (!props.flatId) return '';
  return `${apiService.getBaseUrl()}/control/flats/${encodeURIComponent(props.flatId)}/height-map`;
});

const blockMapUrl = computed(() => {
  if (!props.flatId) return '';
  return `${apiService.getBaseUrl()}/control/flats/${encodeURIComponent(props.flatId)}/block-map`;
});

const exportUrl = computed(() => {
  if (!props.flatId) return '';
  return flatService.getExportUrl(props.flatId);
});

/**
 * Load flat details
 */
const loadFlat = async () => {
  loading.value = true;
  error.value = null;

  try {
    flat.value = await flatService.getFlat(props.flatId);
  } catch (e: any) {
    console.error('[FlatDetailModal] Failed to load flat:', e);
    error.value = e.message;
  } finally {
    loading.value = false;
  }
};

/**
 * Trigger file input click
 */
const triggerImport = () => {
  fileInput.value?.click();
};

/**
 * Handle file import
 */
const handleImport = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];

  if (!file) return;

  importing.value = true;
  importSuccess.value = false;
  importError.value = null;

  try {
    await flatService.importFlat(props.flatId, file);
    importSuccess.value = true;

    // Reload flat data to show updated info
    await loadFlat();

    // Clear success message after 3 seconds
    setTimeout(() => {
      importSuccess.value = false;
    }, 3000);
  } catch (e: any) {
    console.error('[FlatDetailModal] Failed to import flat:', e);
    importError.value = e.message || 'Import failed';
  } finally {
    importing.value = false;
    // Reset file input
    target.value = '';
  }
};

/**
 * Start editing metadata
 */
const startEditMetadata = () => {
  if (!flat.value) return;
  editTitle.value = flat.value.title || '';
  editDescription.value = flat.value.description || '';
  editingMetadata.value = true;
};

/**
 * Cancel editing metadata
 */
const cancelEditMetadata = () => {
  editingMetadata.value = false;
  editTitle.value = '';
  editDescription.value = '';
};

/**
 * Save metadata
 */
const saveMetadata = async () => {
  if (!flat.value) return;

  savingMetadata.value = true;

  try {
    const updated = await flatService.updateMetadata(
      props.flatId,
      editTitle.value || null,
      editDescription.value || null
    );

    // Update flat data
    flat.value = updated;
    editingMetadata.value = false;
  } catch (e: any) {
    console.error('[FlatDetailModal] Failed to update metadata:', e);
    alert('Failed to update metadata: ' + (e.message || 'Unknown error'));
  } finally {
    savingMetadata.value = false;
  }
};

/**
 * Handle manipulator completed
 */
const handleManipulatorCompleted = async () => {
  console.log('[FlatDetailModal] Manipulator completed, reloading flat');
  // Reload flat data to show updated terrain
  await loadFlat();
};

/**
 * Show export confirmation dialog
 */
const showExportConfirmation = () => {
  showingExportConfirmation.value = true;
};

/**
 * Cancel export confirmation
 */
const cancelExportConfirmation = () => {
  showingExportConfirmation.value = false;
};

/**
 * Start export job
 */
const startExport = async () => {
  if (!flat.value) return;

  exporting.value = true;

  try {
    // Initialize jobs composable
    const { createJob } = useJobs(flat.value.worldId);

    // Build job parameters - only flatId is required
    const parameters: Record<string, string> = {
      flatId: flat.value.flatId,
    };

    // Create export job
    const jobRequest: JobCreateRequest = {
      executor: 'flat-export',
      parameters: parameters,
      priority: 5,
      maxRetries: 1,
    };

    console.log('[FlatDetailModal] Creating export job:', jobRequest);
    const createdJob = await createJob(jobRequest);

    console.log('[FlatDetailModal] Export job created:', createdJob.id);

    // Hide confirmation, show job watch
    showingExportConfirmation.value = false;
    exportJobId.value = createdJob.id;
    watchingExportJob.value = true;
  } catch (e: any) {
    console.error('[FlatDetailModal] Failed to create export job:', e);
    alert('Failed to start export: ' + (e.message || 'Unknown error'));
    showingExportConfirmation.value = false;
  } finally {
    exporting.value = false;
  }
};

/**
 * Handle export job watch close
 */
const handleExportJobClose = () => {
  watchingExportJob.value = false;
};

/**
 * Handle export job completed
 */
const handleExportJobCompleted = async (job: Job) => {
  console.log('[FlatDetailModal] Export job completed:', job.id);
  watchingExportJob.value = false;

  // Show success message
  alert('Flat exported successfully to layer!');
};

/**
 * Handle export job failed
 */
const handleExportJobFailed = (job: Job) => {
  console.error('[FlatDetailModal] Export job failed:', job.id, job.errorMessage);
  watchingExportJob.value = false;
  alert(`Export failed: ${job.errorMessage || 'Unknown error'}`);
};

// Watch for flatId changes
watch(() => props.flatId, () => {
  if (props.flatId) {
    loadFlat();
  }
}, { immediate: true });
</script>
