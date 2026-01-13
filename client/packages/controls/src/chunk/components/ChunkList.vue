<template>
  <div class="grid grid-cols-1 gap-4">
    <ChunkListItem
      v-for="chunk in chunks"
      :key="chunk.id"
      :chunk="chunk"
      @view="$emit('view', chunk.chunk)"
      @mark-dirty="$emit('markDirty', chunk.chunk)"
    />
  </div>
</template>

<script setup lang="ts">
import type { ChunkMetadata } from '@/services/ChunkService';
import ChunkListItem from './ChunkListItem.vue';

interface Props {
  chunks: ChunkMetadata[];
  loading: boolean;
}

defineProps<Props>();

defineEmits<{
  (e: 'view', chunkKey: string): void;
  (e: 'markDirty', chunkKey: string): void;
}>();
</script>
