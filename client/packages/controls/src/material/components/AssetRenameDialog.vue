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
            <DialogPanel class="w-full max-w-md transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-lg font-bold mb-4">
                Rename Asset
              </DialogTitle>

              <!-- Current Path -->
              <div class="mb-4 p-3 bg-base-200 rounded">
                <p class="text-xs text-base-content/60 mb-1">Current path:</p>
                <p class="text-sm font-mono break-all">{{ assetPath }}</p>
              </div>

              <!-- New Name Input -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">New filename</span>
                  <span class="label-text-alt text-error" v-if="!newName.trim()">Required</span>
                </label>
                <input
                  v-model="newName"
                  type="text"
                  class="input input-bordered"
                  placeholder="filename.png"
                  @keyup.enter="handleRename"
                  autofocus
                />
                <label class="label">
                  <span class="label-text-alt">Only filename (with extension)</span>
                </label>
              </div>

              <!-- New Path Preview -->
              <div v-if="newName.trim()" class="mb-4 p-3 bg-success/10 border border-success rounded">
                <p class="text-xs text-base-content/60 mb-1">New path:</p>
                <p class="text-sm font-mono break-all">{{ newPath }}</p>
              </div>

              <!-- Error -->
              <div v-if="error" class="alert alert-error mb-4">
                <svg class="stroke-current flex-shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ error }}</span>
              </div>

              <!-- Actions -->
              <div class="mt-6 flex justify-end gap-2">
                <button class="btn btn-ghost" @click="emit('close')" :disabled="renaming">
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  @click="handleRename"
                  :disabled="!newName.trim() || renaming"
                >
                  <span v-if="renaming" class="loading loading-spinner loading-sm mr-2"></span>
                  {{ renaming ? 'Renaming...' : 'Rename' }}
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
import { ref, computed } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { assetService } from '@/services/AssetService';

const props = defineProps<{
  worldId: string;
  assetPath: string;
}>();

const emit = defineEmits<{
  'close': [];
  'renamed': [];
}>();

// Extract current filename and folder
const extractPathParts = (path: string) => {
  // Remove collection prefix (w:, r:, p:, xyz:)
  let cleanPath = path;
  const colonPos = path.indexOf(':');
  if (colonPos > 0) {
    cleanPath = path.substring(colonPos + 1);
  }

  const lastSlash = cleanPath.lastIndexOf('/');
  const folder = lastSlash >= 0 ? cleanPath.substring(0, lastSlash) : '';
  const filename = lastSlash >= 0 ? cleanPath.substring(lastSlash + 1) : cleanPath;

  return { folder, filename, prefix: colonPos > 0 ? path.substring(0, colonPos + 1) : '' };
};

const { folder, filename, prefix } = extractPathParts(props.assetPath);

const newName = ref(filename);
const renaming = ref(false);
const error = ref<string | null>(null);

// New path preview
const newPath = computed(() => {
  if (!newName.value.trim()) return '';
  const path = folder ? `${folder}/${newName.value}` : newName.value;
  return prefix + path;
});

/**
 * Handle rename
 */
const handleRename = async () => {
  if (!newName.value.trim() || renaming.value) return;

  // Check if name changed
  if (newName.value === filename) {
    emit('close');
    return;
  }

  renaming.value = true;
  error.value = null;

  try {
    // Use duplicate + delete to rename
    const targetPath = folder ? `${folder}/${newName.value}` : newName.value;

    // Step 1: Duplicate to new name
    await assetService.duplicateAsset(props.worldId, props.assetPath, targetPath);

    // Step 2: Delete original
    await assetService.deleteAsset(props.worldId, props.assetPath);

    console.log(`Asset renamed: ${props.assetPath} -> ${targetPath}`);

    // Close and notify parent
    emit('renamed');
    emit('close');

  } catch (e) {
    console.error('Failed to rename asset:', e);
    error.value = e instanceof Error ? e.message : 'Failed to rename asset';
  } finally {
    renaming.value = false;
  }
};
</script>
