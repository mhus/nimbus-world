<template>
  <div class="min-h-screen flex flex-col">
    <!-- Header -->
    <header class="navbar bg-base-200 shadow-lg">
      <div class="flex-1">
        <h1 class="text-xl font-bold px-4">Nimbus Dev Login</h1>
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-1 container mx-auto px-4 py-6 max-w-4xl">
      <div class="space-y-6">
        <!-- Step 1: World Selection -->
        <div class="card bg-base-100 shadow-xl">
          <div class="card-body">
            <h2 class="card-title">1. Select World</h2>

            <!-- Search Hint -->
            <div class="alert alert-info text-sm">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>Bitte Suchbegriff eingeben (min. 2 Zeichen, max. 100 Ergebnisse)</span>
            </div>

            <!-- World Search -->
            <input
              v-model="worldSearchQuery"
              type="text"
              placeholder="Search worlds..."
              class="input input-bordered w-full"
              @input="handleWorldSearch"
            />

            <!-- Loading State -->
            <div v-if="loadingWorlds" class="flex justify-center py-8">
              <span class="loading loading-spinner loading-lg"></span>
            </div>

            <!-- Error State -->
            <div v-else-if="worldsError" class="alert alert-error">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
              <span>{{ worldsError }}</span>
            </div>

            <!-- World Grid -->
            <div v-else-if="worlds.length > 0" class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div
                v-for="world in worlds"
                :key="world.worldId"
                class="card bg-base-200 cursor-pointer transition-all"
                :class="{
                  'ring-2 ring-primary': selectedWorld?.worldId === world.worldId,
                  'hover:shadow-md': selectedWorld?.worldId !== world.worldId,
                  'opacity-50': !world.enabled
                }"
                @click="handleWorldSelect(world)"
              >
                <div class="card-body p-4">
                  <h3 class="font-semibold">{{ world.name }}</h3>
                  <p class="text-xs text-base-content/50 font-mono mt-1">
                    {{ world.worldId }}
                  </p>
                  <p v-if="world.description" class="text-sm text-base-content/70 mt-2">
                    {{ world.description }}
                  </p>
                  <div class="flex items-center gap-2 mt-2">
                    <span class="badge badge-sm" :class="world.enabled ? 'badge-success' : 'badge-error'">
                      {{ world.enabled ? 'Active' : 'Disabled' }}
                    </span>
                    <span v-if="selectedWorld?.worldId === world.worldId" class="badge badge-primary badge-sm">
                      Selected
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Empty State -->
            <div v-else-if="worldSearchQuery && worldSearchQuery.length >= 2" class="text-center py-8 text-base-content/70">
              No worlds found
            </div>
          </div>
        </div>

        <!-- Step 2: Login Type & Parameters (only shown when world selected) -->
        <div v-if="selectedWorld" class="card bg-base-100 shadow-xl">
          <div class="card-body">
            <h2 class="card-title">2. Login Configuration</h2>

            <!-- Login Type Tabs -->
            <div class="tabs tabs-boxed mb-4">
              <button
                class="tab"
                :class="{ 'tab-active': loginType === 'session' }"
                @click="loginType = 'session'"
              >
                Session Login
              </button>
              <button
                class="tab"
                :class="{ 'tab-active': loginType === 'agent' }"
                @click="loginType = 'agent'"
              >
                Agent Login
              </button>
            </div>

            <!-- Session Login Form -->
            <div v-if="loginType === 'session'" class="space-y-4">
              <!-- User ID Input -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">User ID</span>
                </label>
                <input
                  v-model="sessionUserId"
                  type="text"
                  placeholder="Enter user ID"
                  class="input input-bordered w-full"
                />
              </div>

              <!-- Character ID Input -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">Character ID</span>
                </label>
                <input
                  v-model="sessionCharacterId"
                  type="text"
                  placeholder="Enter character ID"
                  class="input input-bordered w-full"
                />
              </div>

              <!-- Actor Selection -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">Actor Type</span>
                </label>
                <select v-model="selectedActor" class="select select-bordered w-full">
                  <option value="PLAYER">Player</option>
                  <option value="EDITOR">Editor</option>
                  <option value="SUPPORT">Support</option>
                </select>
              </div>
            </div>

            <!-- Agent Login Form -->
            <div v-if="loginType === 'agent'" class="space-y-4">
              <!-- User ID Input -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">User ID</span>
                </label>
                <input
                  v-model="agentUserId"
                  type="text"
                  placeholder="Enter user ID"
                  class="input input-bordered w-full"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Step 3: Login Action (only shown when form is valid) -->
        <div v-if="canLogin" class="card bg-base-100 shadow-xl">
          <div class="card-body">
            <h2 class="card-title">3. Login</h2>

            <!-- Login Error -->
            <div v-if="loginError" class="alert alert-error">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
              <span>{{ loginError }}</span>
            </div>

            <!-- Login Summary -->
            <div class="bg-base-200 p-4 rounded-lg space-y-2">
              <div><strong>World:</strong> {{ selectedWorld.name }}</div>
              <div><strong>Type:</strong> {{ loginType === 'session' ? 'Session' : 'Agent' }}</div>
              <div v-if="loginType === 'session'">
                <strong>User ID:</strong> {{ sessionUserId }}
              </div>
              <div v-if="loginType === 'session'">
                <strong>Character ID:</strong> {{ sessionCharacterId }}
              </div>
              <div v-if="loginType === 'session'">
                <strong>Actor:</strong> {{ selectedActor }}
              </div>
              <div v-if="loginType === 'agent'">
                <strong>User ID:</strong> {{ agentUserId }}
              </div>
            </div>

            <!-- Login Button -->
            <button
              class="btn btn-primary btn-lg w-full mt-4"
              :disabled="loggingIn"
              @click="handleLogin"
            >
              <span v-if="loggingIn" class="loading loading-spinner loading-sm"></span>
              <span v-else>Login</span>
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- Footer -->
    <footer class="footer footer-center p-4 bg-base-300 text-base-content">
      <div>
        <p>Nimbus Dev Login v1.0.0</p>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import {
  devLoginService,
  type World,
  type ActorType,
  type LoginRequest
} from './services/DevLoginService';

