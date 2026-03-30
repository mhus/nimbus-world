import { createApp } from 'vue';
import FlagApp from './FlagApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(FlagApp);
  app.mount('#app');
});
