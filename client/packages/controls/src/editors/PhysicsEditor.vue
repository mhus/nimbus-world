<template>
  <div class="space-y-3 pt-2">
    <!-- Checkboxes Row -->
    <div class="grid grid-cols-2 gap-3">
      <div class="form-control">
        <TriStateCheckboxSimple v-model="localValue.solid">
          Solid (collision)
        </TriStateCheckboxSimple>
      </div>
      <div class="form-control">
        <TriStateCheckboxSimple v-model="localValue.interactive">
          Interactive
        </TriStateCheckboxSimple>
      </div>
      <div class="form-control">
        <TriStateCheckboxSimple v-model="localValue.collisionEvent">
          Collision Event
        </TriStateCheckboxSimple>
      </div>
      <div class="form-control">
        <TriStateCheckboxSimple v-model="localValue.autoClimbable">
          Auto Climbable
        </TriStateCheckboxSimple>
      </div>
      <div class="form-control">
        <label class="label cursor-pointer justify-start gap-2">
          <input
            v-model.number="autoJumpValue"
            type="number"
            step="0.1"
            min="0"
            class="input input-bordered input-sm"
            placeholder="0"
          />
          <span class="label-text text-xs">Auto Jump</span>
        </label>
      </div>
    </div>

    <!-- Numeric Fields Row -->
    <div class="grid grid-cols-3 gap-2">
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Resistance</span>
        </label>
        <input
          v-model.number="localValue.resistance"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Climbable</span>
        </label>
        <input
          v-model.number="localValue.climbable"
          type="number"
          step="0.1"
          min="0"
          class="input input-bordered input-sm"
          placeholder="0"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text text-xs">Auto Orientation Y</span>
        </label>
        <input
          v-model.number="localValue.autoOrientationY"
          type="number"
          step="0.1"
          class="input input-bordered input-sm"
          placeholder="0"
        />
      </div>
    </div>
    <label class="label">
      <span class="label-text-alt">Resistance: movement reduction | Climbable: climb speed (0.5=ladder) | Auto Orientation Y: radians (1.57=90°)</span>
    </label>

    <div class="form-control">
      <label class="label">
        <span class="label-text">Auto Move (velocity when on/in block)</span>
      </label>
      <div class="flex gap-2">
        <input
          v-model.number="autoMoveX"
          type="number"
          step="0.1"
          class="input input-bordered input-sm flex-1"
          placeholder="X"
        />
        <input
          v-model.number="autoMoveY"
          type="number"
          step="0.1"
          class="input input-bordered input-sm flex-1"
          placeholder="Y"
        />
        <input
          v-model.number="autoMoveZ"
          type="number"
          step="0.1"
          class="input input-bordered input-sm flex-1"
          placeholder="Z"
        />
      </div>
      <label class="label">
        <span class="label-text-alt">Blocks per second (e.g., 2 0 0 = conveyor belt)</span>
      </label>
    </div>

    <div class="form-control">
      <label class="label">
        <span class="label-text">Passable From (one-way/wall directions)</span>
      </label>
      <div class="flex flex-wrap gap-3 p-2 bg-base-200 rounded">
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromNorth"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">North</span>
        </label>
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromSouth"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">South</span>
        </label>
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromEast"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">East</span>
        </label>
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromWest"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">West</span>
        </label>
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromUp"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">Up</span>
        </label>
        <label class="label cursor-pointer gap-2">
          <input
            v-model="passableFromDown"
            type="checkbox"
            class="checkbox checkbox-xs"
          />
          <span class="label-text text-xs">Down</span>
        </label>
        <button
          @click="resetPassableFrom"
          class="btn btn-xs btn-ghost"
        >
          Reset
        </button>
      </div>
      <label class="label">
        <span class="label-text-alt">Solid: one-way block. Non-solid: wall at edges.</span>
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import type { PhysicsModifier } from '@nimbus/shared';
import { Direction, DirectionHelper } from '@nimbus/shared';
import CollapsibleSection from '@components/CollapsibleSection.vue';
import TriStateCheckboxSimple from '@components/TriStateCheckboxSimple.vue';

interface Props {
  modelValue?: PhysicsModifier;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: PhysicsModifier | undefined): void;
}>();

// Initialize localValue - keep undefined values as undefined
const localValue = ref<PhysicsModifier>(
  props.modelValue ? JSON.parse(JSON.stringify(props.modelValue)) : {}
);

// Computed properties for autoMove Vector3 components
const autoMoveX = computed({
  get: () => localValue.value.autoMove?.x ?? 0,
  set: (value: number) => {
    if (!localValue.value.autoMove) {
      localValue.value.autoMove = { x: 0, y: 0, z: 0 };
    }
    localValue.value.autoMove.x = value;
  }
});

const autoMoveY = computed({
  get: () => localValue.value.autoMove?.y ?? 0,
  set: (value: number) => {
    if (!localValue.value.autoMove) {
      localValue.value.autoMove = { x: 0, y: 0, z: 0 };
    }
    localValue.value.autoMove.y = value;
  }
});

const autoMoveZ = computed({
  get: () => localValue.value.autoMove?.z ?? 0,
  set: (value: number) => {
    if (!localValue.value.autoMove) {
      localValue.value.autoMove = { x: 0, y: 0, z: 0 };
    }
    localValue.value.autoMove.z = value;
  }
});

// Computed properties for passableFrom direction flags
const passableFromNorth = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.NORTH),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.NORTH)
      : DirectionHelper.removeDirection(current, Direction.NORTH);
  }
});

const passableFromSouth = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.SOUTH),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.SOUTH)
      : DirectionHelper.removeDirection(current, Direction.SOUTH);
  }
});

const passableFromEast = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.EAST),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.EAST)
      : DirectionHelper.removeDirection(current, Direction.EAST);
  }
});

const passableFromWest = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.WEST),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.WEST)
      : DirectionHelper.removeDirection(current, Direction.WEST);
  }
});

const passableFromUp = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.UP),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.UP)
      : DirectionHelper.removeDirection(current, Direction.UP);
  }
});

const passableFromDown = computed({
  get: () => DirectionHelper.hasDirection(localValue.value.passableFrom ?? 0, Direction.DOWN),
  set: (value: boolean) => {
    const current = localValue.value.passableFrom ?? 0;
    localValue.value.passableFrom = value
      ? DirectionHelper.addDirection(current, Direction.DOWN)
      : DirectionHelper.removeDirection(current, Direction.DOWN);
  }
});

// Reset passableFrom to undefined
const resetPassableFrom = () => {
  localValue.value.passableFrom = undefined;
};

// Computed property for autoJump as number
const autoJumpValue = computed({
  get: () => localValue.value.autoJump ?? 0,
  set: (value: number) => {
    if (value > 0) {
      localValue.value.autoJump = value;
    } else {
      localValue.value.autoJump = undefined;
    }
  }
});

// Watch for changes in localValue and emit
watch(localValue, (newValue) => {
  emit('update:modelValue', newValue);
}, { deep: true });
</script>
