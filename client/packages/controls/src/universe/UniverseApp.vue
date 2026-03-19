<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <header class="navbar bg-base-200 shadow-lg">
      <div class="flex-none">
        <a href="/controls/index.html" class="btn btn-ghost btn-square">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
          </svg>
        </a>
      </div>
      <div class="flex-1">
        <a class="btn btn-ghost normal-case text-xl">Nimbus Universe Editor</a>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6 max-w-2xl">

      <!-- Status Card -->
      <div class="card bg-base-200 shadow-md mb-4">
        <div class="card-body">
          <h2 class="card-title text-lg">Status</h2>
          <div class="flex items-center gap-2">
            <div class="badge" :class="status.paired ? 'badge-success' : 'badge-warning'">
              {{ status.paired ? 'paired' : 'not paired' }}
            </div>
            <span v-if="status.name" class="text-sm font-mono">{{ status.name }}</span>
          </div>
        </div>
      </div>

      <!-- Universe URL -->
      <div class="card bg-base-200 shadow-md mb-4">
        <div class="card-body">
          <h2 class="card-title text-lg">Universe Connection</h2>

          <div class="form-control w-full">
            <label class="label"><span class="label-text">Universe URL</span></label>
            <input
              v-model="universeUrl"
              type="text"
              placeholder="http://localhost:9040"
              class="input input-bordered w-full"
            />
          </div>

          <div class="card-actions justify-end mt-2">
            <button class="btn btn-primary btn-sm" @click="saveUrl" :disabled="saving">
              <span v-if="saving" class="loading loading-spinner loading-xs"></span>
              Save
            </button>
            <button class="btn btn-outline btn-sm" @click="ping" :disabled="pinging">
              <span v-if="pinging" class="loading loading-spinner loading-xs"></span>
              Ping
            </button>
          </div>
        </div>
      </div>

      <!-- Pair Card (only if not paired) -->
      <div v-if="!status.paired" class="card bg-base-200 shadow-md mb-4">
        <div class="card-body">
          <h2 class="card-title text-lg">Pair with Universe</h2>
          <p class="text-sm text-base-content/60">Enter the invite token from the universe admin (format: name:token)</p>

          <div class="form-control w-full">
            <label class="label"><span class="label-text">Invite Token</span></label>
            <input
              v-model="inviteToken"
              type="text"
              placeholder="sector-alpha:abc123..."
              class="input input-bordered w-full font-mono text-sm"
            />
          </div>

          <div class="card-actions justify-end mt-2">
            <button class="btn btn-primary btn-sm" @click="doPair" :disabled="pairing || !inviteToken.trim()">
              <span v-if="pairing" class="loading loading-spinner loading-xs"></span>
              Pair
            </button>
          </div>
        </div>
      </div>

      <!-- Message -->
      <div v-if="statusMessage" class="mb-4">
        <div class="alert" :class="statusOk ? 'alert-success' : 'alert-error'">
          <span>{{ statusMessage }}</span>
        </div>
      </div>

    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

const BASE = '/control/universe';

const universeUrl = ref('');
const inviteToken = ref('');
const saving = ref(false);
const pinging = ref(false);
const pairing = ref(false);
const statusMessage = ref('');
const statusOk = ref(false);
const status = ref({ url: '', paired: false, name: '' });

function showMsg(text: string, ok: boolean) {
  statusMessage.value = text;
  statusOk.value = ok;
  setTimeout(() => statusMessage.value = '', 5000);
}

async function loadStatus() {
  try {
    const data = await apiService.get<any>(BASE + '/status');
    status.value = data;
    universeUrl.value = data.url || '';
  } catch (e) {
    // ignore — not authenticated or server down
  }
}

async function saveUrl() {
  saving.value = true;
  try {
    await apiService.put(BASE + '/url', { url: universeUrl.value.trim() });
    showMsg('URL saved', true);
    await loadStatus();
  } catch (err: any) {
    showMsg(err.response?.data?.error || 'Save failed', false);
  } finally {
    saving.value = false;
  }
}

async function ping() {
  pinging.value = true;
  try {
    const data = await apiService.post<any>(BASE + '/ping');
    if (data.ok) {
      showMsg('Connected — status: ' + data.status, true);
    } else {
      showMsg(data.error || 'Ping failed', false);
    }
  } catch (err: any) {
    showMsg(err.response?.data?.error || 'Connection failed', false);
  } finally {
    pinging.value = false;
  }
}

async function doPair() {
  pairing.value = true;
  try {
    const data = await apiService.post<any>(BASE + '/pair', { inviteToken: inviteToken.value.trim() });
    if (data.ok) {
      showMsg('Paired as "' + data.name + '"', true);
      inviteToken.value = '';
      await loadStatus();
    } else {
      showMsg(data.error || 'Pairing failed', false);
    }
  } catch (err: any) {
    showMsg(err.response?.data?.error || 'Pairing request failed', false);
  } finally {
    pairing.value = false;
  }
}

onMounted(() => {
  loadStatus();
});
</script>
