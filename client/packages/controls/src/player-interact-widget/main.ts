import { createApp } from 'vue';
import PlayerInteractWidgetApp from './PlayerInteractWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(PlayerInteractWidgetApp);
  app.mount('#app');
});
