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
                      <option v-for="name in blockTypeTypeNames" :key="name" :value="BlockTypeType[name as keyof typeof BlockTypeType]">
                        {{ name }} ({{ BlockTypeType[name as keyof typeof BlockTypeType] }})
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
                    v-model="formData.initialStatus"
                    type="text"
                    class="input input-bordered"
                    placeholder="default"
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
                            Status: {{ status }}
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
                            class="btn btn-sm btn-outline"
                            @click="duplicateModifier(status)"
                            title="Duplicate this modifier as a new status"
                          >
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                            </svg>
                            Duplicate
                          </button>
                          <button
                            class="btn btn-sm btn-ghost btn-square text-error"
                            @click="removeStatus(status)"
                            :disabled="status === 'default'"
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

                <!-- Default Client Parameters -->
                <div class="divider">Default Client Parameters</div>
                <div class="space-y-2">
                  <div
                    v-for="(entry, index) in defaultClientEntries"
                    :key="'dc-' + index"
                    class="flex gap-2 items-center"
                  >
                    <input
                      v-model="entry.key"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Key"
                    />
                    <input
                      v-model="entry.value"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Value"
                    />
                    <button
                      class="btn btn-sm btn-ghost btn-square text-error"
                      @click="defaultClientEntries.splice(index, 1)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                  <button class="btn btn-outline btn-sm w-full" @click="defaultClientEntries.push({ key: '', value: '' })">
                    <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    Add Entry
                  </button>
                </div>

                <!-- Interactive Shortcut -->
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-2">
                    <input
                      :checked="isInteractive"
                      @change="toggleInteractive"
                      type="checkbox"
                      class="checkbox checkbox-sm"
                    />
                    <span class="label-text">Interactive</span>
                    <span class="label-text-alt">Player can interact with blocks of this type</span>
                  </label>
                </div>

                <!-- Default Server Parameters -->
                <div class="divider">Default Server Parameters</div>
                <div class="space-y-2">
                  <div
                    v-for="(entry, index) in defaultServerEntries"
                    :key="'ds-' + index"
                    class="flex gap-2 items-center"
                  >
                    <input
                      v-model="entry.key"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Key"
                    />
                    <input
                      v-model="entry.value"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Value"
                    />
                    <button
                      class="btn btn-sm btn-ghost btn-square text-error"
                      @click="defaultServerEntries.splice(index, 1)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                  <button class="btn btn-outline btn-sm w-full" @click="defaultServerEntries.push({ key: '', value: '' })">
                    <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    Add Entry
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
            v-for="statusId in knownStatuses"
            :key="statusId"
            class="badge badge-lg badge-outline hover:badge-primary cursor-pointer transition-colors"
            :class="{ 'badge-disabled': formData.modifiers?.[statusId] !== undefined }"
            :disabled="formData.modifiers?.[statusId] !== undefined"
            @click="selectStatus(statusId)"
          >
            {{ statusId }}
          </button>
        </div>

        <div class="divider text-xs">OR CUSTOM</div>

        <div class="form-control">
          <label class="label">
            <span class="label-text font-semibold">Custom Status Name</span>
          </label>
          <input
            v-model="newStatusId"
            type="text"
            class="input input-bordered w-full"
            placeholder="e.g., my-status"
            @keyup.enter="confirmAddStatus"
            @keyup.esc="closeAddStatusDialog"
          />
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
  defaultClient?: Record<string, string>;
  defaultServer?: Record<string, string>;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
  (e: 'edit-modifier', data: { blockType: BlockType; status: string; modifier: BlockModifier }): void;
}>();

const { createBlockType, updateBlockType } = useBlockTypes(props.worldId);

const isCreate = computed(() => !props.blockType || !props.blockType.name);
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
const knownStatuses: string[] = [
  'default', 'open', 'closed', 'locked', 'destroyed',
  'winter', 'spring', 'summer', 'autumn',
];

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
  initialStatus: 'default',
  modifiers: {},
});

// Default client/server maps (separate from BlockType publicData)
const defaultClientEntries = ref<Array<{ key: string; value: string }>>([]);
const defaultServerEntries = ref<Array<{ key: string; value: string }>>([]);

// Filter numeric enum reverse mappings (TypeScript numeric enums have both name→value and value→name)
const blockTypeTypeNames = Object.keys(BlockTypeType).filter(k => isNaN(Number(k)));

// Interactive checkbox: reads/writes _interactive in defaultServerEntries
const isInteractive = computed(() =>
  defaultServerEntries.value.some(e => e.key === '_interactive' && e.value === 'true')
);

function toggleInteractive(event: Event) {
  const checked = (event.target as HTMLInputElement).checked;
  const idx = defaultServerEntries.value.findIndex(e => e.key === '_interactive');
  if (checked) {
    if (idx >= 0) {
      defaultServerEntries.value[idx].value = 'true';
    } else {
      defaultServerEntries.value.push({ key: '_interactive', value: 'true' });
    }
  } else {
    if (idx >= 0) {
      defaultServerEntries.value.splice(idx, 1);
    }
  }
}

// Expose method to update modifier from parent
const updateModifier = (status: string, modifier: BlockModifier) => {
  if (formData.value.modifiers) {
    formData.value.modifiers[status] = modifier;
  }
};

// Expose for parent access
defineExpose({
  updateModifier,
  formData
});

// Convert map to entries array for editing
const mapToEntries = (map?: Record<string, string>): Array<{ key: string; value: string }> => {
  if (!map) return [];
  return Object.entries(map).map(([key, value]) => ({ key, value }));
};

