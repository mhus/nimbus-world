import { createApp } from 'vue';
import PanelApp from './PanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(PanelApp);
  app.mount('#app');
});
