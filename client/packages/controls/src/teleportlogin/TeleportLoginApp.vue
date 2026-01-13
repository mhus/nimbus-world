<template>
  <div class="min-h-screen flex flex-col items-center justify-center bg-base-200">
    <div class="card bg-base-100 shadow-xl w-full max-w-md">
      <div class="card-body items-center text-center">
        <!-- Loading State -->
        <div v-if="status === 'loading'" class="space-y-4">
          <div class="loading loading-spinner loading-lg text-primary"></div>
          <h2 class="card-title">Teleporting...</h2>
          <p class="text-base-content/70">{{ loadingMessage }}</p>
        </div>

        <!-- Error State -->
        <div v-else-if="status === 'error'" class="space-y-4">
          <svg class="w-16 h-16 text-error" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <h2 class="card-title text-error">Teleportation Failed</h2>
          <p class="text-base-content/70">{{ errorMessage }}</p>
          <div class="card-actions">
            <button class="btn btn-primary" @click="retry">Retry</button>
            <a href="/" class="btn btn-ghost">Go Home</a>
          </div>
        </div>

        <!-- Success State (Redirecting) -->
        <div v-else-if="status === 'redirecting'" class="space-y-4">
          <svg class="w-16 h-16 text-success" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <h2 class="card-title text-success">Teleportation Successful!</h2>
          <p class="text-base-content/70">Redirecting to new world...</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { apiService } from '@/services/ApiService';

// State
const status = ref<'loading' | 'error' | 'redirecting'>('loading');
const loadingMessage = ref('Preparing teleportation...');
const errorMessage = ref('');

/**
 * Response from /control/public/teleport-login
 */
interface TeleportLoginResponse {
  accessToken: string;
  accessUrls: string[];
  jumpUrl: string;
  sessionId: string;
  playerId: string;
}

/**
 * Perform teleportation login
 */
async function performTeleportLogin() {
  status.value = 'loading';
  loadingMessage.value = 'Checking teleportation target...';

  try {
    // Call teleport-login endpoint
    const apiUrl = apiService.getBaseUrl();
    const response = await fetch(`${apiUrl}/control/public/teleport-login`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }));
      throw new Error(errorData.error || `HTTP ${response.status}: ${response.statusText}`);
    }

    const data: TeleportLoginResponse = await response.json();

    // Authorize with cookie URLs
    loadingMessage.value = 'Authorizing access...';
    if (data.accessUrls && data.accessUrls.length > 0) {
      await authorize(data.accessUrls, data.accessToken);
    }

    // Redirect to jump URL
    status.value = 'redirecting';
    loadingMessage.value = 'Entering new world...';

    // Add small delay for UX (let user see success message)
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Redirect
    window.location.href = data.jumpUrl;

  } catch (err) {
    status.value = 'error';
    errorMessage.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('[TeleportLogin] Teleportation failed:', err);
  }
}

/**
 * Authorize with access URLs.
 * Calls each URL with the access token to set session cookies.
 */
async function authorize(urls: string[], token: string) {
  const promises = urls.map(async (url) => {
    const authorizeUrl = `${url}?token=${encodeURIComponent(token)}`;
    try {
      const response = await fetch(authorizeUrl, {
        method: 'GET',
        credentials: 'include',
      });

      if (!response.ok) {
        console.warn(`Authorization failed for ${url}:`, response.statusText);
      }
    } catch (err) {
      console.warn(`Authorization request failed for ${url}:`, err);
    }
  });

  await Promise.all(promises);
}

/**
 * Retry teleportation
 */
function retry() {
  performTeleportLogin();
}

/**
 * On mounted, start teleportation
 */
onMounted(() => {
  performTeleportLogin();
});
</script>

<style scoped>
/* Add any component-specific styles here if needed */
</style>
