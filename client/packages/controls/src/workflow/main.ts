import { createApp } from 'vue';
import WorkflowApp from './WorkflowApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(WorkflowApp);
  app.mount('#app');
});
