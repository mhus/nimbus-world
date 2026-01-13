<template>
  <div class="fixed inset-0 bg-black/50 flex items-center justify-center" style="z-index: 9999;" @click.self="handleClose">
    <div class="bg-base-100 rounded-lg shadow-xl w-full h-full max-w-7xl max-h-[90vh] flex flex-col" style="z-index: 10000;">
      <!-- Header -->
      <div class="p-4 border-b border-base-300 flex items-center justify-between">
        <div>
          <h2 class="text-xl font-bold">Block Grid Editor</h2>
          <p class="text-sm text-base-content/70">
            {{ sourceType === 'terrain' ? 'WLayerTerrain' : 'WLayerModel' }} -
            {{ layerName }} {{ modelName ? `/ ${modelName}` : '' }}
          </p>
        </div>
        <button class="btn btn-sm btn-ghost" @click="handleClose">Close</button>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-hidden flex">
        <!-- Main Grid View -->
        <div class="flex-1 overflow-hidden bg-base-200 flex items-center justify-center">
          <div v-if="loading" class="flex items-center justify-center">
            <span class="loading loading-spinner loading-lg"></span>
          </div>

          <div v-else-if="error" class="alert alert-error m-4">
            <span>{{ error }}</span>
          </div>

          <div v-else class="w-full h-full flex items-center justify-center">
            <!-- Isometric Grid Canvas -->
            <canvas
              ref="canvasRef"
              :width="canvasWidth"
              :height="canvasHeight"
              class="bg-white cursor-crosshair"
              style="max-width: 100%; max-height: 100%;"
              @click="handleCanvasClick"
              @mousemove="handleCanvasHover"
            />
          </div>
        </div>

        <!-- Sidebar: Navigation and Block Details -->
        <div class="w-96 border-l border-base-300 p-4 overflow-auto flex flex-col gap-4">
          <!-- Block Limit and Rotation -->
          <div class="grid grid-cols-2 gap-4">
            <!-- Block Limit Selection -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-semibold">View Area Size</span>
              </label>
              <select v-model.number="blockLimit" class="select select-bordered select-sm" @change="handleBlockLimitChange">
                <option :value="16">16 blocks</option>
                <option :value="32">32 blocks</option>
                <option :value="64">64 blocks</option>
                <option :value="128">128 blocks</option>
                <option :value="256">256 blocks</option>
                <option :value="512">512 blocks</option>
              </select>
              <label class="label">
                <span class="label-text-alt">
                  {{ sourceType === 'terrain' ? 'Lazy loading' : `${blockCoordinates.length}/${allBlockCoordinates.length}` }}
                </span>
              </label>
            </div>

            <!-- Rotation Selection -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-semibold">Rotation</span>
              </label>
              <select v-model.number="viewRotation" class="select select-bordered select-sm" @change="drawGrid">
                <option :value="0">0°</option>
                <option :value="90">90°</option>
                <option :value="180">180°</option>
                <option :value="270">270°</option>
              </select>
              <label class="label">
                <span class="label-text-alt">&nbsp;</span>
              </label>
            </div>
          </div>

          <!-- Alpha Blending Toggle -->
          <div class="form-control">
            <label class="label cursor-pointer justify-start gap-2">
              <input type="checkbox" v-model="useAlphaBlending" class="checkbox checkbox-sm" @change="drawGrid" />
              <span class="label-text">Alpha Blending</span>
            </label>
          </div>

          <div class="divider"></div>

          <!-- Navigation Component -->
          <div class="flex flex-col items-center">
            <h3 class="font-bold text-lg mb-2">Pan View</h3>
            <NavigateSelectedBlockComponent
              :selected-block="viewCenter"
              :step="navigationStep"
              :size="200"
              :show-execute-button="false"
              @navigate="handlePanView"
            />
            <div class="text-xs text-base-content/70 mt-2">
              Step: {{ navigationStep }} blocks
            </div>
          </div>

          <div class="divider"></div>

          <!-- Block Details -->
          <div v-if="selectedBlock">
            <h3 class="font-bold text-lg mb-4">Block Details</h3>

            <div class="space-y-2 mb-4">
              <div class="flex gap-2">
                <span class="badge badge-error">X: {{ selectedBlock.x }}</span>
                <span class="badge badge-success">Y: {{ selectedBlock.y }}</span>
                <span class="badge badge-info">Z: {{ selectedBlock.z }}</span>
              </div>
            </div>

            <div v-if="loadingBlockDetails" class="flex items-center justify-center py-8">
              <span class="loading loading-spinner loading-md"></span>
            </div>

            <div v-else-if="blockDetails" class="space-y-3">
              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Block Type ID</span>
                </label>
                <input
                  type="text"
                  :value="blockDetails.block?.blockTypeId || 'air'"
                  class="input input-bordered input-sm"
                  readonly
                />
              </div>

              <div class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Status</span>
                </label>
                <input
                  type="number"
                  :value="blockDetails.block?.status || 0"
                  class="input input-bordered input-sm"
                  readonly
                />
              </div>

              <div v-if="blockDetails.group !== undefined" class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Group</span>
                </label>
                <input
                  type="number"
                  :value="blockDetails.group"
                  class="input input-bordered input-sm"
                  readonly
                />
              </div>

              <div v-if="blockDetails.weight !== undefined" class="form-control">
                <label class="label">
                  <span class="label-text font-semibold">Weight</span>
                </label>
                <input
                  type="number"
                  :value="blockDetails.weight"
                  class="input input-bordered input-sm"
                  readonly
                />
              </div>

              <div v-if="blockDetails.override !== undefined" class="form-control">
                <label class="label cursor-pointer justify-start gap-2">
                  <input
                    type="checkbox"
                    :checked="blockDetails.override"
                    class="checkbox checkbox-sm"
                    disabled
                  />
                  <span class="label-text">Override</span>
                </label>
              </div>

              <!-- Raw JSON Display -->
              <div class="collapse collapse-arrow bg-base-200 mt-4">
                <input type="checkbox" />
                <div class="collapse-title text-sm font-medium">
                  Raw JSON
                </div>
                <div class="collapse-content">
                  <pre class="text-xs overflow-auto">{{ JSON.stringify(blockDetails, null, 2) }}</pre>
                </div>
              </div>
            </div>

            <div v-else class="text-center py-8 text-base-content/50">
              No block at this position
            </div>
          </div>

          <div v-else class="text-center py-8 text-base-content/50">
            Click on a block to view details
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, watch, nextTick } from 'vue';
import NavigateSelectedBlockComponent from '@/components/NavigateSelectedBlockComponent.vue';
import { apiService } from '@/services/ApiService';

