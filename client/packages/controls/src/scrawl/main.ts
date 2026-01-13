/**
 * Scrawl Script Editor - Main entry point
 */

import { createApp } from 'vue';
import ScrawlApp from './ScrawlApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ScrawlApp);
  app.mount('#app');
});
