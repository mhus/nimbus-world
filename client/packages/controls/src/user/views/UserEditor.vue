<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button class="btn btn-ghost gap-2" @click="handleBack">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to List
        </button>
      </div>
      <h2 class="text-2xl font-bold">Edit User</h2>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Edit Form -->
    <div v-else class="space-y-6">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <form @submit.prevent="handleSave" class="space-y-4">
            <!-- Username (readonly) -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Username</span>
              </label>
              <input
                :value="user?.username"
                type="text"
                class="input input-bordered w-full"
                disabled
              />
            </div>

            <!-- Display Name (from publicData) -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Display Name</span>
              </label>
              <input
                v-model="formData.displayName"
                type="text"
                class="input input-bordered w-full"
                placeholder="Enter display name..."
              />
              <label class="label">
                <span class="label-text-alt">Public display name for this user</span>
              </label>
            </div>

            <!-- Email -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Email</span>
              </label>
              <input
                v-model="formData.email"
                type="email"
                placeholder="Enter email"
                class="input input-bordered w-full"
                required
              />
            </div>

            <!-- Sector Roles -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Sector Roles</span>
              </label>
              <div class="flex flex-wrap gap-4">
                <label class="label cursor-pointer gap-2">
                  <input
                    type="checkbox"
                    class="checkbox"
                    :checked="user?.sectorRoles?.includes(SectorRoles.USER)"
                    @change="handleSectorRoleChange(SectorRoles.USER, $event)"
                  />
                  <span class="label-text">USER</span>
                </label>
                <label class="label cursor-pointer gap-2">
                  <input
                    type="checkbox"
                    class="checkbox"
                    :checked="user?.sectorRoles?.includes(SectorRoles.ADMIN)"
                    @change="handleSectorRoleChange(SectorRoles.ADMIN, $event)"
                  />
                  <span class="label-text">ADMIN</span>
                </label>
                <label class="label cursor-pointer gap-2">
                  <input
                    type="checkbox"
                    class="checkbox"
                    :checked="user?.sectorRoles?.includes(SectorRoles.PLAYER)"
                    @change="handleSectorRoleChange(SectorRoles.PLAYER, $event)"
                  />
                  <span class="label-text">PLAYER</span>
                </label>
              </div>
            </div>

            <!-- Enabled Status -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Account Status</span>
              </label>
              <div class="flex items-center gap-2">
                <input
                  type="checkbox"
                  :checked="user?.enabled"
                  class="checkbox"
                  disabled
                />
                <span class="label-text">{{ user?.enabled ? 'Enabled' : 'Disabled' }}</span>
              </div>
            </div>

            <!-- Created At -->
            <div v-if="user?.createdAt" class="form-control">
              <label class="label">
                <span class="label-text font-medium">Created At</span>
              </label>
              <input
                :value="formatDate(user.createdAt)"
                type="text"
                class="input input-bordered w-full"
                disabled
              />
            </div>

            <!-- Action Buttons -->
            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>Save</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Region Roles Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Region Roles</h3>

          <!-- Add New Region -->
          <div class="flex gap-2 mb-4">
            <select
              v-model="newRegionId"
              class="select select-bordered flex-1"
            >
              <option value="">Select region to add...</option>
              <option v-for="region in regions" :key="region.name" :value="region.name">
                {{ region.title || region.name }}
              </option>
            </select>
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddRegion"
              :disabled="!newRegionId"
            >
              Add Region
            </button>
          </div>

          <div v-if="user?.regionRoles && Object.keys(user.regionRoles).length > 0" class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Region ID</th>
                  <th>PLAYER</th>
                  <th>SUPPORT</th>
                  <th>EDITOR</th>
                  <th>ADMIN</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(role, regionId) in user.regionRoles" :key="regionId">
                  <td class="font-mono">{{ regionId }}</td>
                  <td>
                    <input
                      type="checkbox"
                      class="checkbox checkbox-sm"
                      :checked="role === 'PLAYER'"
                      @change="handleRoleChange(regionId, 'PLAYER', $event)"
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      class="checkbox checkbox-sm"
                      :checked="role === 'SUPPORT'"
                      @change="handleRoleChange(regionId, 'SUPPORT', $event)"
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      class="checkbox checkbox-sm"
                      :checked="role === 'EDITOR'"
                      @change="handleRoleChange(regionId, 'EDITOR', $event)"
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      class="checkbox checkbox-sm"
                      :checked="role === 'ADMIN'"
                      @change="handleRoleChange(regionId, 'ADMIN', $event)"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-sm btn-error"
                      @click="handleRemoveRegionRole(regionId)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No region roles configured</p>
            <p class="text-base-content/50 text-sm mt-2">Add a region above to configure roles</p>
          </div>
        </div>
      </div>

      <!-- Character Limits Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Character Limits per Region</h3>

          <!-- Add New Character Limit -->
          <div class="flex gap-2 mb-4">
            <select
              v-model="newCharLimitRegionId"
              class="select select-bordered flex-1"
            >
              <option value="">Select region...</option>
              <option v-for="region in regions" :key="region.name" :value="region.name">
                {{ region.title || region.name }}
              </option>
            </select>
            <input
              v-model.number="newCharLimit"
              type="number"
              min="0"
              placeholder="Max characters"
              class="input input-bordered w-32"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddCharacterLimit"
              :disabled="!newCharLimitRegionId || newCharLimit === null"
            >
              Add Limit
            </button>
          </div>

          <div v-if="user?.characterLimits && Object.keys(user.characterLimits).length > 0" class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Region ID</th>
                  <th>Max Characters</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(limit, regionId) in user.characterLimits" :key="regionId">
                  <td class="font-mono">{{ regionId }}</td>
                  <td>
                    <input
                      type="number"
                      min="0"
                      :value="limit"
                      @input="handleCharLimitChange(regionId, $event)"
                      class="input input-bordered input-sm w-24"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-sm btn-error"
                      @click="handleRemoveCharacterLimit(regionId)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No character limits configured</p>
            <p class="text-base-content/50 text-sm mt-2">Add a region above to configure limits</p>
          </div>
        </div>
      </div>

      <!-- User Settings Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">User Settings</h3>

          <!-- Add New Setting -->
          <div class="flex gap-2 mb-4">
            <select
              v-model="newSettingClientType"
              class="select select-bordered flex-1"
            >
              <option value="">Select client type...</option>
              <option :value="ClientType.WEB">Web</option>
              <option :value="ClientType.XBOX">Xbox</option>
              <option :value="ClientType.MOBILE">Mobile</option>
              <option :value="ClientType.DESKTOP">Desktop</option>
            </select>
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddSetting"
              :disabled="!newSettingClientType"
            >
              Add Setting
            </button>
          </div>

          <!-- Existing Settings -->
          <div v-if="user?.userSettings && Object.keys(user.userSettings).length > 0" class="space-y-4">
            <div
              v-for="(settings, clientType) in user.userSettings"
              :key="clientType"
              class="border border-base-300 rounded-lg p-4"
            >
              <div class="flex items-center justify-between mb-2">
                <h4 class="font-bold">{{ clientType }}</h4>
                <button
                  type="button"
                  class="btn btn-sm btn-error"
                  @click="handleDeleteSetting(clientType)"
                >
                  Delete
                </button>
              </div>

              <!-- Display/Edit Mode -->
              <div class="space-y-3">
                <!-- Name -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text-alt">Name</span>
                  </label>
                  <input
                    :value="settings.name || ''"
                    @input="handleSettingFieldChange(clientType, 'name', $event)"
                    type="text"
                    placeholder="Setting name"
                    class="input input-bordered input-sm"
                  />
                </div>

                <!-- Input Controller -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text-alt">Input Controller</span>
                  </label>
                  <input
                    :value="settings.inputController || ''"
                    @input="handleSettingFieldChange(clientType, 'inputController', $event)"
                    type="text"
                    placeholder="Input controller"
                    class="input input-bordered input-sm"
                  />
                </div>

                <!-- Input Mappings -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text-alt">Input Mappings</span>
                  </label>

                  <!-- Add new mapping -->
                  <div class="flex gap-2 mb-2">
                    <input
                      v-model="newMappingKey[clientType]"
                      type="text"
                      placeholder="Key"
                      class="input input-bordered input-sm flex-1"
                    />
                    <input
                      v-model="newMappingValue[clientType]"
                      type="text"
                      placeholder="Value"
                      class="input input-bordered input-sm flex-1"
                    />
                    <button
                      type="button"
                      class="btn btn-sm btn-secondary"
                      @click="handleAddMapping(clientType)"
                      :disabled="!newMappingKey[clientType] || !newMappingValue[clientType]"
                    >
                      Add
                    </button>
                  </div>

                  <!-- Existing mappings -->
                  <div v-if="settings.inputMappings && Object.keys(settings.inputMappings).length > 0" class="space-y-1">
                    <div
                      v-for="(value, key) in settings.inputMappings"
                      :key="key"
                      class="flex gap-2 items-center bg-base-200 p-2 rounded"
                    >
                      <span class="font-mono text-xs flex-1">{{ key }}</span>
                      <span class="text-xs">→</span>
                      <span class="font-mono text-xs flex-1">{{ value }}</span>
                      <button
                        type="button"
                        class="btn btn-xs btn-error"
                        @click="handleRemoveMapping(clientType, key)"
                      >
                        Remove
                      </button>
                    </div>
                  </div>
                  <div v-else class="text-xs text-base-content/60 text-center py-2">
                    No input mappings
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No settings configured</p>
            <p class="text-base-content/50 text-sm mt-2">Add a setting by entering a client type above</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Success Message -->
    <div v-if="successMessage" class="alert alert-success">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
      <span>{{ successMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { userService, type RUser, type Settings, SectorRoles } from '../services/UserService';
import { useRegion } from '@/composables/useRegion';
import { ClientType } from '@nimbus/shared/network/MessageTypes';

const props = defineProps<{
  username: string;
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { regions, loadRegions } = useRegion();
const user = ref<RUser | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  displayName: '',
  email: '',
});

const newSettingClientType = ref('');
const newMappingKey = reactive<Record<string, string>>({});
const newMappingValue = reactive<Record<string, string>>({});

const newRegionId = ref('');
const newCharLimitRegionId = ref('');
const newCharLimit = ref<number | null>(null);

const formatDate = (date: Date | string): string => {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString();
};

const loadUser = async () => {
  loading.value = true;
  error.value = null;

  try {
    const loadedUser = await userService.getUser(props.username);

    // Convert sectorRoles from string names to enum numbers
    const sectorRoles = (loadedUser.sectorRoles as any || []).map((role: any) => {
      if (typeof role === 'string') {
        return SectorRoles[role as keyof typeof SectorRoles];
      }
      return role;
    });

    user.value = {
      ...loadedUser,
      sectorRoles,
    };

    formData.value = {
      displayName: user.value.publicData?.displayName || '',
      email: user.value.email,
    };
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load user';
    console.error('Failed to load user:', e);
  } finally {
    loading.value = false;
  }
};

const handleSave = async () => {
  if (!user.value) return;

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    // Create clean RUser object
    const updatedUser: RUser = {
      username: user.value.username,
      email: formData.value.email,
      createdAt: user.value.createdAt,
      publicData: {
        userId: props.username,
        displayName: formData.value.displayName,
      },
      enabled: user.value.enabled,
      sectorRoles: user.value.sectorRoles || [],
      regionRoles: user.value.regionRoles || {},
      characterLimits: user.value.characterLimits || {},
      userSettings: user.value.userSettings || {},
    };

    await userService.updateUser(props.username, updatedUser);
    successMessage.value = 'User updated successfully';
    await loadUser();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save user';
    console.error('Failed to save user:', e);
  } finally {
    saving.value = false;
  }
};

