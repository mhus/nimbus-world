/**
 * Application Initialization Utility
 * Call this before mounting your Vue app to ensure runtime config is loaded
 */

import { apiService } from '@/services/ApiService';

/**
 * Initialize application with runtime configuration
 * Must be called before any API calls are made
 *
 * @example
 * import { initializeApp } from '@/utils/initApp';
 *
 * initializeApp().then(() => {
 *   createApp(App).mount('#app');
 * });
 */
export async function initializeApp(): Promise<void> {
  console.log('[initApp] Initializing application with runtime config...');

  try {
    // Load runtime configuration and initialize API service
    await apiService.initialize();

    console.log('[initApp] Application initialized successfully');
  } catch (error) {
    console.error('[initApp] Failed to initialize application', error);
    throw error;
  }
}
