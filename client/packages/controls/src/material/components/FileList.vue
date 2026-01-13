<template>
  <div
    class="file-list p-2 h-full"
    @drop.prevent="handleDrop"
    @dragover.prevent="handleDragOver"
    @dragleave.prevent="handleDragLeave"
    :class="{ 'drop-active': isDragOver }"
  >
    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center p-8">
      <span class="loading loading-spinner loading-md"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="text-error text-sm p-4">
      {{ error }}
    </div>

    <!-- Empty State -->
    <div v-else-if="filteredAssets.length === 0" class="text-center p-8 text-base-content/50">
      <p>No assets in this folder</p>
      <p class="text-xs mt-2">Drag & drop files here to upload</p>
    </div>

    <!-- File Grid -->
    <div v-else class="grid grid-cols-2 sm:grid-cols-3 gap-4">
      <div
        v-for="asset in filteredAssets"
        :key="asset.path"
        class="file-item card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer flex flex-col relative group"
        :class="{ 'ring-2 ring-primary': isSelected(asset) }"
        draggable="true"
        @click="handleFileClick(asset, $event)"
        @dblclick="handleFileDoubleClick(asset)"
        @dragstart="handleDragStart(asset, $event)"
        @contextmenu.prevent="handleContextMenu(asset, $event)"
      >
        <!-- Rename Button (top-right corner, visible on hover) -->
        <button
          class="absolute top-1 right-1 btn btn-ghost btn-xs opacity-0 group-hover:opacity-100 transition-opacity z-10 bg-base-100"
          @click.stop="handleRenameClick(asset)"
          title="Rename (or right-click)"
        >
          <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
          </svg>
        </button>

        <!-- Preview with aspect-square -->
        <figure class="aspect-square bg-base-200 flex items-center justify-center p-4 relative">
          <img
            v-if="isImage(asset)"
            :src="getAssetUrl(asset.path)"
            :alt="getFileName(asset)"
            class="w-full h-full object-contain"
            style="image-rendering: pixelated;"
          />
          <div v-else-if="isAudioFile(asset)" class="flex flex-col items-center gap-2">
            <span class="text-6xl">{{ getIcon(asset) }}</span>
            <button
              class="btn btn-circle btn-sm btn-primary"
              @click.stop="toggleAudioPlayback(asset)"
              :title="isPlaying(asset) ? 'Stop' : 'Play'"
            >
              <svg v-if="!isPlaying(asset)" class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                <path d="M8 5v14l11-7z" />
              </svg>
              <svg v-else class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z" />
              </svg>
            </button>
          </div>
          <span v-else class="text-6xl">
            {{ getIcon(asset) }}
          </span>
        </figure>

        <!-- Card Body -->
        <div class="card-body p-3 flex-shrink-0">
          <h3 class="text-sm font-medium truncate" :title="getFileName(asset)">
            {{ getFileName(asset) }}
          </h3>
          <p class="text-xs text-base-content/60">
            {{ formatSize(asset.size) }}
          </p>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="filteredAssets.length > 0 && totalPages > 1" class="flex items-center justify-center gap-2 mt-4">
      <button
        class="btn btn-xs"
        :disabled="currentPage === 1"
        @click="previousPage"
      >
        ‹
      </button>
      <span class="text-xs">{{ currentPage }} / {{ totalPages }}</span>
      <button
        class="btn btn-xs"
        :disabled="currentPage >= totalPages"
        @click="nextPage"
      >
        ›
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useAssets } from '@/composables/useAssets';
import type { Asset } from '@/services/AssetService';

const props = withDefaults(defineProps<{
  worldId?: string;
  folderPath?: string;
  selectedFiles?: Asset[];
  searchQuery?: string;
  panel: 'left' | 'right';
}>(), {
  worldId: '',
  folderPath: '',
  selectedFiles: () => [],
  searchQuery: '',
});

const emit = defineEmits<{
  'fileSelect': [files: Asset[]];
  'fileDrop': [files: FileList];
  'assetMoved': [data: { sourcePanel: string; sourceWorldId: string; moved: boolean }];
  'assetClick': [asset: Asset];
  'assetRename': [asset: Asset];
}>();

