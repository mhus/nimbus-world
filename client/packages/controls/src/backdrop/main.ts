import { createApp } from 'vue';
import BackdropApp from './BackdropApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(BackdropApp);
  app.mount('#app');
});
