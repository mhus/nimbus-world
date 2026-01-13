<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex justify-between items-center">
      <h1 class="text-3xl font-bold">{{ isNew ? 'New Setting' : 'Edit Setting' }}</h1>
      <button @click="$emit('back')" class="btn btn-ghost">
        <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
        Back
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Editor Form -->
    <div v-else class="card bg-base-100 shadow-xl">
      <div class="card-body">
        <form @submit.prevent="save" class="space-y-4">
          <!-- Key -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Key *</span>
            </label>
            <input
              v-model="form.key"
              type="text"
              :disabled="!isNew"
              class="input input-bordered font-mono"
              placeholder="e.g., redis.host"
              required
            />
          </div>

          <!-- Type -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Type *</span>
            </label>
            <select v-model="form.type" class="select select-bordered" required>
              <option value="string">String</option>
              <option value="int">Integer</option>
              <option value="long">Long</option>
              <option value="double">Double</option>
              <option value="boolean">Boolean</option>
              <option value="password">Password (Encrypted)</option>
              <option value="secret">Secret</option>
            </select>
          </div>

          <!-- Value -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Value</span>
            </label>
            <input
              v-if="form.type === 'boolean'"
              v-model="form.value"
              type="checkbox"
              class="checkbox"
            />
            <div v-else-if="form.type === 'password' || form.type === 'secret'" class="space-y-2">
              <input
                v-model="form.value"
                type="password"
                class="input input-bordered w-full"
                placeholder="Enter new password"
              />
              <p v-if="!isNew" class="text-xs text-gray-500">
                Leave empty to keep existing password unchanged
              </p>
            </div>
            <input
              v-else-if="form.type === 'int' || form.type === 'long'"
              v-model="form.value"
              type="number"
              step="1"
              class="input input-bordered"
              placeholder="Enter integer value"
            />
            <input
              v-else-if="form.type === 'double'"
              v-model="form.value"
              type="number"
              step="any"
              class="input input-bordered"
              placeholder="Enter decimal value"
            />
            <input
              v-else
              v-model="form.value"
              type="text"
              class="input input-bordered"
              placeholder="Enter value"
            />
          </div>

          <!-- Default Value -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Default Value</span>
            </label>
            <input
              v-model="form.defaultValue"
              type="text"
              class="input input-bordered"
              placeholder="Optional default value"
            />
          </div>

          <!-- Description -->
          <div class="form-control">
            <label class="label">
              <span class="label-text">Description</span>
            </label>
            <textarea
              v-model="form.description"
              class="textarea textarea-bordered"
              rows="3"
              placeholder="Optional description"
            ></textarea>
          </div>

          <!-- Actions -->
          <div class="flex justify-end gap-2 pt-4">
            <button type="button" @click="$emit('back')" class="btn btn-ghost">
              Cancel
            </button>
            <button type="submit" class="btn btn-primary" :disabled="saving">
              <span v-if="saving" class="loading loading-spinner loading-sm"></span>
              {{ saving ? 'Saving...' : 'Save' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface Props {
  settingKey: string;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const isNew = props.settingKey === '_new';

const form = ref({
  key: '',
  value: '',
  type: 'string',
  defaultValue: '',
  description: '',
});

const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);

const loadSetting = async () => {
  if (isNew) {
    return;
  }

  try {
    loading.value = true;
    error.value = null;
    const setting = await apiService.get<any>(`/control/settings/${encodeURIComponent(props.settingKey)}`);

    form.value = {
      key: setting.key,
      value: setting.value || '',
      type: setting.type || 'string',
      defaultValue: setting.defaultValue || '',
      description: setting.description || '',
    };
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Failed to load setting';
    console.error('Error loading setting:', err);
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  try {
    saving.value = true;
    error.value = null;

    // Validate: password/secret must have a value when creating new
    if (isNew && (form.value.type === 'password' || form.value.type === 'secret')) {
      if (!form.value.value || form.value.value.trim() === '') {
        error.value = 'Password/Secret value is required when creating new setting';
        saving.value = false;
        return;
      }
    }

    const payload = {
      key: form.value.key,
      value: form.value.value || null,
      type: form.value.type,
      defaultValue: form.value.defaultValue || null,
      description: form.value.description || null,
    };

    if (isNew) {
      await apiService.post('/control/settings', payload);
    } else {
      await apiService.put(`/control/settings/${encodeURIComponent(props.settingKey)}`, payload);
    }

    emit('saved');
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Failed to save setting';
    console.error('Error saving setting:', err);
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadSetting();
});
</script>
