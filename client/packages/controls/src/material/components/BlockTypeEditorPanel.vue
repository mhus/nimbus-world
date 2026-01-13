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
            <DialogPanel class="w-full max-w-4xl transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                {{ isCreate ? 'Create Block Type' : `Edit Block Type #${formData.id}` }}
              </DialogTitle>

              <div class="space-y-6 max-h-[70vh] overflow-y-auto pr-2">
                <!-- Basic Properties -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">ID</span>
                    <span class="label-text-alt text-error" v-if="isCreate && !formData.id">Required</span>
                  </label>
                  <input
                    v-model="formData.id"
                    type="text"
                    class="input input-bordered"
                    :disabled="!isCreate"
                    placeholder="e.g., w:123"
                    required
                  />
                  <label v-if="isCreate" class="label">
                    <span class="label-text-alt">Use format: group:name</span>
                  </label>
                </div>

                <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <!-- Title -->
                  <div class="form-control">
                    <label class="label">
                      <span class="label-text font-semibold">Title</span>
                    </label>
                    <input
                      v-model="formData.title"
                      type="text"
                      class="input input-bordered"
                      placeholder="Enter display title..."
                    />
                    <label class="label">
                      <span class="label-text-alt">Display name for this block type</span>
                    </label>
                  </div>

                  <!-- Type -->
                  <div class="form-control">
                    <label class="label">
                      <span class="label-text font-semibold">Type</span>
                    </label>
                    <select
                      v-model="formData.type"
                      class="select select-bordered w-full"
                    >
                      <option :value="undefined">-- Not specified --</option>
                      <option v-for="(value, name) in BlockTypeType" :key="name" :value="value">
                        {{ name }} ({{ value }})
                      </option>
                    </select>
                    <label class="label">
                      <span class="label-text-alt">Category of this block type</span>
                    </label>
                  </div>
                </div>

                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Description</span>
                  </label>
                  <input
                    v-model="formData.description"
                    type="text"
                    class="input input-bordered"
                    placeholder="Enter block type description..."
                  />
                </div>

                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Initial Status</span>
                  </label>
                  <input
                    v-model.number="formData.initialStatus"
                    type="number"
                    class="input input-bordered"
                    placeholder="0"
                  />
                </div>

                <!-- Status Modifiers -->
                <div class="divider">Status Modifiers</div>

                <div class="space-y-3">
                  <div
                    v-for="status in statusList"
                    :key="status"
                    class="card bg-base-200 hover:shadow-md transition-shadow"
                  >
                    <div class="card-body p-4">
                      <div class="flex items-center justify-between">
                        <div class="flex items-center gap-3">
                          <div
                            class="badge badge-primary cursor-pointer hover:badge-secondary"
                            @click="changeStatusId(status)"
                            title="Click to change status ID"
                          >
                            Status {{ status }}{{ getStatusName(status) }}
                          </div>
                          <div class="text-sm text-base-content/70">
                            {{ getModifierSummary(status) }}
                          </div>
                        </div>
                        <div class="flex gap-2">
                          <button
                            class="btn btn-sm btn-outline"
                            @click="editModifier(status)"
                          >
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                            Edit
                          </button>
                          <button
                            class="btn btn-sm btn-ghost btn-square text-error"
                            @click="removeStatus(status)"
                            :disabled="status === 0"
                          >
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                            </svg>
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>

                  <button class="btn btn-outline btn-sm w-full" @click="addStatus">
                    <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    Add Status
                  </button>
                </div>
              </div>

              <!-- Actions -->
              <div class="mt-6 flex justify-between gap-2">
                <div class="flex gap-2">
                  <button class="btn btn-outline btn-sm" @click="showJsonEditor = true">
                    <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
                    </svg>
                    Source
                  </button>
                  <button
                    v-if="!isCreate"
                    class="btn btn-outline btn-sm btn-info"
                    @click="openDuplicateDialog"
                    :disabled="saving"
                    title="Save a copy with a new ID"
                  >
                    <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                    </svg>
                    Save as Copy
                  </button>
                </div>
                <div class="flex gap-2">
                  <button class="btn btn-ghost" @click="emit('close')">
                    Cancel
                  </button>
                  <button class="btn btn-primary" @click="handleSave" :disabled="saving">
                    <span v-if="saving" class="loading loading-spinner loading-sm mr-2"></span>
                    {{ saving ? 'Saving...' : 'Save' }}
                  </button>
                </div>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>

  <!-- JSON Editor Dialog -->
  <JsonEditorDialog
    v-model:is-open="showJsonEditor"
    :model-value="formData"
    @apply="handleJsonApply"
  />

  <!-- Duplicate BlockType Dialog -->
  <TransitionRoot :show="showDuplicateDialog" as="template">
    <Dialog as="div" class="relative z-50" @close="closeDuplicateDialog">
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
            <DialogPanel class="w-full max-w-md transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
          <DialogTitle class="text-lg font-bold mb-4">
            Save as Copy
          </DialogTitle>

          <p class="text-sm text-base-content/70 mb-4">
            Create a copy of this BlockType with a new ID.
          </p>

          <div class="form-control">
            <label class="label">
              <span class="label-text font-semibold">New BlockType ID</span>
              <span class="label-text-alt text-error" v-if="!newBlockTypeId">Required</span>
            </label>
            <input
              v-model="newBlockTypeId"
              type="text"
              class="input input-bordered"
              placeholder="e.g., w:123"
              @keyup.enter="handleDuplicate"
            />
            <label class="label">
              <span class="label-text-alt">Use format: group:name</span>
            </label>
          </div>

          <div v-if="duplicateError" class="alert alert-error mt-4">
            <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <span>{{ duplicateError }}</span>
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button class="btn btn-ghost" @click="closeDuplicateDialog" :disabled="duplicating">
              Cancel
            </button>
            <button
              class="btn btn-primary"
              @click="handleDuplicate"
              :disabled="!newBlockTypeId || duplicating"
            >
              <span v-if="duplicating" class="loading loading-spinner loading-sm mr-2"></span>
              {{ duplicating ? 'Duplicating...' : 'Save Copy' }}
            </button>
          </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>

  <!-- Add Status Dialog -->
  <Teleport to="body" v-if="showAddStatusDialog">
    <div class="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black bg-opacity-50" @click="closeAddStatusDialog"></div>

      <!-- Dialog Content -->
      <div class="relative w-full max-w-md bg-base-100 rounded-lg shadow-2xl p-6">
        <h3 class="text-lg font-bold mb-4">Add Status</h3>

        <p class="text-sm text-base-content/70 mb-3">
          Select a predefined status or enter a custom ID:
        </p>

        <!-- Quick action buttons for known statuses -->
        <div class="flex flex-wrap gap-2 mb-4">
          <button
            v-for="(name, statusId) in knownStatuses"
            :key="statusId"
            class="badge badge-lg badge-outline hover:badge-primary cursor-pointer transition-colors"
            :class="{ 'badge-disabled': formData.modifiers?.[statusId] !== undefined }"
            :disabled="formData.modifiers?.[statusId] !== undefined"
            @click="selectStatus(Number(statusId))"
          >
            {{ statusId }} ({{ name }})
          </button>
        </div>

        <div class="divider text-xs">OR CUSTOM</div>

        <div class="form-control">
          <label class="label">
            <span class="label-text font-semibold">Custom Status ID</span>
          </label>
          <input
            v-model="newStatusId"
            type="number"
            class="input input-bordered w-full"
            :placeholder="`e.g., ${suggestedNextStatus}`"
            @keyup.enter="confirmAddStatus"
            @keyup.esc="closeAddStatusDialog"
          />
          <label class="label">
            <span class="label-text-alt">Custom states typically start at 100</span>
          </label>
        </div>

        <div class="mt-6 flex justify-end gap-2">
          <button class="btn btn-ghost" @click="closeAddStatusDialog">
            Cancel
          </button>
          <button
            class="btn btn-primary"
            @click="confirmAddStatus"
            :disabled="!newStatusId"
          >
            Add Status
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Input Dialog -->
  <InputDialog
    v-model:is-open="showInputDialog"
    :title="inputDialogTitle"
    :message="inputDialogMessage"
    :default-value="inputDialogDefaultValue"
    @ok="handleInputOk"
    @cancel="handleInputCancel"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import type { BlockType, BlockModifier } from '@nimbus/shared';
