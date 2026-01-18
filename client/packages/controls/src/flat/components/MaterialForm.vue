<template>
  <div class="space-y-4">
    <div class="flex justify-between items-center">
      <h3 class="text-lg font-semibold">
        {{ material ? `Edit Material ${materialId}` : `Add Material ${materialId}` }}
        <span v-if="materialLabel" class="ml-2 text-sm font-normal text-gray-500">({{ materialLabel }})</span>
      </h3>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <!-- Block Definition -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-medium">
            Block Definition
            <span class="text-error">*</span>
          </span>
        </label>
        <input
          v-model="formData.blockDef"
          type="text"
          class="input input-bordered w-full font-mono"
          :class="{ 'input-error': blockDefError }"
          placeholder="n:stone@s:default"
          required
        />
        <label class="label">
          <span class="label-text-alt">Format: n:blockid@s:state or w:blockid@s:state</span>
          <span v-if="blockDefError" class="label-text-alt text-error">{{ blockDefError }}</span>
        </label>
      </div>

      <!-- Next Block Definition -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-medium">Next Block Definition</span>
        </label>
        <input
          v-model="formData.nextBlockDef"
          type="text"
          class="input input-bordered w-full font-mono"
          :class="{ 'input-error': nextBlockDefError }"
          placeholder="n:dirt@s:default (optional)"
        />
        <label class="label">
          <span class="label-text-alt">Block used below level (optional)</span>
          <span v-if="nextBlockDefError" class="label-text-alt text-error">{{ nextBlockDefError }}</span>
        </label>
      </div>

      <!-- Checkboxes -->
      <div class="grid grid-cols-2 gap-4">
        <div class="form-control">
          <label class="label cursor-pointer justify-start gap-2">
            <input
              v-model="formData.hasOcean"
              type="checkbox"
              class="checkbox checkbox-primary"
            />
            <span class="label-text font-medium">Has Ocean</span>
          </label>
          <p class="text-xs text-gray-500 ml-6">Material supports ocean blocks</p>
        </div>

        <div class="form-control">
          <label class="label cursor-pointer justify-start gap-2">
            <input
              v-model="formData.isBlockMapDelta"
              type="checkbox"
              class="checkbox checkbox-primary"
            />
            <span class="label-text font-medium">Block Map Delta</span>
          </label>
          <p class="text-xs text-gray-500 ml-6">Use delta values for block heights</p>
        </div>
      </div>

      <!-- Block At Levels Editor -->
      <div class="form-control">
        <BlockAtLevelsEditor v-model="formData.blockAtLevels" />
      </div>

      <!-- Offset Definitions -->
      <div class="space-y-4 border-t pt-4">
        <h4 class="text-md font-medium">Offset Definitions</h4>
        <p class="text-xs text-gray-500">
          Control corner smoothing when blocks are higher or lower than neighbors
        </p>

        <!-- Higher Offsets -->
        <div class="form-control">
          <label class="label">
            <span class="label-text font-medium">Higher Offsets</span>
          </label>
          <p class="text-xs text-gray-500 mb-2">Applied when neighbor blocks are higher</p>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">
                <span class="label-text-alt">One Level (+1)</span>
              </label>
              <input
                v-model.number="formData.higherOffsets.one"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Two Levels (+2)</span>
              </label>
              <input
                v-model.number="formData.higherOffsets.two"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">One Edge (+1 corner)</span>
              </label>
              <input
                v-model.number="formData.higherOffsets.oneEdge"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Two Edge (+2 corner)</span>
              </label>
              <input
                v-model.number="formData.higherOffsets.twoEdge"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
          </div>
        </div>

        <!-- Lower Offsets -->
        <div class="form-control">
          <label class="label">
            <span class="label-text font-medium">Lower Offsets</span>
          </label>
          <p class="text-xs text-gray-500 mb-2">Applied when neighbor blocks are lower</p>
          <div class="grid grid-cols-2 gap-4">
            <div>
              <label class="label">
                <span class="label-text-alt">One Level (-1)</span>
              </label>
              <input
                v-model.number="formData.lowerOffsets.one"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Two Levels (-2)</span>
              </label>
              <input
                v-model.number="formData.lowerOffsets.two"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">One Edge (-1 corner)</span>
              </label>
              <input
                v-model.number="formData.lowerOffsets.oneEdge"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
            <div>
              <label class="label">
                <span class="label-text-alt">Two Edge (-2 corner)</span>
              </label>
              <input
                v-model.number="formData.lowerOffsets.twoEdge"
                type="number"
                step="0.1"
                class="input input-sm input-bordered w-full font-mono"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="flex gap-2 justify-end pt-4 border-t">
        <button
          type="button"
          class="btn btn-ghost"
          @click="handleCancel"
        >
          Cancel
        </button>
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="!isValid"
        >
          {{ material ? 'Update' : 'Create' }} Material
        </button>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { MaterialDefinition, UpdateMaterialRequest } from '../../services/FlatService';
