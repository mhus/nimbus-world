<template>
  <div class="bg-base-100 rounded-lg shadow p-4">
    <!-- Controls -->
    <div class="flex justify-between items-center mb-4">
      <div class="text-sm text-base-content/70">
        {{ hexGrids.length }} hex grid(s)
      </div>
      <div class="flex gap-2">
        <button class="btn btn-sm btn-ghost" @click="zoomOut" title="Zoom out">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM13 10H7" />
          </svg>
        </button>
        <button class="btn btn-sm btn-ghost" @click="zoomIn" title="Zoom in">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
          </svg>
        </button>
        <button class="btn btn-sm btn-ghost" @click="resetView" title="Reset view">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
        </button>
      </div>
    </div>

    <!-- SVG Container -->
    <div class="border border-base-300 rounded overflow-auto bg-base-200" style="height: 600px;">
      <svg
        ref="svgElement"
        :width="svgWidth"
        :height="svgHeight"
        :viewBox="`${viewBox.x} ${viewBox.y} ${viewBox.width} ${viewBox.height}`"
        class="cursor-move"
        @mousedown="startPan"
        @mousemove="doPan"
        @mouseup="endPan"
        @mouseleave="endPan"
        @wheel.prevent="handleWheel"
      >
        <!-- Grid hexagons -->
        <g v-for="hex in allHexagons" :key="hex.key">
          <polygon
            :points="hex.points"
            :fill="hex.color"
            :stroke="hex.strokeColor"
            :stroke-width="2 / scale"
            class="cursor-pointer transition-opacity hover:opacity-80"
            @click.stop="handleHexClick(hex)"
          />
          <text
            v-if="hex.label"
            :x="hex.centerX"
            :y="hex.centerY"
            text-anchor="middle"
            dominant-baseline="middle"
            :font-size="12 / scale"
            :fill="hex.textColor"
            class="pointer-events-none select-none"
          >
            {{ hex.label }}
          </text>
        </g>
      </svg>
    </div>

    <!-- Legend -->
    <div class="mt-4 flex gap-4 text-sm">
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded" style="background-color: #3b82f6;"></div>
        <span>Existing (enabled)</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded" style="background-color: #6b7280;"></div>
        <span>Existing (disabled)</span>
      </div>
      <div class="flex items-center gap-2">
        <div class="w-4 h-4 rounded" style="background-color: #d1d5db;"></div>
        <span>Empty (click to create)</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { HexGridWithId } from '@/composables/useHexGrids';

interface Props {
  hexGrids: HexGridWithId[];
  loading?: boolean;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  edit: [hexGrid: HexGridWithId];
  create: [q: number, r: number];
}>();

// Hex geometry constants
const HEX_SIZE = 50;
const HEX_WIDTH = Math.sqrt(3) * HEX_SIZE;
const HEX_HEIGHT = 2 * HEX_SIZE;

// SVG dimensions
const svgWidth = ref(1200);
const svgHeight = ref(600);

// View state
const scale = ref(1);
const viewBox = ref({ x: -600, y: -300, width: 1200, height: 600 });

// Pan state
const isPanning = ref(false);
const panStart = ref({ x: 0, y: 0 });

/**
 * Convert hex coordinates to pixel coordinates (pointy-top)
 */
const hexToPixel = (q: number, r: number): { x: number; y: number } => {
  const x = HEX_SIZE * (Math.sqrt(3) * q + Math.sqrt(3) / 2 * r);
  const y = HEX_SIZE * (3 / 2 * r);
  return { x, y };
};

/**
 * Get hexagon corner points
 */
const getHexPoints = (centerX: number, centerY: number): string => {
  const points: string[] = [];
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 3) * i - Math.PI / 6; // Pointy-top
    const x = centerX + HEX_SIZE * Math.cos(angle);
    const y = centerY + HEX_SIZE * Math.sin(angle);
    points.push(`${x},${y}`);
  }
  return points.join(' ');
};

/**
 * Get all neighbor positions
 */
const getNeighbors = (q: number, r: number): Array<{ q: number; r: number }> => {
  return [
    { q: q + 1, r: r },
    { q: q + 1, r: r - 1 },
    { q: q, r: r - 1 },
    { q: q - 1, r: r },
    { q: q - 1, r: r + 1 },
    { q: q, r: r + 1 },
  ];
};

/**
 * Parse position string to q and r
 */
const parsePosition = (position: string): { q: number; r: number } => {
  const [q, r] = position.split(':').map(Number);
  return { q, r };
};

/**
 * Check if a position has an existing hex grid
 */
const hasHexAt = (q: number, r: number): boolean => {
  return props.hexGrids.some(hex => {
    const pos = parsePosition(hex.position);
    return pos.q === q && pos.r === r;
  });
};

