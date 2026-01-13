import { createApp } from 'vue';
import StorageApp from './StorageApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(StorageApp);
  app.mount('#app');
});
