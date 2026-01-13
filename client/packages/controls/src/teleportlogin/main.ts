import { createApp } from 'vue';
import TeleportLoginApp from './TeleportLoginApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(TeleportLoginApp);
  app.mount('#app');
});
