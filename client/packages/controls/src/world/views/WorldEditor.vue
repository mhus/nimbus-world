<template>
  <div class="space-y-4">
    <!-- Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <button class="btn btn-ghost gap-2" @click="handleBack">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
          Back to List
        </button>
      </div>
      <h2 class="text-2xl font-bold">
        {{ isNew ? 'Create New World' : 'Edit World' }}
      </h2>
    </div>

    <!-- Hierarchy Info -->
    <div class="alert">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <div class="text-sm">
        <span class="font-medium">Hierarchy:</span>
        <span class="ml-2">region: {{ currentRegionId || '-' }}</span>
        <span class="mx-1">→</span>
        <span>world: {{ formData.worldId || '-' }}</span>
        <span class="mx-1">→</span>
        <span>zone: -</span>
        <span class="mx-1">→</span>
        <span>instance: -</span>
      </div>
    </div>

    <!-- Error State -->
    <div v-if="error" class="alert alert-error">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- System Information (Read-Only) -->
    <div v-if="!isNew" class="card bg-base-100 shadow-xl">
      <div class="card-body">
        <h3 class="card-title">System Information</h3>
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div><span class="font-medium">Database ID:</span> {{ (world as World).id || 'N/A' }}</div>
          <div><span class="font-medium">Region ID:</span> {{ currentRegionId || 'N/A' }}</div>
          <div><span class="font-medium">Created:</span> {{ formatDate((world as World).createdAt) }}</div>
          <div><span class="font-medium">Updated:</span> {{ formatDate((world as World).updatedAt) }}</div>
        </div>
      </div>
    </div>

    <!-- Edit Form -->
    <div class="space-y-6">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <form @submit.prevent="handleSave" class="space-y-4">
            <!-- World ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">World ID</span>
              </label>
              <input
                v-model="formData.worldId"
                type="text"
                placeholder="Enter unique world ID (e.g., main-world-1)"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique identifier for this world</span>
              </label>
            </div>

            <!-- Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Name</span>
              </label>
              <input
                v-model="formData.name"
                type="text"
                placeholder="Enter world name"
                class="input input-bordered w-full"
                required
              />
            </div>

            <!-- Description -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Description</span>
              </label>
              <textarea
                v-model="formData.description"
                placeholder="Enter world description"
                class="textarea textarea-bordered w-full"
                rows="3"
              ></textarea>
            </div>

            <!-- Parent World -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Parent World</span>
              </label>
              <input
                v-model="formData.parent"
                type="text"
                placeholder="Optional parent world reference"
                class="input input-bordered w-full"
              />
              <label class="label">
                <span class="label-text-alt">Optional reference to parent world/group</span>
              </label>
            </div>

            <!-- Enabled Status -->
            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Enabled</span>
                <input
                  v-model="formData.enabled"
                  type="checkbox"
                  class="toggle toggle-success"
                />
              </label>
            </div>

            <!-- Public Flag -->
            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Public</span>
                <input
                  v-model="formData.publicFlag"
                  type="checkbox"
                  class="toggle toggle-info"
                />
              </label>
              <label class="label">
                <span class="label-text-alt">Allow public access to this world</span>
              </label>
            </div>

            <!-- Instanceable Flag -->
            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text font-medium">Instanceable</span>
                <input
                  v-model="formData.instanceable"
                  type="checkbox"
                  class="toggle toggle-warning"
                />
              </label>
              <label class="label">
                <span class="label-text-alt">Allow players to create instances (copies) of this world</span>
              </label>
            </div>

          </form>
        </div>
      </div>

      <!-- Generation Settings Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">World Generation Settings</h3>
          <div class="space-y-4">
            <!-- Ground Level -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Ground Level (Y)</span>
              </label>
              <input
                v-model.number="formData.groundLevel"
                type="number"
                placeholder="0"
                class="input input-bordered w-full"
              />
              <label class="label">
                <span class="label-text-alt">Default Y coordinate for ground generation</span>
              </label>
            </div>

            <!-- Water Level -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Water Level (Y)</span>
              </label>
              <input
                v-model.number="formData.waterLevel"
                type="number"
                placeholder="Optional"
                class="input input-bordered w-full"
              />
              <label class="label">
                <span class="label-text-alt">Y coordinate for water surface (optional)</span>
              </label>
            </div>

            <!-- Ground Block Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Ground Block Type</span>
              </label>
              <input
                v-model="formData.groundBlockType"
                type="text"
                placeholder="r/grass"
                class="input input-bordered w-full"
              />
            </div>

            <!-- Water Block Type -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Water Block Type</span>
              </label>
              <input
                v-model="formData.waterBlockType"
                type="text"
                placeholder="r/ocean"
                class="input input-bordered w-full"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Access Control Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Access Control</h3>
          <div class="space-y-4">

            <!-- Owner -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Owners</span>
              </label>
              <div class="flex flex-wrap gap-2 mb-2">
                <span v-for="userId in formData.owner" :key="userId"
                      class="badge badge-lg badge-primary gap-2">
                  {{ userId }}
                  <button type="button" @click="removeFromSet('owner', userId)"
                          class="btn btn-ghost btn-xs">✕</button>
                </span>
              </div>
              <div class="join w-full">
                <input v-model="newOwner" type="text" placeholder="Add user ID..."
                       class="input input-bordered join-item flex-1"
                       @keyup.enter="addToSet('owner')" />
                <button type="button" @click="addToSet('owner')"
                        class="btn btn-primary join-item">Add</button>
              </div>
            </div>

            <!-- Editor -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Editors</span>
              </label>
              <div class="flex flex-wrap gap-2 mb-2">
                <span v-for="userId in formData.editor" :key="userId"
                      class="badge badge-lg badge-secondary gap-2">
                  {{ userId }}
                  <button type="button" @click="removeFromSet('editor', userId)"
                          class="btn btn-ghost btn-xs">✕</button>
                </span>
              </div>
              <div class="join w-full">
                <input v-model="newEditor" type="text" placeholder="Add user ID..."
                       class="input input-bordered join-item flex-1"
                       @keyup.enter="addToSet('editor')" />
                <button type="button" @click="addToSet('editor')"
                        class="btn btn-secondary join-item">Add</button>
              </div>
            </div>

            <!-- Supporter -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Supporters</span>
              </label>
              <div class="flex flex-wrap gap-2 mb-2">
                <span v-for="userId in formData.supporter" :key="userId"
                      class="badge badge-lg badge-accent gap-2">
                  {{ userId }}
                  <button type="button" @click="removeFromSet('supporter', userId)"
                          class="btn btn-ghost btn-xs">✕</button>
                </span>
              </div>
              <div class="join w-full">
                <input v-model="newSupporter" type="text" placeholder="Add user ID..."
                       class="input input-bordered join-item flex-1"
                       @keyup.enter="addToSet('supporter')" />
                <button type="button" @click="addToSet('supporter')"
                        class="btn btn-accent join-item">Add</button>
              </div>
            </div>

            <!-- Player -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Players</span>
              </label>
              <div class="flex flex-wrap gap-2 mb-2">
                <span v-for="userId in formData.player" :key="userId"
                      :class="userId === '*' ? 'badge badge-lg badge-warning gap-2' : 'badge badge-lg badge-info gap-2'">
                  {{ userId === '*' ? 'All Players (*)' : userId }}
                  <button type="button" @click="removeFromSet('player', userId)"
                          class="btn btn-ghost btn-xs">✕</button>
                </span>
              </div>
              <div class="join w-full">
                <input v-model="newPlayer" type="text" placeholder="Add user ID or '*' for all..."
                       class="input input-bordered join-item flex-1"
                       @keyup.enter="addToSet('player')" />
                <button type="button" @click="addToSet('player')"
                        class="btn btn-info join-item">Add</button>
              </div>
              <label class="label">
                <span class="label-text-alt">Use '*' to allow all players</span>
              </label>
            </div>

          </div>
        </div>
      </div>

      <!-- World Info Settings Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">World Info Settings</h3>

          <!-- Tab Navigation -->
          <div class="tabs tabs-boxed">
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'basic'}"
               @click="activeWorldInfoTab = 'basic'">Basic</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'boundaries'}"
               @click="activeWorldInfoTab = 'boundaries'">Boundaries</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'entryPoint'}"
               @click="activeWorldInfoTab = 'entryPoint'">Entry Point</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'visual'}"
               @click="activeWorldInfoTab = 'visual'">Visual</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'gameplay'}"
               @click="activeWorldInfoTab = 'gameplay'">Gameplay</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'environment'}"
               @click="activeWorldInfoTab = 'environment'">Environment</a>
            <a class="tab" :class="{'tab-active': activeWorldInfoTab === 'time'}"
               @click="activeWorldInfoTab = 'time'">Time System</a>
          </div>

          <!-- Tab: Basic -->
          <div v-show="activeWorldInfoTab === 'basic'" class="space-y-4 mt-4">
            <div class="form-control">
              <label class="label"><span class="label-text">Chunk Size</span></label>
              <input v-model.number="formData.publicData.chunkSize" type="number"
                     class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Size of chunks in blocks (default: 16)</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Hex Grid Size</span></label>
              <input v-model.number="formData.publicData.hexGridSize" type="number"
                     class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Size of hexagonal grid (default: 16)</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">World Icon</span></label>
              <input v-model="formData.publicData.worldIcon" type="text"
                     placeholder="Asset path (e.g., textures/world-icon.png)"
                     class="input input-bordered" />
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Status</span></label>
              <select v-model.number="formData.publicData.status" class="select select-bordered">
                <option :value="0">Active</option>
                <option :value="1">Inactive</option>
                <option :value="2">Maintenance</option>
              </select>
            </div>
          </div>

          <!-- Tab: Boundaries -->
          <div v-show="activeWorldInfoTab === 'boundaries'" class="space-y-4 mt-4">
            <p class="text-sm text-base-content/70">Define the physical boundaries of the world (bounding box).</p>

            <!-- Start Position -->
            <div class="divider">Start Position (Min)</div>
            <div class="grid grid-cols-3 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Start X</span></label>
                <input v-model.number="formData.publicData.start.x" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Minimum X coordinate</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Start Y</span></label>
                <input v-model.number="formData.publicData.start.y" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Minimum Y coordinate</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Start Z</span></label>
                <input v-model.number="formData.publicData.start.z" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Minimum Z coordinate</span>
                </label>
              </div>
            </div>

            <!-- Stop Position -->
            <div class="divider">Stop Position (Max)</div>
            <div class="grid grid-cols-3 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Stop X</span></label>
                <input v-model.number="formData.publicData.stop.x" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Maximum X coordinate</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Stop Y</span></label>
                <input v-model.number="formData.publicData.stop.y" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Maximum Y coordinate</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Stop Z</span></label>
                <input v-model.number="formData.publicData.stop.z" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Maximum Z coordinate</span>
                </label>
              </div>
            </div>

            <!-- Calculated World Size -->
            <div class="divider">World Size</div>
            <div class="stats shadow">
              <div class="stat">
                <div class="stat-title">Width (X)</div>
                <div class="stat-value text-primary">{{ Math.abs(formData.publicData.stop.x - formData.publicData.start.x) }}</div>
                <div class="stat-desc">blocks</div>
              </div>
              <div class="stat">
                <div class="stat-title">Height (Y)</div>
                <div class="stat-value text-secondary">{{ Math.abs(formData.publicData.stop.y - formData.publicData.start.y) }}</div>
                <div class="stat-desc">blocks</div>
              </div>
              <div class="stat">
                <div class="stat-title">Depth (Z)</div>
                <div class="stat-value text-accent">{{ Math.abs(formData.publicData.stop.z - formData.publicData.start.z) }}</div>
                <div class="stat-desc">blocks</div>
              </div>
            </div>
          </div>

          <!-- Tab: Entry Point -->
          <div v-show="activeWorldInfoTab === 'entryPoint'" class="space-y-4 mt-4">
            <p class="text-sm text-base-content/70">Define the spawn point for new players entering the world.</p>

            <!-- Area Position -->
            <div class="divider">Area Position</div>
            <div class="grid grid-cols-3 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Position X</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.position.x" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">X coordinate of spawn area</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Position Y</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.position.y" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Y coordinate of spawn area</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Position Z</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.position.z" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Z coordinate of spawn area</span>
                </label>
              </div>
            </div>

            <!-- Area Size -->
            <div class="divider">Area Size</div>
            <div class="grid grid-cols-3 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Size X</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.size.x" type="number"
                       min="1" class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Width of spawn area</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Size Y</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.size.y" type="number"
                       min="1" class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Height of spawn area</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Size Z</span></label>
                <input v-model.number="formData.publicData.entryPoint.area.size.z" type="number"
                       min="1" class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Depth of spawn area</span>
                </label>
              </div>
            </div>

            <!-- Hex Grid -->
            <div class="divider">Hex Grid Coordinates</div>
            <div class="grid grid-cols-2 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Grid Q</span></label>
                <input v-model.number="formData.publicData.entryPoint.grid.q" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Hex grid Q coordinate</span>
                </label>
              </div>
              <div class="form-control">
                <label class="label"><span class="label-text">Grid R</span></label>
                <input v-model.number="formData.publicData.entryPoint.grid.r" type="number"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Hex grid R coordinate</span>
                </label>
              </div>
            </div>

            <!-- Info Box -->
            <div class="alert alert-info">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div>
                <div class="font-bold">Entry Point Summary</div>
                <div class="text-sm mt-1">
                  Players will spawn within the area from
                  ({{ formData.publicData.entryPoint.area.position.x }}, {{ formData.publicData.entryPoint.area.position.y }}, {{ formData.publicData.entryPoint.area.position.z }})
                  to
                  ({{ formData.publicData.entryPoint.area.position.x + formData.publicData.entryPoint.area.size.x }},
                   {{ formData.publicData.entryPoint.area.position.y + formData.publicData.entryPoint.area.size.y }},
                   {{ formData.publicData.entryPoint.area.position.z + formData.publicData.entryPoint.area.size.z }})
                  on hex grid ({{ formData.publicData.entryPoint.grid.q }}, {{ formData.publicData.entryPoint.grid.r }}).
                </div>
              </div>
            </div>
          </div>

          <!-- Tab: Visual -->
          <div v-show="activeWorldInfoTab === 'visual'" class="space-y-4 mt-4">
            <div class="form-control">
              <label class="label"><span class="label-text">Season Status</span></label>
              <select v-model.number="formData.publicData.seasonStatus" class="select select-bordered">
                <option :value="0">None</option>
                <option :value="1">Winter</option>
                <option :value="2">Spring</option>
                <option :value="3">Summer</option>
                <option :value="4">Autumn</option>
              </select>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Season Progress (0-1)</span></label>
              <input v-model.number="formData.publicData.seasonProgress" type="number"
                     step="0.01" min="0" max="1" class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Progress within the current season (0.0 to 1.0)</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Splash Screen</span></label>
              <input v-model="formData.publicData.splashScreen" type="text"
                     placeholder="Asset path for splash screen image"
                     class="input input-bordered" />
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Splash Screen Audio</span></label>
              <input v-model="formData.publicData.splashScreenAudio" type="text"
                     placeholder="Asset path for splash screen audio"
                     class="input input-bordered" />
            </div>
          </div>

          <!-- Tab: Gameplay -->
          <div v-show="activeWorldInfoTab === 'gameplay'" class="space-y-4 mt-4">
            <div class="form-control">
              <label class="label"><span class="label-text">Max Players</span></label>
              <input v-model.number="formData.publicData.settings.maxPlayers" type="number"
                     min="1" class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Maximum number of concurrent players</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text">Allow Guests</span>
                <input v-model="formData.publicData.settings.allowGuests" type="checkbox"
                       class="toggle" />
              </label>
              <label class="label">
                <span class="label-text-alt">Allow non-authenticated users to join</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text">PvP Enabled</span>
                <input v-model="formData.publicData.settings.pvpEnabled" type="checkbox"
                       class="toggle toggle-warning" />
              </label>
              <label class="label">
                <span class="label-text-alt">Enable player-vs-player combat</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Ping Interval (ms)</span></label>
              <input v-model.number="formData.publicData.settings.pingInterval" type="number"
                     min="1000" step="1000" class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Network ping interval in milliseconds</span>
              </label>
            </div>
            <div class="form-control">
              <label class="label"><span class="label-text">Default Movement Mode</span></label>
              <input v-model="formData.publicData.settings.defaultMovementMode" type="text"
                     placeholder="e.g., walk, fly, spectate"
                     class="input input-bordered" />
            </div>
          </div>

          <!-- Tab: Environment -->
          <div v-show="activeWorldInfoTab === 'environment'" class="space-y-4 mt-4">

            <!-- Clear Color -->
            <div class="form-control">
              <label class="label"><span class="label-text">Clear Color (RGB)</span></label>
              <div class="grid grid-cols-3 gap-2">
                <input v-model.number="formData.publicData.settings.environment.clearColor.r"
                       type="number" step="0.01" min="0" max="1" placeholder="R"
                       class="input input-bordered" />
                <input v-model.number="formData.publicData.settings.environment.clearColor.g"
                       type="number" step="0.01" min="0" max="1" placeholder="G"
                       class="input input-bordered" />
                <input v-model.number="formData.publicData.settings.environment.clearColor.b"
                       type="number" step="0.01" min="0" max="1" placeholder="B"
                       class="input input-bordered" />
              </div>
              <label class="label">
                <span class="label-text-alt">Background clear color (RGB values 0.0 to 1.0)</span>
              </label>
            </div>

            <!-- Camera Max Z -->
            <div class="form-control">
              <label class="label"><span class="label-text">Camera Max Z</span></label>
              <input v-model.number="formData.publicData.settings.environment.cameraMaxZ"
                     type="number" class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">Maximum camera distance</span>
              </label>
            </div>

            <!-- Sun Settings Collapsible -->
            <div class="collapse collapse-arrow bg-base-200">
              <input type="checkbox" />
              <div class="collapse-title font-medium">Sun Settings</div>
              <div class="collapse-content space-y-2">
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-4">
                    <span class="label-text">Sun Enabled</span>
                    <input v-model="formData.publicData.settings.environment.sunEnabled"
                           type="checkbox" class="toggle" />
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Sun Texture</span></label>
                  <input v-model="formData.publicData.settings.environment.sunTexture"
                         type="text" placeholder="Asset path"
                         class="input input-bordered input-sm" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Sun Size</span></label>
                  <input v-model.number="formData.publicData.settings.environment.sunSize"
                         type="number" class="input input-bordered input-sm" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Sun Angle Y</span></label>
                  <input v-model.number="formData.publicData.settings.environment.sunAngleY"
                         type="number" step="0.1" class="input input-bordered input-sm" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Sun Elevation</span></label>
                  <input v-model.number="formData.publicData.settings.environment.sunElevation"
                         type="number" step="0.1" class="input input-bordered input-sm" />
                </div>
                <!-- Sun Color RGB -->
                <div class="form-control">
                  <label class="label"><span class="label-text">Sun Color (RGB)</span></label>
                  <div class="grid grid-cols-3 gap-2">
                    <input v-model.number="formData.publicData.settings.environment.sunColor.r"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                    <input v-model.number="formData.publicData.settings.environment.sunColor.g"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                    <input v-model.number="formData.publicData.settings.environment.sunColor.b"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                  </div>
                </div>
              </div>
            </div>

            <!-- SkyBox Settings Collapsible -->
            <div class="collapse collapse-arrow bg-base-200">
              <input type="checkbox" />
              <div class="collapse-title font-medium">SkyBox Settings</div>
              <div class="collapse-content space-y-2">
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-4">
                    <span class="label-text">SkyBox Enabled</span>
                    <input v-model="formData.publicData.settings.environment.skyBoxEnabled"
                           type="checkbox" class="toggle" />
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">SkyBox Mode</span></label>
                  <input v-model="formData.publicData.settings.environment.skyBoxMode"
                         type="text" placeholder="e.g., texture, color"
                         class="input input-bordered input-sm" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">SkyBox Color (RGB)</span></label>
                  <div class="grid grid-cols-3 gap-2">
                    <input v-model.number="formData.publicData.settings.environment.skyBoxColor.r"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                    <input v-model.number="formData.publicData.settings.environment.skyBoxColor.g"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                    <input v-model.number="formData.publicData.settings.environment.skyBoxColor.b"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                  </div>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">SkyBox Texture Path</span></label>
                  <input v-model="formData.publicData.settings.environment.skyBoxTexturePath"
                         type="text" placeholder="Asset path for skybox texture"
                         class="input input-bordered input-sm" />
                </div>
              </div>
            </div>

            <!-- Environment Scripts -->
            <div class="divider">Environment Scripts</div>
            <p class="text-sm text-base-content/70 mb-4">Map action names to scripts (optional). If not defined here, scripts will be started by action name directly.</p>

            <!-- Existing scripts -->
            <div v-if="formData.publicData.settings.environmentScripts && formData.publicData.settings.environmentScripts.length > 0" class="space-y-2 mb-4">
              <div
                v-for="(scriptDef, index) in formData.publicData.settings.environmentScripts"
                :key="`script-${index}`"
                class="flex gap-2 items-center"
              >
                <input
                  v-model="scriptDef.name"
                  type="text"
                  placeholder="Action name (e.g., custom_weather)"
                  class="input input-bordered input-sm flex-1"
                />
                <input
                  v-model="scriptDef.script"
                  type="text"
                  placeholder="Script name (e.g., weather_rain)"
                  class="input input-bordered input-sm flex-1"
                />
                <button
                  @click="removeEnvironmentScript(index)"
                  class="btn btn-xs btn-error btn-ghost"
                  title="Delete script"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Add new script button -->
            <button
              @click="addEnvironmentScript"
              type="button"
              class="btn btn-sm btn-primary"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Add Environment Script
            </button>

            <!-- Shadow Settings Collapsible -->
            <div class="collapse collapse-arrow bg-base-200">
              <input type="checkbox" />
              <div class="collapse-title font-medium">Shadow Settings</div>
              <div class="collapse-content space-y-2">
                <div class="form-control">
                  <label class="label cursor-pointer justify-start gap-4">
                    <span class="label-text">Shadows Enabled</span>
                    <input v-model="formData.publicData.settings.shadows.enabled"
                           type="checkbox" class="toggle" />
                  </label>
                  <label class="label">
                    <span class="label-text-alt">Enable shadow rendering system</span>
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Shadow Darkness (0.0 - 1.0)</span></label>
                  <input v-model.number="formData.publicData.settings.shadows.darkness"
                         type="number" step="0.1" min="0" max="1"
                         class="input input-bordered input-sm" />
                  <label class="label">
                    <span class="label-text-alt">0.0 = very dark shadows, 1.0 = no shadows (default: 0.6)</span>
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Max Shadow Distance (blocks)</span></label>
                  <input v-model.number="formData.publicData.settings.shadows.maxDistance"
                         type="number" min="1"
                         class="input input-bordered input-sm" />
                  <label class="label">
                    <span class="label-text-alt">Maximum distance for shadow culling (default: 50)</span>
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Shadow Map Size</span></label>
                  <select v-model.number="formData.publicData.settings.shadows.mapSize"
                          class="select select-bordered select-sm">
                    <option :value="512">512</option>
                    <option :value="1024">1024</option>
                    <option :value="2048">2048</option>
                    <option :value="4096">4096</option>
                  </select>
                  <label class="label">
                    <span class="label-text-alt">Shadow map resolution (default: 512)</span>
                  </label>
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Shadow Quality</span></label>
                  <select v-model="formData.publicData.settings.shadows.quality"
                          class="select select-bordered select-sm">
                    <option value="low">Low</option>
                    <option value="medium">Medium</option>
                    <option value="high">High</option>
                  </select>
                  <label class="label">
                    <span class="label-text-alt">Shadow quality preset (default: low)</span>
                  </label>
                </div>
              </div>
            </div>

            <!-- Moon Settings Collapsible -->
            <div class="collapse collapse-arrow bg-base-200 mt-4">
              <input type="checkbox" />
              <div class="collapse-title font-medium">Moon Settings (up to 3 moons)</div>
              <div class="collapse-content space-y-4">
                <!-- Moon 1 -->
                <div class="divider text-sm">Moon 1</div>
                <div class="space-y-2">
                  <div class="form-control">
                    <label class="label cursor-pointer justify-start gap-4">
                      <span class="label-text">Moon 1 Enabled</span>
                      <input v-model="formData.publicData.settings.environment.moons[0].enabled"
                             type="checkbox" class="toggle toggle-sm" />
                    </label>
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Size</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[0].size"
                           type="number" placeholder="60"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Position on Circle (0-360°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[0].positionOnCircle"
                           type="number" min="0" max="360"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Height Over Camera (-90 to 90°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[0].heightOverCamera"
                           type="number" min="-90" max="90"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Distance</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[0].distance"
                           type="number" placeholder="450"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Phase (0.0 = new, 1.0 = full)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[0].phase"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Texture Path</span></label>
                    <input v-model="formData.publicData.settings.environment.moons[0].texture"
                           type="text" placeholder="textures/moon/moon1.png"
                           class="input input-bordered input-sm" />
                  </div>
                </div>

                <!-- Moon 2 -->
                <div class="divider text-sm">Moon 2</div>
                <div class="space-y-2">
                  <div class="form-control">
                    <label class="label cursor-pointer justify-start gap-4">
                      <span class="label-text">Moon 2 Enabled</span>
                      <input v-model="formData.publicData.settings.environment.moons[1].enabled"
                             type="checkbox" class="toggle toggle-sm" />
                    </label>
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Size</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[1].size"
                           type="number" placeholder="60"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Position on Circle (0-360°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[1].positionOnCircle"
                           type="number" min="0" max="360"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Height Over Camera (-90 to 90°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[1].heightOverCamera"
                           type="number" min="-90" max="90"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Distance</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[1].distance"
                           type="number" placeholder="450"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Phase (0.0 = new, 1.0 = full)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[1].phase"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Texture Path</span></label>
                    <input v-model="formData.publicData.settings.environment.moons[1].texture"
                           type="text" placeholder="textures/moon/moon2.png"
                           class="input input-bordered input-sm" />
                  </div>
                </div>

                <!-- Moon 3 -->
                <div class="divider text-sm">Moon 3</div>
                <div class="space-y-2">
                  <div class="form-control">
                    <label class="label cursor-pointer justify-start gap-4">
                      <span class="label-text">Moon 3 Enabled</span>
                      <input v-model="formData.publicData.settings.environment.moons[2].enabled"
                             type="checkbox" class="toggle toggle-sm" />
                    </label>
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Size</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[2].size"
                           type="number" placeholder="60"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Position on Circle (0-360°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[2].positionOnCircle"
                           type="number" min="0" max="360"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Height Over Camera (-90 to 90°)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[2].heightOverCamera"
                           type="number" min="-90" max="90"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Distance</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[2].distance"
                           type="number" placeholder="450"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Phase (0.0 = new, 1.0 = full)</span></label>
                    <input v-model.number="formData.publicData.settings.environment.moons[2].phase"
                           type="number" step="0.01" min="0" max="1"
                           class="input input-bordered input-sm" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Texture Path</span></label>
                    <input v-model="formData.publicData.settings.environment.moons[2].texture"
                           type="text" placeholder="textures/moon/moon3.png"
                           class="input input-bordered input-sm" />
                  </div>
                </div>
              </div>
            </div>

            <!-- Celestial Bodies Automation -->
            <div class="divider">Celestial Bodies Automation</div>
            <p class="text-sm text-base-content/70 mb-4">Automatically update sun and moon positions based on world time</p>

            <div class="form-control">
              <label class="label cursor-pointer justify-start gap-4">
                <span class="label-text">Enable Automatic Celestial Bodies</span>
                <input v-model="formData.publicData.settings.worldTime.celestialBodies.enabled"
                       type="checkbox" class="toggle" />
              </label>
              <label class="label">
                <span class="label-text-alt">Automatically update sun and moon positions based on world time</span>
              </label>
            </div>

            <div v-if="formData.publicData.settings.worldTime.celestialBodies.enabled" class="space-y-4 ml-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Update Interval (seconds)</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.updateIntervalSeconds"
                       type="number" min="1" max="60" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">How often to update positions (default: 10)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Active Moons (0-3)</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.activeMoons"
                       type="number" min="0" max="3" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">Number of moons to animate (default: 0)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Sun Rotation Hours</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.sunRotationHours"
                       type="number" min="1" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">World hours for full sun rotation (default: 24 = one day)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Moon 0 Rotation Hours</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.moon0RotationHours"
                       type="number" min="1" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">World hours for full moon 0 rotation (default: 672 = 28 days)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Moon 1 Rotation Hours</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.moon1RotationHours"
                       type="number" min="1" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">World hours for full moon 1 rotation (default: 504 = 21 days)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Moon 2 Rotation Hours</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.celestialBodies.moon2RotationHours"
                       type="number" min="1" class="input input-bordered input-sm" />
                <label class="label">
                  <span class="label-text-alt">World hours for full moon 2 rotation (default: 336 = 14 days)</span>
                </label>
              </div>
            </div>

          </div>

          <!-- Tab: Time System -->
          <div v-show="activeWorldInfoTab === 'time'" class="space-y-4 mt-4">
            <p class="text-sm text-base-content/70">Configure the world's time system and calendar.</p>

            <!-- Time Scaling -->
            <div class="form-control">
              <label class="label"><span class="label-text">Minute Scaling</span></label>
              <input v-model.number="formData.publicData.settings.worldTime.minuteScaling"
                     type="number" min="1" class="input input-bordered" />
              <label class="label">
                <span class="label-text-alt">How fast time passes (1 = real-time)</span>
              </label>
            </div>

            <!-- Time Units -->
            <div class="divider">Time Units</div>

            <div class="grid grid-cols-2 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Minutes per Hour</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.minutesPerHour"
                       type="number" min="1" class="input input-bordered" />
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Hours per Day</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.hoursPerDay"
                       type="number" min="1" class="input input-bordered" />
              </div>
            </div>

            <!-- Calendar -->
            <div class="divider">Calendar</div>

            <div class="grid grid-cols-2 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Days per Month</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.daysPerMonth"
                       type="number" min="1" class="input input-bordered" />
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Months per Year</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.monthsPerYear"
                       type="number" min="1" class="input input-bordered" />
              </div>
            </div>

            <!-- Epoch -->
            <div class="divider">Time Sync</div>

            <div class="grid grid-cols-2 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Linux Epoch Delta (Minutes)</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.linuxEpocheDeltaMinutes"
                       type="number" class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Offset from Unix epoch in minutes for the current era</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Current Era</span></label>
                <input v-model.number="formData.publicData.settings.worldTime.currentEra"
                       type="number" min="1" class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Current era number</span>
                </label>
              </div>
            </div>

            <!-- Season Months -->
            <div class="divider">Seasons</div>
            <p class="text-sm text-base-content/70 mb-4">Define the starting month for each season (0-based, 0 = first month of year)</p>

            <div class="grid grid-cols-4 gap-4">
              <div class="form-control">
                <label class="label"><span class="label-text">Winter Start Month</span></label>
                <input v-model.number="formData.publicData.seasonMonths[0]"
                       type="number" min="0" :max="formData.publicData.settings.worldTime.monthsPerYear - 1"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Default: 0 (Jan)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Spring Start Month</span></label>
                <input v-model.number="formData.publicData.seasonMonths[1]"
                       type="number" min="0" :max="formData.publicData.settings.worldTime.monthsPerYear - 1"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Default: 3 (Apr)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Summer Start Month</span></label>
                <input v-model.number="formData.publicData.seasonMonths[2]"
                       type="number" min="0" :max="formData.publicData.settings.worldTime.monthsPerYear - 1"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Default: 6 (Jul)</span>
                </label>
              </div>

              <div class="form-control">
                <label class="label"><span class="label-text">Autumn Start Month</span></label>
                <input v-model.number="formData.publicData.seasonMonths[3]"
                       type="number" min="0" :max="formData.publicData.settings.worldTime.monthsPerYear - 1"
                       class="input input-bordered" />
                <label class="label">
                  <span class="label-text-alt">Default: 9 (Oct)</span>
                </label>
              </div>
            </div>

            <!-- Current World Time Display -->
            <div class="divider">Current World Time</div>
            <div class="alert" :class="formData.publicData.settings.worldTime.linuxEpocheDeltaMinutes === 0 ? 'alert-warning' : 'alert-info'">
              <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <div class="w-full">
                <div v-if="formData.publicData.settings.worldTime.linuxEpocheDeltaMinutes === 0" class="font-bold text-lg mb-3">
                  World Time: disabled
                </div>
                <template v-else>
                  <div class="font-bold text-lg mb-3">{{ currentWorldTimeFormatted }}</div>
                  <div class="grid grid-cols-5 gap-2 text-sm">
                    <div>
                      <span class="font-medium">Era:</span> {{ currentWorldTimeComponents.era }}
                    </div>
                    <div>
                      <span class="font-medium">Year:</span> {{ currentWorldTimeComponents.year }}
                    </div>
                    <div>
                      <span class="font-medium">Month:</span> {{ currentWorldTimeComponents.month }}
                    </div>
                    <div>
                      <span class="font-medium">Day:</span> {{ currentWorldTimeComponents.day }}
                    </div>
                    <div>
                      <span class="font-medium">Time:</span> {{ currentWorldTimeComponents.hour }}:{{ currentWorldTimeComponents.minute.toString().padStart(2, '0') }}
                    </div>
                  </div>
                  <div class="text-xs mt-2 opacity-70">Based on current configuration and Unix epoch offset</div>
                </template>
              </div>
            </div>
          </div>

        </div>
      </div>

      <!-- Action Buttons -->
      <div class="card-actions justify-end mt-6">
        <button type="button" class="btn btn-ghost" @click="handleBack">
          Cancel
        </button>
        <button type="submit" class="btn btn-primary" :disabled="saving" @click="handleSave">
          <span v-if="saving" class="loading loading-spinner loading-sm"></span>
          <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
        </button>
      </div>
    </div>

    <!-- Success Message -->
    <div v-if="successMessage" class="alert alert-success">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
      </svg>
      <span>{{ successMessage }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { worldServiceFrontend, type World, type WorldInfo } from '../services/WorldServiceFrontend';

const props = defineProps<{
  world: World | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentRegionId } = useRegion();

const isNew = computed(() => props.world === 'new');

const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

// Timer for world time updates
const currentTime = ref(Date.now());
let timeUpdateInterval: number | null = null;

// Computed: Current world time components
const currentWorldTimeComponents = computed(() => {
  const config = formData.value.publicData.settings.worldTime;

  // Check if world time is disabled
  if (config.linuxEpocheDeltaMinutes === 0) {
    return {
      era: 0,
      year: 0,
      month: 0,
      day: 0,
      hour: 0,
      minute: 0,
      formatted: 'disabled'
    };
  }

  // Calculate current world time (use currentTime.value to make it reactive)
  const now = currentTime.value;
  const realElapsedMinutes = now / (1000 * 60);
  const worldElapsedMinutes = realElapsedMinutes * config.minuteScaling;
  const currentWorldMinute = config.linuxEpocheDeltaMinutes + worldElapsedMinutes;

  // Format world time
  let remainingMinutes = Math.floor(currentWorldMinute);

  const minute = remainingMinutes % config.minutesPerHour;
  remainingMinutes = Math.floor(remainingMinutes / config.minutesPerHour);

  const hour = remainingMinutes % config.hoursPerDay;
  remainingMinutes = Math.floor(remainingMinutes / config.hoursPerDay);

  const day = (remainingMinutes % config.daysPerMonth) + 1;
  remainingMinutes = Math.floor(remainingMinutes / config.daysPerMonth);

  const month = (remainingMinutes % config.monthsPerYear) + 1;
  remainingMinutes = Math.floor(remainingMinutes / config.monthsPerYear);

  const year = remainingMinutes + 1;
  const era = config.currentEra;

  return {
    era,
    year,
    month,
    day,
    hour,
    minute,
    formatted: `@${era}, @${year}.${month}.${day}, ${hour}:${minute.toString().padStart(2, '0')}`
  };
});

const currentWorldTimeFormatted = computed(() => currentWorldTimeComponents.value.formatted);

// Helper refs for tag inputs
const newOwner = ref('');
const newEditor = ref('');
const newSupporter = ref('');
const newPlayer = ref('');

// Tab navigation for WorldInfo
const activeWorldInfoTab = ref<'basic' | 'boundaries' | 'entryPoint' | 'visual' | 'gameplay' | 'environment' | 'time'>('basic');

const formData = ref({
  worldId: '',
  name: '',
  description: '',
  enabled: true,
  publicFlag: false,
  instanceable: false,
  parent: '',
  owner: [] as string[],
  editor: [] as string[],
  supporter: [] as string[],
  player: [] as string[],
  groundLevel: 0,
  waterLevel: null as number | null,
  groundBlockType: 'r/grass',
  waterBlockType: 'r/ocean',
  publicData: {
    worldId: '',
    name: '',
    description: '',
    chunkSize: 16,
    hexGridSize: 16,
    worldIcon: '',
    status: 0,
    seasonStatus: 0,
    seasonProgress: 0,
    seasonMonths: [0, 3, 6, 9],
    splashScreen: '',
    splashScreenAudio: '',
    start: {
      x: -120000,
      y: -100,
      z: -128000
    },
    stop: {
      x: 120000,
      y: 200,
      z: 128000
    },
    entryPoint: {
      area: {
        position: { x: 0, y: 0, z: 0 },
        size: { x: 10, y: 10, z: 10 }
      },
      grid: { q: 0, r: 0 }
    },
    owner: {
      user: '',
      title: '',
      email: ''
    },
    settings: {
      maxPlayers: 100,
      allowGuests: true,
      pvpEnabled: false,
      pingInterval: 30000,
      defaultMovementMode: 'walk',
      environment: {
        clearColor: { r: 0.5, g: 0.7, b: 1.0 },
        cameraMaxZ: 1000,
        sunEnabled: true,
        sunTexture: '',
        sunSize: 100,
        sunAngleY: 0,
        sunElevation: 45,
        sunColor: { r: 1.0, g: 1.0, b: 0.9 },
        skyBoxEnabled: true,
        skyBoxMode: 'texture',
        skyBoxColor: { r: 0.5, g: 0.7, b: 1.0 },
        skyBoxTexturePath: '',
        moons: [
          { enabled: false, size: 60, positionOnCircle: 0, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
          { enabled: false, size: 60, positionOnCircle: 120, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
          { enabled: false, size: 60, positionOnCircle: 240, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' }
        ],
        environmentScripts: []
      },
      worldTime: {
        minuteScaling: 1,
        minutesPerHour: 60,
        hoursPerDay: 24,
        daysPerMonth: 30,
        monthsPerYear: 12,
        currentEra: 1,
        linuxEpocheDeltaMinutes: 0,
        celestialBodies: {
          enabled: false,
          updateIntervalSeconds: 10,
          activeMoons: 0,
          sunRotationHours: 24,
          moon0RotationHours: 672,
          moon1RotationHours: 504,
          moon2RotationHours: 336
        }
      },
      shadows: {
        enabled: false,
        darkness: 0.6,
        maxDistance: 50,
        mapSize: 512,
        quality: 'low'
      }
    }
  }
});

const loadWorld = () => {
  if (isNew.value) {
    formData.value = {
      worldId: '',
      name: '',
      description: '',
      enabled: true,
      publicFlag: false,
      instanceable: false,
      parent: '',
      owner: [],
      editor: [],
      supporter: [],
      player: [],
      groundLevel: 0,
      waterLevel: null,
      groundBlockType: 'r/grass',
      waterBlockType: 'r/ocean',
      publicData: {
        worldId: '',
        name: '',
        description: '',
        chunkSize: 16,
        hexGridSize: 16,
        worldIcon: '',
        status: 0,
        seasonStatus: 0,
        seasonProgress: 0,
        seasonMonths: [0, 3, 6, 9],
        splashScreen: '',
        splashScreenAudio: '',
        start: {
          x: -120000,
          y: -100,
          z: -128000
        },
        stop: {
          x: 120000,
          y: 200,
          z: 128000
        },
        entryPoint: {
          area: {
            position: { x: 0, y: 0, z: 0 },
            size: { x: 10, y: 10, z: 10 }
          },
          grid: { q: 0, r: 0 }
        },
        owner: {
          user: '',
          title: '',
          email: ''
        },
        settings: {
          maxPlayers: 100,
          allowGuests: true,
          pvpEnabled: false,
          pingInterval: 30000,
          defaultMovementMode: 'walk',
          environment: {
            clearColor: { r: 0.5, g: 0.7, b: 1.0 },
            cameraMaxZ: 1000,
            sunEnabled: true,
            sunTexture: '',
            sunSize: 100,
            sunAngleY: 0,
            sunElevation: 45,
            sunColor: { r: 1.0, g: 1.0, b: 0.9 },
            skyBoxEnabled: true,
            skyBoxMode: 'texture',
            skyBoxColor: { r: 0.5, g: 0.7, b: 1.0 },
            skyBoxTexturePath: '',
            moons: [
              { enabled: false, size: 60, positionOnCircle: 0, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
              { enabled: false, size: 60, positionOnCircle: 120, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
              { enabled: false, size: 60, positionOnCircle: 240, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' }
            ],
            environmentScripts: []
          },
          worldTime: {
            minuteScaling: 1,
            minutesPerHour: 60,
            hoursPerDay: 24,
            daysPerMonth: 30,
            monthsPerYear: 12,
            currentEra: 1,
            linuxEpocheDeltaMinutes: 0,
            celestialBodies: {
              enabled: false,
              updateIntervalSeconds: 10,
              activeMoons: 0,
              sunRotationHours: 24,
              moon0RotationHours: 672,
              moon1RotationHours: 504,
              moon2RotationHours: 336
            }
          },
          shadows: {
            enabled: false,
            darkness: 0.6,
            maxDistance: 50,
            mapSize: 512,
            quality: 'low'
          }
        }
      }
    };
    return;
  }

  // Load from props
  const world = props.world as World;

  // Helper to merge publicData with defaults
  const mergePublicData = (worldData: any) => {
    return {
      worldId: worldData?.worldId || '',
      name: worldData?.name || '',
      description: worldData?.description || '',
      chunkSize: worldData?.chunkSize || 16,
      hexGridSize: worldData?.hexGridSize || 16,
      worldIcon: worldData?.worldIcon || '',
      status: worldData?.status || 0,
      seasonStatus: worldData?.seasonStatus || 0,
      seasonProgress: worldData?.seasonProgress || 0,
      seasonMonths: worldData?.seasonMonths || [0, 3, 6, 9],
      splashScreen: worldData?.splashScreen || '',
      splashScreenAudio: worldData?.splashScreenAudio || '',
      start: worldData?.start || { x: -120000, y: -100, z: -128000 },
      stop: worldData?.stop || { x: 120000, y: 200, z: 128000 },
      entryPoint: worldData?.entryPoint || {
        area: {
          position: { x: 0, y: 0, z: 0 },
          size: { x: 10, y: 10, z: 10 }
        },
        grid: { q: 0, r: 0 }
      },
      owner: worldData?.owner || { user: '', title: '', email: '' },
      settings: {
        maxPlayers: worldData?.settings?.maxPlayers || 100,
        allowGuests: worldData?.settings?.allowGuests ?? true,
        pvpEnabled: worldData?.settings?.pvpEnabled ?? false,
        pingInterval: worldData?.settings?.pingInterval || 30000,
        defaultMovementMode: worldData?.settings?.defaultMovementMode || 'walk',
        environment: {
          clearColor: worldData?.settings?.environment?.clearColor || { r: 0.5, g: 0.7, b: 1.0 },
          cameraMaxZ: worldData?.settings?.environment?.cameraMaxZ || 1000,
          sunEnabled: worldData?.settings?.environment?.sunEnabled ?? true,
          sunTexture: worldData?.settings?.environment?.sunTexture || '',
          sunSize: worldData?.settings?.environment?.sunSize || 100,
          sunAngleY: worldData?.settings?.environment?.sunAngleY || 0,
          sunElevation: worldData?.settings?.environment?.sunElevation || 45,
          sunColor: worldData?.settings?.environment?.sunColor || { r: 1.0, g: 1.0, b: 0.9 },
          skyBoxEnabled: worldData?.settings?.environment?.skyBoxEnabled ?? true,
          skyBoxMode: worldData?.settings?.environment?.skyBoxMode || 'texture',
          skyBoxColor: worldData?.settings?.environment?.skyBoxColor || { r: 0.5, g: 0.7, b: 1.0 },
          skyBoxTexturePath: worldData?.settings?.environment?.skyBoxTexturePath || '',
          moons: worldData?.settings?.environment?.moons || [
            { enabled: false, size: 60, positionOnCircle: 0, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
            { enabled: false, size: 60, positionOnCircle: 120, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' },
            { enabled: false, size: 60, positionOnCircle: 240, heightOverCamera: 45, distance: 450, phase: 0.5, texture: '' }
          ],
          environmentScripts: worldData?.settings?.environment?.environmentScripts || []
        },
        worldTime: {
          minuteScaling: worldData?.settings?.worldTime?.minuteScaling || 1,
          minutesPerHour: worldData?.settings?.worldTime?.minutesPerHour || 60,
          hoursPerDay: worldData?.settings?.worldTime?.hoursPerDay || 24,
          daysPerMonth: worldData?.settings?.worldTime?.daysPerMonth || 30,
          monthsPerYear: worldData?.settings?.worldTime?.monthsPerYear || 12,
          currentEra: worldData?.settings?.worldTime?.currentEra || 1,
          linuxEpocheDeltaMinutes: worldData?.settings?.worldTime?.linuxEpocheDeltaMinutes || 0,
          celestialBodies: {
            enabled: worldData?.settings?.worldTime?.celestialBodies?.enabled || false,
            updateIntervalSeconds: worldData?.settings?.worldTime?.celestialBodies?.updateIntervalSeconds || 10,
            activeMoons: worldData?.settings?.worldTime?.celestialBodies?.activeMoons || 0,
            sunRotationHours: worldData?.settings?.worldTime?.celestialBodies?.sunRotationHours || 24,
            moon0RotationHours: worldData?.settings?.worldTime?.celestialBodies?.moon0RotationHours || 672,
            moon1RotationHours: worldData?.settings?.worldTime?.celestialBodies?.moon1RotationHours || 504,
            moon2RotationHours: worldData?.settings?.worldTime?.celestialBodies?.moon2RotationHours || 336
          }
        },
        shadows: {
          enabled: worldData?.settings?.shadows?.enabled ?? false,
          darkness: worldData?.settings?.shadows?.darkness ?? 0.6,
          maxDistance: worldData?.settings?.shadows?.maxDistance ?? 50,
          mapSize: worldData?.settings?.shadows?.mapSize ?? 512,
          quality: worldData?.settings?.shadows?.quality || 'low'
        }
      }
    };
  };

  formData.value = {
    worldId: world.worldId,
    name: world.name,
    description: world.description || '',
    enabled: world.enabled,
    publicFlag: world.publicFlag,
    instanceable: world.instanceable,
    parent: world.parent || '',
    owner: world.owner ? [...world.owner] : [],
    editor: world.editor ? [...world.editor] : [],
    supporter: world.supporter ? [...world.supporter] : [],
    player: world.player ? [...world.player] : [],
    groundLevel: world.groundLevel,
    waterLevel: world.waterLevel,
    groundBlockType: world.groundBlockType,
    waterBlockType: world.waterBlockType,
    publicData: mergePublicData(world.publicData)
  };
};

// Helper method to add user ID to a permission set
const addToSet = (field: 'owner' | 'editor' | 'supporter' | 'player') => {
  const refMap = {
    owner: newOwner,
    editor: newEditor,
    supporter: newSupporter,
    player: newPlayer
  };

  const value = refMap[field].value.trim();
  if (!value) return;

  if (!formData.value[field].includes(value)) {
    formData.value[field].push(value);
  }

  refMap[field].value = '';
};

// Helper method to remove user ID from a permission set
const removeFromSet = (field: 'owner' | 'editor' | 'supporter' | 'player', userId: string) => {
  const index = formData.value[field].indexOf(userId);
  if (index > -1) {
    formData.value[field].splice(index, 1);
  }
};

// Helper method to format date strings
const formatDate = (dateString: string | undefined): string => {
  if (!dateString) return 'N/A';
  try {
    return new Date(dateString).toLocaleString();
  } catch {
    return 'Invalid date';
  }
};

// Helper method to add environment script
const addEnvironmentScript = () => {
  if (!formData.value.publicData.settings.environmentScripts) {
    formData.value.publicData.settings.environmentScripts = [];
  }
  formData.value.publicData.settings.environmentScripts.push({
    name: '',
    script: ''
  });
};

// Helper method to remove environment script
const removeEnvironmentScript = (index: number) => {
  if (formData.value.publicData.settings.environmentScripts) {
    formData.value.publicData.settings.environmentScripts.splice(index, 1);
  }
};

const handleSave = async () => {
  if (!currentRegionId.value) {
    error.value = 'No region selected';
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    const request = {
      worldId: formData.value.worldId,
      name: formData.value.name,
      description: formData.value.description,
      enabled: formData.value.enabled,
      parent: formData.value.parent,
      instanceable: formData.value.instanceable,
      owner: formData.value.owner.length > 0 ? formData.value.owner : undefined,
      editor: formData.value.editor.length > 0 ? formData.value.editor : undefined,
      supporter: formData.value.supporter.length > 0 ? formData.value.supporter : undefined,
      player: formData.value.player.length > 0 ? formData.value.player : undefined,
      publicData: formData.value.publicData,
      groundLevel: formData.value.groundLevel,
      waterLevel: formData.value.waterLevel ?? undefined,
      groundBlockType: formData.value.groundBlockType,
      waterBlockType: formData.value.waterBlockType,
    };

    if (isNew.value) {
      await worldServiceFrontend.createWorld(currentRegionId.value, request);
      successMessage.value = 'World created successfully';
    } else {
      const world = props.world as World;
      await worldServiceFrontend.updateWorld(currentRegionId.value, world.worldId, request);
      successMessage.value = 'World updated successfully';
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save world';
    console.error('[WorldEditor] Failed to save world:', e);
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadWorld();

  // Start timer to update world time display every second
  timeUpdateInterval = window.setInterval(() => {
    currentTime.value = Date.now();
  }, 1000);
});

onUnmounted(() => {
  // Clear timer when component is unmounted
  if (timeUpdateInterval !== null) {
    window.clearInterval(timeUpdateInterval);
    timeUpdateInterval = null;
  }
});
</script>
