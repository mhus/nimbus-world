/**
 * Runtime Configuration Service
 * Loads configuration from /config.json at runtime (not build time)
 * This allows Docker containers to override config without rebuilding
 */

export interface RuntimeConfig {
  apiUrl: string;
}

class ConfigService {
  private config: RuntimeConfig | null = null;
  private loadPromise: Promise<RuntimeConfig> | null = null;

  /**
   * Load configuration from /config.json
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
      const configPath = `${import.meta.env.BASE_URL}config.json`;
      console.log('[ConfigService] Attempting to load config from:', configPath);
      console.log('[ConfigService] BASE_URL:', import.meta.env.BASE_URL);

      const response = await fetch(configPath);

      if (!response.ok) {
        console.error('[ConfigService] Config fetch failed:', response.status, response.statusText);
        throw new Error(`Failed to load config: ${response.status} ${response.statusText}`);
      }

      const config = await response.json();
      console.log('[ConfigService] Successfully loaded runtime config:', config);
      return config;
    } catch (error) {
      console.error('[ConfigService] Failed to load config, using fallback', error);
      console.error('[ConfigService] Fallback apiUrl:', import.meta.env.VITE_CONTROL_API_URL || 'http://localhost:9043');

      // Fallback to .env value if config.json fails to load
      return {
        apiUrl: import.meta.env.VITE_CONTROL_API_URL || 'http://localhost:9043',
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

  /**
   * Get API URL (convenience method)
   */
  getApiUrl(): string {
    return this.getConfig().apiUrl;
  }
}

export const configService = new ConfigService();
