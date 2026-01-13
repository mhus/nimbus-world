<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus Layer Editor" world-filter="withCollectionsAndZones" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <TabNavigation />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@material/components/AppHeader.vue';
import TabNavigation from '@layer/components/TabNavigation.vue';
</script>