interface Props {
  worldId: string;
  layerId: string;
  layerName: string;
  sourceType: 'terrain' | 'model';
  modelId?: string;
  modelName?: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  close: [];
}>();

// State
const loading = ref(true);
const error = ref<string | null>(null);
const allBlockCoordinates = ref<Array<{ x: number; y: number; z: number; color?: string }>>([]);
const blockCoordinates = ref<Array<{ x: number; y: number; z: number; color?: string }>>([]);
const selectedBlock = ref<{ x: number; y: number; z: number } | null>(null);
const loadingBlockDetails = ref(false);
const blockDetails = ref<any>(null);
const blockLimit = ref(128);  // Default: 128 blocks
const useAlphaBlending = ref(true);  // Default: alpha blending enabled
const viewRotation = ref(0);  // Default: 0 degrees

// View center position (for panning the visible grid area)
// Start with a reasonable default that will be updated after first load
// Models start at Y=0, Terrain starts at Y=64
const viewCenter = ref<{ x: number; y: number; z: number }>({
  x: 0,
  y: props.sourceType === 'model' ? 0 : 64,
  z: 0
});

// Canvas
const canvasRef = ref<HTMLCanvasElement | null>(null);
const canvasWidth = ref(1200);
const canvasHeight = ref(900);

// Isometric projection settings - dynamically calculated based on blockLimit
const tileWidth = computed(() => {
  // Calculate how many blocks fit in the view area (cubic root of blockLimit)
  const cubeSize = Math.ceil(Math.cbrt(blockLimit.value));
  // Use 70% of canvas width for the grid
  const availableWidth = canvasWidth.value * 0.7;
  // In isometric view, width spans roughly cubeSize * 2 (X + Z dimensions)
  return Math.max(8, Math.floor(availableWidth / (cubeSize * 2)));
});

const tileHeight = computed(() => tileWidth.value / 2);

// Dynamic offsets based on canvas size
const offsetX = computed(() => canvasWidth.value / 2);
const offsetY = computed(() => canvasHeight.value / 2);  // Center vertically

