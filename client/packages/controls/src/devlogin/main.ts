import { createApp } from 'vue';
import DevLoginApp from './DevLoginApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(DevLoginApp);
  app.mount('#app');
});
