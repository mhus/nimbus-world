<template>
  <div class="space-y-4">
    <!-- Header with Collection Filter and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex gap-2 flex-1">
        <input
          v-model="selectedCollection"
          type="text"
          placeholder="Filter by collection (e.g., lore, quests) - optional"
          class="input input-bordered flex-1"
          @keyup.enter="handleLoadDocuments"
        />
        <button class="btn btn-secondary" @click="handleLoadDocuments">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 4a1 1 0 011-1h16a1 1 0 011 1v2.586a1 1 0 01-.293.707l-6.414 6.414a1 1 0 00-.293.707V17l-4 4v-6.586a1 1 0 00-.293-.707L3.293 7.293A1 1 0 013 6.586V4z" />
          </svg>
          Filter
        </button>
      </div>
      <button
        class="btn btn-primary"
        @click="openCreateDialog"
        :disabled="!selectedCollection"
      >
        <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        New Document
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Empty State -->
    <div v-else-if="!loading && documents.length === 0" class="text-center py-12">
      <svg class="w-16 h-16 mx-auto text-base-content/30 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      </svg>
      <p class="text-base-content/70 text-lg">
        {{ selectedCollection ? `No documents found in collection "${selectedCollection}"` : 'No documents found' }}
      </p>
      <p class="text-base-content/50 text-sm mt-2">Create your first document to get started</p>
    </div>

    <!-- Document List -->
    <div v-else-if="documents.length > 0" class="overflow-x-auto">
      <table class="table table-zebra w-full">
        <thead>
          <tr>
            <th>Title</th>
            <th>Name</th>
            <th>Type</th>
            <th>Language</th>
            <th>Format</th>
            <th>Updated</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="doc in documents"
            :key="doc.documentId"
            class="cursor-pointer hover:bg-base-200"
            @click="openEditDialog(doc)"
          >
            <td>
              <div class="font-semibold">{{ doc.title || '(no title)' }}</div>
              <div v-if="doc.summary" class="text-xs text-base-content/60 mt-1">{{ doc.summary }}</div>
            </td>
            <td><code class="text-xs">{{ doc.name }}</code></td>
            <td>
              <span v-if="doc.type" class="badge badge-sm">{{ doc.type }}</span>
            </td>
            <td>
              <span v-if="doc.language" class="badge badge-sm badge-ghost">{{ doc.language }}</span>
            </td>
            <td>
              <span v-if="doc.format" class="badge badge-sm badge-outline">{{ doc.format }}</span>
            </td>
            <td>
              <span class="text-xs">{{ formatDate(doc.updatedAt) }}</span>
            </td>
            <td @click.stop>
              <div class="flex gap-2">
                <button
                  class="btn btn-sm btn-ghost tooltip"
                  @click="handleGenerateSummary(doc)"
                  data-tip="Generate AI Summary"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
                  </svg>
                </button>
                <button class="btn btn-sm btn-ghost tooltip" @click="openEditDialog(doc)" data-tip="Edit">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button class="btn btn-sm btn-ghost btn-error tooltip" @click="handleDeleteClick(doc)" data-tip="Delete">
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

    <!-- Editor Dialog -->
    <div v-if="showEditorDialog" class="modal modal-open" @click.self="closeEditorDialog">
      <div class="modal-box max-w-4xl">
        <h3 class="font-bold text-lg mb-4">{{ editingDocument ? 'Edit Document' : 'New Document' }}</h3>

        <div class="space-y-4">
          <!-- Title -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Title</span>
            </label>
            <input v-model="formData.title" type="text" placeholder="Document title" class="input input-bordered" />
          </div>

          <!-- Name -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Name (technical)</span>
            </label>
            <input v-model="formData.name" type="text" placeholder="Optional technical name" class="input input-bordered" />
          </div>

          <!-- Type, Language, Format -->
          <div class="grid grid-cols-3 gap-4">
            <div class="form-control">
              <label class="label">
                <span class="label-text">Type</span>
              </label>
              <input v-model="formData.type" type="text" placeholder="e.g., lore, quest" class="input input-bordered" />
            </div>
            <div class="form-control">
              <label class="label">
                <span class="label-text">Language</span>
              </label>
              <input v-model="formData.language" type="text" placeholder="e.g., en, de" class="input input-bordered" />
            </div>
            <div class="form-control">
              <label class="label">
                <span class="label-text">Format</span>
              </label>
              <select v-model="formData.format" class="select select-bordered">
                <option value="plaintext">Plain Text</option>
                <option value="markdown">Markdown</option>
              </select>
            </div>
          </div>

          <!-- Summary -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Summary</span>
            </label>
            <input v-model="formData.summary" type="text" placeholder="Short summary" class="input input-bordered" />
          </div>

          <!-- Content -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Content</span>
            </label>
            <textarea
              v-model="formData.content"
              class="textarea textarea-bordered h-64 font-mono"
              placeholder="Document content..."
            ></textarea>
          </div>

          <!-- IsMain Checkbox -->
          <div class="form-control">
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="formData.isMain" type="checkbox" class="checkbox" />
              <span class="label-text">Is Main Document</span>
            </label>
          </div>
        </div>

        <div class="modal-action">
          <button class="btn btn-ghost" @click="closeEditorDialog">Cancel</button>
          <button class="btn btn-primary" @click="saveDocument" :disabled="saving">
            <span v-if="saving" class="loading loading-spinner loading-sm"></span>
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Dialog -->
    <div v-if="showDeleteConfirmation" class="modal modal-open" @click.self="cancelDelete">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Delete Document</h3>
        <p class="py-4">
          Are you sure you want to delete document
          <strong>"{{ documentToDelete?.title || documentToDelete?.documentId }}"</strong>?
        </p>
        <p class="text-sm text-warning pb-4">
          This action cannot be undone.
        </p>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="cancelDelete">Cancel</button>
          <button class="btn btn-error" @click="confirmDelete" :disabled="deleting">
            <span v-if="deleting" class="loading loading-spinner loading-sm"></span>
            {{ deleting ? 'Deleting...' : 'Delete' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Job Watch Dialog -->
    <JobWatch
      v-if="watchingJobId && currentWorldId"
      :world-id="currentWorldId"
      :job-id="watchingJobId"
      @close="handleJobWatchClose"
      @completed="handleJobCompleted"
      @failed="handleJobFailed"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useDocuments } from '@/composables/useDocuments';
import { useJobs, type Job, type JobCreateRequest } from '@/composables/useJobs';
import type { DocumentMetadata } from '@/services/DocumentService';
import JobWatch from '@components/JobWatch.vue';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('DocumentEditor');

interface FormData {
  title: string;
  name: string;
  type: string;
  language: string;
  format: string;
  summary: string;
  content: string;
  isMain: boolean;
}

const { currentWorldId } = useWorld();

// Composable
const documentsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useDocuments(currentWorldId.value);
});

