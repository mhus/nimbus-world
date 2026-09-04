/// <reference types="vite/client" />

/* eslint-disable @typescript-eslint/no-empty-object-type -- canonical Vue module shim, see https://vuejs.org/guide/typescript/options-api.html */
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
/* eslint-enable @typescript-eslint/no-empty-object-type */

interface ImportMetaEnv {
  readonly VITE_CONTROL_API_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
