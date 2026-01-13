<template>
  <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4">
    <div
      v-for="asset in assets"
      :key="asset.path"
      class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer flex flex-col"
      @click="emit('asset-click', asset)"
    >
      <figure class="aspect-square bg-base-200 flex items-center justify-center p-4">
        <img
          v-if="isImage(asset)"
          :src="getUrl(asset.path)"
          :alt="asset.path"
          class="w-full h-full object-contain"
          style="image-rendering: pixelated;"
          @error="(e: Event) => ((e.target as HTMLImageElement).style.display = 'none')"
        />
        <span v-else class="text-6xl">{{ getIcon(asset) }}</span>
      </figure>
      <div class="card-body p-3 flex-shrink-0">
        <h3 class="text-sm font-medium truncate" :title="asset.path">
          {{ getFileName(asset.path) }}
        </h3>
        <p class="text-xs text-base-content/60">
          {{ formatSize(asset.size) }}
        </p>
        <div class="card-actions justify-end mt-2">
          <button
            class="btn btn-ghost btn-xs"
            @click.stop="downloadAsset(asset)"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
          </button>
          <button
            class="btn btn-ghost btn-xs text-error"
            @click.stop="emit('delete', asset)"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Asset } from '@/services/AssetService';

interface Props {
  assets: Asset[];
  getUrl: (path: string) => string;
  isImage: (asset: Asset) => boolean;
  getIcon: (asset: Asset) => string;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'delete', asset: Asset): void;
  (e: 'asset-click', asset: Asset): void;
}>();

const getFileName = (path: string): string => {
  const parts = path.split('/');
  return parts[parts.length - 1];
};

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const downloadAsset = (asset: Asset) => {
  const props = defineProps<Props>();
  window.open(props.getUrl(asset.path), '_blank');
};
</script>
