import { createApp } from 'vue';
import AgentChatWidgetApp from './AgentChatWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

// Initialize app with runtime config before mounting
initializeApp().then(() => {
  const app = createApp(AgentChatWidgetApp);
  app.mount('#app');
});
