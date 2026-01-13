<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus Chunk Editor" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <ChunkEditor />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@material/components/AppHeader.vue';
import ChunkEditor from './views/ChunkEditor.vue';
</script>
