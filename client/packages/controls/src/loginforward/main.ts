/**
 * Login Forward
 *
 * Fetches the configured loginUrl from the server and redirects there.
 * This allows a central login entry point that works with any configured
 * login provider (dev-login, SSO, etc.).
 *
 * Fallback: dev-login.html if the server is unreachable.
 */

import { apiService } from '@/services/ApiService';
import { initializeApp } from '@/utils/initApp';

const FALLBACK_LOGIN_URL = '/controls/dev-login.html';

const app = document.querySelector<HTMLDivElement>('#app')!;

app.innerHTML = `
  <div style="display:flex;align-items:center;justify-content:center;height:100vh;font-family:system-ui,sans-serif;color:#888;">
    <p>Redirecting to login...</p>
  </div>
`;

async function forward() {
  try {
    await initializeApp();

    const response = await apiService.post<{
      authenticated: boolean;
      loginUrl?: string;
    }>('/control/aaa/status', {});

    let loginUrl = response.loginUrl || FALLBACK_LOGIN_URL;

    // Prevent self-redirect loop
    if (loginUrl.includes('login-forward.html')) {
      console.warn('[LoginForward] loginUrl points to self, using fallback');
      loginUrl = FALLBACK_LOGIN_URL;
    }

    window.location.href = loginUrl;
  } catch (error) {
    console.error('[LoginForward] Failed to get login URL, using fallback', error);
    window.location.href = FALLBACK_LOGIN_URL;
  }
}

forward();
