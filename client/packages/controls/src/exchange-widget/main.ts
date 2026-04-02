import { createApp } from 'vue';
import ExchangeWidgetApp from './ExchangeWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(ExchangeWidgetApp);
  app.mount('#app');
});
