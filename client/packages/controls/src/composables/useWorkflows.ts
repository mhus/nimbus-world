/**
 * useWorkflows Composable
 * Manages workflow listing, viewing, and deletion operations
 */

import { ref, type Ref } from 'vue';
import { apiService } from '../services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useWorkflows');

export interface WorkflowSummary {
  workflowId: string;
  workflowName: string;
  createdAt: string;
}

export interface WorkflowJournalEntry {
  id: string;
  worldId: string;
  workflowId: string;
  type: string;
  data: string;
  createdAt: string;
}

export interface UseWorkflowsReturn {
  workflows: Ref<WorkflowSummary[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadWorkflows: () => Promise<void>;
  loadWorkflowJournal: (workflowId: string) => Promise<WorkflowJournalEntry[]>;
  deleteWorkflow: (workflowId: string) => Promise<void>;
}

export function useWorkflows(worldId: string): UseWorkflowsReturn {
  const workflows = ref<WorkflowSummary[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load all workflows for the current world
   */
  const loadWorkflows = async () => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<WorkflowSummary[]>(
        `/control/worlds/${worldId}/workflows`
      );
      workflows.value = response;
      logger.info('Loaded workflows', { worldId, count: workflows.value.length });
    } catch (err) {
      error.value = 'Failed to load workflows';
      logger.error('Failed to load workflows', { worldId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load journal entries for a specific workflow
   */
  const loadWorkflowJournal = async (workflowId: string): Promise<WorkflowJournalEntry[]> => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return [];
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<WorkflowJournalEntry[]>(
        `/control/worlds/${worldId}/workflows/${workflowId}`
      );
      logger.info('Loaded workflow journal', { worldId, workflowId, count: response.length });
      return response;
    } catch (err) {
      error.value = `Failed to load workflow journal: ${workflowId}`;
      logger.error('Failed to load workflow journal', { worldId, workflowId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Delete a workflow
   */
  const deleteWorkflow = async (workflowId: string) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.delete(`/control/worlds/${worldId}/workflows/${workflowId}`);
      logger.info('Deleted workflow', { worldId, workflowId });
      await loadWorkflows();
    } catch (err) {
      error.value = `Failed to delete workflow: ${workflowId}`;
      logger.error('Failed to delete workflow', { worldId, workflowId }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  return {
    workflows,
    loading,
    error,
    loadWorkflows,
    loadWorkflowJournal,
    deleteWorkflow,
  };
}
