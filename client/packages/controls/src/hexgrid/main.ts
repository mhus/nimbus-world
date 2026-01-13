import { createApp } from 'vue';
import HexEditorApp from './HexEditorApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(HexEditorApp);
  app.mount('#app');
});
