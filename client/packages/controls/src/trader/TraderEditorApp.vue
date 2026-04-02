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
        <h1 class="text-xl font-bold px-4">Nimbus Trader Editor</h1>
      </div>
      <div class="flex-none">
        <WorldSelector />
      </div>
    </header>

    <main class="flex-1 container mx-auto px-4 py-6">
      <div v-if="!currentWorldId" class="alert alert-info">
        <span>Please select a world from the dropdown above</span>
      </div>

      <div v-else-if="loading" class="flex justify-center py-8">
        <span class="loading loading-spinner loading-lg"></span>
      </div>

      <div v-else-if="error" class="alert alert-error">
        <span>{{ error }}</span>
      </div>

      <!-- Trader List / Editor -->
      <div v-else>
        <!-- Edit Mode -->
        <div v-if="editingTrader" class="space-y-4">
          <div class="flex items-center gap-2">
            <button class="btn btn-ghost gap-2" @click="cancelEdit">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
              Back to List
            </button>
            <h2 class="text-2xl font-bold">{{ isNew ? 'Create Trader' : 'Edit Trader' }}</h2>
          </div>

          <div class="card bg-base-100 shadow-xl">
            <div class="card-body space-y-4">
              <!-- Entity ID -->
              <div class="form-control">
                <label class="label"><span class="label-text font-semibold">Entity ID</span></label>
                <input v-model="editingTrader.entityId" type="text" class="input input-bordered" :disabled="!isNew" placeholder="npc_entity_id" />
              </div>

              <!-- Trader Type -->
              <div class="form-control">
                <label class="label"><span class="label-text font-semibold">Trader Type</span></label>
                <select v-model="editingTrader.traderType" class="select select-bordered">
                  <option value="MERCHANT">MERCHANT</option>
                  <option value="TRAINER">TRAINER</option>
                  <option value="SERVICE">SERVICE</option>
                </select>
              </div>

              <div class="divider">Commerce</div>

              <div class="grid grid-cols-2 gap-4">
                <div class="form-control">
                  <label class="label"><span class="label-text">Shop Chest ID</span></label>
                  <input v-model="editingTrader.chestId" type="text" class="input input-bordered" placeholder="npc_shop" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Pool Chest ID</span></label>
                  <input v-model="editingTrader.poolChestId" type="text" class="input input-bordered" placeholder="npc_pool" />
                </div>
              </div>

              <div class="grid grid-cols-3 gap-4">
                <div class="form-control">
                  <label class="label"><span class="label-text">Silver Amount</span></label>
                  <input v-model.number="editingTrader.silverAmount" type="number" class="input input-bordered" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Personality Modifier</span></label>
                  <input v-model.number="editingTrader.personalityModifier" type="number" step="0.01" class="input input-bordered" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Gold Exchange Rate</span></label>
                  <input v-model.number="editingTrader.goldExchangeRate" type="number" step="0.5" class="input input-bordered" />
                </div>
              </div>

              <div class="grid grid-cols-2 gap-4">
                <div class="form-control">
                  <label class="label"><span class="label-text">Max Display Items</span></label>
                  <input v-model.number="editingTrader.maxDisplayItems" type="number" class="input input-bordered" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Pool Sync Interval (sec)</span></label>
                  <input v-model.number="editingTrader.poolSyncIntervalSeconds" type="number" class="input input-bordered" />
                </div>
              </div>

              <!-- Categories -->
              <div class="form-control">
                <label class="label"><span class="label-text font-semibold">Categories (comma-separated)</span></label>
                <input v-model="categoriesStr" type="text" class="input input-bordered" placeholder="food, material, weapon" />
              </div>

              <!-- Trainer -->
              <template v-if="editingTrader.traderType === 'TRAINER'">
                <div class="divider">Trainer</div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Trainable Skills (comma-separated)</span></label>
                  <input v-model="trainableSkillsStr" type="text" class="input input-bordered" placeholder="smithing.iron, sword.steel" />
                </div>
                <div class="grid grid-cols-2 gap-4">
                  <div class="form-control">
                    <label class="label"><span class="label-text">Max Skill Points</span></label>
                    <input v-model.number="editingTrader.maxSkillPoints" type="number" class="input input-bordered" />
                  </div>
                  <div class="form-control">
                    <label class="label"><span class="label-text">Cost Per Skill Point</span></label>
                    <input v-model.number="editingTrader.costPerSkillPoint" type="number" step="0.5" class="input input-bordered" />
                  </div>
                </div>
              </template>

              <!-- Service -->
              <template v-if="editingTrader.traderType === 'SERVICE'">
                <div class="divider">Service</div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Repair Types (comma-separated)</span></label>
                  <input v-model="repairTypesStr" type="text" class="input input-bordered" placeholder="weapon, armor, magic" />
                </div>
                <div class="form-control">
                  <label class="label"><span class="label-text">Repair Cost Per Point</span></label>
                  <input v-model.number="editingTrader.repairCostPerPoint" type="number" step="0.5" class="input input-bordered" />
                </div>
              </template>

              <!-- Actions -->
              <div class="flex gap-2 pt-4">
                <button class="btn btn-primary" @click="saveTrader" :disabled="saving">
                  {{ saving ? 'Saving...' : 'Save' }}
                </button>
                <button class="btn btn-ghost" @click="cancelEdit">Cancel</button>
                <div class="flex-1"></div>
                <button v-if="!isNew" class="btn btn-error" @click="deleteTrader">Delete</button>
              </div>
            </div>
          </div>
        </div>

        <!-- List Mode -->
        <div v-else class="space-y-4">
          <div class="flex justify-between items-center">
            <h2 class="text-2xl font-bold">Traders</h2>
            <button class="btn btn-primary btn-sm" @click="startCreate">
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
              </svg>
              Create
            </button>
          </div>

          <div v-if="traders.length === 0" class="text-center py-8 text-base-content/50">
            No traders found. Create one!
          </div>

          <div v-else class="space-y-2">
            <div
              v-for="trader in traders"
              :key="trader.entityId"
              class="card bg-base-100 shadow cursor-pointer hover:shadow-md transition-shadow"
              @click="startEdit(trader)"
            >
              <div class="card-body p-4 flex-row items-center gap-4">
                <div class="flex-1">
                  <div class="font-semibold">{{ trader.entityId }}</div>
                  <div class="flex gap-2 mt-1">
                    <span class="badge badge-sm badge-primary">{{ trader.traderType }}</span>
                    <span class="badge badge-sm badge-ghost">{{ trader.silverAmount }} Silver</span>
                    <span class="badge badge-sm badge-ghost">{{ (trader.categories || []).join(', ') || 'no categories' }}</span>
                  </div>
                  <div class="text-xs text-base-content/50 mt-1">
                    Shop: {{ trader.chestId || '-' }} | Pool: {{ trader.poolChestId || '-' }}
                  </div>
                </div>
                <svg class="w-5 h-5 text-base-content/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useWorld } from '@/composables/useWorld';
