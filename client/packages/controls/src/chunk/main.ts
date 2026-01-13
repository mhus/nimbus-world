import { createApp } from 'vue';
import ChunkApp from './ChunkApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ChunkApp);
  app.mount('#app');
});
