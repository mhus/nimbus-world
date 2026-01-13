<template>
  <div class="info-bar bg-base-200 p-3 border-t border-base-300 text-sm">
    <!-- Selected Asset Info -->
    <div v-if="selectedAsset" class="flex items-center gap-4 flex-wrap">
      <div class="font-semibold truncate max-w-xs" :title="selectedAsset.path">
        {{ selectedAsset.path }}
      </div>
      <div class="text-base-content/70">
        Size: {{ formatSize(selectedAsset.size) }}
      </div>
      <div class="text-base-content/70">
        Type: {{ selectedAsset.mimeType || 'unknown' }}
      </div>
      <div v-if="selectedAsset.lastModified" class="text-base-content/70">
        Modified: {{ formatDate(selectedAsset.lastModified) }}
      </div>
    </div>

    <!-- Folder Stats -->
    <div v-else-if="folderStats && folderStats.assetCount > 0" class="flex items-center gap-4">
      <div class="text-base-content/70">
        {{ folderStats.assetCount }} files
      </div>
      <div class="text-base-content/70">
        Total: {{ formatSize(folderStats.totalSize) }}
      </div>
    </div>

    <!-- Default Message -->
    <div v-else class="text-base-content/50">
      No selection
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Asset } from '@/services/AssetService';

defineProps<{
  selectedAsset: Asset | null;
  folderStats?: {
    assetCount: number;
    totalSize: number;
  };
}>();

/**
 * Format file size
 */
const formatSize = (bytes: number): string => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
};

/**
 * Format date
 */
const formatDate = (date: string | Date): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString() + ' ' + d.toLocaleTimeString();
};
</script>

<style scoped>
.info-bar {
  flex-shrink: 0;
}
</style>
