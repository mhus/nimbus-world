import { createApp } from 'vue';
import TeamPanelApp from './TeamPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(TeamPanelApp);
  app.mount('#app');
});
