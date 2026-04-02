<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Header -->
    <header class="bg-gray-800 shadow-lg border-b border-gray-700">
      <div class="container mx-auto px-4 py-3">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-xl font-bold text-amber-400">Trade</h1>
          </div>
          <div class="flex items-center gap-4 text-sm">
            <span class="flex items-center gap-1">
              <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-5 h-5" style="image-rendering: pixelated;" />
              <span class="font-bold" :class="effectiveSilver < 0 ? 'text-red-400' : 'text-amber-300'">{{ effectiveSilver }}</span>
              <span v-if="netSilverChange !== 0" class="text-xs text-gray-500">({{ silver }})</span>
            </span>
            <span class="flex items-center gap-1">
              <img :src="getAssetUrl('n:textures/currencies/gold-coin.png')" class="w-5 h-5" style="image-rendering: pixelated;" />
              <span class="font-bold" :class="effectiveGold < 0 ? 'text-red-400' : 'text-yellow-400'">{{ effectiveGold }}</span>
              <span v-if="cartGoldExchange > 0" class="text-xs text-gray-500">({{ gold }})</span>
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
      <!-- Message -->
      <div v-if="tradeMessage" class="mb-3 p-2 rounded-lg text-center text-xs font-medium transition-all"
           :class="tradeMessage.type === 'success' ? 'bg-green-900/30 text-green-400 border border-green-700' : 'bg-red-900/30 text-red-400 border border-red-700'">
        {{ tradeMessage.text }}
      </div>

      <!-- Tabs -->
      <div class="flex border-b border-gray-700 mb-4">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          class="px-4 py-2 text-sm font-medium border-b-2 transition-colors"
          :class="activeTab === tab.id
            ? 'border-amber-400 text-amber-400'
            : 'border-transparent text-gray-400 hover:text-gray-200'"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
          <span v-if="tab.id === 'buy' && cartBuys.length > 0" class="ml-1 bg-amber-500 text-white text-xs rounded-full px-1.5">{{ cartBuys.length }}</span>
          <span v-if="tab.id === 'sell' && cartSells.length > 0" class="ml-1 bg-green-500 text-white text-xs rounded-full px-1.5">{{ cartSells.length }}</span>
          <span v-if="tab.id === 'cart' && cartItemCount > 0" class="ml-1 bg-amber-500 text-white text-xs rounded-full px-1.5">{{ cartItemCount }}</span>
        </button>
      </div>

      <!-- Buy Tab -->
      <div v-if="activeTab === 'buy'">
        <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
          <h2 class="text-base font-bold text-amber-400 mb-2">Shop</h2>

          <div v-if="effectiveShopItems.length === 0" class="text-center py-6 text-gray-500 text-sm">No items for sale</div>

          <div v-else class="grid grid-cols-5 gap-2">
            <div
              v-for="item in effectiveShopItems"
              :key="'shop-' + item.itemId"
              class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
              :class="selectedBuyItem?.itemId === item.itemId ? 'border-amber-400 shadow-lg shadow-amber-400/20' : 'border-gray-600'"
              :title="item.name + ' - ' + item.buyPrice + ' Silver'"
              @click="selectBuyItem(item)"
            >
              <img v-if="item.texture" :src="getAssetUrl(item.texture)" :alt="item.name" class="w-10 h-10 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
              <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
              <span v-if="item.amount > 1" class="absolute -bottom-1 -right-1 bg-amber-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">{{ item.amount > 99 ? '99+' : item.amount }}</span>
            </div>
          </div>

          <!-- Selected Buy Item -->
          <div v-if="selectedBuyItem" class="mt-3 pt-3 border-t border-gray-700">
            <div class="flex items-start gap-2">
              <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                <img v-if="selectedBuyItem.texture" :src="getAssetUrl(selectedBuyItem.texture)" class="w-10 h-10 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
              </div>
              <div class="min-w-0 flex-1">
                <h3 class="font-semibold text-amber-300 truncate text-sm">{{ selectedBuyItem.name }}</h3>
                <p class="text-xs text-gray-400">{{ selectedBuyItem.itemType || 'Unknown type' }}</p>
                <p v-if="selectedBuyItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedBuyItem.description }}</p>
                <p class="text-xs text-amber-400 mt-0.5 font-medium flex items-center gap-1">Price: {{ selectedBuyItem.buyPrice }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-3.5 h-3.5 inline" style="image-rendering: pixelated;" /> each</p>
              </div>
            </div>
            <div class="mt-2 flex items-center gap-2">
              <input v-model.number="buyAmount" type="number" min="1" :max="selectedBuyItem.amount" class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-amber-400" />
              <button @click="addToCartBuy" :disabled="selectedBuyItem.buyPrice * buyAmount > effectiveSilver" class="flex-1 px-3 py-1.5 bg-amber-600 text-white rounded hover:bg-amber-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors text-sm font-medium">
                Add to Cart ({{ selectedBuyItem.buyPrice * buyAmount }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-4 h-4 inline" style="image-rendering: pixelated;" />)
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Sell Tab -->
      <div v-if="activeTab === 'sell'">
        <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
          <h2 class="text-base font-bold text-green-400 mb-2">Backpack</h2>

          <div v-if="effectiveBackpackItems.length === 0" class="text-center py-6 text-gray-500 text-sm">Backpack is empty</div>

          <div v-else class="grid grid-cols-5 gap-2">
            <div
              v-for="item in effectiveBackpackItems"
              :key="'bp-' + item.itemId"
              class="relative w-14 h-14 rounded border-2 cursor-pointer transition-all hover:border-gray-500 flex items-center justify-center bg-gray-700"
              :class="selectedSellItem?.itemId === item.itemId ? 'border-green-400 shadow-lg shadow-green-400/20' : 'border-gray-600'"
              :title="item.name + ' - Sell: ' + item.sellPrice + ' Silver'"
              @click="selectSellItem(item)"
            >
              <img v-if="item.texture" :src="getAssetUrl(item.texture)" :alt="item.name" class="w-10 h-10 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
              <span v-else class="text-xs text-gray-400 text-center leading-tight px-1">{{ item.name?.substring(0, 6) }}</span>
              <span v-if="item.count > 1" class="absolute -bottom-1 -right-1 bg-green-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center">{{ item.count > 99 ? '99+' : item.count }}</span>
            </div>
          </div>

          <!-- Selected Sell Item -->
          <div v-if="selectedSellItem" class="mt-3 pt-3 border-t border-gray-700">
            <div class="flex items-start gap-2">
              <div class="w-14 h-14 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0">
                <img v-if="selectedSellItem.texture" :src="getAssetUrl(selectedSellItem.texture)" class="w-10 h-10 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
              </div>
              <div class="min-w-0 flex-1">
                <h3 class="font-semibold text-green-300 truncate text-sm">{{ selectedSellItem.name }}</h3>
                <p class="text-xs text-gray-400">{{ selectedSellItem.itemType || 'Unknown type' }}</p>
                <p v-if="selectedSellItem.description" class="text-xs text-gray-500 mt-0.5">{{ selectedSellItem.description }}</p>
                <p class="text-xs text-green-400 mt-0.5 font-medium flex items-center gap-1">Sell: {{ selectedSellItem.sellPrice }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-3.5 h-3.5 inline" style="image-rendering: pixelated;" /> each</p>
              </div>
            </div>
            <div class="mt-2 flex items-center gap-2">
              <input v-model.number="sellAmount" type="number" min="1" :max="selectedSellItem.count" class="w-20 px-2 py-1 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-green-400" />
              <button @click="addToCartSell" class="flex-1 px-3 py-1.5 bg-green-600 text-white rounded hover:bg-green-500 transition-colors text-sm font-medium">
                Add to Cart (+{{ selectedSellItem.sellPrice * sellAmount }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-4 h-4 inline" style="image-rendering: pixelated;" />)
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Exchange Tab -->
      <div v-if="activeTab === 'exchange'">
        <div class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
          <h2 class="text-base font-bold text-yellow-400 mb-2">Gold Exchange</h2>
          <p class="text-xs text-gray-400 mb-3 flex items-center gap-1">Rate: 1 <img :src="getAssetUrl('n:textures/currencies/gold-coin.png')" class="w-4 h-4 inline" style="image-rendering: pixelated;" /> = {{ goldExchangeRate }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-4 h-4 inline" style="image-rendering: pixelated;" /></p>
          <div class="flex items-center gap-2">
            <input v-model.number="cartGoldExchange" type="number" min="0" :max="gold" placeholder="Gold amount" class="w-28 px-2 py-1.5 bg-gray-700 border border-gray-600 rounded text-sm text-gray-100 focus:outline-none focus:border-yellow-400" />
            <img :src="getAssetUrl('n:textures/currencies/gold-coin.png')" class="w-5 h-5" style="image-rendering: pixelated;" />
            <span class="text-gray-400 text-sm">=</span>
            <span class="text-yellow-300 font-bold text-sm flex items-center gap-1">{{ Math.round(cartGoldExchange * goldExchangeRate) }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-5 h-5" style="image-rendering: pixelated;" /></span>
          </div>
        </div>
      </div>

      <!-- Cart Tab -->
      <div v-if="activeTab === 'cart'" class="bg-gray-800 rounded-lg shadow-md p-3 border border-gray-700">
        <h2 class="text-base font-bold text-amber-400 mb-2">Cart</h2>

        <div v-if="!hasCartItems" class="text-center py-6 text-gray-500 text-sm">
          Cart is empty. Add items from Buy or Sell tabs.
        </div>

        <template v-else>

        <!-- Buy items in cart -->
        <div v-for="item in cartBuysEnriched" :key="'cart-buy-' + item.itemId" class="flex items-center gap-2 text-sm py-1.5 border-b border-gray-700">
          <div
            class="w-8 h-8 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0 cursor-pointer hover:border-red-400 transition-colors"
            title="Remove 1"
            @click="decrementCartBuy(item.itemId)"
          >
            <img v-if="item.texture" :src="getAssetUrl(item.texture)" class="w-6 h-6 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
            <span v-else class="text-gray-500 text-xs">?</span>
          </div>
          <span class="flex-1 text-gray-300">Buy {{ item.amount }}x {{ item.name }}</span>
          <span class="text-red-400 flex items-center gap-0.5">-{{ item.totalPrice }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-3.5 h-3.5" style="image-rendering: pixelated;" /></span>
          <button @click="removeFromCartBuy(item.itemId)" class="text-gray-500 hover:text-red-400 text-xs">x</button>
        </div>

        <!-- Sell items in cart -->
        <div v-for="item in cartSellsEnriched" :key="'cart-sell-' + item.itemId" class="flex items-center gap-2 text-sm py-1.5 border-b border-gray-700">
          <div
            class="w-8 h-8 rounded bg-gray-700 border border-gray-600 flex items-center justify-center flex-shrink-0 cursor-pointer hover:border-red-400 transition-colors"
            title="Remove 1"
            @click="decrementCartSell(item.itemId)"
          >
            <img v-if="item.texture" :src="getAssetUrl(item.texture)" class="w-6 h-6 object-contain" style="image-rendering: pixelated;" @error="onImageError($event)" />
            <span v-else class="text-gray-500 text-xs">?</span>
          </div>
          <span class="flex-1 text-gray-300">Sell {{ item.amount }}x {{ item.name }}</span>
          <span class="text-green-400 flex items-center gap-0.5">+{{ item.totalPrice }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-3.5 h-3.5" style="image-rendering: pixelated;" /></span>
          <button @click="removeFromCartSell(item.itemId)" class="text-gray-500 hover:text-red-400 text-xs">x</button>
        </div>

        <!-- Gold exchange in cart -->
        <div v-if="cartGoldExchange > 0" class="flex items-center justify-between text-sm py-1.5 border-b border-gray-700">
          <span class="text-gray-300 flex items-center gap-1">Exchange {{ cartGoldExchange }} <img :src="getAssetUrl('n:textures/currencies/gold-coin.png')" class="w-3.5 h-3.5" style="image-rendering: pixelated;" /></span>
          <span class="text-green-400 flex items-center gap-0.5">+{{ Math.round(cartGoldExchange * goldExchangeRate) }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-3.5 h-3.5" style="image-rendering: pixelated;" /></span>
        </div>

        <!-- Net total -->
        <div class="flex items-center justify-between text-sm font-bold pt-2">
          <span class="text-gray-200">Net</span>
          <span class="flex items-center gap-0.5" :class="netSilverChange >= 0 ? 'text-green-400' : 'text-red-400'">
            {{ netSilverChange >= 0 ? '+' : '' }}{{ netSilverChange }} <img :src="getAssetUrl('n:textures/currencies/silver-coin.png')" class="w-4 h-4" style="image-rendering: pixelated;" />
          </span>
        </div>

        <div class="mt-3 flex gap-2">
          <button @click="applyTrade" :disabled="applying || !canApply" class="flex-1 px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-500 disabled:bg-gray-600 disabled:text-gray-400 transition-colors font-semibold text-sm">
            {{ applying ? 'Processing...' : 'Auftrag ausfuehren' }}
          </button>
          <button @click="clearCart" class="px-4 py-2 bg-gray-700 text-gray-300 rounded-lg hover:bg-gray-600 transition-colors text-sm">
            Clear
          </button>
        </div>
        </template>
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

interface CartItem {
  itemId: string;
  name: string;
  amount: number;
  totalPrice: number;
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

const activeTab = ref<'buy' | 'sell' | 'exchange' | 'cart'>('buy');
const tabs = [
  { id: 'buy' as const, label: 'Buy' },
  { id: 'sell' as const, label: 'Sell' },
  { id: 'exchange' as const, label: 'Exchange' },
  { id: 'cart' as const, label: 'Cart' },
];

// Selection
const selectedBuyItem = ref<ShopItemInfo | null>(null);
const selectedSellItem = ref<BackpackItemInfo | null>(null);
const buyAmount = ref(1);
const sellAmount = ref(1);

// Cart
const cartBuys = ref<CartItem[]>([]);
const cartSells = ref<CartItem[]>([]);
const cartGoldExchange = ref(0);
const applying = ref(false);

const hasCartItems = computed(() => cartBuys.value.length > 0 || cartSells.value.length > 0 || cartGoldExchange.value > 0);

const netSilverChange = computed(() => {
  const buyTotal = cartBuys.value.reduce((sum, i) => sum + i.totalPrice, 0);
  const sellTotal = cartSells.value.reduce((sum, i) => sum + i.totalPrice, 0);
  const exchangeTotal = Math.round(cartGoldExchange.value * goldExchangeRate.value);
  return sellTotal + exchangeTotal - buyTotal;
});

const effectiveSilver = computed(() => silver.value + netSilverChange.value);
const effectiveGold = computed(() => gold.value - (cartGoldExchange.value || 0));
const canApply = computed(() => hasCartItems.value && effectiveSilver.value >= 0 && effectiveGold.value >= 0);
const cartItemCount = computed(() => cartBuys.value.length + cartSells.value.length + (cartGoldExchange.value > 0 ? 1 : 0));

// Effective item counts: subtract cart amounts from displayed quantities
const effectiveShopItems = computed(() => {
  return shopItems.value.map(item => {
    const inCart = cartBuys.value.find(c => c.itemId === item.itemId);
    return { ...item, amount: item.amount - (inCart?.amount || 0) };
  }).filter(item => item.amount > 0);
});

const effectiveBackpackItems = computed(() => {
  return backpackItems.value.map(item => {
    const inCart = cartSells.value.find(c => c.itemId === item.itemId);
    return { ...item, count: item.count - (inCart?.amount || 0) };
  }).filter(item => item.count > 0);
});

const getAssetUrl = (texturePath: string): string => {
  if (!texturePath || !worldId.value) return '';
  return `${apiService.getBaseUrl()}/control/player/assets/${texturePath}`;
};

const onImageError = (event: Event) => {
  (event.target as HTMLImageElement).style.display = 'none';
};

const showMessage = (text: string, type: 'success' | 'error') => {
  tradeMessage.value = { text, type };
  setTimeout(() => { tradeMessage.value = null; }, 4000);
};

const selectBuyItem = (item: ShopItemInfo) => {
  if (selectedBuyItem.value?.itemId === item.itemId) {
    // Second click on same item: add 1 to cart directly
    addToCartBuy();
    return;
  }
  selectedBuyItem.value = item;
  buyAmount.value = 1;
};

const selectSellItem = (item: BackpackItemInfo) => {
  if (selectedSellItem.value?.itemId === item.itemId) {
    // Second click on same item: add 1 to cart directly
    addToCartSell();
    return;
  }
  selectedSellItem.value = item;
  sellAmount.value = 1;
};

const addToCartBuy = () => {
  if (!selectedBuyItem.value) return;
  const item = selectedBuyItem.value;
  // Use effective amount (original minus already in cart)
  const effectiveItem = effectiveShopItems.value.find(i => i.itemId === item.itemId);
  const maxAmount = effectiveItem?.amount || 0;
  if (maxAmount <= 0) return;
  const amount = Math.max(1, Math.min(buyAmount.value, maxAmount));
  const existing = cartBuys.value.find(c => c.itemId === item.itemId);
  if (existing) {
    existing.amount += amount;
    existing.totalPrice = existing.amount * item.buyPrice;
  } else {
    cartBuys.value.push({ itemId: item.itemId, name: item.name || item.itemId, amount, totalPrice: amount * item.buyPrice });
  }
  selectedBuyItem.value = null;
};

const addToCartSell = () => {
  if (!selectedSellItem.value) return;
  const item = selectedSellItem.value;
  // Use effective count (original minus already in cart)
  const effectiveItem = effectiveBackpackItems.value.find(i => i.itemId === item.itemId);
  const maxCount = effectiveItem?.count || 0;
  if (maxCount <= 0) return;
  const amount = Math.max(1, Math.min(sellAmount.value, maxCount));
  const existing = cartSells.value.find(c => c.itemId === item.itemId);
  if (existing) {
    existing.amount += amount;
    existing.totalPrice = existing.amount * item.sellPrice;
  } else {
    cartSells.value.push({ itemId: item.itemId, name: item.name || item.itemId, amount, totalPrice: amount * item.sellPrice });
  }
  selectedSellItem.value = null;
};

const removeFromCartBuy = (itemId: string) => {
  cartBuys.value = cartBuys.value.filter(c => c.itemId !== itemId);
};

const removeFromCartSell = (itemId: string) => {
  cartSells.value = cartSells.value.filter(c => c.itemId !== itemId);
};

const decrementCartBuy = (itemId: string) => {
  const item = cartBuys.value.find(c => c.itemId === itemId);
  if (!item) return;
  const shopItem = shopItems.value.find(s => s.itemId === itemId);
  const unitPrice = shopItem?.buyPrice || 0;
  if (item.amount <= 1) {
    removeFromCartBuy(itemId);
  } else {
    item.amount--;
    item.totalPrice = item.amount * unitPrice;
  }
};

const decrementCartSell = (itemId: string) => {
  const item = cartSells.value.find(c => c.itemId === itemId);
  if (!item) return;
  const bpItem = backpackItems.value.find(b => b.itemId === itemId);
  const unitPrice = bpItem?.sellPrice || 0;
  if (item.amount <= 1) {
    removeFromCartSell(itemId);
  } else {
    item.amount--;
    item.totalPrice = item.amount * unitPrice;
  }
};

// Enrich cart items with texture from shop/backpack data
const cartBuysEnriched = computed(() => {
  return cartBuys.value.map(c => {
    const shopItem = shopItems.value.find(s => s.itemId === c.itemId);
    return { ...c, texture: shopItem?.texture || null };
  });
});

const cartSellsEnriched = computed(() => {
  return cartSells.value.map(c => {
    const bpItem = backpackItems.value.find(b => b.itemId === c.itemId);
    return { ...c, texture: bpItem?.texture || null };
  });
});

const clearCart = () => {
  cartBuys.value = [];
  cartSells.value = [];
  cartGoldExchange.value = 0;
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
    error.value = err?.response?.data?.error || 'Failed to load trade data.';
    state.value = 'ERROR';
  }
};

const applyTrade = async () => {
  if (applying.value) return;
  applying.value = true;

  try {
    const result = await apiService.post<{
      totalBuyCost: number;
      totalSellRevenue: number;
      goldExchanged: number;
      silverFromGold: number;
    }>('/control/player/trade-widget/apply', {
      progressId: progressId.value,
      buys: cartBuys.value.map(c => ({ itemId: c.itemId, amount: c.amount })),
      sells: cartSells.value.map(c => ({ itemId: c.itemId, amount: c.amount })),
      goldExchange: cartGoldExchange.value > 0 ? cartGoldExchange.value : null,
    });

    const parts: string[] = [];
    if (result.totalBuyCost > 0) parts.push(`Bought for ${result.totalBuyCost} Silver`);
    if (result.totalSellRevenue > 0) parts.push(`Sold for ${result.totalSellRevenue} Silver`);
    if (result.goldExchanged > 0) parts.push(`Exchanged ${result.goldExchanged} Gold for ${result.silverFromGold} Silver`);
    showMessage(parts.join('. ') || 'Trade completed', 'success');

    clearCart();
    await loadShop();
  } catch (err: any) {
    showMessage(err?.response?.data?.error || 'Trade failed', 'error');
  } finally {
    applying.value = false;
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
