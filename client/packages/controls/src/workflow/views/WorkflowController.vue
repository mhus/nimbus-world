<template>
  <div class="space-y-4">
    <!-- Check if world is selected -->
    <div v-if="!currentWorldId" class="alert alert-info">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Please select a world to view workflows.</span>
    </div>

    <!-- Workflow Editor Content (only shown when world is selected) -->
    <template v-else>
      <!-- Header -->
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-2xl font-bold">Workflows</h2>
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

      <!-- Loading State -->
      <LoadingSpinner v-if="loading && workflows.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && workflows.length === 0" class="text-center py-12">
        <svg class="w-16 h-16 mx-auto text-base-content/30 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
        </svg>
        <p class="text-base-content/70 text-lg">No workflows found for this world</p>
        <p class="text-base-content/50 text-sm mt-2">Workflows will appear here once they are started</p>
      </div>

      <!-- Workflow List -->
      <div v-else class="overflow-x-auto">
        <table class="table w-full">
          <thead>
            <tr>
              <th>Workflow ID</th>
              <th>Workflow Name</th>
              <th>Created At</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="workflow in workflows" :key="workflow.workflowId">
              <!-- Workflow ID -->
              <td>
                <code class="text-xs font-mono">{{ workflow.workflowId }}</code>
              </td>

              <!-- Workflow Name -->
              <td>
                <div class="font-medium">{{ workflow.workflowName }}</div>
              </td>

              <!-- Created At -->
              <td>
                <div class="text-sm text-base-content/70">
                  {{ formatDate(workflow.createdAt) }}
                </div>
              </td>

              <!-- Actions -->
              <td>
                <div class="flex gap-2">
                  <button
                    class="btn btn-xs btn-ghost"
                    @click="handleViewWorkflow(workflow.workflowId)"
                    title="View Journal"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                    </svg>
                    View
                  </button>

                  <button
                    class="btn btn-xs btn-ghost text-error"
                    @click="handleDeleteWorkflow(workflow)"
                    title="Delete"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Workflow Detail Modal -->
      <WorkflowDetailModal
        v-if="selectedWorkflowId"
        :world-id="currentWorldId!"
        :workflow-id="selectedWorkflowId"
        @close="closeWorkflowDetail"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useWorkflows, type WorkflowSummary } from '@/composables/useWorkflows';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import WorkflowDetailModal from '../components/WorkflowDetailModal.vue';

const { currentWorldId, loadWorlds } = useWorld();

const workflowsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useWorkflows(currentWorldId.value);
});

const workflows = computed(() => workflowsComposable.value?.workflows.value || []);
const loading = computed(() => workflowsComposable.value?.loading.value || false);
const error = computed(() => workflowsComposable.value?.error.value || null);
const selectedWorkflowId = ref<string | null>(null);

// Load workflows when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value && currentWorldId.value !== '?') {
    workflowsComposable.value?.loadWorkflows();
  }
}, { immediate: true });

onMounted(() => {
  // Load worlds with allWithoutInstances filter
  loadWorlds('allWithoutInstances');
});

/**
 * Handle refresh
 */
const handleRefresh = () => {
  workflowsComposable.value?.loadWorkflows();
};

/**
 * Handle view workflow
 */
const handleViewWorkflow = (workflowId: string) => {
  selectedWorkflowId.value = workflowId;
};

/**
 * Handle delete workflow
 */
const handleDeleteWorkflow = async (workflow: WorkflowSummary) => {
  if (!workflowsComposable.value) return;

  if (!confirm(`Are you sure you want to delete workflow "${workflow.workflowName}"?\n\nWorkflow ID: ${workflow.workflowId}\n\nThis will remove all journal entries for this workflow.`)) {
    return;
  }

  try {
    await workflowsComposable.value.deleteWorkflow(workflow.workflowId);
  } catch (e: any) {
    console.error('[WorkflowController] Failed to delete workflow:', e);
    alert(`Failed to delete workflow: ${e.message}`);
  }
};

/**
 * Close workflow detail
 */
const closeWorkflowDetail = () => {
  selectedWorkflowId.value = null;
};

/**
 * Format date for display
 */
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString();
};
</script>
