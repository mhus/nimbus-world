<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex items-center gap-4">
      <button class="btn btn-ghost btn-sm" @click="emit('back')">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
        Back
      </button>
      <h2 class="text-2xl font-bold">{{ team.title }}</h2>
      <span class="badge" :class="statusBadgeClass(team.status)">{{ team.status }}</span>
    </div>

    <!-- Team Info -->
    <div class="card bg-base-100 shadow">
      <div class="card-body">
        <h3 class="card-title text-base">Team Info</h3>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="form-control">
            <label class="label"><span class="label-text">Title</span></label>
            <input v-model="editTitle" type="text" class="input input-bordered w-full" />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Team ID</span></label>
            <input :value="team.teamId" type="text" class="input input-bordered w-full" disabled />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">World ID</span></label>
            <input :value="team.worldId" type="text" class="input input-bordered w-full" disabled />
          </div>
          <div class="form-control">
            <label class="label"><span class="label-text">Created</span></label>
            <input :value="formatDate(team.createdAt)" type="text" class="input input-bordered w-full" disabled />
          </div>
        </div>
        <div class="flex gap-2 mt-4">
          <button class="btn btn-primary btn-sm" :disabled="editTitle === team.title || saving" @click="handleSaveTitle">
            <span v-if="saving" class="loading loading-spinner loading-xs"></span>
            Save Title
          </button>

          <!-- Status actions -->
          <button
            v-if="team.status === 'ACTIVE'"
            class="btn btn-warning btn-sm"
            @click="handleUpdateStatus('INACTIVE')"
          >
            Deactivate
          </button>
          <button
            v-if="team.status === 'INACTIVE'"
            class="btn btn-success btn-sm"
            @click="handleUpdateStatus('ACTIVE')"
          >
            Activate
          </button>

          <!-- Emigrate (only LOBBY) -->
          <button
            v-if="team.status === 'LOBBY'"
            class="btn btn-accent btn-sm"
            @click="emigrateDialog?.showModal()"
          >
            Emigrate to Instance
          </button>

          <button class="btn btn-error btn-sm btn-outline ml-auto" @click="handleDelete">Delete Team</button>
        </div>
        <div v-if="saveError" class="alert alert-error text-sm mt-2">{{ saveError }}</div>
      </div>
    </div>

    <!-- Members -->
    <div class="card bg-base-100 shadow">
      <div class="card-body">
        <h3 class="card-title text-base">Members ({{ currentTeam.members.length }})</h3>
        <div class="flex gap-2 mb-4">
          <input v-model="newMember" type="text" class="input input-bordered input-sm flex-1" placeholder="Player name" @keyup.enter="handleAddMember" />
          <button class="btn btn-sm btn-primary" :disabled="!newMember.trim()" @click="handleAddMember">Add</button>
        </div>
        <div v-if="currentTeam.members.length === 0" class="text-base-content/50 text-sm">No members</div>
        <div class="flex flex-wrap gap-2">
          <div v-for="member in currentTeam.members" :key="member" class="badge badge-lg gap-2">
            {{ member }}
            <button class="btn btn-ghost btn-xs" @click="handleRemoveMember(member)">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Invitations -->
    <div class="card bg-base-100 shadow">
      <div class="card-body">
        <h3 class="card-title text-base">Invitations ({{ currentTeam.invitation.length }})</h3>
        <div class="flex gap-2 mb-4">
          <input v-model="newInvite" type="text" class="input input-bordered input-sm flex-1" placeholder="Player name" @keyup.enter="handleAddInvitation" />
          <button class="btn btn-sm btn-primary" :disabled="!newInvite.trim()" @click="handleAddInvitation">Invite</button>
        </div>
        <div v-if="currentTeam.invitation.length === 0" class="text-base-content/50 text-sm">No invitations</div>
        <div class="flex flex-wrap gap-2">
          <div v-for="invite in currentTeam.invitation" :key="invite" class="badge badge-lg badge-outline gap-2">
            {{ invite }}
            <button class="btn btn-ghost btn-xs" @click="handleRemoveInvitation(invite)">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Emigrate Modal -->
    <dialog ref="emigrateDialog" class="modal">
      <div class="modal-box max-w-sm">
        <h3 class="font-bold text-lg mb-4">Emigrate Team to Instance</h3>
        <div class="form-control">
          <label class="label"><span class="label-text">Instance World ID</span></label>
          <input v-model="emigrateInstanceId" type="text" class="input input-bordered w-full" placeholder="regionId:worldName:instanceId" />
        </div>
        <div v-if="emigrateError" class="alert alert-error text-sm mt-2">{{ emigrateError }}</div>
        <div class="modal-action">
          <button class="btn btn-ghost" @click="emigrateDialog?.close()">Cancel</button>
          <button class="btn btn-accent" :disabled="!emigrateInstanceId.trim() || emigrateSaving" @click="handleEmigrate">
            <span v-if="emigrateSaving" class="loading loading-spinner loading-xs"></span>
            Emigrate
          </button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop"><button>close</button></form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue';
