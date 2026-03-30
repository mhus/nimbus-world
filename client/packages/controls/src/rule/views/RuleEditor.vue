<template>
  <div class="space-y-4">
    <!-- Check if world is selected -->
    <div v-if="!currentWorldId" class="alert alert-info">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Please select a world to view and edit logic rules.</span>
    </div>

    <!-- Rule Editor Content -->
    <template v-else>
      <!-- Header with Search and Actions -->
      <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
        <div class="flex-1">
          <SearchInput
            v-model="searchQuery"
            placeholder="Search rules by name..."
            @search="handleSearch"
          />
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-primary"
            @click="openCreateDialog"
          >
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
            </svg>
            New Rule
          </button>
        </div>
      </div>

      <!-- Loading State -->
      <LoadingSpinner v-if="loading && rules.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && rules.length === 0" class="text-center py-12">
        <p class="text-base-content/70 text-lg">No logic rules found</p>
        <p class="text-base-content/50 text-sm mt-2">Create your first rule to get started</p>
      </div>

      <!-- Rule List -->
      <RuleList
        v-else
        :rules="rules"
        :loading="loading"
        @edit="openEditDialog"
        @delete="handleDelete"
      />

      <!-- Pagination Controls -->
      <div v-if="!loading && rules.length > 0" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, totalCount) }} of {{ totalCount }} rules
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-sm"
            :disabled="!hasPreviousPage"
            @click="handlePreviousPage"
          >
            Previous
          </button>
          <div class="flex items-center gap-2 px-4">
            <span class="text-sm">Page {{ currentPage }} of {{ totalPages }}</span>
          </div>
          <button
            class="btn btn-sm"
            :disabled="!hasNextPage"
            @click="handleNextPage"
          >
            Next
          </button>
        </div>
      </div>

      <!-- Editor Dialog -->
      <RuleEditorPanel
        v-if="isEditorOpen"
        :rule="selectedRule"
        :world-id="currentWorldId!"
        :current-epoch="effectiveEpoch"
        @close="closeEditor"
        @saved="handleSaved"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { LogicRuleDto } from '@/services/LogicRuleService';
import { useWorld } from '@/composables/useWorld';
import { useLogicRules } from '@/composables/useLogicRules';
import SearchInput from '@components/SearchInput.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import RuleList from '@rule/components/RuleList.vue';
import RuleEditorPanel from '@rule/components/RuleEditorPanel.vue';

const props = defineProps<{
  epoch?: number;
}>();

const { currentWorldId } = useWorld();

const rulesComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useLogicRules(currentWorldId.value);
});

const rules = computed(() => rulesComposable.value?.rules.value || []);
const loading = computed(() => rulesComposable.value?.loading.value || false);
const error = computed(() => rulesComposable.value?.error.value || null);
const searchQuery = ref('');

const totalCount = computed(() => rulesComposable.value?.totalCount.value || 0);
const currentPage = computed(() => rulesComposable.value?.currentPage.value || 1);
const pageSize = computed(() => rulesComposable.value?.pageSize.value || 50);
const totalPages = computed(() => rulesComposable.value?.totalPages.value || 0);
const hasNextPage = computed(() => rulesComposable.value?.hasNextPage.value || false);
const hasPreviousPage = computed(() => rulesComposable.value?.hasPreviousPage.value || false);

const isEditorOpen = ref(false);
const selectedRule = ref<LogicRuleDto | null>(null);

const effectiveEpoch = computed(() => props.epoch === -1 ? undefined : props.epoch);

watch(currentWorldId, () => {
  if (currentWorldId.value) {
    rulesComposable.value?.setEpochFilter(effectiveEpoch.value);
  }
}, { immediate: true });

watch(() => props.epoch, () => {
  rulesComposable.value?.setEpochFilter(effectiveEpoch.value);
});

const handleSearch = (query: string) => {
  if (!rulesComposable.value) return;
  rulesComposable.value.searchRules(query);
};

const openCreateDialog = () => {
  selectedRule.value = null;
  isEditorOpen.value = true;
};

const openEditDialog = (rule: LogicRuleDto) => {
  selectedRule.value = rule;
  isEditorOpen.value = true;
};

const closeEditor = () => {
  isEditorOpen.value = false;
  selectedRule.value = null;
};

const handleSaved = async () => {
  closeEditor();
  if (rulesComposable.value) {
    await rulesComposable.value.loadRules();
  }
};

const handleDelete = async (rule: LogicRuleDto) => {
  if (!rulesComposable.value) return;
  if (!confirm(`Are you sure you want to delete rule "${rule.name}"?`)) return;
  await rulesComposable.value.deleteRule(rule.id);
};

const handleNextPage = () => rulesComposable.value?.nextPage();
const handlePreviousPage = () => rulesComposable.value?.previousPage();
</script>
