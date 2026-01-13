<template>
  <div class="p-6 space-y-6">
    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="alert alert-error">
      <span>{{ error }}</span>
    </div>

    <!-- Editor Form -->
    <div v-else-if="localItem" class="space-y-6">
      <!-- Item ID -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Item ID</span>
          <span v-if="!isNew" class="label-text-alt text-xs opacity-50">Read-only</span>
        </label>
        <input
          v-model="localItem.id"
          type="text"
          class="input input-bordered"
          :disabled="!isNew"
          placeholder="item_id"
        />
      </div>

      <!-- Item Type ID -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Item Type</span>
        </label>
        <input
          v-model="localItem.itemType"
          type="text"
          class="input input-bordered"
          placeholder="e.g., sword, wand, potion"
          :disabled="!isNew"
        />
        <label class="label">
          <span class="label-text-alt text-xs">{{ isNew ? 'References ItemType definition' : 'Cannot be changed' }}</span>
        </label>
      </div>

      <!-- Display Name -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Display Name</span>
        </label>
        <input
          v-model="localItem.name"
          type="text"
          class="input input-bordered"
          placeholder="Item name (optional, uses ItemType.name if empty)"
        />
      </div>

      <!-- Description -->
      <div class="form-control">
        <label class="label">
          <span class="label-text font-semibold">Description</span>
        </label>
        <textarea
          v-model="localItem.description"
          class="textarea textarea-bordered"
          rows="2"
          placeholder="Item description (optional)"
        ></textarea>
      </div>

      <!-- Modifier Overrides -->
      <div class="divider">Visual Modifier (Overrides)</div>
      <div class="space-y-4">
        <div class="form-control">
          <label class="label">
            <span class="label-text font-semibold">Texture Override</span>
          </label>
          <input
            v-model="modifierTexture"
            type="text"
            class="input input-bordered"
            placeholder="Optional texture override (uses ItemType default if empty)"
          />
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="form-control">
            <label class="label">
              <span class="label-text">Scale X</span>
            </label>
            <input
              v-model.number="modifierScaleX"
              type="number"
              step="0.1"
              class="input input-bordered"
              placeholder="ItemType default"
            />
          </div>

          <div class="form-control">
            <label class="label">
              <span class="label-text">Scale Y</span>
            </label>
            <input
              v-model.number="modifierScaleY"
              type="number"
              step="0.1"
              class="input input-bordered"
              placeholder="ItemType default"
            />
          </div>
        </div>

        <div class="form-control">
          <label class="label">
            <span class="label-text">Pose Override</span>
          </label>
          <input
            v-model="modifierPose"
            type="text"
            class="input input-bordered"
            placeholder="e.g., attack, use, drink"
          />
        </div>
      </div>

      <!-- OnUseEffect -->
      <div class="divider">Scrawl Effect (onUseEffect)</div>
      <ScriptActionEditor
        v-model="modifierOnUseEffect"
      />

      <!-- Actions -->
      <div class="flex gap-2 pt-4">
        <button class="btn btn-primary" @click="save">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          Save
        </button>
        <button class="btn btn-outline btn-sm" @click="showJsonEditor = true">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4" />
          </svg>
          Source
        </button>
        <button class="btn btn-ghost" @click="$emit('close')">
          Cancel
        </button>
        <div class="flex-1"></div>
        <button v-if="!isNew" class="btn btn-error" @click="confirmDelete">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
          Delete
        </button>
      </div>
    </div>

    <!-- JSON Editor Dialog -->
    <JsonEditorDialog
      v-model:is-open="showJsonEditor"
      :model-value="localItem"
      @apply="handleJsonApply"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import type { Item } from '@nimbus/shared';
import { ItemApiService } from '../services/itemApiService';
import ScriptActionEditor from '../components/ScriptActionEditor.vue';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import { useWorld } from '@/composables/useWorld';

// Alias for backward compatibility
type ItemData = Item;

const props = defineProps<{
  itemId: string;
  isNew: boolean;
}>();

const { currentWorldId, loadWorlds } = useWorld();

