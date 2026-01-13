import { createApp } from 'vue';
import JobControllerApp from './JobControllerApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(JobControllerApp);
  app.mount('#app');
});
