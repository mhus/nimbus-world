import { createApp } from 'vue';
import CraftingWidgetApp from './CraftingWidgetApp.vue';
import '../style.css';
import { initializeApp } from '@/utils/initApp';

initializeApp().then(() => {
  const app = createApp(CraftingWidgetApp);
  app.mount('#app');
});
