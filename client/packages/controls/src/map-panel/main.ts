import { createApp } from 'vue';
import MapPanelApp from './MapPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(MapPanelApp);
  app.mount('#app');
});
