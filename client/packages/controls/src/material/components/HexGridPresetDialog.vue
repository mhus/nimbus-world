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
            <DialogPanel class="w-full max-w-2xl transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                Create Hex Grid
              </DialogTitle>

              <!-- Loading State -->
              <div v-if="loading" class="text-center py-8">
                <span class="loading loading-spinner loading-lg"></span>
                <p class="mt-2 text-sm text-base-content/70">Loading presets...</p>
              </div>

              <!-- Main Content -->
              <div v-else>
                <!-- Selected Preset Form -->
                <div v-if="selectedPreset" class="space-y-4">
                  <div class="alert alert-info">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                    </svg>
                    <div>
                      <div class="font-semibold">{{ selectedPreset.title || selectedPreset.name }}</div>
                      <div v-if="selectedPreset.description" class="text-xs">{{ selectedPreset.description }}</div>
                    </div>
                  </div>

                  <form @submit.prevent="handleCreate" class="space-y-4">
                    <!-- Position -->
                    <div class="grid grid-cols-2 gap-4">
                      <div class="form-control">
                        <label class="label">
                          <span class="label-text">Position Q</span>
                        </label>
                        <input
                          v-model.number="formData.position.q"
                          type="number"
                          class="input input-bordered"
                          placeholder="0"
                          required
                        />
                      </div>
                      <div class="form-control">
                        <label class="label">
                          <span class="label-text">Position R</span>
                        </label>
                        <input
                          v-model.number="formData.position.r"
                          type="number"
                          class="input input-bordered"
                          placeholder="0"
                          required
                        />
                      </div>
                    </div>

                    <!-- Title -->
                    <div class="form-control">
                      <label class="label">
                        <span class="label-text">Title</span>
                      </label>
                      <input
                        v-model="formData.title"
                        type="text"
                        class="input input-bordered"
                        placeholder="Enter hex grid title"
                        required
                        @input="generateName"
                      />
                    </div>

                    <!-- Name (auto-generated) -->
                    <div class="form-control">
                      <label class="label">
                        <span class="label-text">Name (auto-generated)</span>
                      </label>
                      <input
                        v-model="formData.name"
                        type="text"
                        class="input input-bordered"
                        placeholder="name_will_be_generated"
                        readonly
                      />
                    </div>

                    <!-- Preset Info -->
                    <div class="text-xs text-base-content/60 space-y-1">
                      <div v-if="selectedPreset.data.icon">Icon: {{ selectedPreset.data.icon }}</div>
                      <div v-if="selectedPreset.data.splashScreen">Splash Screen: {{ selectedPreset.data.splashScreen }}</div>
                      <div v-if="selectedPreset.data.splashAudio">Splash Audio: {{ selectedPreset.data.splashAudio }}</div>
                      <div v-if="selectedPreset.data.parameters && Object.keys(selectedPreset.data.parameters).length > 0">
                        Parameters: {{ Object.keys(selectedPreset.data.parameters).length }} configured
                      </div>
                    </div>

                    <!-- Error Display -->
                    <ErrorAlert v-if="saveError" :message="saveError" />

                    <!-- Actions -->
                    <div class="flex justify-end gap-2 mt-6">
                      <button
                        type="button"
                        class="btn"
                        @click="selectedPreset = null"
                        :disabled="saving"
                      >
                        Back
                      </button>
                      <button
                        type="submit"
                        class="btn btn-primary"
                        :disabled="saving || !formData.title"
                      >
                        <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                        {{ saving ? 'Creating...' : 'Create' }}
                      </button>
                    </div>
                  </form>
                </div>

                <!-- Preset Selection -->
                <div v-else class="space-y-4">
                  <!-- Presets List -->
                  <div v-if="presets.length > 0" class="space-y-2">
                    <label class="label">
                      <span class="label-text font-semibold">Select a Preset</span>
                    </label>
                    <div class="max-h-96 overflow-y-auto space-y-2 border border-base-300 rounded-lg p-2">
                      <button
                        v-for="preset in presets"
                        :key="preset.id"
                        type="button"
                        class="btn btn-outline w-full justify-start text-left h-auto min-h-[3rem] py-2"
                        @click="selectPreset(preset)"
                      >
                        <div class="flex flex-col items-start gap-0.5">
                          <div class="font-semibold">{{ preset.title || preset.name }}</div>
                          <div v-if="preset.description" class="text-xs opacity-60 leading-tight">
                            {{ preset.description }}
                          </div>
                        </div>
                      </button>
                    </div>
                  </div>

                  <!-- Empty State -->
                  <div v-else class="text-center py-8 text-base-content/50">
                    <p>No presets available</p>
                  </div>

                  <!-- Custom Button -->
                  <div class="divider">OR</div>
                  <button
                    type="button"
                    class="btn btn-outline w-full"
                    @click="$emit('custom')"
                  >
                    <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    Custom
                  </button>

                  <!-- Close Button -->
                  <div class="flex justify-end mt-4">
                    <button
                      type="button"
                      class="btn"
                      @click="$emit('close')"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
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
import { useHexGrids } from '@/composables/useHexGrids';
import { type HexGridPreset } from '@/composables/useHexGridPresets';
import ErrorAlert from '@components/ErrorAlert.vue';

