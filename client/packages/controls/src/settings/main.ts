import { createApp } from 'vue';
import SettingsApp from './SettingsApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(SettingsApp);
  app.mount('#app');
});
