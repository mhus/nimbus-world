<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
      </div>
    </main>

    <!-- Dialog Content (placeholder) -->
    <main v-else class="flex-1 container mx-auto px-4 py-6">
      <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-6">
        <h1 class="text-2xl font-bold text-amber-400 mb-4">Dialog</h1>
        <p class="text-gray-400 mb-4">Playbook: {{ playbook }}</p>
        <div v-if="dialogData" class="text-gray-300">
          <pre class="bg-gray-900 rounded p-4 text-sm overflow-auto max-h-96">{{ JSON.stringify(dialogData, null, 2) }}</pre>
        </div>
        <p v-else class="text-gray-500 italic">No dialog data available.</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ApiService } from '@/services/ApiService';

const apiService = new ApiService();

const loading = ref(true);
const error = ref('');
const playbook = ref('');
const dialogData = ref<any>(null);

interface DialogResponse {
  playbook: string;
  progressId: string;
  data: any;
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const progressId = params.get('progressId');

  if (!progressId) {
    error.value = 'No progressId provided';
    loading.value = false;
    return;
  }

  try {
    const response = await apiService.get<DialogResponse>(
      `/control/player/dialog?progressId=${encodeURIComponent(progressId)}`
    );
    playbook.value = response.playbook || '';
    dialogData.value = response.data || null;
  } catch (e: any) {
    const msg = e.response?.data?.error || e.message || 'Failed to load dialog';
    error.value = msg;
  } finally {
    loading.value = false;
  }
});
</script>
