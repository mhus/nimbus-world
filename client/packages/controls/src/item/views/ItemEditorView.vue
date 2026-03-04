<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button class="btn btn-ghost gap-2" @click="$emit('close')">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to List
        </button>
      </div>
      <h2 class="text-2xl font-bold">
        {{ isNew ? 'Create New Item' : 'Edit Item' }}
      </h2>
    </div>

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
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Item Details</h3>

          <!-- Item ID (name) -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-semibold">Item ID</span>
              <span v-if="!isNew" class="label-text-alt text-xs opacity-50">Read-only</span>
            </label>
            <input
              v-model="localItem.name"
              type="text"
              class="input input-bordered"
              :disabled="!isNew"
              placeholder="item_name"
            />
          </div>

          <!-- Item Type (category) -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-semibold">Item Type</span>
            </label>
            <input
              v-model="localItem.itemType"
              type="text"
              class="input input-bordered"
              placeholder="e.g., sword, wand, potion"
            />
            <label class="label">
              <span class="label-text-alt text-xs">Category identifier for grouping items</span>
            </label>
          </div>

          <!-- Type (enum) -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-semibold">Category</span>
            </label>
            <select v-model="localItem.type" class="select select-bordered">
              <option value="">-- none --</option>
              <option value="weapon">weapon</option>
              <option value="tool">tool</option>
              <option value="food">food</option>
              <option value="potion">potion</option>
              <option value="armor">armor</option>
              <option value="material">material</option>
            </select>
          </div>

          <!-- Display Name -->
          <div class="form-control">
            <label class="label">
              <span class="label-text font-semibold">Display Name</span>
            </label>
            <input
              v-model="localItem.title"
              type="text"
              class="input input-bordered"
              placeholder="Display title"
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
              placeholder="Item description"
            ></textarea>
          </div>

          <!-- Visual Properties -->
          <div class="divider">Visual Properties</div>
          <div class="space-y-4">
            <div class="form-control">
              <label class="label">
                <span class="label-text font-semibold">Texture</span>
              </label>
              <input
                v-model="localItem.texture"
                type="text"
                class="input input-bordered"
                placeholder="e.g., items/sword.png"
              />
            </div>

            <div class="grid grid-cols-2 gap-4">
              <div class="form-control">
                <label class="label">
                  <span class="label-text">Scale X</span>
                </label>
                <input
                  v-model.number="localItem.scaleX"
                  type="number"
                  step="0.1"
                  class="input input-bordered"
                  placeholder="0.5"
                />
              </div>

              <div class="form-control">
                <label class="label">
                  <span class="label-text">Scale Y</span>
                </label>
                <input
                  v-model.number="localItem.scaleY"
                  type="number"
                  step="0.1"
                  class="input input-bordered"
                  placeholder="0.5"
                />
              </div>
            </div>

            <div class="form-control">
              <label class="label">
                <span class="label-text">Color Tint</span>
              </label>
              <input
                v-model="localItem.color"
                type="text"
                class="input input-bordered"
                placeholder="e.g., #ff0000"
              />
            </div>
          </div>

          <!-- Behavior -->
          <div class="divider">Behavior</div>
          <div class="space-y-4">
            <div class="form-control">
              <label class="label">
                <span class="label-text">Pose</span>
              </label>
              <input
                v-model="localItem.pose"
                type="text"
                class="input input-bordered"
                placeholder="e.g., attack, use, drink, cast"
              />
            </div>

            <div class="form-control">
              <label class="label">
                <span class="label-text">Action Targeting</span>
              </label>
              <select v-model="localItem.actionTargeting" class="select select-bordered">
                <option value="">ALL (default)</option>
                <option value="ENTITY">ENTITY</option>
                <option value="BLOCK">BLOCK</option>
                <option value="BOTH">BOTH</option>
                <option value="GROUND">GROUND</option>
                <option value="ALL">ALL</option>
              </select>
            </div>

            <div class="flex gap-4">
              <label class="label cursor-pointer gap-2">
                <input
                  type="checkbox"
                  class="checkbox checkbox-sm"
                  :checked="localItem.exclusive === true"
                  @change="localItem.exclusive = ($event.target as HTMLInputElement).checked || undefined"
                />
                <span class="label-text">Exclusive (blocks other shortcuts while active)</span>
              </label>

              <label class="label cursor-pointer gap-2">
                <input
                  type="checkbox"
                  class="checkbox checkbox-sm"
                  :checked="localItem.generic === true"
                  @change="localItem.generic = ($event.target as HTMLInputElement).checked || undefined"
                />
                <span class="label-text">Generic (stackable in inventory)</span>
              </label>
            </div>
          </div>

          <!-- OnUseEffect -->
          <div class="divider">Scrawl Effect (onUseEffect)</div>
          <ScriptActionEditor
            v-model="localItem.onUseEffect"
          />

          <!-- Wearable Slots -->
          <div class="divider">Wearable Slots</div>
          <div class="space-y-2">
            <label class="label">
              <span class="label-text font-semibold">Allowed wearing slots</span>
              <span class="label-text-alt text-xs">None selected = not wearable</span>
            </label>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="slot in ALL_WEARABLE_SLOTS"
                :key="slot"
                type="button"
                class="btn btn-sm"
                :class="isWearableSlotActive(slot) ? 'btn-primary' : 'btn-outline'"
                @click="toggleWearableSlot(slot)"
              >
                {{ slot }}
              </button>
            </div>
          </div>

          <!-- Public Parameters -->
          <div class="divider">Parameters (Public)</div>
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="label-text font-semibold">Key-Value parameters sent to clients</span>
              <button type="button" class="btn btn-ghost btn-sm" @click="addPublicParam">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                Add
              </button>
            </div>
            <div v-if="publicParamEntries.length === 0" class="text-sm text-base-content/50 py-1">
              No public parameters (except wearableSlots above)
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="(entry, index) in publicParamEntries"
                :key="'pub-' + index"
                class="flex items-center gap-2"
              >
                <input v-model="entry.key" type="text" placeholder="Key" class="input input-bordered input-sm flex-1" />
                <input v-model="entry.value" type="text" placeholder="Value" class="input input-bordered input-sm flex-[2]" />
                <button type="button" class="btn btn-ghost btn-sm btn-square text-error" @click="removePublicParam(index)">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- Server Parameters -->
          <div class="divider">Server Parameters</div>
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="label-text font-semibold">Server-side parameters (not sent to clients)</span>
              <button type="button" class="btn btn-ghost btn-sm" @click="addServerParam">
                <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
                </svg>
                Add
              </button>
            </div>
            <div v-if="serverParamEntries.length === 0" class="text-sm text-base-content/50 py-1">
              No server parameters defined
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="(entry, index) in serverParamEntries"
                :key="'srv-' + index"
                class="flex items-center gap-2"
              >
                <input v-model="entry.key" type="text" placeholder="Key" class="input input-bordered input-sm flex-1" />
                <input v-model="entry.value" type="text" placeholder="Value" class="input input-bordered input-sm flex-[2]" />
                <button type="button" class="btn btn-ghost btn-sm btn-square text-error" @click="removeServerParam(index)">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

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
            <div class="flex-1"></div>
            <button v-if="!isNew" class="btn btn-error" @click="confirmDelete">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              Delete
            </button>
          </div>
        </div>
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
import { ref, onMounted, watch } from 'vue';
import type { Item } from '@nimbus/shared';
import { ItemApiService } from '../services/itemApiService';
import ScriptActionEditor from '../components/ScriptActionEditor.vue';
import JsonEditorDialog from '@components/JsonEditorDialog.vue';
import { useWorld } from '@/composables/useWorld';

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
const localItem = ref<Item | null>(null);
const showJsonEditor = ref(false);

