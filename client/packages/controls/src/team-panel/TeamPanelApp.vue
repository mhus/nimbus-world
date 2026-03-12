<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <svg class="w-7 h-7 text-teal-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            <h1 class="text-xl font-bold text-teal-400">Team</h1>
          </div>
          <button @click="refresh" class="btn btn-sm btn-ghost text-gray-400 hover:text-teal-400">
            <svg class="w-5 h-5" :class="{ 'animate-spin': loading }" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
          </button>
        </div>
      </div>
    </header>

    <!-- Action Message -->
    <div v-if="actionMessage" class="container mx-auto px-4 pt-3">
      <div class="alert" :class="actionSuccess ? 'alert-success' : 'alert-error'">
        <span>{{ actionMessage }}</span>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading && !data" class="flex-1 flex items-center justify-center">
      <span class="loading loading-spinner loading-lg text-teal-400"></span>
    </div>

    <!-- Error -->
    <div v-else-if="error" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <svg class="w-16 h-16 mx-auto text-red-400 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
        </svg>
        <p class="text-red-400">{{ error }}</p>
        <button @click="refresh" class="btn btn-sm btn-outline btn-error mt-4">Retry</button>
      </div>
    </div>

    <!-- Content -->
    <main v-else class="flex-1 container mx-auto px-4 py-4 space-y-4">

      <!-- Current Team Section -->
      <section v-if="data?.team" class="bg-gray-800 rounded-lg border border-gray-700 p-4">
        <div class="flex items-center justify-between mb-3">
          <h2 class="text-lg font-semibold text-teal-400">{{ data.team.title }}</h2>
          <div class="flex items-center gap-2">
            <span class="badge" :class="statusBadgeClass(data.team.status)">{{ data.team.status }}</span>
            <button @click="leaveTeam" class="btn btn-sm btn-outline btn-error" :disabled="actionLoading">
              Leave
            </button>
          </div>
        </div>

        <!-- Members -->
        <div class="mb-3">
          <h3 class="text-sm font-medium text-gray-400 mb-2">Members ({{ data.team.members.length }})</h3>
          <div class="flex flex-wrap gap-2">
            <div v-for="member in data.team.members" :key="member"
              class="badge badge-lg gap-2"
              :class="member === playerName ? 'badge-primary' : 'badge-ghost'">
              <span>{{ formatPlayerName(member) }}</span>
              <button v-if="member !== playerName"
                @click="kickMember(member)"
                class="btn btn-xs btn-circle btn-ghost text-error"
                :disabled="actionLoading"
                title="Kick">
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- Pending Invitations (outgoing) -->
        <div v-if="data.team.invitation && data.team.invitation.length > 0">
          <h3 class="text-sm font-medium text-gray-400 mb-2">Pending Invitations</h3>
          <div class="flex flex-wrap gap-2">
            <div v-for="inv in data.team.invitation" :key="inv" class="badge badge-lg badge-warning gap-2">
              <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              {{ formatPlayerName(inv) }}
              <button @click="uninvitePlayer(inv)"
                class="btn btn-xs btn-circle btn-ghost text-error"
                :disabled="actionLoading"
                title="Revoke invitation">
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>

        <!-- Team Parameters -->
        <div v-if="data.team.parameters && Object.keys(data.team.parameters).length > 0" class="mt-3">
          <h3 class="text-sm font-medium text-gray-400 mb-2">Stats</h3>
          <div class="flex flex-wrap gap-2">
            <div v-for="(value, key) in data.team.parameters" :key="key"
              class="badge badge-lg badge-info gap-1">
              <span class="font-medium">{{ key }}:</span> {{ value }}
            </div>
          </div>
        </div>

        <!-- Invite Player -->
        <div class="mt-4 flex gap-2">
          <input v-model="invitePlayerName" type="text" placeholder="Character names (comma-separated)"
            class="input input-bordered input-sm flex-1 bg-gray-700 border-gray-600"
            @keyup.enter="invitePlayer" />
          <button @click="invitePlayer" class="btn btn-sm btn-primary" :disabled="actionLoading || !invitePlayerName.trim()">
            Invite
          </button>
        </div>
      </section>

      <!-- No Team Section -->
      <section v-else class="bg-gray-800 rounded-lg border border-gray-700 p-6 text-center">
        <svg class="w-12 h-12 mx-auto text-gray-500 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <p class="text-gray-400 mb-4">You are not in a team</p>

        <!-- Create Team -->
        <div class="flex gap-2 max-w-sm mx-auto">
          <input v-model="newTeamTitle" type="text" placeholder="Team name"
            class="input input-bordered input-sm flex-1 bg-gray-700 border-gray-600"
            @keyup.enter="createTeam" />
          <button @click="createTeam" class="btn btn-sm btn-primary" :disabled="actionLoading || !newTeamTitle.trim()">
            Create Team
          </button>
        </div>
      </section>

      <!-- Incoming Invitations -->
      <section v-if="data?.invitations && data.invitations.length > 0"
        class="bg-gray-800 rounded-lg border border-gray-700 p-4">
        <h2 class="text-lg font-semibold text-amber-400 mb-3">
          <svg class="w-5 h-5 inline-block mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          Invitations ({{ data.invitations.length }})
        </h2>
        <div class="space-y-2">
          <div v-for="inv in data.invitations" :key="inv.teamId"
            class="flex items-center justify-between bg-gray-750 rounded-lg p-3 border border-gray-600">
            <div>
              <span class="font-medium text-gray-100">{{ inv.title }}</span>
            </div>
            <div class="flex gap-2">
              <button @click="acceptInvitation(inv.teamId)" class="btn btn-sm btn-success" :disabled="actionLoading">
                Accept
              </button>
              <button @click="declineInvitation(inv.teamId)" class="btn btn-sm btn-outline btn-error" :disabled="actionLoading">
                Decline
              </button>
            </div>
          </div>
        </div>
      </section>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface TeamResponse {
  teamId: string;
  title: string;
  members: string[];
  invitation: string[];
  status: string;
  parameters: Record<string, string>;
}