// ===== STATE =====

// Worlds
const worlds = ref<World[]>([]);
const loadingWorlds = ref(false);
const worldsError = ref<string | null>(null);
const worldSearchQuery = ref('');
const selectedWorld = ref<World | null>(null);

// Login Type
const loginType = ref<'session' | 'agent'>('session');

// Session Login
const sessionUserId = ref('');
const sessionCharacterId = ref('');
const selectedActor = ref<ActorType>('PLAYER');

// Agent Login
const agentUserId = ref('');

// Login
const loggingIn = ref(false);
const loginError = ref<string | null>(null);

// ===== COMPUTED =====

// Check if login form is valid
const canLogin = computed(() => {
  if (!selectedWorld.value) return false;

  if (loginType.value === 'session') {
    return sessionUserId.value.trim() !== '' &&
           sessionCharacterId.value.trim() !== '' &&
           selectedActor.value !== null;
  } else {
    return agentUserId.value.trim() !== '';
  }
});

// ===== METHODS =====

/**
 * Handle world search (debounced)
 */
let worldSearchTimeout: ReturnType<typeof setTimeout> | null = null;
const handleWorldSearch = () => {
  if (worldSearchTimeout) {
    clearTimeout(worldSearchTimeout);
  }

  worldSearchTimeout = setTimeout(() => {
    loadWorlds();
  }, 300);
};

/**
 * Load worlds with search filter
 */
const loadWorlds = async () => {
  if (!worldSearchQuery.value || worldSearchQuery.value.length < 2) {
    worlds.value = [];
    return;
  }

  loadingWorlds.value = true;
  worldsError.value = null;

  try {
    worlds.value = await devLoginService.getWorlds(worldSearchQuery.value, 100);
  } catch (e) {
    worldsError.value = e instanceof Error ? e.message : 'Failed to load worlds';
    console.error('[DevLogin] Failed to load worlds:', e);
  } finally {
    loadingWorlds.value = false;
  }
};

/**
 * Handle world selection
 */
const handleWorldSelect = (world: World) => {
  selectedWorld.value = world;

  // Reset login error when changing worlds
  loginError.value = null;
};

/**
 * Handle login
 */
const handleLogin = async () => {
  if (!canLogin.value || !selectedWorld.value) return;

  loggingIn.value = true;
  loginError.value = null;

  try {
    // Build login request
    let request: LoginRequest;

    if (loginType.value === 'session') {
      request = {
        worldId: selectedWorld.value.worldId,
        agent: false,
        userId: sessionUserId.value.trim(),
        characterId: sessionCharacterId.value.trim(),
        actor: selectedActor.value,
      };
    } else {
      request = {
        worldId: selectedWorld.value.worldId,
        agent: true,
        userId: agentUserId.value.trim(),
      };
    }

    // Perform login
    const response = await devLoginService.login(request);

    console.log('[DevLogin] Login successful:', response);

    // Authorize with cookie URLs
    if (response.cookieUrls && response.cookieUrls.length > 0) {
      console.log('[DevLogin] Authorizing cookies:', response.cookieUrls);
      await devLoginService.authorize(response.cookieUrls, response.accessToken);
    }

    // Redirect to jump URL
    console.log('[DevLogin] Redirecting to:', response.jumpUrl);
    window.location.href = response.jumpUrl;
  } catch (e) {
    loginError.value = e instanceof Error ? e.message : 'Login failed';
    console.error('[DevLogin] Login failed:', e);
  } finally {
    loggingIn.value = false;
  }
};

// ===== WATCHERS =====

/**
 * When login type changes, clear selections and errors
 */
watch(loginType, () => {
  loginError.value = null;
});
</script>

<style scoped>
/* Add any component-specific styles here if needed */
</style>