// Key-value entries for publicData.parameters (excluding wearableSlots which has its own UI)
const publicParamEntries = ref<{ key: string; value: string }[]>([]);
// Key-value entries for WItem.server
const serverParamEntries = ref<{ key: string; value: string }[]>([]);

const addPublicParam = () => publicParamEntries.value.push({ key: '', value: '' });
const removePublicParam = (index: number) => publicParamEntries.value.splice(index, 1);
const addServerParam = () => serverParamEntries.value.push({ key: '', value: '' });
const removeServerParam = (index: number) => serverParamEntries.value.splice(index, 1);

/** Convert key-value entries back to a map, filtering empty keys */
const entriesToMap = (entries: { key: string; value: string }[]): Record<string, string> => {
  const map: Record<string, string> = {};
  for (const e of entries) {
    if (e.key.trim()) map[e.key.trim()] = e.value;
  }
  return map;
};

/** Load key-value entries from a map */
const mapToEntries = (map: Record<string, any> | null | undefined, excludeKeys: string[] = []): { key: string; value: string }[] => {
  if (!map) return [];
  return Object.entries(map)
    .filter(([k]) => !excludeKeys.includes(k))
    .map(([key, value]) => ({ key, value: String(value ?? '') }));
};

const ALL_WEARABLE_SLOTS = ['HEAD', 'NECK', 'BODY', 'ARMS', 'LEGS', 'FEET', 'RING', 'HAND'] as const;

