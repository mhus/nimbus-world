import { createApp } from 'vue';
import ProgressApp from './ProgressApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ProgressApp);
  app.mount('#app');
});
