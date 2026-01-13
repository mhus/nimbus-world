import { createApp } from 'vue';
import LayerApp from './LayerApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(LayerApp);
  app.mount('#app');
});
