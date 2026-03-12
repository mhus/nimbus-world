<template>
  <div class="space-y-4">
    <!-- World Selector -->
    <div class="flex flex-col sm:flex-row gap-4 items-stretch sm:items-end">
      <div class="form-control flex-1">
        <label class="label"><span class="label-text">Main World</span></label>
        <select v-model="selectedWorldId" class="select select-bordered w-full" @change="handleWorldChange">
          <option value="">-- Select a main world --</option>
          <option v-for="w in mainWorlds" :key="w.worldId" :value="w.worldId">
            {{ w.title }} ({{ w.worldId }})
          </option>
        </select>
      </div>

      <div class="form-control">
        <label class="label"><span class="label-text">View</span></label>
        <select v-model="viewMode" class="select select-bordered" @change="handleViewChange">
          <option value="lobby">Lobby Teams</option>
          <option value="instance">Instance Teams</option>
        </select>
      </div>

      <!-- Instance selector (only visible in instance mode) -->
      <div v-if="viewMode === 'instance'" class="form-control flex-1">
        <label class="label"><span class="label-text">Instance</span></label>
        <select v-model="selectedInstanceId" class="select select-bordered w-full" @change="loadTeams">
          <option value="">-- Select an instance --</option>
          <option v-for="inst in instances" :key="inst.instanceId" :value="inst.instanceId">
            {{ inst.title || inst.instanceId }}
          </option>
        </select>
      </div>

      <div class="form-control">
        <label class="label"><span class="label-text">&nbsp;</span></label>
        <button class="btn btn-primary" :disabled="!selectedWorldId" @click="handleCreateTeam">
          <svg class="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Create Team
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-12">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="alert alert-error">
      <span>{{ error }}</span>
    </div>

    <!-- No world selected -->
    <div v-else-if="!selectedWorldId" class="text-center py-12">
      <p class="text-base-content/70 text-lg">Select a main world to see teams</p>
    </div>

    <!-- Empty -->
    <div v-else-if="teams.length === 0" class="text-center py-12">
      <p class="text-base-content/70 text-lg">No teams found</p>
      <p class="text-base-content/50 text-sm mt-2">
        {{ viewMode === 'lobby' ? 'Create a team in the lobby' : 'No teams in this instance' }}
      </p>
    </div>

    <!-- Teams Table -->
    <div v-else class="overflow-x-auto">
      <table class="table table-zebra w-full">
        <thead>
          <tr>
            <th>Title</th>
            <th>Team ID</th>
            <th>Members</th>
            <th>Invitations</th>
            <th>Status</th>
            <th>Created</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="team in teams" :key="team.teamId">
            <td class="font-medium">{{ team.title }}</td>
            <td>
              <div class="font-mono text-xs truncate max-w-xs" :title="team.teamId">{{ team.teamId }}</div>
            </td>
            <td>
              <span class="badge badge-sm badge-outline">{{ team.members.length }}</span>
            </td>
            <td>
              <span class="badge badge-sm badge-outline">{{ team.invitation.length }}</span>
            </td>
            <td>
              <span class="badge badge-sm" :class="statusBadgeClass(team.status)">{{ team.status }}</span>
            </td>
            <td class="text-xs text-base-content/70">{{ formatDate(team.createdAt) }}</td>
            <td>
              <div class="flex gap-1">
                <button class="btn btn-ghost btn-xs" @click="emit('select', team)">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                  Edit
                </button>
                <button class="btn btn-ghost btn-xs text-error" @click="handleDelete(team)">
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

    <!-- Create Team Modal -->
    <dialog ref="createDialog" class="modal">
      <div class="modal-box max-w-sm">
        <h3 class="font-bold text-lg mb-4">Create Team</h3>
        <div class="space-y-4">
          <div class="form-control">
            <label class="label"><span class="label-text">Title</span></label>
            <input v-model="createForm.title" type="text" class="input input-bordered w-full" placeholder="Team name" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Creator (Player Name)</span></label>
            <input v-model="createForm.creatorPlayerName" type="text" class="input input-bordered w-full" placeholder="Player name" />
          </div>
          <div v-if="createError" class="alert alert-error text-sm">{{ createError }}</div>
        </div>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="createDialog?.close()">Cancel</button>
          <button class="btn btn-primary" :disabled="!createForm.title || !createForm.creatorPlayerName || createSaving" @click="handleConfirmCreate">
            <span v-if="createSaving" class="loading loading-spinner loading-xs"></span>
            Create
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop"><button>close</button></form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { teamServiceFrontend, type Team } from '../services/TeamServiceFrontend';
import { worldServiceFrontend, type World } from '../../world/services/WorldServiceFrontend';
import { instanceServiceFrontend, type Instance } from '../../world/services/InstanceServiceFrontend';