const isWearableSlotActive = (slot: string): boolean => {
  const slots = localItem.value?.parameters?.wearableSlots;
  if (!Array.isArray(slots)) return false;
  return slots.includes(slot);
};

const toggleWearableSlot = (slot: string) => {
  if (!localItem.value) return;
  if (!localItem.value.parameters) localItem.value.parameters = {};
  const slots: string[] = Array.isArray(localItem.value.parameters.wearableSlots)
    ? [...localItem.value.parameters.wearableSlots]
    : [];
  const idx = slots.indexOf(slot);
  if (idx >= 0) {
    slots.splice(idx, 1);
  } else {
    slots.push(slot);
  }
  localItem.value.parameters.wearableSlots = slots.length > 0 ? slots : undefined;
};

async function loadItem() {
  if (props.isNew) {
    localItem.value = {
      name: 'new_item_' + Date.now(),
      itemType: '',
      title: 'New Item',
      description: '',
      texture: '',
      scaleX: 0.5,
      scaleY: 0.5,
      parameters: {},
    };
    publicParamEntries.value = [];
    serverParamEntries.value = [];
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

    // Extract Item from WItem wrapper
    // WItem has structure: { id: mongoId, itemId: string, publicData: Item, server: Map }
    const itemData = (serverItem as any).publicData || serverItem;
    localItem.value = itemData;

    // Load publicData.parameters (exclude wearableSlots - it has its own UI)
    publicParamEntries.value = mapToEntries(itemData.parameters, ['wearableSlots']);
    // Load WItem.server parameters
    serverParamEntries.value = mapToEntries((serverItem as any).server);

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
    // Merge publicParamEntries back into localItem.parameters (preserve wearableSlots)
    const pubParams = entriesToMap(publicParamEntries.value);
    if (!localItem.value.parameters) localItem.value.parameters = {};
    // Remove old non-wearableSlots keys, then merge new ones
    const wearableSlots = localItem.value.parameters.wearableSlots;
    const mergedParams: Record<string, any> = { ...pubParams };
    if (wearableSlots !== undefined) mergedParams.wearableSlots = wearableSlots;
    localItem.value.parameters = mergedParams;

    const serverMap = entriesToMap(serverParamEntries.value);

    if (props.isNew) {
      await ItemApiService.createItem(localItem.value, currentWorldId.value, serverMap);
    } else {
      await ItemApiService.updateItem(props.itemId, localItem.value, currentWorldId.value, serverMap);
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

function handleJsonApply(updatedItem: Item) {
  localItem.value = updatedItem;
  showJsonEditor.value = false;
}

watch(() => props.itemId, () => {
  loadItem();
});

onMounted(() => {
  loadWorlds('regionOnly');
  loadItem();
});
</script>
