<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-xl font-bold text-amber-400">Trade</h1>
            <p class="text-gray-400 text-xs mt-0.5">Buy and sell items</p>
          </div>
          <div class="flex items-center gap-4 text-sm">
            <span class="text-gray-300">
              Silver: <span class="text-amber-300 font-bold">{{ silver }}</span>
            </span>
            <span class="text-gray-300">
              Gold: <span class="text-yellow-400 font-bold">{{ gold }}</span>
            </span>
          </div>
        </div>
      </div>
    </header>

    <!-- Loading State -->
    <main v-if="state === 'LOADING'" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-3 text-sm">Loading...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="state === 'ERROR'" class="flex-1 container mx-auto px-4 py-6">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-5 text-center">
        <h2 class="text-lg font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300 text-sm">{{ error }}</p>
      </div>
    </main>

    <!-- Active State -->
    <main v-else-if="state === 'ACTIVE'" class="flex-1 container mx-auto px-3 py-4">
      <!-- Trade Message -->
      <div v-if="tradeMessage" class="mb-3 p-2 rounded-lg text-center text-xs font-medium transition-all"
           :class="tradeMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ tradeMessage.text }}
      </div>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <!-- Left Column: Shop Items -->
        <div>
          <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
            <h2 class="text-base font-bold text-amber-400 mb-2">Shop</h2>

            <div v-if="shopItems.length === 0" class="text-center py-6 text-gray-500 text-sm">
              No items for sale
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in shopItems"
                :key="'shop-' + item.itemId"
                class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
                :class="selectedShopItem?.itemId === item.itemId ? 'border-amber-400 shadow-lg shadow-amber-400/20' : 'border-gray-600'"
                :title="item.name + ' - ' + item.buyPrice + ' Silver'"
                @click="selectShopItem(item)"
              >
                <img
                  v-if="item.texture"
                  :src="getAssetUrl(item.texture)"
                  :alt="item.name"
                  class="w-10 h-10 object-contain"
                  style="image-rendering: pixelated;"
                  @error="onImageError($event)"
                />
                <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
                <span
                  v-if="item.amount > 1"
                  class="absolute -bottom-1 -right-1 bg-amber-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                >{{ item.amount > 99 ? '99+' : item.amount }}</span>
              </div>
            </div>

            <!-- Selected Shop Item Detail -->
            <div v-if="selectedShopItem" class="mt-3 pt-3 border-t border-gray-700">
              <div class="flex items-start gap-2">
                <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                  <img
                    v-if="selectedShopItem.texture"
                    :src="getAssetUrl(selectedShopItem.texture)"
                    :alt="selectedShopItem.name"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-gray-500 text-xs">No icon</span>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="font-semibold text-amber-300 truncate text-sm">{{ selectedShopItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedShopItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedShopItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedShopItem.description }}</p>
                  <p class="text-xs text-amber-400 mt-0.5 font-medium">Price: {{ selectedShopItem.buyPrice }} Silver</p>
                </div>
              </div>
              <div class="mt-2 flex items-center gap-2">
                <input
                  v-model.number="buyAmount"
                  type="number"
                  min="1"
                  :max="selectedShopItem.amount"
                  class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-amber-400"
                />
                <button
                  @click="buyItem"
                  :disabled="trading || buyTotal > silver"
                  class="flex-1 px-3 py-1.5 bg-amber-600 text-white rounded hover:bg-amber-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium"
                >
                  Buy ({{ buyTotal }} Silver)
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Right Column: Backpack Items -->
        <div>
          <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
            <h2 class="text-base font-bold text-blue-400 mb-2">Backpack</h2>

            <div v-if="backpackItems.length === 0" class="text-center py-6 text-gray-500 text-sm">
              Backpack is empty
            </div>

            <div v-else class="grid grid-cols-5 gap-2">
              <div
                v-for="item in backpackItems"
                :key="'bp-' + item.itemId"
                class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
                :class="selectedBackpackItem?.itemId === item.itemId ? 'border-blue-400 shadow-lg shadow-blue-400/20' : 'border-gray-600'"
                :title="item.name + ' - Sell: ' + item.sellPrice + ' Silver'"
                @click="selectBackpackItem(item)"
              >
                <img
                  v-if="item.texture"
                  :src="getAssetUrl(item.texture)"
                  :alt="item.name"
                  class="w-10 h-10 object-contain"
                  style="image-rendering: pixelated;"
                  @error="onImageError($event)"
                />
                <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
                <span
                  v-if="item.count > 1"
                  class="absolute -bottom-1 -right-1 bg-blue-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center"
                >{{ item.count > 99 ? '99+' : item.count }}</span>
              </div>
            </div>

            <!-- Selected Backpack Item Detail -->
            <div v-if="selectedBackpackItem" class="mt-3 pt-3 border-t border-gray-700">
              <div class="flex items-start gap-2">
                <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                  <img
                    v-if="selectedBackpackItem.texture"
                    :src="getAssetUrl(selectedBackpackItem.texture)"
                    :alt="selectedBackpackItem.name"
                    class="w-10 h-10 object-contain"
                    style="image-rendering: pixelated;"
                    @error="onImageError($event)"
                  />
                  <span v-else class="text-gray-500 text-xs">No icon</span>
                </div>
                <div class="min-w-0 flex-1">
                  <h3 class="font-semibold text-blue-300 truncate text-sm">{{ selectedBackpackItem.name }}</h3>
                  <p class="text-xs text-gray-400">{{ selectedBackpackItem.itemType || 'Unknown type' }}</p>
                  <p v-if="selectedBackpackItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedBackpackItem.description }}</p>
                  <p class="text-xs text-green-400 mt-0.5 font-medium">Sell: {{ selectedBackpackItem.sellPrice }} Silver</p>
                </div>
              </div>
              <div class="mt-2 flex items-center gap-2">
                <input
                  v-model.number="sellAmount"
                  type="number"
                  min="1"
                  :max="selectedBackpackItem.count"
                  class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-blue-400"
                />
                <button
                  @click="sellItem"
                  :disabled="trading"
                  class="flex-1 px-3 py-1.5 bg-green-600 text-white rounded hover:bg-green-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium"
                >
                  Sell ({{ sellTotal }} Silver)
                </button>
              </div>
            </div>
          </div>

          <!-- Gold Exchange -->
          <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700 mt-4">
            <h2 class="text-base font-bold text-yellow-400 mb-2">Gold Exchange</h2>
            <p class="text-xs text-gray-400 mb-2">Rate: 1 Gold = {{ goldExchangeRate }} Silver</p>
            <div class="flex items-center gap-2">
              <input
                v-model.number="goldExchangeAmount"
                type="number"
                min="1"
                :max="gold"
                placeholder="Gold"
                class="w-24 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-yellow-400"
              />
              <button
                @click="exchangeGold"
                :disabled="trading || goldExchangeAmount <= 0 || goldExchangeAmount > gold"
                class="flex-1 px-3 py-1.5 bg-yellow-600 text-white rounded hover:bg-yellow-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium"
              >
                Exchange ({{ goldExchangeSilver }} Silver)
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

