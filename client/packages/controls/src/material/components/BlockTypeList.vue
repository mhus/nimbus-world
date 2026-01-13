<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    <BlockTypeListItem
      v-for="blockType in blockTypes"
      :key="blockType.id"
      :block-type="blockType"
      @edit="emit('edit', $event)"
      @delete="emit('delete', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import type { BlockType } from '@nimbus/shared';
import BlockTypeListItem from './BlockTypeListItem.vue';

interface Props {
  blockTypes: BlockType[];
  loading?: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  (e: 'edit', blockType: BlockType): void;
  (e: 'delete', blockType: BlockType): void;
}>();
</script>