// Navigation step: size / 4 where size is cube root of blockLimit (represents linear dimension)
const navigationStep = computed(() => Math.max(1, Math.floor(Math.cbrt(blockLimit.value) / 4)));

// Update canvas size to match container
function updateCanvasSize() {
  const canvas = canvasRef.value;
  if (!canvas) {
    console.log('[updateCanvasSize] No canvas ref');
    return;
  }

  const parent = canvas.parentElement;
  if (!parent) {
    console.log('[updateCanvasSize] No parent element');
    return;
  }

  const rect = parent.getBoundingClientRect();
  console.log('[updateCanvasSize] Parent rect:', rect.width, 'x', rect.height);

  canvasWidth.value = rect.width;
  canvasHeight.value = rect.height;

  console.log('[updateCanvasSize] Setting canvas to:', canvasWidth.value, 'x', canvasHeight.value);

  // Redraw after resize
  nextTick(() => drawGrid());
}

// Grid bounds (dynamically calculated from blocks)
const gridBounds = computed(() => {
  if (blockCoordinates.value.length === 0) {
    return { minX: 0, maxX: 0, minY: 0, maxY: 0, minZ: 0, maxZ: 0 };
  }

  const minX = Math.min(...blockCoordinates.value.map(b => b.x));
  const maxX = Math.max(...blockCoordinates.value.map(b => b.x));
  const minY = Math.min(...blockCoordinates.value.map(b => b.y));
  const maxY = Math.max(...blockCoordinates.value.map(b => b.y));
  const minZ = Math.min(...blockCoordinates.value.map(b => b.z));
  const maxZ = Math.max(...blockCoordinates.value.map(b => b.z));

  return { minX, maxX, minY, maxY, minZ, maxZ };
});

// Rotate a block's coordinates around Y-axis based on viewRotation
// Rotation is performed around viewCenter, not origin
function rotateBlock(x: number, y: number, z: number): { x: number; y: number; z: number } {
  // Translate to origin (relative to viewCenter)
  const relX = x - viewCenter.value.x;
  const relZ = z - viewCenter.value.z;

  // Rotate around Y-axis (looking down from above)
  // 0° = no rotation, 90° = rotate clockwise, 180° = flip, 270° = rotate counter-clockwise
  let rotX: number, rotZ: number;
  switch (viewRotation.value) {
    case 90:
      rotX = -relZ;
      rotZ = relX;
      break;
    case 180:
      rotX = -relX;
      rotZ = -relZ;
      break;
    case 270:
      rotX = relZ;
      rotZ = -relX;
      break;
    default: // 0°
      rotX = relX;
      rotZ = relZ;
      break;
  }

  // Translate back
  return {
    x: rotX + viewCenter.value.x,
    y,
    z: rotZ + viewCenter.value.z
  };
}

// Convert 3D world coordinates to 2D isometric screen coordinates
function worldToScreen(x: number, y: number, z: number): { x: number; y: number } {
  // Subtract viewCenter to make blocks relative to view
  const relX = x - viewCenter.value.x;
  const relY = y - viewCenter.value.y;
  const relZ = z - viewCenter.value.z;

  // Flipped isometric projection (reversed X and Z)
  const isoX = (relZ - relX) * (tileWidth.value / 2) + offsetX.value;
  const isoY = (relZ + relX) * (tileHeight.value / 2) - relY * tileHeight.value + offsetY.value;
  return { x: isoX, y: isoY };
}

// Convert 2D screen coordinates to approximate 3D world coordinates
// This is an approximation - for accurate picking, we need ray casting
function screenToWorld(screenX: number, screenY: number, y: number = 0): { x: number; y: number; z: number } {
  // Inverse isometric projection
  const relX = screenX - offsetX.value;
  const relY = screenY - offsetY.value + y * tileHeight.value;

  const x = (relX / (tileWidth.value / 2) + relY / (tileHeight.value / 2)) / 2;
  const z = (relY / (tileHeight.value / 2) - relX / (tileWidth.value / 2)) / 2;

  return { x: Math.round(x), y, z: Math.round(z) };
}

