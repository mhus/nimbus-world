import { createApp } from 'vue';
import DocumentWidgetApp from './DocumentWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(DocumentWidgetApp);
  app.mount('#app');
});
