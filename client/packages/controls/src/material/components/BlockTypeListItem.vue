<template>
  <div class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer" @click="emit('edit', blockType)">
    <div class="card-body">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h3 class="card-title text-lg">
            ID: {{ blockType.id }}
          </h3>
          <p v-if="blockType.title" class="text-base-content/90 text-sm font-semibold">
            {{ blockType.title }}
          </p>
          <p class="text-base-content/70 text-sm mt-1">
            {{ blockType.description || 'No description' }}
          </p>
        </div>
        <button
          class="btn btn-ghost btn-sm btn-square"
          @click.stop="emit('delete', blockType)"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <div class="badge badge-outline">
          {{ modifierCount }} {{ modifierCount === 1 ? 'status' : 'statuses' }}
        </div>
        <div v-if="blockType.initialStatus !== undefined" class="badge badge-primary badge-outline">
          Initial: {{ blockType.initialStatus }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { BlockType } from '@nimbus/shared';

interface Props {
  blockType: BlockType;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', blockType: BlockType): void;
  (e: 'delete', blockType: BlockType): void;
}>();

const modifierCount = computed(() => {
  return Object.keys(props.blockType.modifiers || {}).length;
});
</script>
