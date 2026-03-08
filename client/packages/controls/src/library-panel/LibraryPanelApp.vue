<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-4">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-amber-400">Bibliothek</h1>
            <p class="text-gray-400 text-sm mt-1">Gesammelte Dokumente</p>
          </div>
          <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Back to Panels">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
          </a>
        </div>
      </div>
    </header>

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

    <!-- Main Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-6">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-6 h-full">
        <!-- Left: Document List -->
        <div class="lg:col-span-1">
          <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-4">
            <h2 class="text-lg font-bold text-amber-400 mb-3">Dokumente</h2>

            <div v-if="items.length === 0" class="text-center py-8 text-gray-500">
              Keine Dokumente in der Bibliothek
            </div>

            <div v-else class="space-y-1 max-h-[70vh] overflow-y-auto">
              <div
                v-for="item in items"
                :key="item.progressId"
                class="p-3 rounded-lg cursor-pointer transition-all border"
                :class="selectedItem?.progressId === item.progressId
                  ? 'bg-amber-900/30 border-amber-600 text-amber-200'
                  : 'bg-gray-700/50 border-gray-700 hover:bg-gray-700 hover:border-gray-600'"
                @click="selectItem(item)"
              >
                <div class="font-medium text-sm truncate">{{ item.title || 'Unbenannt' }}</div>
                <div v-if="item.createdAt" class="text-xs text-gray-500 mt-1">{{ formatDate(item.createdAt) }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Right: Document Content -->
        <div class="lg:col-span-2">
          <div class="bg-gray-800 rounded-lg shadow-md border border-gray-700 p-6 min-h-[50vh]">
            <!-- No selection -->
            <div v-if="!selectedItem" class="flex items-center justify-center h-full text-gray-500">
              <p>Waehle ein Dokument aus der Liste</p>
            </div>

            <!-- Loading document -->
            <div v-else-if="docLoading" class="flex items-center justify-center h-full">
              <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-amber-400"></div>
            </div>

            <!-- Document error -->
            <div v-else-if="docError" class="text-red-400 text-center py-8">
              {{ docError }}
            </div>

            <!-- Document content -->
            <div v-else>
              <h2 class="text-xl font-bold text-amber-400 mb-4">{{ docTitle }}</h2>
              <div
                v-if="docFormat === 'markdown'"
                class="prose prose-invert prose-amber max-w-none"
                v-html="renderedContent"
              ></div>
              <div v-else class="whitespace-pre-wrap text-gray-200">{{ docContent }}</div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { marked } from 'marked';
import { apiService } from '@/services/ApiService';

interface LibraryItem {
  progressId: string;
  title: string;
  document: string;
  createdAt: string;
}

interface DocumentResponse {
  title: string;
  content: string;
  format: string;
}

const loading = ref(true);
const error = ref<string | null>(null);
const items = ref<LibraryItem[]>([]);
const selectedItem = ref<LibraryItem | null>(null);

const docLoading = ref(false);
const docError = ref<string | null>(null);
const docTitle = ref('');
const docContent = ref('');
const docFormat = ref('');

const renderedContent = computed(() => {
  if (docFormat.value !== 'markdown') return '';
  return marked.parse(docContent.value || '', { async: false }) as string;
});

const formatDate = (isoString: string): string => {
  try {
    return new Date(isoString).toLocaleDateString('de-DE', {
      day: '2-digit', month: '2-digit', year: 'numeric'
    });
  } catch {
    return '';
  }
};

const selectItem = async (item: LibraryItem) => {
  if (selectedItem.value?.progressId === item.progressId) return;
  selectedItem.value = item;
  docLoading.value = true;
  docError.value = null;

  try {
    const doc = await apiService.get<DocumentResponse>(
      `/control/player/document?progressId=${encodeURIComponent(item.progressId)}`
    );
    docTitle.value = doc.title || item.title || '';
    docContent.value = doc.content || '';
    docFormat.value = doc.format || 'plaintext';
  } catch (e: any) {
    docError.value = e.response?.data?.error || e.message || 'Dokument konnte nicht geladen werden';
  } finally {
    docLoading.value = false;
  }
};

onMounted(async () => {
  try {
    const response = await apiService.get<{ items: LibraryItem[] }>('/control/player/library');
    items.value = response.items || [];
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Bibliothek konnte nicht geladen werden';
  } finally {
    loading.value = false;
  }
});
</script>

<style>
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
