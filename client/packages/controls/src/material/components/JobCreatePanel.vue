<template>
  <TransitionRoot :show="true" as="template">
    <Dialog as="div" class="relative z-50" @close="$emit('close')">
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
                {{ initialJob ? 'Clone Job' : (presetData ? 'Create Job from Preset' : 'Create New Job') }}
              </DialogTitle>

              <!-- Form -->
              <form @submit.prevent="handleCreate" class="space-y-4">
                <!-- Executor -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text">Executor</span>
                  </label>
                  <input
                    v-model="formData.executor"
                    type="text"
                    class="input input-bordered"
                    placeholder="e.g., flat-world-generator"
                    required
                  />
                </div>

                <!-- Type -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text">Job Type</span>
                  </label>
                  <input
                    v-model="formData.type"
                    type="text"
                    class="input input-bordered"
                    placeholder="e.g., terrain-generation"
                  />
                </div>

                <!-- Priority and Retries -->
                <div class="grid grid-cols-2 gap-4">
                  <div class="form-control">
                    <label class="label">
                      <span class="label-text">Priority (1-10)</span>
                    </label>
                    <input
                      v-model.number="formData.priority"
                      type="number"
                      min="1"
                      max="10"
                      class="input input-bordered"
                      required
                    />
                  </div>
                  <div class="form-control">
                    <label class="label">
                      <span class="label-text">Max Retries</span>
                    </label>
                    <input
                      v-model.number="formData.maxRetries"
                      type="number"
                      min="0"
                      max="10"
                      class="input input-bordered"
                      required
                    />
                  </div>
                </div>

                <!-- Parameters -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text">Parameters</span>
                    <button
                      type="button"
                      class="btn btn-xs btn-outline"
                      @click="addParameter"
                    >
                      <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                      </svg>
                      Add
                    </button>
                  </label>

                  <div class="overflow-x-auto border border-base-300 rounded">
                    <table class="table table-sm w-full">
                      <thead>
                        <tr>
                          <th class="w-1/3">Key</th>
                          <th class="w-1/2">Value</th>
                          <th class="w-16">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="(param, index) in parameterList" :key="index">
                          <td>
                            <input
                              v-model="param.key"
                              type="text"
                              class="input input-xs input-bordered w-full"
                              placeholder="Parameter name"
                              required
                            />
                          </td>
                          <td>
                            <input
                              v-model="param.value"
                              type="text"
                              class="input input-xs input-bordered w-full"
                              placeholder="Parameter value"
                              required
                            />
                          </td>
                          <td>
                            <button
                              type="button"
                              class="btn btn-xs btn-ghost text-error"
                              @click="removeParameter(index)"
                              title="Remove"
                            >
                              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                              </svg>
                            </button>
                          </td>
                        </tr>
                        <tr v-if="parameterList.length === 0">
                          <td colspan="3" class="text-center text-base-content/50 py-4">
                            No parameters. Click "Add" or use a preset.
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <!-- On Success Job -->
                <div class="form-control">
                  <div class="collapse collapse-arrow bg-base-200">
                    <input type="checkbox" v-model="showOnSuccess" />
                    <div class="collapse-title font-medium">
                      On Success Job (Optional)
                      <span v-if="onSuccessData.executor" class="badge badge-success badge-sm ml-2">
                        {{ onSuccessData.executor }}
                      </span>
                    </div>
                    <div class="collapse-content">
                      <div class="space-y-3 pt-3">
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Executor</span>
                          </label>
                          <input
                            v-model="onSuccessData.executor"
                            type="text"
                            class="input input-sm input-bordered"
                            placeholder="e.g., cleanup-executor"
                          />
                        </div>
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Type</span>
                          </label>
                          <input
                            v-model="onSuccessData.type"
                            type="text"
                            class="input input-sm input-bordered"
                            placeholder="e.g., cleanup-job"
                          />
                        </div>
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Parameters</span>
                            <button
                              type="button"
                              class="btn btn-xs btn-outline"
                              @click="addOnSuccessParameter"
                            >
                              <svg class="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                              </svg>
                              Add
                            </button>
                          </label>
                          <div v-if="onSuccessParameters.length > 0" class="overflow-x-auto border border-base-300 rounded">
                            <table class="table table-xs w-full">
                              <thead>
                                <tr>
                                  <th class="w-1/3">Key</th>
                                  <th class="w-1/2">Value</th>
                                  <th class="w-16">Actions</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr v-for="(param, index) in onSuccessParameters" :key="index">
                                  <td>
                                    <input
                                      v-model="param.key"
                                      type="text"
                                      class="input input-xs input-bordered w-full"
                                      placeholder="Key"
                                    />
                                  </td>
                                  <td>
                                    <input
                                      v-model="param.value"
                                      type="text"
                                      class="input input-xs input-bordered w-full"
                                      placeholder="Value"
                                    />
                                  </td>
                                  <td>
                                    <button
                                      type="button"
                                      class="btn btn-xs btn-ghost text-error"
                                      @click="removeOnSuccessParameter(index)"
                                    >
                                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                                      </svg>
                                    </button>
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- On Error Job -->
                <div class="form-control">
                  <div class="collapse collapse-arrow bg-base-200">
                    <input type="checkbox" v-model="showOnError" />
                    <div class="collapse-title font-medium">
                      On Error Job (Optional)
                      <span v-if="onErrorData.executor" class="badge badge-error badge-sm ml-2">
                        {{ onErrorData.executor }}
                      </span>
                    </div>
                    <div class="collapse-content">
                      <div class="space-y-3 pt-3">
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Executor</span>
                          </label>
                          <input
                            v-model="onErrorData.executor"
                            type="text"
                            class="input input-sm input-bordered"
                            placeholder="e.g., rollback-executor"
                          />
                        </div>
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Type</span>
                          </label>
                          <input
                            v-model="onErrorData.type"
                            type="text"
                            class="input input-sm input-bordered"
                            placeholder="e.g., rollback-job"
                          />
                        </div>
                        <div class="form-control">
                          <label class="label">
                            <span class="label-text text-sm">Parameters</span>
                            <button
                              type="button"
                              class="btn btn-xs btn-outline"
                              @click="addOnErrorParameter"
                            >
                              <svg class="w-3 h-3 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                              </svg>
                              Add
                            </button>
                          </label>
                          <div v-if="onErrorParameters.length > 0" class="overflow-x-auto border border-base-300 rounded">
                            <table class="table table-xs w-full">
                              <thead>
                                <tr>
                                  <th class="w-1/3">Key</th>
                                  <th class="w-1/2">Value</th>
                                  <th class="w-16">Actions</th>
                                </tr>
                              </thead>
                              <tbody>
                                <tr v-for="(param, index) in onErrorParameters" :key="index">
                                  <td>
                                    <input
                                      v-model="param.key"
                                      type="text"
                                      class="input input-xs input-bordered w-full"
                                      placeholder="Key"
                                    />
                                  </td>
                                  <td>
                                    <input
                                      v-model="param.value"
                                      type="text"
                                      class="input input-xs input-bordered w-full"
                                      placeholder="Value"
                                    />
                                  </td>
                                  <td>
                                    <button
                                      type="button"
                                      class="btn btn-xs btn-ghost text-error"
                                      @click="removeOnErrorParameter(index)"
                                    >
                                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                                      </svg>
                                    </button>
                                  </td>
                                </tr>
                              </tbody>
                            </table>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Error Display -->
                <ErrorAlert v-if="saveError" :message="saveError" />

                <!-- Actions -->
                <div class="mt-6 flex justify-end gap-2">
                  <button
                    type="button"
                    class="btn"
                    @click="$emit('close')"
                    :disabled="saving"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    class="btn btn-primary"
                    :disabled="saving || !isFormValid"
                  >
                    <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                    {{ saving ? 'Creating...' : 'Create Job' }}
                  </button>
                </div>
              </form>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { useJobs, type Job, type JobCreateRequest, type NextJob } from '@/composables/useJobs';