const handleSectorRoleChange = (role: number, event: Event) => {
  if (!user.value) return;

  const target = event.target as HTMLInputElement;
  const currentRoles = user.value.sectorRoles || [];

  let updatedRoles: number[];
  if (target.checked) {
    updatedRoles = [...currentRoles, role];
  } else {
    updatedRoles = currentRoles.filter(r => r !== role);
  }

  // Directly modify the sectorRoles array
  if (!user.value.sectorRoles) {
    user.value.sectorRoles = [];
  }
  user.value.sectorRoles = updatedRoles;

  console.log('Updated sectorRoles:', user.value.sectorRoles);
};

const handleAddSetting = () => {
  if (!user.value || !newSettingClientType.value) {
    return;
  }

  const clientType = newSettingClientType.value;

  // Add empty settings to user object
  user.value = {
    ...user.value,
    userSettings: {
      ...user.value.userSettings,
      [clientType]: {
        name: '',
        inputController: '',
        inputMappings: {},
      },
    },
  };

  newSettingClientType.value = '';
};

const handleSettingFieldChange = (clientType: string, field: string, event: Event) => {
  if (!user.value) return;

  const target = event.target as HTMLInputElement;
  const currentSettings = user.value.userSettings?.[clientType] || {};

  user.value = {
    ...user.value,
    userSettings: {
      ...user.value.userSettings,
      [clientType]: {
        ...currentSettings,
        [field]: target.value,
      },
    },
  };
};