const props = defineProps<{ regionId: string }>();
const emit = defineEmits<{
  select: [team: Team];
}>();

const mainWorlds = ref<World[]>([]);
const instances = ref<Instance[]>([]);
const teams = ref<Team[]>([]);
const selectedWorldId = ref('');
const selectedInstanceId = ref('');
const viewMode = ref<'lobby' | 'instance'>('lobby');
const loading = ref(false);
const error = ref<string | null>(null);

// Create modal
const createDialog = ref<HTMLDialogElement | null>(null);
const createForm = ref({ title: '', creatorPlayerName: '' });
const createSaving = ref(false);
const createError = ref<string | null>(null);

const formatDate = (dateString: string): string => {
  try {
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
  } catch {
    return dateString;
  }
};

const statusBadgeClass = (status: string) => {
  switch (status) {
    case 'LOBBY': return 'badge-info';
    case 'ACTIVE': return 'badge-success';
    case 'INACTIVE': return 'badge-ghost';
    default: return 'badge-ghost';
  }
};

const loadMainWorlds = async () => {
  if (!props.regionId) return;
  try {
    const all = await worldServiceFrontend.listWorlds(props.regionId);
    // Filter main worlds (no ':' zone separator beyond region:world)
    mainWorlds.value = all.filter(w => {
      const parts = w.worldId.split(':');
      return parts.length === 2; // regionId:worldName = main world
    });
  } catch (e) {
    console.error('[TeamList] Failed to load worlds:', e);
  }
};

const loadInstances = async () => {
  if (!selectedWorldId.value) {
    instances.value = [];
    return;
  }
  try {
    instances.value = await instanceServiceFrontend.listInstances(selectedWorldId.value);
  } catch (e) {
    console.error('[TeamList] Failed to load instances:', e);
  }
};

const loadTeams = async () => {
  if (!selectedWorldId.value) {
    teams.value = [];
    return;
  }

  loading.value = true;
  error.value = null;

  try {
    if (viewMode.value === 'lobby') {
      teams.value = await teamServiceFrontend.listTeams(selectedWorldId.value, 'LOBBY');
    } else if (selectedInstanceId.value) {
      teams.value = await teamServiceFrontend.listTeams(selectedInstanceId.value);
    } else {
      teams.value = [];
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to load teams';
  } finally {
    loading.value = false;
  }
};

const handleWorldChange = () => {
  selectedInstanceId.value = '';
  if (viewMode.value === 'instance') {
    loadInstances();
  }
  loadTeams();
};

const handleViewChange = () => {
  selectedInstanceId.value = '';
  if (viewMode.value === 'instance') {
    loadInstances();
  }
  loadTeams();
};

const handleCreateTeam = () => {
  createForm.value = { title: '', creatorPlayerName: '' };
  createError.value = null;
  createDialog.value?.showModal();
};

const handleConfirmCreate = async () => {
  createSaving.value = true;
  createError.value = null;
  try {
    await teamServiceFrontend.createTeam({
      worldId: selectedWorldId.value,
      title: createForm.value.title,
      creatorPlayerName: createForm.value.creatorPlayerName,
    });
    createDialog.value?.close();
    await loadTeams();
  } catch (e) {
    createError.value = e instanceof Error ? e.message : 'Failed to create team';
  } finally {
    createSaving.value = false;
  }
};

const handleDelete = async (team: Team) => {
  if (!confirm(`Delete team "${team.title}"? This cannot be undone.`)) return;
  try {
    await teamServiceFrontend.deleteTeam(team.teamId);
    await loadTeams();
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to delete team';
  }
};

watch(() => props.regionId, () => {
  selectedWorldId.value = '';
  selectedInstanceId.value = '';
  teams.value = [];
  loadMainWorlds();
}, { immediate: true });

onMounted(() => {
  loadMainWorlds();
});
</script>
