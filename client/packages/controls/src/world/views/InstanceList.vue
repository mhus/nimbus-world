<template>
  <div class="space-y-4">
    <!-- Header with Search and Filters -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-center justify-between">
      <div class="flex-1">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="Search instances by title, instanceId or worldId..."
          class="input input-bordered w-full"
          @input="handleSearch"
        />
      </div>
    </div>

    <!-- Filter Controls -->
    <div class="flex flex-wrap gap-4">
      <div class="form-control">
        <label class="label">
          <span class="label-text">Filter by World ID:</span>
        </label>
        <input
          v-model="filterWorldId"
          type="text"
          placeholder="Enter worldId"
          class="input input-bordered input-sm"
          @input="handleFilterChange"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text">Filter by Creator:</span>
        </label>
        <input
          v-model="filterCreator"
          type="text"
          placeholder="Enter playerId"
          class="input input-bordered input-sm"
          @input="handleFilterChange"
        />
      </div>
      <div class="form-control">
        <label class="label">
          <span class="label-text">&nbsp;</span>
        </label>
        <button class="btn btn-sm btn-ghost" @click="handleClearFilters">
          Clear Filters
        </button>
      </div>
    </div>

    <!-- Info Alert -->
    <div class="alert alert-info">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>Instances are created by players in-game. Here you can view and delete instances.</span>
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

    <!-- Empty State -->
    <div v-else-if="!loading && filteredInstances.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No instances found</p>
      <p class="text-base-content/50 text-sm mt-2">Instances will appear here when players create them in-game</p>
    </div>

    <!-- Instances Table -->
    <div v-else>
      <div class="overflow-x-auto">
        <table class="table table-zebra w-full">
          <thead>
            <tr>
              <th>Instance ID</th>
              <th>Title</th>
              <th>World ID</th>
              <th>Epoch</th>
              <th>Creator</th>
              <th>Players</th>
              <th>Access</th>
              <th>Duration</th>
              <th>Created</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="instance in paginatedInstances" :key="instance.instanceId">
              <td>
                <div class="font-mono text-xs truncate max-w-xs" :title="instance.instanceId">
                  {{ instance.instanceId }}
                </div>
              </td>
              <td>
                <div class="font-medium" :title="instance.title">
                  {{ instance.title }}
                </div>
                <div v-if="instance.description" class="text-xs text-base-content/70 truncate max-w-xs">
                  {{ instance.description }}
                </div>
              </td>
              <td>
                <div class="font-mono text-xs" :title="instance.worldId">
                  {{ instance.worldId }}
                </div>
              </td>
              <td>
                <span class="badge badge-sm badge-outline font-mono">
                  {{ instance.epoch }}
                </span>
              </td>
              <td>
                <div class="font-mono text-xs" :title="instance.creator">
                  {{ instance.creator }}
                </div>
              </td>
              <td>
                <div class="badge badge-sm badge-outline">
                  {{ instance.activePlayers?.length || 0 }}/{{ instance.players.length }}
                </div>
              </td>
              <td>
                <span class="badge badge-sm" :class="accessBadgeClass(instance.accessType)">
                  {{ instance.accessType || 'PRIVATE' }}
                </span>
              </td>
              <td>
                <span class="badge badge-sm" :class="durationBadgeClass(instance.durationType)">
                  {{ instance.durationType || 'SHORT' }}
                </span>
                <div v-if="instance.expiresAt" class="text-xs text-base-content/70 mt-1">
                  {{ formatDate(instance.expiresAt) }}
                </div>
              </td>
              <td>
                <div class="text-xs text-base-content/70">
                  {{ formatDate(instance.createdAt) }}
                </div>
              </td>
              <td>
                <span
                  class="badge badge-sm"
                  :class="instance.enabled ? 'badge-success' : 'badge-error'"
                >
                  {{ instance.enabled ? 'Enabled' : 'Disabled' }}
                </span>
              </td>
              <td>
                <div class="flex gap-1">
                  <button
                    class="btn btn-ghost btn-xs"
                    @click="handleSwitchEpoch(instance)"
                    title="Switch epoch"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                    </svg>
                    Epoch
                  </button>
                  <button
                    class="btn btn-ghost btn-xs"
                    @click="handleEdit(instance)"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                    </svg>
                    Edit
                  </button>
                  <button
                    class="btn btn-ghost btn-xs text-error"
                    @click="handleDelete(instance.instanceId, instance.title)"
                  >
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                    </svg>
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination Controls -->
      <div v-if="totalPages > 1" class="flex flex-col sm:flex-row items-center justify-between gap-4 mt-6">
        <div class="text-sm text-base-content/70">
          Showing {{ ((currentPage - 1) * pageSize) + 1 }}-{{ Math.min(currentPage * pageSize, filteredInstances.length) }} of {{ filteredInstances.length }} instances
        </div>
        <div class="flex gap-2">
          <button
            class="btn btn-sm"
            :disabled="!hasPreviousPage"
            @click="handlePreviousPage"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
            Previous
          </button>
          <div class="flex items-center gap-2 px-4">
            <span class="text-sm">Page {{ currentPage }} of {{ totalPages }}</span>
          </div>
          <button
            class="btn btn-sm"
            :disabled="!hasNextPage"
            @click="handleNextPage"
          >
            Next
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Edit Modal -->
    <dialog ref="editDialog" class="modal">
      <div class="modal-box max-w-lg">
        <h3 class="font-bold text-lg mb-4">Edit Instance</h3>

        <div v-if="editInstance" class="space-y-4">
          <div class="text-xs font-mono text-base-content/50 mb-2">{{ editInstance.instanceId }}</div>

          <!-- Title -->
          <div class="form-control">
            <label class="label"><span class="label-text">Title</span></label>
            <input v-model="editForm.title" type="text" class="input input-bordered w-full" />
          </div>

          <!-- Description -->
          <div class="form-control">
            <label class="label"><span class="label-text">Description</span></label>
            <textarea v-model="editForm.description" class="textarea textarea-bordered w-full" rows="2"></textarea>
          </div>

          <!-- Access Type -->
          <div class="form-control">
            <label class="label"><span class="label-text">Access Type</span></label>
            <select v-model="editForm.accessType" class="select select-bordered w-full">
              <option value="PRIVATE">PRIVATE - Only creator</option>
              <option value="TEAM">TEAM - Creator + invited players</option>
              <option value="PUBLIC">PUBLIC - Everyone</option>
            </select>
          </div>

          <!-- Duration Type -->
          <div class="form-control">
            <label class="label"><span class="label-text">Duration Type</span></label>
            <select v-model="editForm.durationType" class="select select-bordered w-full">
              <option value="SHORT">SHORT - Deleted when empty</option>
              <option value="SEASONAL">SEASONAL - Persists for a season</option>
              <option value="EVENT">EVENT - Tied to an event</option>
            </select>
          </div>

          <!-- Expires At -->
          <div v-if="editForm.durationType !== 'SHORT'" class="form-control">
            <label class="label"><span class="label-text">Expires At</span></label>
            <input v-model="editForm.expiresAt" type="datetime-local" class="input input-bordered w-full" />
          </div>

          <!-- Enabled -->
          <div class="form-control">
            <label class="label cursor-pointer justify-start gap-4">
              <input v-model="editForm.enabled" type="checkbox" class="toggle toggle-primary" />
              <span class="label-text">Enabled</span>
            </label>
          </div>

          <!-- Players (read-only) -->
          <div class="form-control">
            <label class="label"><span class="label-text">Players ({{ editInstance.players.length }})</span></label>
            <div class="bg-base-200 rounded-lg p-2 max-h-24 overflow-y-auto">
              <div v-if="editInstance.players.length === 0" class="text-xs text-base-content/50">No players</div>
              <div v-for="player in editInstance.players" :key="player" class="text-xs font-mono">{{ player }}</div>
            </div>
          </div>

          <!-- Active Players (read-only) -->
          <div class="form-control">
            <label class="label"><span class="label-text">Active Players ({{ editInstance.activePlayers?.length || 0 }})</span></label>
            <div class="bg-base-200 rounded-lg p-2 max-h-24 overflow-y-auto">
              <div v-if="!editInstance.activePlayers?.length" class="text-xs text-base-content/50">No active players</div>
              <div v-for="player in editInstance.activePlayers" :key="player" class="text-xs font-mono">{{ player }}</div>
            </div>
          </div>

          <!-- Error -->
          <div v-if="editError" class="alert alert-error text-sm">{{ editError }}</div>
        </div>

        <div class="modal-action">
          <button class="btn btn-ghost" @click="closeEditDialog">Cancel</button>
          <button class="btn btn-primary" :disabled="editSaving" @click="handleSaveEdit">
            <span v-if="editSaving" class="loading loading-spinner loading-xs"></span>
            Save
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop"><button>close</button></form>
    </dialog>
    <!-- Epoch Switch Modal -->
    <dialog ref="epochDialog" class="modal">
      <div class="modal-box max-w-sm">
        <h3 class="font-bold text-lg mb-4">Switch Epoch</h3>

        <div v-if="epochInstance" class="space-y-4">
          <div class="text-xs font-mono text-base-content/50">{{ epochInstance.instanceId }}</div>
          <div class="text-sm">Current epoch: <span class="badge badge-sm badge-outline font-mono">{{ epochInstance.epoch }}</span></div>

          <div class="form-control">
            <label class="label"><span class="label-text">New Epoch</span></label>
            <input
              v-model.number="newEpochValue"
              type="number"
              min="0"
              class="input input-bordered w-full"
              @keyup.enter="handleConfirmEpochSwitch"
            />
          </div>

          <div v-if="epochError" class="alert alert-error text-sm">{{ epochError }}</div>
        </div>

        <div class="modal-action">
          <button class="btn btn-ghost" @click="closeEpochDialog">Cancel</button>
          <button class="btn btn-primary" :disabled="epochSaving" @click="handleConfirmEpochSwitch">
            <span v-if="epochSaving" class="loading loading-spinner loading-xs"></span>
            Switch
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop"><button>close</button></form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { instanceServiceFrontend, type Instance, type InstanceAccessType, type InstanceDurationType, type InstanceUpdateRequest } from '../services/InstanceServiceFrontend';

