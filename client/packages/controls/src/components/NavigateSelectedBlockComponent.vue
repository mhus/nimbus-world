<template>
  <div class="w-full flex flex-col items-center gap-3">
    <!-- SVG Axis Navigator -->
    <svg
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      class="bg-base-100 rounded-lg shadow-lg"
      :class="{ 'opacity-50': isDisabled }"
    >
      <!-- Optional Grid -->
      <g v-if="showGrid">
        <line
          v-for="i in 8"
          :key="`grid-v-${i}`"
          :x1="(i / 9) * size"
          y1="8"
          :x2="(i / 9) * size"
          :y2="size - 8"
          stroke="currentColor"
          stroke-width="1"
          class="text-base-300"
        />
        <line
          v-for="i in 8"
          :key="`grid-h-${i}`"
          x1="8"
          :y1="(i / 9) * size"
          :x2="size - 8"
          :y2="(i / 9) * size"
          stroke="currentColor"
          stroke-width="1"
          class="text-base-300"
        />
      </g>

      <!-- X-Axis (Red, Horizontal) -->
      <Axis
        :from="points.xNeg"
        :to="points.xPos"
        color="#ef4444"
        label="X"
      />

      <!-- Y-Axis (Green, Vertical) -->
      <Axis
        :from="points.yNeg"
        :to="points.yPos"
        color="#22c55e"
        label="Y"
      />

      <!-- Z-Axis (Blue, Diagonal) -->
      <Axis
        :from="points.zNeg"
        :to="points.zPos"
        color="#3b82f6"
        label="Z"
      />

      <!-- Origin Point -->
      <g>
        <circle :cx="cx" :cy="cy" r="6" fill="currentColor" class="text-base-content" />
        <title>Origin (0,0,0)</title>
      </g>

      <!-- Execute Action Button (Center) -->
      <g
        v-if="showExecuteButton && !isDisabled"
        class="cursor-pointer select-none hover:opacity-80 transition-opacity"
        @click="executeAction"
        role="button"
        aria-label="Execute action on selected block"
        tabindex="0"
      >
        <!-- Outer circle with shadow -->
        <circle
          :cx="cx"
          :cy="cy"
          r="32"
          fill="white"
          style="filter: drop-shadow(0 4px 6px rgba(0,0,0,0.2))"
        />
        <!-- Colored border -->
        <circle
          :cx="cx"
          :cy="cy"
          r="30"
          stroke-width="3"
          stroke="#10b981"
          fill="none"
        />
        <!-- Play icon (triangle pointing right) -->
        <path
          :d="`M ${cx - 8} ${cy - 10} L ${cx + 12} ${cy} L ${cx - 8} ${cy + 10} Z`"
          fill="#10b981"
          class="pointer-events-none"
        />
        <title>Execute action</title>
      </g>

      <!-- X-Axis Buttons -->
      <NodeButton
        :x="points.xNeg.x"
        :y="points.xNeg.y"
        label="-"
        title="Decrease X by step"
        color="#ef4444"
        :disabled="isDisabled"
        @click="bump('x', -1)"
      />
      <NodeButton
        :x="points.xPos.x"
        :y="points.xPos.y"
        label="+"
        title="Increase X by step"
        color="#ef4444"
        :disabled="isDisabled"
        @click="bump('x', 1)"
      />

      <!-- Y-Axis Buttons -->
      <NodeButton
        :x="points.yNeg.x"
        :y="points.yNeg.y"
        label="-"
        title="Decrease Y by step"
        color="#22c55e"
        :disabled="isDisabled"
        @click="bump('y', -1)"
      />
      <NodeButton
        :x="points.yPos.x"
        :y="points.yPos.y"
        label="+"
        title="Increase Y by step"
        color="#22c55e"
        :disabled="isDisabled"
        @click="bump('y', 1)"
      />

      <!-- Z-Axis Buttons -->
      <NodeButton
        :x="points.zNeg.x"
        :y="points.zNeg.y"
        label="-"
        title="Decrease Z by step"
        color="#3b82f6"
        :disabled="isDisabled"
        @click="bump('z', -1)"
      />
      <NodeButton
        :x="points.zPos.x"
        :y="points.zPos.y"
        label="+"
        title="Increase Z by step"
        color="#3b82f6"
        :disabled="isDisabled"
        @click="bump('z', 1)"
      />
    </svg>

    <!-- Position Display -->
    <div v-if="selectedBlock" class="flex items-center gap-2 text-xs">
      <span class="px-2 py-1 bg-red-50 text-red-600 rounded font-mono shadow-sm">
        x: {{ selectedBlock.x }}
      </span>
      <span class="px-2 py-1 bg-green-50 text-green-600 rounded font-mono shadow-sm">
        y: {{ selectedBlock.y }}
      </span>
      <span class="px-2 py-1 bg-blue-50 text-blue-600 rounded font-mono shadow-sm">
        z: {{ selectedBlock.z }}
      </span>
    </div>

    <!-- Disabled State Message -->
    <div v-else class="text-xs text-base-content/50">
      No block selected
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

