<template>
  <div class="overflow-x-auto">
    <table class="table w-full">
      <thead>
        <tr>
          <th>ID</th>
          <th>Executor</th>
          <th>Type</th>
          <th>Status</th>
          <th>Priority</th>
          <th>Retries</th>
          <th>Created</th>
          <th>Duration</th>
          <th>Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job in jobs" :key="job.id">
          <!-- ID -->
          <td>
            <code class="text-xs font-mono">{{ job.id.substring(0, 8) }}...</code>
          </td>

          <!-- Executor -->
          <td>
            <div class="font-medium text-sm">{{ job.executor }}</div>
          </td>

          <!-- Type -->
          <td>
            <div class="text-sm text-base-content/70">{{ job.type }}</div>
          </td>

          <!-- Status -->
          <td>
            <span
              class="badge badge-sm"
              :class="getStatusClass(job.status)"
            >
              {{ job.status }}
            </span>
            <div v-if="job.errorMessage" class="text-xs text-error mt-1 max-w-xs truncate">
              {{ job.errorMessage }}
            </div>
          </td>

          <!-- Priority -->
          <td>
            <div class="text-sm">{{ job.priority }}</div>
          </td>

          <!-- Retries -->
          <td>
            <div class="text-sm">
              {{ job.retryCount }} / {{ job.maxRetries }}
            </div>
          </td>

          <!-- Created -->
          <td>
            <div class="text-sm text-base-content/70">
              {{ formatDate(job.createdAt) }}
            </div>
          </td>

          <!-- Duration -->
          <td>
            <div class="text-sm text-base-content/70">
              {{ getDuration(job) }}
            </div>
          </td>

          <!-- Actions -->
          <td>
            <div class="flex gap-2">
              <button
                class="btn btn-xs btn-ghost"
                @click="$emit('view', job)"
                title="View Details"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              </button>

              <button
                class="btn btn-xs btn-ghost text-info"
                @click="$emit('clone', job)"
                title="Clone Job"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                </svg>
              </button>

              <button
                v-if="job.status === 'FAILED'"
                class="btn btn-xs btn-ghost text-warning"
                @click="$emit('retry', job)"
                title="Retry"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
              </button>

              <button
                v-if="job.status === 'PENDING' || job.status === 'RUNNING'"
                class="btn btn-xs btn-ghost text-warning"
                @click="$emit('cancel', job)"
                title="Cancel"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>

              <button
                class="btn btn-xs btn-ghost text-error"
                @click="$emit('delete', job)"
                title="Delete"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import type { Job, JobStatus } from '@/composables/useJobs';

defineProps<{
  jobs: Job[];
  loading?: boolean;
}>();

defineEmits<{
  view: [job: Job];
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
 * Get job duration
 */
const getDuration = (job: Job): string => {
  if (!job.startedAt) {
    return '-';
  }

  const start = new Date(job.startedAt).getTime();
  const end = job.completedAt ? new Date(job.completedAt).getTime() : Date.now();
  const durationMs = end - start;

  const seconds = Math.floor(durationMs / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);

  if (hours > 0) {
    return `${hours}h ${minutes % 60}m`;
  } else if (minutes > 0) {
    return `${minutes}m ${seconds % 60}s`;
  } else {
    return `${seconds}s`;
  }
};
</script>
