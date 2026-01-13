<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <AppHeader title="Nimbus Material Editor" />

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <TabNavigation />
    </main>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@material/components/AppHeader.vue';
import TabNavigation from '@material/components/TabNavigation.vue';
</script>
