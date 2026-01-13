<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus BlockType Editor" world-filter="withCollections" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <BlockTypeEditor />
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@material/components/AppHeader.vue';
import BlockTypeEditor from '@material/views/BlockTypeEditor.vue';
</script>
