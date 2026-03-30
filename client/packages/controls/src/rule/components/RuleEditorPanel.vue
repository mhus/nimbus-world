<template>
  <div class="modal modal-open" @click.self="emit('close')">
    <div class="modal-box max-w-4xl" @click.stop>
      <h3 class="font-bold text-lg mb-4">
        {{ isEditMode ? 'Edit Rule' : 'Create Rule' }}
      </h3>

      <!-- Error Alert -->
      <ErrorAlert v-if="errorMessage" :message="errorMessage" class="mb-4" />

      <!-- Read-only ID in edit mode -->
      <div v-if="isEditMode && props.rule" class="text-sm text-base-content/70 mb-4">
        <span class="font-semibold">ID:</span> {{ props.rule.id }}
      </div>

      <form @submit.prevent="handleSave" class="space-y-4">
        <!-- Name -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Name *</span>
          </label>
          <input
            v-model="formData.name"
            type="text"
            class="input input-bordered"
            placeholder="e.g. open_door_on_key"
            required
          />
        </div>

        <!-- Name row: Description + Package -->
        <div class="grid grid-cols-2 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Description</span>
            </label>
            <input
              v-model="formData.description"
              type="text"
              class="input input-bordered"
              placeholder="What does this rule do?"
            />
          </div>
          <div class="form-control">
            <label class="label">
              <span class="label-text">Package</span>
            </label>
            <input
              v-model="formData.rulePackage"
              type="text"
              class="input input-bordered"
              placeholder="e.g. puzzle_door, quest_forest"
            />
          </div>
        </div>

        <!-- Affected Flags (auto-computed, read-only) -->
        <div v-if="isEditMode && props.rule?.affected?.length" class="form-control">
          <label class="label">
            <span class="label-text">Affected Flags (auto-computed)</span>
          </label>
          <div class="flex flex-wrap gap-1">
            <span v-for="flag in props.rule.affected" :key="flag" class="badge badge-outline badge-sm">{{ flag }}</span>
          </div>
          <label class="label">
            <span class="label-text-alt">Automatically derived from condition and effects on save</span>
          </label>
        </div>

        <!-- SpEL Condition -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">SpEL Condition</span>
          </label>
          <textarea
            v-model="formData.spelCondition"
            class="textarea textarea-bordered font-mono text-sm"
            placeholder="e.g. flags.hasKey == true (shorthand for flags.{package}.hasKey)"
            rows="3"
          ></textarea>
          <label class="label">
            <span class="label-text-alt">Boolean SpEL expression. "flags.x" = same package, "flags.pkg.x" = cross-package. Empty = always true.</span>
          </label>
        </div>

        <!-- Effects -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Effects</span>
          </label>
          <div class="space-y-3">
            <div v-for="(effect, index) in formData.effects" :key="index" class="flex gap-2 items-start">
              <div class="flex-1 space-y-2 border border-base-300 rounded-lg p-3">
                <div class="flex gap-2">
                  <select
                    v-model="effect.type"
                    class="select select-bordered select-sm flex-1"
                  >
                    <option value="">Select type</option>
                    <option value="LogicFlagUpdate">LogicFlagUpdate</option>
                    <option value="block_status">block_status</option>
                    <option value="apply_rule">apply_rule</option>
                  </select>
                </div>
                <!-- Key-Value Parameter Editor -->
                <div class="space-y-1">
                  <div
                    v-for="(paramEntry, pIdx) in effectParamEntries[index]"
                    :key="pIdx"
                    class="flex gap-1 items-center"
                  >
                    <input
                      v-model="paramEntry.key"
                      type="text"
                      class="input input-bordered input-xs flex-1 font-mono"
                      placeholder="key"
                      @blur="syncEffectParams(index)"
                    />
                    <input
                      v-model="paramEntry.value"
                      type="text"
                      class="input input-bordered input-xs flex-1 font-mono"
                      placeholder="value"
                      @blur="syncEffectParams(index)"
                    />
                    <button
                      type="button"
                      class="btn btn-ghost btn-xs btn-square"
                      @click="removeEffectParam(index, pIdx)"
                    >
                      <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                      </svg>
                    </button>
                  </div>
                  <button
                    type="button"
                    class="btn btn-ghost btn-xs"
                    @click="addEffectParam(index)"
                  >
                    + Add Parameter
                  </button>
                </div>
              </div>
              <button
                type="button"
                class="btn btn-ghost btn-sm btn-square mt-3"
                @click="removeEffect(index)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="addEffect"
            >
              <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add Effect
            </button>
          </div>
        </div>

        <!-- Priority -->
        <div class="grid grid-cols-2 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Priority</span>
            </label>
            <input
              v-model.number="formData.priority"
              type="number"
              class="input input-bordered"
              placeholder="100"
            />
            <label class="label">
              <span class="label-text-alt">Lower values execute first</span>
            </label>
          </div>

          <!-- Enabled -->
          <div class="form-control">
            <label class="label cursor-pointer">
              <span class="label-text">Enabled</span>
              <input
                v-model="formData.enabled"
                type="checkbox"
                class="checkbox checkbox-primary"
              />
            </label>
          </div>
        </div>

        <!-- Epoches -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Epoches</span>
          </label>
          <input
            v-model="epochesText"
            type="text"
            class="input input-bordered"
            placeholder="Comma-separated epoch numbers, e.g. 0,1,2"
          />
          <label class="label">
            <span class="label-text-alt">Comma-separated list of epoch numbers (empty = not active in any epoch)</span>
          </label>
        </div>

        <!-- Action Buttons -->
        <div class="modal-action">
          <button
            type="button"
            class="btn"
            @click="emit('close')"
          >
            Cancel
          </button>
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="saving"
          >
            <span v-if="saving" class="loading loading-spinner"></span>
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { LogicRuleDto, LogicEffect, CreateLogicRuleRequest, UpdateLogicRuleRequest } from '@/services/LogicRuleService';
import ErrorAlert from '@components/ErrorAlert.vue';
import { useLogicRules } from '@/composables/useLogicRules';

