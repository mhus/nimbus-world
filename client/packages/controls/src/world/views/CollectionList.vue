<template>
  <div class="space-y-4">
    <!-- Header with Search and Actions -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search collections by title or worldId..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
      <div class="flex gap-2">
        <button class="btn btn-secondary" @click="handleCreateShared">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
          </svg>
          Add Shared
        </button>
        <button class="btn btn-primary" @click="handleCreate">
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Collection
        </button>
      </div>
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

    <!-- Empty State -->
    <div v-else-if="!loading && filteredCollections.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No collections found</p>
      <p class="text-base-content/50 text-sm mt-2">Create your first collection to get started</p>
    </div>

    <!-- Collections Grid -->
    <div v-else>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
        <div
          v-for="collection in paginatedCollections"
          :key="collection.worldId"
          class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer"
          @click="handleSelect(collection.worldId)"
        >
          <div class="card-body p-4">
            <h3 class="card-title text-base truncate" :title="collection.title">
              {{ collection.title }}
            </h3>
            <div class="space-y-2">
              <div class="text-xs text-base-content/70 truncate" :title="collection.worldId">
                ID: {{ collection.worldId }}
              </div>
              <div class="flex items-center gap-2">
                <span
                  class="badge badge-sm"
                  :class="collection.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ collection.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </div>
              <div v-if="collection.description" class="text-xs text-base-content/70 line-clamp-2">
                {{ collection.description }}
              </div>
            </div>
            <div class="card-actions justify-end mt-2">
              <button
                class="btn btn-ghost btn-xs"
                @click.stop="handleSelect(collection.worldId)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                class="btn btn-ghost btn-xs text-error"
                @click.stop="handleDelete(collection.worldId, collection.title)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredCollections.length) }} of {{ filteredCollections.length }} collections
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-sm"
            :disabled="!hasPreviousPage"
            @click="handlePreviousPage"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
            Previous
          </button>
          <div class="flex items-center gap-2 px-4">
            <span class="text-sm">Page {{ currentPage }} of {{ totalPages }}</span>
          </div>
          <button
            class="btn btn-sm"
            :disabled="!hasNextPage"
            @click="handleNextPage"
          >
            Next
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Add Shared Collection Dialog -->
    <dialog ref="sharedDialog" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Add Shared Collection</h3>
        <p class="py-2 text-sm text-base-content/70">Create a new shared collection with format @shared:NAME</p>

        <!-- Dialog Form -->
        <form @submit.prevent="handleCreateSharedSubmit" class="space-y-4 mt-4">
          <!-- Name Field -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Name</span>
            </label>
            <input
              v-model="sharedForm.name"
              type="text"
              placeholder="Enter collection name (e.g., minecraft)"
              class="input input-bordered w-full"
              required
              pattern="[a-z0-9\-_]+"
              title="Only lowercase letters, numbers, hyphens and underscores allowed"
            />
            <label class="label">
              <span class="label-text-alt">Will create: @shared:{{ sharedForm.name || 'NAME' }}</span>
            </label>
          </div>

          <!-- Title Field -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Title</span>
            </label>
            <input
              v-model="sharedForm.title"
              type="text"
              placeholder="Enter display title"
              class="input input-bordered w-full"
              required
            />
            <label class="label">
              <span class="label-text-alt">Display name for this collection</span>
            </label>
          </div>

          <!-- Description Field -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Description (Optional)</span>
            </label>
            <textarea
              v-model="sharedForm.description"
              placeholder="Enter description"
              class="textarea textarea-bordered w-full"
              rows="3"
            ></textarea>
          </div>

          <!-- Error in Dialog -->
          <div v-if="dialogError" class="alert alert-error">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
            <span>{{ dialogError }}</span>
          </div>

          <!-- Dialog Actions -->
          <div class="modal-action">
            <button type="button" class="btn" @click="handleCloseDialog">Cancel</button>
            <button type="submit" class="btn btn-primary" :disabled="dialogSaving">
              <span v-if="dialogSaving" class="loading loading-spinner loading-sm"></span>
              <span v-else>Create</span>
            </button>
          </div>
        </form>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button @click="handleCloseDialog">close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { collectionServiceFrontend, type Collection } from '../services/CollectionServiceFrontend';

const emit = defineEmits<{
  select: [collection: Collection];
  create: [];
}>();

const collections = ref<Collection[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');

// Dialog state
const sharedDialog = ref<HTMLDialogElement | null>(null);
const sharedForm = ref({
  name: '',
  title: '',
  description: '',
});
const dialogError = ref<string | null>(null);
const dialogSaving = ref(false);

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

const filteredCollections = computed(() => {
  let result = collections.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(c =>
      c.title.toLowerCase().includes(query) ||
      c.worldId.toLowerCase().includes(query) ||
      (c.description && c.description.toLowerCase().includes(query))
    );
  }

  return result;
});

const paginatedCollections = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredCollections.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredCollections.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const loadCollections = async () => {
  loading.value = true;
  error.value = null;

  try {
    console.log('[CollectionList] Loading collections');
    collections.value = await collectionServiceFrontend.listCollections();
    console.log('[CollectionList] Loaded collections:', collections.value.length);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load collections';
    console.error('[CollectionList] Failed to load collections:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
};

const handleCreate = () => {
  emit('create');
};

const handleCreateShared = () => {
  // Reset form
  sharedForm.value = {
    name: '',
    title: '',
    description: '',
  };
  dialogError.value = null;
  dialogSaving.value = false;

  // Open dialog
  sharedDialog.value?.showModal();
};

const handleCloseDialog = () => {
  sharedDialog.value?.close();
};

const handleCreateSharedSubmit = async () => {
  // Validate name
  if (!sharedForm.value.name) {
    dialogError.value = 'Name is required';
    return;
  }

  // Validate name format
  if (!/^[a-z0-9\-_]+$/.test(sharedForm.value.name)) {
    dialogError.value = 'Name must contain only lowercase letters, numbers, hyphens and underscores';
    return;
  }

  if (!sharedForm.value.title) {
    dialogError.value = 'Title is required';
    return;
  }

  dialogSaving.value = true;
  dialogError.value = null;

  try {
    const worldId = `@shared:${sharedForm.value.name}`;

    const request = {
      worldId: worldId,
      title: sharedForm.value.title,
      description: sharedForm.value.description,
    };

    await collectionServiceFrontend.createCollection(request);

    // Close dialog and reload collections
    handleCloseDialog();
    await loadCollections();
  } catch (e) {
    dialogError.value = e instanceof Error ? e.message : 'Failed to create shared collection';
    console.error('[CollectionList] Failed to create shared collection:', e);
  } finally {
    dialogSaving.value = false;
  }
};

const handleSelect = (worldId: string) => {
  const collection = collections.value.find(c => c.worldId === worldId);
  if (collection) {
    emit('select', collection);
  }
};

const handleDelete = async (worldId: string, title: string) => {
  if (!confirm(`Are you sure you want to delete collection "${title}"?`)) {
    return;
  }

  try {
    await collectionServiceFrontend.deleteCollection(worldId);
    await loadCollections();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete collection';
    console.error('[CollectionList] Failed to delete collection:', e);
  }
};

const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
  }
};

const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
  }
};

onMounted(() => {
  loadCollections();
});
</script>
