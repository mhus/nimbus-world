import { createApp } from 'vue';
import UniverseApp from './UniverseApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(UniverseApp);
  app.mount('#app');
});
