import { createApp } from 'vue';
import LibraryPanelApp from './LibraryPanelApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(LibraryPanelApp);
  app.mount('#app');
});
