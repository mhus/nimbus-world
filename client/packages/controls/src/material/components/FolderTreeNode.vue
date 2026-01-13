<template>
  <div class="folder-node">
    <!-- Folder Item -->
    <div
      class="folder-item cursor-pointer hover:bg-base-200 rounded px-2 py-1 flex items-center gap-1"
      :class="[
        { 'bg-base-300': currentPath === folder.path },
        { 'italic text-base-content/50': folder.isPseudo }
      ]"
      :style="{ paddingLeft: `${depth * 12 + 8}px` }"
      @click="handleClick"
    >
      <!-- Expand/Collapse Icon -->
      <span
        v-if="children.length > 0"
        class="expand-icon text-xs"
        @click.stop="toggleExpanded"
      >
        {{ expanded ? '▼' : '▶' }}
      </span>
      <span v-else class="expand-icon text-xs opacity-0">▶</span>

      <!-- Folder Icon & Name -->
      <span class="text-sm flex-1">
        {{ folder.isPseudo ? '📂' : '📁' }} {{ folder.name }}
      </span>

      <!-- Asset Count (only for real folders) -->
      <span v-if="!folder.isPseudo" class="text-xs text-base-content/50">
        {{ folder.assetCount }}
      </span>
    </div>

    <!-- Children (if expanded) -->
    <div v-if="expanded && children.length > 0">
      <FolderTreeNode
        v-for="child in children"
        :key="child.path"
        :folder="child"
        :current-path="currentPath"
        :all-folders="allFolders"
        :depth="depth + 1"
        @select="$emit('select', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { FolderInfo } from '@shared/generated/entities/FolderInfo';

const props = withDefaults(defineProps<{
  folder: FolderInfo;
  currentPath: string;
  allFolders: FolderInfo[];
  depth?: number;
}>(), {
  depth: 0,
});

const emit = defineEmits<{
  'select': [path: string];
}>();

const expanded = ref(false);

// Child folders (direct children only), sorted alphabetically
const children = computed(() => {
  const childFolders = props.allFolders.filter(f => f.parentPath === props.folder.path);
  return childFolders.sort((a, b) => {
    const nameA = (a.name || '').toLowerCase();
    const nameB = (b.name || '').toLowerCase();
    return nameA.localeCompare(nameB);
  });
});

/**
 * Toggle expanded state
 */
const toggleExpanded = () => {
  expanded.value = !expanded.value;
};

/**
 * Handle folder click
 */
const handleClick = () => {
  emit('select', props.folder.path);
};
</script>

<style scoped>
.folder-item {
  transition: background-color 0.15s;
}

.expand-icon {
  cursor: pointer;
  user-select: none;
  width: 12px;
  text-align: center;
}
</style>
