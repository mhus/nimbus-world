<template>
  <div class="space-y-6">
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
        {{ isNew ? 'Create New Character' : 'Edit Character' }}
      </h2>
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

    <!-- Edit Form -->
    <div v-else class="space-y-6">
      <!-- Basic Info Card -->
      <div class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Basic Information</h3>
          <form @submit.prevent="handleSave" class="space-y-4">
            <!-- User ID -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">User ID</span>
              </label>
              <select
                v-if="isNew"
                v-model="formData.userId"
                class="select select-bordered w-full"
                required
              >
                <option value="">Select a user...</option>
                <option v-for="user in users" :key="user.username" :value="user.username">
                  {{ user.username }} ({{ user.publicData?.displayName || 'No display name' }})
                </option>
              </select>
              <input
                v-else
                v-model="formData.userId"
                type="text"
                class="input input-bordered w-full"
                disabled
              />
              <label v-if="isNew && loadingUsers" class="label">
                <span class="label-text-alt">Loading users...</span>
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
                placeholder="Enter character name"
                class="input input-bordered w-full"
                :disabled="!isNew"
                required
              />
              <label class="label">
                <span class="label-text-alt">Unique name for this character</span>
              </label>
            </div>

            <!-- Display Name -->
            <div class="form-control">
              <label class="label">
                <span class="label-text font-medium">Display Name</span>
              </label>
              <input
                v-model="formData.display"
                type="text"
                placeholder="Enter display name"
                class="input input-bordered w-full"
                required
              />
              <label class="label">
                <span class="label-text-alt">Name shown to other players</span>
              </label>
            </div>

            <!-- Action Buttons -->
            <div class="card-actions justify-end mt-6">
              <button type="button" class="btn btn-ghost" @click="handleBack">
                Cancel
              </button>
              <button type="submit" class="btn btn-primary" :disabled="saving">
                <span v-if="saving" class="loading loading-spinner loading-sm"></span>
                <span v-else>{{ isNew ? 'Create' : 'Save' }}</span>
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Shortcuts Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between mb-4">
            <h3 class="card-title">Shortcuts (JSON)</h3>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="showShortcutsEditor = !showShortcutsEditor"
            >
              {{ showShortcutsEditor ? 'Hide' : 'Show' }} Editor
            </button>
          </div>

          <div v-if="showShortcutsEditor" class="space-y-2">
            <textarea
              v-model="shortcutsJson"
              class="textarea textarea-bordered w-full font-mono text-sm"
              rows="10"
              placeholder='{"key0": {"action": "...", ...}}'
            ></textarea>
            <div class="flex gap-2">
              <button
                type="button"
                class="btn btn-sm btn-primary"
                @click="handleSaveShortcuts"
              >
                Apply Changes
              </button>
              <button
                type="button"
                class="btn btn-sm btn-ghost"
                @click="handleResetShortcuts"
              >
                Reset
              </button>
            </div>
            <div v-if="shortcutsError" class="alert alert-error text-sm">
              <span>{{ shortcutsError }}</span>
            </div>
          </div>

          <div v-else class="text-sm text-base-content/70">
            Click "Show Editor" to edit shortcuts as JSON
          </div>
        </div>
      </div>

      <!-- Editor Shortcuts Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <div class="flex items-center justify-between mb-4">
            <h3 class="card-title">Editor Shortcuts (JSON)</h3>
            <button
              type="button"
              class="btn btn-sm btn-outline"
              @click="showEditorShortcutsEditor = !showEditorShortcutsEditor"
            >
              {{ showEditorShortcutsEditor ? 'Hide' : 'Show' }} Editor
            </button>
          </div>

          <div v-if="showEditorShortcutsEditor" class="space-y-2">
            <textarea
              v-model="editorShortcutsJson"
              class="textarea textarea-bordered w-full font-mono text-sm"
              rows="10"
              placeholder='{"key0": {"action": "...", ...}}'
            ></textarea>
            <div class="flex gap-2">
              <button
                type="button"
                class="btn btn-sm btn-primary"
                @click="handleSaveEditorShortcuts"
              >
                Apply Changes
              </button>
              <button
                type="button"
                class="btn btn-sm btn-ghost"
                @click="handleResetEditorShortcuts"
              >
                Reset
              </button>
            </div>
            <div v-if="editorShortcutsError" class="alert alert-error text-sm">
              <span>{{ editorShortcutsError }}</span>
            </div>
          </div>

          <div v-else class="text-sm text-base-content/70">
            Click "Show Editor" to edit editor shortcuts as JSON
          </div>
        </div>
      </div>

      <!-- Third Person Model Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Third Person Model</h3>

          <div class="form-control">
            <label class="label">
              <span class="label-text font-medium">Entity Model ID</span>
            </label>
            <div class="flex gap-2">
              <input
                v-model="thirdPersonModelId"
                type="text"
                placeholder="e.g., farmer1, warrior2, ..."
                class="input input-bordered flex-1"
                @input="handleModelIdChange"
              />
              <button
                type="button"
                class="btn btn-outline"
                @click="openModelSelector"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
                </svg>
                Select Model
              </button>
            </div>
            <label class="label">
              <span class="label-text-alt">Enter entity model ID or click "Select Model" to browse available entity models</span>
            </label>
          </div>

          <div v-if="thirdPersonModelId" class="mt-4">
            <div class="text-sm text-base-content/70 mb-2">Current Model:</div>
            <div class="bg-base-200 p-4 rounded-lg">
              <div class="font-mono text-sm">{{ thirdPersonModelId }}</div>
            </div>
          </div>

          <div v-else class="text-center py-4">
            <p class="text-base-content/50 text-sm">No third person model configured</p>
          </div>

          <!-- Model Modifiers -->
          <div v-if="thirdPersonModelId" class="mt-6">
            <h4 class="font-semibold mb-3">Model Modifiers</h4>

            <!-- Add New Modifier -->
            <div class="flex gap-2 mb-4">
              <input
                v-model="newModifierKey"
                type="text"
                placeholder="Modifier key (e.g., color, texture)"
                class="input input-bordered flex-1"
              />
              <input
                v-model="newModifierValue"
                type="text"
                placeholder="Modifier value"
                class="input input-bordered flex-1"
              />
              <button
                type="button"
                class="btn btn-secondary"
                @click="handleAddModifier"
                :disabled="!newModifierKey || !newModifierValue"
              >
                Add Modifier
              </button>
            </div>

            <!-- Existing Modifiers -->
            <div v-if="thirdPersonModelModifiers && Object.keys(thirdPersonModelModifiers).length > 0" class="overflow-x-auto">
              <table class="table table-zebra">
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Value</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(value, key) in thirdPersonModelModifiers" :key="key">
                    <td class="font-mono">{{ key }}</td>
                    <td>
                      <input
                        type="text"
                        :value="value"
                        @input="handleModifierChange(key, $event)"
                        class="input input-bordered input-sm w-full"
                      />
                    </td>
                    <td>
                      <button
                        type="button"
                        class="btn btn-sm btn-error"
                        @click="handleRemoveModifier(key)"
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Empty State -->
            <div v-else class="text-center py-4">
              <p class="text-base-content/70 text-sm">No model modifiers configured</p>
              <p class="text-base-content/50 text-xs mt-1">Add modifiers to customize the model appearance</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Attributes Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Attributes</h3>

          <!-- Add New Attribute -->
          <div class="flex gap-2 mb-4">
            <input
              v-model="newAttributeKey"
              type="text"
              placeholder="Attribute key"
              class="input input-bordered flex-1"
            />
            <input
              v-model="newAttributeValue"
              type="text"
              placeholder="Attribute value"
              class="input input-bordered flex-1"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddAttribute"
              :disabled="!newAttributeKey || !newAttributeValue"
            >
              Add Attribute
            </button>
          </div>

          <!-- Existing Attributes -->
          <div v-if="character?.attributes && Object.keys(character.attributes).length > 0" class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Key</th>
                  <th>Value</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(value, key) in character.attributes" :key="key">
                  <td class="font-mono">{{ key }}</td>
                  <td>
                    <input
                      type="text"
                      :value="value"
                      @input="handleAttributeChange(key, $event)"
                      class="input input-bordered input-sm w-full"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-sm btn-error"
                      @click="handleRemoveAttribute(key)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No attributes configured</p>
            <p class="text-base-content/50 text-sm mt-2">Add an attribute by entering a key and value above</p>
          </div>
        </div>
      </div>

      <!-- Skills Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Skills</h3>

          <!-- Add New Skill -->
          <div class="flex gap-2 mb-4">
            <input
              v-model="newSkillName"
              type="text"
              placeholder="Skill name (e.g., mining, combat)"
              class="input input-bordered flex-1"
            />
            <input
              v-model.number="newSkillLevel"
              type="number"
              min="0"
              placeholder="Level"
              class="input input-bordered w-24"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddSkill"
              :disabled="!newSkillName.trim()"
            >
              Add Skill
            </button>
          </div>

          <!-- Existing Skills -->
          <div v-if="character?.skills && Object.keys(character.skills).length > 0" class="space-y-2">
            <div
              v-for="(level, skillName) in character.skills"
              :key="skillName"
              class="flex items-center gap-4 p-3 border border-base-300 rounded-lg"
            >
              <span class="font-medium flex-1">{{ skillName }}</span>
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="btn btn-sm btn-ghost"
                  @click="handleIncrementSkill(skillName, -1)"
                  :disabled="saving"
                >
                  -
                </button>
                <span class="badge badge-lg">Level {{ level }}</span>
                <button
                  type="button"
                  class="btn btn-sm btn-ghost"
                  @click="handleIncrementSkill(skillName, 1)"
                  :disabled="saving"
                >
                  +
                </button>
              </div>
              <button
                type="button"
                class="btn btn-sm btn-error"
                @click="handleSetSkillLevel(skillName, 0)"
                :disabled="saving"
              >
                Remove
              </button>
            </div>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No skills yet</p>
            <p class="text-base-content/50 text-sm mt-2">Add a skill using the form above</p>
          </div>
        </div>
      </div>

      <!-- Movement State Values Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Movement State Values</h3>
          <p class="text-sm text-base-content/70 mb-4">Configure physics and movement properties for each movement state</p>

          <!-- Empty State Warning -->
          <div v-if="!stateValues || Object.keys(stateValues).length === 0" class="alert alert-warning mb-4">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <span>No movement state values configured. These are initialized when a character is created.</span>
          </div>

          <!-- State Editors (Accordion) -->
          <div v-else class="space-y-2">
            <div
              v-for="stateKey in movementStateKeys"
              :key="stateKey"
              class="collapse collapse-arrow border border-base-300 bg-base-200"
            >
              <input type="checkbox" />
              <div class="collapse-title text-lg font-medium capitalize">
                {{ stateKey.replace('_', ' ') }}
              </div>
              <div class="collapse-content">
                <div v-if="stateValues[stateKey]" class="grid grid-cols-1 md:grid-cols-2 gap-4 pt-4">
                  <!-- Movement Speeds -->
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Base Move Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].baseMoveSpeed"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Effective Move Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].effectiveMoveSpeed"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>

                  <!-- Jump Speeds -->
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Base Jump Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].baseJumpSpeed"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Effective Jump Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].effectiveJumpSpeed"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>

                  <!-- Camera & View -->
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Eye Height</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].eyeHeight"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Selection Radius</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].selectionRadius"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>

                  <!-- Turn Speeds -->
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Base Turn Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].baseTurnSpeed"
                      type="number"
                      step="0.001"
                      class="input input-bordered input-sm"
                    />
                  </div>
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Effective Turn Speed</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].effectiveTurnSpeed"
                      type="number"
                      step="0.001"
                      class="input input-bordered input-sm"
                    />
                  </div>

                  <!-- Stealth & Detection -->
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Stealth Range</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].stealthRange"
                      type="number"
                      step="0.1"
                      class="input input-bordered input-sm"
                    />
                  </div>
                  <div class="form-control">
                    <label class="label py-1">
                      <span class="label-text text-xs">Distance Notify Reduction</span>
                    </label>
                    <input
                      v-model.number="stateValues[stateKey].distanceNotifyReduction"
                      type="number"
                      step="0.1"
                      min="0"
                      max="1"
                      class="input input-bordered input-sm"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Backpack Items Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Backpack - Items</h3>

          <!-- Add New Item -->
          <div class="flex gap-2 mb-4">
            <div class="flex gap-1 flex-1">
              <input
                v-model="newItemId"
                type="text"
                placeholder="Item ID (e.g., apple, sword)"
                class="input input-bordered flex-1"
              />
              <button
                type="button"
                class="btn btn-square btn-outline"
                @click="openItemSelectorForNew"
                title="Search items"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
              </button>
            </div>
            <input
              v-model.number="newItemCount"
              type="number"
              min="1"
              placeholder="Count"
              class="input input-bordered w-32"
            />
            <button
              type="button"
              class="btn btn-secondary"
              @click="handleAddItem"
              :disabled="!newItemId || !newItemCount || newItemCount <= 0"
            >
              Add Item
            </button>
          </div>

          <!-- Existing Items -->
          <div v-if="backpackItems && Object.keys(backpackItems).length > 0" class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Item ID</th>
                  <th>Count</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(count, itemId) in backpackItems" :key="itemId">
                  <td class="font-mono">{{ itemId }}</td>
                  <td>
                    <input
                      type="number"
                      :value="count"
                      @input="handleItemCountChange(itemId, $event)"
                      min="0"
                      class="input input-bordered input-sm w-24"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-sm btn-error"
                      @click="handleRemoveItem(itemId)"
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Empty State -->
          <div v-else class="text-center py-8">
            <p class="text-base-content/70">No items in backpack</p>
            <p class="text-base-content/50 text-sm mt-2">Add items using the form above</p>
          </div>
        </div>
      </div>

      <!-- Backpack Wearing Items Card (only for existing characters) -->
      <div v-if="!isNew" class="card bg-base-100 shadow-xl">
        <div class="card-body">
          <h3 class="card-title">Backpack - Wearing Items</h3>
          <p class="text-sm text-base-content/70 mb-4">Configure items worn in specific equipment slots</p>

          <!-- Wearing Slots Table -->
          <div class="overflow-x-auto">
            <table class="table table-zebra">
              <thead>
                <tr>
                  <th>Slot</th>
                  <th>Item ID</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="slot in wearableSlots" :key="slot">
                  <td class="font-medium">{{ formatSlotName(slot) }}</td>
                  <td>
                    <div class="flex gap-1">
                      <input
                        v-model="wearingItems[slot]"
                        type="text"
                        :placeholder="`Item ID for ${formatSlotName(slot).toLowerCase()}`"
                        class="input input-bordered input-sm flex-1"
                      />
                      <button
                        type="button"
                        class="btn btn-sm btn-square btn-outline"
                        @click="openItemSelectorForSlot(slot)"
                        title="Search items"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                        </svg>
                      </button>
                    </div>
                  </td>
                  <td>
                    <button
                      v-if="wearingItems[slot]"
                      type="button"
                      class="btn btn-sm btn-ghost"
                      @click="wearingItems[slot] = ''"
                    >
                      Clear
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
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

  <!-- Entity Model Selector Dialog for Third Person Model -->
  <EntityModelSelectorDialog
    v-if="isModelSelectorOpen && assetWorldId"
    :world-id="assetWorldId"
    :current-model-id="thirdPersonModelId"
    @close="closeModelSelector"
    @select="handleModelSelected"
  />

  <!-- Item Selector Dialog for Backpack Items -->
  <ItemSelectorDialog
    v-if="isItemSelectorOpen && assetWorldId"
    :world-id="assetWorldId"
    :current-item-id="itemSelectorContext === 'new' ? newItemId : (itemSelectorContext ? wearingItems[itemSelectorContext] : '')"
    @close="closeItemSelector"
    @select="handleItemSelected"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRegion } from '@/composables/useRegion';
