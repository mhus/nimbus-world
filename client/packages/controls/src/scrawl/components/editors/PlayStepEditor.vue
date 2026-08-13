<template>
  <div class="space-y-2">
    <div class="text-sm font-semibold opacity-70">Play Effect Step</div>

    <!-- Effect ID -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Effect ID</span>
      </label>
      <input
        :value="modelValue.effectId"
        type="text"
        class="input input-bordered input-sm"
        placeholder="log"
        @input="updateEffectId($event)"
      />
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Available: log, command
        </span>
      </label>
    </div>

    <!-- Source -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Source (optional)</span>
      </label>
      <input
        :value="modelValue.ctx?.source || ''"
        type="text"
        class="input input-bordered input-sm"
        placeholder="$actor"
        @input="updateSource($event)"
      />
    </div>

    <!-- Target -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Target (optional)</span>
      </label>
      <input
        :value="modelValue.ctx?.target || ''"
        type="text"
        class="input input-bordered input-sm"
        placeholder="$patient"
        @input="updateTarget($event)"
      />
    </div>

    <!-- Receive Player Direction -->
    <div class="form-control">
      <label class="label cursor-pointer py-1">
        <span class="label-text text-xs">Receive Player Direction</span>
        <input
          :checked="modelValue.receivePlayerDirection || false"
          type="checkbox"
          class="checkbox checkbox-sm"
          @change="updateReceivePlayerDirection($event)"
        />
      </label>
      <label class="label py-0">
        <span class="label-text-alt text-xs opacity-60">
          Effect receives continuous target updates (for beam:follow, etc.)
        </span>
      </label>
    </div>

    <!-- Context -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs">Context (JSON)</span>
      </label>
      <textarea
        :value="ctxJson"
        class="textarea textarea-bordered textarea-sm font-mono text-xs"
        rows="3"
        placeholder='{"message": "Hello"}'
        @input="updateCtx($event)"
      ></textarea>
      <label v-if="ctxError" class="label py-0">
        <span class="label-text-alt text-xs text-error">{{ ctxError }}</span>
      </label>
    </div>

    <!-- Effect Presets -->
    <PresetSelector
      title="Effect Presets"
      :presets="effectPresets"
      :loading="presetsLoading"
      @select="applyEffectPreset"
    />

    <!-- Preview -->
    <div class="text-xs opacity-50">
      <code>{{ modelValue.effectId }}({{ ctxJson }})</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import type { ScrawlStep } from '@nimbus/shared';
import PresetSelector from '../PresetSelector.vue';
import { presetService, type EffectPreset } from '../../services/presetService';

const props = defineProps<{
  modelValue: ScrawlStep & { kind: 'Play' };
}>();

const emit = defineEmits<{
  'update:modelValue': [step: ScrawlStep];
}>();

const ctxError = ref<string | null>(null);
const effectPresets = ref<EffectPreset[]>([]);
const presetsLoading = ref(false);

const ctxJson = computed(() => {
  return JSON.stringify(props.modelValue.ctx || {}, null, 0);
});

function updateEffectId(event: Event) {
  const target = event.target as HTMLInputElement;
  emit('update:modelValue', {
    ...props.modelValue,
    effectId: target.value,
  });
}

function updateSource(event: Event) {
  const target = event.target as HTMLInputElement;
  const value = target.value.trim();
  // source/target are subject references and live in ctx, where the executor
  // resolves them ($actor, $patient, $patient[N], ...)
  const ctx = { ...(props.modelValue.ctx || {}) };
  if (value) {
    ctx.source = value;
  } else {
    delete ctx.source;
  }
  const updated = { ...props.modelValue, ctx };
  emit('update:modelValue', updated);
}

function updateTarget(event: Event) {
  const target = event.target as HTMLInputElement;
  const value = target.value.trim();
  // source/target are subject references and live in ctx, where the executor
  // resolves them ($actor, $patient, $patient[N], ...)
  const ctx = { ...(props.modelValue.ctx || {}) };
  if (value) {
    ctx.target = value;
  } else {
    delete ctx.target;
  }
  const updated = { ...props.modelValue, ctx };
  emit('update:modelValue', updated);
}

function updateReceivePlayerDirection(event: Event) {
  const target = event.target as HTMLInputElement;
  const checked = target.checked;
  const updated: any = { ...props.modelValue };
  if (checked) {
    updated.receivePlayerDirection = true;
  } else {
    delete updated.receivePlayerDirection;
  }
  emit('update:modelValue', updated);
}

function updateCtx(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  try {
    const parsed = JSON.parse(target.value);
    if (typeof parsed !== 'object') {
      ctxError.value = 'Context must be an object';
      return;
    }
    ctxError.value = null;
    emit('update:modelValue', {
      ...props.modelValue,
      ctx: parsed,
    });
  } catch (e: any) {
    ctxError.value = e.message;
  }
}

function applyEffectPreset(preset: EffectPreset) {
  const template = preset.template;
  const ctx = { ...(template.ctx || {}) };
  // older presets still carry source/target at step level - migrate them into ctx
  if (template.source) ctx.source = template.source;
  if (template.target) ctx.target = template.target;
  emit('update:modelValue', {
    ...props.modelValue,
    effectId: template.effectId,
    ctx,
  });
}

async function loadPresets() {
  presetsLoading.value = true;
  try {
    effectPresets.value = await presetService.getEffectPresets();
  } catch (error) {
    console.error('Failed to load effect presets:', error);
  } finally {
    presetsLoading.value = false;
  }
}

onMounted(() => {
  loadPresets();
});
</script>
