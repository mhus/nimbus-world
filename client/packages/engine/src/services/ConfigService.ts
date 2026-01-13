/**
 * ConfigService - Manages configuration loading and caching
 *
 * Loads EngineConfiguration from REST API and provides cached access.
 * Configuration includes: WorldInfo, PlayerInfo, PlayerBackpack, Settings
 */

import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type {
  EngineConfiguration,
  PlayerInfo,
  WorldInfo,
  PlayerBackpack,
  Settings,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { updateConfigFromServer } from '../config/ClientConfig';

const logger = getLogger('ConfigService');

export class ConfigService {
  private appContext: AppContext;
  private config: EngineConfiguration | null = null;
  private loading: boolean = false;

  constructor(appContext: AppContext) {
    this.appContext = appContext;
  }

  /**
   * Load complete configuration from REST API
   *
   * @param clientType - 'viewer' or 'editor'
   * @param forceReload - Force reload even if already cached
   * @param worldId - Optional world ID (defaults to config.worldId or 'main')
   */
  async loadConfig(
    clientType: 'viewer' | 'editor' = 'viewer',
    forceReload: boolean = false,
    worldId?: string
  ): Promise<EngineConfiguration> {
    // Return cached config if available and not forcing reload
    if (this.config && !forceReload) {
      logger.debug('Returning cached config');
      return this.config;
    }

    // Prevent concurrent loading
    if (this.loading) {
      logger.debug('Config already loading, waiting...');
      // Wait for current load to complete
      while (this.loading) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      if (this.config) {
        return this.config;
      }
    }

    this.loading = true;

    try {
      // Get worldId from parameter, config, or default to 'main'
      const targetWorldId = worldId || this.appContext.config?.worldId;
      const apiUrl = this.appContext.config?.apiUrl;
      const url = `${apiUrl}/player/world/config?client=${clientType}&t=${Date.now()}`;

      logger.debug('Loading configuration from REST API', { url, clientType });

      const response = await fetch(url, {
        credentials: 'include', // Include cookies for authentication
      });

      if(response.status == 401) {
        // DEAD CODE: fetch will throw on 401
        // redirect to login page if unauthorized
        this.gotoLoginScreen();
        throw new Error('Unauthorized: Redirecting to login');
      }
      if (!response.ok) {
        throw new Error(`Failed to load config: ${response.statusText}`);
      }

      const oldWorldInfo = this.appContext.worldInfo;
      const config: EngineConfiguration = await response.json();
      this.config = config;

      // Update AppContext with loaded config
      if (config.worldInfo) {
        this.appContext.worldInfo = config.worldInfo;
      }
      if (config.playerInfo) {
        this.appContext.playerInfo = config.playerInfo;
      }

      // Update client config with server-provided websocketUrl
      if (config.serverInfo && this.appContext.config) {
        updateConfigFromServer(
          this.appContext.config,
          config.serverInfo.websocketUrl
        );

        // Update NetworkService with new websocketUrl
        if (this.appContext.services.network) {
          this.appContext.services.network.updateWebSocketUrl();
        }
      }

      logger.debug('Configuration loaded successfully', {
        hasWorldInfo: !!config.worldInfo,
        hasPlayerInfo: !!config.playerInfo,
        hasBackpack: !!config.playerBackpack,
        hasSettings: !!config.settings,
        hasServerInfo: !!config.serverInfo,
      });

      // If reloading and status/season changed, recalculate modifiers
      if (forceReload && oldWorldInfo && config.worldInfo) {
        const statusChanged = oldWorldInfo.status !== config.worldInfo.status;
        const seasonStatusChanged = oldWorldInfo.seasonStatus !== config.worldInfo.seasonStatus;
        const seasonProgressChanged = oldWorldInfo.seasonProgress !== config.worldInfo.seasonProgress;

        if (statusChanged || seasonStatusChanged || seasonProgressChanged) {
          logger.debug('WorldInfo status/season changed during reload, recalculating modifiers', {
            statusChanged,
            seasonStatusChanged,
            seasonProgressChanged,
          });

          const chunkService = this.appContext.services.chunk;
          if (chunkService) {
            const result = chunkService.recalculateAndRedrawAll();
            logger.debug('Modifiers recalculated and chunks redrawn', result);
          }
        }
      }

      return config;
    } catch (error) {
        logger.error('Error loading configuration', {error});
      window.location.href = this.appContext.config?.exitUrl || '/login';
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ConfigService.loadConfig',
        { clientType, forceReload }
      );
    } finally {
      this.loading = false;
    }
  }

  /**
   * Reload configuration (force refresh from server)
   */
  async reloadConfig(
    clientType: 'viewer' | 'editor' = 'viewer',
    worldId?: string
  ): Promise<EngineConfiguration> {
    logger.debug('Reloading configuration');
    return this.loadConfig(clientType, true, worldId);
  }

  /**
   * Get cached configuration (returns null if not loaded yet)
   */
  getConfig(): EngineConfiguration | null {
    return this.config;
  }

  /**
   * Get WorldInfo from cached config
   */
  getWorldInfo(): WorldInfo | null {
    return this.config?.worldInfo || null;
  }

  /**
   * Get PlayerInfo from cached config
   */
  getPlayerInfo(): PlayerInfo | null {
    return this.config?.playerInfo || null;
  }

  /**
   * Get PlayerBackpack from cached config
   */
  getPlayerBackpack(): PlayerBackpack | null {
    return this.config?.playerBackpack || null;
  }

  /**
   * Get Settings from cached config
   */
  getSettings(): Settings | null {
    return this.config?.settings || null;
  }

  /**
   * Load specific config section
   */
  async loadWorldInfo(worldId?: string): Promise<WorldInfo> {
    const targetWorldId = worldId || this.appContext.config?.worldId;
    const apiUrl = this.appContext.config?.apiUrl;
    const url = `${apiUrl}/player/world/config/worldinfo?t=${Date.now()}`;

    const response = await fetch(url, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error(`Failed to load WorldInfo: ${response.statusText}`);
    }

    const oldWorldInfo = this.appContext.worldInfo;
    const newWorldInfo = await response.json();

    // Check if status or season changed
    const statusChanged = oldWorldInfo?.status !== newWorldInfo.status;
    const seasonStatusChanged = oldWorldInfo?.seasonStatus !== newWorldInfo.seasonStatus;
    const seasonProgressChanged = oldWorldInfo?.seasonProgress !== newWorldInfo.seasonProgress;

    if (this.config) {
      this.config.worldInfo = newWorldInfo;
    }
    this.appContext.worldInfo = newWorldInfo;

    // If status or season changed, recalculate all modifiers
    if (statusChanged || seasonStatusChanged || seasonProgressChanged) {
      logger.debug('WorldInfo status/season changed, recalculating modifiers', {
        statusChanged,
        seasonStatusChanged,
        seasonProgressChanged,
        oldStatus: oldWorldInfo?.status,
        newStatus: newWorldInfo.status,
        oldSeasonStatus: oldWorldInfo?.seasonStatus,
        newSeasonStatus: newWorldInfo.seasonStatus,
        oldSeasonProgress: oldWorldInfo?.seasonProgress,
        newSeasonProgress: newWorldInfo.seasonProgress,
      });

      const chunkService = this.appContext.services.chunk;
      if (chunkService) {
        const result = chunkService.recalculateAndRedrawAll();
        logger.debug('Modifiers recalculated and chunks redrawn', result);
      }

      // If season changed, start corresponding season script
      if (seasonStatusChanged && newWorldInfo.seasonStatus) {
        const environmentService = this.appContext.services.environment;
        if (environmentService) {
          const seasonScriptName = `season_${newWorldInfo.seasonStatus.toLowerCase()}`;
          logger.debug('Starting season script', {
            seasonStatus: newWorldInfo.seasonStatus,
            scriptName: seasonScriptName
          });
          environmentService.startEnvironmentScript(seasonScriptName);
        }
      }
    }

    return newWorldInfo;
  }

  /**
   * Load specific config section
   */
  async loadPlayerInfo(worldId?: string): Promise<PlayerInfo> {
    const targetWorldId = worldId || this.appContext.config?.worldId;
    const apiUrl = this.appContext.config?.apiUrl;
    const url = `${apiUrl}/player/world/config/playerinfo`;

    const response = await fetch(url, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error(`Failed to load PlayerInfo: ${response.statusText}`);
    }

    const playerInfo = await response.json();
    if (this.config) {
      this.config.playerInfo = playerInfo;
    }
    this.appContext.playerInfo = playerInfo;

    return playerInfo;
  }

  /**
   * Load specific config section
   */
  async loadPlayerBackpack(worldId?: string): Promise<PlayerBackpack> {
    const targetWorldId = worldId || this.appContext.config?.worldId;
    const apiUrl = this.appContext.config?.apiUrl;
    const url = `${apiUrl}/player/world/config/playerbackpack`;

    const response = await fetch(url, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error(`Failed to load PlayerBackpack: ${response.statusText}`);
    }

    const backpack = await response.json();
    if (this.config) {
      this.config.playerBackpack = backpack;
    }

    return backpack;
  }

  /**
   * Load specific config section
   */
  async loadSettings(clientType: 'viewer' | 'editor' = 'viewer', worldId?: string): Promise<Settings> {
    const targetWorldId = worldId || this.appContext.config?.worldId;
    const apiUrl = this.appContext.config?.apiUrl;
    const url = `${apiUrl}/player/world/config/settings?client=${clientType}`;

    const response = await fetch(url, {
      credentials: 'include',
    });
    if (!response.ok) {
      throw new Error(`Failed to load Settings: ${response.statusText}`);
    }

    const settings = await response.json();
    if (this.config) {
      this.config.settings = settings;
    }

    return settings;
  }

  /**
   * Clear cached configuration
   */
  clearCache(): void {
    logger.debug('Clearing config cache');
    this.config = null;
  }

  public gotoLoginScreen() {
    window.location.href = this.appContext.config?.exitUrl || '/login';
  }
}
