import { createApp } from 'vue';
import EntityModelApp from './EntityModelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(EntityModelApp);
  app.mount('#app');
});
