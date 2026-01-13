<template>
  <div class="folder-tree p-2 h-full">
    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center p-4">
      <span class="loading loading-spinner loading-sm"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="text-error text-sm p-2">
      {{ error }}
    </div>

    <!-- Tree -->
    <div v-else class="space-y-1">
      <!-- Root -->
      <div
        class="folder-item cursor-pointer hover:bg-base-200 rounded px-2 py-1"
        :class="{ 'bg-base-300': currentPath === '' }"
        @click="selectFolder('')"
      >
        <span class="text-sm">📁 Root</span>
      </div>

      <!-- Folders (including pseudo) -->
      <FolderTreeNode
        v-for="folder in rootFolders"
        :key="folder.path"
        :folder="folder"
        :current-path="currentPath"
        :all-folders="allFolders"
        @select="selectFolder"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useFolders } from '@/composables/useFolders';
import FolderTreeNode from '@material/components/FolderTreeNode.vue';

const props = withDefaults(defineProps<{
  worldId?: string;
  currentPath?: string;
}>(), {
  worldId: '',
  currentPath: '',
});

const emit = defineEmits<{
  'folderSelect': [path: string];
  'folderCreate': [name: string];
}>();

const folders = ref<any[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const pseudoFolders = ref<any[]>([]);

// Combined folders (real + pseudo)
const allFolders = computed(() => {
  // Convert pseudo folders to FolderInfo format
  const pseudoAsFolderInfo = pseudoFolders.value.map(p => {
    const lastSlash = p.path.lastIndexOf('/');
    const parentPath = lastSlash >= 0 ? p.path.substring(0, lastSlash) : '';

    console.log('Pseudo folder:', p.path, 'parent:', parentPath);

    return {
      path: p.path,
      name: p.name,
      assetCount: 0,
      totalAssetCount: 0,
      subfolderCount: 0,
      parentPath,
      isPseudo: true,
    };
  });

  return [...folders.value, ...pseudoAsFolderInfo];
});

// Root folders (no parent), sorted alphabetically
const rootFolders = computed(() => {
  const roots = allFolders.value.filter(f => !f.parentPath || f.parentPath === '');
  return roots.sort((a, b) => {
    const nameA = (a.name || '').toLowerCase();
    const nameB = (b.name || '').toLowerCase();
    return nameA.localeCompare(nameB);
  });
});

/**
 * Load folders for current world
 */
const loadFolders = async () => {
  if (!props.worldId) {
    folders.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    const { assetService } = await import('@/services/AssetService');
    const response = await assetService.getFolders(props.worldId);
    folders.value = response.folders;
  } catch (e) {
    console.error('Failed to load folders:', e);
    error.value = e instanceof Error ? e.message : 'Failed to load folders';
  } finally {
    loading.value = false;
  }
};

/**
 * Select folder
 */
const selectFolder = (path: string) => {
  emit('folderSelect', path);
};

/**
 * Reload folders when world changes
 */
watch(() => props.worldId, () => {
  if (props.worldId) {
    loadFolders();
  } else {
    folders.value = [];
  }
}, { immediate: true });

/**
 * Add pseudo-folder (UI-only until asset is added)
 */
const addPseudoFolder = (name: string, parentPath?: string) => {
  const path = parentPath ? `${parentPath}/${name}` : name;

  // Check if already exists
  const exists = folders.value.some(f => f.path === path) ||
                 pseudoFolders.value.some(f => f.path === path);

  if (exists) {
    console.warn('Folder already exists:', path);
    return;
  }

  pseudoFolders.value.push({ path, name });
  console.log('Pseudo-folder created:', path);
};

// Expose methods to parent
defineExpose({
  addPseudoFolder,
  loadFolders,
});
</script>

<style scoped>
.folder-tree {
  font-size: 0.875rem;
}

.folder-item {
  transition: background-color 0.15s;
}
</style>
