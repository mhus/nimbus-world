<template>
  <div class="space-y-2">
    <div
      v-for="model in models"
      :key="model.id"
      class="card bg-base-200 hover:bg-base-300 transition-colors"
    >
      <div class="card-body p-4">
        <div class="flex items-center justify-between">
          <div class="flex-1">
            <h4 class="font-semibold">
              {{ model.title || model.name || 'Unnamed Model' }}
            </h4>
            <div class="text-sm text-base-content/70 space-y-1 mt-1">
              <div>Mount: ({{ model.mountX }}, {{ model.mountY }}, {{ model.mountZ }})</div>
              <div>Order: {{ model.order }}</div>
              <div v-if="model.rotation !== 0">Rotation: {{ model.rotation * 90 }}°</div>
              <div v-if="model.referenceModelId">
                Reference: {{ model.referenceModelId }}
              </div>
            </div>
          </div>
          <div class="flex gap-2">
            <button
              class="btn btn-sm btn-ghost"
              @click="$emit('edit', model)"
              title="Edit model"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
              </svg>
            </button>
            <button
              class="btn btn-sm btn-ghost btn-error"
              @click="$emit('delete', model)"
              title="Delete model"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="models.length === 0" class="text-center py-8">
      <p class="text-base-content/50">No models found for this layer</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { LayerModelDto } from '@nimbus/shared';

interface Props {
  models: LayerModelDto[];
}

defineProps<Props>();

defineEmits<{
  (e: 'edit', model: LayerModelDto): void;
  (e: 'delete', model: LayerModelDto): void;
}>();
</script>
