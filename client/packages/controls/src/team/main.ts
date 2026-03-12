import { createApp } from 'vue';
import TeamApp from './TeamApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(TeamApp);
  app.mount('#app');
});
