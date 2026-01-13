<template>
  <div class="space-y-3 pt-2">
    <!-- No offsets for INVISIBLE -->
    <div v-if="shape === 0" class="text-sm text-base-content/60">
      No offsets for invisible blocks
    </div>

    <!-- CUBE, HASH, CROSS, FLIPBOX: 8 corners (24 values) -->
    <div v-else-if="isCubeType" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">8 corners × XYZ (supports float values){{ shape === 11 ? ' — FLIPBOX uses top 4 corners only' : '' }}</p>
      <div v-for="(corner, index) in cubeCorners" :key="index" class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">{{ corner }}:</span>
        <input
          v-model.number="offsets[index * 3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[index * 3 + 1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[index * 3 + 2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- CYLINDER: 4 points (special meaning) -->
    <div v-else-if="shape === 9" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">Cylinder offsets (supports float values)</p>

      <!-- Point 1: Radius offset top (XZ) -->
      <div class="grid grid-cols-3 gap-2 items-center">
        <span class="text-xs text-base-content/70">Radius Top:</span>
        <input
          v-model.number="offsets[0]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>

      <!-- Point 2: Radius offset bottom (XZ) -->
      <div class="grid grid-cols-3 gap-2 items-center">
        <span class="text-xs text-base-content/70">Radius Bottom:</span>
        <input
          v-model.number="offsets[3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[5]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>

      <!-- Point 3: Displacement top (XYZ) -->
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Disp. Top:</span>
        <input
          v-model.number="offsets[6]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[7]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[8]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>

      <!-- Point 4: Displacement bottom (XYZ) -->
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Disp. Bottom:</span>
        <input
          v-model.number="offsets[9]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[10]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[11]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- SPHERE: 2 points -->
    <div v-else-if="shape === 8" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">Sphere offsets (supports float values)</p>

      <!-- Point 1: Radius offset (XYZ) -->
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Radius Offset:</span>
        <input
          v-model.number="offsets[0]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>

      <!-- Point 2: Displacement (XYZ) -->
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Displacement:</span>
        <input
          v-model.number="offsets[3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[4]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[5]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- FLAT, GLASS_FLAT: 4 corners (12 values) -->
    <div v-else-if="shape === 7 || shape === 6" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">4 corners × XYZ (supports float values)</p>
      <div v-for="(corner, index) in flatCorners" :key="index" class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">{{ corner }}:</span>
        <input
          v-model.number="offsets[index * 3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[index * 3 + 1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[index * 3 + 2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- WATER: 8 corners + water properties -->
    <div v-else-if="shape === 22" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">Water surface: 8 corners × XYZ (supports float values)</p>
      <div v-for="(corner, index) in cubeCorners" :key="index" class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">{{ corner }}:</span>
        <input
          v-model.number="offsets[index * 3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[index * 3 + 1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[index * 3 + 2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>

      <!-- Water color and transparency -->
      <div class="divider text-xs text-base-content/60">Water Properties</div>

      <!-- Water color (RGB) -->
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Color (RGB):</span>
        <input
          v-model.number="offsets[24]"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="R (0-1)"
        />
        <input
          v-model.number="offsets[25]"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="G (0-1)"
        />
        <input
          v-model.number="offsets[26]"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="B (0-1)"
        />
      </div>

      <!-- Water transparency -->
      <div class="grid grid-cols-2 gap-2 items-center">
        <span class="text-xs text-base-content/70">Transparency (Alpha):</span>
        <input
          v-model.number="offsets[27]"
          type="number"
          step="0.1"
          min="0"
          max="1"
          class="input input-bordered input-sm"
          placeholder="0=transparent, 1=opaque"
        />
      </div>

      <!-- Water presets -->
      <div class="flex flex-wrap gap-2 mt-2">
        <button class="btn btn-xs btn-outline" @click="setWaterPreset('clear')">Clear Water</button>
        <button class="btn btn-xs btn-outline" @click="setWaterPreset('ocean')">Ocean Blue</button>
        <button class="btn btn-xs btn-outline" @click="setWaterPreset('swamp')">Swamp Green</button>
        <button class="btn btn-xs btn-outline" @click="setWaterPreset('murky')">Murky Brown</button>
      </div>
    </div>

    <!-- THIN_INSTANCES: Single offset (XYZ) -->
    <div v-else-if="shape === 25" class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">Position offset (supports float values)</p>
      <div class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Offset:</span>
        <input
          v-model.number="offsets[0]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- Unknown shapes: Generic numbered list (8 points × XYZ = 24 values) -->
    <div v-else class="space-y-3">
      <p class="text-sm text-base-content/70 mb-2">Generic offsets: 8 points × XYZ (supports float values)</p>
      <div v-for="pointIndex in 8" :key="pointIndex - 1" class="grid grid-cols-4 gap-2 items-center">
        <span class="text-xs text-base-content/70">Point {{ pointIndex - 1 }}:</span>
        <input
          v-model.number="offsets[(pointIndex - 1) * 3]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="X"
        />
        <input
          v-model.number="offsets[(pointIndex - 1) * 3 + 1]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Y"
        />
        <input
          v-model.number="offsets[(pointIndex - 1) * 3 + 2]"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="Z"
        />
      </div>
    </div>

    <!-- Reset Button -->
    <button
      v-if="hasOffsets"
      class="btn btn-ghost btn-sm btn-outline"
      @click="resetOffsets"
    >
      Reset Offsets
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';

