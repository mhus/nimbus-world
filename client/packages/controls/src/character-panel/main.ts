import { createApp } from 'vue';
import CharacterPanelApp from './CharacterPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(CharacterPanelApp);
  app.mount('#app');
});