// Draw the isometric grid
function drawGrid() {
  const canvas = canvasRef.value;
  if (!canvas) {
    console.log('[BlockGridEditor] drawGrid: no canvas');
    return;
  }

  const ctx = canvas.getContext('2d');
  if (!ctx) {
    console.log('[BlockGridEditor] drawGrid: no context');
    return;
  }

  console.log('[BlockGridEditor] drawGrid: rendering', blockCoordinates.value.length, 'blocks',
    'canvas:', canvasWidth.value, 'x', canvasHeight.value,
    'first block:', blockCoordinates.value[0]);

  // Clear canvas
  ctx.clearRect(0, 0, canvasWidth.value, canvasHeight.value);

  // Draw debug info
  ctx.fillStyle = '#000000';
  ctx.font = '14px monospace';
  ctx.fillText(`Blocks: ${blockCoordinates.value.length}`, 10, 20);
  ctx.fillText(`Canvas: ${canvasWidth.value}x${canvasHeight.value}`, 10, 40);
  ctx.fillText(`View Center: (${viewCenter.value.x}, ${viewCenter.value.y}, ${viewCenter.value.z})`, 10, 60);

  // Draw coordinates at specific blocks for debugging
  const debugBlocks = [
    { x: -5, y: 64, z: -5 },  // Front-left
    { x: 5, y: 64, z: -5 },   // Front-right
    { x: -5, y: 64, z: 5 },   // Back-left
    { x: 5, y: 64, z: 5 },    // Back-right
    { x: 0, y: 64, z: 0 },    // Center
    { x: 0, y: 64, z: 5 },    // Back-center
    { x: 5, y: 64, z: 0 },    // Right-center
  ];

  ctx.fillStyle = '#000000';
  ctx.font = '14px bold monospace';

  for (const debugBlock of debugBlocks) {
    const found = blockCoordinates.value.find(b =>
      b.x === debugBlock.x && b.y === debugBlock.y && b.z === debugBlock.z
    );
    if (found) {
      const rotated = rotateBlock(debugBlock.x, debugBlock.y, debugBlock.z);
      const pos = worldToScreen(rotated.x, rotated.y + 0.5, rotated.z + 0.5);
      const label = `(${debugBlock.x},${debugBlock.z})`;

      // Draw black text
      ctx.fillText(label, pos.x - 20, pos.y);
    }
  }

  // Sort blocks for proper rendering in isometric view
  // Apply rotation to blocks before sorting
  const rotatedBlocks = blockCoordinates.value.map(block => {
    const rotated = rotateBlock(block.x, block.y, block.z);
    return {
      original: block,
      x: rotated.x,
      y: rotated.y,
      z: rotated.z
    };
  });

  // Sort by rotated coordinates
  const sortedBlocks = rotatedBlocks.sort((a, b) => {
    // 1. Bottom to top (lower Y first) - stays the same
    if (a.y !== b.y) return a.y - b.y;
    // 2. Try reversed order: lower Z+X first
    const depthA = a.z + a.x;
    const depthB = b.z + b.x;
    if (depthA !== depthB) return depthA - depthB;  // Lower depth first (reversed)
    // 3. If same depth, reversed
    if (a.z !== b.z) return a.z - b.z;
    return a.x - b.x;
  });

  // Draw each block as a wireframe cube
  for (const blockData of sortedBlocks) {
    const { x, y, z } = blockData.x !== undefined ? blockData : blockData.original;
    const block = blockData.original || blockData;
    const color = block.color || '#3b82f6';
    const isSelected = selectedBlock.value?.x === x && selectedBlock.value?.y === y && selectedBlock.value?.z === z;

    // Calculate 8 corner points of the cube
    const corners = [
      worldToScreen(x, y, z),         // Bottom-front-left
      worldToScreen(x + 1, y, z),     // Bottom-front-right
      worldToScreen(x + 1, y, z + 1), // Bottom-back-right
      worldToScreen(x, y, z + 1),     // Bottom-back-left
      worldToScreen(x, y + 1, z),     // Top-front-left
      worldToScreen(x + 1, y + 1, z), // Top-front-right
      worldToScreen(x + 1, y + 1, z + 1), // Top-back-right
      worldToScreen(x, y + 1, z + 1), // Top-back-left
    ];

    // Selection outline
    if (isSelected) {
      ctx.strokeStyle = '#ff0000';
      ctx.lineWidth = 3;
      ctx.globalAlpha = 1.0;

      // Draw selection outline around all visible edges
      ctx.beginPath();
      // Top face
      ctx.moveTo(corners[4].x, corners[4].y);
      ctx.lineTo(corners[5].x, corners[5].y);
      ctx.lineTo(corners[6].x, corners[6].y);
      ctx.lineTo(corners[7].x, corners[7].y);
      ctx.closePath();
      // Left side (Z-positive face)
      ctx.moveTo(corners[7].x, corners[7].y);
      ctx.lineTo(corners[6].x, corners[6].y);
      ctx.lineTo(corners[2].x, corners[2].y);
      ctx.lineTo(corners[3].x, corners[3].y);
      ctx.closePath();
      // Right side (X-positive face)
      ctx.moveTo(corners[5].x, corners[5].y);
      ctx.lineTo(corners[6].x, corners[6].y);
      ctx.lineTo(corners[2].x, corners[2].y);
      ctx.lineTo(corners[1].x, corners[1].y);
      ctx.closePath();
      ctx.stroke();
    }

    // Draw filled faces in correct order: back to front
    ctx.globalAlpha = useAlphaBlending.value ? 0.8 : 1.0;

    // 1. Left side (Z-positive face, red) - furthest back
    ctx.fillStyle = isSelected ? '#f87171' : '#ef4444';
    ctx.beginPath();
    ctx.moveTo(corners[7].x, corners[7].y);  // Top-back-left
    ctx.lineTo(corners[6].x, corners[6].y);  // Top-back-right
    ctx.lineTo(corners[2].x, corners[2].y);  // Bottom-back-right
    ctx.lineTo(corners[3].x, corners[3].y);  // Bottom-back-left
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#991b1b';
    ctx.lineWidth = 1;
    ctx.stroke();

    // 2. Right side (X-positive face, green) - middle
    ctx.fillStyle = isSelected ? '#4ade80' : '#22c55e';
    ctx.beginPath();
    ctx.moveTo(corners[5].x, corners[5].y);  // Top-front-right
    ctx.lineTo(corners[6].x, corners[6].y);  // Top-back-right
    ctx.lineTo(corners[2].x, corners[2].y);  // Bottom-back-right
    ctx.lineTo(corners[1].x, corners[1].y);  // Bottom-front-right
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#166534';
    ctx.lineWidth = 1;
    ctx.stroke();

    // 3. Top face (bright, primary color) - closest to camera
    ctx.fillStyle = isSelected ? '#60a5fa' : '#3b82f6';
    ctx.beginPath();
    ctx.moveTo(corners[4].x, corners[4].y);
    ctx.lineTo(corners[5].x, corners[5].y);
    ctx.lineTo(corners[6].x, corners[6].y);
    ctx.lineTo(corners[7].x, corners[7].y);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = '#1e40af';
    ctx.lineWidth = 1;
    ctx.stroke();

    ctx.globalAlpha = 1.0;
  }
}

