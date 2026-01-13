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
              <DialogTitle class="text-xl font-bold mb-4">
                Search Collections
              </DialogTitle>

              <div class="space-y-4">
                <!-- Search Input -->
                <div class="form-control">
                  <label class="label">
                    <span class="label-text">Collection Name</span>
                  </label>
                  <input
                    v-model="searchQuery"
                    type="text"
                    class="input input-bordered"
                    placeholder="Type to search..."
                    @input="handleSearch"
                    @keyup.enter="handleEnter"
                    autofocus
                  />
                </div>

                <!-- Loading State -->
                <div v-if="loading" class="flex justify-center py-4">
                  <span class="loading loading-spinner loading-md"></span>
                </div>

                <!-- Results List -->
                <div v-else-if="filteredCollections.length > 0" class="max-h-64 overflow-y-auto">
                  <div class="space-y-1">
                    <button
                      v-for="collection in filteredCollections"
                      :key="collection"
                      class="w-full text-left px-4 py-2 hover:bg-base-200 rounded transition-colors"
                      @click="handleSelect(collection)"
                    >
                      <span class="font-mono">{{ collection }}</span>
                    </button>
                  </div>
                </div>

                <!-- Empty State (no collections in region at all) -->
                <div v-else-if="!loading && collections.length === 0 && !searchQuery" class="text-center py-8 text-base-content/60">
                  No collections found in this region
                </div>

                <!-- No Matches State (search filtered everything out) -->
                <div v-else-if="!loading && searchQuery" class="text-center py-8 text-base-content/60">
                  <p>No matching collections found</p>
                  <p class="text-sm mt-2">Press Enter or click Search to use "{{ searchQuery }}"</p>
                </div>
              </div>

              <!-- Actions -->
              <div class="flex justify-end gap-3 mt-6">
                <button
                  class="btn btn-ghost"
                  @click="emit('close')"
                >
                  Cancel
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
import { ref, onMounted } from 'vue';
import { Dialog, DialogPanel, DialogTitle, TransitionRoot, TransitionChild } from '@headlessui/vue';
import { anythingService } from '@/services/AnythingService';
import { useRegion } from '@/composables/useRegion';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('CollectionSearchDialog');

const { currentRegionId } = useRegion();

const emit = defineEmits<{
  close: [];
  select: [collection: string];
}>();

const searchQuery = ref('');
const collections = ref<string[]>([]);
const filteredCollections = ref<string[]>([]);
const loading = ref(false);
const hasSearched = ref(false);

/**
 * Load all collections from the server
 */
const loadCollections = async () => {
  loading.value = true;

  try {
    const result = await anythingService.getCollections(currentRegionId.value || undefined);
    collections.value = result.collections;
    filteredCollections.value = result.collections;
    logger.debug('Loaded collections', { count: result.count });
  } catch (error) {
    logger.error('Failed to load collections', error as Error);
    collections.value = [];
    filteredCollections.value = [];
  } finally {
    loading.value = false;
  }
};

/**
 * Handle search - filter loaded collections
 */
const handleSearch = () => {
  hasSearched.value = true;

  if (!searchQuery.value) {
    filteredCollections.value = collections.value;
    return;
  }

  const query = searchQuery.value.toLowerCase();
  filteredCollections.value = collections.value.filter(c =>
    c.toLowerCase().includes(query)
  );
};

/**
 * Handle Enter key - use search query as collection name if provided
 */
const handleEnter = () => {
  if (searchQuery.value.trim()) {
    handleSelect(searchQuery.value.trim());
  }
};

/**
 * Handle collection selection
 */
const handleSelect = (collection: string) => {
  emit('select', collection);
  emit('close');
};

onMounted(() => {
  loadCollections();
});
</script>
