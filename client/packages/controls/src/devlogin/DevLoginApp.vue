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
              <span>Zeigt die ersten 100 Welten. Suche zum Filtern verwenden.</span>
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
            <div v-else class="text-center py-8 text-base-content/70">
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
              <!-- User Search -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">User (max. 100 shown, search to filter)</span>
                </label>
                <input
                  v-model="sessionUserSearchQuery"
                  type="text"
                  placeholder="Search users..."
                  class="input input-bordered w-full"
                  @input="handleSessionUserSearch"
                />
              </div>

              <!-- Loading Users -->
              <div v-if="loadingSessionUsers" class="flex justify-center py-4">
                <span class="loading loading-spinner loading-md"></span>
              </div>

              <!-- Users Error -->
              <div v-else-if="sessionUsersError" class="alert alert-error alert-sm">
                <span class="text-xs">{{ sessionUsersError }}</span>
              </div>

              <!-- User List -->
              <div v-else-if="sessionUsers.length > 0" class="space-y-2 max-h-64 overflow-y-auto">
                <div
                  v-for="user in sessionUsers"
                  :key="user.id"
                  class="p-3 rounded-lg cursor-pointer transition-all"
                  :class="{
                    'bg-primary text-primary-content': selectedSessionUser?.id === user.id,
                    'bg-base-200 hover:bg-base-300': selectedSessionUser?.id !== user.id
                  }"
                  @click="handleSessionUserSelect(user)"
                >
                  <div class="font-medium">{{ user.username }}</div>
                  <div v-if="user.email" class="text-xs opacity-70">{{ user.email }}</div>
                </div>
              </div>

              <!-- Characters Section (shown after user selected) -->
              <div v-if="selectedSessionUser" class="mt-4 pt-4 border-t border-base-300">
                <label class="label">
                  <span class="label-text">Characters</span>
                </label>

                <!-- Loading Characters -->
                <div v-if="loadingCharacters" class="flex justify-center py-4">
                  <span class="loading loading-spinner loading-md"></span>
                </div>

                <!-- Characters Error -->
                <div v-else-if="charactersError" class="alert alert-error alert-sm">
                  <span class="text-xs">{{ charactersError }}</span>
                </div>

                <!-- Character List -->
                <div v-else-if="characters.length > 0" class="space-y-2">
                  <div
                    v-for="char in characters"
                    :key="char.id"
                    class="p-3 rounded-lg cursor-pointer transition-all"
                    :class="{
                      'bg-primary text-primary-content': selectedCharacter?.id === char.id,
                      'bg-base-200 hover:bg-base-300': selectedCharacter?.id !== char.id
                    }"
                    @click="selectedCharacter = char"
                  >
                    <div class="font-medium">{{ char.display || char.name }}</div>
                    <div class="text-xs opacity-70 font-mono">{{ char.id }}</div>
                  </div>
                </div>

                <!-- No Characters -->
                <div v-else class="text-center py-4 text-base-content/70 text-sm">
                  No characters found for this user
                </div>
              </div>

              <!-- Actor Selection (shown after character selected) -->
              <div v-if="selectedCharacter" class="form-control">
                <label class="label">
                  <span class="label-text">Actor Type</span>
                </label>
                <select v-model="selectedActor" class="select select-bordered w-full">
                  <option value="PLAYER">Player</option>
                  <option value="EDITOR">Editor</option>
                  <option value="SUPPORT">Support</option>
                </select>
              </div>

              <!-- View Distance Selection (shown after character selected) -->
              <div v-if="selectedCharacter" class="form-control">
                <label class="label">
                  <span class="label-text">View Distance</span>
                </label>
                <div class="flex gap-2 justify-center">
                  <button
                    v-for="distance in [2, 3, 4]"
                    :key="distance"
                    class="btn btn-sm flex-1"
                    :class="{ 'btn-primary': viewDistance === distance, 'btn-outline': viewDistance !== distance }"
                    @click="viewDistance = distance"
                  >
                    {{ distance }}
                  </button>
                </div>
              </div>

              <!-- Entry Point Selection (shown after character selected) -->
              <div v-if="selectedCharacter" class="form-control">
                <label class="label">
                  <span class="label-text">Entry Point (Spawn Location)</span>
                </label>

                <!-- Entry Point Options -->
                <div class="space-y-2">
                  <!-- Last Position -->
                  <label class="label cursor-pointer justify-start gap-3 bg-base-200 p-3 rounded-lg">
                    <input
                      type="radio"
                      name="entry-point"
                      class="radio radio-primary"
                      value="last"
                      v-model="entryPoint"
                    />
                    <div class="flex-1">
                      <span class="label-text font-medium">Last Position</span>
                      <div class="text-xs text-base-content/60 mt-1">
                        Spawn at last saved position (if available, otherwise world default)
                      </div>
                    </div>
                  </label>

                  <!-- Hex Grid Coordinates -->
                  <label class="label cursor-pointer justify-start gap-3 bg-base-200 p-3 rounded-lg">
                    <input
                      type="radio"
                      name="entry-point"
                      class="radio radio-primary"
                      value="grid"
                      v-model="entryPoint"
                    />
                    <div class="flex-1">
                      <span class="label-text font-medium">Hex Grid Coordinates</span>
                      <div class="text-xs text-base-content/60 mt-1">
                        Spawn at specific hex grid coordinates
                      </div>

                      <!-- Grid Coordinate Inputs (shown when grid option selected) -->
                      <div v-if="entryPoint === 'grid'" class="flex gap-2 mt-3" @click.stop>
                        <div class="form-control flex-1">
                          <label class="label py-1">
                            <span class="label-text text-xs">Q Coordinate</span>
                          </label>
                          <input
                            v-model="gridQ"
                            type="number"
                            placeholder="0"
                            class="input input-bordered input-sm w-full"
                          />
                        </div>
                        <div class="form-control flex-1">
                          <label class="label py-1">
                            <span class="label-text text-xs">R Coordinate</span>
                          </label>
                          <input
                            v-model="gridR"
                            type="number"
                            placeholder="0"
                            class="input input-bordered input-sm w-full"
                          />
                        </div>
                      </div>
                    </div>
                  </label>

                  <!-- World Default -->
                  <label class="label cursor-pointer justify-start gap-3 bg-base-200 p-3 rounded-lg">
                    <input
                      type="radio"
                      name="entry-point"
                      class="radio radio-primary"
                      value="world"
                      v-model="entryPoint"
                    />
                    <div class="flex-1">
                      <span class="label-text font-medium">World Default</span>
                      <div class="text-xs text-base-content/60 mt-1">
                        Use the world's default spawn point
                      </div>
                    </div>
                  </label>
                </div>
              </div>
            </div>

            <!-- Agent Login Form -->
            <div v-if="loginType === 'agent'" class="space-y-4">
              <!-- User Search -->
              <div class="form-control">
                <label class="label">
                  <span class="label-text">User (max. 100 shown, search to filter)</span>
                </label>
                <input
                  v-model="agentUserSearchQuery"
                  type="text"
                  placeholder="Search users..."
                  class="input input-bordered w-full"
                  @input="handleAgentUserSearch"
                />
              </div>

              <!-- Loading Users -->
              <div v-if="loadingAgentUsers" class="flex justify-center py-4">
                <span class="loading loading-spinner loading-md"></span>
              </div>

              <!-- Users Error -->
              <div v-else-if="agentUsersError" class="alert alert-error alert-sm">
                <span class="text-xs">{{ agentUsersError }}</span>
              </div>

              <!-- User List -->
              <div v-else-if="agentUsers.length > 0" class="space-y-2 max-h-64 overflow-y-auto">
                <div
                  v-for="user in agentUsers"
                  :key="user.id"
                  class="p-3 rounded-lg cursor-pointer transition-all"
                  :class="{
                    'bg-primary text-primary-content': selectedAgentUser?.id === user.id,
                    'bg-base-200 hover:bg-base-300': selectedAgentUser?.id !== user.id
                  }"
                  @click="selectedAgentUser = user"
                >
                  <div class="font-medium">{{ user.username }}</div>
                  <div v-if="user.email" class="text-xs opacity-70">{{ user.email }}</div>
                </div>
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
                <strong>User:</strong> {{ selectedSessionUser?.username }}
              </div>
              <div v-if="loginType === 'session'">
                <strong>Character:</strong> {{ selectedCharacter?.display || selectedCharacter?.name }}
              </div>
              <div v-if="loginType === 'session'">
                <strong>Actor:</strong> {{ selectedActor }}
              </div>
              <div v-if="loginType === 'session'">
                <strong>View Distance:</strong> {{ viewDistance }} (Render: {{ viewDistance - 1 }}, Unload: {{ viewDistance }})
              </div>
              <div v-if="loginType === 'agent'">
                <strong>User:</strong> {{ selectedAgentUser?.username }}
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from 'vue';
import {
  devLoginService,
  type World,
  type User,
  type Character,
  type ActorType,
  type LoginRequest
} from './services/DevLoginService';

