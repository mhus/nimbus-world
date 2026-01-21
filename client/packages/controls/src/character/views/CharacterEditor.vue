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
      <h2 class="text-2xl font-bold">
        {{ isNew ? 'Create New Character' : 'Edit Character' }}
      </h2>
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
            <!-- User ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">User ID</span>
              </label>
              <select
                v-if="isNew"
                v-model="formData.userId"
                class="select select-bordered w-full"
                required
              >
                <option value="">Select a user...</option>
                <option v-for="user in users" :key="user.username" :value="user.username">
                  {{ user.username }} ({{ user.publicData?.displayName || 'No display name' }})
                </option>
              </select>
              <input
                v-else
                v-model="formData.userId"
                type="text"
                class="input input-bordered w-full"
                disabled
              />
              <label v-if="isNew && loadingUsers" class="label">
                <span class="label-text-alt">Loading users...</span>
              </label>
            </div>

            <!-- Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Name</span>
              </label>
              <input
                v-model="formData.name"
                type="text"
                placeholder="Enter character name"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique name for this character</span>
              </label>
            </div>

            <!-- Display Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Display Name</span>
              </label>
              <input
                v-model="formData.display"
                type="text"
                placeholder="Enter display name"
                class="input input-bordered w-full"
                required
              />
              <label class="label">
                <span class="label-text-alt">Name shown to other players</span>
              </label>
            </div>

            <!-- Action Buttons -->
            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Shortcuts Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between mb-4">
            <h3 class="card-title">Shortcuts (JSON)</h3>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="showShortcutsEditor = !showShortcutsEditor"
            >
              {{ showShortcutsEditor ? 'Hide' : 'Show' }} Editor
            </button>
          </div>

          <div v-if="showShortcutsEditor" class="space-y-2">
            <textarea
              v-model="shortcutsJson"
              class="textarea textarea-bordered w-full font-mono text-sm"
              rows="10"
              placeholder='{"key0": {"action": "...", ...}}'
            ></textarea>
            <div class="flex gap-2">
              <button
                type="button"
                class="btn btn-sm btn-primary"
                @click="handleSaveShortcuts"
              >
                Apply Changes
              </button>
              <button
                type="button"
                class="btn btn-sm btn-ghost"
                @click="handleResetShortcuts"
              >
                Reset
              </button>
            </div>
            <div v-if="shortcutsError" class="alert alert-error text-sm">
              <span>{{ shortcutsError }}</span>
            </div>
          </div>

          <div v-else class="text-sm text-base-content/70">
            Click "Show Editor" to edit shortcuts as JSON
          </div>
        </div>
      </div>

      <!-- Editor Shortcuts Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between mb-4">
            <h3 class="card-title">Editor Shortcuts (JSON)</h3>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="showEditorShortcutsEditor = !showEditorShortcutsEditor"
            >
              {{ showEditorShortcutsEditor ? 'Hide' : 'Show' }} Editor
            </button>
          </div>

          <div v-if="showEditorShortcutsEditor" class="space-y-2">
            <textarea
              v-model="editorShortcutsJson"
              class="textarea textarea-bordered w-full font-mono text-sm"
              rows="10"
              placeholder='{"key0": {"action": "...", ...}}'
            ></textarea>
            <div class="flex gap-2">
              <button
                type="button"
                class="btn btn-sm btn-primary"
                @click="handleSaveEditorShortcuts"
              >
                Apply Changes
              </button>
              <button
                type="button"
                class="btn btn-sm btn-ghost"
                @click="handleResetEditorShortcuts"
              >
                Reset
              </button>
            </div>
            <div v-if="editorShortcutsError" class="alert alert-error text-sm">
              <span>{{ editorShortcutsError }}</span>
            </div>
          </div>

          <div v-else class="text-sm text-base-content/70">
            Click "Show Editor" to edit editor shortcuts as JSON
          </div>
        </div>
      </div>

      <!-- Third Person Model Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Third Person Model</h3>

          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Entity Model ID</span>
            </label>
            <div class="flex gap-2">
              <input
                v-model="thirdPersonModelId"
                type="text"
                placeholder="e.g., farmer1, warrior2, ..."
                class="input input-bordered flex-1"
                @input="handleModelIdChange"
              />
              <button
                type="button"
                class="btn btn-outline"
                @click="openModelSelector"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
                Select Model
              </button>
            </div>
            <label class="label">
              <span class="label-text-alt">Enter entity model ID or click "Select Model" to browse available entity models</span>
            </label>
          </div>

          <div v-if="thirdPersonModelId" class="mt-4">
            <div class="text-sm text-base-content/70 mb-2">Current Model:</div>
            <div class="bg-base-200 p-4 rounded-lg">
              <div class="font-mono text-sm">{{ thirdPersonModelId }}</div>
            </div>
          </div>

          <div v-else class="text-center py-4">
            <p class="text-base-content/50 text-sm">No third person model configured</p>
          </div>
        </div>
      </div>

      <!-- Attributes Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Attributes</h3>

          <!-- Add New Attribute -->
          <div class="flex gap-2 mb-4">
            <input
              v-model="newAttributeKey"
              type="text"
              placeholder="Attribute key"
              class="input input-bordered flex-1"
            />
            <input
              v-model="newAttributeValue"
              type="text"
              placeholder="Attribute value"
              class="input input-bordered flex-1"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddAttribute"
              :disabled="!newAttributeKey || !newAttributeValue"
            >
              Add Attribute
            </button>
          </div>

          <!-- Existing Attributes -->
          <div v-if="character?.attributes && Object.keys(character.attributes).length > 0" class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Value</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(value, key) in character.attributes" :key="key">
                  <td class="font-mono">{{ key }}</td>
                  <td>
                    <input
                      type="text"
                      :value="value"
                      @input="handleAttributeChange(key, $event)"
                      class="input input-bordered input-sm w-full"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-sm btn-error"
                      @click="handleRemoveAttribute(key)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No attributes configured</p>
            <p class="text-base-content/50 text-sm mt-2">Add an attribute by entering a key and value above</p>
          </div>
        </div>
      </div>

      <!-- Skills Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Skills</h3>

          <!-- Add New Skill -->
          <div class="flex gap-2 mb-4">
            <input
              v-model="newSkillName"
              type="text"
              placeholder="Skill name (e.g., mining, combat)"
              class="input input-bordered flex-1"
            />
            <input
              v-model.number="newSkillLevel"
              type="number"
              min="0"
              placeholder="Level"
              class="input input-bordered w-24"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddSkill"
              :disabled="!newSkillName.trim()"
            >
              Add Skill
            </button>
          </div>

          <!-- Existing Skills -->
          <div v-if="character?.skills && Object.keys(character.skills).length > 0" class="space-y-2">
            <div
              v-for="(level, skillName) in character.skills"
              :key="skillName"
              class="flex items-center gap-4 p-3 border border-base-300 rounded-lg"
            >
              <span class="font-medium flex-1">{{ skillName }}</span>
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="btn btn-sm btn-ghost"
                  @click="handleIncrementSkill(skillName, -1)"
                  :disabled="saving"
                >
                  -
                </button>
                <span class="badge badge-lg">Level {{ level }}</span>
                <button
                  type="button"
                  class="btn btn-sm btn-ghost"
                  @click="handleIncrementSkill(skillName, 1)"
                  :disabled="saving"
                >
                  +
                </button>
              </div>
              <button
                type="button"
                class="btn btn-sm btn-error"
                @click="handleSetSkillLevel(skillName, 0)"
                :disabled="saving"
              >
                Remove
              </button>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No skills yet</p>
            <p class="text-base-content/50 text-sm mt-2">Add a skill using the form above</p>
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

  <!-- Entity Model Selector Dialog for Third Person Model -->
  <EntityModelSelectorDialog
    v-if="isModelSelectorOpen && assetWorldId"
    :world-id="assetWorldId"
    :current-model-id="thirdPersonModelId"
    @close="closeModelSelector"
    @select="handleModelSelected"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { characterService, type RCharacter } from '../services/CharacterService';