import ErrorAlert from '@components/ErrorAlert.vue';

const props = defineProps<{
  worldId: string;
  initialJob?: Job | null;
  presetData?: any;
}>();

const emit = defineEmits<{
  close: [];
  created: [];
}>();

const { createJob } = useJobs(props.worldId);

// Form data
const formData = ref({
  executor: '',
  type: '',
  priority: 5,
  maxRetries: 2,
});

// Parameter list (key-value pairs)
const parameterList = ref<Array<{ key: string; value: string }>>([]);

// On Success Job
const showOnSuccess = ref(false);
const onSuccessData = ref({
  executor: '',
  type: '',
});
const onSuccessParameters = ref<Array<{ key: string; value: string }>>([]);

// On Error Job
const showOnError = ref(false);
const onErrorData = ref({
  executor: '',
  type: '',
});
const onErrorParameters = ref<Array<{ key: string; value: string }>>([]);

const saving = ref(false);
const saveError = ref<string | null>(null);

/**
 * Load initial values from a job (for cloning)
 */
const loadFromJob = (job: Job) => {
  // Set basic form data
  formData.value.executor = job.executor;
  formData.value.type = job.type;
  formData.value.priority = job.priority;
  formData.value.maxRetries = job.maxRetries;

  // Load parameters
  parameterList.value = Object.entries(job.parameters).map(([key, value]) => ({
    key,
    value,
  }));

  // Load onSuccess if exists
  if (job.onSuccess) {
    showOnSuccess.value = true;
    onSuccessData.value.executor = job.onSuccess.executor;
    onSuccessData.value.type = job.onSuccess.type || '';
    onSuccessParameters.value = job.onSuccess.parameters
      ? Object.entries(job.onSuccess.parameters).map(([key, value]) => ({
          key,
          value,
        }))
      : [];
  }

  // Load onError if exists
  if (job.onError) {
    showOnError.value = true;
    onErrorData.value.executor = job.onError.executor;
    onErrorData.value.type = job.onError.type || '';
    onErrorParameters.value = job.onError.parameters
      ? Object.entries(job.onError.parameters).map(([key, value]) => ({
          key,
          value,
        }))
      : [];
  }
};

