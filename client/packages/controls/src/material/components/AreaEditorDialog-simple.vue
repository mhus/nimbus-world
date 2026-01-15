<template>
  <Teleport to="body">
  <div v-if="isOpen" class="fixed inset-0 z-[100] overflow-y-auto">
    <!-- Backdrop -->
    <div class="fixed inset-0 bg-black bg-opacity-40" @click="handleClose"></div>

    <!-- Dialog -->
    <div class="flex min-h-full items-center justify-center p-4" @click.self="handleClose">
      <div class="relative w-full max-w-2xl transform rounded-2xl bg-base-100 p-6 text-left shadow-xl" @click.stop>
        <h3 class="text-xl font-bold mb-4">
          {{ isEditMode ? 'Edit Area' : 'Create Area' }}
        </h3>

        <!-- Area Coordinates -->
        <div class="divider">Area Coordinates</div>
        <div class="grid grid-cols-4 gap-4 mb-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Position X</span>
            </label>
            <input
              ref="firstInputRef"
              v-model.number="localArea.x"
              type="number"
              class="input input-bordered input-sm"
              placeholder="0"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Position Z</span>
            </label>
            <input
              v-model.number="localArea.z"
              type="number"
              class="input input-bordered input-sm"
              placeholder="0"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size X</span>
            </label>
            <input
              v-model.number="localArea.sizeX"
              type="number"
              class="input input-bordered input-sm"
              placeholder="16"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Size Z</span>
            </label>
            <input
              v-model.number="localArea.sizeZ"
              type="number"
              class="input input-bordered input-sm"
              placeholder="16"
            />
          </div>
        </div>

        <!-- Parameters -->
        <div class="divider">Parameters</div>
        <div class="space-y-2 mb-4">
          <div v-for="(value, key) in localArea.parameters" :key="key" class="flex gap-2">
            <input
              :value="key"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Key"
              readonly
            />
            <input
              v-model="localArea.parameters[key]"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="Value"
            />
            <button
              type="button"
              class="btn btn-sm btn-ghost btn-square"
              @click="removeParameter(key)"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- Add New Parameter -->
          <div class="flex gap-2">
            <input
              v-model="newParamKey"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="New parameter key"
              @keyup.enter="addParameter"
            />
            <input
              v-model="newParamValue"
              type="text"
              class="input input-bordered input-sm flex-1"
              placeholder="New parameter value"
              @keyup.enter="addParameter"
            />
            <button
              type="button"
              class="btn btn-sm btn-primary"
              @click="addParameter"
              :disabled="!newParamKey || !newParamValue"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Actions -->
        <div class="mt-6 flex justify-end gap-2">
          <button
            type="button"
            class="btn"
            @click="handleClose"
          >
            Cancel
          </button>
          <button
            type="button"
            class="btn btn-primary"
            @click="handleSave"
            :disabled="!isValid"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';

export interface AreaData {
  x: number;
  z: number;
  sizeX: number;
  sizeZ: number;
  parameters: Record<string, string>;
}

const props = defineProps<{
  isOpen: boolean;
  area: AreaData | null;
}>();

const emit = defineEmits<{
  'update:isOpen': [value: boolean];
  save: [area: AreaData];
}>();

const isEditMode = computed(() => props.area !== null);

const firstInputRef = ref<HTMLInputElement | null>(null);

const localArea = ref<AreaData>({
  x: 0,
  z: 0,
  sizeX: 16,
  sizeZ: 16,
  parameters: {}
});

const newParamKey = ref('');
const newParamValue = ref('');

watch(() => props.isOpen, async (isOpen) => {
  if (isOpen) {
    if (props.area) {
      localArea.value = JSON.parse(JSON.stringify(props.area));
    } else {
      localArea.value = {
        x: 0,
        z: 0,
        sizeX: 16,
        sizeZ: 16,
        parameters: {}
      };
    }
    newParamKey.value = '';
    newParamValue.value = '';

    // Focus with delay to ensure dialog is fully rendered
    await nextTick();
    setTimeout(() => {
      if (firstInputRef.value) {
        firstInputRef.value.focus();
        firstInputRef.value.select();
      }
    }, 50);
  }
}, { immediate: true });

const isValid = computed(() => {
  return localArea.value.sizeX > 0 && localArea.value.sizeZ > 0;
});

const addParameter = () => {
  if (!newParamKey.value || !newParamValue.value) return;
  localArea.value.parameters[newParamKey.value] = newParamValue.value;
  newParamKey.value = '';
  newParamValue.value = '';
};

const removeParameter = (key: string) => {
  delete localArea.value.parameters[key];
};

const handleClose = () => {
  emit('update:isOpen', false);
};

const handleSave = () => {
  if (!isValid.value) return;
  emit('save', localArea.value);
  emit('update:isOpen', false);
};
</script>
