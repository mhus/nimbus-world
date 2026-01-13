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
        <a class="btn btn-ghost normal-case text-xl">Nimbus Settings Editor</a>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6">
      <SettingsList v-if="!selectedKey" @select="handleSelect" />
      <SettingsEditor v-else :settingKey="selectedKey" @back="handleBack" @saved="handleSaved" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import SettingsList from './views/SettingsList.vue';
import SettingsEditor from './views/SettingsEditor.vue';

// Read key from URL query parameter
const getKeyFromUrl = (): string | null => {
  const params = new URLSearchParams(window.location.search);
  return params.get('key');
};

const selectedKey = ref<string | null>(getKeyFromUrl());

const handleSelect = (key: string) => {
  selectedKey.value = key;
};

const handleBack = () => {
  selectedKey.value = null;
};

const handleSaved = () => {
  selectedKey.value = null;
};
</script>
