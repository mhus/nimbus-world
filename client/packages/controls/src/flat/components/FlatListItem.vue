<template>
  <div class="card bg-base-100 shadow-sm hover:shadow-md transition-shadow">
    <div class="card-body p-4">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <!-- Flat Info -->
        <div class="flex-1 space-y-1">
          <div class="flex items-center gap-2">
            <div class="flex flex-col">
              <h3 v-if="flat.title" class="font-bold text-lg">{{ flat.title }}</h3>
              <span class="text-sm" :class="flat.title ? 'text-base-content/70' : 'font-bold text-lg'">{{ flat.flatId }}</span>
            </div>
            <span class="badge badge-sm badge-outline">{{ flat.sizeX }}x{{ flat.sizeZ }}</span>
          </div>
          <div class="text-sm text-base-content/70 space-x-4">
            <span>Mount: ({{ flat.mountX }}, {{ flat.mountZ }})</span>
            <span>Ocean: {{ flat.oceanLevel }}</span>
            <span>Layer: {{ flat.layerDataId }}</span>
          </div>
          <div class="text-xs text-base-content/50">
            Created: {{ formatDate(flat.createdAt) }}
          </div>
        </div>

        <!-- Actions -->
        <div class="flex gap-2">
          <button
            class="btn btn-sm btn-primary"
            @click="$emit('view')"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            View
          </button>
          <button
            class="btn btn-sm btn-error"
            @click="$emit('delete')"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
            Delete
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Flat {
  id: string;
  worldId: string;
  layerDataId: string;
  flatId: string;
  title: string | null;
  description: string | null;
  sizeX: number;
  sizeZ: number;
  mountX: number;
  mountZ: number;
  oceanLevel: number;
  createdAt: string;
  updatedAt: string;
}

defineProps<{
  flat: Flat;
}>();

defineEmits<{
  view: [];
  delete: [];
}>();

const formatDate = (dateString: string) => {
  const date = new Date(dateString);
  return date.toLocaleString();
};
</script>
