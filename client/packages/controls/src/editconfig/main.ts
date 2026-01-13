import { createApp } from 'vue';
import EditConfigApp from './EditConfigApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(EditConfigApp);
  app.mount('#app');
});
