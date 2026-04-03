<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Loading -->
    <main v-if="state === 'LOADING'" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading exchange...</p>
      </div>
    </main>

    <!-- Error -->
    <main v-else-if="state === 'ERROR'" class="flex-1 flex items-center justify-center p-4">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center max-w-md">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
        <button @click="closeWidget" class="mt-4 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">Close</button>
      </div>
    </main>

    <!-- Completed -->
    <main v-else-if="state === 'COMPLETED'" class="flex-1 flex items-center justify-center p-4">
      <div class="bg-green-900/30 border border-green-700 rounded-lg p-6 text-center max-w-md">
        <h2 class="text-xl font-bold text-green-400 mb-2">Exchange Complete!</h2>
        <p class="text-green-300">Items and currency have been transferred.</p>
        <button @click="closeWidget" class="mt-4 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">Close</button>
      </div>
    </main>

    <!-- Cancelled -->
    <main v-else-if="state === 'CANCELLED'" class="flex-1 flex items-center justify-center p-4">
      <div class="bg-yellow-900/30 border border-yellow-700 rounded-lg p-6 text-center max-w-md">
        <h2 class="text-xl font-bold text-yellow-400 mb-2">Exchange Cancelled</h2>
        <p class="text-yellow-300">The exchange was cancelled.</p>
        <button @click="closeWidget" class="mt-4 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">Close</button>
      </div>
    </main>

    <!-- Active -->
    <main v-else-if="state === 'ACTIVE'" class="flex-1 flex flex-col p-3 gap-3 overflow-auto">

      <!-- Header with status -->
      <div class="flex items-center justify-between bg-gray-800 rounded-lg p-2 px-3">
        <h1 class="text-base font-bold text-amber-400">Exchange with {{ data.partnerName }}</h1>
        <div class="flex gap-2 text-xs">
          <span :class="data.myAccepted ? 'text-green-400' : 'text-gray-500'">
            You: {{ data.myAccepted ? 'accepted' : 'pending' }}
          </span>
          <span class="text-gray-600">|</span>
          <span :class="data.partnerAccepted ? 'text-green-400' : 'text-gray-500'">
            {{ data.partnerName }}: {{ data.partnerAccepted ? 'accepted' : 'pending' }}
          </span>
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3 flex-1 min-h-0">

        <!-- LEFT: My Transfer Chest (partner selects from here) -->
        <div class="bg-gray-800 rounded-lg p-3 flex flex-col gap-2 overflow-auto">
          <h2 class="text-sm font-bold text-amber-300">
            My Transfer Chest
            <span class="text-xs font-normal text-gray-500 ml-1">
              ({{ data.partnerSelectedItems.length }} items selected by {{ data.partnerName }})
            </span>
          </h2>

          <div v-if="data.myTransferItems.length === 0" class="text-gray-500 text-xs text-center py-4">
            No items in your transfer chest
          </div>
          <div v-else class="grid grid-cols-5 gap-2">
            <div
              v-for="item in data.myTransferItems"
              :key="'my-' + item.itemId"
              class="relative w-14 h-14 rounded border-2 flex items-center justify-center bg-gray-700"
              :class="getPartnerSelectedAmount(item.itemId) > 0
                ? 'border-blue-400 shadow-lg shadow-blue-400/20'
                : 'border-gray-600'"
              :title="item.name + (item.description ? ' — ' + item.description : '')"
            >
              <img v-if="item.texture" :src="getAssetUrl(item.texture)" :alt="item.name"
                   class="w-10 h-10 object-contain" style="image-rendering: pixelated;"
                   @error="onImageError($event)" />
              <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
              <span v-if="item.amount > 1"
                    class="absolute -bottom-1 -right-1 bg-amber-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
                {{ item.amount > 99 ? '99+' : item.amount }}
              </span>
              <!-- Partner wants this many -->
              <div v-if="getPartnerSelectedAmount(item.itemId) > 0"
                   class="absolute -top-1 -left-1 bg-blue-500 text-white rounded-full w-4 h-4 flex items-center justify-center text-xs">
                {{ getPartnerSelectedAmount(item.itemId) }}
              </div>
            </div>
          </div>

          <!-- My currency offer -->
          <div class="border-t border-gray-700 pt-2 flex flex-col gap-1">
            <div class="flex items-center gap-2">
              <label class="text-xs text-gray-400 w-12">Silver:</label>
              <input v-model.number="silverOffer" type="number" min="0" :max="data.mySilver"
                     :disabled="data.myAccepted"
                     class="bg-gray-700 rounded px-2 py-1 text-xs w-20 text-right disabled:opacity-50" />
              <span class="text-xs text-gray-500">/ {{ data.mySilver }}</span>
            </div>
            <div class="flex items-center gap-2">
              <label class="text-xs text-gray-400 w-12">Gold:</label>
              <input v-model.number="goldOffer" type="number" min="0" :max="data.myGold"
                     :disabled="data.myAccepted"
                     class="bg-gray-700 rounded px-2 py-1 text-xs w-20 text-right disabled:opacity-50" />
              <span class="text-xs text-gray-500">/ {{ data.myGold }}</span>
            </div>
          </div>

          <!-- Message -->
          <div class="border-t border-gray-700 pt-2">
            <input v-model="message" type="text" maxlength="140" placeholder="Short message..."
                   :disabled="data.myAccepted"
                   class="bg-gray-700 rounded px-2 py-1 text-xs w-full disabled:opacity-50" />
          </div>
        </div>

        <!-- RIGHT: Partner's Transfer Chest (I select from here) -->
        <div class="bg-gray-800 rounded-lg p-3 flex flex-col gap-2 overflow-auto">
          <h2 class="text-sm font-bold text-blue-300">
            {{ data.partnerName }}'s Transfer Chest
            <span class="text-xs font-normal text-gray-500 ml-1">
              ({{ selectedItems.length }} items selected)
            </span>
          </h2>

          <div v-if="data.partnerTransferItems.length === 0" class="text-gray-500 text-xs text-center py-4">
            No items in partner's transfer chest
          </div>
          <div v-else class="grid grid-cols-5 gap-2">
            <div
              v-for="item in data.partnerTransferItems"
              :key="'partner-' + item.itemId"
              class="relative w-14 h-14 rounded border-2 transition-all flex items-center justify-center bg-gray-700"
              :class="[
                isSelected(item.itemId)
                  ? 'border-green-400 shadow-lg shadow-green-400/20'
                  : 'border-gray-600',
                data.myAccepted ? 'opacity-50 cursor-default' : 'cursor-pointer hover:border-gray-400'
              ]"
              :title="item.name + (item.description ? ' — ' + item.description : '')"
              @click="!data.myAccepted && toggleItemSelection(item.itemId)"
            >
              <img v-if="item.texture" :src="getAssetUrl(item.texture)" :alt="item.name"
                   class="w-10 h-10 object-contain" style="image-rendering: pixelated;"
                   @error="onImageError($event)" />
              <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
              <span v-if="item.amount > 1"
                    class="absolute -bottom-1 -right-1 bg-amber-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">
                {{ item.amount > 99 ? '99+' : item.amount }}
              </span>
              <!-- Selection checkmark with amount -->
              <div v-if="isSelected(item.itemId)"
                   class="absolute -top-1 -left-1 bg-green-500 text-white rounded-full w-4 h-4 flex items-center justify-center text-xs">
                {{ getSelectedAmount(item.itemId) }}
              </div>
            </div>
          </div>

          <!-- Partner currency offer (readonly) -->
          <div class="border-t border-gray-700 pt-2 flex flex-col gap-1">
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-400 w-12">Silver:</span>
              <span class="text-xs font-bold" :class="data.partnerSilverOffer > 0 ? 'text-green-400' : 'text-gray-500'">
                {{ data.partnerSilverOffer }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-400 w-12">Gold:</span>
              <span class="text-xs font-bold" :class="data.partnerGoldOffer > 0 ? 'text-amber-400' : 'text-gray-500'">
                {{ data.partnerGoldOffer }}
              </span>
            </div>
          </div>

          <!-- Partner message -->
          <div v-if="data.partnerMessage" class="border-t border-gray-700 pt-2">
            <p class="text-xs text-gray-300 italic">"{{ data.partnerMessage }}"</p>
          </div>
        </div>
      </div>

      <!-- Selected item detail (when clicking partner item) -->
      <div v-if="selectedItemDetail" class="bg-gray-800 rounded-lg p-3">
        <div class="flex items-start gap-3">
          <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
            <img v-if="selectedItemDetail.texture" :src="getAssetUrl(selectedItemDetail.texture)"
                 :alt="selectedItemDetail.name" class="w-10 h-10 object-contain" style="image-rendering: pixelated;"
                 @error="onImageError($event)" />
            <span v-else class="text-gray-500 text-xs">No icon</span>
          </div>
          <div class="min-w-0 flex-1">
            <h3 class="font-semibold text-amber-300 truncate text-sm">{{ selectedItemDetail.name }}</h3>
            <p class="text-xs text-gray-400">{{ selectedItemDetail.itemType || 'Unknown type' }}</p>
            <p v-if="selectedItemDetail.description" class="text-xs text-gray-500 mt-0.5">{{ selectedItemDetail.description }}</p>
            <p class="text-xs text-gray-500 mt-0.5">Available: {{ selectedItemDetail.amount }}</p>
          </div>
          <!-- Amount selector (only when item is selected) -->
          <div v-if="isSelected(selectedItemDetail.itemId)" class="flex items-center gap-1 flex-shrink-0">
            <label class="text-xs text-gray-400">Want:</label>
            <input
              :value="getSelectedAmount(selectedItemDetail.itemId)"
              @input="setSelectedAmount(selectedItemDetail.itemId, Number(($event.target as HTMLInputElement).value))"
              type="number" min="1" :max="selectedItemDetail.amount"
              :disabled="data.myAccepted"
              class="bg-gray-700 rounded px-2 py-1 text-xs w-14 text-right disabled:opacity-50"
            />
            <span class="text-xs text-gray-500">/ {{ selectedItemDetail.amount }}</span>
          </div>
        </div>
      </div>

      <!-- Actions -->
      <div class="flex gap-2">
        <button @click="updateOffer"
                :disabled="data.myAccepted"
                class="flex-1 px-3 py-2 bg-blue-700 hover:bg-blue-600 rounded text-sm font-medium disabled:bg-gray-700 disabled:text-gray-500 disabled:cursor-default">
          Update Offer
        </button>
        <button v-if="!data.myAccepted" @click="acceptExchange"
                class="flex-1 px-3 py-2 bg-green-700 hover:bg-green-600 rounded text-sm font-medium">
          Accept
        </button>
        <button v-else @click="revokeAccept"
                class="flex-1 px-3 py-2 bg-yellow-700 hover:bg-yellow-600 rounded text-sm font-medium">
          Revoke Accept
        </button>
        <button @click="cancelExchange" class="px-3 py-2 bg-red-800 hover:bg-red-700 rounded text-sm font-medium">
          Cancel
        </button>
      </div>

      <!-- Status -->
      <div v-if="statusMessage" class="text-center text-xs" :class="statusIsError ? 'text-red-400' : 'text-green-400'">
        {{ statusMessage }}
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { ApiService } from '@/services/ApiService';
import { useModal } from '@/composables/useModal';

const apiService = new ApiService();
const { closeModal, onParentClose } = useModal();

type WidgetState = 'LOADING' | 'ERROR' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
const state = ref<WidgetState>('LOADING');
const error = ref('');
const statusMessage = ref('');
const statusIsError = ref(false);

interface EnrichedItem {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  amount: number;
}

interface SelectedItem { itemId: string; amount: number; }

interface ExchangeData {
  myLeaseId: string;
  partnerLeaseId: string;
  partnerName: string;
  myTransferItems: EnrichedItem[];
  partnerTransferItems: EnrichedItem[];
  mySilver: number;
  myGold: number;
  mySilverOffer: number;
  myGoldOffer: number;
  mySelectedItems: SelectedItem[];
  myMessage: string;
  myAccepted: boolean;
  partnerSilverOffer: number;
  partnerGoldOffer: number;
  partnerSelectedItems: SelectedItem[];
  partnerMessage: string;
  partnerAccepted: boolean;
}

const data = ref<ExchangeData>({
  myLeaseId: '', partnerLeaseId: '', partnerName: '',
  myTransferItems: [], partnerTransferItems: [],
  mySilver: 0, myGold: 0,
  mySilverOffer: 0, myGoldOffer: 0, mySelectedItems: [] as SelectedItem[], myMessage: '', myAccepted: false,
  partnerSilverOffer: 0, partnerGoldOffer: 0, partnerSelectedItems: [] as SelectedItem[], partnerMessage: '', partnerAccepted: false,
});

const silverOffer = ref(0);
const goldOffer = ref(0);
const message = ref('');
const selectedItems = ref<SelectedItem[]>([]);
const lastClickedItemId = ref<string | null>(null);

let progressId = '';
let refreshInterval: ReturnType<typeof setInterval> | null = null;
let cleanupParentClose: (() => void) | null = null;

const selectedItemDetail = computed(() => {
  if (!lastClickedItemId.value) return null;
  return data.value.partnerTransferItems.find(i => i.itemId === lastClickedItemId.value) || null;
});

function showStatus(msg: string, isError = false) {
  statusMessage.value = msg;
  statusIsError.value = isError;
  setTimeout(() => { statusMessage.value = ''; }, 3000);
}

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
};

