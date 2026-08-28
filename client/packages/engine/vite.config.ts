import { defineConfig, loadEnv } from 'vite';
import { resolve } from 'path';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, import.meta.dirname, '');
  const isEditor = mode === 'editor';
  const isViewer = mode === 'viewer' || mode === 'development';

  return {
    root: '.',
    base: isEditor ? '/editor/' : '/viewer/',
    build: {
      outDir: isEditor ? 'dist/editor' : 'dist/viewer',
      emptyOutDir: true,
      chunkSizeWarningLimit: 2000, // Increase to 2MB for BabylonJS
      sourcemap: mode === 'development',
      minify: mode !== 'development',
      rollupOptions: {
        input: {
          main: resolve(import.meta.dirname, 'index.html'),
        },
        output: {
          // Rolldown replaced manualChunks with codeSplitting groups; loaders is
          // listed first so its modules do not get pulled into babylon-core.
          codeSplitting: {
            groups: [
              { name: 'babylon-loaders', test: /[\\/]@babylonjs[\\/]loaders[\\/]/ },
              { name: 'babylon-core', test: /[\\/]@babylonjs[\\/]core[\\/]/ },
            ],
          },
        },
      },
    },
    define: {
      // Global constants for conditional compilation
      __EDITOR__: JSON.stringify(isEditor),
      __VIEWER__: JSON.stringify(isViewer),
      __BUILD_MODE__: JSON.stringify(mode),
      __DEBUG_COMMANDS__: JSON.stringify(env.VITE_DEBUG_COMMANDS === 'true'),
      __PAKO_MODE__: JSON.stringify(env.VITE_PAKO_MODE || 'safari'),
      __INPUT_CONTROLLER__: JSON.stringify(env.VITE_INPUT_CONTROLLER || 'auto'),
    },
    resolve: {
      alias: {
        '@': resolve(import.meta.dirname, './src'),
        '@nimbus/shared': resolve(import.meta.dirname, '../shared/src'),
      },
    },
    server: {
      port: 3001,
      open: true,
      hmr: {
        overlay: true,
        // Increase timeout for large updates
        timeout: 30000,
      },
      watch: {
        // Use polling for better reliability with large projects
        usePolling: false,
        // Increase file descriptor limit awareness
        ignored: ['**/node_modules/**', '**/dist/**'],
      },
    },
    optimizeDeps: {
      // Force re-optimization on startup
      force: false,
      // Include dependencies that need pre-bundling
      // Also include @nimbus/shared to prevent issues with dynamic/static imports
      include: [
        '@babylonjs/core',
        '@babylonjs/loaders',
        '@nimbus/shared',
      ],
    },
    // Performance optimizations for large projects
    cacheDir: 'node_modules/.vite',
  };
});