interface Props {
  modelValue?: number[];
  shape?: number;
}

const props = withDefaults(defineProps<Props>(), {
  shape: 1, // Default: CUBE
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: number[] | undefined): void;
}>();

// Local copy of offsets (normalize on load)
const offsets = ref<number[]>(
  props.modelValue
    ? props.modelValue.map(v => (v === null || v === undefined || isNaN(v)) ? 0 : v)
    : []
);

// Watch offsets and emit changes
watch(offsets, (newValue) => {
  // Convert null/undefined to 0
  const normalized = normalizeOffsets(newValue);
  // Clean up trailing zeros
  const trimmed = trimTrailingZeros(normalized);
  emit('update:modelValue', trimmed.length > 0 ? trimmed : undefined);
}, { deep: true });

// Helper to normalize offsets: convert null/undefined to 0
const normalizeOffsets = (arr: number[]): number[] => {
  return arr.map(v => (v === null || v === undefined || isNaN(v)) ? 0 : v);
};

// Helper to trim trailing zeros
const trimTrailingZeros = (arr: number[]): number[] => {
  let lastNonZero = -1;
  for (let i = arr.length - 1; i >= 0; i--) {
    if (arr[i] !== 0) {
      lastNonZero = i;
      break;
    }
  }
  return lastNonZero >= 0 ? arr.slice(0, lastNonZero + 1) : [];
};

// Check if cube-type shape (CUBE=1, CROSS=2, HASH=3, FLIPBOX=11)
const isCubeType = computed(() => {
  return props.shape === 1 || props.shape === 2 || props.shape === 3 || props.shape === 11;
});

// Cube corner labels
const cubeCorners = [
  'bottom front left (SW)',
  'bottom front right (SE)',
  'bottom back left (NW)',
  'bottom back right (NE)',
  'top front left (SW)',
  'top front right (SE)',
  'top back left (NW)',
  'top back right (NE)',
];

// Flat corner labels
const flatCorners = [
  'front left (SW)',
  'front right (SE)',
  'back left (NW)',
  'back right (NE)',
];

const hasOffsets = computed(() => {
  return offsets.value.length > 0 && offsets.value.some(v => v !== 0);
});

const resetOffsets = () => {
  offsets.value = [];
  emit('update:modelValue', undefined);
};

// Set water preset colors
const setWaterPreset = (preset: string) => {
  // Ensure offsets array has enough space
  while (offsets.value.length < 28) {
    offsets.value.push(0);
  }

  switch (preset) {
    case 'clear':
      // Clear water: light blue, semi-transparent
      offsets.value[24] = 0.7;  // R
      offsets.value[25] = 0.85; // G
      offsets.value[26] = 1.0;  // B
      offsets.value[27] = 0.3;  // Alpha
      break;
    case 'ocean':
      // Ocean blue: deep blue, moderate transparency
      offsets.value[24] = 0.1;  // R
      offsets.value[25] = 0.3;  // G
      offsets.value[26] = 0.8;  // B
      offsets.value[27] = 0.5;  // Alpha
      break;
    case 'swamp':
      // Swamp green: murky green, less transparent
      offsets.value[24] = 0.2;  // R
      offsets.value[25] = 0.5;  // G
      offsets.value[26] = 0.2;  // B
      offsets.value[27] = 0.6;  // Alpha
      break;
    case 'murky':
      // Murky brown: brownish, mostly opaque
      offsets.value[24] = 0.4;  // R
      offsets.value[25] = 0.3;  // G
      offsets.value[26] = 0.2;  // B
      offsets.value[27] = 0.7;  // Alpha
      break;
  }
};

// Watch shape changes to reset offsets if needed
watch(() => props.shape, (newShape, oldShape) => {
  if (newShape !== oldShape) {
    // Shape changed - optionally reset offsets
    // For now, keep them but user can manually reset
  }
});
</script>