import { characterService, type RCharacter } from '../services/CharacterService';
import { userService, type RUser } from '../../user/services/UserService';
import EntityModelSelectorDialog from '@components/EntityModelSelectorDialog.vue';
import ItemSelectorDialog from '@components/ItemSelectorDialog.vue';

const props = defineProps<{
  character: RCharacter | 'new';
}>();

const emit = defineEmits<{
  back: [];
  saved: [];
}>();

const { currentRegionId } = useRegion();

// Dynamic worldId for asset picker based on current region
const assetWorldId = computed(() => currentRegionId.value ? `@region:${currentRegionId.value}` : '@shared:n');

const isNew = computed(() => props.character === 'new');

const character = ref<RCharacter | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref<string | null>(null);
const successMessage = ref<string | null>(null);

const users = ref<RUser[]>([]);
const loadingUsers = ref(false);

const formData = ref({
  userId: '',
  name: '',
  display: '',
});

const newSkillName = ref('');
const newSkillLevel = ref(1);
const newAttributeKey = ref('');
const newAttributeValue = ref('');

// Shortcuts JSON editors
const showShortcutsEditor = ref(false);
const showEditorShortcutsEditor = ref(false);
const shortcutsJson = ref('');
const editorShortcutsJson = ref('');
const shortcutsError = ref<string | null>(null);
const editorShortcutsError = ref<string | null>(null);

