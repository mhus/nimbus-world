<template>
  <div class="modal modal-open">
    <div class="modal-box">
      <h3 class="font-bold text-lg mb-4">Job Execution</h3>

      <!-- Loading State -->
      <div v-if="job && (job.status === 'PENDING' || job.status === 'RUNNING')" class="space-y-4">
        <div class="flex items-center gap-3">
          <span class="loading loading-spinner loading-lg text-primary"></span>
          <div>
            <div class="font-semibold">{{ job.status === 'PENDING' ? 'Waiting...' : 'Running...' }}</div>
            <div class="text-sm text-base-content/70">{{ job.executor }}</div>
          </div>
        </div>

        <div class="text-sm text-base-content/70">
          <div>Job ID: <span class="font-mono">{{ job.id }}</span></div>
          <div v-if="job.startedAt">Started: {{ formatDate(job.startedAt) }}</div>
          <div v-else>Created: {{ formatDate(job.createdAt) }}</div>
        </div>

        <div class="alert alert-info">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <span>This may take a while. You can close this dialog and the job will continue running.</span>
        </div>
      </div>

      <!-- Success State -->
      <div v-else-if="job && job.status === 'COMPLETED'" class="space-y-4">
        <div class="alert alert-success">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <div class="font-semibold">Job Completed Successfully</div>
            <div v-if="job.completedAt" class="text-sm">Completed: {{ formatDate(job.completedAt) }}</div>
          </div>
        </div>

        <div v-if="job.resultData" class="bg-success/10 rounded p-3">
          <div class="font-semibold text-sm mb-1">Result:</div>
          <pre class="text-xs font-mono overflow-x-auto">{{ job.resultData }}</pre>
        </div>
      </div>

      <!-- Failed State -->
      <div v-else-if="job && job.status === 'FAILED'" class="space-y-4">
        <div class="alert alert-error">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div>
            <div class="font-semibold">Job Failed</div>
            <div v-if="job.completedAt" class="text-sm">Failed: {{ formatDate(job.completedAt) }}</div>
          </div>
        </div>

        <div v-if="job.errorMessage" class="bg-error/10 rounded p-3">
          <div class="font-semibold text-sm mb-1">Error:</div>
          <pre class="text-xs font-mono overflow-x-auto text-error">{{ job.errorMessage }}</pre>
        </div>
      </div>

      <!-- Loading Error -->
      <div v-else-if="error" class="alert alert-error">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>{{ error }}</span>
      </div>

      <!-- Actions -->
      <div class="modal-action">
        <button
          class="btn"
          @click="handleClose"
        >
          {{ isRunning ? 'Close (Job continues)' : 'Close' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useJobs, type Job } from '@/composables/useJobs';

const props = defineProps<{
  worldId: string;
  jobId: string;
  pollInterval?: number; // milliseconds, default 2000
}>();

const emit = defineEmits<{
  close: [];
  completed: [job: Job];
  failed: [job: Job];
}>();

const { loadJob } = useJobs(props.worldId);

const job = ref<Job | null>(null);
const error = ref<string | null>(null);
let pollTimer: number | null = null;

const isRunning = computed(() => {
  return job.value && (job.value.status === 'PENDING' || job.value.status === 'RUNNING');
});

const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleString();
};

const pollJob = async () => {
  try {
    const loadedJob = await loadJob(props.jobId);
    if (loadedJob) {
      job.value = loadedJob;

      // Check if job is finished
      if (loadedJob.status === 'COMPLETED') {
        stopPolling();
        emit('completed', loadedJob);
      } else if (loadedJob.status === 'FAILED') {
        stopPolling();
        emit('failed', loadedJob);
      }
    }
  } catch (err: any) {
    console.error('[JobWatch] Failed to poll job:', err);
    error.value = err.message || 'Failed to load job status';
    stopPolling();
  }
};

const startPolling = () => {
  const interval = props.pollInterval || 2000;
  pollTimer = window.setInterval(pollJob, interval);
};

const stopPolling = () => {
  if (pollTimer !== null) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

const handleClose = () => {
  stopPolling();
  emit('close');
};

onMounted(async () => {
  // Initial load
  await pollJob();

  // Start polling if job is running
  if (isRunning.value) {
    startPolling();
  }
});

onUnmounted(() => {
  stopPolling();
});
</script>
