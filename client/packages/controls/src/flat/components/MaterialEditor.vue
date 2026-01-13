<template>
  <div class="space-y-4">
    <!-- Toolbar -->
    <div class="flex justify-between items-center">
      <h3 class="text-lg font-semibold">Material Definitions</h3>
      <div class="flex gap-2">
        <!-- Apply Palette Dropdown -->
        <div class="dropdown dropdown-end">
          <label tabindex="0" class="btn btn-sm btn-outline">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01" />
            </svg>
            Apply Palette
          </label>
          <ul tabindex="0" class="dropdown-content z-[1] menu p-2 shadow bg-base-100 rounded-box w-52">
            <li><a @click="applyPalette('nimbus')">Nimbus (Modern)</a></li>
            <li><a @click="applyPalette('legacy')">Legacy (Classic)</a></li>
          </ul>
        </div>

        <!-- Add Material Button -->
        <button
          class="btn btn-sm btn-primary"
          @click="startAddMaterial"
          :disabled="loading"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 mr-1" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
          </svg>
          Add Material
        </button>
      </div>
    </div>

    <!-- Error Alert -->
    <div v-if="error" class="alert alert-error">
      <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-8">
      <span class="loading loading-spinner loading-lg"></span>
    </div>

    <!-- Materials Table -->
    <div v-else-if="materials.length > 0" class="overflow-x-auto">
      <table class="table table-sm w-full">
        <thead>
          <tr>
            <th class="w-32">ID</th>
            <th>Block Definition</th>
            <th>Next Block</th>
            <th class="w-24">Ocean</th>
            <th class="w-24">Delta</th>
            <th class="w-24">Levels</th>
            <th class="w-24"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="material in materials" :key="material.materialId"
              :class="{ 'bg-base-200': editingMaterialId === material.materialId }">
            <td>
              <span class="font-mono">{{ material.materialId }}</span>
              <span v-if="getMaterialLabel(material.materialId)" class="ml-2 text-xs text-gray-500">
                {{ getMaterialLabel(material.materialId) }}
              </span>
            </td>
            <td class="font-mono text-xs">{{ material.blockDef }}</td>
            <td class="font-mono text-xs">{{ material.nextBlockDef || '-' }}</td>
            <td>
              <span v-if="material.hasOcean" class="badge badge-sm badge-info">Yes</span>
              <span v-else class="text-gray-400">No</span>
            </td>
            <td>
              <span v-if="material.isBlockMapDelta" class="badge badge-sm badge-success">Yes</span>
              <span v-else class="text-gray-400">No</span>
            </td>
            <td>
              <span v-if="Object.keys(material.blockAtLevels).length > 0" class="badge badge-sm">
                {{ Object.keys(material.blockAtLevels).length }}
              </span>
              <span v-else class="text-gray-400">0</span>
            </td>
            <td>
              <div class="flex gap-1">
                <button
                  class="btn btn-ghost btn-xs"
                  @click="startEditMaterial(material)"
                  :disabled="material.materialId === 0"
                  title="Edit material"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                  </svg>
                </button>
                <button
                  class="btn btn-ghost btn-xs text-error"
                  @click="confirmDeleteMaterial(material)"
                  :disabled="material.materialId === 0"
                  title="Delete material"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="text-xs text-gray-500 mt-2">
        Material ID 0 (UNKNOWN_PROTECTED) cannot be edited or deleted
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="text-center py-8 text-gray-500">
      <p>No materials defined</p>
      <p class="text-sm">Click "Add Material" to create one</p>
    </div>

    <!-- Material Form (shown when editing/adding) -->
    <div v-if="editingMaterialId !== null" class="border-t pt-4 mt-4">
      <MaterialForm
        :material-id="editingMaterialId"
        :material="editingMaterial"
        @save="handleSaveMaterial"
        @cancel="cancelEdit"
      />
    </div>

    <!-- Delete Confirmation Modal -->
    <dialog ref="deleteModal" class="modal">
      <div class="modal-box">
        <h3 class="font-bold text-lg">Delete Material?</h3>
        <p class="py-4">
          Are you sure you want to delete material <strong>{{ materialToDelete?.materialId }}</strong>?
          <br>
          <span class="text-sm text-gray-600">Block: {{ materialToDelete?.blockDef }}</span>
        </p>
        <div class="modal-action">
          <button class="btn" @click="closeDeleteModal">Cancel</button>
          <button class="btn btn-error" @click="deleteMaterial">Delete</button>
        </div>
      </div>
      <form method="dialog" class="modal-backdrop">
        <button>close</button>
      </form>
    </dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { flatService } from '../../services/FlatService';