// Load block coordinates from backend
async function loadBlockCoordinates() {
  loading.value = true;
  error.value = null;

  try {
    const apiUrl = apiService.getBaseUrl();
    let url: string;

    if (props.sourceType === 'terrain') {
      // For terrain: send center and radius to load only relevant chunks
      // Use larger radius to ensure we load enough blocks
      const cubeSize = Math.ceil(Math.cbrt(blockLimit.value));
      const radiusXZ = cubeSize * 2;  // Double the cube size for X/Z
      const radiusY = cubeSize * 4;   // Quadruple for Y (more vertical range)
      console.log('[loadBlockCoordinates] Terrain: blockLimit=', blockLimit.value, 'cubeSize=', cubeSize, 'radiusXZ=', radiusXZ, 'radiusY=', radiusY);
      const params = new URLSearchParams({
        centerX: viewCenter.value.x.toString(),
        centerY: viewCenter.value.y.toString(),
        centerZ: viewCenter.value.z.toString(),
        radiusXZ: radiusXZ.toString(),
        radiusY: radiusY.toString()
      });
      url = `${apiUrl}/control/worlds/${props.worldId}/layers/${props.layerId}/grid/terrain/blocks?${params}`;
    } else {
      // For model: load all blocks at once
      url = `${apiUrl}/control/worlds/${props.worldId}/layers/${props.layerId}/grid/models/${props.modelId}/blocks`;
    }

    const response = await fetch(url, {
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`Failed to load blocks: ${response.statusText}`);
    }

    const data = await response.json();
    console.log('[BlockGridEditor] Loaded blocks:', {
      sourceType: props.sourceType,
      count: data.count,
      blocks: data.blocks?.length,
      viewCenter: viewCenter.value,
      blockLimit: blockLimit.value,
      chunksChecked: data.chunksChecked,
      chunksFound: data.chunksFound,
      hint: data.hint,
      mountPoint: data.mountPoint
    });

    if (props.sourceType === 'terrain') {
      // For terrain: blocks are already filtered by backend
      blockCoordinates.value = data.blocks || [];
      console.log('[BlockGridEditor] Terrain blocks:', blockCoordinates.value.length);

      // Show hint if provided
      if (data.hint && blockCoordinates.value.length === 0) {
        error.value = data.hint;
      }
    } else {
      // For model: use relative coordinates directly (without adding mountPoint)
      // This shows the block coordinates as stored in WLayerModel.content
      allBlockCoordinates.value = (data.blocks || []).map(block => ({
        x: block.x,
        y: block.y,
        z: block.z,
        color: block.color
      }));

      // Initialize view center for model based on actual block bounds
      if (allBlockCoordinates.value.length > 0) {
        const bounds = {
          minX: Math.min(...allBlockCoordinates.value.map(b => b.x)),
          maxX: Math.max(...allBlockCoordinates.value.map(b => b.x)),
          minY: Math.min(...allBlockCoordinates.value.map(b => b.y)),
          maxY: Math.max(...allBlockCoordinates.value.map(b => b.y)),
          minZ: Math.min(...allBlockCoordinates.value.map(b => b.z)),
          maxZ: Math.max(...allBlockCoordinates.value.map(b => b.z)),
        };

        viewCenter.value = {
          x: Math.floor((bounds.minX + bounds.maxX) / 2),
          y: Math.floor((bounds.minY + bounds.maxY) / 2),
          z: Math.floor((bounds.minZ + bounds.maxZ) / 2),
        };

        console.log('[BlockGridEditor] Model view center set to:', viewCenter.value, 'bounds:', bounds);
      }

      applyBlockLimit();
    }

    // Redraw grid after loading
    setTimeout(() => drawGrid(), 50);
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load blocks';
    console.error('Failed to load block coordinates:', err);
  } finally {
    loading.value = false;
  }
}

