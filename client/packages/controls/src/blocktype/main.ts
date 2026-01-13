import { createApp } from 'vue';
import BlockTypeApp from './BlockTypeApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(BlockTypeApp);
  app.mount('#app');
});
