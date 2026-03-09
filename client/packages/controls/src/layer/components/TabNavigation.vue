<template>
  <div class="card bg-base-100 shadow-xl">
    <div class="card-body">
      <LayerEditor :epoch="epoch" />
    </div>
  </div>
</template>

<script setup lang="ts">
import LayerEditor from '../views/LayerEditor.vue';

defineProps<{
  epoch?: number;
}>();
</script>