// Third person model
const thirdPersonModelId = ref('');
const thirdPersonModelModifiers = ref<Record<string, string>>({});
const newModifierKey = ref('');
const newModifierValue = ref('');
const isModelSelectorOpen = ref(false);

// Movement State Values
const movementStateKeys = ['default', 'walk', 'sprint', 'crouch', 'swim', 'climb', 'free_fly', 'fly', 'teleport', 'riding'] as const;
type MovementStateKey = typeof movementStateKeys[number];

interface MovementStateValues {
  baseMoveSpeed: number;
  effectiveMoveSpeed: number;
  baseJumpSpeed: number;
  effectiveJumpSpeed: number;
  eyeHeight: number;
  baseTurnSpeed: number;
  effectiveTurnSpeed: number;
  selectionRadius: number;
  stealthRange: number;
  distanceNotifyReduction: number;
}

const stateValues = ref<Record<MovementStateKey, MovementStateValues>>({} as any);

// Backpack Items
const backpackItems = ref<Record<string, number>>({});
const newItemId = ref('');
const newItemCount = ref(1);

// Backpack Wearing Items
const wearableSlots = ['HEAD', 'BODY', 'LEGS', 'FEET', 'HANDS', 'NECK', 'LEFT_RING', 'RIGHT_RING', 'LEFT_WEAPON_1', 'RIGHT_WEAPON_1', 'LEFT_WEAPON_2', 'RIGHT_WEAPON_2'] as const;
type WearableSlot = typeof wearableSlots[number];
const wearingItems = ref<Record<WearableSlot, string>>({} as any);

