<template>
  <div class="flex flex-col h-full">
    <!-- Script Info -->
    <div class="mb-4 p-4 bg-base-200 rounded-lg space-y-3">
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Script ID</span>
        </label>
        <input
          v-model="localScript.id"
          type="text"
          class="input input-bordered input-sm"
          placeholder="script_id"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Description</span>
        </label>
        <input
          v-model="localScript.description"
          type="text"
          class="input input-bordered input-sm"
          placeholder="Script description"
        />
      </div>

      <!-- Parameters Section -->
      <div class="collapse collapse-arrow bg-base-100">
        <input type="checkbox" />
        <div class="collapse-title text-sm font-semibold px-0">
          Parameters
          <span v-if="localScript.parameters?.length" class="badge badge-sm badge-primary ml-2">
            {{ localScript.parameters.length }}
          </span>
        </div>
        <div class="collapse-content px-0">
          <ParameterDefinitionEditor
            v-model="localScript.parameters"
          />
        </div>
      </div>
    </div>

    <!-- Editor Tabs -->
    <div class="tabs tabs-boxed mb-4">
      <button
        class="tab"
        :class="{ 'tab-active': currentTab === 'visual' }"
        @click="currentTab = 'visual'"
      >
        Visual Editor
      </button>
      <button
        class="tab"
        :class="{ 'tab-active': currentTab === 'source' }"
        @click="currentTab = 'source'"
      >
        JSON Source
      </button>
    </div>

    <!-- Visual Editor -->
    <div v-if="currentTab === 'visual'" class="flex-1 overflow-auto p-4 bg-base-200 rounded-lg">
      <div class="text-sm font-semibold mb-3">Root Step</div>

      <!-- Root Step Editor -->
      <div v-if="localScript.root">
        <StepEditor
          v-model="localScript.root"
        />
      </div>

      <!-- No Root Step -->
      <div v-else class="space-y-4">
        <div class="text-center opacity-50 py-4">
          No root step defined. Add a step to get started.
        </div>
        <button class="btn btn-primary btn-sm w-full" @click="createRootStep">
          Add Root Step
        </button>
      </div>
    </div>

    <!-- JSON Source Editor -->
    <div v-if="currentTab === 'source'" class="flex-1 overflow-auto">
      <textarea
        v-model="jsonSource"
        class="textarea textarea-bordered w-full h-full font-mono text-xs"
        @blur="updateFromJson"
      ></textarea>
    </div>

    <!-- Actions -->
    <div class="flex gap-2 mt-4">
      <button class="btn btn-primary btn-sm" @click="save">
        Save
      </button>
      <button class="btn btn-ghost btn-sm" @click="cancel">
        Cancel
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { ScrawlScript } from '@nimbus/shared';
import StepEditor from './components/StepEditor.vue';
import ParameterDefinitionEditor from './components/ParameterDefinitionEditor.vue';

const props = defineProps<{
  initialScript: ScrawlScript;
}>();

const emit = defineEmits<{
  save: [script: ScrawlScript];
  cancel: [];
}>();

const localScript = ref<ScrawlScript>({ ...props.initialScript });
const currentTab = ref<'visual' | 'source'>('visual');

const jsonSource = computed({
  get: () => JSON.stringify(localScript.value, null, 2),
  set: (value: string) => {
    try {
      localScript.value = JSON.parse(value);
    } catch (e) {
      console.error('Invalid JSON', e);
    }
  },
});

function updateFromJson() {
  // Validation happens in computed setter
}

function save() {
  emit('save', localScript.value);
}

function cancel() {
  emit('cancel');
}

function createRootStep() {
  localScript.value.root = {
    kind: 'Sequence',
    steps: [],
  };
}

watch(() => props.initialScript, (newScript) => {
  localScript.value = { ...newScript };
}, { deep: true });
</script>
