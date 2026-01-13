import { createApp } from 'vue';
import AnythingApp from './AnythingApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(AnythingApp);
  app.mount('#app');
});
