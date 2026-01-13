<template>
  <div class="space-y-4">
    <!-- Summary Cards -->
    <div v-if="summary" class="grid grid-cols-2 md:grid-cols-5 gap-4">
      <div class="stat bg-base-200 rounded-lg shadow">
        <div class="stat-title">Total</div>
        <div class="stat-value text-2xl">{{ summary.total }}</div>
      </div>
      <div class="stat bg-base-200 rounded-lg shadow">
        <div class="stat-title">Pending</div>
        <div class="stat-value text-2xl text-warning">{{ summary.pending }}</div>
      </div>
      <div class="stat bg-base-200 rounded-lg shadow">
        <div class="stat-title">Running</div>
        <div class="stat-value text-2xl text-info">{{ summary.running }}</div>
      </div>
      <div class="stat bg-base-200 rounded-lg shadow">
        <div class="stat-title">Completed</div>
        <div class="stat-value text-2xl text-success">{{ summary.completed }}</div>
      </div>
      <div class="stat bg-base-200 rounded-lg shadow">
        <div class="stat-title">Failed</div>
        <div class="stat-value text-2xl text-error">{{ summary.failed }}</div>
      </div>
    </div>

    <!-- Header with Filter and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex gap-2">
        <!-- Status Filter -->
        <select
          v-model="statusFilter"
          class="select select-bordered"
          @change="handleStatusFilterChange"
        >
          <option value="">All Jobs</option>
          <option value="PENDING">Pending</option>
          <option value="RUNNING">Running</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
        </select>

        <!-- Refresh Button -->
        <button
          class="btn btn-ghost"
          @click="handleRefresh"
          :disabled="loading"
        >
          <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          Refresh
        </button>
      </div>

      <div class="flex gap-2">
        <!-- Preset Selector -->
        <select
          v-if="presets.length > 0"
          class="select select-bordered"
          @change="handlePresetSelect"
          v-model="selectedPresetId"
        >
          <option value="">Load from Preset...</option>
          <option v-for="preset in presets" :key="preset.id" :value="preset.id">
            {{ preset.title || preset.name }}
          </option>
        </select>

        <button
          class="btn btn-primary"
          @click="openCreateDialog"
        >
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          New Job
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <LoadingSpinner v-if="loading && jobs.length === 0" />

    <!-- Error State -->
    <ErrorAlert v-else-if="error" :message="error" />

    <!-- Empty State -->
    <div v-else-if="!loading && jobs.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No jobs found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first job to get started</p>
    </div>

    <!-- Job List -->
    <JobList
      v-else
      :jobs="jobs"
      :loading="loading"
      @view="openDetailsDialog"
      @clone="handleClone"
      @retry="handleRetry"
      @cancel="handleCancel"
      @delete="handleDelete"
    />

    <!-- Create Job Dialog -->
    <JobCreatePanel
      v-if="isCreateDialogOpen"
      :world-id="currentWorldId!"
      :initial-job="cloneSourceJob"
      :preset-data="presetData"
      @close="closeCreateDialog"
      @created="handleCreated"
    />

    <!-- Job Details Dialog -->
    <JobDetailsPanel
      v-if="isDetailsDialogOpen && selectedJob"
      :job="selectedJob"
      :world-id="currentWorldId!"
      @close="closeDetailsDialog"
      @clone="handleClone"
      @retry="handleRetry"
      @cancel="handleCancel"
      @delete="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useJobs, type Job, type JobStatus } from '@/composables/useJobs';
import { useJobPresets, type JobPreset } from '@/composables/useJobPresets';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import JobList from '@material/components/JobList.vue';
import JobCreatePanel from '@material/components/JobCreatePanel.vue';
import JobDetailsPanel from '@material/components/JobDetailsPanel.vue';

const { currentWorldId, loadWorlds } = useWorld();

const { presets, loadPresets } = useJobPresets();

const jobsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useJobs(currentWorldId.value);
});

const jobs = computed(() => jobsComposable.value?.jobs.value || []);
const summary = computed(() => jobsComposable.value?.summary.value || null);
const loading = computed(() => jobsComposable.value?.loading.value || false);
const error = computed(() => jobsComposable.value?.error.value || null);