// Use assets composable with folder filter
const assetsComposable = computed(() => {
  if (!props.worldId) return null;
  return useAssets(props.worldId, {
    folderPath: props.folderPath,
  });
});

const assets = computed(() => assetsComposable.value?.assets.value || []);
const loading = computed(() => assetsComposable.value?.loading.value || false);
const error = computed(() => assetsComposable.value?.error.value || null);
const getAssetUrl = computed(() => assetsComposable.value?.getAssetUrl || (() => ''));
const isImage = computed(() => assetsComposable.value?.isImage || (() => false));
const getIcon = computed(() => assetsComposable.value?.getIcon || (() => '📦'));

// Pagination
const currentPage = computed(() => assetsComposable.value?.currentPage.value || 1);
const totalPages = computed(() => assetsComposable.value?.totalPages.value || 0);

// Audio playback state
const audioElements = ref<Map<string, HTMLAudioElement>>(new Map());
const playingAssets = ref<Set<string>>(new Set());

// Drag & Drop state
const isDragOver = ref(false);

// Filter assets by folder path and search query
// Note: Asset paths may have prefixes like "w:", "r:", "p:", "xyz:" or none (legacy)
// Examples: "w:textures/sun/sun1.png", "textures/magic/red_book.png"
const filteredAssets = computed(() => {
  let filtered: Asset[];

  // If folderPath is set, backend already filtered by query
  // We just need to show direct children only (no nested subfolders)
  if (props.folderPath && props.folderPath !== '') {
    filtered = assets.value.filter(a => {
      // Extract path without collection prefix (w:, r:, p:, xyz:)
      let assetPath = a.path;
      const colonPos = assetPath.indexOf(':');
      if (colonPos > 0) {
        assetPath = assetPath.substring(colonPos + 1);
      }

      // Check if asset is direct child of folder
      const prefix = props.folderPath + '/';
      if (!assetPath.startsWith(prefix)) return false;

      // Only direct children (no nested subfolders)
      const remainder = assetPath.substring(prefix.length);
      return !remainder.includes('/');
    });
  } else {
    // Root: show all assets without folder (after prefix removal)
    filtered = assets.value.filter(a => {
      let assetPath = a.path;
      const colonPos = assetPath.indexOf(':');
      if (colonPos > 0) {
        assetPath = assetPath.substring(colonPos + 1);
      }
      return !assetPath.includes('/');
    });
  }

  // Apply search filter if search query is provided
  if (props.searchQuery && props.searchQuery.trim() !== '') {
    const searchLower = props.searchQuery.toLowerCase().trim();
    filtered = filtered.filter(a => {
      const fileName = getFileName(a).toLowerCase();
      return fileName.includes(searchLower);
    });
  }

  // Sort alphabetically by filename (case-insensitive)
  return filtered.sort((a, b) => {
    const nameA = getFileName(a).toLowerCase();
    const nameB = getFileName(b).toLowerCase();
    return nameA.localeCompare(nameB);
  });
});

/**
 * Check if asset is selected
 */
const isSelected = (asset: Asset): boolean => {
  return props.selectedFiles.some(f => f.path === asset.path);
};

/**
 * Handle file click (select)
 */
const handleFileClick = (asset: Asset, event: MouseEvent) => {
  if (event.ctrlKey || event.metaKey) {
    // Ctrl+Click: Toggle selection
    let newSelection: Asset[];
    if (isSelected(asset)) {
      newSelection = props.selectedFiles.filter(f => f.path !== asset.path);
    } else {
      newSelection = [...props.selectedFiles, asset];
    }
    emit('fileSelect', newSelection);
  } else {
    // Single click: Select this asset
    emit('fileSelect', [asset]);
  }
};

/**
 * Handle double-click (open info dialog)
 */
const handleFileDoubleClick = (asset: Asset) => {
  emit('assetClick', asset);
};

/**
 * Handle context menu (right-click)
 */
const handleContextMenu = (asset: Asset, event: MouseEvent) => {
  // Right-click: open rename dialog
  emit('assetRename', asset);
};

