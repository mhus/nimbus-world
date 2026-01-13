import { createApp } from 'vue';
import HomeApp from './HomeApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(HomeApp);
  app.mount('#app');
});
