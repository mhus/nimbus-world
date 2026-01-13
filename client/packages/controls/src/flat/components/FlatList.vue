<template>
  <div class="space-y-3">
    <FlatListItem
      v-for="flat in flats"
      :key="flat.id"
      :flat="flat"
      @view="$emit('view', flat.id)"
      @delete="$emit('delete', flat.id)"
    />
  </div>
</template>

<script setup lang="ts">
import FlatListItem from './FlatListItem.vue';

interface Flat {
  id: string;
  worldId: string;
  layerDataId: string;
  flatId: string;
  sizeX: number;
  sizeZ: number;
  mountX: number;
  mountZ: number;
  oceanLevel: number;
  createdAt: string;
  updatedAt: string;
}

defineProps<{
  flats: Flat[];
  loading?: boolean;
}>();

defineEmits<{
  view: [flatId: string];
  delete: [flatId: string];
}>();
</script>
