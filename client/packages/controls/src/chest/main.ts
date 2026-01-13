import { createApp } from 'vue';
import ChestApp from './ChestApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ChestApp);
  app.mount('#app');
});