interface InviteResponse {
  teamId: string;
  title: string;
  worldId: string;
}

interface MyTeamResponse {
  team: TeamResponse | null;
  invitations: InviteResponse[];
}

const API_PATH = '/control/player/team';

const loading = ref(false);
const error = ref<string | null>(null);
const data = ref<MyTeamResponse | null>(null);
const actionLoading = ref(false);
const actionMessage = ref('');
const actionSuccess = ref(true);

const newTeamTitle = ref('');
const invitePlayerName = ref('');
const playerName = ref('');

function formatPlayerName(name: string): string {
  return name.startsWith('@') ? name.substring(1) : name;
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'ACTIVE': return 'badge-success';
    case 'LOBBY': return 'badge-warning';
    case 'INACTIVE': return 'badge-ghost';
    default: return 'badge-ghost';
  }
}

function showMessage(msg: string, success: boolean) {
  actionMessage.value = msg;
  actionSuccess.value = success;
  setTimeout(() => { actionMessage.value = ''; }, 3000);
}

async function refresh() {
  loading.value = true;
  error.value = null;
  try {
    data.value = await apiService.get<MyTeamResponse>(API_PATH);
  } catch (e: any) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function createTeam() {
  if (!newTeamTitle.value.trim()) return;
  actionLoading.value = true;
  try {
    await apiService.post(API_PATH, { title: newTeamTitle.value.trim() });
    newTeamTitle.value = '';
    showMessage('Team created', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function leaveTeam() {
  actionLoading.value = true;
  try {
    await apiService.delete(`${API_PATH}/leave`);
    showMessage('Left team', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function acceptInvitation(teamId: string) {
  actionLoading.value = true;
  try {
    await apiService.post(`${API_PATH}/accept/${teamId}`, {});
    showMessage('Joined team', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function declineInvitation(teamId: string) {
  actionLoading.value = true;
  try {
    await apiService.delete(`${API_PATH}/decline/${teamId}`);
    showMessage('Invitation declined', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function invitePlayer() {
  if (!invitePlayerName.value.trim()) return;
  actionLoading.value = true;
  try {
    const result = await apiService.post<{ invited: string[]; failed: string[] }>(
      `${API_PATH}/invite`, { playerName: invitePlayerName.value.trim() }
    );
    invitePlayerName.value = '';
    const parts: string[] = [];
    if (result.invited?.length) parts.push(`Invited: ${result.invited.join(', ')}`);
    if (result.failed?.length) parts.push(`Failed: ${result.failed.join(', ')}`);
    showMessage(parts.join(' | ') || 'Done', result.failed?.length === 0);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function uninvitePlayer(characterName: string) {
  actionLoading.value = true;
  try {
    await apiService.delete(`${API_PATH}/uninvite/${encodeURIComponent(characterName)}`);
    showMessage('Invitation revoked', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

async function kickMember(memberName: string) {
  actionLoading.value = true;
  try {
    await apiService.delete(`${API_PATH}/kick/${encodeURIComponent(memberName)}`);
    showMessage('Member kicked', true);
    await refresh();
  } catch (e: any) {
    showMessage(e.message, false);
  } finally {
    actionLoading.value = false;
  }
}

onMounted(() => {
  refresh();
});
</script>
