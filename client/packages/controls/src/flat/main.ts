import { createApp } from 'vue';
import FlatApp from './FlatApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(FlatApp);
  app.mount('#app');
});