import { teamServiceFrontend, type Team, type TeamStatus } from '../services/TeamServiceFrontend';

const props = defineProps<{ team: Team }>();
const emit = defineEmits<{
  back: [];
  saved: [team: Team];
  deleted: [];
}>();

const currentTeam = reactive<Team>({ ...props.team });
const editTitle = ref(props.team.title);
const saving = ref(false);
const saveError = ref<string | null>(null);

const newMember = ref('');
const newInvite = ref('');

const emigrateDialog = ref<HTMLDialogElement | null>(null);
const emigrateInstanceId = ref('');
const emigrateSaving = ref(false);
const emigrateError = ref<string | null>(null);

watch(() => props.team, (t) => {
  Object.assign(currentTeam, t);
  editTitle.value = t.title;
});

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

const refreshTeam = async () => {
  try {
    const updated = await teamServiceFrontend.getTeam(currentTeam.teamId);
    Object.assign(currentTeam, updated);
    emit('saved', updated);
  } catch (e) {
    console.error('[TeamEditor] Failed to refresh team:', e);
  }
};

const handleSaveTitle = async () => {
  saving.value = true;
  saveError.value = null;
  try {
    const updated = await teamServiceFrontend.updateTeam(currentTeam.teamId, editTitle.value);
    Object.assign(currentTeam, updated);
    emit('saved', updated);
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to save';
  } finally {
    saving.value = false;
  }
};

const handleUpdateStatus = async (status: TeamStatus) => {
  saveError.value = null;
  try {
    const updated = await teamServiceFrontend.updateStatus(currentTeam.teamId, status);
    Object.assign(currentTeam, updated);
    emit('saved', updated);
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to update status';
  }
};

const handleDelete = async () => {
  if (!confirm(`Delete team "${currentTeam.title}"? This cannot be undone.`)) return;
  try {
    await teamServiceFrontend.deleteTeam(currentTeam.teamId);
    emit('deleted');
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to delete';
  }
};

const handleAddMember = async () => {
  const name = newMember.value.trim();
  if (!name) return;
  try {
    await teamServiceFrontend.addMember(currentTeam.teamId, name);
    newMember.value = '';
    await refreshTeam();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to add member';
  }
};

const handleRemoveMember = async (name: string) => {
  try {
    await teamServiceFrontend.removeMember(currentTeam.teamId, name);
    await refreshTeam();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to remove member';
  }
};

const handleAddInvitation = async () => {
  const name = newInvite.value.trim();
  if (!name) return;
  try {
    await teamServiceFrontend.addInvitation(currentTeam.teamId, name);
    newInvite.value = '';
    await refreshTeam();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to add invitation';
  }
};

const handleRemoveInvitation = async (name: string) => {
  try {
    await teamServiceFrontend.removeInvitation(currentTeam.teamId, name);
    await refreshTeam();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : 'Failed to remove invitation';
  }
};

const handleEmigrate = async () => {
  emigrateSaving.value = true;
  emigrateError.value = null;
  try {
    const updated = await teamServiceFrontend.emigrate(currentTeam.teamId, emigrateInstanceId.value.trim());
    Object.assign(currentTeam, updated);
    emigrateDialog.value?.close();
    emit('saved', updated);
  } catch (e) {
    emigrateError.value = e instanceof Error ? e.message : 'Failed to emigrate';
  } finally {
    emigrateSaving.value = false;
  }
};
</script>
