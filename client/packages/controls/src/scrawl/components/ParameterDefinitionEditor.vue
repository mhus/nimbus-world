<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold">Parameters</h3>
      <button class="btn btn-xs btn-primary" @click="addParameter">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        Add Parameter
      </button>
    </div>

    <!-- Parameter List -->
    <div v-if="parameters.length === 0" class="text-center py-4 opacity-50 text-xs">
      No parameters defined
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="(param, index) in parameters"
        :key="index"
        class="card bg-base-200 shadow-sm"
      >
        <div class="card-body p-4 space-y-2">
          <!-- Header with Delete -->
          <div class="flex items-center justify-between">
            <span class="text-xs font-semibold opacity-70">Parameter {{ index + 1 }}</span>
            <button
              class="btn btn-xs btn-error"
              @click="deleteParameter(index)"
            >
              ✕
            </button>
          </div>

          <!-- Name -->
          <div class="form-control">
            <label class="label py-0">
              <span class="label-text text-xs">Name</span>
            </label>
            <input
              v-model="param.name"
              type="text"
              class="input input-bordered input-xs"
              placeholder="parameterName"
              @input="emitUpdate"
            />
          </div>

          <!-- Type -->
          <div class="form-control">
            <label class="label py-0">
              <span class="label-text text-xs">Type</span>
            </label>
            <select
              v-model="param.type"
              class="select select-bordered select-xs"
              @change="emitUpdate"
            >
              <option value="number">Number</option>
              <option value="string">String</option>
              <option value="boolean">Boolean</option>
              <option value="color">Color</option>
              <option value="vector3">Vector3</option>
            </select>
          </div>

          <!-- Default Value -->
          <div class="form-control">
            <label class="label py-0">
              <span class="label-text text-xs">Default Value (optional)</span>
            </label>
            <input
              v-if="param.type === 'number'"
              v-model.number="param.default"
              type="number"
              class="input input-bordered input-xs"
              placeholder="0"
              @input="emitUpdate"
            />
            <input
              v-else-if="param.type === 'boolean'"
              type="checkbox"
              class="checkbox checkbox-xs"
              :checked="param.default || false"
              @change="param.default = ($event.target as HTMLInputElement).checked; emitUpdate()"
            />
            <input
              v-else-if="param.type === 'color'"
              v-model="param.default"
              type="color"
              class="input input-bordered input-xs h-8"
              @input="emitUpdate"
            />
            <input
              v-else
              v-model="param.default"
              type="text"
              class="input input-bordered input-xs"
              :placeholder="getPlaceholder(param.type)"
              @input="emitUpdate"
            />
          </div>

          <!-- Description -->
          <div class="form-control">
            <label class="label py-0">
              <span class="label-text text-xs">Description (optional)</span>
            </label>
            <textarea
              v-model="param.description"
              class="textarea textarea-bordered textarea-xs"
              rows="2"
              placeholder="Parameter description"
              @input="emitUpdate"
            ></textarea>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { ScrawlParameterDefinition } from '@nimbus/shared';

const props = defineProps<{
  modelValue?: ScrawlParameterDefinition[];
}>();

const emit = defineEmits<{
  'update:modelValue': [params: ScrawlParameterDefinition[]];
}>();

const parameters = ref<ScrawlParameterDefinition[]>(props.modelValue || []);

function addParameter() {
  parameters.value.push({
    name: '',
    type: 'string',
    default: undefined,
    description: '',
  });
  emitUpdate();
}

function deleteParameter(index: number) {
  parameters.value.splice(index, 1);
  emitUpdate();
}

function emitUpdate() {
  emit('update:modelValue', parameters.value);
}

function getPlaceholder(type: string): string {
  switch (type) {
    case 'vector3':
      return '{"x": 0, "y": 0, "z": 0}';
    case 'string':
      return 'default value';
    default:
      return '';
  }
}

watch(() => props.modelValue, (newVal) => {
  parameters.value = newVal || [];
}, { deep: true });
</script>
