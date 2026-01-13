import { createApp } from 'vue';
import EditorShortcutPanelApp from './EditorShortcutPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(EditorShortcutPanelApp);
  app.mount('#app');
});
