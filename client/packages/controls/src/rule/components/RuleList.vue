<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    <RuleListItem
      v-for="rule in rules"
      :key="rule.id"
      :rule="rule"
      @edit="emit('edit', $event)"
      @delete="emit('delete', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import type { LogicRuleDto } from '@/services/LogicRuleService';
import RuleListItem from './RuleListItem.vue';

interface Props {
  rules: LogicRuleDto[];
  loading?: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', rule: LogicRuleDto): void;
  (e: 'delete', rule: LogicRuleDto): void;
}>();
</script>