// Apply block limit to displayed blocks (only for MODEL layers)
// Shows blocks within a cubic area around viewCenter
function applyBlockLimit() {
  if (props.sourceType === 'terrain') {
    // For terrain, blocks are already filtered by backend
    return;
  }

  if (blockLimit.value === 0) {
    blockCoordinates.value = allBlockCoordinates.value;
  } else {
    // Calculate cubic root to get radius for each axis
    const cubeSize = Math.ceil(Math.cbrt(blockLimit.value));
    const radius = Math.floor(cubeSize / 2);

    // Filter blocks within the cubic area around viewCenter
    blockCoordinates.value = allBlockCoordinates.value.filter(block => {
      const dx = Math.abs(block.x - viewCenter.value.x);
      const dy = Math.abs(block.y - viewCenter.value.y);
      const dz = Math.abs(block.z - viewCenter.value.z);
      return dx <= radius && dy <= radius && dz <= radius;
    });

    // If we have more blocks than the limit, take the closest ones
    if (blockCoordinates.value.length > blockLimit.value) {
      blockCoordinates.value = blockCoordinates.value
        .sort((a, b) => {
          const distA = Math.abs(a.x - viewCenter.value.x) + Math.abs(a.y - viewCenter.value.y) + Math.abs(a.z - viewCenter.value.z);
          const distB = Math.abs(b.x - viewCenter.value.x) + Math.abs(b.y - viewCenter.value.y) + Math.abs(b.z - viewCenter.value.z);
          return distA - distB;
        })
        .slice(0, blockLimit.value);
    }
  }

  // Redraw after applying limit
  setTimeout(() => drawGrid(), 50);
}

// Handle block limit change
async function handleBlockLimitChange() {
  if (props.sourceType === 'terrain') {
    // Reload with new radius
    await loadBlockCoordinates();
  } else {
    // Reapply filter
    applyBlockLimit();
  }
}

// Load details for a specific block
async function loadBlockDetails(x: number, y: number, z: number) {
  loadingBlockDetails.value = true;
  blockDetails.value = null;

  try {
    const apiUrl = apiService.getBaseUrl();
    let url: string;

    if (props.sourceType === 'terrain') {
      url = `${apiUrl}/control/worlds/${props.worldId}/layers/${props.layerId}/grid/terrain/block/${x}/${y}/${z}`;
    } else {
      url = `${apiUrl}/control/worlds/${props.worldId}/layers/${props.layerId}/grid/models/${props.modelId}/block/${x}/${y}/${z}`;
    }

    const response = await fetch(url, {
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
      },
    });

    if (response.status === 404) {
      blockDetails.value = null;
      return;
    }

    if (!response.ok) {
      throw new Error(`Failed to load block details: ${response.statusText}`);
    }

    blockDetails.value = await response.json();
  } catch (err) {
    console.error('Failed to load block details:', err);
    blockDetails.value = null;
  } finally {
    loadingBlockDetails.value = false;
  }
}

