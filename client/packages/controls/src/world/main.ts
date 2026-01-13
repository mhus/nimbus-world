import { createApp } from 'vue';
import WorldApp from './WorldApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(WorldApp);
  app.mount('#app');
});