import WorldSelector from '@/material/components/WorldSelector.vue';
import { apiService } from '@/services/ApiService';

const { currentWorldId, loadWorlds } = useWorld();

interface Trader {
  entityId: string;
  traderType: string;
  categories: string[];
  personalityModifier: number;
  silverAmount: number;
  chestId: string;
  poolChestId: string;
  questItems: string[];
  maxDisplayItems: number;
  goldExchangeRate: number;
  trainableSkills: string[];
  maxSkillPoints: number;
  costPerSkillPoint: number;
  repairTypes: string[];
  repairCostPerPoint: number;
  poolSyncIntervalSeconds: number;
}

const traders = ref<Trader[]>([]);
const loading = ref(false);
const error = ref<string | null>(null);
const saving = ref(false);

const editingTrader = ref<Trader | null>(null);
const isNew = ref(false);

// Comma-separated helpers
const categoriesStr = computed({
  get: () => editingTrader.value?.categories?.join(', ') || '',
  set: (v: string) => { if (editingTrader.value) editingTrader.value.categories = v.split(',').map(s => s.trim()).filter(Boolean); }
});
const trainableSkillsStr = computed({
  get: () => editingTrader.value?.trainableSkills?.join(', ') || '',
  set: (v: string) => { if (editingTrader.value) editingTrader.value.trainableSkills = v.split(',').map(s => s.trim()).filter(Boolean); }
});
const repairTypesStr = computed({
  get: () => editingTrader.value?.repairTypes?.join(', ') || '',
  set: (v: string) => { if (editingTrader.value) editingTrader.value.repairTypes = v.split(',').map(s => s.trim()).filter(Boolean); }
});

async function loadTraders() {
  if (!currentWorldId.value) return;
  loading.value = true;
  error.value = null;
  try {
    traders.value = await apiService.get<Trader[]>(`/control/world/${currentWorldId.value}/traders`);
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Failed to load traders';
  } finally {
    loading.value = false;
  }
}

function startCreate() {
  isNew.value = true;
  editingTrader.value = {
    entityId: '',
    traderType: 'MERCHANT',
    categories: [],
    personalityModifier: 0,
    silverAmount: 500,
    chestId: '',
    poolChestId: '',
    questItems: [],
    maxDisplayItems: 12,
    goldExchangeRate: 10,
    trainableSkills: [],
    maxSkillPoints: 0,
    costPerSkillPoint: 0,
    repairTypes: [],
    repairCostPerPoint: 0,
    poolSyncIntervalSeconds: 3600,
  };
}

function startEdit(trader: Trader) {
  isNew.value = false;
  editingTrader.value = { ...trader };
}

function cancelEdit() {
  editingTrader.value = null;
}

async function saveTrader() {
  if (!editingTrader.value || !currentWorldId.value) return;
  saving.value = true;
  error.value = null;
  try {
    if (isNew.value) {
      await apiService.post(`/control/world/${currentWorldId.value}/traders`, editingTrader.value);
    } else {
      await apiService.put(`/control/world/${currentWorldId.value}/traders/${editingTrader.value.entityId}`, editingTrader.value);
    }
    editingTrader.value = null;
    await loadTraders();
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Failed to save trader';
  } finally {
    saving.value = false;
  }
}

async function deleteTrader() {
  if (!editingTrader.value || !currentWorldId.value) return;
  if (!confirm(`Delete trader "${editingTrader.value.entityId}"?`)) return;
  try {
    await apiService.delete(`/control/world/${currentWorldId.value}/traders/${editingTrader.value.entityId}`);
    editingTrader.value = null;
    await loadTraders();
  } catch (e: any) {
    error.value = e.response?.data?.error || e.message || 'Failed to delete trader';
  }
}

watch(currentWorldId, () => loadTraders());

onMounted(async () => {
  await loadWorlds('withCollections');
  if (currentWorldId.value) await loadTraders();
});
</script>
