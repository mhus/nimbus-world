<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-bold text-amber-400">Map</h1>
            <p class="text-gray-400 text-sm mt-0.5">{{ hexTitle || hexName || 'Unknown Area' }}</p>
          </div>
          <div class="flex items-center gap-2">
            <!-- Map type toggle -->
            <button
              class="px-3 py-1.5 rounded text-sm font-medium transition-colors"
              :class="mapType === 'level' ? 'bg-amber-600 text-white' : 'bg-gray-700 text-gray-300 hover:bg-gray-600'"
              @click="mapType = 'level'"
            >Terrain</button>
            <button
              class="px-3 py-1.5 rounded text-sm font-medium transition-colors"
              :class="mapType === 'material' ? 'bg-amber-600 text-white' : 'bg-gray-700 text-gray-300 hover:bg-gray-600'"
              @click="mapType = 'material'"
            >Schema</button>
            <!-- Home button -->
            <button
              v-if="!isHome"
              class="p-2 rounded bg-green-700 hover:bg-green-600 transition-colors"
              title="Back to current position"
              @click="goHome()"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
              </svg>
            </button>
            <!-- Back to panels -->
            <a href="/controls/panels.html" class="p-2 rounded bg-gray-700 hover:bg-gray-600 transition-colors" title="Back to Panels">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
              </svg>
            </a>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="loading" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading map...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="error" class="flex-1 container mx-auto px-4 py-8">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
      </div>
    </main>

    <!-- Map Content -->
    <main v-else class="flex-1 flex items-center justify-center p-4">
      <div class="relative inline-block">
        <!-- Map image container -->
        <div class="relative">
          <img
            :src="mapImageUrl"
            alt="Map"
            class="max-w-full max-h-[70vh] rounded-lg border border-gray-700"
            style="image-rendering: pixelated;"
            @error="onMapImageError"
            @load="onMapImageLoad"
          />
          <!-- Player position overlay (only on home hex) -->
          <div
            v-if="isHome && playerOverlay"
            class="absolute pointer-events-none"
            :style="playerOverlayStyle"
          >
            <!-- Crosshair -->
            <div class="relative w-6 h-6">
              <div class="absolute left-1/2 top-0 w-0.5 h-full bg-red-500 -translate-x-1/2"></div>
              <div class="absolute top-1/2 left-0 h-0.5 w-full bg-red-500 -translate-y-1/2"></div>
              <div class="absolute left-1/2 top-1/2 w-2 h-2 bg-red-500 rounded-full -translate-x-1/2 -translate-y-1/2"></div>
            </div>
          </div>
        </div>

        <!-- Navigation buttons -->
        <!-- NW -->
        <button
          v-if="neighborByEdge('NW')?.canNavigate"
          class="absolute nav-btn nav-nw"
          :title="neighborByEdge('NW')?.title || 'NW'"
          @click="navigateTo(neighborByEdge('NW')!)"
        >NW</button>
        <!-- NE -->
        <button
          v-if="neighborByEdge('NE')?.canNavigate"
          class="absolute nav-btn nav-ne"
          :title="neighborByEdge('NE')?.title || 'NE'"
          @click="navigateTo(neighborByEdge('NE')!)"
        >NE</button>
        <!-- W -->
        <button
          v-if="neighborByEdge('W')?.canNavigate"
          class="absolute nav-btn nav-w"
          :title="neighborByEdge('W')?.title || 'W'"
          @click="navigateTo(neighborByEdge('W')!)"
        >W</button>
        <!-- E -->
        <button
          v-if="neighborByEdge('E')?.canNavigate"
          class="absolute nav-btn nav-e"
          :title="neighborByEdge('E')?.title || 'E'"
          @click="navigateTo(neighborByEdge('E')!)"
        >E</button>
        <!-- SW -->
        <button
          v-if="neighborByEdge('SW')?.canNavigate"
          class="absolute nav-btn nav-sw"
          :title="neighborByEdge('SW')?.title || 'SW'"
          @click="navigateTo(neighborByEdge('SW')!)"
        >SW</button>
        <!-- SE -->
        <button
          v-if="neighborByEdge('SE')?.canNavigate"
          class="absolute nav-btn nav-se"
          :title="neighborByEdge('SE')?.title || 'SE'"
          @click="navigateTo(neighborByEdge('SE')!)"
        >SE</button>

        <!-- Hex coordinates display -->
        <div class="absolute bottom-2 left-2 bg-black/60 text-gray-300 text-xs px-2 py-1 rounded">
          {{ currentQ }}, {{ currentR }}
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { apiService } from '@/services/ApiService';

interface NeighborInfo {
  edge: string;
  q: number;
  r: number;
  exists: boolean;
  explored: boolean;
  name?: string;
  title?: string;
  canNavigate: boolean;
}

interface HexData {
  q: number;
  r: number;
  name?: string;
  title?: string;
  description?: string;
  exists: boolean;
  neighbors: NeighborInfo[];
  // Only on home response
  playerWorldX?: number;
  playerWorldZ?: number;
  hexCenterX?: number;
  hexCenterZ?: number;
  hexGridSize?: number;
  hexGridWidth?: number;
}

