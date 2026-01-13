<template>
  <div class="card bg-base-100 shadow hover:shadow-lg transition-shadow cursor-pointer" @click="emit('edit', layer)">
    <div class="card-body">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h3 class="card-title text-lg">
            {{ layer.name }}
          </h3>
          <p class="text-base-content/70 text-sm mt-1">
            ID: {{ layer.id }}
          </p>
        </div>
        <button
          class="btn btn-ghost btn-sm btn-square"
          @click.stop="emit('delete', layer)"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>

      <div class="mt-4 flex flex-wrap gap-2">
        <!-- Layer Type Badge -->
        <div :class="[
          'badge',
          layer.layerType === 'GROUND' ? 'badge-success' : 'badge-info'
        ]">
          {{ layer.layerType === 'GROUND' ? 'Ground' : 'Model' }}
        </div>

        <!-- Order Badge -->
        <div class="badge badge-outline">
          Order: {{ layer.order }}
        </div>

        <!-- Ground Badge -->
        <div v-if="layer.baseGround" class="badge badge-warning badge-outline">
          Ground Level
        </div>

        <!-- Enabled/Disabled Badge -->
        <div :class="[
          'badge',
          layer.enabled ? 'badge-success badge-outline' : 'badge-error badge-outline'
        ]">
          {{ layer.enabled ? 'Enabled' : 'Disabled' }}
        </div>

        <!-- All Chunks Badge -->
        <div v-if="!layer.allChunks" class="badge badge-secondary badge-outline">
          {{ layer.affectedChunks?.length || 0 }} chunks
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { WLayer } from '@nimbus/shared';

interface Props {
  layer: WLayer;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', layer: WLayer): void;
  (e: 'delete', layer: WLayer): void;
}>();
</script>
