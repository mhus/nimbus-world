<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-4xl">
      <h3 class="font-bold text-lg mb-4 flex items-center gap-2">
        Job Details
        <span
          class="badge"
          :class="getStatusClass(job.status)"
        >
          {{ job.status }}
        </span>
      </h3>

      <!-- Job Info -->
      <div class="space-y-4">
        <!-- Basic Info -->
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="text-sm font-semibold text-base-content/70">Job ID</label>
            <div class="font-mono text-sm mt-1">{{ job.id }}</div>
          </div>
          <div>
            <label class="text-sm font-semibold text-base-content/70">World ID</label>
            <div class="font-mono text-sm mt-1">{{ job.worldId }}</div>
          </div>
          <div>
            <label class="text-sm font-semibold text-base-content/70">Executor</label>
            <div class="text-sm mt-1">{{ job.executor }}</div>
          </div>
          <div>
            <label class="text-sm font-semibold text-base-content/70">Type</label>
            <div class="text-sm mt-1">{{ job.type }}</div>
          </div>
          <div>
            <label class="text-sm font-semibold text-base-content/70">Priority</label>
            <div class="text-sm mt-1">{{ job.priority }}</div>
          </div>
          <div>
            <label class="text-sm font-semibold text-base-content/70">Retries</label>
            <div class="text-sm mt-1">{{ job.retryCount }} / {{ job.maxRetries }}</div>
          </div>
        </div>

        <!-- Timestamps -->
        <div class="divider">Timestamps</div>
        <div class="grid grid-cols-2 gap-4">
          <div>
            <label class="text-sm font-semibold text-base-content/70">Created</label>
            <div class="text-sm mt-1">{{ formatDate(job.createdAt) }}</div>
          </div>
          <div v-if="job.startedAt">
            <label class="text-sm font-semibold text-base-content/70">Started</label>
            <div class="text-sm mt-1">{{ formatDate(job.startedAt) }}</div>
          </div>
          <div v-if="job.completedAt">
            <label class="text-sm font-semibold text-base-content/70">Completed</label>
            <div class="text-sm mt-1">{{ formatDate(job.completedAt) }}</div>
          </div>
          <div v-if="job.modifiedAt">
            <label class="text-sm font-semibold text-base-content/70">Modified</label>
            <div class="text-sm mt-1">{{ formatDate(job.modifiedAt) }}</div>
          </div>
        </div>

        <!-- Parameters -->
        <div class="divider">Parameters</div>
        <div class="bg-base-200 rounded p-4">
          <pre class="text-xs font-mono overflow-x-auto">{{ formatJson(job.parameters) }}</pre>
        </div>

        <!-- Result Data -->
        <div v-if="job.resultData" class="divider">Result</div>
        <div v-if="job.resultData" class="bg-success/10 rounded p-4">
          <pre class="text-xs font-mono overflow-x-auto text-success">{{ job.resultData }}</pre>
        </div>

        <!-- Error Message -->
        <div v-if="job.errorMessage" class="divider">Error</div>
        <div v-if="job.errorMessage" class="bg-error/10 rounded p-4">
          <pre class="text-xs font-mono overflow-x-auto text-error">{{ job.errorMessage }}</pre>
        </div>

        <!-- Actions -->
        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="$emit('close')"
          >
            Close
          </button>

          <button
            type="button"
            class="btn btn-info"
            @click="$emit('clone', job)"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
            </svg>
            Clone
          </button>

          <button
            v-if="job.status === 'FAILED'"
            type="button"
            class="btn btn-warning"
            @click="$emit('retry', job)"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Retry
          </button>

          <button
            v-if="job.status === 'PENDING' || job.status === 'RUNNING'"
            type="button"
            class="btn btn-warning"
            @click="$emit('cancel', job)"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
            Cancel
          </button>

          <button
            type="button"
            class="btn btn-error"
            @click="$emit('delete', job)"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            Delete
          </button>
        </div>
      </div>
    </div>
    <div class="modal-backdrop" @click="$emit('close')"></div>
  </div>
</template>

<script setup lang="ts">
import type { Job, JobStatus } from '@/composables/useJobs';

defineProps<{
  job: Job;
  worldId: string;
}>();

defineEmits<{
  close: [];
  clone: [job: Job];
  retry: [job: Job];
  cancel: [job: Job];
  delete: [job: Job];
}>();

/**
 * Get status badge class
 */
const getStatusClass = (status: JobStatus): string => {
  switch (status) {
    case 'PENDING':
      return 'badge-warning';
    case 'RUNNING':
      return 'badge-info';
    case 'COMPLETED':
      return 'badge-success';
    case 'FAILED':
      return 'badge-error';
    default:
      return '';
  }
};

/**
 * Format date for display
 */
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString();
};

/**
 * Format JSON for display
 */
const formatJson = (obj: any): string => {
  return JSON.stringify(obj, null, 2);
};
</script>
