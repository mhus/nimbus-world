<template>
  <div class="h-screen flex flex-col bg-base-100 overflow-hidden">
    <!-- Header without global World Selector (each panel has its own) -->
    <AssetAppHeader title="Nimbus MC Asset Editor" :show-world-selector="false" />

    <!-- Main Content (Full Height, No Scroll) -->
    <main class="flex-1 overflow-hidden">
      <McAssetEditor />
    </main>
  </div>
</template>

<script setup lang="ts">
import AssetAppHeader from '@material/components/AssetAppHeader.vue';
import McAssetEditor from '@material/views/McAssetEditor.vue';
</script>

<style scoped>
/* App fills viewport exactly - no scrolling on body */
</style>
