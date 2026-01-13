/**
 * Item Editor - Main entry point
 */

import { createApp } from 'vue';
import ItemApp from './ItemApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ItemApp);
  app.mount('#app');
});
