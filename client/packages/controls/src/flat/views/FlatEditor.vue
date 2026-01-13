<template>
  <div class="space-y-4">
    <!-- Check if world is selected -->
    <div v-if="!currentWorldId" class="alert alert-info">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Please select a world to view flats.</span>
    </div>

    <!-- Flat Editor Content (only shown when world is selected) -->
    <template v-else>
      <!-- Header with Create Button -->
      <div class="flex justify-between items-center mb-4">
        <h2 class="text-2xl font-bold">Flats</h2>
        <button
          class="btn btn-primary"
          @click="showCreateDialog = true"
        >
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create New Flat
        </button>
      </div>

      <!-- Loading State -->
      <LoadingSpinner v-if="loading && flats.length === 0" />

      <!-- Error State -->
      <ErrorAlert v-else-if="error" :message="error" />

      <!-- Empty State -->
      <div v-else-if="!loading && flats.length === 0" class="text-center py-12">
        <p class="text-base-content/70 text-lg">No flats found for this world</p>
        <button
          class="btn btn-primary mt-4"
          @click="showCreateDialog = true"
        >
          <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create New Flat
        </button>
      </div>

      <!-- Flat List -->
      <FlatList
        v-else
        :flats="flats"
        :loading="loading"
        @view="handleViewFlat"
        @delete="handleDeleteFlat"
      />

      <!-- Flat Detail Modal -->
      <FlatDetailModal
        v-if="selectedFlatId"
        :flat-id="selectedFlatId"
        @close="closeFlatDetail"
      />

      <!-- Flat Create Dialog -->
      <FlatCreateDialog
        v-if="showCreateDialog && currentWorldId"
        :world-id="currentWorldId"
        @close="showCreateDialog = false"
        @job-created="handleJobCreated"
      />

      <!-- Job Watch Dialog -->
      <JobWatch
        v-if="watchingJobId && currentWorldId"
        :world-id="currentWorldId"
        :job-id="watchingJobId"
        @close="handleJobWatchClose"
        @completed="handleJobCompleted"
        @failed="handleJobFailed"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import { useFlats } from '@/composables/useFlats';
import LoadingSpinner from '@components/LoadingSpinner.vue';
import ErrorAlert from '@components/ErrorAlert.vue';
import FlatList from '../components/FlatList.vue';
import FlatDetailModal from '../components/FlatDetailModal.vue';
import FlatCreateDialog from '../components/FlatCreateDialog.vue';
import JobWatch from '../components/JobWatch.vue';
import type { Job } from '@/composables/useJobs';

const { currentWorldId, loadWorlds } = useWorld();

const flatsComposable = computed(() => {
  if (!currentWorldId.value) return null;
  return useFlats(currentWorldId.value);
});

const flats = computed(() => flatsComposable.value?.flats.value || []);
const loading = computed(() => flatsComposable.value?.loading.value || false);
const error = computed(() => flatsComposable.value?.error.value || null);
const selectedFlatId = ref<string | null>(null);
const showCreateDialog = ref(false);
const watchingJobId = ref<string | null>(null);

// Load flats when world changes
watch(currentWorldId, () => {
  if (currentWorldId.value) {
    flatsComposable.value?.loadFlats();
  }
}, { immediate: true });

onMounted(() => {
  // Load worlds with allWithoutInstances filter
  loadWorlds('allWithoutInstances');
});

/**
 * Handle view flat
 */
const handleViewFlat = (flatId: string) => {
  selectedFlatId.value = flatId;
};

/**
 * Handle delete flat
 */
const handleDeleteFlat = async (flatId: string) => {
  if (!confirm('Are you sure you want to delete this flat?')) {
    return;
  }

  try {
    await flatsComposable.value?.deleteFlat(flatId);
    alert('Flat deleted successfully!');
  } catch (e: any) {
    console.error('[FlatEditor] Failed to delete flat:', e);
    alert(`Failed to delete flat: ${e.message}`);
  }
};

/**
 * Close flat detail
 */
const closeFlatDetail = () => {
  selectedFlatId.value = null;
};

/**
 * Handle job created from create dialog
 */
const handleJobCreated = (jobId: string) => {
  console.log('[FlatEditor] Job created:', jobId);
  showCreateDialog.value = false;
  watchingJobId.value = jobId;
};

/**
 * Handle job watch close
 */
const handleJobWatchClose = () => {
  watchingJobId.value = null;
};

/**
 * Handle job completed
 */
const handleJobCompleted = (job: Job) => {
  console.log('[FlatEditor] Job completed:', job);
  watchingJobId.value = null;

  // Reload flat list
  flatsComposable.value?.loadFlats();
};

/**
 * Handle job failed
 */
const handleJobFailed = (job: Job) => {
  console.error('[FlatEditor] Job failed:', job);
  watchingJobId.value = null;

  // Still reload flat list in case partial work was done
  flatsComposable.value?.loadFlats();
};
</script>
