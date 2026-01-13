/**
 * useJobs Composable
 * Manages job CRUD and control operations
 */

import { ref, type Ref } from 'vue';
import { apiService } from '../services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useJobs');

export type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface NextJob {
  executor: string;
  type?: string;
  parameters?: Record<string, string>;
}

export interface Job {
  id: string;
  worldId: string;
  executor: string;
  type: string;
  parameters: Record<string, string>;
  status: JobStatus;
  priority: number;
  maxRetries: number;
  retryCount: number;
  resultData?: string;
  errorMessage?: string;
  onSuccess?: NextJob;
  onError?: NextJob;
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  modifiedAt?: string;
}

export interface JobSummary {
  total: number;
  pending: number;
  running: number;
  completed: number;
  failed: number;
}

export interface JobCreateRequest {
  executor: string;
  type?: string;
  parameters: Record<string, string>;
  priority?: number;
  maxRetries?: number;
  onSuccess?: NextJob;
  onError?: NextJob;
}

export interface UseJobsReturn {
  jobs: Ref<Job[]>;
  summary: Ref<JobSummary | null>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadJobs: (status?: JobStatus) => Promise<void>;
  loadJob: (jobId: string) => Promise<Job | null>;
  loadSummary: () => Promise<void>;
  createJob: (request: JobCreateRequest) => Promise<Job>;
  retryJob: (jobId: string) => Promise<void>;
  cancelJob: (jobId: string) => Promise<void>;
  deleteJob: (jobId: string) => Promise<void>;
}

export function useJobs(worldId: string): UseJobsReturn {
  const jobs = ref<Job[]>([]);
  const summary = ref<JobSummary | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load all jobs or filter by status
   */
  const loadJobs = async (status?: JobStatus) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      const url = status
        ? `/control/worlds/${worldId}/jobs/status/${status}`
        : `/control/worlds/${worldId}/jobs`;

      const response = await apiService.get<Job[]>(url);
      jobs.value = response;
      logger.info('Loaded jobs', { worldId, status, count: jobs.value.length });
    } catch (err) {
      error.value = 'Failed to load jobs';
      logger.error('Failed to load jobs', { worldId, status }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load single job
   */
  const loadJob = async (jobId: string): Promise<Job | null> => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return null;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<Job>(
        `/control/worlds/${worldId}/jobs/${jobId}`
      );
      logger.info('Loaded job', { worldId, jobId });
      return response;
    } catch (err) {
      error.value = `Failed to load job ${jobId}`;
      logger.error('Failed to load job', { worldId, jobId }, err as Error);
      return null;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load job summary
   */
  const loadSummary = async () => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<JobSummary>(
        `/control/worlds/${worldId}/jobs/summary`
      );
      summary.value = response;
      logger.info('Loaded job summary', { worldId, summary: response });
    } catch (err) {
      error.value = 'Failed to load job summary';
      logger.error('Failed to load job summary', { worldId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Create job
   */
  const createJob = async (request: JobCreateRequest): Promise<Job> => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      const createdJob = await apiService.post<Job>(
        `/control/worlds/${worldId}/jobs`,
        request
      );
      logger.info('Created job', { worldId, request, jobId: createdJob.id });
      await loadJobs();
      await loadSummary();
      return createdJob;
    } catch (err) {
      error.value = 'Failed to create job';
      logger.error('Failed to create job', { worldId, request }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Retry failed job
   */
  const retryJob = async (jobId: string) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.post<void>(
        `/control/worlds/${worldId}/jobs/${jobId}/retry`
      );
      logger.info('Retried job', { worldId, jobId });
      await loadJobs();
      await loadSummary();
    } catch (err) {
      error.value = `Failed to retry job ${jobId}`;
      logger.error('Failed to retry job', { worldId, jobId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Cancel pending/running job
   */
  const cancelJob = async (jobId: string) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.patch<void>(
        `/control/worlds/${worldId}/jobs/${jobId}`,
        { status: 'FAILED' }
      );
      logger.info('Cancelled job', { worldId, jobId });
      await loadJobs();
      await loadSummary();
    } catch (err) {
      error.value = `Failed to cancel job ${jobId}`;
      logger.error('Failed to cancel job', { worldId, jobId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Delete job
   */
  const deleteJob = async (jobId: string) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.delete(
        `/control/worlds/${worldId}/jobs/${jobId}`
      );
      logger.info('Deleted job', { worldId, jobId });
      await loadJobs();
      await loadSummary();
    } catch (err) {
      error.value = `Failed to delete job ${jobId}`;
      logger.error('Failed to delete job', { worldId, jobId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  return {
    jobs,
    summary,
    loading,
    error,
    loadJobs,
    loadJob,
    loadSummary,
    createJob,
    retryJob,
    cancelJob,
    deleteJob,
  };
}