interface Props {
  rule: LogicRuleDto | null;
  worldId: string;
  currentEpoch?: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const isEditMode = computed(() => !!props.rule);
const { createRule, updateRule } = useLogicRules(props.worldId);

const formData = ref<{
  name: string;
  description: string;
  rulePackage: string;
  spelCondition: string;
  effects: LogicEffect[];
  enabled: boolean;
  priority: number;
}>({
  name: '',
  description: '',
  rulePackage: '',
  spelCondition: '',
  effects: [],
  enabled: true,
  priority: 100,
});

const epochesText = ref('');
const errorMessage = ref('');
const saving = ref(false);

interface ParamEntry {
  key: string;
  value: string;
}

/**
 * Reactive key-value entries for each effect's parameters.
 * Each effect has its own array of {key, value} pairs.
 */
const effectParamEntries = ref<ParamEntry[][]>([]);

/**
 * Convert a parameters map to key-value entries for the editor.
 */
function paramsToEntries(params: Record<string, string> | undefined): ParamEntry[] {
  if (!params) return [];
  return Object.entries(params).map(([key, value]) => ({ key, value: value ?? '' }));
}

/**
 * Convert key-value entries back to a parameters map (all strings).
 */
function entriesToParams(entries: ParamEntry[]): Record<string, string> {
  const params: Record<string, string> = {};
  for (const entry of entries) {
    if (!entry.key.trim()) continue;
    params[entry.key.trim()] = entry.value;
  }
  return params;
}

// Initialize form data
if (props.rule) {
  formData.value = {
    name: props.rule.name || '',
    description: props.rule.description || '',
    rulePackage: props.rule.rulePackage || '',
    spelCondition: props.rule.spelCondition || '',
    effects: (props.rule.effects || []).map(e => ({ ...e, parameters: { ...(e.parameters || {}) } })),
    enabled: props.rule.enabled,
    priority: props.rule.priority,
  };
  epochesText.value = (props.rule.epoches || []).join(', ');
  effectParamEntries.value = formData.value.effects.map(e => paramsToEntries(e.parameters));
} else if (props.currentEpoch !== undefined) {
  epochesText.value = String(props.currentEpoch);
}

const addEffect = () => {
  formData.value.effects.push({ type: '', parameters: {} });
  effectParamEntries.value.push([]);
};

const removeEffect = (index: number) => {
  formData.value.effects.splice(index, 1);
  effectParamEntries.value.splice(index, 1);
};

const addEffectParam = (effectIndex: number) => {
  effectParamEntries.value[effectIndex].push({ key: '', value: '' });
};

const removeEffectParam = (effectIndex: number, paramIndex: number) => {
  effectParamEntries.value[effectIndex].splice(paramIndex, 1);
  syncEffectParams(effectIndex);
};

/**
 * Sync key-value entries back to the effect's parameters map.
 */
const syncEffectParams = (effectIndex: number) => {
  formData.value.effects[effectIndex].parameters = entriesToParams(effectParamEntries.value[effectIndex]);
};

const parseEpoches = (): number[] => {
  return epochesText.value
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0 && !isNaN(Number(s)))
    .map(s => Number(s));
};

const handleSave = async () => {
  errorMessage.value = '';
  saving.value = true;

  try {
    if (!formData.value.name?.trim()) {
      throw new Error('Name is required');
    }

    // Sync all effect parameters from key-value entries
    for (let i = 0; i < formData.value.effects.length; i++) {
      syncEffectParams(i);
      if (!formData.value.effects[i].type) {
        throw new Error(`Effect ${i + 1}: type is required`);
      }
    }

    const epoches = parseEpoches();

    if (isEditMode.value && props.rule?.id) {
      const updateData: UpdateLogicRuleRequest = {
        name: formData.value.name.trim(),
        description: formData.value.description || undefined,
        rulePackage: formData.value.rulePackage || undefined,
        spelCondition: formData.value.spelCondition,
        effects: formData.value.effects,
        epoches,
        enabled: formData.value.enabled,
        priority: formData.value.priority,
      };
      const success = await updateRule(props.rule.id, updateData);
      if (!success) {
        throw new Error('Failed to update rule');
      }
    } else {
      const createData: CreateLogicRuleRequest = {
        name: formData.value.name.trim(),
        description: formData.value.description || undefined,
        rulePackage: formData.value.rulePackage || undefined,
        spelCondition: formData.value.spelCondition,
        effects: formData.value.effects,
        epoches,
        enabled: formData.value.enabled,
        priority: formData.value.priority,
      };
      const id = await createRule(createData);
      if (!id) {
        throw new Error('Failed to create rule');
      }
    }

    emit('saved');
  } catch (error: any) {
    errorMessage.value = error.message || 'Failed to save rule';
  } finally {
    saving.value = false;
  }
};
</script>