const props = defineProps<{
  worldId: string;
  presets: HexGridPreset[];
  loading: boolean;
  initialPosition?: { q: number; r: number } | null;
}>();

const emit = defineEmits<{
  close: [];
  custom: [];
  created: [];
}>();

const { createHexGrid } = useHexGrids(props.worldId);

const selectedPreset = ref<HexGridPreset | null>(null);
const saving = ref(false);
const saveError = ref<string | null>(null);

const formData = ref({
  position: { q: 0, r: 0 },
  title: '',
  name: '',
});

// Initialize position if provided
watch(() => props.initialPosition, (pos) => {
  if (pos) {
    formData.value.position = { ...pos };
  }
}, { immediate: true });

/**
 * Generate technical name from title
 */
const generateName = () => {
  const title = formData.value.title.trim();
  if (title) {
    formData.value.name = title
      .toLowerCase()
      .replace(/\s+/g, '_')
      .replace(/[^a-z0-9_]/g, '');
  } else {
    formData.value.name = '';
  }
};

/**
 * Select a preset
 */
const selectPreset = (preset: HexGridPreset) => {
  selectedPreset.value = preset;
  formData.value.title = '';
  formData.value.name = '';
};

/**
 * Handle create
 */
const handleCreate = async () => {
  if (!selectedPreset.value || !formData.value.title) {
    return;
  }

  saving.value = true;
  saveError.value = null;

  try {
    // Build publicData from preset
    const publicData: any = {
      position: formData.value.position,
      name: formData.value.name,
      title: formData.value.title,
      description: selectedPreset.value.description || '',
    };

    // Add preset data
    if (selectedPreset.value.data.icon) {
      publicData.icon = selectedPreset.value.data.icon;
    }
    if (selectedPreset.value.data.splashScreen) {
      publicData.splashScreen = selectedPreset.value.data.splashScreen;
    }
    if (selectedPreset.value.data.splashAudio) {
      publicData.splashScreenAudio = selectedPreset.value.data.splashAudio;
    }

    // Entry Point is always "not set" - explicitly set to null
    publicData.entryPoint = null;

    // Transform parameters: replace '-dot-' with '.' in keys
    const transformedParameters: Record<string, string> = {};
    if (selectedPreset.value.data.parameters) {
      Object.entries(selectedPreset.value.data.parameters).forEach(([key, value]) => {
        const transformedKey = key.replace(/-dot-/g, '.');
        transformedParameters[transformedKey] = value;
      });
    }

    // Build request payload
    const payload = {
      publicData: publicData,
      enabled: true,
      parameters: transformedParameters,
      areas: {}
    };

    await createHexGrid(payload);
    emit('created');
  } catch (err) {
    saveError.value = `Failed to create hex grid: ${(err as Error).message}`;
  } finally {
    saving.value = false;
  }
};
</script>