import { userService, type RUser } from '../../user/services/UserService';
import EntityModelSelectorDialog from '@components/EntityModelSelectorDialog.vue';

const props = defineProps<{
  character: RCharacter | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentRegionId } = useRegion();

// Dynamic worldId for asset picker based on current region
const assetWorldId = computed(() => currentRegionId.value ? `@region:${currentRegionId.value}` : '@shared:n');

const isNew = computed(() => props.character === 'new');

const character = ref<RCharacter | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const users = ref<RUser[]>([]);
const loadingUsers = ref(false);

const formData = ref({
  userId: '',
  name: '',
  display: '',
});

const newSkillName = ref('');
const newSkillLevel = ref(1);
const newAttributeKey = ref('');
const newAttributeValue = ref('');

// Shortcuts JSON editors
const showShortcutsEditor = ref(false);
const showEditorShortcutsEditor = ref(false);
const shortcutsJson = ref('');
const editorShortcutsJson = ref('');
const shortcutsError = ref<string | null>(null);
const editorShortcutsError = ref<string | null>(null);

// Third person model
const thirdPersonModelId = ref('');
const isModelSelectorOpen = ref(false);

const loadUsers = async () => {
  loadingUsers.value = true;
  try {
    users.value = await userService.listUsers();
  } catch (e) {
    console.error('[CharacterEditor] Failed to load users:', e);
    error.value = 'Failed to load user list';
  } finally {
    loadingUsers.value = false;
  }
};

