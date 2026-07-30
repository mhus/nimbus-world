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

    <!-- Document Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-6">
      <h1 class="text-2xl font-bold text-amber-400 mb-4">{{ title }}</h1>
      <div
        v-if="isMarkdown"
        class="prose prose-invert prose-amber max-w-none"
        v-html="renderedContent"
      ></div>
      <div v-else class="whitespace-pre-wrap text-gray-200">{{ content }}</div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { renderMarkdown } from '@/utils/markdown';
import { ApiService } from '@/services/ApiService';

const apiService = new ApiService();

const loading = ref(true);
const error = ref('');
const title = ref('');
const content = ref('');
const format = ref('');

const isMarkdown = computed(() => format.value === 'markdown');

const renderedContent = computed(() => {
  if (!isMarkdown.value) return '';
  return renderMarkdown(content.value);
});

interface DocumentResponse {
  title: string;
  content: string;
  format: string;
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
    const doc = await apiService.get<DocumentResponse>(
      `/control/player/document?progressId=${encodeURIComponent(progressId)}`
    );
    title.value = doc.title || '';
    content.value = doc.content || '';
    format.value = doc.format || 'plaintext';
  } catch (e: any) {
    const msg = e.response?.data?.error || e.message || 'Failed to load document';
    error.value = msg;
  } finally {
    loading.value = false;
  }
});
</script>

<style>
/* Minimal prose styling for markdown content */
.prose h1 { font-size: 1.75rem; font-weight: 700; margin-bottom: 0.75rem; margin-top: 1.5rem; }
.prose h2 { font-size: 1.5rem; font-weight: 600; margin-bottom: 0.5rem; margin-top: 1.25rem; }
.prose h3 { font-size: 1.25rem; font-weight: 600; margin-bottom: 0.5rem; margin-top: 1rem; }
.prose p { margin-bottom: 0.75rem; line-height: 1.7; }
.prose ul { list-style-type: disc; padding-left: 1.5rem; margin-bottom: 0.75rem; }
.prose ol { list-style-type: decimal; padding-left: 1.5rem; margin-bottom: 0.75rem; }
.prose li { margin-bottom: 0.25rem; }
.prose a { color: #fbbf24; text-decoration: underline; }
.prose blockquote { border-left: 3px solid #fbbf24; padding-left: 1rem; font-style: italic; color: #9ca3af; margin-bottom: 0.75rem; }
.prose code { background: rgba(255,255,255,0.1); padding: 0.15rem 0.3rem; border-radius: 0.25rem; font-size: 0.9em; }
.prose pre { background: rgba(0,0,0,0.4); padding: 1rem; border-radius: 0.5rem; overflow-x: auto; margin-bottom: 0.75rem; }
.prose pre code { background: none; padding: 0; }
.prose hr { border-color: #374151; margin: 1.5rem 0; }
.prose strong { color: #f3f4f6; }
.prose em { color: #d1d5db; }
</style>