const handleAddMapping = (clientType: string) => {
  if (!user.value || !newMappingKey[clientType] || !newMappingValue[clientType]) {
    return;
  }

  const currentSettings = user.value.userSettings?.[clientType] || {};
  const currentMappings = currentSettings.inputMappings || {};

  user.value = {
    ...user.value,
    userSettings: {
      ...user.value.userSettings,
      [clientType]: {
        ...currentSettings,
        inputMappings: {
          ...currentMappings,
          [newMappingKey[clientType]]: newMappingValue[clientType],
        },
      },
    },
  };

  delete newMappingKey[clientType];
  delete newMappingValue[clientType];
};

const handleRemoveMapping = (clientType: string, key: string) => {
  if (!user.value) return;

  const currentSettings = user.value.userSettings?.[clientType] || {};
  const { [key]: removed, ...remainingMappings } = currentSettings.inputMappings || {};

  user.value = {
    ...user.value,
    userSettings: {
      ...user.value.userSettings,
      [clientType]: {
        ...currentSettings,
        inputMappings: remainingMappings,
      },
    },
  };
};

const handleDeleteSetting = (clientType: string) => {
  if (!user.value) return;

  // Update local user object
  const { [clientType]: removed, ...remainingSettings } = user.value.userSettings || {};
  user.value = {
    ...user.value,
    userSettings: remainingSettings,
  };
};

