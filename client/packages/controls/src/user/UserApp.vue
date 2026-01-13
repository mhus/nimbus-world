<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <header class="navbar bg-base-200 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <a class="btn btn-ghost normal-case text-xl">Nimbus User Editor</a>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <UserList v-if="!selectedUsername" @select="handleUserSelect" />
      <UserEditor v-else :username="selectedUsername" @back="handleBack" @saved="handleSaved" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import UserList from './views/UserList.vue';
import UserEditor from './views/UserEditor.vue';

// Read id from URL query parameter
const getIdFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('id');
};

const selectedUsername = ref<string | null>(getIdFromUrl());

const handleUserSelect = (username: string) => {
  selectedUsername.value = username;
};

const handleBack = () => {
  selectedUsername.value = null;
};

const handleSaved = () => {
  selectedUsername.value = null;
};
</script>
