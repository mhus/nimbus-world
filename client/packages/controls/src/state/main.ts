import { createApp } from 'vue';
import StateApp from './StateApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(StateApp);
  app.mount('#app');
});