const emit = defineEmits<{
  save: [];
  close: [];
  delete: [];
}>();

const loading = ref(false);
const error = ref<string | null>(null);
const localItem = ref<ItemData | null>(null);
const editableItemId = ref(props.itemId);
const showJsonEditor = ref(false);

// Computed properties for modifier fields
const modifierTexture = computed({
  get: () => localItem.value?.modifier?.texture || '',
  set: (value: string) => {
    if (localItem.value) {
      if (!localItem.value.modifier) localItem.value.modifier = {};
      localItem.value.modifier.texture = value || undefined;
    }
  },
});

const modifierScaleX = computed({
  get: () => localItem.value?.modifier?.scaleX,
  set: (value: number | undefined) => {
    if (localItem.value) {
      if (!localItem.value.modifier) localItem.value.modifier = {};
      localItem.value.modifier.scaleX = value;
    }
  },
});

const modifierScaleY = computed({
  get: () => localItem.value?.modifier?.scaleY,
  set: (value: number | undefined) => {
    if (localItem.value) {
      if (!localItem.value.modifier) localItem.value.modifier = {};
      localItem.value.modifier.scaleY = value;
    }
  },
});

const modifierPose = computed({
  get: () => localItem.value?.modifier?.pose || '',
  set: (value: string) => {
    if (localItem.value) {
      if (!localItem.value.modifier) localItem.value.modifier = {};
      localItem.value.modifier.pose = value || undefined;
    }
  },
});

const modifierOnUseEffect = computed({
  get: () => localItem.value?.modifier?.onUseEffect,
  set: (value: any) => {
    if (localItem.value) {
      if (!localItem.value.modifier) localItem.value.modifier = {};
      localItem.value.modifier.onUseEffect = value;
    }
  },
});

async function loadItem() {
  if (props.isNew) {
    // Create new item template
    localItem.value = {
      id: 'new_item_' + Date.now(),
      itemType: '',
      name: 'New Item',
      description: '',
      modifier: {
        texture: '',
        scaleX: 0.5,
        scaleY: 0.5,
        pose: 'use',
      },
      parameters: {},
    };
    return;
  }

  if (!currentWorldId.value) {
    error.value = 'No world selected';
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    const serverItem = await ItemApiService.getItem(props.itemId, currentWorldId.value);
    if (!serverItem) {
      error.value = 'Item not found';
      return;
    }

    // Extract Item from ServerItem
    // ServerItem has structure: { item: Item, itemBlockRef?: ItemBlockRef }
    const itemData = (serverItem as any).item || serverItem;
    localItem.value = itemData;

    console.log('Item loaded:', itemData);
  } catch (e: any) {
    error.value = e.message || 'Failed to load item';
    console.error('Failed to load item:', e);
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!localItem.value || !currentWorldId.value) return;

  loading.value = true;
  error.value = null;

  try {
    if (props.isNew) {
      await ItemApiService.createItem(localItem.value, currentWorldId.value);
    } else {
      await ItemApiService.updateItem(localItem.value.id, localItem.value, currentWorldId.value);
    }
    emit('save');
  } catch (e: any) {
    error.value = e.message || 'Failed to save item';
    console.error('Failed to save item:', e);
  } finally {
    loading.value = false;
  }
}

async function confirmDelete() {
  if (!confirm(`Delete item "${props.itemId}"?`)) {
    return;
  }

  if (!currentWorldId.value) {
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    await ItemApiService.deleteItem(props.itemId, currentWorldId.value);
    emit('delete');
  } catch (e: any) {
    error.value = e.message || 'Failed to delete item';
    console.error('Failed to delete item:', e);
  } finally {
    loading.value = false;
  }
}

function handleJsonApply(updatedItem: ItemData) {
  localItem.value = updatedItem;
  showJsonEditor.value = false;
}

watch(() => props.itemId, () => {
  loadItem();
});

onMounted(() => {
  // Load worlds with regionOnly filter for item editor
  loadWorlds('regionOnly');
  loadItem();
});
</script>