import { BlockTypeType } from '@nimbus/shared';
import { useBlockTypes } from '@/composables/useBlockTypes';
import { apiService } from '@/services/ApiService';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import InputDialog from '@components/InputDialog.vue';

interface Props {
  blockType: BlockType | null;
  worldId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
  (e: 'edit-modifier', data: { blockType: BlockType; status: number; modifier: BlockModifier }): void;
}>();

const { createBlockType, updateBlockType } = useBlockTypes(props.worldId);

const isCreate = computed(() => !props.blockType);
const saving = ref(false);
const showJsonEditor = ref(false);

// Duplicate dialog state
const showDuplicateDialog = ref(false);
const newBlockTypeId = ref('');
const duplicating = ref(false);
const duplicateError = ref<string | null>(null);

// Add Status dialog state
const showAddStatusDialog = ref(false);
const newStatusId = ref<string>('');

// Known statuses for quick selection
const knownStatuses: Record<number, string> = {
  0: 'DEFAULT',
  1: 'OPEN',
  2: 'CLOSED',
  3: 'LOCKED',
  5: 'DESTROYED',
  10: 'WINTER',
  11: 'SPRING',
  12: 'SUMMER',
  13: 'AUTUMN',
};

// Input dialog state
const showInputDialog = ref(false);
const inputDialogTitle = ref('');
const inputDialogMessage = ref('');
const inputDialogDefaultValue = ref('');
const inputDialogCallback = ref<((value: string | null) => void) | null>(null);