// Handle canvas click for block selection
function handleCanvasClick(event: MouseEvent) {
  const canvas = canvasRef.value;
  if (!canvas) return;

  const rect = canvas.getBoundingClientRect();

  // Account for canvas scaling (CSS size vs actual canvas size)
  const scaleX = canvas.width / rect.width;
  const scaleY = canvas.height / rect.height;

  const clickX = (event.clientX - rect.left) * scaleX;
  const clickY = (event.clientY - rect.top) * scaleY;

  // Find clicked block using reverse rendering order (front to back for hit detection)
  const sortedBlocks = [...blockCoordinates.value].sort((a, b) => {
    // 1. Top to bottom (higher Y first - closest to camera)
    if (a.y !== b.y) return b.y - a.y;
    // 2. Front to back (lower Z first - closer in isometric view)
    if (a.z !== b.z) return a.z - b.z;
    // 3. Right to left (higher X first)
    return b.x - a.x;
  });

  for (const block of sortedBlocks) {
    const { x, y, z } = block;

    // Check if click is inside the block's bounds (simple bounding box test)
    const corners = [
      worldToScreen(x, y, z),
      worldToScreen(x + 1, y, z),
      worldToScreen(x + 1, y, z + 1),
      worldToScreen(x, y, z + 1),
      worldToScreen(x, y + 1, z),
      worldToScreen(x + 1, y + 1, z),
      worldToScreen(x + 1, y + 1, z + 1),
      worldToScreen(x, y + 1, z + 1),
    ];

    const minX = Math.min(...corners.map(c => c.x));
    const maxX = Math.max(...corners.map(c => c.x));
    const minY = Math.min(...corners.map(c => c.y));
    const maxY = Math.max(...corners.map(c => c.y));

    if (clickX >= minX && clickX <= maxX && clickY >= minY && clickY <= maxY) {
      selectedBlock.value = { x, y, z };
      loadBlockDetails(x, y, z);
      drawGrid();
      return;
    }
  }
}

// Handle canvas hover (optional - for highlighting)
function handleCanvasHover(event: MouseEvent) {
  // Could implement hover highlighting here
  // Note: If implementing, use the same scaling logic as handleCanvasClick:
  // const canvas = canvasRef.value;
  // if (!canvas) return;
  // const rect = canvas.getBoundingClientRect();
  // const scaleX = canvas.width / rect.width;
  // const scaleY = canvas.height / rect.height;
  // const hoverX = (event.clientX - rect.left) * scaleX;
  // const hoverY = (event.clientY - rect.top) * scaleY;
}

// Handle pan view navigation (moves the visible area)
async function handlePanView(position: { x: number; y: number; z: number }) {
  console.log('[handlePanView] Moving view center from', viewCenter.value, 'to', position);
  viewCenter.value = position;

  if (props.sourceType === 'terrain') {
    // For terrain: reload blocks from backend with new center
    console.log('[handlePanView] Reloading terrain blocks with new center');
    await loadBlockCoordinates();
  } else {
    // For model: reapply client-side filter
    console.log('[handlePanView] Reapplying model filter');
    applyBlockLimit();
  }
}

// Handle close
function handleClose() {
  emit('close');
}

// Watch for canvas size changes
watch([canvasWidth, canvasHeight], () => {
  setTimeout(() => drawGrid(), 50);
});

// Watch for block coordinates changes
watch(blockCoordinates, () => {
  // Only draw if canvas is initialized
  if (canvasRef.value && canvasWidth.value > 0 && canvasHeight.value > 0) {
    drawGrid();
  }
});

// Lifecycle
onMounted(async () => {
  // Initialize canvas size FIRST
  await nextTick();
  updateCanvasSize();

  // Then load blocks
  await loadBlockCoordinates();

  // Add resize listener
  window.addEventListener('resize', updateCanvasSize);
});

// Cleanup
onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCanvasSize);
});
</script>
