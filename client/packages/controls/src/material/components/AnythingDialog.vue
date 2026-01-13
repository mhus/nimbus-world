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
                {{ isEditMode ? 'Edit Entity' : 'Create Entity' }}
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
                    placeholder="Enter name..."
                    :disabled="isEditMode"
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">
                      {{ isEditMode ? 'Name cannot be changed' : 'Unique identifier for this entity' }}
                    </span>
                  </label>
                </div>

                <!-- Title -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Title</span>
                  </label>
                  <input
                    v-model="formData.title"
                    type="text"
                    class="input input-bordered"
                    placeholder="Enter title..."
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Display title for this entity</span>
                  </label>
                </div>

                <!-- Type -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Type</span>
                  </label>
                  <input
                    v-model="formData.type"
                    type="text"
                    class="input input-bordered"
                    placeholder="Enter type..."
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Type identifier for categorization</span>
                  </label>
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

                <!-- Data (JSON Editor) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Data (JSON)</span>
                  </label>
                  <textarea
                    v-model="dataJson"
                    class="textarea textarea-bordered font-mono text-sm h-48"
                    placeholder='{"key": "value"}'
                    @blur="validateJson"
                  ></textarea>
                  <label class="label">
                    <span v-if="jsonError" class="label-text-alt text-error">{{ jsonError }}</span>
                    <span v-else class="label-text-alt text-base-content/60">Enter valid JSON data</span>
                  </label>
                </div>

                <!-- Enabled Toggle (Edit Mode Only) -->
                <div v-if="isEditMode" class="form-control">
                  <label class="label cursor-pointer">
                    <span class="label-text font-semibold">Enabled</span>
                    <input
                      v-model="formData.enabled"
                      type="checkbox"
                      class="toggle toggle-primary"
                    />
                  </label>
                </div>

                <!-- Scope Info (Edit Mode) -->
                <div v-if="isEditMode" class="p-4 bg-base-200 rounded">
                  <div class="text-sm font-semibold mb-2">Scope</div>
                  <div class="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <span class="text-base-content/60">Collection:</span>
                      <span class="ml-2 font-medium">{{ collection }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">Region:</span>
                      <span class="ml-2 font-medium">{{ regionId || '-' }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">World:</span>
                      <span class="ml-2 font-medium">{{ worldId || '-' }}</span>
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
                  <span v-else>{{ isEditMode ? 'Save' : 'Create' }}</span>
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
import { ref, computed, watch, onMounted } from 'vue';
import { TransitionRoot, TransitionChild, Dialog, DialogPanel, DialogTitle } from '@headlessui/vue';
import type { WAnything } from '@shared/generated/entities/WAnything';
import { anythingService } from '@/services/AnythingService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('AnythingDialog');

const props = defineProps<{
  entity?: WAnything | null;
  collection: string;
  regionId?: string;
  worldId?: string;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const isEditMode = computed(() => !!props.entity);

// Form data
const formData = ref({
  name: '',
  title: '',
  type: '',
  description: '',
  enabled: true,
});

const dataJson = ref('{}');
const jsonError = ref<string | null>(null);
const error = ref<string | null>(null);
const saving = ref(false);

/**
 * Validate JSON
 */
const validateJson = () => {
  jsonError.value = null;
  try {
    if (dataJson.value.trim()) {
      JSON.parse(dataJson.value);
    }
  } catch (e: any) {
    jsonError.value = 'Invalid JSON: ' + e.message;
  }
};

/**
 * Parse data JSON safely
 */
const parseDataJson = (): any => {
  try {
    if (!dataJson.value.trim()) return null;
    return JSON.parse(dataJson.value);
  } catch (e) {
    return null;
  }
};

/**
 * Check if form is valid
 */
const isValid = computed(() => {
  if (!formData.value.name) return false;
  if (jsonError.value) return false;
  return true;
});

/**
 * Handle save
 */
const handleSave = async () => {
  error.value = null;
  saving.value = true;

  try {
    validateJson();
    if (jsonError.value) {
      error.value = 'Please fix JSON errors before saving';
      saving.value = false;
      return;
    }

    const data = parseDataJson();

    if (isEditMode.value && props.entity) {
      // Update existing entity
      await anythingService.update(props.entity.id, {
        title: formData.value.title || undefined,
        description: formData.value.description || undefined,
        type: formData.value.type || undefined,
        data: data || undefined,
        enabled: formData.value.enabled,
      });
      logger.info('Entity updated', { id: props.entity.id });
    } else {
      // Create new entity
      await anythingService.create({
        regionId: props.regionId,
        worldId: props.worldId,
        collection: props.collection,
        name: formData.value.name,
        title: formData.value.title || undefined,
        description: formData.value.description || undefined,
        type: formData.value.type || undefined,
        data: data || undefined,
      });
      logger.info('Entity created', { name: formData.value.name });
    }

    emit('saved');
  } catch (e: any) {
    logger.error('Failed to save entity', {}, e);
    error.value = e.message || 'Failed to save entity';
  } finally {
    saving.value = false;
  }
};

/**
 * Initialize form data
 */
const initializeForm = () => {
  if (props.entity) {
    formData.value = {
      name: props.entity.name || '',
      title: props.entity.title || '',
      type: props.entity.type || '',
      description: props.entity.description || '',
      enabled: props.entity.enabled ?? true,
    };

    // Serialize data to JSON
    if (props.entity.data) {
      try {
        dataJson.value = JSON.stringify(props.entity.data, null, 2);
      } catch (e) {
        logger.warn('Failed to serialize entity data', {}, e);
        dataJson.value = '{}';
      }
    } else {
      dataJson.value = '{}';
    }
  } else {
    formData.value = {
      name: '',
      title: '',
      type: '',
      description: '',
      enabled: true,
    };
    dataJson.value = '{}';
  }
};

// Watch for entity changes
watch(() => props.entity, initializeForm, { immediate: true });

onMounted(() => {
  initializeForm();
});
</script>
