<template>
  <div class="modal modal-open" @click.self="emit('close')">
    <div class="modal-box max-w-6xl max-h-[90vh] overflow-y-auto" @click.stop>
      <h3 class="font-bold text-lg mb-4">
        Chunk Details: {{ chunkKey }}
      </h3>

      <!-- Error Alert -->
      <ErrorAlert v-if="errorMessage" :message="errorMessage" class="mb-4" />

      <!-- Loading State -->
      <LoadingSpinner v-if="loading" />

      <!-- Chunk Data -->
      <div v-else-if="chunkData" class="space-y-4">
        <!-- Metadata -->
        <div class="card bg-base-200">
          <div class="card-body">
            <h4 class="font-semibold mb-2">Metadata</h4>
            <div class="grid grid-cols-2 gap-2 text-sm">
              <div><span class="font-medium">CX:</span> {{ chunkData.cx }}</div>
              <div><span class="font-medium">CZ:</span> {{ chunkData.cz }}</div>
              <div><span class="font-medium">Size:</span> {{ chunkData.size }}</div>
              <div><span class="font-medium">Block Count:</span> {{ chunkData.blockCount }}</div>
              <div>
                <span class="font-medium">Compressed:</span>
                <span v-if="chunkMetadata?.compressed" class="badge badge-success badge-sm ml-2">Yes</span>
                <span v-else class="badge badge-ghost badge-sm ml-2">No</span>
              </div>
              <div v-if="storageInfo">
                <span class="font-medium">Storage Size:</span>
                <span class="text-xs">{{ formatFileSize(storageInfo.size) }}</span>
              </div>
              <div v-if="chunkMetadata">
                <span class="font-medium">Storage ID:</span>
                <span class="text-xs">{{ chunkMetadata.storageId }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Tabs for Blocks and HeightData -->
        <TabGroup>
          <TabList class="tabs tabs-boxed">
            <Tab v-slot="{ selected }" class="tab">
              <span :class="{ 'tab-active': selected }">Blocks ({{ chunkData.blockCount }})</span>
            </Tab>
            <Tab v-slot="{ selected }" class="tab">
              <span :class="{ 'tab-active': selected }">Height Data ({{ chunkData.heightData?.length || 0 }})</span>
            </Tab>
          </TabList>

          <TabPanels class="mt-4">
            <!-- Blocks Tab -->
            <TabPanel>
              <div class="overflow-x-auto">
                <table class="table table-sm table-zebra">
                  <thead>
                    <tr>
                      <th>Position</th>
                      <th>Block Type ID</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(block, idx) in chunkData.blocks" :key="idx">
                      <td>
                        {{ block.position ? `(${block.position.x}, ${block.position.y}, ${block.position.z})` : 'N/A' }}
                      </td>
                      <td>{{ block.blockTypeId || 'N/A' }}</td>
                      <td>{{ block.status ?? 'N/A' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </TabPanel>

            <!-- HeightData Tab -->
            <TabPanel>
              <div class="overflow-x-auto">
                <table class="table table-sm table-zebra">
                  <thead>
                    <tr>
                      <th>X</th>
                      <th>Z</th>
                      <th>Max Height</th>
                      <th>Ground Level</th>
                      <th>Water Level</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(data, idx) in chunkData.heightData" :key="idx">
                      <td>{{ data[0] }}</td>
                      <td>{{ data[1] }}</td>
                      <td>{{ data[2] }}</td>
                      <td>{{ data[3] }}</td>
                      <td>{{ data.length > 4 ? data[4] : '-' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </TabPanel>
          </TabPanels>
        </TabGroup>
      </div>

      <!-- Close Button -->
      <div class="modal-action">
        <button
          type="button"
          class="btn"
          @click="emit('close')"
        >
          Close
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { TabGroup, TabList, Tab, TabPanels, TabPanel } from '@headlessui/vue';
import { chunkService, type ChunkDataResponse, type ChunkMetadata } from '@/services/ChunkService';
import { storageService, type StorageInfo } from '@/services/StorageService';
import { formatFileSize } from '@/utils/format';
import ErrorAlert from '@components/ErrorAlert.vue';
import LoadingSpinner from '@components/LoadingSpinner.vue';

interface Props {
  worldId: string;
  chunkKey: string;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const chunkData = ref<ChunkDataResponse | null>(null);
const chunkMetadata = ref<ChunkMetadata | null>(null);
const storageInfo = ref<StorageInfo | null>(null);
const loading = ref(false);
const errorMessage = ref('');

/**
 * Load chunk data and metadata
 */
const loadChunkData = async () => {
  loading.value = true;
  errorMessage.value = '';

  try {
    // Load both data and metadata in parallel
    const [data, metadata] = await Promise.all([
      chunkService.getChunkData(props.worldId, props.chunkKey),
      chunkService.getChunk(props.worldId, props.chunkKey)
    ]);
    chunkData.value = data;
    chunkMetadata.value = metadata;

    // Load storage info if storageId is available
    if (metadata.storageId) {
      try {
        storageInfo.value = await storageService.getInfo(metadata.storageId);
      } catch (error) {
        // Ignore storage info loading errors
      }
    }
  } catch (error: any) {
    errorMessage.value = error.message || 'Failed to load chunk data';
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadChunkData();
});
</script>
