<template>
  <div class="space-y-3">
    <!-- Type-specific Editor -->
    <component
      :is="getEditorComponent(modelValue.kind)"
      :model-value="modelValue"
      @update:model-value="emit('update:modelValue', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { defineAsyncComponent } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';
import WaitStepEditor from './editors/WaitStepEditor.vue';
import CmdStepEditor from './editors/CmdStepEditor.vue';
import PlayStepEditor from './editors/PlayStepEditor.vue';
import SequenceStepEditor from './editors/SequenceStepEditor.vue';
import ParallelStepEditor from './editors/ParallelStepEditor.vue';
import RepeatStepEditor from './editors/RepeatStepEditor.vue';
import WhileStepEditor from './editors/WhileStepEditor.vue';
import UntilStepEditor from './editors/UntilStepEditor.vue';
import ForEachStepEditor from './editors/ForEachStepEditor.vue';
import IfStepEditor from './editors/IfStepEditor.vue';
import CallStepEditor from './editors/CallStepEditor.vue';
import SetVarStepEditor from './editors/SetVarStepEditor.vue';
import EmitEventStepEditor from './editors/EmitEventStepEditor.vue';
import WaitEventStepEditor from './editors/WaitEventStepEditor.vue';
import LodSwitchStepEditor from './editors/LodSwitchStepEditor.vue';

const props = defineProps<{
  modelValue: ScrawlStep;
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

// Map step kinds to editor components
function getEditorComponent(kind: string) {
  switch (kind) {
    case 'Wait':
      return WaitStepEditor;
    case 'Cmd':
      return CmdStepEditor;
    case 'Play':
      return PlayStepEditor;
    case 'Sequence':
      return SequenceStepEditor;
    case 'Parallel':
      return ParallelStepEditor;
    case 'Repeat':
      return RepeatStepEditor;
    case 'While':
      return WhileStepEditor;
    case 'Until':
      return UntilStepEditor;
    case 'ForEach':
      return ForEachStepEditor;
    case 'If':
      return IfStepEditor;
    case 'Call':
      return CallStepEditor;
    case 'SetVar':
      return SetVarStepEditor;
    case 'EmitEvent':
      return EmitEventStepEditor;
    case 'WaitEvent':
      return WaitEventStepEditor;
    case 'LodSwitch':
      return LodSwitchStepEditor;
    default:
      // Fallback: JSON editor for unimplemented types
      return defineAsyncComponent(() => import('./editors/GenericStepEditor.vue'));
  }
}
</script>