interface ShopItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  amount: number;
  buyPrice: number;
}

interface BackpackItemInfo {
  itemId: string;
  name: string;
  itemType: string | null;
  texture: string | null;
  description: string | null;
  count: number;
  sellPrice: number;
}

type WidgetState = 'LOADING' | 'ERROR' | 'ACTIVE';

const state = ref<WidgetState>('LOADING');
const error = ref<string | null>(null);
const tradeMessage = ref<{ text: string; type: 'success' | 'error' } | null>(null);

const progressId = ref('');
const worldId = ref('');
const silver = ref(0);
const gold = ref(0);
const goldExchangeRate = ref(10);

const shopItems = ref<ShopItemInfo[]>([]);
const backpackItems = ref<BackpackItemInfo[]>([]);

const selectedShopItem = ref<ShopItemInfo | null>(null);
const selectedBackpackItem = ref<BackpackItemInfo | null>(null);
const buyAmount = ref(1);
const sellAmount = ref(1);
const goldExchangeAmount = ref(1);

const trading = ref(false);

const buyTotal = computed(() => {
  if (!selectedShopItem.value) return 0;
  return selectedShopItem.value.buyPrice * buyAmount.value;
});

const sellTotal = computed(() => {
  if (!selectedBackpackItem.value) return 0;
  return selectedBackpackItem.value.sellPrice * sellAmount.value;
});

const goldExchangeSilver = computed(() => {
  return Math.round(goldExchangeAmount.value * goldExchangeRate.value);
});

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !worldId.value) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement;
  img.style.display = 'none';
};

