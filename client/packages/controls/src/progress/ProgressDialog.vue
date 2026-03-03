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
                {{ isEditMode ? 'Edit Progress' : 'Create Progress' }}
              </DialogTitle>

              <!-- Error Alert -->
              <div v-if="error" class="alert alert-error mb-4">
                <span>{{ error }}</span>
              </div>

              <div class="space-y-4">
                <!-- Player ID -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Player ID *</span>
                  </label>
                  <input
                    v-model="formData.playerId"
                    type="text"
                    class="input input-bordered"
                    placeholder="Player identifier..."
                    :disabled="isEditMode"
                  />
                </div>

                <!-- Type -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Type *</span>
                  </label>
                  <input
                    v-model="formData.type"
                    type="text"
                    class="input input-bordered"
                    placeholder="e.g. quest, achievement, skill, exploration"
                    :disabled="isEditMode"
                  />
                </div>

                <!-- Quest -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Quest</span>
                  </label>
                  <input
                    v-model="formData.quest"
                    type="text"
                    class="input input-bordered"
                    placeholder="Optional quest identifier..."
                    :disabled="isEditMode"
                  />
                </div>

                <!-- Progress Data (JSON) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Progress Data (JSON)</span>
                  </label>
                  <textarea
                    v-model="progressDataJson"
                    class="textarea textarea-bordered font-mono text-sm h-48"
                    placeholder='{"step": 1, "completed": false}'
                  ></textarea>
                  <label class="label">
                    <span class="label-text-alt" :class="jsonError ? 'text-error' : 'text-base-content/60'">
                      {{ jsonError || 'Key-value JSON object' }}
                    </span>
                  </label>
                </div>

                <!-- Metadata (Edit Mode) -->
                <div v-if="isEditMode && progress" class="p-4 bg-base-200 rounded">
                  <div class="text-sm font-semibold mb-2">Metadata</div>
                  <div class="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <span class="text-base-content/60">ID:</span>
                      <span class="ml-2 font-mono text-xs">{{ progress.id }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">World:</span>
                      <span class="ml-2 font-mono text-xs">{{ progress.worldId }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">Created:</span>
                      <span class="ml-2 text-xs">{{ formatDate(progress.createdAt) }}</span>
                    </div>
                    <div>
                      <span class="text-base-content/60">Updated:</span>
                      <span class="ml-2 text-xs">{{ formatDate(progress.updatedAt) }}</span>
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
import { apiService } from '@/services/ApiService';

interface ProgressItem {
  id: string;
  worldId: string;
  playerId: string;
  quest?: string;
  type: string;
  progressData: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

interface Props {
  worldId: string;
  progress?: ProgressItem | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const isEditMode = computed(() => !!props.progress);

const formData = ref({
  playerId: '',
  type: '',
  quest: '',
});

const progressDataJson = ref('{}');
const jsonError = ref<string | null>(null);
const error = ref<string | null>(null);
const saving = ref(false);

const isValid = computed(() => {
  return formData.value.playerId.trim() !== '' &&
         formData.value.type.trim() !== '' &&
         !jsonError.value;
});

// Validate JSON on change
watch(progressDataJson, (val) => {
  try {
    JSON.parse(val);
    jsonError.value = null;
  } catch {
    jsonError.value = 'Invalid JSON';
  }
});

const handleSave = async () => {
  if (!isValid.value) return;

  saving.value = true;
  error.value = null;

  try {
    let parsedData: Record<string, any> = {};
    try {
      parsedData = JSON.parse(progressDataJson.value);
    } catch {
      error.value = 'Invalid JSON in progress data';
      return;
    }

    const body = {
      playerId: formData.value.playerId,
      type: formData.value.type,
      quest: formData.value.quest || null,
      progressData: parsedData,
    };

    if (isEditMode.value && props.progress) {
      await apiService.put(
        `/control/worlds/${props.worldId}/progress/${props.progress.id}`,
        body
      );
    } else {
      await apiService.post(
        `/control/worlds/${props.worldId}/progress`,
        body
      );
    }

    emit('saved');
  } catch (err: any) {
    error.value = err.message || 'Failed to save';
  } finally {
    saving.value = false;
  }
};

const formatDate = (date: string | undefined): string => {
  if (!date) return '-';
  return new Date(date).toLocaleString();
};

onMounted(() => {
  if (props.progress) {
    formData.value = {
      playerId: props.progress.playerId,
      type: props.progress.type,
      quest: props.progress.quest || '',
    };
    progressDataJson.value = JSON.stringify(props.progress.progressData || {}, null, 2);
  }
});
</script>