/**
 * Load initial values from preset data
 */
const loadFromPreset = (data: any) => {
  if (!data) return;

  // Set basic form data from preset
  if (data.executor) formData.value.executor = data.executor;
  if (data.type) formData.value.type = data.type;
  if (data.priority !== undefined) formData.value.priority = data.priority;
  if (data.maxRetries !== undefined) formData.value.maxRetries = data.maxRetries;

  // Load parameters from preset
  if (data.parameters && typeof data.parameters === 'object') {
    parameterList.value = Object.entries(data.parameters).map(([key, value]) => ({
      key,
      value: String(value),
    }));
  }

  // Load onSuccess if exists in preset
  if (data.onSuccess) {
    showOnSuccess.value = true;
    onSuccessData.value.executor = data.onSuccess.executor || '';
    onSuccessData.value.type = data.onSuccess.type || '';
    onSuccessParameters.value = data.onSuccess.parameters
      ? Object.entries(data.onSuccess.parameters).map(([key, value]) => ({
          key,
          value: String(value),
        }))
      : [];
  }

  // Load onError if exists in preset
  if (data.onError) {
    showOnError.value = true;
    onErrorData.value.executor = data.onError.executor || '';
    onErrorData.value.type = data.onError.type || '';
    onErrorParameters.value = data.onError.parameters
      ? Object.entries(data.onError.parameters).map(([key, value]) => ({
          key,
          value: String(value),
        }))
      : [];
  }
};

// Watch for initialJob changes
watch(
  () => props.initialJob,
  (newJob) => {
    if (newJob) {
      loadFromJob(newJob);
    }
  },
  { immediate: true }
);

// Watch for presetData changes
watch(
  () => props.presetData,
  (newPreset) => {
    if (newPreset) {
      loadFromPreset(newPreset);
    }
  },
  { immediate: true }
);

/**
 * Validate form
 */
const isFormValid = computed(() => {
  return formData.value.executor !== '';
});

/**
 * Add new parameter row
 */
const addParameter = () => {
  parameterList.value.push({ key: '', value: '' });
};

/**
 * Remove parameter row
 */
const removeParameter = (index: number) => {
  parameterList.value.splice(index, 1);
};

/**
 * Add onSuccess parameter row
 */
const addOnSuccessParameter = () => {
  onSuccessParameters.value.push({ key: '', value: '' });
};

/**
 * Remove onSuccess parameter row
 */
const removeOnSuccessParameter = (index: number) => {
  onSuccessParameters.value.splice(index, 1);
};

/**
 * Add onError parameter row
 */
const addOnErrorParameter = () => {
  onErrorParameters.value.push({ key: '', value: '' });
};

/**
 * Remove onError parameter row
 */
const removeOnErrorParameter = (index: number) => {
  onErrorParameters.value.splice(index, 1);
};

/**
 * Build parameters object from list
 */
const buildParameters = (): Record<string, string> => {
  const result: Record<string, string> = {};
  parameterList.value.forEach(param => {
    if (param.key.trim() && param.value.trim()) {
      result[param.key.trim()] = param.value.trim();
    }
  });
  return result;
};

/**
 * Build NextJob object from onSuccess data
 */
const buildOnSuccessJob = (): NextJob | undefined => {
  if (!showOnSuccess.value || !onSuccessData.value.executor.trim()) {
    return undefined;
  }

  const parameters: Record<string, string> = {};
  onSuccessParameters.value.forEach(param => {
    if (param.key.trim() && param.value.trim()) {
      parameters[param.key.trim()] = param.value.trim();
    }
  });

  return {
    executor: onSuccessData.value.executor.trim(),
    type: onSuccessData.value.type.trim() || undefined,
    parameters: Object.keys(parameters).length > 0 ? parameters : undefined,
  };
};

/**
 * Build NextJob object from onError data
 */
const buildOnErrorJob = (): NextJob | undefined => {
  if (!showOnError.value || !onErrorData.value.executor.trim()) {
    return undefined;
  }

  const parameters: Record<string, string> = {};
  onErrorParameters.value.forEach(param => {
    if (param.key.trim() && param.value.trim()) {
      parameters[param.key.trim()] = param.value.trim();
    }
  });

  return {
    executor: onErrorData.value.executor.trim(),
    type: onErrorData.value.type.trim() || undefined,
    parameters: Object.keys(parameters).length > 0 ? parameters : undefined,
  };
};

/**
 * Handle create
 */
const handleCreate = async () => {
  if (!isFormValid.value) {
    return;
  }

  saving.value = true;
  saveError.value = null;

  try {
    const request: JobCreateRequest = {
      executor: formData.value.executor,
      type: formData.value.type.trim() || undefined,
      parameters: buildParameters(),
      priority: formData.value.priority,
      maxRetries: formData.value.maxRetries,
      onSuccess: buildOnSuccessJob(),
      onError: buildOnErrorJob(),
    };

    await createJob(request);
    emit('created');
  } catch (err) {
    saveError.value = `Failed to create job: ${(err as Error).message}`;
  } finally {
    saving.value = false;
  }
};
</script>
