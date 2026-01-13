<template>
  <div class="min-h-screen flex flex-col bg-base-200">
    <!-- Header -->
    <header class="navbar bg-base-100 shadow-lg sticky top-0 z-10">
      <div class="flex-1">
        <a href="/controls/index.html" class="btn btn-ghost normal-case text-xl gap-2">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          Storage Editor
        </a>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <StorageList />
    </main>
  </div>
</template>

<script setup lang="ts">
import StorageList from './views/StorageList.vue';
</script>