// ===== LOCAL STORAGE KEYS =====
const STORAGE_KEY_WORLD = 'nimbus-devlogin-world';
const STORAGE_KEY_LOGIN_TYPE = 'nimbus-devlogin-logintype';
const STORAGE_KEY_SESSION_USER = 'nimbus-devlogin-session-user';
const STORAGE_KEY_CHARACTER = 'nimbus-devlogin-character';
const STORAGE_KEY_ACTOR = 'nimbus-devlogin-actor';
const STORAGE_KEY_AGENT_USER = 'nimbus-devlogin-agent-user';
const STORAGE_KEY_ENTRY_POINT = 'nimbus-devlogin-entrypoint';
const STORAGE_KEY_GRID_Q = 'nimbus-devlogin-grid-q';
const STORAGE_KEY_GRID_R = 'nimbus-devlogin-grid-r';
const STORAGE_KEY_VIEW_DISTANCE = 'nimbus-devlogin-view-distance';

// ===== STATE =====

// Worlds
const worlds = ref<World[]>([]);
const loadingWorlds = ref(false);
const worldsError = ref<string | null>(null);
const worldSearchQuery = ref('');
const selectedWorld = ref<World | null>(null);

// Login Type
const loginType = ref<'session' | 'agent'>('session');

// Session Login - Users
const sessionUsers = ref<User[]>([]);
const loadingSessionUsers = ref(false);
const sessionUsersError = ref<string | null>(null);
const sessionUserSearchQuery = ref('');
const selectedSessionUser = ref<User | null>(null);