const instances = ref<Instance[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const searchQuery = ref('');
const filterWorldId = ref('');
const filterCreator = ref('');

// Edit modal state
const editDialog = ref<HTMLDialogElement | null>(null);
const editInstance = ref<Instance | null>(null);
const editForm = ref({
  title: '',
  description: '',
  accessType: 'PRIVATE' as InstanceAccessType,
  durationType: 'SHORT' as InstanceDurationType,
  expiresAt: '',
  enabled: true,
});
const editSaving = ref(false);
const editError = ref<string | null>(null);

// Epoch switch modal state
const epochDialog = ref<HTMLDialogElement | null>(null);
const epochInstance = ref<Instance | null>(null);
const newEpochValue = ref(0);
const epochSaving = ref(false);
const epochError = ref<string | null>(null);

// Paging
const currentPage = ref(1);
const pageSize = ref(20);

const filteredInstances = computed(() => {
  let result = instances.value;

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(i =>
      i.title.toLowerCase().includes(query) ||
      i.instanceId.toLowerCase().includes(query) ||
      i.worldId.toLowerCase().includes(query) ||
      (i.description && i.description.toLowerCase().includes(query))
    );
  }

  return result;
});

const paginatedInstances = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredInstances.value.slice(start, end);
});

const totalPages = computed(() => Math.ceil(filteredInstances.value.length / pageSize.value));
const hasNextPage = computed(() => currentPage.value < totalPages.value);
const hasPreviousPage = computed(() => currentPage.value > 1);