// Helper to show input dialog (replaces prompt)
const showInput = (title: string, message: string, defaultValue: string): Promise<string | null> => {
  return new Promise((resolve) => {
    inputDialogTitle.value = title;
    inputDialogMessage.value = message;
    inputDialogDefaultValue.value = defaultValue;
    inputDialogCallback.value = resolve;
    showInputDialog.value = true;
  });
};

const handleInputOk = (value: string) => {
  if (inputDialogCallback.value) {
    inputDialogCallback.value(value);
    inputDialogCallback.value = null;
  }
};

const handleInputCancel = () => {
  if (inputDialogCallback.value) {
    inputDialogCallback.value(null);
    inputDialogCallback.value = null;
  }
};

// Form data
const formData = ref<Partial<BlockType>>({
  id: '',
  description: '',
  initialStatus: 0,
  modifiers: {},
});

// Expose method to update modifier from parent
const updateModifier = (status: number, modifier: BlockModifier) => {
  if (formData.value.modifiers) {
    formData.value.modifiers[status] = modifier;
  }
};

// Expose for parent access
defineExpose({
  updateModifier,
  formData
});

// Initialize form
const initializeForm = async () => {
  if (props.blockType) {
    formData.value = JSON.parse(JSON.stringify(props.blockType));
  } else {
    formData.value = {
      id: '', // Must be provided by user
      description: '',
      initialStatus: 0,
      modifiers: {
        0: { visibility: { shape: 1, textures: {} } }, // Default status with CUBE shape
      },
    };
  }
};

onMounted(() => {
  initializeForm();
});

// Status list
const statusList = computed(() => {
  return Object.keys(formData.value.modifiers || {}).map(Number).sort((a, b) => a - b);
});

// Suggested next status for custom statuses
const suggestedNextStatus = computed(() => {
  const existingStatuses = statusList.value;
  if (existingStatuses.length === 0) return 100;
  const maxStatus = Math.max(...existingStatuses);
  return maxStatus >= 100 ? maxStatus + 1 : 100;
});

// Get status name from BlockStatus enum
const getStatusName = (status: number): string => {
  return knownStatuses[status] ? ` (${knownStatuses[status]})` : '';
};

// Get modifier summary
const getModifierSummary = (status: number): string => {
  const modifier = formData.value.modifiers?.[status];
  if (!modifier) return 'Empty';

  const parts: string[] = [];
  if (modifier.visibility) parts.push('visibility');
  if (modifier.physics) parts.push('physics');
  if (modifier.wind) parts.push('wind');
  if (modifier.effects) parts.push('effects');
  if (modifier.illumination) parts.push('illumination');
  if (modifier.audio) parts.push('audio');

  return parts.length > 0 ? parts.join(', ') : 'Empty';
};

// Add status - open dialog
const addStatus = () => {
  newStatusId.value = '';
  showAddStatusDialog.value = true;
};

// Close add status dialog
const closeAddStatusDialog = () => {
  showAddStatusDialog.value = false;
  newStatusId.value = '';
};

// Select a known status from quick action buttons
const selectStatus = (statusId: number) => {
  if (formData.value.modifiers?.[statusId] !== undefined) {
    return; // Already exists
  }

  if (!formData.value.modifiers) {
    formData.value.modifiers = {};
  }

  formData.value.modifiers[statusId] = {
    visibility: { shape: 1, textures: {} }
  };

  closeAddStatusDialog();
};