function isSelected(itemId: string): boolean {
  return selectedItems.value.some(s => s.itemId === itemId);
}

function getSelectedAmount(itemId: string): number {
  const sel = selectedItems.value.find(s => s.itemId === itemId);
  return sel ? sel.amount : 0;
}

function getPartnerSelectedAmount(itemId: string): number {
  const sel = data.value.partnerSelectedItems.find(s => s.itemId === itemId);
  return sel ? sel.amount : 0;
}

function toggleItemSelection(itemId: string) {
  lastClickedItemId.value = itemId;
  const idx = selectedItems.value.findIndex(s => s.itemId === itemId);
  if (idx >= 0) {
    selectedItems.value.splice(idx, 1);
  } else {
    selectedItems.value.push({ itemId, amount: 1 });
  }
}

function setSelectedAmount(itemId: string, amount: number) {
  const item = data.value.partnerTransferItems.find(i => i.itemId === itemId);
  const maxAmount = item ? item.amount : 1;
  amount = Math.max(1, Math.min(amount, maxAmount));
  const sel = selectedItems.value.find(s => s.itemId === itemId);
  if (sel) {
    sel.amount = amount;
  }
}

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  progressId = params.get('progressId') || '';

  if (!progressId) {
    error.value = 'No progressId provided';
    state.value = 'ERROR';
    return;
  }

  // Listen for parent closing this modal (X button) — treat as cancel
  cleanupParentClose = onParentClose(async () => {
    if (refreshInterval) clearInterval(refreshInterval);
    if (state.value === 'ACTIVE' && progressId) {
      try {
        await apiService.post('/control/player/exchange/cancel', {}, { params: { progressId } });
      } catch (e) {
        // ignore — modal is closing
      }
    }
  });

  await loadData();

  refreshInterval = setInterval(async () => {
    if (state.value === 'ACTIVE') {
      await loadData(true);
    }
  }, 5000);
});

