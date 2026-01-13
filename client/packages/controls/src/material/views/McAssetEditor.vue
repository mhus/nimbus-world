<template>
  <div class="mc-asset-editor flex flex-col h-full overflow-hidden">
    <!-- Dual Panels (flexible height, no scroll here) -->
    <div class="flex-1 grid grid-cols-2 gap-4 p-4 overflow-hidden">
      <!-- Left Panel -->
      <AssetPanel
        ref="leftPanelRef"
        v-model:world-id="leftWorldId"
        v-model:current-path="leftCurrentPath"
        v-model:selected-files="leftSelectedFiles"
        panel="left"
        @file-dropped="handleFileDrop"
        @asset-moved="handleAssetMoved"
      />

      <!-- Right Panel -->
      <AssetPanel
        ref="rightPanelRef"
        v-model:world-id="rightWorldId"
        v-model:current-path="rightCurrentPath"
        v-model:selected-files="rightSelectedFiles"
        panel="right"
        @file-dropped="handleFileDrop"
        @asset-moved="handleAssetMoved"
      />
    </div>

    <!-- Info Bar (Bottom, fixed height) -->
    <InfoBar
      :selected-asset="selectedAsset"
      :folder-stats="folderStats"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { getLogger } from '@nimbus/shared';
import AssetPanel from '@material/components/AssetPanel.vue';
import InfoBar from '@material/components/InfoBar.vue';
import type { Asset } from '@/services/AssetService';

const logger = getLogger('McAssetEditor');

const { currentWorldId, worlds, loadWorlds } = useWorld();

// LocalStorage keys for panel world selections
const LEFT_PANEL_WORLD_KEY = 'nimbus.mcAssetEditor.leftWorldId';
const RIGHT_PANEL_WORLD_KEY = 'nimbus.mcAssetEditor.rightWorldId';

// Panel refs for triggering refresh
const leftPanelRef = ref<any>(null);
const rightPanelRef = ref<any>(null);

// Panel states
const leftWorldId = ref<string>('');
const rightWorldId = ref<string>('');
const leftCurrentPath = ref<string>('');
const rightCurrentPath = ref<string>('');
const leftSelectedFiles = ref<Asset[]>([]);
const rightSelectedFiles = ref<Asset[]>([]);

// Trigger keys to force reload
const leftTrigger = ref(0);
const rightTrigger = ref(0);

// Read worldId from Local Storage
const getWorldIdFromStorage = (key: string): string | null => {
  try {
    return localStorage.getItem(key);
  } catch (err) {
    logger.warn(`Failed to read from localStorage: ${key}`, { error: err });
    return null;
  }
};

// Save worldId to Local Storage
const saveWorldIdToStorage = (key: string, worldId: string): void => {
  try {
    localStorage.setItem(key, worldId);
    logger.info(`Saved worldId to localStorage: ${key}`, { worldId });
  } catch (err) {
    logger.warn(`Failed to write to localStorage: ${key}`, { error: err });
  }
};

// Load main worlds and collections, initialize both panels
onMounted(async () => {
  await loadWorlds('withCollections');

  if (worlds.value.length > 0) {
    // Try to restore from localStorage
    const storedLeftWorldId = getWorldIdFromStorage(LEFT_PANEL_WORLD_KEY);
    const storedRightWorldId = getWorldIdFromStorage(RIGHT_PANEL_WORLD_KEY);

    // Validate stored values exist in loaded worlds
    const leftWorldExists = storedLeftWorldId && worlds.value.some(w => w.worldId === storedLeftWorldId);
    const rightWorldExists = storedRightWorldId && worlds.value.some(w => w.worldId === storedRightWorldId);

    // Set left panel world
    if (leftWorldExists) {
      leftWorldId.value = storedLeftWorldId!;
      logger.info('Restored left panel worldId from localStorage', { worldId: storedLeftWorldId });
    } else {
      leftWorldId.value = worlds.value[0].worldId;
      saveWorldIdToStorage(LEFT_PANEL_WORLD_KEY, leftWorldId.value);
      logger.info('Initialized left panel with first world', { worldId: leftWorldId.value });
    }

    // Set right panel world
    if (rightWorldExists) {
      rightWorldId.value = storedRightWorldId!;
      logger.info('Restored right panel worldId from localStorage', { worldId: storedRightWorldId });
    } else {
      rightWorldId.value = worlds.value[0].worldId;
      saveWorldIdToStorage(RIGHT_PANEL_WORLD_KEY, rightWorldId.value);
      logger.info('Initialized right panel with first world', { worldId: rightWorldId.value });
    }
  }
});

// Watch for changes and persist to localStorage
watch(leftWorldId, (newWorldId) => {
  if (newWorldId) {
    saveWorldIdToStorage(LEFT_PANEL_WORLD_KEY, newWorldId);
  }
});

watch(rightWorldId, (newWorldId) => {
  if (newWorldId) {
    saveWorldIdToStorage(RIGHT_PANEL_WORLD_KEY, newWorldId);
  }
});

// Selected asset for info bar (last selected from either panel)
const selectedAsset = computed<Asset | null>(() => {
  if (leftSelectedFiles.value.length > 0) {
    return leftSelectedFiles.value[leftSelectedFiles.value.length - 1];
  }
  if (rightSelectedFiles.value.length > 0) {
    return rightSelectedFiles.value[rightSelectedFiles.value.length - 1];
  }
  return null;
});

// Folder statistics (placeholder)
const folderStats = computed(() => {
  // TODO: Implement folder statistics calculation
  return {
    assetCount: 0,
    totalSize: 0,
  };
});

/**
 * Handle file dropped from browser
 */
const handleFileDrop = async (event: { panel: 'left' | 'right'; files: FileList; targetPath: string }) => {
  console.log('File dropped:', event);

  const worldId = event.panel === 'left' ? leftWorldId.value : rightWorldId.value;

  if (!worldId) {
    alert('No world selected in ' + event.panel + ' panel');
    return;
  }

  try {
    const { assetService } = await import('@/services/AssetService');

    // Upload all dropped files
    for (let i = 0; i < event.files.length; i++) {
      const file = event.files[i];
      const fileName = file.name;

      // Build target path
      const targetPath = event.targetPath
        ? `${event.targetPath}/${fileName}`
        : fileName;

      console.log(`Uploading: ${fileName} -> ${targetPath}`);

      await assetService.uploadAsset(worldId, targetPath, file);
      console.log(`Uploaded: ${targetPath}`);
    }

    // Reload the target panel
    if (event.panel === 'left' && leftPanelRef.value) {
      leftPanelRef.value.reload();
    } else if (event.panel === 'right' && rightPanelRef.value) {
      rightPanelRef.value.reload();
    }

    console.log(`Successfully uploaded ${event.files.length} file(s)`);

  } catch (e) {
    console.error('Failed to upload files:', e);
    alert('Failed to upload files: ' + (e instanceof Error ? e.message : 'Unknown error'));
  }
};

/**
 * Handle asset moved between panels
 */
const handleAssetMoved = (data: { sourcePanel: string; sourceWorldId: string; moved: boolean }) => {
  console.log('Asset moved event:', data);

  // If asset was moved (not copied), refresh the source panel
  if (data.moved) {
    if (data.sourcePanel === 'left' && leftPanelRef.value) {
      leftPanelRef.value.reload();
    } else if (data.sourcePanel === 'right' && rightPanelRef.value) {
      rightPanelRef.value.reload();
    }
  }
};
</script>

<style scoped>
.mc-asset-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
