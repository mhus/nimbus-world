<template>
  <div class="modal modal-open" @click.self="emit('close')">
    <div class="modal-box max-w-lg" @click.stop>
      <h3 class="font-bold text-lg mb-4">
        {{ isEditMode ? 'Edit State' : 'Create State' }}
      </h3>

      <div v-if="errorMessage" class="alert alert-error mb-4">
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="isEditMode && props.flag" class="text-sm text-base-content/70 mb-4">
        <span class="font-semibold">ID:</span> {{ props.flag.id }}
        <span v-if="props.flag.autoCreated" class="badge badge-xs badge-warning ml-2">auto-created</span>
      </div>

      <form @submit.prevent="handleSave" class="space-y-4">
        <!-- State Name -->
        <div class="form-control">
          <label class="label"><span class="label-text">State Name *</span></label>
          <input
            v-model="formData.flagName"
            type="text"
            class="input input-bordered font-mono"
            placeholder="e.g. hasKey, doorOpen, counter"
            :disabled="isEditMode"
            required
          />
          <label class="label">
            <span class="label-text-alt">Qualified name used in rules: state.&lt;package&gt;.{{ formData.flagName || 'flagName' }}</span>
          </label>
        </div>

        <!-- Type -->
        <div class="form-control">
          <label class="label"><span class="label-text">Type</span></label>
          <select v-model="formData.type" class="select select-bordered">
            <option value="">Unspecified</option>
            <option value="boolean">boolean</option>
            <option value="integer">integer</option>
            <option value="string">string</option>
          </select>
        </div>

        <!-- Default Value -->
        <div class="form-control">
          <label class="label"><span class="label-text">Default Value</span></label>
          <input
            v-model="formData.defaultValue"
            type="text"
            class="input input-bordered"
            placeholder="e.g. false, 0, locked"
          />
        </div>

        <!-- Description -->
        <div class="form-control">
          <label class="label"><span class="label-text">Description</span></label>
          <input
            v-model="formData.description"
            type="text"
            class="input input-bordered"
            placeholder="What does this flag represent?"
          />
        </div>

        <div class="modal-action">
          <button type="button" class="btn" @click="emit('close')">Cancel</button>
          <button type="submit" class="btn btn-primary" :disabled="saving">
            <span v-if="saving" class="loading loading-spinner"></span>
            {{ saving ? 'Saving...' : 'Save' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { apiService } from '@/services/ApiService';

interface StateDefItem {
  id: string;
  worldId: string;
  flagName: string;
  defaultValue: any;
  type?: string;
  description?: string;
  autoCreated: boolean;
  createdAt: string;
}

interface Props {
  worldId: string;
  flag: StateDefItem | null;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const isEditMode = computed(() => !!props.flag);
const errorMessage = ref('');
const saving = ref(false);

const formData = ref({
  flagName: props.flag?.flagName || '',
  type: props.flag?.type || '',
  defaultValue: props.flag?.defaultValue != null ? String(props.flag.defaultValue) : '',
  description: props.flag?.description || '',
});

const handleSave = async () => {
  errorMessage.value = '';
  saving.value = true;

  try {
    if (!formData.value.flagName.trim()) throw new Error('Flag name is required');

    const body: Record<string, any> = {
      type: formData.value.type || undefined,
      defaultValue: formData.value.defaultValue || undefined,
      description: formData.value.description || undefined,
    };

    if (isEditMode.value && props.flag?.id) {
      await apiService.put(`/control/worlds/${props.worldId}/logic-states/${props.flag.id}`, body);
    } else {
      body.flagName = formData.value.flagName.trim();
      await apiService.post(`/control/worlds/${props.worldId}/logic-states`, body);
    }

    emit('saved');
  } catch (err: any) {
    errorMessage.value = err.message || 'Failed to save state';
  } finally {
    saving.value = false;
  }
};
</script>