const loadCharacter = () => {
  if (isNew.value) {
    character.value = null;
    formData.value = {
      userId: '',
      name: '',
      display: '',
    };
    shortcutsJson.value = '';
    editorShortcutsJson.value = '';
    thirdPersonModelId.value = '';
    return;
  }

  // Load from props
  character.value = props.character as RCharacter;
  formData.value = {
    userId: character.value.userId,
    name: character.value.name,
    display: character.value.publicData?.title || character.value.name,
  };

  // Initialize JSON strings for shortcuts
  shortcutsJson.value = JSON.stringify(character.value.publicData?.shortcuts || {}, null, 2);
  editorShortcutsJson.value = JSON.stringify(character.value.publicData?.editorShortcuts || {}, null, 2);

  // Initialize third person model
  thirdPersonModelId.value = character.value.publicData?.thirdPersonModelId || '';
};

const handleSave = async () => {
  if (!currentRegionId.value) {
    error.value = 'No region selected';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    if (isNew.value) {
      await characterService.createCharacter(currentRegionId.value, {
        userId: formData.value.userId,
        name: formData.value.name,
        display: formData.value.display,
      });
      successMessage.value = 'Character created successfully';
    } else if (character.value) {
      // Update publicData with current thirdPersonModelId
      const updatedPublicData = {
        ...character.value.publicData,
        thirdPersonModelId: thirdPersonModelId.value || undefined,
      };

      // Update existing character with full DTO
      const updatedChar = await characterService.updateCharacter(
        currentRegionId.value,
        character.value.id,
        character.value.userId,
        character.value.name,
        {
          userId: character.value.userId,
          name: character.value.name,
          display: formData.value.display,
          publicData: updatedPublicData,
          backpack: character.value.backpack,
          skills: character.value.skills,
          attributes: character.value.attributes,
        }
      );
      successMessage.value = 'Character updated successfully';
      // Update local character with response
      character.value = updatedChar;
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save character';
    console.error('Failed to save character:', e);
  } finally {
    saving.value = false;
  }
};

const handleAddSkill = async () => {
  if (!newSkillName.value.trim() || !currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.setSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      newSkillName.value.trim(),
      newSkillLevel.value
    );
    successMessage.value = `Skill "${newSkillName.value}" added successfully`;
    newSkillName.value = '';
    newSkillLevel.value = 1;
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to add skill';
    console.error('Failed to add skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleSetSkillLevel = async (skillName: string, level: number) => {
  if (!currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.setSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      skillName,
      level
    );
    if (level === 0) {
      successMessage.value = `Skill "${skillName}" removed successfully`;
    } else {
      successMessage.value = `Skill "${skillName}" updated to level ${level}`;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to update skill';
    console.error('Failed to update skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleIncrementSkill = async (skillName: string, delta: number) => {
  if (!currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.incrementSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      skillName,
      delta
    );
    successMessage.value = `Skill "${skillName}" ${delta > 0 ? 'increased' : 'decreased'}`;
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to increment skill';
    console.error('Failed to increment skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleAddAttribute = () => {
  if (!newAttributeKey.value.trim() || !newAttributeValue.value.trim() || !character.value) {
    return;
  }

  // Initialize attributes if not present
  if (!character.value.attributes) {
    character.value = {
      ...character.value,
      attributes: {},
    };
  }

  // Update local character object
  character.value = {
    ...character.value,
    attributes: {
      ...character.value.attributes,
      [newAttributeKey.value]: newAttributeValue.value,
    },
  };

  newAttributeKey.value = '';
  newAttributeValue.value = '';
};

const handleAttributeChange = (key: string, event: Event) => {
  if (!character.value) return;

  const target = event.target as HTMLInputElement;

  // Update local character object
  character.value = {
    ...character.value,
    attributes: {
      ...character.value.attributes,
      [key]: target.value,
    },
  };
};

const handleRemoveAttribute = (key: string) => {
  if (!character.value) return;

  // Update local character object
  const { [key]: removed, ...remainingAttributes } = character.value.attributes || {};
  character.value = {
    ...character.value,
    attributes: remainingAttributes,
  };
};

const handleSaveShortcuts = () => {
  if (!character.value) return;

  shortcutsError.value = null;

  try {
    // Parse JSON
    const parsed = JSON.parse(shortcutsJson.value);

    // Update character
    if (!character.value.publicData) {
      character.value = {
        ...character.value,
        publicData: {
          playerId: character.value.userId,
          title: character.value.publicData?.title || character.value.name,
          shortcuts: parsed,
          stateValues: character.value.publicData?.stateValues || {},
        },
      };
    } else {
      character.value = {
        ...character.value,
        publicData: {
          ...character.value.publicData,
          shortcuts: parsed,
        },
      };
    }

    successMessage.value = 'Shortcuts updated (remember to save the character)';
  } catch (e) {
    shortcutsError.value = e instanceof Error ? e.message : 'Invalid JSON';
  }
};

const handleResetShortcuts = () => {
  if (!character.value) return;
  shortcutsJson.value = JSON.stringify(character.value.publicData?.shortcuts || {}, null, 2);
  shortcutsError.value = null;
};

const handleSaveEditorShortcuts = () => {
  if (!character.value) return;

  editorShortcutsError.value = null;

  try {
    // Parse JSON
    const parsed = JSON.parse(editorShortcutsJson.value);

    // Update character
    if (!character.value.publicData) {
      character.value = {
        ...character.value,
        publicData: {
          playerId: character.value.userId,
          title: character.value.publicData?.title || character.value.name,
          editorShortcuts: parsed,
          stateValues: character.value.publicData?.stateValues || {},
        },
      };
    } else {
      character.value = {
        ...character.value,
        publicData: {
          ...character.value.publicData,
          editorShortcuts: parsed,
        },
      };
    }

    successMessage.value = 'Editor shortcuts updated (remember to save the character)';
  } catch (e) {
    editorShortcutsError.value = e instanceof Error ? e.message : 'Invalid JSON';
  }
};

const handleResetEditorShortcuts = () => {
  if (!character.value) return;
  editorShortcutsJson.value = JSON.stringify(character.value.publicData?.editorShortcuts || {}, null, 2);
  editorShortcutsError.value = null;
};

const handleModelIdChange = () => {
  // Update character immediately for local state
  if (character.value && character.value.publicData) {
    character.value = {
      ...character.value,
      publicData: {
        ...character.value.publicData,
        thirdPersonModelId: thirdPersonModelId.value || undefined,
      },
    };
  }
};

const openModelSelector = () => {
  isModelSelectorOpen.value = true;
};

const closeModelSelector = () => {
  isModelSelectorOpen.value = false;
};

const handleModelSelected = (modelId: string) => {
  thirdPersonModelId.value = modelId;
  handleModelIdChange();
  closeModelSelector();
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadCharacter();
  if (isNew.value) {
    loadUsers();
  }
});
</script>