// Session Login - Characters
const characters = ref<Character[]>([]);
const loadingCharacters = ref(false);
const charactersError = ref<string | null>(null);
const selectedCharacter = ref<Character | null>(null);
const selectedActor = ref<ActorType>('PLAYER');

// Session Login - Entry Point
const entryPoint = ref<'last' | 'grid' | 'world'>('world');
const gridQ = ref<string>('0');
const gridR = ref<string>('0');

// Session Login - View Distance
const viewDistance = ref<number>(2);

// Agent Login - Users
const agentUsers = ref<User[]>([]);
const loadingAgentUsers = ref(false);
const agentUsersError = ref<string | null>(null);
const agentUserSearchQuery = ref('');
const selectedAgentUser = ref<User | null>(null);

// Login
const loggingIn = ref(false);
const loginError = ref<string | null>(null);

// ===== COMPUTED =====

// Check if login form is valid
const canLogin = computed(() => {
  if (!selectedWorld.value) return false;

  if (loginType.value === 'session') {
    return selectedSessionUser.value !== null &&
           selectedCharacter.value !== null &&
           selectedActor.value !== null;
  } else {
    return selectedAgentUser.value !== null;
  }
});

// ===== METHODS =====

/**
 * Save state to localStorage
 */
const saveToLocalStorage = () => {
  try {
    if (selectedWorld.value) {
      localStorage.setItem(STORAGE_KEY_WORLD, JSON.stringify(selectedWorld.value));
    }
    localStorage.setItem(STORAGE_KEY_LOGIN_TYPE, loginType.value);

    if (selectedSessionUser.value) {
      localStorage.setItem(STORAGE_KEY_SESSION_USER, JSON.stringify(selectedSessionUser.value));
    }
    if (selectedCharacter.value) {
      localStorage.setItem(STORAGE_KEY_CHARACTER, JSON.stringify(selectedCharacter.value));
    }
    if (selectedActor.value) {
      localStorage.setItem(STORAGE_KEY_ACTOR, selectedActor.value);
    }
    if (selectedAgentUser.value) {
      localStorage.setItem(STORAGE_KEY_AGENT_USER, JSON.stringify(selectedAgentUser.value));
    }

    // Save entry point settings
    localStorage.setItem(STORAGE_KEY_ENTRY_POINT, entryPoint.value);
    localStorage.setItem(STORAGE_KEY_GRID_Q, gridQ.value);
    localStorage.setItem(STORAGE_KEY_GRID_R, gridR.value);

    // Save view distance
    localStorage.setItem(STORAGE_KEY_VIEW_DISTANCE, viewDistance.value.toString());
  } catch (e) {
    console.error('[DevLogin] Failed to save to localStorage:', e);
  }
};

