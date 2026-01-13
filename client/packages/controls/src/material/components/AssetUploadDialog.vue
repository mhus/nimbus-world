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
            <DialogPanel class="w-full max-w-2xl transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                Upload Asset
              </DialogTitle>

              <div class="space-y-4">
                <!-- File Drop Zone -->
                <div
                  class="border-2 border-dashed rounded-lg p-8 text-center hover:border-primary transition-colors cursor-pointer"
                  :class="{
                    'border-primary bg-primary/10': isDragging,
                    'border-base-300': !isDragging
                  }"
                  @drop.prevent="handleDrop"
                  @dragover.prevent="isDragging = true"
                  @dragleave.prevent="isDragging = false"
                >
                  <input
                    ref="fileInput"
                    type="file"
                    class="hidden"
                    @change="handleFileSelect"
                  />
                  <div v-if="!selectedFile" @click="$refs.fileInput?.click()">
                    <svg class="mx-auto h-12 w-12 text-base-content/40" stroke="currentColor" fill="none" viewBox="0 0 48 48">
                      <path d="M28 8H12a4 4 0 00-4 4v20m32-12v8m0 0v8a4 4 0 01-4 4H12a4 4 0 01-4-4v-4m32-4l-3.172-3.172a4 4 0 00-5.656 0L28 28M8 32l9.172-9.172a4 4 0 015.656 0L28 28m0 0l4 4m4-24h8m-4-4v8m-12 4h.02" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                    </svg>
                    <p class="mt-2 text-sm text-base-content/70">
                      <span class="font-semibold">Click to upload</span> or drag and drop
                    </p>
                    <p class="text-xs text-base-content/50">PNG, JPG, OBJ, JSON, etc.</p>
                  </div>
                  <div v-else class="text-left">
                    <div class="flex items-center justify-between p-3 bg-base-200 rounded">
                      <div class="flex items-center space-x-3">
                        <span class="text-2xl">{{ getFileIcon(selectedFile) }}</span>
                        <div>
                          <p class="font-medium">{{ selectedFile.name }}</p>
                          <p class="text-sm text-base-content/60">{{ formatSize(selectedFile.size) }}</p>
                        </div>
                      </div>
                      <button
                        class="btn btn-ghost btn-sm btn-square"
                        @click="selectedFile = null"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                      </button>
                    </div>
                  </div>
                </div>

                <!-- Asset Path -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Asset Path</span>
                  </label>
                  <input
                    v-model="assetPath"
                    type="text"
                    class="input input-bordered"
                    placeholder="textures/block/custom/my_texture.png"
                  />
                  <label class="label">
                    <span class="label-text-alt">Relative path in the assets directory</span>
                  </label>
                </div>

                <!-- Upload Progress -->
                <div v-if="uploading" class="space-y-2">
                  <progress class="progress progress-primary w-full" :value="uploadProgress" max="100"></progress>
                  <p class="text-sm text-center text-base-content/70">Uploading... {{ uploadProgress }}%</p>
                </div>

                <!-- Error -->
                <div v-if="errorMessage" class="alert alert-error">
                  <svg class="stroke-current flex-shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  <span>{{ errorMessage }}</span>
                </div>
              </div>

              <!-- Actions -->
              <div class="mt-6 flex justify-end gap-2">
                <button class="btn btn-ghost" @click="emit('close')" :disabled="uploading">
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  @click="handleUpload"
                  :disabled="!selectedFile || !assetPath || uploading"
                >
                  <span v-if="uploading" class="loading loading-spinner loading-sm mr-2"></span>
                  {{ uploading ? 'Uploading...' : 'Upload' }}
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
import { ref, watch } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { useAssets } from '@/composables/useAssets';

interface Props {
  worldId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'uploaded'): void;
}>();

const { uploadAsset, uploadProgress } = useAssets(props.worldId);

const isDragging = ref(false);
const selectedFile = ref<File | null>(null);
const assetPath = ref('');
const uploading = ref(false);
const errorMessage = ref<string | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);

// Auto-fill asset path when file is selected
watch(selectedFile, (file) => {
  if (file && !assetPath.value) {
    assetPath.value = `custom/${file.name}`;
  }
});

const handleFileSelect = (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files[0]) {
    selectedFile.value = target.files[0];
  }
};

const handleDrop = (event: DragEvent) => {
  isDragging.value = false;
  if (event.dataTransfer?.files && event.dataTransfer.files[0]) {
    selectedFile.value = event.dataTransfer.files[0];
  }
};

const handleUpload = async () => {
  if (!selectedFile.value || !assetPath.value) return;

  uploading.value = true;
  errorMessage.value = null;

  try {
    const success = await uploadAsset(assetPath.value, selectedFile.value);
    if (success) {
      emit('uploaded');
    } else {
      errorMessage.value = 'Failed to upload asset';
    }
  } catch (err) {
    errorMessage.value = 'Failed to upload asset';
  } finally {
    uploading.value = false;
  }
};

const getFileIcon = (file: File): string => {
  const type = file.type;
  if (type.startsWith('image/')) return '🖼️';
  if (type.startsWith('audio/')) return '🔊';
  if (type === 'application/json') return '📄';
  return '📦';
};

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
</script>