// Props
interface Props {
  selectedBlock: { x: number; y: number; z: number } | null;
  step?: number;
  size?: number;
  showGrid?: boolean;
  showExecuteButton?: boolean; // Show execute action button in center
}

const props = withDefaults(defineProps<Props>(), {
  step: 1,
  size: 280,
  showGrid: true,
  showExecuteButton: false,
});

// Emits
const emit = defineEmits<{
  navigate: [position: { x: number; y: number; z: number }];
  execute: []; // Execute action on selected block
}>();

// Computed
const isDisabled = computed(() => props.selectedBlock === null);

const cx = computed(() => props.size / 2);
const cy = computed(() => props.size / 2);
const pad = 28;

// Calculate axis endpoint positions
const points = computed(() => {
  const { size } = props;
  const centerX = cx.value;
  const centerY = cy.value;

  // X-Axis: horizontal
  const xNeg = { x: pad, y: centerY };
  const xPos = { x: size - pad, y: centerY };

  // Y-Axis: vertical (Y+ upward)
  const yNeg = { x: centerX, y: size - pad }; // -Y downward
  const yPos = { x: centerX, y: pad }; // +Y upward

  // Z-Axis: diagonal (left-handed, Z+ forward = top-right)
  const diag = Math.min(centerX, centerY) - pad;
  const zNeg = { x: centerX - diag * 0.7, y: centerY + diag * 0.7 }; // -Z
  const zPos = { x: centerX + diag * 0.7, y: centerY - diag * 0.7 }; // +Z

  return { xNeg, xPos, yNeg, yPos, zNeg, zPos };
});

// Methods
function bump(axis: 'x' | 'y' | 'z', sign: 1 | -1) {
  if (!props.selectedBlock) return;

  const delta = sign * props.step;
  const next = {
    ...props.selectedBlock,
    [axis]: props.selectedBlock[axis] + delta,
  };

  emit('navigate', next);
}

function executeAction() {
  emit('execute');
}
</script>

<script lang="ts">
import { defineComponent, h } from 'vue';

// Axis Line Component
const Axis = defineComponent({
  name: 'Axis',
  props: {
    from: { type: Object as () => { x: number; y: number }, required: true },
    to: { type: Object as () => { x: number; y: number }, required: true },
    color: { type: String, required: true },
    label: { type: String, required: true },
  },
  setup(props) {
    return () => [
      // Axis line
      h('line', {
        x1: props.from.x,
        y1: props.from.y,
        x2: props.to.x,
        y2: props.to.y,
        stroke: props.color,
        'stroke-width': 4,
        'stroke-linecap': 'round',
      }),
      // Axis label
      h(
        'text',
        {
          x: (props.from.x + props.to.x) / 2,
          y: (props.from.y + props.to.y) / 2 - 8,
          'text-anchor': 'middle',
          fill: props.color,
          class: 'font-semibold select-none',
          style: { fontSize: '14px' },
        },
        props.label
      ),
    ];
  },
});

// Node Button Component (Circle with +/- label)
const NodeButton = defineComponent({
  name: 'NodeButton',
  props: {
    x: { type: Number, required: true },
    y: { type: Number, required: true },
    label: { type: String, required: true },
    title: { type: String, required: true },
    color: { type: String, required: true },
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
  setup(props, { emit }) {
    const r = 16;

    function handleClick() {
      if (!props.disabled) {
        emit('click');
      }
    }

    return () =>
      h(
        'g',
        {
          class: props.disabled
            ? 'cursor-not-allowed select-none opacity-40'
            : 'cursor-pointer select-none hover:opacity-80 transition-opacity',
          onClick: handleClick,
          role: 'button',
          'aria-label': props.title,
          tabindex: props.disabled ? -1 : 0,
        },
        [
          // Outer white circle with shadow
          h('circle', {
            cx: props.x,
            cy: props.y,
            r,
            fill: 'white',
            style: { filter: 'drop-shadow(0 2px 4px rgba(0,0,0,0.15))' },
          }),
          // Colored border
          h('circle', {
            cx: props.x,
            cy: props.y,
            r: r - 2,
            'stroke-width': 2,
            stroke: props.color,
            fill: 'none',
          }),
          // Label text (+/-)
          h(
            'text',
            {
              x: props.x,
              y: props.y + 5,
              'text-anchor': 'middle',
              fill: props.color,
              class: 'font-bold select-none pointer-events-none',
              style: { fontSize: '16px' },
            },
            props.label
          ),
          // Tooltip
          h('title', props.title),
        ]
      );
  },
});

export { Axis, NodeButton };
</script>
