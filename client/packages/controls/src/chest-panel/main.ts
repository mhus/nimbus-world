import { createApp } from 'vue';
import ChestPanelApp from './ChestPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ChestPanelApp);
  app.mount('#app');
});
