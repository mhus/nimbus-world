<template>
  <div class="space-y-6">
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
        {{ isNew ? 'Create New Region' : 'Edit Region' }}
      </h2>
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

    <!-- Edit Form -->
    <div v-else class="card bg-base-100 shadow-xl">
      <div class="card-body">
        <form @submit.prevent="handleSave" class="space-y-4">
          <!-- Name -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Region Name</span>
            </label>
            <input
              v-model="formData.name"
              type="text"
              placeholder="Enter region name"
              class="input input-bordered w-full"
              required
            />
          </div>

          <!-- Enabled Status -->
          <div v-if="!isNew" class="form-control">
            <label class="label cursor-pointer justify-start gap-4">
              <span class="label-text font-medium">Enabled</span>
              <input
                v-model="formData.enabled"
                type="checkbox"
                class="toggle toggle-success"
              />
            </label>
            <label class="label">
              <span class="label-text-alt">Enable or disable this region</span>
            </label>
          </div>

          <!-- Maintainers -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Maintainers</span>
            </label>
            <input
              v-model="formData.maintainers"
              type="text"
              placeholder="Enter maintainer IDs separated by commas (e.g., user1, user2)"
              class="input input-bordered w-full"
            />
            <label class="label">
              <span class="label-text-alt">Comma-separated list of user IDs with maintainer rights</span>
            </label>
          </div>

          <!-- Current Maintainers (for existing regions) -->
          <div v-if="!isNew && currentMaintainers.length > 0" class="form-control">
            <label class="label">
              <span class="label-text font-medium">Current Maintainers</span>
            </label>
            <div class="flex flex-wrap gap-2">
              <div
                v-for="maintainer in currentMaintainers"
                :key="maintainer"
                class="badge badge-lg badge-outline gap-2"
              >
                {{ maintainer }}
                <button
                  type="button"
                  class="btn btn-ghost btn-xs"
                  @click="handleRemoveMaintainer(maintainer)"
                >
                  <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- Quick Add Maintainer -->
          <div v-if="!isNew" class="form-control">
            <label class="label">
              <span class="label-text font-medium">Add Maintainer</span>
            </label>
            <div class="flex gap-2">
              <input
                v-model="newMaintainerId"
                type="text"
                placeholder="Enter user ID"
                class="input input-bordered flex-1"
              />
              <button
                type="button"
                class="btn btn-secondary"
                @click="handleAddMaintainer"
                :disabled="!newMaintainerId.trim()"
              >
                Add
              </button>
            </div>
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
import { regionService, type Region } from '../services/RegionService';

const props = defineProps<{
  regionId: string;
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const isNew = computed(() => props.regionId === 'new');

const region = ref<Region | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);
const newMaintainerId = ref('');

const formData = ref({
  name: '',
  enabled: true,
  maintainers: '',
});

const currentMaintainers = computed(() => {
  return region.value?.maintainers || [];
});

const loadRegion = async () => {
  if (isNew.value) {
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    region.value = await regionService.getRegion(props.regionId);
    formData.value = {
      name: region.value.name,
      enabled: region.value.enabled,
      maintainers: region.value.maintainers.join(', '),
    };
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load region';
    console.error('Failed to load region:', e);
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    const request = {
      name: formData.value.name,
      maintainers: formData.value.maintainers,
    };

    if (isNew.value) {
      await regionService.createRegion(request);
      successMessage.value = 'Region created successfully';
    } else {
      await regionService.updateRegion(props.regionId, request, formData.value.enabled);
      successMessage.value = 'Region updated successfully';
      await loadRegion();
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save region';
    console.error('Failed to save region:', e);
  } finally {
    saving.value = false;
  }
};

const handleAddMaintainer = async () => {
  if (!newMaintainerId.value.trim()) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    await regionService.addMaintainer(props.regionId, newMaintainerId.value.trim());
    successMessage.value = `Maintainer "${newMaintainerId.value}" added successfully`;
    newMaintainerId.value = '';
    await loadRegion();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to add maintainer';
    console.error('Failed to add maintainer:', e);
  } finally {
    saving.value = false;
  }
};

const handleRemoveMaintainer = async (userId: string) => {
  if (!confirm(`Are you sure you want to remove maintainer "${userId}"?`)) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    await regionService.removeMaintainer(props.regionId, userId);
    successMessage.value = `Maintainer "${userId}" removed successfully`;
    await loadRegion();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to remove maintainer';
    console.error('Failed to remove maintainer:', e);
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  if (!isNew.value) {
    loadRegion();
  }
});
</script>
