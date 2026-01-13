<template>
  <div class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer" @click="$emit('view', chunk.chunk)">
    <div class="card-body">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h3 class="card-title text-lg">
            Chunk: {{ chunk.chunk }}
            <span v-if="chunk.compressed" class="badge badge-success badge-sm">Compressed</span>
          </h3>
          <div class="mt-2 space-y-1 text-sm text-base-content/70">
            <p>Storage ID: {{ chunk.storageId || 'N/A' }}</p>
            <p>Created: {{ formatDate(chunk.createdAt) }}</p>
            <p>Updated: {{ formatDate(chunk.updatedAt) }}</p>
          </div>
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-warning btn-sm"
            @click.stop="$emit('markDirty', chunk.chunk)"
            title="Mark chunk as dirty for regeneration"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Dirty
          </button>
          <button
            class="btn btn-ghost btn-sm"
            @click.stop="$emit('view', chunk.chunk)"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
            View
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChunkMetadata } from '@/services/ChunkService';

interface Props {
  chunk: ChunkMetadata;
}

defineProps<Props>();

defineEmits<{
  (e: 'view', chunkKey: string): void;
  (e: 'markDirty', chunkKey: string): void;
}>();

function formatDate(dateStr: string): string {
  if (!dateStr) return 'N/A';
  try {
    return new Date(dateStr).toLocaleString();
  } catch {
    return dateStr;
  }
}
</script>
