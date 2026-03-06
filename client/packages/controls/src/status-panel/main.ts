import { createApp } from 'vue';
import StatusPanelApp from './StatusPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(StatusPanelApp);
  app.mount('#app');
});