// Confirm adding custom status from input field
const confirmAddStatus = () => {
  const statusId = parseInt(newStatusId.value, 10);

  if (isNaN(statusId)) {
    alert('Please enter a valid number');
    return;
  }

  if (formData.value.modifiers && formData.value.modifiers[statusId]) {
    alert('Status ID already exists');
    return;
  }

  if (!formData.value.modifiers) {
    formData.value.modifiers = {};
  }

  formData.value.modifiers[statusId] = {
    visibility: { shape: 1, textures: {} }
  };

  closeAddStatusDialog();
};

// Change status ID
const changeStatusId = async (oldStatus: number) => {
  if (oldStatus === 0) {
    return;
  }

  const newStatusIdStr = await showInput(
    'Change Status ID',
    `Change status ID from ${oldStatus} to:`,
    oldStatus.toString()
  );

  if (newStatusIdStr === null) return; // Cancelled

  const newStatusId = parseInt(newStatusIdStr, 10);

  if (isNaN(newStatusId)) {
    return;
  }

  if (newStatusId === oldStatus) return; // No change

  if (formData.value.modifiers && formData.value.modifiers[newStatusId]) {
    return;
  }

  if (!formData.value.modifiers || !formData.value.modifiers[oldStatus]) {
    return;
  }

  // Copy modifier to new status ID
  formData.value.modifiers[newStatusId] = formData.value.modifiers[oldStatus];

  // Delete old status
  delete formData.value.modifiers[oldStatus];
};

// Remove status
const removeStatus = (status: number) => {
  if (status === 0) {
    alert('Cannot remove status 0 (default status)');
    return;
  }

  if (formData.value.modifiers && formData.value.modifiers[status]) {
    delete formData.value.modifiers[status];
  }
};

// Edit modifier - emit event to parent
const editModifier = (status: number) => {
  if (!formData.value.modifiers) return;

  emit('edit-modifier', {
    blockType: formData.value as BlockType,
    status,
    modifier: formData.value.modifiers[status]
  });
};

// Handle save
const handleSave = async () => {
  if (isCreate.value && !formData.value.id) {
    alert('ID is required');
    return;
  }

  if (!formData.value.modifiers || Object.keys(formData.value.modifiers).length === 0) {
    alert('At least one modifier (status 0) is required');
    return;
  }

  saving.value = true;

  try {
    if (isCreate.value) {
      await createBlockType(formData.value);
    } else {
      await updateBlockType(formData.value.id!, formData.value);
    }

    emit('saved');
  } catch (err: any) {
    // Extract error message from server response
    let errorMessage = 'Failed to save block type';
    if (err?.response?.data?.error) {
      errorMessage = err.response.data.error;
    } else if (err?.message) {
      errorMessage = err.message;
    }
    alert(errorMessage);
  } finally {
    saving.value = false;
  }
};

// Handle JSON apply from JSON editor
const handleJsonApply = (jsonData: any) => {
  formData.value = jsonData;
};

// Duplicate BlockType functionality
const openDuplicateDialog = () => {
  newBlockTypeId.value = '';
  duplicateError.value = null;
  showDuplicateDialog.value = true;
};

const closeDuplicateDialog = () => {
  showDuplicateDialog.value = false;
  newBlockTypeId.value = '';
  duplicateError.value = null;
};

const handleDuplicate = async () => {
  if (!newBlockTypeId.value || duplicating.value || !props.blockType?.id) {
    return;
  }

  duplicating.value = true;
  duplicateError.value = null;

  try {
    const apiUrl = apiService.getBaseUrl();
    const sourceBlockId = props.blockType.id;
    const url = `${apiUrl}/control/worlds/${props.worldId}/blocktypes/duplicate/${encodeURIComponent(sourceBlockId)}`;

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        newBlockId: newBlockTypeId.value,
      }),
      credentials: 'include'
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }));
      duplicateError.value = errorData.error || `Failed to duplicate BlockType: ${response.statusText}`;
      return;
    }

    const result = await response.json();

    // Close dialog
    closeDuplicateDialog();

    // Show success message
    alert(`BlockType duplicated successfully!\n\nNew ID: ${result.blockId}\n\nThe page will reload to show the updated list.`);

    // Emit saved event to refresh the list
    emit('saved');

    // Close the editor
    emit('close');
  } catch (err) {
    duplicateError.value = err instanceof Error ? err.message : 'Unknown error occurred';
  } finally {
    duplicating.value = false;
  }
};
</script>
