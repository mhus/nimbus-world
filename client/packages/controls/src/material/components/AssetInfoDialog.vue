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
                Asset Info
              </DialogTitle>

              <div class="mb-4 p-3 bg-base-200 rounded">
                <p class="text-sm font-mono break-all">{{ assetPath }}</p>
              </div>

              <!-- Asset Preview Section -->
              <div v-if="isAudioFile || isImageFile" class="mb-4 p-4 bg-base-200 rounded">
                <div class="text-sm font-semibold mb-2">Vorschau</div>

                <!-- Image Preview -->
                <img
                  v-if="isImageFile"
                  :src="assetUrl"
                  :alt="assetPath"
                  class="max-w-full h-auto rounded border border-base-300"
                  style="image-rendering: pixelated;"
                  @error="handleImageError"
                />

                <!-- Audio Preview -->
                <div v-if="isAudioFile" class="flex items-center gap-3">
                  <button
                    class="btn btn-circle btn-primary"
                    @click="toggleAudioPlayback"
                    :disabled="audioLoading"
                  >
                    <span v-if="audioLoading" class="loading loading-spinner loading-sm"></span>
                    <svg v-else-if="!isPlaying" class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M8 5v14l11-7z" />
                    </svg>
                    <svg v-else class="w-6 h-6" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
                    </svg>
                  </button>
                  <div class="flex-1">
                    <div class="text-xs text-base-content/70 mb-1">{{ audioFileName }}</div>
                    <div class="text-xs text-base-content/50">{{ audioFormatText }}</div>
                  </div>
                </div>

                <!-- Audio Error -->
                <div v-if="audioError" class="alert alert-warning mt-2">
                  <svg class="stroke-current flex-shrink-0 h-5 w-5" fill="none" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  <span class="text-xs">{{ audioError }}</span>
                </div>
              </div>

              <div class="space-y-4">
                <!-- Description (Required) -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Beschreibung *</span>
                  </label>
                  <textarea
                    v-model="localInfo.description"
                    class="textarea textarea-bordered h-24"
                    placeholder="Beschreibung des Assets..."
                  ></textarea>
                </div>

                <!-- Source -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Source</span>
                  </label>
                  <input
                    v-model="localInfo.source"
                    type="text"
                    class="input input-bordered"
                    placeholder="Source of the asset..."
                    :disabled="localInfo.licenseFixed"
                  />
                </div>

                <!-- Author -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Author</span>
                  </label>
                  <input
                    v-model="localInfo.author"
                    type="text"
                    class="input input-bordered"
                    placeholder="Author of the asset..."
                    :disabled="localInfo.licenseFixed"
                  />
                </div>

                <!-- License -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">License</span>
                  </label>
                  <input
                    v-model="localInfo.license"
                    type="text"
                    class="input input-bordered"
                    placeholder="License information..."
                    :disabled="localInfo.licenseFixed"
                  />
                </div>

                <!-- License Fixed -->
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-4">
                    <span class="label-text font-semibold">License Fixed</span>
                    <input
                      v-model="localInfo.licenseFixed"
                      type="checkbox"
                      class="checkbox checkbox-sm"
                    />
                  </label>
                  <label class="label">
                    <span class="label-text-alt">When checked, source/author/license fields are read-only</span>
                  </label>
                </div>

                <!-- Custom Key-Value Pairs -->
                <div class="space-y-2">
                  <label class="label">
                    <span class="label-text font-semibold">Weitere Attribute</span>
                  </label>

                  <div
                    v-for="(value, key) in customFields"
                    :key="key"
                    class="flex gap-2 items-start"
                  >
                    <input
                      v-model="customFieldKeys[key]"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Key"
                      @blur="handleKeyChange(key, customFieldKeys[key])"
                    />
                    <input
                      v-model="customFields[key]"
                      type="text"
                      class="input input-bordered input-sm flex-1"
                      placeholder="Value"
                    />
                    <button
                      class="btn btn-ghost btn-sm btn-square text-error"
                      @click="removeField(key)"
                    >
                      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>

                  <button
                    class="btn btn-outline btn-sm"
                    @click="addField"
                  >
                    <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                    </svg>
                    Attribut hinzufügen
                  </button>
                </div>

                <!-- Loading -->
                <div v-if="loading" class="flex justify-center py-4">
                  <span class="loading loading-spinner loading-lg"></span>
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
                <button class="btn btn-ghost" @click="emit('close')" :disabled="loading">
                  Abbrechen
                </button>
                <button
                  class="btn btn-primary"
                  @click="handleSave"
                  :disabled="!localInfo.description.trim() || loading"
                >
                  <span v-if="loading" class="loading loading-spinner loading-sm mr-2"></span>
                  {{ loading ? 'Speichert...' : 'Speichern' }}
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { assetInfoService, type AssetInfo } from '@/services/AssetInfoService';
import { assetService } from '@/services/AssetService';

