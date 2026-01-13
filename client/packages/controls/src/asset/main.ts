import { createApp } from 'vue';
import AssetApp from './AssetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(AssetApp);
  app.mount('#app');
});
