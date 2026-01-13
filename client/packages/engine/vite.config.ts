import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig(({ mode }) => {
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
          main: resolve(__dirname, 'index.html'),
        },
        output: {
          manualChunks: {
            'babylon-core': ['@babylonjs/core'],
            'babylon-loaders': ['@babylonjs/loaders'],
          },
        },
      },
    },
    define: {
      // Global constants for conditional compilation
      __EDITOR__: JSON.stringify(isEditor),
      __VIEWER__: JSON.stringify(isViewer),
      __BUILD_MODE__: JSON.stringify(mode),
    },
    resolve: {
      alias: {
        '@': resolve(__dirname, './src'),
        '@nimbus/shared': resolve(__dirname, '../shared/src'),
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
      // Increase esbuild memory and threads
      esbuildOptions: {
        // More memory for large dependencies
        logLevel: 'info',
      },
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
