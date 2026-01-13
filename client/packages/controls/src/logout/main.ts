/**
 * Nimbus Logout
 *
 * Performs logout by:
 * 1. Fetching logout URLs from status endpoint
 * 2. Calling DELETE on all logout URLs to clear cookies
 * 3. Redirecting to login page
 */

import { logoutService } from './services/LogoutService';
import { getLogger } from '@nimbus/shared';
import './style.css';

const logger = getLogger('LogoutMain');

// App container
const app = document.querySelector<HTMLDivElement>('#app')!;

/**
 * Show loading state
 */
function showLoading() {
  app.innerHTML = `
    <div class="container">
      <div class="card">
        <h1>Logging out...</h1>
        <div class="spinner"></div>
        <p>Please wait while we log you out.</p>
      </div>
    </div>
  `;
}

/**
 * Show error state
 */
function showError(message: string) {
  app.innerHTML = `
    <div class="container">
      <div class="card error">
        <h1>Logout Failed</h1>
        <p class="error-message">${message}</p>
        <button id="retry-btn" class="btn btn-primary">Retry</button>
        <button id="login-btn" class="btn btn-secondary">Go to Login</button>
      </div>
    </div>
  `;

  // Retry button
  document.getElementById('retry-btn')?.addEventListener('click', () => {
    performLogout();
  });

  // Login button
  document.getElementById('login-btn')?.addEventListener('click', () => {
    redirectToLogin();
  });
}

/**
 * Show success state (briefly before redirect)
 */
function showSuccess() {
  app.innerHTML = `
    <div class="container">
      <div class="card success">
        <h1>Logged Out Successfully</h1>
        <div class="checkmark">✓</div>
        <p>Redirecting to login page...</p>
      </div>
    </div>
  `;
}

/**
 * Redirect to login page
 */
function redirectToLogin(loginUrl?: string) {
  const url = loginUrl || '/controls/dev-login.html';
  logger.info('Redirecting to login', { url });
  window.location.href = url;
}

/**
 * Perform logout process
 */
async function performLogout() {
  showLoading();

  try {
    logger.info('Starting logout process');

    // Get logout URLs and login URL from status endpoint
    const { accessUrls, loginUrl } = await logoutService.getLogoutUrls();

    logger.info('Got logout URLs', { accessUrls, loginUrl });

    // Call DELETE on all logout URLs to clear cookies
    await logoutService.logout(accessUrls);

    logger.info('Logout successful');

    // Show success message
    showSuccess();

    // Redirect to login after short delay
    setTimeout(() => {
      redirectToLogin(loginUrl);
    }, 1500);

  } catch (error) {
    logger.error('Logout failed', {}, error instanceof Error ? error : undefined);

    const errorMessage = error instanceof Error
      ? error.message
      : 'An unexpected error occurred during logout';

    showError(errorMessage);
  }
}

// Start logout process on page load
performLogout();
