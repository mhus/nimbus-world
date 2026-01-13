/**
 * Runtime Configuration Service
 * Loads configuration from ./config.json at runtime (not build time)
 * This allows Docker containers to override config without rebuilding
 * Uses relative path to support different base paths (/editor/, /viewer/, etc.)
 */

import { getLogger } from '@nimbus/shared';

const logger = getLogger('RuntimeConfig');

export interface RuntimeConfig {
  apiUrl: string;
  exitUrl: string;
}

class RuntimeConfigService {
  private config: RuntimeConfig | null = null;
  private loadPromise: Promise<RuntimeConfig> | null = null;

  /**
   * Load configuration from ./config.json (relative to base path)
   * Returns cached config if already loaded
   */
  async loadConfig(): Promise<RuntimeConfig> {
    // Return cached config if available
    if (this.config) {
      return this.config;
    }

    // Return existing promise if already loading
    if (this.loadPromise) {
      return this.loadPromise;
    }

    // Start loading config
    this.loadPromise = this.fetchConfig();
    this.config = await this.loadPromise;
    return this.config;
  }

  /**
   * Fetch config.json from server
   */
  private async fetchConfig(): Promise<RuntimeConfig> {
    try {
      // Use relative path to support different base paths (/editor/, /viewer/, etc.)
      const response = await fetch('./config.json');

      if (!response.ok) {
        throw new Error(`Failed to load config: ${response.status} ${response.statusText}`);
      }

      const config = await response.json();
      logger.info('Loaded runtime config from ./config.json', config);
      return config;
    } catch (error) {
      logger.warn('Failed to load ./config.json, using fallback from .env', error);

      // Fallback to .env values if config.json fails to load
      return {
        apiUrl: import.meta.env.VITE_SERVER_API_URL || 'http://localhost:9042',
        exitUrl: import.meta.env.VITE_EXIT_URL || '/login',
      };
    }
  }

  /**
   * Get current config (must call loadConfig first)
   */
  getConfig(): RuntimeConfig {
    if (!this.config) {
      throw new Error('Config not loaded. Call loadConfig() first.');
    }
    return this.config;
  }
}

export const runtimeConfigService = new RuntimeConfigService();
