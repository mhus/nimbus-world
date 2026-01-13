import { createApp } from 'vue';
import McAssetApp from './McAssetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(McAssetApp);
  app.mount('#app');
});
