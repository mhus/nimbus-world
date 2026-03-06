import { createApp } from 'vue';
import DialogWidgetApp from './DialogWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(DialogWidgetApp);
  app.mount('#app');
});
