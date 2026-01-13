/**
 * useFolders Composable
 * Manages folder tree state and operations for MC-style asset editor
 */

import { ref, Ref } from 'vue';
import { assetService } from '@/services/AssetService';
import type { FolderInfo } from '@nimbus/shared/generated/entities/FolderInfo';

export interface PseudoFolder {
  path: string;
  name: string;
}

export function useFolders(worldId: string) {
  const folders: Ref<FolderInfo[]> = ref([]);
  const loading = ref(false);
  const error: Ref<string | null> = ref(null);

  // Pseudo-folders (not yet persisted, only in UI state)
  const pseudoFolders: Ref<PseudoFolder[]> = ref([]);

  /**
   * Load folders from backend
   */
  const loadFolders = async (parentPath?: string) => {
    if (!worldId) {
      error.value = 'No world ID provided';
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await assetService.getFolders(worldId, parentPath);
      folders.value = response.folders;
    } catch (e) {
      console.error('Failed to load folders:', e);
      error.value = e instanceof Error ? e.message : 'Failed to load folders';
    } finally {
      loading.value = false;
    }
  };

  /**
   * Add a pseudo-folder (UI-only, not persisted until asset is added)
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
  };

  /**
   * Remove a pseudo-folder
   */
  const removePseudoFolder = (path: string) => {
    pseudoFolders.value = pseudoFolders.value.filter(f => f.path !== path);
  };

  /**
   * Convert pseudo-folder to real folder (when first asset is added)
   */
  const promotePseudoFolder = (path: string) => {
    const pseudo = pseudoFolders.value.find(f => f.path === path);
    if (!pseudo) return;

    // Remove from pseudo list
    removePseudoFolder(path);

    // Reload folders to get the new folder from backend
    loadFolders();
  };

  /**
   * Move/rename a folder (bulk path update)
   * Note: Backend endpoint may not be implemented (Phase 2)
   */
  const moveFolder = async (oldPath: string, newPath: string) => {
    if (!worldId) {
      throw new Error('No world ID provided');
    }

    loading.value = true;
    error.value = null;

    try {
      await assetService.moveFolder(worldId, oldPath, newPath);
      await loadFolders(); // Reload after move
    } catch (e) {
      console.error('Failed to move folder:', e);
      error.value = e instanceof Error ? e.message : 'Failed to move folder';
      throw e;
    } finally {
      loading.value = false;
    }
  };

  return {
    folders,
    loading,
    error,
    pseudoFolders,
    loadFolders,
    addPseudoFolder,
    removePseudoFolder,
    promotePseudoFolder,
    moveFolder,
  };
}
