<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus Flat Editor" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <FlatEditor />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@material/components/AppHeader.vue';
import FlatEditor from './views/FlatEditor.vue';
</script>
