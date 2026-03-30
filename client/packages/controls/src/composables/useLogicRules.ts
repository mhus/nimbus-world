/**
 * useLogicRules Composable
 * Manages logic rule list and operations
 */

import { ref, computed } from 'vue';
import type { LogicRuleDto, CreateLogicRuleRequest, UpdateLogicRuleRequest } from '../services/LogicRuleService';
import { logicRuleService } from '../services/LogicRuleService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('useLogicRules');

export function useLogicRules(worldId: string) {
  const rules = ref<LogicRuleDto[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const searchQuery = ref('');
  const epochFilter = ref<number | undefined>(undefined);

  // Paging state
  const totalCount = ref(0);
  const currentPage = ref(1);
  const pageSize = ref(50);

  // Computed
  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value));
  const hasNextPage = computed(() => currentPage.value < totalPages.value);
  const hasPreviousPage = computed(() => currentPage.value > 1);

  const loadRules = async (page: number = 1) => {
    loading.value = true;
    error.value = null;
    currentPage.value = page;

    try {
      const offset = (page - 1) * pageSize.value;
      const response = await logicRuleService.getRules(worldId, {
        query: searchQuery.value || undefined,
        epoch: epochFilter.value,
        limit: pageSize.value,
        offset,
      });

      rules.value = response.rules;
      totalCount.value = response.count;

      logger.info('Loaded logic rules', {
        count: rules.value.length,
        totalCount: totalCount.value,
        page,
        worldId,
      });
    } catch (err) {
      error.value = 'Failed to load rules';
      logger.error('Failed to load logic rules', { worldId, page }, err as Error);
    } finally {
      loading.value = false;
    }
  };

  const searchRules = async (query: string) => {
    searchQuery.value = query;
    currentPage.value = 1;
    await loadRules(1);
  };

  const nextPage = async () => {
    if (hasNextPage.value) {
      await loadRules(currentPage.value + 1);
    }
  };

  const previousPage = async () => {
    if (hasPreviousPage.value) {
      await loadRules(currentPage.value - 1);
    }
  };

  const goToPage = async (page: number) => {
    if (page >= 1 && page <= totalPages.value) {
      await loadRules(page);
    }
  };

  const createRule = async (rule: CreateLogicRuleRequest): Promise<string | null> => {
    try {
      const id = await logicRuleService.createRule(worldId, rule);
      logger.info('Created logic rule', { worldId, id });
      await loadRules();
      return id;
    } catch (err) {
      error.value = 'Failed to create rule';
      logger.error('Failed to create logic rule', { worldId }, err as Error);
      return null;
    }
  };

  const updateRule = async (id: string, rule: UpdateLogicRuleRequest): Promise<boolean> => {
    try {
      await logicRuleService.updateRule(worldId, id, rule);
      logger.info('Updated logic rule', { worldId, id });
      await loadRules();
      return true;
    } catch (err) {
      error.value = 'Failed to update rule';
      logger.error('Failed to update logic rule', { worldId, id }, err as Error);
      return false;
    }
  };

  const deleteRule = async (id: string): Promise<boolean> => {
    try {
      await logicRuleService.deleteRule(worldId, id);
      logger.info('Deleted logic rule', { worldId, id });
      await loadRules();
      return true;
    } catch (err) {
      error.value = 'Failed to delete rule';
      logger.error('Failed to delete logic rule', { worldId, id }, err as Error);
      return false;
    }
  };

  const setEpochFilter = async (epoch: number | undefined) => {
    epochFilter.value = epoch;
    currentPage.value = 1;
    await loadRules(1);
  };

  return {
    rules,
    loading,
    error,
    searchQuery,
    epochFilter,
    totalCount,
    currentPage,
    pageSize,
    totalPages,
    hasNextPage,
    hasPreviousPage,
    loadRules,
    searchRules,
    setEpochFilter,
    nextPage,
    previousPage,
    goToPage,
    createRule,
    updateRule,
    deleteRule,
  };
}
