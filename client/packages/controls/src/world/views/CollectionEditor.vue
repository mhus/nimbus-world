<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button class="btn btn-ghost gap-2" @click="handleBack">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to List
        </button>
      </div>
      <h2 class="text-2xl font-bold">
        {{ isNew ? 'Create New Collection' : 'Edit Collection' }}
      </h2>
    </div>

    <!-- Error State -->
    <div v-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Edit Form -->
    <div class="space-y-6">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Collection Information</h3>
          <form @submit.prevent="handleSave" class="space-y-4">
            <!-- World ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Collection ID (World ID)</span>
              </label>
              <input
                v-model="formData.worldId"
                type="text"
                placeholder="Enter collection ID (must start with '@', e.g., @minecraft)"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
                pattern="@.*"
                title="Collection ID must start with '@'"
              />
              <label class="label">
                <span class="label-text-alt">Unique identifier for this collection (must start with '@')</span>
              </label>
            </div>

            <!-- Title -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Title</span>
              </label>
              <input
                v-model="formData.title"
                type="text"
                placeholder="Enter collection title"
                class="input input-bordered w-full"
                required
              />
              <label class="label">
                <span class="label-text-alt">Display name for this collection</span>
              </label>
            </div>

            <!-- Description -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Description</span>
              </label>
              <textarea
                v-model="formData.description"
                placeholder="Enter collection description"
                class="textarea textarea-bordered w-full"
                rows="3"
              ></textarea>
              <label class="label">
                <span class="label-text-alt">Optional description of what this collection contains</span>
              </label>
            </div>

            <!-- Action Buttons -->
            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- Success Message -->
    <div v-if="successMessage" class="alert alert-success">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
      <span>{{ successMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { collectionServiceFrontend, type Collection } from '../services/CollectionServiceFrontend';

const props = defineProps<{
  collection: Collection | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const isNew = computed(() => props.collection === 'new');

const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  worldId: '',
  title: '',
  description: '',
});

const loadCollection = () => {
  if (isNew.value) {
    formData.value = {
      worldId: '@',
      title: '',
      description: '',
    };
    return;
  }

  // Load from props
  const collection = props.collection as Collection;
  formData.value = {
    worldId: collection.worldId,
    title: collection.title,
    description: collection.description || '',
  };
};

const handleSave = async () => {
  // Validate worldId starts with '@'
  if (!formData.value.worldId.startsWith('@')) {
    error.value = 'Collection ID must start with "@"';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    const request = {
      worldId: formData.value.worldId,
      title: formData.value.title,
      description: formData.value.description,
    };

    if (isNew.value) {
      await collectionServiceFrontend.createCollection(request);
      successMessage.value = 'Collection created successfully';
    } else {
      const collection = props.collection as Collection;
      await collectionServiceFrontend.updateCollection(collection.worldId, request);
      successMessage.value = 'Collection updated successfully';
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save collection';
    console.error('[CollectionEditor] Failed to save collection:', e);
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadCollection();
});
</script>