import BlockAtLevelsEditor from './BlockAtLevelsEditor.vue';

// Material ID labels (from FlatMaterialService.java)
const MATERIAL_LABELS: Record<number, string> = {
  1: 'GRASS',
  2: 'DIRT',
  3: 'STONE',
  4: 'SAND',
  5: 'WATER',
  6: 'BEDROCK',
  7: 'SNOW',
  8: 'INVISIBLE',
  9: 'INVISIBLE_SOLID',
};

interface Props {
  materialId: number;
  material?: MaterialDefinition | null;
}

interface Emits {
  (e: 'save', data: UpdateMaterialRequest): void;
  (e: 'cancel'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Material label
const materialLabel = computed(() => MATERIAL_LABELS[props.materialId] || '');

// Form data
const formData = ref<UpdateMaterialRequest>({
  blockDef: '',
  nextBlockDef: null,
  hasOcean: false,
  isBlockMapDelta: true,
  blockAtLevels: {},
  higherOffsets: { one: 0.2, two: 0.8, oneEdge: 0.4, twoEdge: 1.0 },
  lowerOffsets: { one: -0.1, two: -0.2, oneEdge: -0.3, twoEdge: -0.4 },
});

// Initialize form data from material prop
const initializeForm = () => {
  if (props.material) {
    formData.value = {
      blockDef: props.material.blockDef,
      nextBlockDef: props.material.nextBlockDef,
      hasOcean: props.material.hasOcean,
      isBlockMapDelta: props.material.isBlockMapDelta,
      blockAtLevels: { ...props.material.blockAtLevels },
      higherOffsets: props.material.higherOffsets
        ? { ...props.material.higherOffsets }
        : { one: 0.2, two: 0.8, oneEdge: 0.4, twoEdge: 1.0 },
      lowerOffsets: props.material.lowerOffsets
        ? { ...props.material.lowerOffsets }
        : { one: -0.1, two: -0.2, oneEdge: -0.3, twoEdge: -0.4 },
    };
  } else {
    formData.value = {
      blockDef: '',
      nextBlockDef: null,
      hasOcean: false,
      isBlockMapDelta: true,
      blockAtLevels: {},
      higherOffsets: { one: 0.2, two: 0.8, oneEdge: 0.4, twoEdge: 1.0 },
      lowerOffsets: { one: -0.1, two: -0.2, oneEdge: -0.3, twoEdge: -0.4 },
    };
  }
};

// Initialize on mount and when material changes
watch(() => props.material, initializeForm, { immediate: true });

// Validation
const blockDefPattern = /^[nw]:[a-zA-Z0-9_]+(@s:[a-zA-Z0-9_]+)?$/;

const blockDefError = computed(() => {
  if (!formData.value.blockDef) return 'Required';
  if (!blockDefPattern.test(formData.value.blockDef)) {
    return 'Invalid format';
  }
  return '';
});

const nextBlockDefError = computed(() => {
  if (!formData.value.nextBlockDef) return '';
  if (!blockDefPattern.test(formData.value.nextBlockDef)) {
    return 'Invalid format';
  }
  return '';
});

const isValid = computed(() => {
  return formData.value.blockDef &&
         !blockDefError.value &&
         !nextBlockDefError.value;
});

// Handle submit
const handleSubmit = () => {
  if (!isValid.value) return;

  // Clean up nextBlockDef - empty string becomes null
  const submitData: UpdateMaterialRequest = {
    ...formData.value,
    nextBlockDef: formData.value.nextBlockDef || null,
  };

  emit('save', submitData);
};

// Handle cancel
const handleCancel = () => {
  emit('cancel');
};
</script>
