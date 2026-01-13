import { createApp } from 'vue';
import CharacterApp from './CharacterApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(CharacterApp);
  app.mount('#app');
});
