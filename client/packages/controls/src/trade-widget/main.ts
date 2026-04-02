import { createApp } from 'vue';
import TradeWidgetApp from './TradeWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(TradeWidgetApp);
  app.mount('#app');
});
