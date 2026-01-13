<template>
  <div class="space-y-4">
    <!-- Search -->
    <div class="form-control">
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Search scrawl scripts..."
        class="input input-bordered"
      />
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <span>{{ error }}</span>
    </div>

    <!-- Scripts Grid -->
    <div v-else-if="filteredScripts.length > 0" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      <div
        v-for="script in filteredScripts"
        :key="script.path"
        class="card bg-base-200 shadow-md hover:shadow-lg transition-shadow cursor-pointer"
        @click="$emit('select', script.script)"
      >
        <div class="card-body p-4">
          <h3 class="card-title text-sm">{{ script.script.id }}</h3>
          <p v-if="script.script.description" class="text-xs opacity-70">{{ script.script.description }}</p>
          <p class="text-xs opacity-50">{{ script.filename }}</p>

          <div class="card-actions justify-end mt-2">
            <button
              class="btn btn-xs btn-ghost"
              title="Duplicate"
              @click.stop="$emit('duplicate', script.script)"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="text-center py-8 opacity-50">
      <p>No scrawl scripts found</p>
      <p class="text-sm mt-2">Create a new script to get started</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { ScrawlScript } from '@nimbus/shared';
import { apiService } from '../../services/ApiService';
import { useWorld } from '@/composables/useWorld';

const emit = defineEmits<{
  select: [script: ScrawlScript];
  duplicate: [script: ScrawlScript];
}>();

interface ScriptAsset {
  path: string;
  filename: string;
  script: ScrawlScript;
}

const { currentWorldId } = useWorld();
const searchQuery = ref('');
const scripts = ref<ScriptAsset[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);

const filteredScripts = computed(() => {
  if (!searchQuery.value) return scripts.value;

  const query = searchQuery.value.toLowerCase();
  return scripts.value.filter(s =>
    s.script.id.toLowerCase().includes(query) ||
    s.script.description?.toLowerCase().includes(query) ||
    s.filename.toLowerCase().includes(query)
  );
});

async function loadScripts() {
  if (!currentWorldId.value) {
    scripts.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    // Get all .scrawl.json files from assets
    // Filter by query=scrawl to get files in scrawl/ directory
    const response = await apiService.get<{ assets: any[] }>(
      `/control/worlds/${currentWorldId.value}/assets?query=scrawl`
    );

    const assets = response.assets || [];

    // Filter to only .scrawl.json files (not effects.json, step-definitions.json, etc.)
    const scrawlAssets = assets.filter(asset =>
      asset.path.endsWith('.scrawl.json')
    );

    // Load each script
    const loadedScripts: ScriptAsset[] = [];
    for (const asset of scrawlAssets) {
      try {
        // Fetch script content
        const scriptUrl = `${apiService.getBaseUrl()}/control/worlds/${currentWorldId.value}/assets/${asset.path}`;
        const scriptResponse = await fetch(scriptUrl, {
          credentials: 'include'
        });
        const script: ScrawlScript = await scriptResponse.json();

        loadedScripts.push({
          path: asset.path,
          filename: asset.path.split('/').pop() || asset.path,
          script,
        });
      } catch (e) {
        console.warn('Failed to load script:', asset.path, e);
      }
    }

    scripts.value = loadedScripts;
  } catch (e: any) {
    error.value = e.message || 'Failed to load scripts';
    console.error('Failed to load scripts:', e);
  } finally {
    loading.value = false;
  }
}

// Watch for world changes
watch(currentWorldId, () => {
  loadScripts();
}, { immediate: true });
</script>