const handleAddRegion = () => {
  if (!user.value || !newRegionId.value.trim()) {
    return;
  }

  // Update local user object (will be saved when Save button is clicked)
  user.value = {
    ...user.value,
    regionRoles: {
      ...user.value.regionRoles,
      [newRegionId.value]: 'PLAYER' as any, // Default role
    },
  };

  newRegionId.value = '';
};

const handleRoleChange = (regionId: string, newRole: string, event: Event) => {
  if (!user.value) return;

  const target = event.target as HTMLInputElement;
  if (!target.checked) {
    // If unchecking, don't do anything (must have one role selected)
    target.checked = true;
    return;
  }

  // Update local user object (will be saved when Save button is clicked)
  user.value = {
    ...user.value,
    regionRoles: {
      ...user.value.regionRoles,
      [regionId]: newRole as any,
    },
  };
};

const handleRemoveRegionRole = (regionId: string) => {
  if (!user.value) return;

  // Update local user object (will be saved when Save button is clicked)
  const { [regionId]: removed, ...remainingRoles } = user.value.regionRoles || {};
  user.value = {
    ...user.value,
    regionRoles: remainingRoles,
  };
};

const handleAddCharacterLimit = () => {
  if (!user.value || !newCharLimitRegionId.value || newCharLimit.value === null) {
    return;
  }

  // Update local user object
  user.value = {
    ...user.value,
    characterLimits: {
      ...user.value.characterLimits,
      [newCharLimitRegionId.value]: newCharLimit.value,
    },
  };

  newCharLimitRegionId.value = '';
  newCharLimit.value = null;
};

const handleCharLimitChange = (regionId: string, event: Event) => {
  if (!user.value) return;

  const target = event.target as HTMLInputElement;
  const newValue = parseInt(target.value);

  if (isNaN(newValue) || newValue < 0) return;

  // Update local user object
  user.value = {
    ...user.value,
    characterLimits: {
      ...user.value.characterLimits,
      [regionId]: newValue,
    },
  };
};

const handleRemoveCharacterLimit = (regionId: string) => {
  if (!user.value) return;

  // Update local user object
  const { [regionId]: removed, ...remainingLimits } = user.value.characterLimits || {};
  user.value = {
    ...user.value,
    characterLimits: remainingLimits,
  };
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadUser();
  loadRegions();
});
</script>
