/**
 * useHexGrids Composable
 * Manages hex grid CRUD operations
 */

import { ref, type Ref } from 'vue';
import { apiService } from '../services/ApiService';
import { getLogger, type HexGrid } from '@nimbus/shared';

const logger = getLogger('useHexGrids');

export interface HexGridWithId {
  id: string;
  worldId: string;
  position: string;
  publicData: HexGrid;
  generatorParameters?: Record<string, string>;
  createdAt?: string;
  updatedAt?: string;
  enabled: boolean;
}

export interface UseHexGridsReturn {
  hexGrids: Ref<HexGridWithId[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  loadHexGrids: () => Promise<void>;
  loadHexGrid: (q: number, r: number) => Promise<HexGridWithId | null>;
  createHexGrid: (hexGrid: Partial<HexGridWithId>) => Promise<void>;
  updateHexGrid: (q: number, r: number, hexGrid: Partial<HexGridWithId>) => Promise<void>;
  deleteHexGrid: (q: number, r: number) => Promise<void>;
  enableHexGrid: (q: number, r: number) => Promise<void>;
  disableHexGrid: (q: number, r: number) => Promise<void>;
}

export function useHexGrids(worldId: string): UseHexGridsReturn {
  const hexGrids = ref<HexGridWithId[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  /**
   * Load all hex grids for world
   */
  const loadHexGrids = async () => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<HexGridWithId[]>(
        `/control/worlds/${worldId}/hexgrid`
      );
      hexGrids.value = response;
      logger.info('Loaded hex grids', { worldId, count: hexGrids.value.length });
    } catch (err) {
      error.value = 'Failed to load hex grids';
      logger.error('Failed to load hex grids', { worldId }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  /**
   * Load single hex grid
   */
  const loadHexGrid = async (q: number, r: number): Promise<HexGridWithId | null> => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      return null;
    }

    loading.value = true;
    error.value = null;

    try {
      const response = await apiService.get<HexGridWithId>(
        `/control/worlds/${worldId}/hexgrid/${q}/${r}`
      );
      logger.info('Loaded hex grid', { worldId, q, r });
      return response;
    } catch (err) {
      error.value = `Failed to load hex grid at ${q}:${r}`;
      logger.error('Failed to load hex grid', { worldId, q, r }, err as Error);
      return null;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Create hex grid
   */
  const createHexGrid = async (hexGrid: Partial<HexGridWithId>) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.post<HexGridWithId>(
        `/control/worlds/${worldId}/hexgrid`,
        hexGrid
      );
      logger.info('Created hex grid', { worldId, hexGrid });
      await loadHexGrids();
    } catch (err) {
      error.value = 'Failed to create hex grid';
      logger.error('Failed to create hex grid', { worldId, hexGrid }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Update hex grid
   */
  const updateHexGrid = async (q: number, r: number, hexGrid: Partial<HexGridWithId>) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.put<HexGridWithId>(
        `/control/worlds/${worldId}/hexgrid/${q}/${r}`,
        hexGrid
      );
      logger.info('Updated hex grid', { worldId, q, r, hexGrid });
      await loadHexGrids();
    } catch (err) {
      error.value = `Failed to update hex grid at ${q}:${r}`;
      logger.error('Failed to update hex grid', { worldId, q, r, hexGrid }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Delete hex grid
   */
  const deleteHexGrid = async (q: number, r: number) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.delete(
        `/control/worlds/${worldId}/hexgrid/${q}/${r}`
      );
      logger.info('Deleted hex grid', { worldId, q, r });
      await loadHexGrids();
    } catch (err) {
      error.value = `Failed to delete hex grid at ${q}:${r}`;
      logger.error('Failed to delete hex grid', { worldId, q, r }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Enable hex grid
   */
  const enableHexGrid = async (q: number, r: number) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.post<void>(
        `/control/worlds/${worldId}/hexgrid/${q}/${r}/enable`
      );
      logger.info('Enabled hex grid', { worldId, q, r });
      await loadHexGrids();
    } catch (err) {
      error.value = `Failed to enable hex grid at ${q}:${r}`;
      logger.error('Failed to enable hex grid', { worldId, q, r }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  /**
   * Disable hex grid
   */
  const disableHexGrid = async (q: number, r: number) => {
    // Skip if worldId is not set
    if (!worldId || worldId === '?') {
      throw new Error('World ID not set');
    }

    loading.value = true;
    error.value = null;

    try {
      await apiService.post<void>(
        `/control/worlds/${worldId}/hexgrid/${q}/${r}/disable`
      );
      logger.info('Disabled hex grid', { worldId, q, r });
      await loadHexGrids();
    } catch (err) {
      error.value = `Failed to disable hex grid at ${q}:${r}`;
      logger.error('Failed to disable hex grid', { worldId, q, r }, err as Error);
      throw err;
    } finally {
      loading.value = false;
    }
  };

  return {
    hexGrids,
    loading,
    error,
    loadHexGrids,
    loadHexGrid,
    createHexGrid,
    updateHexGrid,
    deleteHexGrid,
    enableHexGrid,
    disableHexGrid,
  };
}
