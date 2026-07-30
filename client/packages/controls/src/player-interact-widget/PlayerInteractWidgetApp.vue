<template>
  <div class="min-h-screen flex flex-col bg-gray-900 text-gray-100">
    <!-- Loading State -->
    <main v-if="state === 'LOADING'" class="flex-1 flex items-center justify-center">
      <div class="text-center">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-amber-400 mx-auto"></div>
        <p class="text-gray-400 mt-4">Loading...</p>
      </div>
    </main>

    <!-- Error State -->
    <main v-else-if="state === 'ERROR'" class="flex-1 flex items-center justify-center p-4">
      <div class="bg-red-900/30 border border-red-700 rounded-lg p-6 text-center max-w-md">
        <h2 class="text-xl font-bold text-red-400 mb-2">Error</h2>
        <p class="text-red-300">{{ error }}</p>
        <button @click="closeWidget" class="mt-4 px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded text-sm">
          Close
        </button>
      </div>
    </main>

    <!-- Active State -->
    <main v-else-if="state === 'ACTIVE'" class="flex-1 flex flex-col p-4 gap-4">

      <!-- Player Header -->
      <div class="flex items-center gap-3 bg-gray-800 rounded-lg p-3">
        <div class="w-12 h-12 bg-gray-700 rounded-full flex items-center justify-center text-2xl">
          👤
        </div>
        <div class="flex-1">
          <h1 class="text-lg font-bold text-amber-400">{{ data.targetName }}</h1>
        </div>
        <button @click="reload" class="w-8 h-8 flex items-center justify-center bg-gray-700 hover:bg-gray-600 rounded-lg text-gray-400 hover:text-gray-200 transition-colors" title="Refresh">
          ↻
        </button>
      </div>

      <!-- Emoji Bar -->
      <div class="bg-gray-800 rounded-lg p-3">
        <div class="flex flex-wrap gap-2 justify-center">
          <button
            v-for="emoji in emojis"
            :key="emoji.id"
            @click="sendEmoji(emoji.id)"
            :disabled="emojiCooldown"
            class="w-10 h-10 text-xl rounded-lg transition-colors flex items-center justify-center"
            :class="emojiCooldown
              ? 'bg-gray-700 opacity-50 cursor-not-allowed'
              : 'bg-gray-700 hover:bg-gray-600 cursor-pointer'"
            :title="emoji.label"
          >
            {{ emoji.icon }}
          </button>
        </div>
        <p v-if="emojiCooldown" class="text-xs text-gray-500 text-center mt-2">
          Wait {{ cooldownRemaining }}s...
        </p>
      </div>

      <!-- Actions -->
      <div class="bg-gray-800 rounded-lg p-3 flex flex-col gap-2">

        <!-- Team Section -->
        <template v-if="data.hasTeam && data.teamInvitations.length === 0">
          <button @click="inviteToTeam" class="action-btn">
            ⚔️ Team einladen
          </button>
        </template>

        <template v-if="data.teamInvitations.length > 0">
          <div v-for="inv in data.teamInvitations" :key="inv.teamId" class="flex gap-2">
            <button @click="acceptTeam(inv.teamId)" class="action-btn flex-1 !bg-green-800 hover:!bg-green-700">
              ✅ Team beitreten: {{ inv.title }}
            </button>
            <button @click="declineTeam(inv.teamId)" class="action-btn !bg-red-800 hover:!bg-red-700 !px-3">
              ✖
            </button>
          </div>
          <p v-if="data.hasTeam" class="text-xs text-gray-400 pl-1">
            (Aktuelles Team wird verlassen)
          </p>
        </template>

        <!-- Trade Section -->
        <template v-if="data.tradeOffers.length === 0">
          <button @click="offerTrade" class="action-btn">
            🤝 Tausch anbieten
          </button>
        </template>

        <template v-if="data.tradeOffers.length > 0">
          <div v-for="offer in data.tradeOffers" :key="offer.leaseId" class="flex gap-2">
            <button @click="acceptTrade(offer.leaseId)" class="action-btn flex-1 !bg-green-800 hover:!bg-green-700">
              ✅ Tausch annehmen
            </button>
            <button @click="declineTrade(offer.leaseId)" class="action-btn !bg-red-800 hover:!bg-red-700 !px-3">
              ✖
            </button>
          </div>
        </template>

        <!-- Block -->
        <button v-if="!data.isBlocked" @click="blockPlayer" class="action-btn !bg-red-900/50 hover:!bg-red-800">
          🚫 Player blocken
        </button>
        <button v-else @click="unblockPlayer" class="action-btn !bg-gray-700 hover:!bg-gray-600">
          🔓 Player freigeben
        </button>
      </div>

      <!-- Status Message -->
      <div v-if="statusMessage" class="text-center text-sm" :class="statusIsError ? 'text-red-400' : 'text-green-400'">
        {{ statusMessage }}
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { ApiService } from '@/services/ApiService';
import { useModal } from '@/composables/useModal';

const apiService = new ApiService();
const { closeModal } = useModal();

type WidgetState = 'LOADING' | 'ERROR' | 'ACTIVE';
const state = ref<WidgetState>('LOADING');
const error = ref('');
const statusMessage = ref('');
const statusIsError = ref(false);

