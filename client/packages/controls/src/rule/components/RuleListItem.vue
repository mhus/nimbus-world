<template>
  <div class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer" @click="emit('edit', rule)">
    <div class="card-body">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h3 class="card-title text-lg">
            {{ rule.name }}
          </h3>
          <p v-if="rule.description" class="text-base-content/70 text-sm mt-1">
            {{ rule.description }}
          </p>
          <p class="text-base-content/50 text-xs mt-1 font-mono">
            {{ rule.spelCondition || '(no condition)' }}
          </p>
        </div>
        <button
          class="btn btn-ghost btn-sm btn-square"
          @click.stop="emit('delete', rule)"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <!-- Package Badge -->
        <div v-if="rule.rulePackage" class="badge badge-neutral badge-outline">
          {{ rule.rulePackage }}
        </div>

        <!-- Enabled/Disabled Badge -->
        <div :class="[
          'badge',
          rule.enabled ? 'badge-success badge-outline' : 'badge-error badge-outline'
        ]">
          {{ rule.enabled ? 'Enabled' : 'Disabled' }}
        </div>

        <!-- Priority Badge -->
        <div class="badge badge-outline">
          Priority: {{ rule.priority }}
        </div>

        <!-- Affected Flags Badge -->
        <div v-if="rule.affected && rule.affected.length > 0" class="badge badge-secondary badge-outline">
          Affected: {{ rule.affected.join(', ') }}
        </div>

        <!-- Effects Count Badge -->
        <div v-if="rule.effects && rule.effects.length > 0" class="badge badge-info badge-outline">
          {{ rule.effects.length }} effect{{ rule.effects.length > 1 ? 's' : '' }}
        </div>

        <!-- Epochs Badge -->
        <div v-if="rule.epoches && rule.epoches.length > 0" class="badge badge-primary badge-outline">
          Epochs: {{ rule.epoches.join(', ') }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { LogicRuleDto } from '@/services/LogicRuleService';

interface Props {
  rule: LogicRuleDto;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', rule: LogicRuleDto): void;
  (e: 'delete', rule: LogicRuleDto): void;
}>();
</script>
