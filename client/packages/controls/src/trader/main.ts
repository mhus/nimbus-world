import { createApp } from 'vue';
import TraderEditorApp from './TraderEditorApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(TraderEditorApp);
  app.mount('#app');
});
