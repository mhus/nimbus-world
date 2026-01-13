import { createApp } from 'vue';
import MaterialApp from './MaterialApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(MaterialApp);
  app.mount('#app');
});
