/// <reference types="vite/client" />

/**
 * Global build-time constants injected by Vite
 * These are replaced at compile time with their actual values
 */

declare const __EDITOR__: boolean;
declare const __VIEWER__: boolean;
declare const __DEBUG_COMMANDS__: boolean;
declare const __BUILD_MODE__: 'viewer' | 'editor' | 'development';
declare const __PAKO_MODE__: 'safari' | 'all';