/**
 * Load state from localStorage
 */
const loadFromLocalStorage = async () => {
  try {
    // Load login type
    const savedLoginType = localStorage.getItem(STORAGE_KEY_LOGIN_TYPE);
    if (savedLoginType === 'session' || savedLoginType === 'agent') {
      loginType.value = savedLoginType;
    }

    // Load world
    const savedWorld = localStorage.getItem(STORAGE_KEY_WORLD);
    if (savedWorld) {
      const world = JSON.parse(savedWorld) as World;
      // Verify world still exists in loaded worlds
      const foundWorld = worlds.value.find(w => w.worldId === world.worldId);
      if (foundWorld) {
        selectedWorld.value = foundWorld;
      }
    }

    // Load session user and characters
    if (loginType.value === 'session') {
      const savedSessionUser = localStorage.getItem(STORAGE_KEY_SESSION_USER);
      if (savedSessionUser) {
        selectedSessionUser.value = JSON.parse(savedSessionUser) as User;

        // Load session users list if empty (needed to show in UI)
        if (sessionUsers.value.length === 0) {
          await loadSessionUsers();
        }

        // Load characters for this user if world is selected
        if (selectedWorld.value && selectedSessionUser.value) {
          await loadCharacters(selectedSessionUser.value.username, selectedWorld.value.worldId);

          // Load character after characters are loaded
          const savedCharacter = localStorage.getItem(STORAGE_KEY_CHARACTER);
          if (savedCharacter) {
            const character = JSON.parse(savedCharacter) as Character;
            // Verify character still exists in loaded characters
            const foundChar = characters.value.find(c => c.id === character.id);
            if (foundChar) {
              selectedCharacter.value = foundChar;

              // Load actor AFTER character is selected (so the select element exists in DOM)
              // Use setTimeout to ensure DOM is fully rendered before setting actor value
              const savedActor = localStorage.getItem(STORAGE_KEY_ACTOR);
              if (savedActor) {
                setTimeout(() => {
                  selectedActor.value = savedActor as ActorType;
                }, 100);
              }

              // Load entry point settings
              const savedEntryPoint = localStorage.getItem(STORAGE_KEY_ENTRY_POINT);
              if (savedEntryPoint === 'last' || savedEntryPoint === 'grid' || savedEntryPoint === 'world') {
                entryPoint.value = savedEntryPoint;
              }

              const savedGridQ = localStorage.getItem(STORAGE_KEY_GRID_Q);
              if (savedGridQ) {
                gridQ.value = savedGridQ;
              }

              const savedGridR = localStorage.getItem(STORAGE_KEY_GRID_R);
              if (savedGridR) {
                gridR.value = savedGridR;
              }

              // Load view distance
              const savedViewDistance = localStorage.getItem(STORAGE_KEY_VIEW_DISTANCE);
              if (savedViewDistance) {
                const distance = parseInt(savedViewDistance, 10);
                if (distance === 2 || distance === 3 || distance === 4) {
                  viewDistance.value = distance;
                }
              }
            }
          }
        }
      }
    }

    // Load agent user
    if (loginType.value === 'agent') {
      const savedAgentUser = localStorage.getItem(STORAGE_KEY_AGENT_USER);
      if (savedAgentUser) {
        const agentUser = JSON.parse(savedAgentUser) as User;

        // Load agent users list if empty (needed to show in UI)
        if (agentUsers.value.length === 0) {
          await loadAgentUsers();
        }

        // Set selected user after list is loaded (with timeout to ensure DOM is ready)
        setTimeout(() => {
          selectedAgentUser.value = agentUser;
        }, 100);
      }
    }
  } catch (e) {
    console.error('[DevLogin] Failed to load from localStorage:', e);
  }
};

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
  loadingWorlds.value = true;
  worldsError.value = null;

  try {
    worlds.value = await devLoginService.getWorlds(worldSearchQuery.value || undefined, 100);
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

  // Reset form when changing worlds
  selectedSessionUser.value = null;
  selectedAgentUser.value = null;
  selectedCharacter.value = null;
  characters.value = [];
  loginError.value = null;
};