const statusFilter = ref<JobStatus | ''>('');
const isCreateDialogOpen = ref(false);
const isDetailsDialogOpen = ref(false);
const selectedJob = ref<Job | null>(null);
const cloneSourceJob = ref<Job | null>(null);
const selectedPresetId = ref<string>('');
const presetData = ref<any>(null);

/**
 * Load jobs and summary
 */
const loadData = async () => {
  if (!jobsComposable.value) return;
  await Promise.all([
    jobsComposable.value.loadJobs(statusFilter.value || undefined),
    jobsComposable.value.loadSummary(),
  ]);
};

// Load jobs, summary, and presets when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value && currentWorldId.value !== '?') {
    loadData();
    loadPresets(currentWorldId.value);
  }
}, { immediate: true });

/**
 * Handle status filter change
 */
const handleStatusFilterChange = () => {
  if (!jobsComposable.value) return;
  jobsComposable.value.loadJobs(statusFilter.value || undefined);
};

/**
 * Handle refresh
 */
const handleRefresh = () => {
  loadData();
};

/**
 * Open create dialog
 */
const openCreateDialog = () => {
  cloneSourceJob.value = null;
  presetData.value = null;
  selectedPresetId.value = '';
  isCreateDialogOpen.value = true;
};

/**
 * Close create dialog
 */
const closeCreateDialog = () => {
  isCreateDialogOpen.value = false;
  cloneSourceJob.value = null;
  presetData.value = null;
  selectedPresetId.value = '';
};

/**
 * Handle job created
 */
const handleCreated = () => {
  closeCreateDialog();
};

/**
 * Open details dialog
 */
const openDetailsDialog = (job: Job) => {
  selectedJob.value = job;
  isDetailsDialogOpen.value = true;
};

/**
 * Close details dialog
 */
const closeDetailsDialog = () => {
  isDetailsDialogOpen.value = false;
  selectedJob.value = null;
};

/**
 * Handle clone
 */
const handleClone = (job: Job) => {
  cloneSourceJob.value = job;
  presetData.value = null;
  selectedPresetId.value = '';
  closeDetailsDialog();
  isCreateDialogOpen.value = true;
};

/**
 * Handle preset selection
 */
const handlePresetSelect = () => {
  if (!selectedPresetId.value) {
    return;
  }

  const preset = presets.value.find((p: JobPreset) => p.id === selectedPresetId.value);
  if (!preset) {
    return;
  }

  presetData.value = preset.data;
  cloneSourceJob.value = null;
  isCreateDialogOpen.value = true;
};

/**
 * Handle retry
 */
const handleRetry = async (job: Job) => {
  if (!jobsComposable.value) return;

  if (!confirm(`Are you sure you want to retry job "${job.id}"?`)) {
    return;
  }

  await jobsComposable.value.retryJob(job.id);
  closeDetailsDialog();
};

/**
 * Handle cancel
 */
const handleCancel = async (job: Job) => {
  if (!jobsComposable.value) return;

  if (!confirm(`Are you sure you want to cancel job "${job.id}"?`)) {
    return;
  }

  await jobsComposable.value.cancelJob(job.id);
  closeDetailsDialog();
};

/**
 * Handle delete
 */
const handleDelete = async (job: Job) => {
  if (!jobsComposable.value) return;

  if (!confirm(`Are you sure you want to delete job "${job.id}"?`)) {
    return;
  }

  await jobsComposable.value.deleteJob(job.id);
  closeDetailsDialog();
};

// Auto-refresh every 5 seconds
let refreshInterval: number | null = null;

onMounted(() => {
  // Load worlds with allWithoutInstances filter for job controller
  loadWorlds('allWithoutInstances');

  refreshInterval = window.setInterval(() => {
    if (currentWorldId.value && !isCreateDialogOpen.value && !isDetailsDialogOpen.value) {
      loadData();
    }
  }, 5000);
});

// Clean up interval on unmount
import { onUnmounted } from 'vue';
onUnmounted(() => {
  if (refreshInterval !== null) {
    clearInterval(refreshInterval);
  }
});
</script>
