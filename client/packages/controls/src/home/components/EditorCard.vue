<template>
  <div class="bg-white rounded-lg shadow-md hover:shadow-lg transition-shadow">
    <a :href="url" class="block p-6 no-underline">
      <div class="flex items-center gap-3 mb-3">
        <slot />
        <h2 class="text-xl font-bold text-gray-800">{{ title }}</h2>
      </div>
      <p class="text-gray-600 text-sm mb-4">{{ description }}</p>
      <div class="inline-block w-full text-center bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 transition-colors">
        Open Editor
      </div>
    </a>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
  url: string;
}>();
</script>