/**
 * Handle rename button click
 */
const handleRenameClick = (asset: Asset) => {
  emit('assetRename', asset);
};

/**
 * Handle drag start (drag asset or multiple selected assets)
 */
const handleDragStart = (asset: Asset, event: DragEvent) => {
  if (!event.dataTransfer) return;

  // If dragging a selected asset, drag all selected assets
  // Otherwise, drag just this asset
  const assetsToDrag = isSelected(asset) && props.selectedFiles.length > 1
    ? props.selectedFiles
    : [asset];

  event.dataTransfer.effectAllowed = 'copyMove';
  event.dataTransfer.setData('application/json', JSON.stringify({
    type: 'assets',  // Changed to 'assets' (plural) to indicate multiple
    panel: props.panel,
    worldId: props.worldId,
    assets: assetsToDrag,
  }));

  // Visual feedback: show copy or move cursor
  event.dataTransfer.dropEffect = 'move';
};

/**
 * Handle drag over (browser files or assets)
 */
const handleDragOver = (event: DragEvent) => {
  if (!event.dataTransfer) return;
  isDragOver.value = true;

  // Show copy cursor if Alt is pressed, otherwise move cursor
  const isAssetDrag = event.dataTransfer.types.includes('application/json');
  if (isAssetDrag) {
    event.dataTransfer.dropEffect = event.altKey ? 'copy' : 'move';
  } else {
    // Browser files always copy
    event.dataTransfer.dropEffect = 'copy';
  }
};

/**
 * Handle drag leave
 */
const handleDragLeave = () => {
  isDragOver.value = false;
};

/**
 * Handle drop (browser files OR asset)
 */
const handleDrop = (event: DragEvent) => {
  isDragOver.value = false;

  if (!event.dataTransfer) return;

  // Check if asset drop (internal)
  const jsonData = event.dataTransfer.getData('application/json');
  if (jsonData) {
    try {
      const data = JSON.parse(jsonData);
      if (data.type === 'assets' || data.type === 'asset') {
        // Asset(s) dropped from other panel or same panel
        // Alt key = copy, otherwise = move
        const shouldCopy = event.altKey;
        handleAssetsDrop(data, shouldCopy);
        return;
      }
    } catch (e) {
      console.error('Failed to parse drag data:', e);
    }
  }

  // Browser file drop
  const files = event.dataTransfer.files;
  if (files.length > 0) {
    emit('fileDrop', files);
  }
};

/**
 * Handle asset(s) drop (move/copy one or multiple assets)
 * @param shouldCopy true = copy, false = move (delete source after copy)
 */
const handleAssetsDrop = async (data: any, shouldCopy: boolean = false) => {
  const sourcePanel = data.panel;
  const sourceWorldId = data.worldId;

  // Handle both old format (single asset) and new format (multiple assets)
  const assetsToMove = data.assets || [data.asset];

  // Target info
  const targetWorldId = props.worldId;
  const targetPath = props.folderPath;

  const action = shouldCopy ? 'Copying' : 'Moving';
  console.log(`${action} ${assetsToMove.length} asset(s):`, {
    from: { panel: sourcePanel, worldId: sourceWorldId },
    to: { panel: props.panel, worldId: targetWorldId, folder: targetPath }
  });

  if (!targetWorldId) {
    console.error('No target world selected');
    return;
  }

  try {
    const { assetService } = await import('@/services/AssetService');

    // Process each asset
    for (const sourceAsset of assetsToMove) {
      // Extract filename from source path
      const fileName = getFileName(sourceAsset);

      // Build target path
      let newPath = targetPath ? `${targetPath}/${fileName}` : fileName;

      // If same world and same path: skip
      if (sourceWorldId === targetWorldId && sourceAsset.path === newPath) {
        console.log('Same location - skipping:', sourceAsset.path);
        continue;
      }

      // Step 1: Duplicate/Copy asset to new location
      if (sourceWorldId === targetWorldId) {
        // Same world: use duplicateAsset (efficient)
        await assetService.duplicateAsset(sourceWorldId, sourceAsset.path, newPath);
        console.log(`Asset duplicated: ${sourceAsset.path} -> ${newPath}`);
      } else {
        // Different world: use cross-world copy endpoint (preserves metadata)
        console.log(`Copying asset between worlds: ${sourceWorldId} -> ${targetWorldId}`);
        await assetService.copyAssetFromWorld(targetWorldId, sourceWorldId, sourceAsset.path, newPath);
        console.log(`Asset copied to new world: ${sourceAsset.path} -> ${newPath}`);
      }

      // Step 2: If move (not copy), delete source
      if (!shouldCopy) {
        await assetService.deleteAsset(sourceWorldId, sourceAsset.path);
        console.log(`Source asset deleted: ${sourceAsset.path}`);
      }
    }

    // Reload assets in current panel (target)
    if (assetsComposable.value) {
      await assetsComposable.value.loadAssets();
    }

    // Emit event to refresh source panel (if moved, not copied)
    emit('assetMoved', {
      sourcePanel: sourcePanel,
      sourceWorldId: sourceWorldId,
      moved: !shouldCopy,
    });

    console.log(`${assetsToMove.length} asset(s) ${shouldCopy ? 'copied' : 'moved'} successfully`);

  } catch (e) {
    console.error(`Failed to ${shouldCopy ? 'copy' : 'move'} assets:`, e);
    alert(`Failed to ${shouldCopy ? 'copy' : 'move'} assets: ` + (e instanceof Error ? e.message : 'Unknown error'));
  }
};

