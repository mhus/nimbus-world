import { createApp } from 'vue';
import ChestWidgetApp from './ChestWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(ChestWidgetApp);
  app.mount('#app');
});
