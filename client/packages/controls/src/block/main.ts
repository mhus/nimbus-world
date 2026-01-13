import { createApp } from 'vue';
import BlockApp from './BlockApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(BlockApp);
  app.mount('#app');
});