/**
 * Check if asset is audio file
 */
const isAudioFile = (asset: Asset): boolean => {
  const audioExtensions = ['.mp3', '.wav', '.ogg'];
  return audioExtensions.some(ext => asset.path.toLowerCase().endsWith(ext));
};

/**
 * Check if audio is currently playing
 */
const isPlaying = (asset: Asset): boolean => {
  return playingAssets.value.has(asset.path);
};

/**
 * Toggle audio playback
 */
const toggleAudioPlayback = async (asset: Asset) => {
  const path = asset.path;

  // If already playing, stop it
  if (playingAssets.value.has(path)) {
    const audio = audioElements.value.get(path);
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
    }
    playingAssets.value.delete(path);
    return;
  }

  // Stop all other playing audio
  playingAssets.value.forEach((_, p) => {
    const audio = audioElements.value.get(p);
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
    }
  });
  playingAssets.value.clear();

  // Create and play new audio
  try {
    const url = getAssetUrl.value(path);
    const audio = new Audio(url);
    audioElements.value.set(path, audio);

    audio.addEventListener('ended', () => {
      playingAssets.value.delete(path);
    });

    audio.addEventListener('error', () => {
      playingAssets.value.delete(path);
      console.error('Failed to play audio:', path);
    });

    await audio.play();
    playingAssets.value.add(path);
  } catch (e) {
    console.error('Failed to play audio:', e);
  }
};

/**
 * Extract filename from asset path
 */
const getFileName = (asset: Asset): string => {
  if (asset.name) return asset.name;

  // Extract from path (handle collection prefix)
  let path = asset.path;
  const colonPos = path.indexOf(':');
  if (colonPos > 0) {
    path = path.substring(colonPos + 1);
  }

  const lastSlash = path.lastIndexOf('/');
  return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
};

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
 * Pagination
 */
const previousPage = () => {
  assetsComposable.value?.previousPage();
};

const nextPage = () => {
  assetsComposable.value?.nextPage();
};

/**
 * Reload assets when folder changes
 */
watch(() => [props.worldId, props.folderPath], () => {
  if (props.worldId) {
    assetsComposable.value?.loadAssets();
  }
}, { immediate: true });
</script>

<style scoped>
.file-list {
  min-height: 0;
  transition: background-color 0.2s;
}

.file-list.drop-active {
  background-color: rgba(var(--p) / 0.1);
  border: 2px dashed rgba(var(--p) / 0.5);
}

.file-item {
  transition: all 0.15s;
}

.preview {
  background-color: rgba(0, 0, 0, 0.05);
  border-radius: 0.25rem;
}
</style>
