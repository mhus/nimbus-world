<template>
  <div class="space-y-4">
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
        {{ isNew ? 'Create New Entity' : 'Edit Entity' }}
      </h2>
      <div class="flex gap-2">
        <button
          v-if="!isNew && entityData"
          type="button"
          class="btn btn-outline btn-sm"
          @click="showJsonEditor = true"
        >
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
          Source
        </button>
        <button type="button" class="btn btn-ghost" @click="handleBack">
          Cancel
        </button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="handleSave">
          <span v-if="saving" class="loading loading-spinner loading-sm"></span>
          <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
        </button>
      </div>
    </div>

    <!-- Error State -->
    <div v-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Edit Form -->
    <div v-else class="space-y-4">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <div class="space-y-4">
            <!-- Entity ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Entity ID</span>
              </label>
              <input
                v-model="formData.entityId"
                type="text"
                placeholder="Enter unique entity ID"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique identifier for this entity</span>
              </label>
            </div>

            <!-- Model ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Model ID</span>
              </label>
              <input
                v-model="formData.modelId"
                type="text"
                placeholder="Enter entity model ID"
                class="input input-bordered w-full"
                required
              />
              <label class="label">
                <span class="label-text-alt">Reference to entity model definition</span>
              </label>
            </div>

            <!-- Enabled Status -->
            <div v-if="!isNew" class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Enabled</span>
                <input
                  v-model="formData.enabled"
                  type="checkbox"
                  class="toggle toggle-success"
                />
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Entity Properties Card -->
      <div v-if="entityData" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Entity Properties</h3>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <!-- Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Name</span>
              </label>
              <input
                v-model="entityData.name"
                type="text"
                class="input input-bordered input-sm"
                placeholder="Entity display name"
              />
            </div>

            <!-- Movement Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Movement Type</span>
              </label>
              <select v-model="entityData.movementType" class="select select-bordered select-sm">
                <option value="static">Static</option>
                <option value="passive">Passive</option>
                <option value="slow">Slow</option>
                <option value="dynamic">Dynamic</option>
              </select>
            </div>

            <!-- Controlled By -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Controlled By</span>
              </label>
              <select v-model="entityData.controlledBy" class="select select-bordered select-sm">
                <option value="player">Player</option>
                <option value="server">Server</option>
                <option value="ai">AI</option>
                <option value="client">Client</option>
              </select>
            </div>

            <!-- Health Max -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Max Health</span>
              </label>
              <input
                v-model.number="entityData.healthMax"
                type="number"
                class="input input-bordered input-sm"
                placeholder="100"
              />
            </div>
          </div>

          <!-- Checkboxes -->
          <div class="grid grid-cols-2 md:grid-cols-3 gap-3 mt-4">
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.solid" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Solid</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.interactive" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Interactive</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.physics" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Physics</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.clientPhysics" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Client Physics</span>
            </label>
            <label class="label cursor-pointer justify-start gap-2">
              <input v-model="entityData.notifyOnCollision" type="checkbox" class="checkbox checkbox-sm" />
              <span class="label-text text-sm">Notify Collision</span>
            </label>
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

    <!-- JSON Editor Dialog -->
    <JsonEditorDialog
      v-model:is-open="showJsonEditor"
      :model-value="entityData"
      @apply="handleJsonApply"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { entityService, type EntityData } from '../services/EntityService';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';

const props = defineProps<{
  entity: EntityData | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentWorldId, loadWorlds } = useWorld();

const isNew = computed(() => props.entity === 'new');

const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const formData = ref({
  entityId: '',
  modelId: '',
  enabled: true,
});

const entityData = ref<any>(null);
const showJsonEditor = ref(false);

const loadEntity = () => {
  if (isNew.value) {
    formData.value = {
      entityId: '',
      modelId: '',
      enabled: true,
    };
    entityData.value = {
      id: '',
      name: '',
      model: '',
      modelModifier: {},
      movementType: 'static',
      controlledBy: 'server',
      solid: false,
      interactive: false,
      physics: false,
      clientPhysics: false,
      notifyOnCollision: false,
      healthMax: 100,
    };
    return;
  }

  const entity = props.entity as EntityData;
  formData.value = {
    entityId: entity.entityId,
    modelId: entity.modelId,
    enabled: entity.enabled,
  };
  entityData.value = entity.publicData || {};
};

const handleSave = async () => {
  if (!currentWorldId.value) {
    error.value = 'No world selected';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    if (isNew.value) {
      await entityService.createEntity(currentWorldId.value, {
        entityId: formData.value.entityId,
        publicData: entityData.value,
        modelId: formData.value.modelId,
      });
      successMessage.value = 'Entity created successfully';
    } else {
      await entityService.updateEntity(currentWorldId.value, formData.value.entityId, {
        modelId: formData.value.modelId,
        enabled: formData.value.enabled,
        publicData: entityData.value,
      });
      successMessage.value = 'Entity saved successfully';
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save entity';
    console.error('[EntityEditor] Failed to save entity:', e);
  } finally {
    saving.value = false;
  }
};

const handleJsonApply = (jsonData: any) => {
  entityData.value = jsonData;
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  // Load worlds with mainWorldsAndInstances filter for entity editor
  loadWorlds('mainWorldsAndInstances');
  loadEntity();
});
</script>