const formatSlotName = (slot: string): string => {
  return slot
    .split('_')
    .map(word => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};

// Item Selector Dialog
const isItemSelectorOpen = ref(false);
const itemSelectorContext = ref<'new' | WearableSlot | null>(null);

const loadUsers = async () => {
  loadingUsers.value = true;
  try {
    users.value = await userService.listUsers();
  } catch (e) {
    console.error('[CharacterEditor] Failed to load users:', e);
    error.value = 'Failed to load user list';
  } finally {
    loadingUsers.value = false;
  }
};

const loadCharacter = () => {
  if (isNew.value) {
    character.value = null;
    formData.value = {
      userId: '',
      name: '',
      display: '',
    };
    shortcutsJson.value = '';
    editorShortcutsJson.value = '';
    thirdPersonModelId.value = '';
    thirdPersonModelModifiers.value = {};
    stateValues.value = {} as any;
    backpackItems.value = {};
    wearingItems.value = {} as any;
    return;
  }

  // Load from props
  character.value = props.character as RCharacter;
  formData.value = {
    userId: character.value.userId,
    name: character.value.name,
    display: character.value.publicData?.title || character.value.name,
  };

  // Initialize JSON strings for shortcuts
  shortcutsJson.value = JSON.stringify(character.value.publicData?.shortcuts || {}, null, 2);
  editorShortcutsJson.value = JSON.stringify(character.value.publicData?.editorShortcuts || {}, null, 2);

  // Initialize third person model
  thirdPersonModelId.value = character.value.publicData?.thirdPersonModelId || '';
  thirdPersonModelModifiers.value = character.value.publicData?.thirdPersonModelModifiers || {};

  // Initialize movement state values
  stateValues.value = character.value.publicData?.stateValues || {} as any;

  // Initialize backpack items
  backpackItems.value = character.value.backpack?.itemIds || {};

  // Initialize wearing items
  wearingItems.value = character.value.backpack?.wearingItemIds || {} as any;
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
    if (isNew.value) {
      await characterService.createCharacter(currentRegionId.value, {
        userId: formData.value.userId,
        name: formData.value.name,
        display: formData.value.display,
      });
      successMessage.value = 'Character created successfully';
    } else if (character.value) {
      // Update publicData with current thirdPersonModelId, modifiers, and stateValues
      const updatedPublicData = {
        ...character.value.publicData,
        thirdPersonModelId: thirdPersonModelId.value || undefined,
        thirdPersonModelModifiers: Object.keys(thirdPersonModelModifiers.value).length > 0
          ? thirdPersonModelModifiers.value
          : undefined,
        stateValues: stateValues.value,
      };

      // Update backpack with current items (filter out count=0 and empty strings)
      const updatedBackpack = {
        ...character.value.backpack,
        itemIds: Object.fromEntries(
          Object.entries(backpackItems.value).filter(([_, count]) => count > 0)
        ),
        wearingItemIds: Object.fromEntries(
          Object.entries(wearingItems.value).filter(([_, itemId]) => itemId && itemId.trim() !== '')
        ),
      };

      // Update existing character with full DTO
      const updatedChar = await characterService.updateCharacter(
        currentRegionId.value,
        character.value.id,
        character.value.userId,
        character.value.name,
        {
          userId: character.value.userId,
          name: character.value.name,
          display: formData.value.display,
          publicData: updatedPublicData,
          backpack: updatedBackpack,
          skills: character.value.skills,
          attributes: character.value.attributes,
        }
      );
      successMessage.value = 'Character updated successfully';
      // Update local character with response
      character.value = updatedChar;
    }

    setTimeout(() => {
      emit('saved');
    }, 1000);
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to save character';
    console.error('Failed to save character:', e);
  } finally {
    saving.value = false;
  }
};