/**
 * Get hex grid at position
 */
const getHexAt = (q: number, r: number): HexGridWithId | null => {
  return props.hexGrids.find(hex => {
    const pos = parsePosition(hex.position);
    return pos.q === q && pos.r === r;
  }) || null;
};

/**
 * Compute all hexagons to display (existing + empty neighbors)
 */
const allHexagons = computed(() => {
  const hexagons: Array<{
    key: string;
    q: number;
    r: number;
    centerX: number;
    centerY: number;
    points: string;
    color: string;
    strokeColor: string;
    textColor: string;
    label: string;
    isExisting: boolean;
    hexGrid?: HexGridWithId;
  }> = [];

  const emptyPositions = new Set<string>();

  // Add existing hexagons
  props.hexGrids.forEach(hexGrid => {
    const { q, r } = parsePosition(hexGrid.position);
    const { x, y } = hexToPixel(q, r);

    hexagons.push({
      key: `${q}:${r}`,
      q,
      r,
      centerX: x,
      centerY: y,
      points: getHexPoints(x, y),
      color: hexGrid.enabled ? '#3b82f6' : '#6b7280',
      strokeColor: hexGrid.enabled ? '#2563eb' : '#4b5563',
      textColor: '#ffffff',
      label: hexGrid.publicData.name || `${q}:${r}`,
      isExisting: true,
      hexGrid
    });

    // Add empty neighbors
    getNeighbors(q, r).forEach(neighbor => {
      const key = `${neighbor.q}:${neighbor.r}`;
      if (!hasHexAt(neighbor.q, neighbor.r) && !emptyPositions.has(key)) {
        emptyPositions.add(key);
      }
    });
  });

  // Add empty hexagons
  emptyPositions.forEach(posKey => {
    const [q, r] = posKey.split(':').map(Number);
    const { x, y } = hexToPixel(q, r);

    hexagons.push({
      key: posKey,
      q,
      r,
      centerX: x,
      centerY: y,
      points: getHexPoints(x, y),
      color: '#d1d5db',
      strokeColor: '#9ca3af',
      textColor: '#6b7280',
      label: '',
      isExisting: false
    });
  });

  // If no hexagons exist, show hex at 0:0
  if (hexagons.length === 0) {
    const { x, y } = hexToPixel(0, 0);
    hexagons.push({
      key: '0:0',
      q: 0,
      r: 0,
      centerX: x,
      centerY: y,
      points: getHexPoints(x, y),
      color: '#d1d5db',
      strokeColor: '#9ca3af',
      textColor: '#6b7280',
      label: '',
      isExisting: false
    });
  }

  return hexagons;
});

/**
 * Handle hexagon click
 */
const handleHexClick = (hex: typeof allHexagons.value[0]) => {
  if (hex.isExisting && hex.hexGrid) {
    emit('edit', hex.hexGrid);
  } else {
    emit('create', hex.q, hex.r);
  }
};

/**
 * Zoom controls
 */
const zoomIn = () => {
  scale.value = Math.min(scale.value * 1.2, 5);
  updateViewBox();
};

const zoomOut = () => {
  scale.value = Math.max(scale.value / 1.2, 0.2);
  updateViewBox();
};

const resetView = () => {
  scale.value = 1;
  viewBox.value = { x: -600, y: -300, width: 1200, height: 600 };
};

const updateViewBox = () => {
  const width = 1200 / scale.value;
  const height = 600 / scale.value;
  viewBox.value = {
    ...viewBox.value,
    width,
    height
  };
};

/**
 * Pan controls
 */
const startPan = (event: MouseEvent) => {
  isPanning.value = true;
  panStart.value = { x: event.clientX, y: event.clientY };
};

const doPan = (event: MouseEvent) => {
  if (!isPanning.value) return;

  const dx = (panStart.value.x - event.clientX) / scale.value;
  const dy = (panStart.value.y - event.clientY) / scale.value;

  viewBox.value.x += dx;
  viewBox.value.y += dy;

  panStart.value = { x: event.clientX, y: event.clientY };
};

const endPan = () => {
  isPanning.value = false;
};

/**
 * Wheel zoom
 */
const handleWheel = (event: WheelEvent) => {
  const delta = event.deltaY > 0 ? 0.9 : 1.1;
  scale.value = Math.max(0.2, Math.min(5, scale.value * delta));
  updateViewBox();
};

onMounted(() => {
  // Center view on first load if hexagons exist
  if (props.hexGrids.length > 0) {
    const firstHex = props.hexGrids[0];
    const { q, r } = parsePosition(firstHex.position);
    const { x, y } = hexToPixel(q, r);
    viewBox.value.x = x - viewBox.value.width / 2;
    viewBox.value.y = y - viewBox.value.height / 2;
  }
});
</script>
