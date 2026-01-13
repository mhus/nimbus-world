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
              <input
                v-model="formData.userId"
                type="text"
                placeholder="Enter user ID"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
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
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { characterService, type Character } from '../services/CharacterService';

const props = defineProps<{
  character: Character | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentRegionId } = useRegion();

const isNew = computed(() => props.character === 'new');

const character = ref<Character | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  userId: '',
  name: '',
  display: '',
});

const newSkillName = ref('');
const newSkillLevel = ref(1);

const loadCharacter = () => {
  if (isNew.value) {
    character.value = null;
    formData.value = {
      userId: '',
      name: '',
      display: '',
    };
    return;
  }

  // Load from props
  character.value = props.character as Character;
  formData.value = {
    userId: character.value.userId,
    name: character.value.name,
    display: character.value.display,
  };
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
      // Update existing character
      await characterService.updateCharacter(
        currentRegionId.value,
        character.value.id,
        character.value.userId,
        character.value.name,
        {
          userId: character.value.userId,
          name: character.value.name,
          display: formData.value.display,
        }
      );
      successMessage.value = 'Character updated successfully';
      // Reload character data
      character.value.display = formData.value.display;
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

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadCharacter();
});
</script>