interface Props {
  worldId: string;
  assetPath: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const localInfo = reactive<AssetInfo>({
  description: '',
  source: '',
  author: '',
  license: '',
  licenseFixed: false,
});

const customFields = reactive<Record<string, string>>({});
const customFieldKeys = reactive<Record<string, string>>({});
const loading = ref(false);
const errorMessage = ref<string | null>(null);

// Duplicate functionality removed - use drag & drop in MC Asset Editor instead

// Audio preview state
const audioElement = ref<HTMLAudioElement | null>(null);
const isPlaying = ref(false);
const audioLoading = ref(false);
const audioError = ref<string | null>(null);

// Computed properties for file type detection
const assetExtension = computed(() => {
  const parts = props.assetPath.split('.');
  return parts.length > 1 ? `.${parts[parts.length - 1].toLowerCase()}` : '';
});

const isAudioFile = computed(() => {
  const audioExtensions = ['.mp3', '.wav', '.ogg'];
  return audioExtensions.includes(assetExtension.value);
});

const isImageFile = computed(() => {
  const imageExtensions = ['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg'];
  return imageExtensions.includes(assetExtension.value);
});

const assetUrl = computed(() => {
  return assetService.getAssetUrl(props.worldId, props.assetPath);
});

const audioFileName = computed(() => {
  const parts = props.assetPath.split('/');
  return parts[parts.length - 1];
});

const audioFormatText = computed(() => {
  const ext = assetExtension.value.replace('.', '').toUpperCase();
  return `${ext} Audio`;
});

// Load existing info
onMounted(async () => {
  loading.value = true;
  errorMessage.value = null;

  try {
    const info = await assetInfoService.getAssetInfo(props.worldId, props.assetPath);

    // Set fixed fields
    localInfo.description = info.description || '';
    localInfo.source = info.source || '';
    localInfo.author = info.author || '';
    localInfo.license = info.license || '';
    localInfo.licenseFixed = info.licenseFixed || false;

    // Extract custom fields (exclude fixed fields)
    const fixedFields = ['description', 'source', 'author', 'license', 'licenseFixed'];
    Object.keys(info).forEach((key) => {
      if (!fixedFields.includes(key)) {
        const value = info[key];
        customFields[key] = String(value);
        customFieldKeys[key] = key;
      }
    });
  } catch (error) {
    errorMessage.value = 'Fehler beim Laden der Info-Datei';
    console.error('Failed to load asset info', error);
  } finally {
    loading.value = false;
  }
});

const addField = () => {
  const newKey = `key${Object.keys(customFields).length + 1}`;
  customFields[newKey] = '';
  customFieldKeys[newKey] = newKey;
};

const removeField = (key: string) => {
  delete customFields[key];
  delete customFieldKeys[key];
};

const handleKeyChange = (oldKey: string, newKey: string) => {
  if (oldKey === newKey) return;
  if (!newKey.trim()) return;

  // Check if new key already exists
  if (newKey in customFields && newKey !== oldKey) {
    errorMessage.value = `Key "${newKey}" existiert bereits`;
    customFieldKeys[oldKey] = oldKey;
    return;
  }

  // Rename key
  const value = customFields[oldKey];
  delete customFields[oldKey];
  customFields[newKey] = value;
  customFieldKeys[newKey] = newKey;
  delete customFieldKeys[oldKey];
};

// Audio preview functions
const toggleAudioPlayback = async () => {
  if (!isAudioFile.value) return;

  audioError.value = null;

  try {
    // Create audio element if not exists
    if (!audioElement.value) {
      audioLoading.value = true;
      audioElement.value = new Audio(assetUrl.value);

      // Setup event listeners
      audioElement.value.addEventListener('ended', () => {
        isPlaying.value = false;
      });

      audioElement.value.addEventListener('error', (e) => {
        audioError.value = 'Fehler beim Laden der Audio-Datei';
        isPlaying.value = false;
        audioLoading.value = false;
      });

      audioElement.value.addEventListener('canplay', () => {
        audioLoading.value = false;
      });
    }

    // Toggle playback
    if (isPlaying.value) {
      audioElement.value.pause();
      isPlaying.value = false;
    } else {
      await audioElement.value.play();
      isPlaying.value = true;
    }
  } catch (error) {
    audioError.value = 'Fehler beim Abspielen der Audio-Datei';
    isPlaying.value = false;
    audioLoading.value = false;
    console.error('Audio playback error:', error);
  }
};

const handleImageError = () => {
  console.error('Failed to load image preview');
};

// Cleanup audio on unmount
onUnmounted(() => {
  if (audioElement.value) {
    audioElement.value.pause();
    audioElement.value = null;
  }
});

const handleSave = async () => {
  if (!localInfo.description.trim()) {
    errorMessage.value = 'Beschreibung ist erforderlich';
    return;
  }

  loading.value = true;
  errorMessage.value = null;

  try {
    // Build info object with fixed fields
    const info: AssetInfo = {
      description: localInfo.description.trim(),
      source: localInfo.source?.trim() || '',
      author: localInfo.author?.trim() || '',
      license: localInfo.license?.trim() || '',
      licenseFixed: localInfo.licenseFixed || false,
    };

    // Add custom fields with proper keys (exclude licenseFixed from custom fields)
    Object.keys(customFields).forEach((oldKey) => {
      const actualKey = customFieldKeys[oldKey];
      if (actualKey && actualKey.trim() && customFields[oldKey] && actualKey !== 'licenseFixed') {
        info[actualKey.trim()] = customFields[oldKey];
      }
    });

    await assetInfoService.saveAssetInfo(props.worldId, props.assetPath, info);
    emit('saved');
  } catch (error) {
    errorMessage.value = 'Fehler beim Speichern der Info-Datei';
    console.error('Failed to save asset info', error);
  } finally {
    loading.value = false;
  }
};

// Duplicate functionality removed - use drag & drop in MC Asset Editor for copying
</script>
