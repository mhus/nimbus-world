import { createApp } from 'vue';
import RegionApp from './RegionApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(RegionApp);
  app.mount('#app');
});