/**
 * Handle session user search (debounced)
 */
let sessionUserSearchTimeout: ReturnType<typeof setTimeout> | null = null;
const handleSessionUserSearch = () => {
  if (sessionUserSearchTimeout) {
    clearTimeout(sessionUserSearchTimeout);
  }

  sessionUserSearchTimeout = setTimeout(() => {
    loadSessionUsers();
  }, 300);
};

/**
 * Load session users
 */
const loadSessionUsers = async () => {
  loadingSessionUsers.value = true;
  sessionUsersError.value = null;

  try {
    sessionUsers.value = await devLoginService.getUsers(sessionUserSearchQuery.value || undefined, 100);
  } catch (e) {
    sessionUsersError.value = e instanceof Error ? e.message : 'Failed to load users';
    console.error('[DevLogin] Failed to load users:', e);
  } finally {
    loadingSessionUsers.value = false;
  }
};

/**
 * Handle session user selection
 */
const handleSessionUserSelect = async (user: User) => {
  selectedSessionUser.value = user;
  selectedCharacter.value = null;

  // Load characters for this user
  if (selectedWorld.value) {
    await loadCharacters(user.username, selectedWorld.value.worldId);
  }
};

/**
 * Load characters for user in world
 */
const loadCharacters = async (userId: string, worldId: string) => {
  loadingCharacters.value = true;
  charactersError.value = null;

  try {
    characters.value = await devLoginService.getCharacters(userId, worldId);
  } catch (e) {
    charactersError.value = e instanceof Error ? e.message : 'Failed to load characters';
    console.error('[DevLogin] Failed to load characters:', e);
  } finally {
    loadingCharacters.value = false;
  }
};

/**
 * Handle agent user search (debounced)
 */
let agentUserSearchTimeout: ReturnType<typeof setTimeout> | null = null;
const handleAgentUserSearch = () => {
  if (agentUserSearchTimeout) {
    clearTimeout(agentUserSearchTimeout);
  }

  agentUserSearchTimeout = setTimeout(() => {
    loadAgentUsers();
  }, 300);
};

