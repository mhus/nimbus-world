<template>
  <div class="space-y-2 bg-base-200 p-3 rounded-lg">
    <!-- Condition Type Selector -->
    <div class="form-control">
      <label class="label py-1">
        <span class="label-text text-xs font-semibold">Condition Type</span>
      </label>
      <select
        :value="modelValue.kind"
        class="select select-bordered select-sm"
        @change="changeConditionType($event)"
      >
        <option value="VarEquals">Variable Equals</option>
        <option value="VarExists">Variable Exists</option>
        <option value="Chance">Random Chance</option>
        <option value="HasTargets">Has Targets</option>
        <option value="HasSource">Has Source</option>
        <option value="IsVarTrue">Variable Is True</option>
        <option value="IsVarFalse">Variable Is False</option>
      </select>
    </div>

    <!-- VarEquals -->
    <template v-if="modelValue.kind === 'VarEquals'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Variable Name</span>
        </label>
        <input
          :value="modelValue.name"
          type="text"
          class="input input-bordered input-sm"
          placeholder="variableName"
          @input="updateField('name', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Expected Value</span>
        </label>
        <input
          :value="modelValue.value"
          type="text"
          class="input input-bordered input-sm"
          placeholder="value"
          @input="updateField('value', ($event.target as HTMLInputElement).value)"
        />
      </div>
    </template>

    <!-- VarExists -->
    <template v-else-if="modelValue.kind === 'VarExists'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Variable Name</span>
        </label>
        <input
          :value="modelValue.name"
          type="text"
          class="input input-bordered input-sm"
          placeholder="variableName"
          @input="updateField('name', ($event.target as HTMLInputElement).value)"
        />
      </div>
    </template>

    <!-- Chance -->
    <template v-else-if="modelValue.kind === 'Chance'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Probability (0.0 - 1.0)</span>
        </label>
        <input
          :value="modelValue.p"
          type="number"
          class="input input-bordered input-sm"
          placeholder="0.5"
          min="0"
          max="1"
          step="0.1"
          @input="updateField('p', parseFloat(($event.target as HTMLInputElement).value))"
        />
      </div>
    </template>

    <!-- HasTargets -->
    <template v-else-if="modelValue.kind === 'HasTargets'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Minimum Count (optional)</span>
        </label>
        <input
          :value="modelValue.min || 1"
          type="number"
          class="input input-bordered input-sm"
          placeholder="1"
          min="0"
          @input="updateField('min', parseInt(($event.target as HTMLInputElement).value))"
        />
      </div>
    </template>

    <!-- HasSource (no fields) -->
    <template v-else-if="modelValue.kind === 'HasSource'">
      <div class="text-xs opacity-60">
        Checks if source/actor exists (no configuration needed)
      </div>
    </template>

    <!-- IsVarTrue -->
    <template v-else-if="modelValue.kind === 'IsVarTrue'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Variable Name</span>
        </label>
        <input
          :value="modelValue.name"
          type="text"
          class="input input-bordered input-sm"
          placeholder="variableName"
          @input="updateField('name', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Default Value (if not exists)</span>
        </label>
        <input
          type="checkbox"
          class="checkbox checkbox-sm"
          :checked="modelValue.defaultValue || false"
          @change="updateField('defaultValue', ($event.target as HTMLInputElement).checked)"
        />
      </div>
    </template>

    <!-- IsVarFalse -->
    <template v-else-if="modelValue.kind === 'IsVarFalse'">
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Variable Name</span>
        </label>
        <input
          :value="modelValue.name"
          type="text"
          class="input input-bordered input-sm"
          placeholder="variableName"
          @input="updateField('name', ($event.target as HTMLInputElement).value)"
        />
      </div>
      <div class="form-control">
        <label class="label py-1">
          <span class="label-text text-xs">Default Value (if not exists)</span>
        </label>
        <input
          type="checkbox"
          class="checkbox checkbox-sm"
          :checked="modelValue.defaultValue !== false"
          @change="updateField('defaultValue', ($event.target as HTMLInputElement).checked)"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import type { ScrawlCondition } from '@nimbus/shared';

const props = defineProps<{
  modelValue: ScrawlCondition;
}>();

const emit = defineEmits<{
  'update:modelValue': [condition: ScrawlCondition];
}>();

function changeConditionType(event: Event) {
  const target = event.target as HTMLSelectElement;
  const kind = target.value;

  let newCondition: ScrawlCondition;
  switch (kind) {
    case 'VarEquals':
      newCondition = { kind: 'VarEquals', name: '', value: '' };
      break;
    case 'VarExists':
      newCondition = { kind: 'VarExists', name: '' };
      break;
    case 'Chance':
      newCondition = { kind: 'Chance', p: 0.5 };
      break;
    case 'HasTargets':
      newCondition = { kind: 'HasTargets', min: 1 };
      break;
    case 'HasSource':
      newCondition = { kind: 'HasSource' };
      break;
    case 'IsVarTrue':
      newCondition = { kind: 'IsVarTrue', name: '', defaultValue: false };
      break;
    case 'IsVarFalse':
      newCondition = { kind: 'IsVarFalse', name: '', defaultValue: true };
      break;
    default:
      newCondition = { kind: 'VarExists', name: '' };
  }

  emit('update:modelValue', newCondition);
}

function updateField(field: string, value: any) {
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value,
  } as ScrawlCondition);
}
</script>
