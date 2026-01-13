import { createApp } from 'vue';
import EntityApp from './EntityApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(EntityApp);
  app.mount('#app');
});