/**
 * Load agent users
 */
const loadAgentUsers = async () => {
  loadingAgentUsers.value = true;
  agentUsersError.value = null;

  try {
    agentUsers.value = await devLoginService.getUsers(agentUserSearchQuery.value || undefined, 100);
  } catch (e) {
    agentUsersError.value = e instanceof Error ? e.message : 'Failed to load users';
    console.error('[DevLogin] Failed to load users:', e);
  } finally {
    loadingAgentUsers.value = false;
  }
};

/**
 * Handle login
 */
const handleLogin = async () => {
  if (!canLogin.value || !selectedWorld.value) return;

  // Save current selection to localStorage before login
  saveToLocalStorage();

  loggingIn.value = true;
  loginError.value = null;

  try {
    // Build login request
    let request: LoginRequest;

    if (loginType.value === 'session') {
      if (!selectedSessionUser.value || !selectedCharacter.value) {
        throw new Error('User and character required');
      }

      // Build entry point string
      let entryPointStr: string | undefined;
      if (entryPoint.value === 'last') {
        entryPointStr = 'last';
      } else if (entryPoint.value === 'grid') {
        entryPointStr = `grid:${gridQ.value},${gridR.value}`;
      } else if (entryPoint.value === 'world') {
        entryPointStr = 'world';
      }

      request = {
        worldId: selectedWorld.value.worldId,
        agent: false,
        userId: selectedSessionUser.value.username,
        characterId: selectedCharacter.value.id,
        actor: selectedActor.value,
        entryPoint: entryPointStr,
      };
    } else {
      if (!selectedAgentUser.value) {
        throw new Error('User required');
      }

      request = {
        worldId: selectedWorld.value.worldId,
        agent: true,
        userId: selectedAgentUser.value.username,
      };
    }

    // Perform login
    const response = await devLoginService.login(request);

    // Authorize with cookie URLs
    if (response.accessUrls && response.accessUrls.length > 0) {
      await devLoginService.authorize(response.accessUrls, response.accessToken);
    }

    // Append view distance parameters to jumpUrl (for session login only)
    let jumpUrl = response.jumpUrl;
    if (loginType.value === 'session') {
      const renderDistance = viewDistance.value - 1;
      const unloadDistance = viewDistance.value;
      const separator = jumpUrl.includes('?') ? '&' : '?';
      jumpUrl = `${jumpUrl}${separator}renderDistance=${renderDistance}&unloadDistance=${unloadDistance}`;
    }

    // Redirect to jump URL
    window.location.href = jumpUrl;
  } catch (e) {
    loginError.value = e instanceof Error ? e.message : 'Login failed';
    console.error('[DevLogin] Login failed:', e);
  } finally {
    loggingIn.value = false;
  }
};

// ===== WATCHERS =====

/**
 * When login type changes, clear selections and load users
 */
watch(loginType, (newType, oldType) => {
  // Only clear selections if user manually changed the type (not on initial load)
  if (oldType !== undefined) {
    selectedSessionUser.value = null;
    selectedAgentUser.value = null;
    selectedCharacter.value = null;
    loginError.value = null;
  }

  // Load users when switching to a tab
  if (newType === 'session' && sessionUsers.value.length === 0) {
    loadSessionUsers();
  } else if (newType === 'agent' && agentUsers.value.length === 0) {
    loadAgentUsers();
  }
});

/**
 * When world is selected, load session users if on session tab
 */
watch(selectedWorld, (newWorld) => {
  if (newWorld && loginType.value === 'session' && sessionUsers.value.length === 0) {
    loadSessionUsers();
  }
});

// ===== LIFECYCLE =====

/**
 * Load initial data on mount
 */
onMounted(async () => {
  await loadWorlds(); // Load first 100 worlds on mount
  await loadFromLocalStorage(); // Restore previous selections
});
</script>

<style scoped>
/* Add any component-specific styles here if needed */
</style>
