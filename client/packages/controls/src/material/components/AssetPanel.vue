<template>
  <div class="asset-panel flex flex-col h-full border border-base-300 rounded-lg overflow-hidden">
    <!-- Asset Info Dialog -->
    <AssetInfoDialog
      v-if="isInfoDialogOpen && selectedAssetForEdit"
      :world-id="localWorldId"
      :asset-path="selectedAssetForEdit.path"
      @close="closeInfoDialog"
      @saved="handleInfoSaved"
    />

    <!-- Asset Rename Dialog -->
    <AssetRenameDialog
      v-if="isRenameDialogOpen && selectedAssetForRename"
      :world-id="localWorldId"
      :asset-path="selectedAssetForRename.path"
      @close="closeRenameDialog"
      @renamed="handleRenamed"
    />
    <!-- Panel Header -->
    <div class="panel-header bg-base-200 p-3 border-b border-base-300">
      <div class="flex gap-2">
        <!-- World Selector -->
        <select
          v-model="localWorldId"
          class="select select-sm select-bordered flex-1"
          @change="handleWorldChange"
          :disabled="worldsLoading"
        >
          <option value="">{{ worldsLoading ? 'Loading worlds...' : 'Select World...' }}</option>
          <option v-for="world in worlds" :key="world.worldId" :value="world.worldId">
            {{ world.name || world.worldId }}
          </option>
        </select>

        <!-- Search Field -->
        <div class="relative flex-1">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="Search assets..."
            class="input input-sm input-bordered w-full pr-8"
            :disabled="!localWorldId"
          />
          <button
            v-if="searchQuery"
            class="btn btn-ghost btn-xs absolute right-1 top-1/2 -translate-y-1/2"
            @click="searchQuery = ''"
            title="Clear search"
          >
            ✕
          </button>
        </div>
      </div>
    </div>

    <!-- Panel Content: Folder Tree + File List (with independent scrolling) -->
    <div class="flex-1 grid grid-cols-[30%_70%] gap-2 p-2 overflow-hidden min-h-0">
      <!-- Folder Tree (30%, scrollable) -->
      <div class="folder-tree-container border border-base-300 rounded overflow-y-auto overflow-x-hidden">
        <FolderTree
          v-if="localWorldId"
          ref="folderTreeRef"
          :world-id="localWorldId"
          :current-path="localCurrentPath"
          @folder-select="handleFolderSelect"
          @folder-create="handleFolderCreate"
        />
        <div v-else class="p-4 text-center text-base-content/50">
          Select a world
        </div>
      </div>

      <!-- File List (70%, scrollable) -->
      <div class="file-list-container border border-base-300 rounded overflow-y-auto overflow-x-hidden">
        <FileList
          v-if="localWorldId"
          ref="fileListRef"
          :key="localWorldId + '-' + localCurrentPath + '-' + reloadKey"
          :world-id="localWorldId"
          :folder-path="localCurrentPath"
          :selected-files="localSelectedFiles"
          :search-query="searchQuery"
          :panel="panel"
          @file-select="handleFileSelect"
          @file-drop="handleFileDrop"
          @asset-moved="handleAssetMoved"
          @asset-click="handleAssetClick"
          @asset-rename="handleAssetRename"
        />
        <div v-else class="p-4 text-center text-base-content/50">
          Select a world
        </div>
      </div>
    </div>

    <!-- Action Bar (Bottom of Panel) -->
    <ActionBar
      :world-id="localWorldId"
      :current-path="localCurrentPath"
      :selected-files="localSelectedFiles"
      :panel="panel"
      @upload="handleUpload"
      @download="handleDownload"
      @delete="handleDelete"
      @refresh="handleRefresh"
      @create-folder="handleCreateFolder"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { useWorld } from '@/composables/useWorld';
import FolderTree from '@material/components/FolderTree.vue';
import FileList from '@material/components/FileList.vue';
import ActionBar from '@material/components/ActionBar.vue';
import AssetInfoDialog from '@material/components/AssetInfoDialog.vue';
import AssetRenameDialog from '@material/components/AssetRenameDialog.vue';
import type { Asset } from '@/services/AssetService';

const props = withDefaults(defineProps<{
  worldId?: string;
  currentPath?: string;
  selectedFiles?: Asset[];
  panel: 'left' | 'right';
}>(), {
  worldId: '',
  currentPath: '',
  selectedFiles: () => [],
});

const emit = defineEmits<{
  'update:worldId': [value: string];
  'update:currentPath': [value: string];
  'update:selectedFiles': [value: Asset[]];
  'fileDropped': [event: { panel: 'left' | 'right'; files: FileList; targetPath: string }];
  'assetMoved': [data: { sourcePanel: string; sourceWorldId: string; moved: boolean }];
}>();

const { worlds, loading: worldsLoading } = useWorld();

// Refs
const fileListRef = ref<any>(null);
const folderTreeRef = ref<any>(null);
const reloadKey = ref(0);

// Local state (v-model sync)
const localWorldId = ref(props.worldId);
const localCurrentPath = ref(props.currentPath);
const localSelectedFiles = ref(props.selectedFiles);

// Search state
const searchQuery = ref('');

