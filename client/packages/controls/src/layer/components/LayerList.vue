<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    <LayerListItem
      v-for="layer in layers"
      :key="layer.id"
      :layer="layer"
      @edit="emit('edit', $event)"
      @delete="emit('delete', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import type { LayerDto } from '@nimbus/shared';
import LayerListItem from './LayerListItem.vue';

interface Props {
  layers: LayerDto[];
  loading?: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', layer: LayerDto): void;
  (e: 'delete', layer: LayerDto): void;
}>();
</script>
