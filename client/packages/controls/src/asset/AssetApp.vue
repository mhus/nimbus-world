<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header with World Selector -->
    <AssetAppHeader title="Nimbus Asset Editor" world-filter="withCollections" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <AssetEditor />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import AssetAppHeader from '@material/components/AssetAppHeader.vue';
import AssetEditor from '@material/views/AssetEditor.vue';
</script>