// Asset info dialog
const isInfoDialogOpen = ref(false);
const selectedAssetForEdit = ref<Asset | null>(null);

// Asset rename dialog
const isRenameDialogOpen = ref(false);
const selectedAssetForRename = ref<Asset | null>(null);

// Sync props to local state
watch(() => props.worldId, (val) => localWorldId.value = val);
watch(() => props.currentPath, (val) => localCurrentPath.value = val);
watch(() => props.selectedFiles, (val) => localSelectedFiles.value = val);

// Sync local state to parent
watch(localWorldId, (val) => emit('update:worldId', val));
watch(localCurrentPath, (val) => emit('update:currentPath', val));
watch(localSelectedFiles, (val) => emit('update:selectedFiles', val));

/**
 * Handle world change
 */
const handleWorldChange = () => {
  localCurrentPath.value = '';
  localSelectedFiles.value = [];
  searchQuery.value = ''; // Clear search when changing world
};

/**
 * Handle folder selection
 */
const handleFolderSelect = (folderPath: string) => {
  localCurrentPath.value = folderPath;
  localSelectedFiles.value = [];
};

/**
 * Handle file selection
 */
const handleFileSelect = (files: Asset[]) => {
  localSelectedFiles.value = files;
};

/**
 * Handle file drop from browser
 */
const handleFileDrop = (files: FileList) => {
  emit('fileDropped', {
    panel: props.panel,
    files,
    targetPath: localCurrentPath.value,
  });
};

/**
 * Handle upload
 */
const handleUpload = () => {
  const input = document.createElement('input');
  input.type = 'file';
  input.multiple = true;
  input.onchange = (e) => {
    const files = (e.target as HTMLInputElement).files;
    if (files) {
      handleFileDrop(files);
    }
  };
  input.click();
};

/**
 * Handle download
 */
const handleDownload = async () => {
  const { assetService } = await import('@/services/AssetService');

  localSelectedFiles.value.forEach(asset => {
    const url = assetService.getAssetUrl(localWorldId.value, asset.path);
    window.open(url, '_blank');
  });
};

/**
 * Handle delete
 */
const handleDelete = async () => {
  if (localSelectedFiles.value.length === 0) return;

  const fileNames = localSelectedFiles.value.map(a => {
    const name = a.path.split('/').pop();
    return name;
  }).join(', ');

  if (!confirm(`Delete ${localSelectedFiles.value.length} file(s)?\n\n${fileNames}`)) {
    return;
  }

  try {
    const { assetService } = await import('@/services/AssetService');

    // Delete all selected assets
    for (const asset of localSelectedFiles.value) {
      await assetService.deleteAsset(localWorldId.value, asset.path);
      console.log('Deleted:', asset.path);
    }

    // Clear selection
    localSelectedFiles.value = [];

    // Reload panel
    reload();

  } catch (e) {
    console.error('Failed to delete assets:', e);
    alert('Failed to delete assets: ' + (e instanceof Error ? e.message : 'Unknown error'));
  }
};

/**
 * Handle refresh
 */
const handleRefresh = () => {
  reload();
};

/**
 * Handle create folder
 */
const handleCreateFolder = () => {
  const name = prompt('Folder name:');
  if (name) {
    handleFolderCreate(name);
  }
};

/**
 * Handle folder create from tree
 */
const handleFolderCreate = (name: string) => {
  console.log('Create folder:', name, 'in parent:', localCurrentPath.value);

  // Add pseudo-folder to FolderTree
  if (folderTreeRef.value) {
    folderTreeRef.value.addPseudoFolder(name, localCurrentPath.value);
  } else {
    console.error('FolderTree ref not available');
  }
};

/**
 * Handle asset moved from FileList
 */
const handleAssetMoved = (data: { sourcePanel: string; sourceWorldId: string; moved: boolean }) => {
  // Forward to parent (McAssetEditor)
  emit('assetMoved', data);
};

/**
 * Handle asset click (open info dialog)
 */
const handleAssetClick = (asset: Asset) => {
  selectedAssetForEdit.value = asset;
  isInfoDialogOpen.value = true;
};

/**
 * Close info dialog
 */
const closeInfoDialog = () => {
  isInfoDialogOpen.value = false;
  selectedAssetForEdit.value = null;
};

/**
 * Handle info saved (reload after metadata update)
 */
const handleInfoSaved = () => {
  closeInfoDialog();
  reload();
};

/**
 * Handle asset rename
 */
const handleAssetRename = (asset: Asset) => {
  selectedAssetForRename.value = asset;
  isRenameDialogOpen.value = true;
};

/**
 * Close rename dialog
 */
const closeRenameDialog = () => {
  isRenameDialogOpen.value = false;
  selectedAssetForRename.value = null;
};

/**
 * Handle renamed (reload after rename)
 */
const handleRenamed = () => {
  closeRenameDialog();
  reload();
};

/**
 * Reload assets in this panel (called from parent)
 */
const reload = () => {
  console.log('Panel reload requested for', props.panel);
  // Force re-render of FileList by changing key
  reloadKey.value++;
};

// Expose reload method to parent
defineExpose({
  reload,
});
</script>

<style scoped>
.asset-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.folder-tree-container,
.file-list-container {
  min-height: 0;
  height: 100%;
}
</style>