const documents = computed(() => documentsComposable.value?.documents.value || []);
const loading = computed(() => documentsComposable.value?.loading.value || false);
const error = computed(() => documentsComposable.value?.error.value || null);

// State
const selectedCollection = ref('');

// Editor dialog
const showEditorDialog = ref(false);
const editingDocument = ref<DocumentMetadata | null>(null);
const saving = ref(false);
const formData = ref<FormData>({
  title: '',
  name: '',
  type: '',
  language: '',
  format: 'plaintext',
  summary: '',
  content: '',
  isMain: true,
});

// Delete confirmation
const showDeleteConfirmation = ref(false);
const documentToDelete = ref<DocumentMetadata | null>(null);
const deleting = ref(false);

// Job watching
const watchingJobId = ref<string | null>(null);
const jobsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useJobs(currentWorldId.value);
});

// Load documents (with optional collection filter)
const handleLoadDocuments = async () => {
  if (!documentsComposable.value) {
    logger.warn('handleLoadDocuments: documentsComposable is null');
    return;
  }
  logger.info('handleLoadDocuments: starting with collection=' + (selectedCollection.value || 'null'), {
    worldId: currentWorldId.value,
    collection: selectedCollection.value
  });
  await documentsComposable.value.loadDocuments(selectedCollection.value || undefined);
  logger.info('handleLoadDocuments: completed');
};

// Auto-load documents when worldId becomes available
watch(currentWorldId, (newWorldId, oldWorldId) => {
  logger.info('DocumentEditor: currentWorldId changed', {
    oldValue: oldWorldId,
    newValue: currentWorldId.value,
    hasComposable: !!documentsComposable.value
  });

  if (currentWorldId.value) {
    logger.info('DocumentEditor: worldId is now available, auto-loading documents');
    handleLoadDocuments();
  }
}, { immediate: true }); // immediate: true triggers on mount as well

