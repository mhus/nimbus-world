<template>
  <div class="border border-base-300 rounded-lg p-3 bg-base-100">
    <!-- Step Header -->
    <div class="flex items-center gap-2 mb-2">
      <span class="badge badge-primary badge-sm">{{ step.kind }}</span>
      <span class="text-sm font-semibold">{{ getStepTitle(step) }}</span>
    </div>

    <!-- Step Details -->
    <div class="text-xs opacity-70 ml-6">
      <div v-if="step.kind === 'Play'">
        Effect: <code>{{ step.effectId }}</code>
        <div v-if="step.source">Source: <code>{{ step.source }}</code></div>
        <div v-if="step.target">Target: <code>{{ step.target }}</code></div>
      </div>

      <div v-else-if="step.kind === 'Wait'">
        Duration: {{ step.seconds }}s
      </div>

      <div v-else-if="step.kind === 'Cmd'">
        Command: <code>{{ step.cmd }}</code>
        <div v-if="step.parameters">
          Parameters: <code>{{ JSON.stringify(step.parameters) }}</code>
        </div>
      </div>

      <div v-else-if="step.kind === 'Sequence' || step.kind === 'Parallel'">
        {{ step.steps.length }} step(s)
        <div class="ml-4 mt-2 space-y-2">
          <StepViewer
            v-for="(childStep, index) in step.steps"
            :key="index"
            :step="childStep"
          />
        </div>
      </div>

      <div v-else-if="step.kind === 'Repeat'">
        <div v-if="step.times">Times: {{ step.times }}</div>
        <div v-if="step.untilEvent">Until event: <code>{{ step.untilEvent }}</code></div>
        <div class="ml-4 mt-2">
          <StepViewer :step="step.step" />
        </div>
      </div>

      <div v-else-if="step.kind === 'ForEach'">
        Collection: <code>{{ step.collection }}</code>, Item: <code>{{ step.itemVar }}</code>
        <div class="ml-4 mt-2">
          <StepViewer :step="step.step" />
        </div>
      </div>

      <div v-else-if="step.kind === 'If'">
        Condition: <code>{{ step.cond.kind }}</code>
        <div class="ml-4 mt-2 space-y-2">
          <div>
            <span class="badge badge-success badge-xs">Then</span>
            <StepViewer :step="step.then" />
          </div>
          <div v-if="step.else">
            <span class="badge badge-error badge-xs">Else</span>
            <StepViewer :step="step.else" />
          </div>
        </div>
      </div>

      <div v-else-if="step.kind === 'Call'">
        Script: <code>{{ step.scriptId }}</code>
      </div>

      <div v-else-if="step.kind === 'SetVar'">
        <code>{{ step.name }}</code> = <code>{{ JSON.stringify(step.value) }}</code>
      </div>

      <div v-else-if="step.kind === 'EmitEvent'">
        Event: <code>{{ step.name }}</code>
      </div>

      <div v-else-if="step.kind === 'WaitEvent'">
        Event: <code>{{ step.name }}</code>
        <span v-if="step.timeout">(timeout: {{ step.timeout }}s)</span>
      </div>

      <div v-else-if="step.kind === 'LodSwitch'">
        LOD levels: {{ Object.keys(step.levels).join(', ') }}
      </div>

      <div v-else-if="step.kind === 'While'">
        Task ID: <code>{{ step.taskId }}</code>
        <span v-if="step.timeout">(timeout: {{ step.timeout }}s)</span>
        <div class="ml-4 mt-2">
          <StepViewer :step="step.step" />
        </div>
      </div>

      <div v-else-if="step.kind === 'Until'">
        Event: <code>{{ step.event }}</code>
        <span v-if="step.timeout">(timeout: {{ step.timeout }}s)</span>
        <div class="ml-4 mt-2">
          <StepViewer :step="step.step" />
        </div>
      </div>

      <div v-else>
        <pre class="text-xs">{{ JSON.stringify(step, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlStep } from '@nimbus/shared';

defineProps<{
  step: ScrawlStep;
}>();

function getStepTitle(step: ScrawlStep): string {
  switch (step.kind) {
    case 'Play':
      return `Play Effect: ${step.effectId}`;
    case 'Wait':
      return `Wait ${step.seconds}s`;
    case 'Cmd':
      return `Command: ${step.cmd}`;
    case 'Sequence':
      return 'Sequence';
    case 'Parallel':
      return 'Parallel';
    case 'Repeat':
      return 'Repeat';
    case 'While':
      return `While Task: ${step.taskId}`;
    case 'Until':
      return `Until Event: ${step.event}`;
    case 'ForEach':
      return 'For Each';
    case 'If':
      return 'If Condition';
    case 'Call':
      return `Call: ${step.scriptId}`;
    case 'SetVar':
      return `Set Variable: ${step.name}`;
    case 'EmitEvent':
      return `Emit: ${step.name}`;
    case 'WaitEvent':
      return `Wait Event: ${step.name}`;
    case 'LodSwitch':
      return 'LOD Switch';
    default:
      return step.kind;
  }
}
</script>
