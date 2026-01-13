<template>
  <TransitionRoot :show="true" as="template">
    <Dialog as="div" class="relative z-50" @close="emit('close')">
      <TransitionChild
        as="template"
        enter="ease-out duration-300"
        enter-from="opacity-0"
        enter-to="opacity-100"
        leave="ease-in duration-200"
        leave-from="opacity-100"
        leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-black bg-opacity-25" />
      </TransitionChild>

      <div class="fixed inset-0 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="ease-out duration-300"
            enter-from="opacity-0 scale-95"
            enter-to="opacity-100 scale-100"
            leave="ease-in duration-200"
            leave-from="opacity-100 scale-100"
            leave-to="opacity-0 scale-95"
          >
            <DialogPanel class="w-full max-w-3xl transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                {{ isEditMode ? 'View/Edit Chest' : 'Create Chest' }}
              </DialogTitle>

              <!-- Error Alert -->
              <div v-if="error" class="alert alert-error mb-4">
                <svg class="stroke-current flex-shrink-0 h-5 w-5" fill="none" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ error }}</span>
              </div>

              <div class="space-y-4">
                <!-- Name (Required) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Name *</span>
                  </label>
                  <input
                    v-model="formData.name"
                    type="text"
                    class="input input-bordered"
                    placeholder="Enter unique name..."
                    :disabled="isEditMode"
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">
                      {{ isEditMode ? 'Name cannot be changed' : 'Unique identifier (e.g., UUID)' }}
                    </span>
                  </label>
                </div>

                <!-- Display Name -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Display Name</span>
                  </label>
                  <input
                    v-model="formData.displayName"
                    type="text"
                    class="input input-bordered"
                    placeholder="Enter display name..."
                  />
                </div>

                <!-- Description -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Description</span>
                  </label>
                  <textarea
                    v-model="formData.description"
                    class="textarea textarea-bordered h-24"
                    placeholder="Enter description..."
                  ></textarea>
                </div>

                <!-- Type (Required) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Type *</span>
                  </label>
                  <select
                    v-model="formData.type"
                    class="select select-bordered"
                    :disabled="isEditMode"
                  >
                    <option value="">Select type...</option>
                    <option value="REGION">Region Chest</option>
                    <option value="WORLD">World Chest</option>
                    <option value="USER">User Chest</option>
                  </select>
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">
                      {{ isEditMode ? 'Type cannot be changed' : 'Determines access scope' }}
                    </span>
                  </label>
                </div>

                <!-- World ID (for WORLD type) -->
                <div v-if="formData.type === 'WORLD'" class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">World {{ formData.type === 'WORLD' ? '*' : '' }}</span>
                  </label>
                  <select
                    v-model="formData.worldId"
                    class="select select-bordered"
                  >
                    <option value="">Select world...</option>
                    <option v-for="world in worlds" :key="world.worldId" :value="world.worldId">
                      {{ world.publicData?.name || world.worldId }}
                    </option>
                  </select>
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Required for WORLD type chests</span>
                  </label>
                </div>

                <!-- User ID (for USER type) -->
                <div v-if="formData.type === 'USER'" class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">User {{ formData.type === 'USER' ? '*' : '' }}</span>
                  </label>
                  <select
                    v-model="formData.userId"
                    class="select select-bordered"
                    :disabled="loadingUsers"
                  >
                    <option value="">{{ loadingUsers ? 'Loading users...' : 'Select user...' }}</option>
                    <option v-for="user in users" :key="user.id" :value="user.id">
                      {{ user.publicData?.displayName || user.username }} ({{ user.username }})
                    </option>
                  </select>
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Required for USER type chests</span>
                  </label>
                </div>

                <!-- Items (View Only in Edit Mode) -->
                <div v-if="isEditMode && chest" class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Items ({{ chest.items?.length || 0 }})</span>
                  </label>
                  <div v-if="chest.items && chest.items.length > 0" class="max-h-48 overflow-y-auto border rounded p-2">
                    <div v-for="(itemRef, index) in chest.items" :key="itemRef.itemId" class="p-2 border-b last:border-0">
                      <div class="flex justify-between items-start">
                        <div class="flex-1">
                          <div class="font-mono text-sm">{{ itemRef.itemId }}</div>
                          <div v-if="itemRef.name" class="text-xs">{{ itemRef.name }}</div>
                          <div class="flex gap-2 items-center mt-1">
                            <span v-if="itemRef.amount" class="badge badge-sm">x{{ itemRef.amount }}</span>
                            <span v-if="itemRef.texture" class="text-xs text-base-content/60">{{ itemRef.texture }}</span>
                          </div>
                        </div>
                        <button
                          class="btn btn-xs btn-error"
                          @click="handleRemoveItem(itemRef.itemId)"
                          title="Remove item"
                        >
                          <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      </div>
                    </div>
                  </div>
                  <div v-else class="text-center py-4 text-base-content/50 text-sm">
                    No items in this chest
                  </div>
                </div>

                <!-- Metadata (Edit Mode) -->
                <div v-if="isEditMode && chest" class="p-4 bg-base-200 rounded">
                  <div class="text-sm font-semibold mb-2">Metadata</div>
                  <div class="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <span class="text-base-content/60">Region ID:</span>
                      <span class="ml-2 font-mono text-xs">{{ chest.regionId }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">Created:</span>
                      <span class="ml-2 text-xs">{{ formatDate(chest.createdAt) }}</span>
                    </div>
                    <div v-if="chest.worldId">
                      <span class="text-base-content/60">World ID:</span>
                      <span class="ml-2 font-mono text-xs">{{ chest.worldId }}</span>
                    </div>
                    <div v-if="chest.updatedAt">
                      <span class="text-base-content/60">Updated:</span>
                      <span class="ml-2 text-xs">{{ formatDate(chest.updatedAt) }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Actions -->
              <div class="flex justify-end gap-3 mt-6">
                <button
                  class="btn btn-ghost"
                  @click="emit('close')"
                  :disabled="saving"
                >
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  @click="handleSave"
                  :disabled="saving || !isValid"
                >
                  <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                  {{ isEditMode ? 'Update' : 'Create' }}
                </button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import type { WChest, ChestType } from '@shared/generated/entities/WChest';
import type { RUser } from '@shared/generated/entities/RUser';
import { chestService, type ChestRequest } from '@/services/ChestService';
import { useWorld } from '@/composables/useWorld';
import { apiService } from '@/services/ApiService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ChestDialog');

interface Props {
  regionId: string;
  chest?: WChest | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const isEditMode = computed(() => !!props.chest);

const formData = ref<ChestRequest>({
  name: '',
  displayName: '',
  description: '',
  worldId: '',
  userId: '',
  type: '' as ChestType,
});

const error = ref<string | null>(null);
const saving = ref(false);

// Load worlds for region
const { worlds, loadWorlds } = useWorld();

// Load users
const users = ref<RUser[]>([]);
const loadingUsers = ref(false);

/**
 * Load users from RUserController
 */
const loadUsers = async () => {
  loadingUsers.value = true;
  try {
    const response = await apiService.get<RUser[]>(`/control/users`);
    users.value = response;
    logger.info('Loaded users', { count: users.value.length });
  } catch (err) {
    logger.error('Failed to load users', err as Error);
    users.value = [];
  } finally {
    loadingUsers.value = false;
  }
};

/**
 * Validate form
 */
const isValid = computed(() => {
  if (!formData.value.name || !formData.value.type) {
    return false;
  }

  // Validate type-specific requirements
  if (formData.value.type === 'WORLD' && !formData.value.worldId) {
    return false;
  }

  if (formData.value.type === 'USER' && !formData.value.userId) {
    return false;
  }

  return true;
});

/**
 * Handle save
 */
const handleSave = async () => {
  if (!isValid.value) {
    error.value = 'Please fill in all required fields';
    return;
  }

  saving.value = true;
  error.value = null;

  try {
    if (isEditMode.value && props.chest) {
      // Update existing chest
      await chestService.updateChest(props.regionId, props.chest.name, {
        displayName: formData.value.displayName,
        description: formData.value.description,
        // Note: name, type, worldId, userId cannot be changed in edit mode
      });
      logger.info('Updated chest', { name: props.chest.name });
    } else {
      // Create new chest
      await chestService.createChest(props.regionId, formData.value);
      logger.info('Created chest', { name: formData.value.name });
    }

    emit('saved');
  } catch (err) {
    error.value = isEditMode.value ? 'Failed to update chest' : 'Failed to create chest';
    logger.error(isEditMode.value ? 'Failed to update chest' : 'Failed to create chest', err as Error);
  } finally {
    saving.value = false;
  }
};

/**
 * Handle remove item
 */
const handleRemoveItem = async (itemId: string) => {
  if (!props.chest) return;

  if (!confirm(`Are you sure you want to remove this item?`)) {
    return;
  }

  try {
    await chestService.removeItem(props.regionId, props.chest.name, itemId);
    logger.info('Removed item from chest', { chestName: props.chest.name, itemId });
    emit('saved'); // Trigger reload
  } catch (err) {
    error.value = 'Failed to remove item';
    logger.error('Failed to remove item', { chestName: props.chest.name, itemId }, err as Error);
  }
};

/**
 * Format date
 */
const formatDate = (date: string | undefined): string => {
  if (!date) return '-';
  return new Date(date).toLocaleString();
};

/**
 * Generate UUID for name
 */
const generateUuid = (): string => {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.random() * 16 | 0;
    const v = c === 'x' ? r : (r & 0x3 | 0x8);
    return v.toString(16);
  });
};

// Watch type changes to load worlds/users
watch(() => formData.value.type, (newType) => {
  if (newType === 'WORLD' && worlds.value.length === 0) {
    loadWorlds('mainOnly');
  } else if (newType === 'USER' && users.value.length === 0) {
    loadUsers();
  }
});

onMounted(() => {
  if (props.chest) {
    // Populate form with chest data
    formData.value = {
      name: props.chest.name,
      displayName: props.chest.displayName || '',
      description: props.chest.description || '',
      worldId: props.chest.worldId || '',
      userId: props.chest.userId || '',
      type: props.chest.type,
    };

    // Load worlds/users if needed for existing chest
    if (props.chest.type === 'WORLD') {
      loadWorlds('mainOnly');
    } else if (props.chest.type === 'USER') {
      loadUsers();
    }
  } else {
    // Generate UUID for new chest name
    formData.value.name = generateUuid();
  }
});
</script>
