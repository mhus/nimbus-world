import { createApp } from 'vue';
import UserApp from './UserApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(UserApp);
  app.mount('#app');
});