const showMessage = (text: string, type: 'success' | 'error') => {
  tradeMessage.value = { text, type };
  setTimeout(() => { tradeMessage.value = null; }, 3000);
};

const selectShopItem = (item: ShopItemInfo) => {
  selectedBackpackItem.value = null;
  if (selectedShopItem.value?.itemId === item.itemId) {
    selectedShopItem.value = null;
  } else {
    selectedShopItem.value = item;
    buyAmount.value = 1;
  }
};

const selectBackpackItem = (item: BackpackItemInfo) => {
  selectedShopItem.value = null;
  if (selectedBackpackItem.value?.itemId === item.itemId) {
    selectedBackpackItem.value = null;
  } else {
    selectedBackpackItem.value = item;
    sellAmount.value = 1;
  }
};

const loadShop = async () => {
  try {
    const response = await apiService.get<{
      worldId: string;
      goldExchangeRate: number;
      shopItems: ShopItemInfo[];
      backpackItems: BackpackItemInfo[];
      silver: number;
      gold: number;
    }>(`/control/player/trade-widget?progressId=${encodeURIComponent(progressId.value)}`);

    worldId.value = response.worldId || '';
    goldExchangeRate.value = response.goldExchangeRate || 10;
    shopItems.value = response.shopItems || [];
    backpackItems.value = response.backpackItems || [];
    silver.value = response.silver || 0;
    gold.value = response.gold || 0;
    state.value = 'ACTIVE';
  } catch (err: any) {
    console.error('[TradeWidget] Failed to load shop:', err);
    error.value = err?.response?.data?.error || 'Failed to load trade data.';
    state.value = 'ERROR';
  }
};

const buyItem = async () => {
  if (!selectedShopItem.value || trading.value) return;
  const amount = Math.max(1, Math.min(buyAmount.value, selectedShopItem.value.amount));
  trading.value = true;

  try {
    const result = await apiService.post<{ totalCost: number; amount: number }>('/control/player/trade-widget/buy', {
      progressId: progressId.value,
      itemId: selectedShopItem.value.itemId,
      amount,
    });

    selectedShopItem.value = null;
    showMessage(`Bought ${result.amount} item(s) for ${result.totalCost} Silver`, 'success');
    await loadShop();
  } catch (err: any) {
    console.error('[TradeWidget] Buy failed:', err);
    showMessage(err?.response?.data?.error || 'Purchase failed', 'error');
  } finally {
    trading.value = false;
  }
};

const sellItem = async () => {
  if (!selectedBackpackItem.value || trading.value) return;
  const amount = Math.max(1, Math.min(sellAmount.value, selectedBackpackItem.value.count));
  trading.value = true;

  try {
    const result = await apiService.post<{ totalRevenue: number; amount: number }>('/control/player/trade-widget/sell', {
      progressId: progressId.value,
      itemId: selectedBackpackItem.value.itemId,
      amount,
    });

    selectedBackpackItem.value = null;
    showMessage(`Sold ${result.amount} item(s) for ${result.totalRevenue} Silver`, 'success');
    await loadShop();
  } catch (err: any) {
    console.error('[TradeWidget] Sell failed:', err);
    showMessage(err?.response?.data?.error || 'Sale failed', 'error');
  } finally {
    trading.value = false;
  }
};

const exchangeGold = async () => {
  if (trading.value || goldExchangeAmount.value <= 0) return;
  trading.value = true;

  try {
    const result = await apiService.post<{ goldSpent: number; silverReceived: number }>('/control/player/trade-widget/exchange-gold', {
      progressId: progressId.value,
      goldAmount: goldExchangeAmount.value,
    });

    showMessage(`Exchanged ${result.goldSpent} Gold for ${result.silverReceived} Silver`, 'success');
    await loadShop();
  } catch (err: any) {
    console.error('[TradeWidget] Gold exchange failed:', err);
    showMessage(err?.response?.data?.error || 'Exchange failed', 'error');
  } finally {
    trading.value = false;
  }
};

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  const paramProgressId = params.get('progressId') || '';

  if (!paramProgressId) {
    error.value = 'Missing progressId parameter';
    state.value = 'ERROR';
    return;
  }

  progressId.value = paramProgressId;
  await loadShop();
});
</script>
