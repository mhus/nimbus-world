<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-6xl">
      <h3 class="font-bold text-lg mb-4 flex items-center gap-2">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        Workflow Journal
      </h3>

      <!-- Workflow ID -->
      <div class="mb-4">
        <label class="text-sm font-semibold text-base-content/70">Workflow ID</label>
        <div class="font-mono text-sm mt-1">{{ workflowId }}</div>
      </div>

      <!-- Loading State -->
      <LoadingSpinner v-if="loading" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Journal Entries -->
      <div v-else-if="journalEntries.length > 0" class="space-y-4">
        <div class="divider">Journal Entries ({{ journalEntries.length }})</div>

        <div class="overflow-x-auto max-h-[60vh]">
          <div class="space-y-3">
            <div
              v-for="(entry, index) in journalEntries"
              :key="entry.id"
              class="card bg-base-200 shadow"
            >
              <div class="card-body p-4">
                <div class="flex justify-between items-start mb-2">
                  <div class="flex items-center gap-2">
                    <span class="badge badge-sm badge-primary">{{ index + 1 }}</span>
                    <span class="font-semibold text-sm">{{ getShortType(entry.type) }}</span>
                  </div>
                  <div class="text-xs text-base-content/50">
                    {{ formatDate(entry.createdAt) }}
                  </div>
                </div>

                <!-- Type (full) -->
                <div class="text-xs text-base-content/70 mb-2">
                  <span class="font-mono">{{ entry.type }}</span>
                </div>

                <!-- Data -->
                <div class="bg-base-100 rounded p-3">
                  <pre class="text-xs font-mono overflow-x-auto whitespace-pre-wrap break-all">{{ entry.data }}</pre>
                </div>

                <!-- Entry ID -->
                <div class="text-xs text-base-content/50 mt-2">
                  Entry ID: <span class="font-mono">{{ entry.id }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else class="text-center py-8">
        <p class="text-base-content/70">No journal entries found for this workflow</p>
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
      </div>
    </div>
    <div class="modal-backdrop" @click="$emit('close')"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useWorkflows, type WorkflowJournalEntry } from '@/composables/useWorkflows';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';

const props = defineProps<{
  worldId: string;
  workflowId: string;
}>();

defineEmits<{
  close: [];
}>();

const { loadWorkflowJournal } = useWorkflows(props.worldId);

const journalEntries = ref<WorkflowJournalEntry[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

/**
 * Load journal entries
 */
const loadJournal = async () => {
  loading.value = true;
  error.value = null;

  try {
    journalEntries.value = await loadWorkflowJournal(props.workflowId);
  } catch (err) {
    error.value = 'Failed to load workflow journal';
    console.error('[WorkflowDetailModal] Failed to load journal:', err);
  } finally {
    loading.value = false;
  }
};

/**
 * Get short type name (class name only, without package)
 */
const getShortType = (type: string): string => {
  const parts = type.split('.');
  return parts[parts.length - 1];
};

/**
 * Format date for display
 */
const formatDate = (dateString: string): string => {
  const date = new Date(dateString);
  return date.toLocaleString();
};

onMounted(() => {
  loadJournal();
});
</script>
