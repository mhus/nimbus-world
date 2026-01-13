<template>
  <div class="space-y-6">
    <div class="flex justify-between items-center">
      <h1 class="text-3xl font-bold">Settings</h1>
      <button @click="createNew" class="btn btn-primary">
        <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
        </svg>
        New Setting
      </button>
    </div>

    <!-- Search and Filter -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <!-- Search by Key -->
      <div class="form-control">
        <label class="label">
          <span class="label-text">Search by Key</span>
        </label>
        <input
          v-model="searchKey"
          type="text"
          placeholder="e.g., redis.host"
          class="input input-bordered font-mono"
        />
      </div>

      <!-- Filter by Type -->
      <div class="form-control">
        <label class="label">
          <span class="label-text">Filter by Type</span>
        </label>
        <select v-model="filterType" class="select select-bordered">
          <option value="">All Types</option>
          <option value="string">String</option>
          <option value="int">Integer</option>
          <option value="long">Long</option>
          <option value="double">Double</option>
          <option value="boolean">Boolean</option>
          <option value="password">Password</option>
          <option value="secret">Secret</option>
        </select>
      </div>
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

    <!-- Settings Table -->
    <div v-else class="overflow-x-auto">
      <table class="table table-zebra w-full">
        <thead>
          <tr>
            <th>Key</th>
            <th>Type</th>
            <th>Value</th>
            <th>Description</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="setting in filteredSettings" :key="setting.key" class="hover">
            <td class="font-mono">{{ setting.key }}</td>
            <td>
              <span class="badge badge-sm" :class="getTypeBadgeClass(setting.type)">
                {{ setting.type }}
              </span>
            </td>
            <td class="max-w-xs truncate">
              <span v-if="setting.type === 'password' || setting.type === 'secret'">••••••••</span>
              <span v-else>{{ formatValue(setting.value, setting.defaultValue) }}</span>
            </td>
            <td class="text-sm text-gray-500">{{ setting.description || '-' }}</td>
            <td>
              <div class="flex gap-2">
                <button @click="editSetting(setting.key)" class="btn btn-sm btn-ghost">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button @click="deleteSetting(setting.key)" class="btn btn-sm btn-ghost btn-error">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div v-if="filteredSettings.length === 0" class="text-center py-12 text-gray-500">
        No settings found
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface Setting {
  key: string;
  value: string | null;
  type: string;
  defaultValue: string | null;
  description: string | null;
  options: Record<string, string> | null;
}

const emit = defineEmits<{
  select: [key: string];
}>();

const settings = ref<Setting[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const filterType = ref('');
const searchKey = ref('');

const filteredSettings = computed(() => {
  let result = settings.value;

  // Filter by type
  if (filterType.value) {
    result = result.filter(s => s.type === filterType.value);
  }

  // Search by key
  if (searchKey.value) {
    const search = searchKey.value.toLowerCase();
    result = result.filter(s => s.key.toLowerCase().includes(search));
  }

  return result;
});

const loadSettings = async () => {
  try {
    loading.value = true;
    error.value = null;
    settings.value = await apiService.get<Setting[]>('/control/settings');
  } catch (err: any) {
    error.value = err.response?.data?.message || 'Failed to load settings';
    console.error('Error loading settings:', err);
  } finally {
    loading.value = false;
  }
};

const editSetting = (key: string) => {
  emit('select', key);
};

const createNew = () => {
  emit('select', '_new');
};

const deleteSetting = async (key: string) => {
  if (!confirm(`Delete setting "${key}"?`)) {
    return;
  }

  try {
    await apiService.delete(`/control/settings/${encodeURIComponent(key)}`);
    await loadSettings();
  } catch (err: any) {
    alert(err.response?.data?.message || 'Failed to delete setting');
    console.error('Error deleting setting:', err);
  }
};

const formatValue = (value: string | null, defaultValue: string | null): string => {
  if (value !== null && value !== undefined) {
    return value;
  }
  if (defaultValue !== null && defaultValue !== undefined) {
    return `${defaultValue} (default)`;
  }
  return '-';
};

const getTypeBadgeClass = (type: string): string => {
  const classes: Record<string, string> = {
    string: 'badge-info',
    int: 'badge-success',
    long: 'badge-success',
    double: 'badge-success',
    boolean: 'badge-warning',
    password: 'badge-error',
    secret: 'badge-error',
  };
  return classes[type] || 'badge-ghost';
};

onMounted(() => {
  loadSettings();
});
</script>