onBeforeUnmount(() => {
  if (refreshInterval) clearInterval(refreshInterval);
  if (cleanupParentClose) cleanupParentClose();
});

async function loadData(silent = false) {
  try {
    const result = await apiService.get<ExchangeData>('/control/player/exchange', { progressId });
    data.value = result;

    if (!silent) {
      silverOffer.value = result.mySilverOffer;
      goldOffer.value = result.myGoldOffer;
      message.value = result.myMessage;
      selectedItems.value = result.mySelectedItems.map(s => ({ ...s }));
    }

    state.value = 'ACTIVE';
  } catch (e: any) {
    if (silent) {
      // Lease gone during refresh → exchange was completed or cancelled by partner
      const status = (e as any).response?.status;
      if (status === 404 || status === 400) {
        // If both had accepted, it was a successful transfer; otherwise cancelled
        if (data.value.myAccepted && data.value.partnerAccepted) {
          state.value = 'COMPLETED';
        } else {
          state.value = 'CANCELLED';
        }
        if (refreshInterval) clearInterval(refreshInterval);
      }
    } else {
      error.value = e.response?.data?.message || e.message || 'Failed to load';
      state.value = 'ERROR';
    }
  }
}

async function updateOffer() {
  try {
    await apiService.post('/control/player/exchange/update', {
      selectedItems: selectedItems.value,
      silverOffer: silverOffer.value,
      goldOffer: goldOffer.value,
      message: message.value,
    }, { params: { progressId } });
    showStatus('Offer updated');
    await loadData(true);
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to update', true);
  }
}

async function acceptExchange() {
  if (data.value.myAccepted) return;

  try {
    const result = await apiService.post<{ accepted: boolean; completed: boolean }>(
      '/control/player/exchange/accept',
      {
        selectedItems: selectedItems.value,
        silverOffer: silverOffer.value,
        goldOffer: goldOffer.value,
        message: message.value,
      },
      { params: { progressId } }
    );
    if (result.completed) {
      state.value = 'COMPLETED';
    } else {
      showStatus('Accepted — waiting for partner');
      await loadData(true);
    }
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to accept', true);
  }
}

async function revokeAccept() {
  try {
    // Just send an update with current values — this sets accepted=false
    await apiService.post('/control/player/exchange/update', {
      selectedItems: selectedItems.value,
      silverOffer: silverOffer.value,
      goldOffer: goldOffer.value,
      message: message.value,
    }, { params: { progressId } });
    showStatus('Accept revoked');
    await loadData(true);
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to revoke', true);
  }
}

async function cancelExchange() {
  try {
    await apiService.post('/control/player/exchange/cancel', {}, { params: { progressId } });
    state.value = 'CANCELLED';
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to cancel', true);
  }
}

function closeWidget() {
  closeModal('user_close');
}
</script>