import type { MaterialDefinition, UpdateMaterialRequest } from '../../services/FlatService';
import MaterialForm from './MaterialForm.vue';

interface Props {
  flatId: string;
}

const props = defineProps<Props>();

// Material ID labels (from FlatMaterialService.java)
const MATERIAL_LABELS: Record<number, string> = {
  1: 'GRASS',
  2: 'DIRT',
  3: 'STONE',
  4: 'SAND',
  5: 'WATER',
  6: 'BEDROCK',
  7: 'SNOW',
  8: 'INVISIBLE',
  9: 'INVISIBLE_SOLID',
};

const getMaterialLabel = (id: number): string => {
  return MATERIAL_LABELS[id] || '';
};

// State
const materials = ref<MaterialDefinition[]>([]);
const loading = ref(false);
const error = ref('');
const editingMaterialId = ref<number | null>(null);
const editingMaterial = ref<MaterialDefinition | null>(null);
const materialToDelete = ref<MaterialDefinition | null>(null);
const deleteModal = ref<HTMLDialogElement | null>(null);

// Load materials
const loadMaterials = async () => {
  loading.value = true;
  error.value = '';
  try {
    materials.value = await flatService.listMaterials(props.flatId);
  } catch (err) {
    error.value = `Failed to load materials: ${err instanceof Error ? err.message : 'Unknown error'}`;
  } finally {
    loading.value = false;
  }
};

// Apply palette
const applyPalette = async (paletteName: string) => {
  loading.value = true;
  error.value = '';
  try {
    materials.value = await flatService.applyPalette(props.flatId, paletteName);
    editingMaterialId.value = null;
    editingMaterial.value = null;
  } catch (err) {
    error.value = `Failed to apply palette: ${err instanceof Error ? err.message : 'Unknown error'}`;
  } finally {
    loading.value = false;
  }
};

// Start adding new material
const startAddMaterial = () => {
  // Find next available material ID (1-254, skip 0 and existing)
  const usedIds = new Set(materials.value.map(m => m.materialId));
  let nextId = 1;
  while (usedIds.has(nextId) && nextId < 255) {
    nextId++;
  }

  if (nextId >= 255) {
    error.value = 'All material IDs (1-254) are in use';
    return;
  }

  editingMaterialId.value = nextId;
  editingMaterial.value = null;
};

// Start editing material
const startEditMaterial = (material: MaterialDefinition) => {
  if (material.materialId === 0) {
    error.value = 'Material 0 (UNKNOWN_PROTECTED) cannot be edited';
    return;
  }
  editingMaterialId.value = material.materialId;
  editingMaterial.value = material;
};

// Save material (create or update)
const handleSaveMaterial = async (data: UpdateMaterialRequest) => {
  if (editingMaterialId.value === null) return;

  loading.value = true;
  error.value = '';
  try {
    await flatService.updateMaterial(props.flatId, editingMaterialId.value, data);
    await loadMaterials();
    editingMaterialId.value = null;
    editingMaterial.value = null;
  } catch (err) {
    error.value = `Failed to save material: ${err instanceof Error ? err.message : 'Unknown error'}`;
  } finally {
    loading.value = false;
  }
};

// Cancel editing
const cancelEdit = () => {
  editingMaterialId.value = null;
  editingMaterial.value = null;
};

// Confirm delete material
const confirmDeleteMaterial = (material: MaterialDefinition) => {
  if (material.materialId === 0) {
    error.value = 'Material 0 (UNKNOWN_PROTECTED) cannot be deleted';
    return;
  }
  materialToDelete.value = material;
  deleteModal.value?.showModal();
};

// Delete material
const deleteMaterial = async () => {
  if (!materialToDelete.value) return;

  loading.value = true;
  error.value = '';
  try {
    await flatService.deleteMaterial(props.flatId, materialToDelete.value.materialId);
    await loadMaterials();
    closeDeleteModal();
  } catch (err) {
    error.value = `Failed to delete material: ${err instanceof Error ? err.message : 'Unknown error'}`;
  } finally {
    loading.value = false;
  }
};

// Close delete modal
const closeDeleteModal = () => {
  deleteModal.value?.close();
  materialToDelete.value = null;
};

// Initialize
onMounted(() => {
  loadMaterials();
});
</script>
