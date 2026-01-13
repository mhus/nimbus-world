<template>
  <div class="space-y-4">
    <div class="form-control">
      <label class="label">
        <span class="label-text font-semibold">On Use Effect</span>
        <span class="label-text-alt text-xs">Scrawl script executed when item is used</span>
      </label>

      <!-- Quick Options -->
      <div class="flex gap-2 mb-2">
        <button
          v-if="!modelValue"
          class="btn btn-sm btn-primary"
          @click="createInlineScript"
        >
          Add Script
        </button>
        <button
          v-if="modelValue"
          class="btn btn-sm btn-error"
          @click="removeScript"
        >
          Remove Script
        </button>
        <button
          v-if="modelValue"
          class="btn btn-sm btn-secondary"
          @click="editScript"
        >
          Edit Script
        </button>
      </div>

      <!-- Display Current Script -->
      <div v-if="modelValue" class="bg-base-200 p-4 rounded-lg">
        <div class="text-sm">
          <div v-if="modelValue.scriptId" class="flex items-center gap-2">
            <span class="badge badge-primary">Reference</span>
            <code class="text-xs">{{ modelValue.scriptId }}</code>
          </div>
          <div v-if="modelValue.script" class="flex items-center gap-2">
            <span class="badge badge-secondary">Inline</span>
            <code class="text-xs">{{ modelValue.script.id }}</code>
          </div>
          <div v-if="modelValue.parameters" class="mt-2">
            <span class="text-xs opacity-70">Parameters:</span>
            <pre class="text-xs mt-1 opacity-70">{{ JSON.stringify(modelValue.parameters, null, 2) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- Script Editor Modal -->
    <dialog ref="editorModal" class="modal">
      <div class="modal-box w-11/12 max-w-7xl h-5/6">
        <h3 class="font-bold text-lg mb-4">Edit Scrawl Script</h3>

        <!-- Embedded Scrawl Editor -->
        <div class="h-full overflow-auto">
          <ScrawlAppEmbedded
            v-if="editingScript"
            :initial-script="editingScript"
            @save="handleScriptSave"
            @cancel="closeScriptEditor"
          />
        </div>

        <div class="modal-action">
          <button class="btn" @click="closeScriptEditor">Close</button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button>close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { ScriptActionDefinition, ScrawlScript } from '@nimbus/shared';
import ScrawlAppEmbedded from '../../scrawl/ScrawlAppEmbedded.vue';

const props = defineProps<{
  modelValue?: ScriptActionDefinition;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: ScriptActionDefinition | undefined];
}>();

const editorModal = ref<HTMLDialogElement | null>(null);
const editingScript = ref<ScrawlScript | null>(null);

function createInlineScript() {
  const newScript: ScrawlScript = {
    id: 'item_effect',
    description: 'Item use effect',
    root: {
      kind: 'Sequence',
      steps: [],
    },
  };

  const newAction: ScriptActionDefinition = {
    script: newScript,
  };

  emit('update:modelValue', newAction);
  editingScript.value = newScript;
  editorModal.value?.showModal();
}

function editScript() {
  if (!props.modelValue) return;

  if (props.modelValue.script) {
    editingScript.value = { ...props.modelValue.script };
  } else if (props.modelValue.scriptId) {
    // TODO: Load script from server
    editingScript.value = {
      id: props.modelValue.scriptId,
      root: {
        kind: 'Sequence',
        steps: [],
      },
    };
  }

  editorModal.value?.showModal();
}

function handleScriptSave(script: ScrawlScript) {
  const updatedAction: ScriptActionDefinition = {
    ...props.modelValue,
    script,
  };

  emit('update:modelValue', updatedAction);
  closeScriptEditor();
}

function closeScriptEditor() {
  editorModal.value?.close();
  editingScript.value = null;
}

function removeScript() {
  emit('update:modelValue', undefined);
}
</script>