// Convert entries array back to map
const entriesToMap = (entries: Array<{ key: string; value: string }>): Record<string, string> => {
  const map: Record<string, string> = {};
  for (const entry of entries) {
    if (entry.key.trim()) {
      map[entry.key.trim()] = entry.value;
    }
  }
  return map;
};

// Initialize form
const initializeForm = async () => {
  if (props.blockType) {
    formData.value = JSON.parse(JSON.stringify(props.blockType));
  } else {
    formData.value = {
      id: '', // Must be provided by user
      description: '',
      initialStatus: 'default',
      modifiers: {
        'default': { visibility: { shape: 1, textures: {} } }, // Default status with CUBE shape
      },
    };
  }
  defaultClientEntries.value = mapToEntries(props.defaultClient);
  defaultServerEntries.value = mapToEntries(props.defaultServer);
};

onMounted(() => {
  initializeForm();
});

// Status list - 'default' always first, rest alphabetically
const statusList = computed(() => {
  const keys = Object.keys(formData.value.modifiers || {});
  return keys.sort((a, b) => {
    if (a === 'default') return -1;
    if (b === 'default') return 1;
    return a.localeCompare(b);
  });
});

// Get modifier summary
const getModifierSummary = (status: string): string => {
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

// Create a new modifier by copying the 'default' modifier as template
const createModifierFromDefault = (): any => {
  const defaultModifier = formData.value.modifiers?.['default'];
  if (defaultModifier) {
    return JSON.parse(JSON.stringify(defaultModifier));
  }
  return { visibility: { shape: 1, textures: {} } };
};

// Select a known status from quick action buttons
const selectStatus = (statusId: string) => {
  if (formData.value.modifiers?.[statusId] !== undefined) {
    return; // Already exists
  }

  if (!formData.value.modifiers) {
    formData.value.modifiers = {};
  }

  formData.value.modifiers[statusId] = createModifierFromDefault();
  closeAddStatusDialog();
};

// Confirm adding custom status from input field
const confirmAddStatus = () => {
  const statusId = newStatusId.value.trim();

  if (!statusId) {
    alert('Please enter a status name');
    return;
  }

  if (formData.value.modifiers && formData.value.modifiers[statusId]) {
    alert('Status already exists');
    return;
  }

  if (!formData.value.modifiers) {
    formData.value.modifiers = {};
  }

  formData.value.modifiers[statusId] = createModifierFromDefault();
  closeAddStatusDialog();
};

// Change status ID
const changeStatusId = async (oldStatus: string) => {
  if (oldStatus === 'default') {
    return;
  }

  const newStatusIdStr = await showInput(
    'Change Status ID',
    `Change status ID from "${oldStatus}" to:`,
    oldStatus
  );

  if (newStatusIdStr === null) return; // Cancelled

  const newId = newStatusIdStr.trim();
  if (!newId || newId === oldStatus) return;

  if (formData.value.modifiers && formData.value.modifiers[newId]) {
    return;
  }

  if (!formData.value.modifiers || !formData.value.modifiers[oldStatus]) {
    return;
  }

  // Copy modifier to new status ID
  formData.value.modifiers[newId] = formData.value.modifiers[oldStatus];

  // Delete old status
  delete formData.value.modifiers[oldStatus];
};

// Remove status
const removeStatus = (status: string) => {
  if (status === 'default') {
    alert('Cannot remove default status');
    return;
  }

  if (formData.value.modifiers && formData.value.modifiers[status]) {
    delete formData.value.modifiers[status];
  }
};

// Edit modifier - emit event to parent
const editModifier = (status: string) => {
  if (!formData.value.modifiers) return;

  emit('edit-modifier', {
    blockType: formData.value as BlockType,
    status,
    modifier: formData.value.modifiers[status]
  });
};

// Duplicate modifier - copy this modifier to a new status
const duplicateModifier = async (sourceStatus: string) => {
  const modifier = formData.value.modifiers?.[sourceStatus];
  if (!modifier) return;

  const newId = await showInput(
    'Duplicate Modifier',
    `New status name for the copy of "${sourceStatus}":`,
    ''
  );

  if (!newId) return;

  const trimmedId = newId.trim();
  if (!trimmedId) return;

  if (formData.value.modifiers?.[trimmedId] !== undefined) {
    alert('Status already exists');
    return;
  }

  if (!formData.value.modifiers) {
    formData.value.modifiers = {};
  }

  formData.value.modifiers[trimmedId] = JSON.parse(JSON.stringify(modifier));
};

// Handle save
const handleSave = async () => {
  if (isCreate.value && !formData.value.id) {
    alert('ID is required');
    return;
  }

  if (!formData.value.modifiers || Object.keys(formData.value.modifiers).length === 0) {
    alert('At least one modifier (status "default") is required');
    return;
  }

  saving.value = true;

  try {
    const extraData = {
      defaultClient: entriesToMap(defaultClientEntries.value),
      defaultServer: entriesToMap(defaultServerEntries.value),
    };
    if (isCreate.value) {
      await createBlockType(formData.value);
      // For create, update defaults in a second call if any entries exist
      if (Object.keys(extraData.defaultClient).length > 0 || Object.keys(extraData.defaultServer).length > 0) {
        await updateBlockType(formData.value.id!, formData.value, extraData);
      }
    } else {
      await updateBlockType(formData.value.id!, formData.value, extraData);
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
    const sourceBlockId = props.blockType.name;
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
    alert(`BlockType duplicated successfully!\n\nNew Name: ${result.name}\n\nThe page will reload to show the updated list.`);

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