// Create dialog
const openCreateDialog = () => {
  editingDocument.value = null;
  formData.value = {
    title: '',
    name: '',
    type: '',
    language: '',
    format: 'plaintext',
    summary: '',
    content: '',
    isMain: true,
  };
  showEditorDialog.value = true;
};

// Edit dialog
const openEditDialog = async (doc: DocumentMetadata) => {
  editingDocument.value = doc;

  // Load full document with content
  if (!documentsComposable.value) {
    return;
  }

  const fullDoc = await documentsComposable.value.getDocument(doc.collection, doc.documentId);

  formData.value = {
    title: doc.title || '',
    name: doc.name || '',
    type: doc.type || '',
    language: doc.language || '',
    format: doc.format || 'plaintext',
    summary: doc.summary || '',
    content: fullDoc?.content || '',
    isMain: doc.isMain,
  };
  showEditorDialog.value = true;
};

// Close editor dialog
const closeEditorDialog = () => {
  showEditorDialog.value = false;
  editingDocument.value = null;
};

// Save document
const saveDocument = async () => {
  if (!documentsComposable.value || !selectedCollection.value) {
    return;
  }

  saving.value = true;

  try {
    if (editingDocument.value) {
      // Update existing document
      await documentsComposable.value.updateDocument(
        selectedCollection.value,
        editingDocument.value.documentId,
        formData.value
      );
    } else {
      // Create new document
      await documentsComposable.value.createDocument({
        collection: selectedCollection.value,
        ...formData.value,
      });
    }

    closeEditorDialog();
  } catch (e: any) {
    console.error('Error saving document:', e);
  } finally {
    saving.value = false;
  }
};

// Delete
const handleDeleteClick = (doc: DocumentMetadata) => {
  documentToDelete.value = doc;
  showDeleteConfirmation.value = true;
};

const cancelDelete = () => {
  showDeleteConfirmation.value = false;
  documentToDelete.value = null;
};

const confirmDelete = async () => {
  if (!documentsComposable.value || !documentToDelete.value) {
    return;
  }

  deleting.value = true;

  try {
    await documentsComposable.value.deleteDocument(
      documentToDelete.value.collection,
      documentToDelete.value.documentId
    );

    showDeleteConfirmation.value = false;
    documentToDelete.value = null;
  } catch (e: any) {
    console.error('Error deleting document:', e);
  } finally {
    deleting.value = false;
  }
};

// Format date
const formatDate = (dateString: string): string => {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleString();
};

// Generate AI Summary
const handleGenerateSummary = async (doc: DocumentMetadata) => {
  if (!jobsComposable.value || !currentWorldId.value) {
    logger.warn('Cannot generate summary: missing jobsComposable or worldId');
    return;
  }

  try {
    logger.info('Creating AI summary generation job', {
      worldId: currentWorldId.value,
      collection: doc.collection,
      documentId: doc.documentId
    });

    // Create job request
    const jobRequest: JobCreateRequest = {
      executor: 'document-summary',
      type: 'document-summary',
      parameters: {
        collection: doc.collection,
        documentId: doc.documentId,
        aiModel: 'default:chat',
        maxTokens: '200',
        temperature: '0.7'
      },
      priority: 5,
      maxRetries: 1
    };

    // Create job via API
    const createdJob = await jobsComposable.value.createJob(jobRequest);
    logger.info('Job created successfully', { jobId: createdJob.id });

    // Start watching the job
    watchingJobId.value = createdJob.id;
  } catch (err: any) {
    logger.error('Failed to create summary generation job', err);
    alert(`Failed to create job: ${err.message || 'Unknown error'}`);
  }
};

// Job watch handlers
const handleJobWatchClose = () => {
  watchingJobId.value = null;
};

const handleJobCompleted = (job: Job) => {
  logger.info('Job completed successfully', { jobId: job.id, result: job.resultData });
  watchingJobId.value = null;

  // Reload documents to show updated summary
  handleLoadDocuments();
};

const handleJobFailed = (job: Job) => {
  logger.error('Job failed', { jobId: job.id, error: job.errorMessage });
  watchingJobId.value = null;

  // Still reload in case partial work was done
  handleLoadDocuments();
};
</script>