const loading = ref(true);
const error = ref<string | null>(null);
const mapType = ref<'level' | 'material'>('level');

const currentQ = ref(0);
const currentR = ref(0);
const homeQ = ref(0);
const homeR = ref(0);
const hexName = ref('');
const hexTitle = ref('');
const neighbors = ref<NeighborInfo[]>([]);

// Player overlay data (only valid on home hex)
const playerOverlay = ref<{
  playerWorldX: number;
  playerWorldZ: number;
  hexCenterX: number;
  hexCenterZ: number;
  hexGridSize: number;
  hexGridWidth: number;
} | null>(null);

const mapImageWidth = ref(0);
const mapImageHeight = ref(0);

const isHome = computed(() => currentQ.value === homeQ.value && currentR.value === homeR.value);

const mapImageUrl = computed(() => {
  const q = currentQ.value;
  const r = currentR.value;
  const type = mapType.value;
  return `${apiService.getBaseUrl()}/control/player/assets/w:map/${q}_${r}/${type}.png`;
});

const playerOverlayStyle = computed(() => {
  if (!playerOverlay.value || mapImageWidth.value === 0 || mapImageHeight.value === 0) {
    return { display: 'none' };
  }

  const { playerWorldX, playerWorldZ, hexCenterX, hexCenterZ, hexGridSize, hexGridWidth } = playerOverlay.value;

  // Player position relative to hex center in world coords
  const relX = playerWorldX - hexCenterX;
  const relZ = playerWorldZ - hexCenterZ;

  // Map image represents the hex area: width = hexGridWidth, height = hexGridSize
  // Image center = hex center
  // Convert world offset to image percentage
  const pctX = 50 + (relX / hexGridWidth) * 100;
  const pctY = 50 - (relZ / hexGridSize) * 100; // Z is inverted (world north = image top)

  return {
    left: `calc(${pctX}% - 12px)`,
    top: `calc(${pctY}% - 12px)`,
  };
});

const neighborByEdge = (edge: string): NeighborInfo | undefined => {
  return neighbors.value.find(n => n.edge === edge);
};

const navigateTo = async (neighbor: NeighborInfo) => {
  await loadHex(neighbor.q, neighbor.r);
};

const goHome = async () => {
  await loadHome();
};

const onMapImageError = () => {
  // Image not found - not critical, just show broken image
};

const onMapImageLoad = (event: Event) => {
  const img = event.target as HTMLImageElement;
  mapImageWidth.value = img.naturalWidth;
  mapImageHeight.value = img.naturalHeight;
};

const loadHome = async () => {
  try {
    loading.value = true;
    error.value = null;

    const data = await apiService.get<HexData>('/control/player/map/home');

    homeQ.value = data.q;
    homeR.value = data.r;
    currentQ.value = data.q;
    currentR.value = data.r;
    hexName.value = data.name || '';
    hexTitle.value = data.title || '';
    neighbors.value = (data.neighbors || []).map(n => ({
      ...n,
      canNavigate: n.exists && n.explored,
    }));

    if (data.playerWorldX !== undefined) {
      playerOverlay.value = {
        playerWorldX: data.playerWorldX,
        playerWorldZ: data.playerWorldZ!,
        hexCenterX: data.hexCenterX!,
        hexCenterZ: data.hexCenterZ!,
        hexGridSize: data.hexGridSize!,
        hexGridWidth: data.hexGridWidth!,
      };
    }
  } catch (err) {
    console.error('[MapPanel] Failed to load home:', err);
    error.value = 'Failed to load map data.';
  } finally {
    loading.value = false;
  }
};

const loadHex = async (q: number, r: number) => {
  try {
    loading.value = true;
    error.value = null;

    const data = await apiService.get<HexData>(`/control/player/map/hex?q=${q}&r=${r}`);

    currentQ.value = data.q;
    currentR.value = data.r;
    hexName.value = data.name || '';
    hexTitle.value = data.title || '';
    neighbors.value = (data.neighbors || []).map(n => ({
      ...n,
      canNavigate: n.exists && n.explored,
    }));

    // No player overlay on non-home hexes
    playerOverlay.value = null;
  } catch (err) {
    console.error('[MapPanel] Failed to load hex:', err);
    error.value = 'Failed to load hex data.';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadHome();
});
</script>

<style scoped>
.nav-btn {
  @apply px-3 py-1.5 rounded bg-amber-700/80 hover:bg-amber-600 text-white text-xs font-bold
         transition-colors shadow-lg border border-amber-500/50 backdrop-blur-sm;
}

/* Pointy-top hex navigation button positions */
.nav-nw { top: 5%; left: 5%; }
.nav-ne { top: 5%; right: 5%; }
.nav-w  { top: 50%; left: 0%; transform: translateY(-50%); }
.nav-e  { top: 50%; right: 0%; transform: translateY(-50%); }
.nav-sw { bottom: 5%; left: 5%; }
.nav-se { bottom: 5%; right: 5%; }
</style>