interface TeamInviteInfo { teamId: string; title: string; }
interface TradeOfferInfo { leaseId: string; fromName: string; }
interface InteractWidgetData {
  targetEntityId: string;
  targetName: string;
  targetPortrait: string | null;
  isBlocked: boolean;
  hasTeam: boolean;
  myTeamId: string | null;
  teamInvitations: TeamInviteInfo[];
  tradeOffers: TradeOfferInfo[];
}

const data = ref<InteractWidgetData>({
  targetEntityId: '',
  targetName: '',
  targetPortrait: null,
  isBlocked: false,
  hasTeam: false,
  myTeamId: null,
  teamInvitations: [],
  tradeOffers: [],
});

const emojis = [
  { id: 'wave', icon: '👋', label: 'Wave' },
  { id: 'ok', icon: '👍', label: 'OK' },
  { id: 'smile', icon: '😊', label: 'Smile' },
  { id: 'angry', icon: '😠', label: 'Angry' },
  { id: 'sad', icon: '😢', label: 'Sad' },
  { id: 'laugh', icon: '😂', label: 'Laugh' },
  { id: 'heart', icon: '❤️', label: 'Heart' },
  { id: 'question', icon: '❓', label: 'Question' },
];

const emojiCooldown = ref(false);
const cooldownRemaining = ref(0);
let cooldownTimer: ReturnType<typeof setInterval> | null = null;

let progressId = '';

function showStatus(msg: string, isError = false) {
  statusMessage.value = msg;
  statusIsError.value = isError;
  setTimeout(() => { statusMessage.value = ''; }, 3000);
}

const cooldownRemainingComputed = computed(() => cooldownRemaining.value);

onMounted(async () => {
  const params = new URLSearchParams(window.location.search);
  progressId = params.get('progressId') || '';

  if (!progressId) {
    error.value = 'No progressId provided';
    state.value = 'ERROR';
    return;
  }

  try {
    data.value = await apiService.get<InteractWidgetData>(
      '/control/player/interact',
      { progressId }
    );
    state.value = 'ACTIVE';
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || 'Failed to load';
    state.value = 'ERROR';
  }
});

async function sendEmoji(emojiId: string) {
  if (emojiCooldown.value) return;

  try {
    await apiService.post('/control/player/interact/emoji', { emoji: emojiId }, {
      params: { progressId },
    });
    showStatus(`${emojis.find(e => e.id === emojiId)?.icon || emojiId} sent!`);

    // Start cooldown
    emojiCooldown.value = true;
    cooldownRemaining.value = 10;
    cooldownTimer = setInterval(() => {
      cooldownRemaining.value--;
      if (cooldownRemaining.value <= 0) {
        emojiCooldown.value = false;
        if (cooldownTimer) clearInterval(cooldownTimer);
        cooldownTimer = null;
      }
    }, 1000);
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to send emoji', true);
  }
}

async function inviteToTeam() {
  try {
    await apiService.post('/control/player/interact/invite-team', {}, {
      params: { progressId },
    });
    showStatus('Team invitation sent');
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to invite', true);
  }
}

async function acceptTeam(teamId: string) {
  try {
    // If already in a team, leave first
    if (data.value.hasTeam) {
      await apiService.delete('/control/player/team/leave');
    }
    await apiService.post(`/control/player/team/accept/${teamId}`, {});
    showStatus('Team joined!');
    await reload();
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to accept', true);
  }
}

async function declineTeam(teamId: string) {
  try {
    await apiService.delete(`/control/player/team/decline/${teamId}`);
    showStatus('Invitation declined');
    await reload();
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to decline', true);
  }
}

async function offerTrade() {
  try {
    await apiService.post('/control/player/interact/offer-trade', {}, {
      params: { progressId },
    });
    showStatus('Trade offer sent');
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to offer trade', true);
  }
}

async function acceptTrade(offerId: string) {
  try {
    await apiService.post('/control/player/interact/accept-trade', {}, {
      params: { progressId, offerId },
    });
    showStatus('Trade accepted');
    await reload();
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to accept trade', true);
  }
}

async function declineTrade(offerId: string) {
  try {
    await apiService.post('/control/player/interact/decline-trade', {}, {
      params: { progressId, offerId },
    });
    showStatus('Trade declined');
    await reload();
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to decline trade', true);
  }
}

async function blockPlayer() {
  try {
    await apiService.post('/control/player/interact/block', {}, {
      params: { progressId },
    });
    data.value.isBlocked = true;
    showStatus('Player blocked');
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to block', true);
  }
}

async function unblockPlayer() {
  try {
    await apiService.post('/control/player/interact/unblock', {}, {
      params: { progressId },
    });
    data.value.isBlocked = false;
    showStatus('Player unblocked');
  } catch (e: any) {
    showStatus(e.response?.data?.message || 'Failed to unblock', true);
  }
}

async function reload() {
  try {
    data.value = await apiService.get<InteractWidgetData>(
      '/control/player/interact',
      { progressId }
    );
  } catch (e) {
    // Ignore reload errors
  }
}

function closeWidget() {
  closeModal('user_close');
}
</script>

<style scoped>
/* Tailwind 4: scoped <style> blocks are processed in isolation, so @apply needs
   a @reference to know the utility classes. */
@reference "tailwindcss";

.action-btn {
  @apply px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm font-medium transition-colors text-left;
}
</style>
