import { createApp } from 'vue';
import WearingPanelApp from './WearingPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(WearingPanelApp);
  app.mount('#app');
});
