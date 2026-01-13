<template>
  <TransitionRoot :show="true" as="template">
    <Dialog as="div" class="relative z-50" @close="emit('close')">
      <TransitionChild
        as="template"
        enter="ease-out duration-300"
        enter-from="opacity-0"
        enter-to="opacity-100"
        leave="ease-in duration-200"
        leave-from="opacity-100"
        leave-to="opacity-0"
      >
        <div class="fixed inset-0 bg-black bg-opacity-25" />
      </TransitionChild>

      <div class="fixed inset-0 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-4">
          <TransitionChild
            as="template"
            enter="ease-out duration-300"
            enter-from="opacity-0 scale-95"
            enter-to="opacity-100 scale-100"
            leave="ease-in duration-200"
            leave-from="opacity-100 scale-100"
            leave-to="opacity-0 scale-95"
          >
            <DialogPanel class="w-full max-w-md transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-2xl font-bold mb-4">
                Add Item to Chest
              </DialogTitle>

              <!-- Error Alert -->
              <div v-if="error" class="alert alert-error mb-4">
                <svg class="stroke-current flex-shrink-0 h-5 w-5" fill="none" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>{{ error }}</span>
              </div>

              <div class="space-y-4">
                <!-- Item Info -->
                <div class="p-4 bg-base-200 rounded">
                  <div class="text-sm font-semibold mb-2">Item</div>
                  <div class="font-semibold">{{ item.name }}</div>
                  <div class="text-xs font-mono text-base-content/60">{{ item.itemId }}</div>
                  <div v-if="item.texture" class="text-xs text-base-content/50 mt-1">{{ item.texture }}</div>
                </div>

                <!-- Chest Info -->
                <div class="p-4 bg-base-200 rounded">
                  <div class="text-sm font-semibold mb-2">Target Chest</div>
                  <div class="font-semibold">{{ chest.displayName || chest.name }}</div>
                  <div class="text-xs font-mono text-base-content/60">{{ chest.name }}</div>
                </div>

                <!-- Amount -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Amount *</span>
                  </label>
                  <input
                    v-model.number="amount"
                    type="number"
                    min="1"
                    class="input input-bordered"
                    placeholder="Enter amount..."
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">How many items to add</span>
                  </label>
                </div>

                <!-- Optional: Custom Name -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">Custom Name (optional)</span>
                  </label>
                  <input
                    v-model="customName"
                    type="text"
                    class="input input-bordered"
                    placeholder="Override item name..."
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Leave empty to use default name</span>
                  </label>
                </div>
              </div>

              <!-- Actions -->
              <div class="flex justify-end gap-3 mt-6">
                <button
                  class="btn btn-ghost"
                  @click="emit('close')"
                  :disabled="saving"
                >
                  Cancel
                </button>
                <button
                  class="btn btn-primary"
                  @click="handleAdd"
                  :disabled="saving || !isValid"
                >
                  <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                  Add to Chest
                </button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import type { WChest } from '@shared/generated/entities/WChest';
import type { ItemRef } from '@shared/generated/types/ItemRef';
import type { ItemSearchResult } from '@/composables/useItems';
import { chestService } from '@/services/ChestService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('ItemRefDialog');

interface Props {
  item: ItemSearchResult;
  chest: WChest;
  regionId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const amount = ref(1);
const customName = ref('');
const error = ref<string | null>(null);
const saving = ref(false);

/**
 * Validate form
 */
const isValid = computed(() => {
  return amount.value > 0;
});

/**
 * Handle add item to chest
 */
const handleAdd = async () => {
  if (!isValid.value) {
    error.value = 'Please enter a valid amount';
    return;
  }

  saving.value = true;
  error.value = null;

  try {
    const itemRef: ItemRef = {
      itemId: props.item.itemId,
      name: customName.value || props.item.name,
      texture: props.item.texture || '',
      amount: amount.value,
    };

    await chestService.addItem(props.regionId, props.chest.name, itemRef);
    logger.info('Added item to chest', {
      regionId: props.regionId,
      chestName: props.chest.name,
      itemId: props.item.itemId,
      amount: amount.value
    });

    emit('saved');
  } catch (err) {
    error.value = 'Failed to add item to chest';
    logger.error('Failed to add item to chest', {
      regionId: props.regionId,
      chestName: props.chest.name,
      itemId: props.item.itemId
    }, err as Error);
  } finally {
    saving.value = false;
  }
};
</script>
