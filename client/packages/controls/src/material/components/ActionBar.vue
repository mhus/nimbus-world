<template>
  <div class="action-bar bg-base-200 p-2 border-t border-base-300 flex gap-2 flex-wrap">
    <!-- Upload Button -->
    <button
      class="btn btn-sm btn-primary"
      :disabled="!worldId"
      @click="$emit('upload')"
      title="Upload files"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
      </svg>
    </button>

    <!-- Download Button -->
    <button
      class="btn btn-sm"
      :disabled="selectedFiles.length === 0"
      @click="$emit('download')"
      title="Download selected files"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
      </svg>
    </button>

    <!-- Delete Button -->
    <button
      class="btn btn-sm btn-error"
      :disabled="selectedFiles.length === 0"
      @click="$emit('delete')"
      title="Delete selected files"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
      </svg>
    </button>

    <!-- Create Folder Button -->
    <button
      class="btn btn-sm"
      :disabled="!worldId"
      @click="$emit('createFolder')"
      title="Create new folder"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
      </svg>
    </button>

    <!-- Refresh Button -->
    <button
      class="btn btn-sm"
      :disabled="!worldId"
      @click="$emit('refresh')"
      title="Refresh view"
    >
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
      </svg>
    </button>

    <!-- Spacer -->
    <div class="flex-1"></div>

    <!-- Selection Count -->
    <div v-if="selectedFiles.length > 0" class="flex items-center text-sm text-base-content/70">
      {{ selectedFiles.length }} selected
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Asset } from '@/services/AssetService';

withDefaults(defineProps<{
  worldId?: string;
  currentPath?: string;
  selectedFiles?: Asset[];
  panel: 'left' | 'right';
}>(), {
  worldId: '',
  currentPath: '',
  selectedFiles: () => [],
});

defineEmits<{
  'upload': [];
  'download': [];
  'delete': [];
  'refresh': [];
  'createFolder': [];
}>();
</script>

<style scoped>
.action-bar {
  flex-shrink: 0;
}
</style>