const formatDate = (dateString: string): string => {
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  } catch {
    return dateString;
  }
};

const loadInstances = async () => {
  loading.value = true;
  error.value = null;

  try {
    console.log('[InstanceList] Loading instances with filters:', {
      worldId: filterWorldId.value,
      creator: filterCreator.value,
    });

    instances.value = await instanceServiceFrontend.listInstances(
      filterWorldId.value || undefined,
      filterCreator.value || undefined
    );

    console.log('[InstanceList] Loaded instances:', instances.value.length);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load instances';
    console.error('[InstanceList] Failed to load instances:', e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
};

const handleFilterChange = () => {
  currentPage.value = 1;
  loadInstances();
};

const handleClearFilters = () => {
  filterWorldId.value = '';
  filterCreator.value = '';
  searchQuery.value = '';
  currentPage.value = 1;
  loadInstances();
};

const accessBadgeClass = (type: string) => {
  switch (type) {
    case 'PUBLIC': return 'badge-success';
    case 'TEAM': return 'badge-info';
    case 'PRIVATE': return 'badge-warning';
    default: return 'badge-ghost';
  }
};

const durationBadgeClass = (type: string) => {
  switch (type) {
    case 'SHORT': return 'badge-ghost';
    case 'SEASONAL': return 'badge-accent';
    case 'EVENT': return 'badge-secondary';
    default: return 'badge-ghost';
  }
};

const toLocalDatetime = (isoString?: string): string => {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    const offset = date.getTimezoneOffset();
    const local = new Date(date.getTime() - offset * 60000);
    return local.toISOString().slice(0, 16);
  } catch {
    return '';
  }
};

const handleEdit = (instance: Instance) => {
  editInstance.value = instance;
  editForm.value = {
    title: instance.title || '',
    description: instance.description || '',
    accessType: instance.accessType || 'PRIVATE',
    durationType: instance.durationType || 'SHORT',
    expiresAt: toLocalDatetime(instance.expiresAt),
    enabled: instance.enabled,
  };
  editError.value = null;
  editDialog.value?.showModal();
};

const closeEditDialog = () => {
  editDialog.value?.close();
  editInstance.value = null;
};

const handleSaveEdit = async () => {
  if (!editInstance.value) return;
  editSaving.value = true;
  editError.value = null;

  try {
    const request: InstanceUpdateRequest = {
      title: editForm.value.title,
      description: editForm.value.description,
      accessType: editForm.value.accessType,
      durationType: editForm.value.durationType,
      expiresAt: editForm.value.expiresAt ? new Date(editForm.value.expiresAt).toISOString() : undefined,
      enabled: editForm.value.enabled,
    };
    await instanceServiceFrontend.updateInstance(editInstance.value.instanceId, request);
    closeEditDialog();
    await loadInstances();
  } catch (e) {
    editError.value = e instanceof Error ? e.message : 'Failed to save instance';
    console.error('[InstanceList] Failed to save instance:', e);
  } finally {
    editSaving.value = false;
  }
};

const handleDelete = async (instanceId: string, title: string) => {
  if (!confirm(`Are you sure you want to delete instance "${title}"?\n\nThis action cannot be undone and will remove all data associated with this instance.`)) {
    return;
  }

  try {
    await instanceServiceFrontend.deleteInstance(instanceId);
    await loadInstances();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete instance';
    console.error('[InstanceList] Failed to delete instance:', e);
  }
};

const handleSwitchEpoch = (instance: Instance) => {
  epochInstance.value = instance;
  newEpochValue.value = instance.epoch;
  epochError.value = null;
  epochDialog.value?.showModal();
};

const closeEpochDialog = () => {
  epochDialog.value?.close();
  epochInstance.value = null;
};

const handleConfirmEpochSwitch = async () => {
  if (!epochInstance.value) return;
  if (newEpochValue.value === epochInstance.value.epoch) {
    epochError.value = 'Epoch is already ' + newEpochValue.value;
    return;
  }
  epochSaving.value = true;
  epochError.value = null;

  try {
    await instanceServiceFrontend.switchEpoch(epochInstance.value.instanceId, newEpochValue.value);
    closeEpochDialog();
    await loadInstances();
  } catch (e) {
    epochError.value = e instanceof Error ? e.message : 'Failed to switch epoch';
    console.error('[InstanceList] Failed to switch epoch:', e);
  } finally {
    epochSaving.value = false;
  }
};

const handleNextPage = () => {
  if (hasNextPage.value) {
    currentPage.value++;
  }
};

const handlePreviousPage = () => {
  if (hasPreviousPage.value) {
    currentPage.value--;
  }
};

onMounted(() => {
  loadInstances();
});
</script>
