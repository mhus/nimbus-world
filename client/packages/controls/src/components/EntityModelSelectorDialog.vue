<template>
  <div class="modal modal-open">
    <div class="modal-box max-w-2xl">
      <h3 class="font-bold text-lg mb-4">Select Entity Model</h3>

      <!-- Search -->
      <div class="form-control mb-4">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search models..."
          class="input input-bordered w-full"
        />
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="flex justify-center py-8">
        <span class="loading loading-spinner loading-lg"></span>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="alert alert-error">
        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
        </svg>
        <span>{{ error }}</span>
      </div>

      <!-- Model List -->
      <div v-else class="max-h-96 overflow-y-auto">
        <div v-if="models.length === 0" class="text-center py-8 text-base-content/70">
          No models found
        </div>
        <div v-else class="grid grid-cols-1 gap-2">
          <button
            v-for="model in models"
            :key="model"
            class="btn btn-outline btn-sm justify-start"
            :class="{ 'btn-primary': model === currentModelId }"
            @click="handleSelect(model)"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
            <span class="flex-1 text-left font-mono text-xs">{{ model }}</span>
            <span v-if="model === currentModelId" class="badge badge-sm badge-primary">Current</span>
          </button>
        </div>
      </div>

      <!-- Actions -->
      <div class="modal-action">
        <button class="btn btn-ghost" @click="handleClose">
          Cancel
        </button>
      </div>
    </div>
    <div class="modal-backdrop" @click="handleClose"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { entityModelService } from '../entitymodel/services/EntityModelService';

interface Props {
  worldId: string;
  currentModelId?: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  select: [modelId: string];
}>();

const models = ref<string[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');

let debounceTimer: number | null = null;

const loadModels = async (query?: string) => {
  loading.value = true;
  error.value = null;

  try {
    // Load entity models with server-side filtering
    const response = await entityModelService.listEntityModels(props.worldId, query, 0, 1000);

    // Extract modelIds from entityModels (publicData has 'id' field)
    const modelIds = response.entityModels
      .map((model: any) => model.id)
      .filter((id: string | undefined) => id !== undefined);

    models.value = Array.from(new Set(modelIds)).sort();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load models';
    console.error('[EntityModelSelectorDialog] Failed to load models:', e);
  } finally {
    loading.value = false;
  }
};

// Watch searchQuery and reload with debounce
watch(searchQuery, (newQuery) => {
  if (debounceTimer !== null) {
    clearTimeout(debounceTimer);
  }

  debounceTimer = setTimeout(() => {
    loadModels(newQuery || undefined);
  }, 300) as unknown as number;
});

const handleSelect = (modelId: string) => {
  emit('select', modelId);
};

const handleClose = () => {
  emit('close');
};

onMounted(() => {
  loadModels();
});
</script>
