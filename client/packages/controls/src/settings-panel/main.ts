import { createApp } from 'vue';
import SettingsPanelApp from './SettingsPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(SettingsPanelApp);
  app.mount('#app');
});
