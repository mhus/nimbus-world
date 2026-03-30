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

        <!-- Description -->
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

        <!-- Affected Flags -->
        <div class="form-control">
          <label class="label">
            <span class="label-text">Affected Flags *</span>
          </label>
          <input
            v-model="affectedText"
            type="text"
            class="input input-bordered"
            placeholder="Comma-separated flag names, e.g. hasKey, doorOpen"
          />
          <label class="label">
            <span class="label-text-alt">Flag names that trigger this rule when changed</span>
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
            placeholder="e.g. flags.hasKey == true && flags.doorOpen == false"
            rows="3"
          ></textarea>
          <label class="label">
            <span class="label-text-alt">Boolean SpEL expression. Empty = always true when affected flags change.</span>
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
                  </select>
                </div>
                <textarea
                  v-model="effectParamsText[index]"
                  class="textarea textarea-bordered textarea-sm w-full font-mono text-xs"
                  :placeholder="getEffectPlaceholder(effect.type)"
                  rows="2"
                  @blur="parseEffectParams(index)"
                ></textarea>
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
  spelCondition: string;
  effects: LogicEffect[];
  enabled: boolean;
  priority: number;
}>({
  name: '',
  description: '',
  spelCondition: '',
  effects: [],
  enabled: true,
  priority: 100,
});

const affectedText = ref('');
const epochesText = ref('');
const effectParamsText = ref<string[]>([]);
const errorMessage = ref('');
const saving = ref(false);

// Initialize form data
if (props.rule) {
  formData.value = {
    name: props.rule.name || '',
    description: props.rule.description || '',
    spelCondition: props.rule.spelCondition || '',
    effects: (props.rule.effects || []).map(e => ({ ...e })),
    enabled: props.rule.enabled,
    priority: props.rule.priority,
  };
  affectedText.value = (props.rule.affected || []).join(', ');
  epochesText.value = (props.rule.epoches || []).join(', ');
  effectParamsText.value = formData.value.effects.map(e =>
    JSON.stringify(e.parameters || {}, null, 2)
  );
} else if (props.currentEpoch !== undefined) {
  epochesText.value = String(props.currentEpoch);
}

const getEffectPlaceholder = (type: string): string => {
  switch (type) {
    case 'LogicFlagUpdate':
      return '{"doorOpen": true, "counter": 5}';
    case 'block_status':
      return '{"chunkKey": "1:2", "blockKey": "5,3,8", "value": "toggle", "defaultState": "closed"}';
    default:
      return '{"key": "value"}';
  }
};

const addEffect = () => {
  formData.value.effects.push({ type: '', parameters: {} });
  effectParamsText.value.push('{}');
};

const removeEffect = (index: number) => {
  formData.value.effects.splice(index, 1);
  effectParamsText.value.splice(index, 1);
};

const parseEffectParams = (index: number) => {
  try {
    const parsed = JSON.parse(effectParamsText.value[index] || '{}');
    formData.value.effects[index].parameters = parsed;
  } catch {
    // Leave as-is, will validate on save
  }
};

const parseAffected = (): string[] => {
  return affectedText.value
    .split(',')
    .map(s => s.trim())
    .filter(s => s.length > 0);
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

    const affected = parseAffected();
    if (affected.length === 0) {
      throw new Error('At least one affected flag is required');
    }

    // Parse all effect parameters
    for (let i = 0; i < formData.value.effects.length; i++) {
      parseEffectParams(i);
      if (!formData.value.effects[i].type) {
        throw new Error(`Effect ${i + 1}: type is required`);
      }
    }

    const epoches = parseEpoches();

    if (isEditMode.value && props.rule?.id) {
      const updateData: UpdateLogicRuleRequest = {
        name: formData.value.name.trim(),
        description: formData.value.description || undefined,
        affected,
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
        affected,
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
