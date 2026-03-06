import { createApp } from 'vue';
import SkillPanelApp from './SkillPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(SkillPanelApp);
  app.mount('#app');
});
