import { createApp } from 'vue';
import RuleApp from './RuleApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(RuleApp);
  app.mount('#app');
});
