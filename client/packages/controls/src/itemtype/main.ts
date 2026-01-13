/**
 * ItemType Editor - Main entry point
 */

import { createApp } from 'vue';
import ItemTypeApp from './ItemTypeApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ItemTypeApp);
  app.mount('#app');
});