const handleAddSkill = async () => {
  if (!newSkillName.value.trim() || !currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.setSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      newSkillName.value.trim(),
      newSkillLevel.value
    );
    successMessage.value = `Skill "${newSkillName.value}" added successfully`;
    newSkillName.value = '';
    newSkillLevel.value = 1;
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to add skill';
    console.error('Failed to add skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleSetSkillLevel = async (skillName: string, level: number) => {
  if (!currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.setSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      skillName,
      level
    );
    if (level === 0) {
      successMessage.value = `Skill "${skillName}" removed successfully`;
    } else {
      successMessage.value = `Skill "${skillName}" updated to level ${level}`;
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to update skill';
    console.error('Failed to update skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleIncrementSkill = async (skillName: string, delta: number) => {
  if (!currentRegionId.value || !character.value) {
    return;
  }

  saving.value = true;
  error.value = null;
  successMessage.value = null;

  try {
    character.value = await characterService.incrementSkill(
      currentRegionId.value,
      character.value.id,
      character.value.userId,
      character.value.name,
      skillName,
      delta
    );
    successMessage.value = `Skill "${skillName}" ${delta > 0 ? 'increased' : 'decreased'}`;
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to increment skill';
    console.error('Failed to increment skill:', e);
  } finally {
    saving.value = false;
  }
};

const handleAddAttribute = () => {
  if (!newAttributeKey.value.trim() || !newAttributeValue.value.trim() || !character.value) {
    return;
  }

  // Initialize attributes if not present
  if (!character.value.attributes) {
    character.value = {
      ...character.value,
      attributes: {},
    };
  }

  // Update local character object
  character.value = {
    ...character.value,
    attributes: {
      ...character.value.attributes,
      [newAttributeKey.value]: newAttributeValue.value,
    },
  };

  newAttributeKey.value = '';
  newAttributeValue.value = '';
};

const handleAttributeChange = (key: string, event: Event) => {
  if (!character.value) return;

  const target = event.target as HTMLInputElement;

  // Update local character object
  character.value = {
    ...character.value,
    attributes: {
      ...character.value.attributes,
      [key]: target.value,
    },
  };
};

const handleRemoveAttribute = (key: string) => {
  if (!character.value) return;

  // Update local character object
  const { [key]: removed, ...remainingAttributes } = character.value.attributes || {};
  character.value = {
    ...character.value,
    attributes: remainingAttributes,
  };
};

const handleSaveShortcuts = () => {
  if (!character.value) return;

  shortcutsError.value = null;

  try {
    // Parse JSON
    const parsed = JSON.parse(shortcutsJson.value);

    // Update character
    if (!character.value.publicData) {
      character.value = {
        ...character.value,
        publicData: {
          playerId: character.value.userId,
          title: character.value.publicData?.title || character.value.name,
          shortcuts: parsed,
          stateValues: character.value.publicData?.stateValues || {},
        },
      };
    } else {
      character.value = {
        ...character.value,
        publicData: {
          ...character.value.publicData,
          shortcuts: parsed,
        },
      };
    }

    successMessage.value = 'Shortcuts updated (remember to save the character)';
  } catch (e) {
    shortcutsError.value = e instanceof Error ? e.message : 'Invalid JSON';
  }
};

const handleResetShortcuts = () => {
  if (!character.value) return;
  shortcutsJson.value = JSON.stringify(character.value.publicData?.shortcuts || {}, null, 2);
  shortcutsError.value = null;
};

const handleSaveEditorShortcuts = () => {
  if (!character.value) return;

  editorShortcutsError.value = null;

  try {
    // Parse JSON
    const parsed = JSON.parse(editorShortcutsJson.value);

    // Update character
    if (!character.value.publicData) {
      character.value = {
        ...character.value,
        publicData: {
          playerId: character.value.userId,
          title: character.value.publicData?.title || character.value.name,
          editorShortcuts: parsed,
          stateValues: character.value.publicData?.stateValues || {},
        },
      };
    } else {
      character.value = {
        ...character.value,
        publicData: {
          ...character.value.publicData,
          editorShortcuts: parsed,
        },
      };
    }

    successMessage.value = 'Editor shortcuts updated (remember to save the character)';
  } catch (e) {
    editorShortcutsError.value = e instanceof Error ? e.message : 'Invalid JSON';
  }
};

const handleResetEditorShortcuts = () => {
  if (!character.value) return;
  editorShortcutsJson.value = JSON.stringify(character.value.publicData?.editorShortcuts || {}, null, 2);
  editorShortcutsError.value = null;
};

const handleModelIdChange = () => {
  // Update character immediately for local state
  if (character.value && character.value.publicData) {
    character.value = {
      ...character.value,
      publicData: {
        ...character.value.publicData,
        thirdPersonModelId: thirdPersonModelId.value || undefined,
      },
    };
  }
};

const openModelSelector = () => {
  isModelSelectorOpen.value = true;
};

const closeModelSelector = () => {
  isModelSelectorOpen.value = false;
};

const handleModelSelected = (modelId: string) => {
  thirdPersonModelId.value = modelId;
  handleModelIdChange();
  closeModelSelector();
};

const handleAddModifier = () => {
  if (!newModifierKey.value.trim() || !newModifierValue.value.trim()) {
    return;
  }

  // Update local modifiers object
  thirdPersonModelModifiers.value = {
    ...thirdPersonModelModifiers.value,
    [newModifierKey.value]: newModifierValue.value,
  };

  newModifierKey.value = '';
  newModifierValue.value = '';
};

const handleModifierChange = (key: string, event: Event) => {
  const target = event.target as HTMLInputElement;

  // Update local modifiers object
  thirdPersonModelModifiers.value = {
    ...thirdPersonModelModifiers.value,
    [key]: target.value,
  };
};

const handleRemoveModifier = (key: string) => {
  // Update local modifiers object
  const { [key]: removed, ...remainingModifiers } = thirdPersonModelModifiers.value;
  thirdPersonModelModifiers.value = remainingModifiers;
};

const handleAddItem = () => {
  if (!newItemId.value.trim() || newItemCount.value <= 0) {
    return;
  }

  // Check for duplicate itemId
  if (backpackItems.value[newItemId.value]) {
    error.value = `Item "${newItemId.value}" already exists in backpack`;
    setTimeout(() => {
      error.value = null;
    }, 3000);
    return;
  }

  // Add item to backpack
  backpackItems.value = {
    ...backpackItems.value,
    [newItemId.value]: newItemCount.value,
  };

  newItemId.value = '';
  newItemCount.value = 1;
};

const handleItemCountChange = (itemId: string, event: Event) => {
  const target = event.target as HTMLInputElement;
  const newCount = parseInt(target.value, 10);

  if (isNaN(newCount)) {
    return;
  }

  if (newCount === 0) {
    // Remove item if count is 0
    const { [itemId]: removed, ...remainingItems } = backpackItems.value;
    backpackItems.value = remainingItems;
  } else {
    // Update count
    backpackItems.value = {
      ...backpackItems.value,
      [itemId]: newCount,
    };
  }
};

const handleRemoveItem = (itemId: string) => {
  // Remove item from backpack
  const { [itemId]: removed, ...remainingItems } = backpackItems.value;
  backpackItems.value = remainingItems;
};

const openItemSelectorForNew = () => {
  itemSelectorContext.value = 'new';
  isItemSelectorOpen.value = true;
};

const openItemSelectorForSlot = (slot: WearableSlot) => {
  itemSelectorContext.value = slot;
  isItemSelectorOpen.value = true;
};

const closeItemSelector = () => {
  isItemSelectorOpen.value = false;
  itemSelectorContext.value = null;
};

const handleItemSelected = (itemId: string) => {
  if (itemSelectorContext.value === 'new') {
    // Set itemId for new item
    newItemId.value = itemId;
  } else if (itemSelectorContext.value) {
    // Set itemId for wearing slot
    wearingItems.value[itemSelectorContext.value] = itemId;
  }
  closeItemSelector();
};

const handleBack = () => {
  emit('back');
};

onMounted(() => {
  loadCharacter();
  if (isNew.value) {
    loadUsers();
  }
});
</script>
