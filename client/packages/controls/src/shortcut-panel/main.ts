import { createApp } from 'vue';
import ShortcutPanelApp from './ShortcutPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(ShortcutPanelApp);
  app.mount('#app');
});
