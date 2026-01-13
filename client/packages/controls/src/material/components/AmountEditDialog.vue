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
            <DialogPanel class="w-full max-w-sm transform overflow-hidden rounded-2xl bg-base-100 p-6 text-left align-middle shadow-xl transition-all">
              <DialogTitle class="text-xl font-bold mb-4">
                Edit Amount
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
                <div class="p-3 bg-base-200 rounded">
                  <div class="font-semibold">{{ itemRef.name || itemRef.itemId }}</div>
                  <div class="text-xs font-mono text-base-content/60">{{ itemRef.itemId }}</div>
                </div>

                <!-- Current Amount -->
                <div class="text-sm text-base-content/70">
                  Current amount: <span class="font-bold">{{ itemRef.amount }}</span>
                </div>

                <!-- New Amount Input -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text font-semibold">New Amount *</span>
                  </label>
                  <input
                    v-model.number="newAmount"
                    type="number"
                    min="0"
                    class="input input-bordered"
                    placeholder="Enter new amount..."
                    @keyup.enter="handleSave"
                  />
                  <label class="label">
                    <span class="label-text-alt text-base-content/60">Set to 0 to remove item</span>
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
                  @click="handleSave"
                  :disabled="saving || !isValid"
                >
                  <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                  Save
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
import { ref, computed, onMounted } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import type { WChest } from '@shared/generated/entities/WChest';
import type { ItemRef } from '@shared/generated/types/ItemRef';
import { chestService } from '@/services/ChestService';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('AmountEditDialog');

interface Props {
  itemRef: ItemRef;
  chest: WChest;
  regionId: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  close: [];
  saved: [];
}>();

const newAmount = ref(0);
const error = ref<string | null>(null);
const saving = ref(false);

/**
 * Validate form
 */
const isValid = computed(() => {
  return newAmount.value >= 0;
});

/**
 * Handle save
 */
const handleSave = async () => {
  if (!isValid.value) {
    error.value = 'Please enter a valid amount (0 or greater)';
    return;
  }

  saving.value = true;
  error.value = null;

  try {
    if (newAmount.value === 0) {
      // Remove item from chest
      await chestService.removeItem(props.regionId, props.chest.name, props.itemRef.itemId);
      logger.info('Removed item from chest (amount set to 0)', {
        regionId: props.regionId,
        chestName: props.chest.name,
        itemId: props.itemRef.itemId
      });
    } else {
      // Update amount using dedicated endpoint
      await chestService.updateItemAmount(props.regionId, props.chest.name, props.itemRef.itemId, newAmount.value);

      logger.info('Updated item amount in chest', {
        regionId: props.regionId,
        chestName: props.chest.name,
        itemId: props.itemRef.itemId,
        oldAmount: props.itemRef.amount,
        newAmount: newAmount.value
      });
    }

    emit('saved');
  } catch (err) {
    error.value = 'Failed to update amount';
    logger.error('Failed to update amount', {
      regionId: props.regionId,
      chestName: props.chest.name,
      itemId: props.itemRef.itemId
    }, err as Error);
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  newAmount.value = props.itemRef.amount;
});
</script>
