/**
 * ItemService - Loads and caches items from server
 *
 * Provides access to item data including textures for UI display.
 * Items are loaded from the server REST API and cached locally.
 *
 * Also handles item activation (pose, wait, duration) when shortcuts are triggered.
 */

import {Item, FullItem} from '@nimbus/shared';
import { getLogger, ExceptionHandler, ENTITY_POSES } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { StackName } from './ModifierService';

const logger = getLogger('ItemService');

/**
 * ItemService manages item data from server
 */
export class ItemService {
  /** Cache of loaded items: itemId -> Item */
  private itemCache: Map<string, Item> = new Map();

  /** Pending requests to avoid duplicate fetches */
  private pendingRequests: Map<string, Promise<Item | null>> = new Map();

  /** Active pose timers (for duration cleanup) */
  private poseTimers: Map<string, number> = new Map();

  constructor(private appContext: AppContext) {
    logger.debug('ItemService initialized');
  }

  /**
   * Initialize event subscriptions
   * Called after PlayerService is available
   */
  initializeEventSubscriptions(): void {
    const playerService = this.appContext.services.player;
    if (!playerService) {
      logger.warn('PlayerService not available for event subscriptions');
      return;
    }

    // Subscribe to shortcut activation events
    playerService.on('shortcut:activated', (data: {
      shortcutKey: string;
      itemId?: string;
      target?: any;
      targetPosition?: { x: number; y: number; z: number };
    }) => {
      this.handleShortcutActivation(data.shortcutKey, data.itemId, data.target, data.targetPosition);
    });

    logger.debug('ItemService event subscriptions initialized');
  }

  /**
   * Get item by ID from cache or server
   *
   * @param itemId Item ID
   * @returns Item or null if not found
   */
  async getItem(itemId: string): Promise<FullItem | null> {
    // Check cache first
    if (this.itemCache.has(itemId)) {
      return this.itemCache.get(itemId)!;
    }

    // Check if already fetching
    const pending = this.pendingRequests.get(itemId);
    if (pending) {
      return pending;
    }

    // Fetch from server
    const promise = this.fetchItemFromServer(itemId);
    this.pendingRequests.set(itemId, promise);

    try {
      const item = await promise;
      if (item) {
        this.itemCache.set(itemId, item);
        return item;
      }
      return item;
    } finally {
      this.pendingRequests.delete(itemId);
    }
  }

  /**
   * Fetch item from server REST API
   *
   * @param itemId Item ID
   * @returns Item or null if not found
   */
  private async fetchItemFromServer(itemId: string): Promise<Item | null> {
    try {
      const worldId = this.appContext.worldInfo?.worldId;
      if (!worldId) {
        logger.warn('Cannot fetch item: no worldId', { itemId });
        return null;
      }

      const networkService = this.appContext.services.network;
      if (!networkService) {
        logger.warn('NetworkService not available', { itemId });
        return null;
      }

      const url = networkService.getItemUrl(itemId);

      logger.debug('Fetching item from server', { itemId, url });

      const response = await fetch(url, {
        credentials: 'include',
      });

      if (!response.ok) {
        if (response.status === 404) {
          logger.debug('Item not found', { itemId });
          return null;
        }
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const serverItem = await response.json();

      // Extract Item from ServerItem
      // ServerItem has structure: { item: Item, itemBlockRef?: ItemBlockRef }
      const item: Item = (serverItem as any).item || serverItem;

      logger.debug('Item loaded from server', { itemId });

      return item;
    } catch (error) {
      ExceptionHandler.handle(error, 'ItemService.fetchItemFromServer', { itemId });
      return null;
    }
  }

  /**
   * Get texture URL for item
   *
   * Returns the asset URL for the item's texture that can be used in <img> tags.
   *
   * @param item Item
   * @returns Texture URL or null if no texture
   */
  async getTextureUrl(item: Item): Promise<string | null> {
    try {
      if (!item.texture) {
        return null;
      }

      // Build asset URL via NetworkService
      const networkService = this.appContext.services.network;
      if (!networkService) {
        logger.warn('NetworkService not available for texture URL');
        return null;
      }

      return networkService.getAssetUrl(item.texture);
    } catch (error) {
      ExceptionHandler.handle(error, 'ItemService.getTextureUrl', {
        itemId: item.name,
      });
      return null;
    }
  }

  /**
   * Preload multiple items
   *
   * Useful for preloading items that will be displayed in UI.
   *
   * @param itemIds Array of item IDs to preload
   */
  async preloadItems(itemIds: string[]): Promise<void> {
    const promises = itemIds.map((id) => this.getItem(id));
    await Promise.all(promises);
    logger.debug('Items preloaded', { count: itemIds.length });
  }

  /**
   * Clear item cache
   */
  clearCache(): void {
    this.itemCache.clear();
    logger.debug('Item cache cleared');
  }

  /**
   * Get cache size
   *
   * @returns Number of cached items
   */
  getCacheSize(): number {
    return this.itemCache.size;
  }

  /**
   * Handle shortcut activation
   *
   * Loads item data and activates pose with wait/duration timing.
   *
   * @param shortcutKey Shortcut key that was activated
   * @param itemId Item ID from shortcut
   * @param target Target object (Block or Entity) from ShortcutService
   * @param targetPosition Target position from ShortcutService
   */
  private async handleShortcutActivation(
    shortcutKey: string,
    itemId?: string,
    target?: any,
    targetPosition?: { x: number; y: number; z: number }
  ): Promise<void> {
    try {
      if (!itemId) {
        logger.debug('No itemId for shortcut activation', { shortcutKey });
        return;
      }

      // Load Item
      const item = await this.getItem(itemId);
      if (!item) {
        logger.warn('Item not found for shortcut', { shortcutKey, itemId });
        return;
      }

      const { pose, onUseEffect } = item;

      // Execute scrawl script if defined
      let executorId: string | undefined;
      if (onUseEffect) {
        const scrawlService = this.appContext.services.scrawl;
        if (scrawlService) {
          try {
            logger.debug('Executing onUseEffect script', {
              itemId,
              shortcutKey,
              hasTarget: !!target,
              targetPosition,
            });

            // Get source (player)
            const playerService = this.appContext.services.player;
            const source = playerService?.getPlayerEntity();

            // Prepare context - all values go into vars for consistency
            // Target comes from ShortcutService (already resolved)
            const scriptContext: any = {
              vars: {
                itemId: item.name,
                shortcutKey,
                item,
                itemName: item.title,
                itemTexture: item.texture,
                source,              // $source (Player)
                target,              // $target (from ShortcutService)
                targets: target ? [target] : [], // $targets
              },
            };

            executorId = await scrawlService.executeAction(onUseEffect, scriptContext);
          } catch (error) {
            ExceptionHandler.handle(error, 'ItemService.handleShortcutActivation.onUseEffect', {
              shortcutKey,
              itemId,
            });
            logger.warn('Failed to execute onUseEffect script', {
              itemId,
              error: (error as Error).message,
            });
          }
        } else {
          logger.debug('ScrawlService not available, skipping onUseEffect', { itemId });
        }
      }

      // Register in ShortcutService if executorId exists
      if (executorId) {
        const shortcutService = this.appContext.services.shortcut;
        const playerService = this.appContext.services.player;

        if (shortcutService && playerService) {
          const shortcutNr = this.extractShortcutNumber(shortcutKey);

          // Get exclusive flag and targeting mode directly from item
          const isExclusive = item.exclusive ?? false;
          const targetingMode = item.actionTargeting ?? 'ALL';

          shortcutService.startShortcut(shortcutNr, shortcutKey, executorId, isExclusive, itemId, targetingMode);

          // Emit started event
          playerService.emitShortcutStarted({
            shortcutNr,
            shortcutKey,
            executorId,
            itemId,
            exclusive: isExclusive,
          });

          logger.debug('Shortcut registered in ShortcutService', {
            shortcutNr,
            executorId,
            exclusive: isExclusive,
            targetingMode,
          });
        }
      }

      // Handle pose animation if defined
      if (pose) {
        // Get pose ID from ENTITY_POSES
        const poseId = (ENTITY_POSES as any)[pose.toUpperCase()];
        if (poseId === undefined) {
          logger.warn('Unknown pose', { pose, itemId });
          return;
        }

        // Activate pose with priority 10 (overrides idle=100, but not calculated movement poses)
        // Default duration: 1000ms
        this.activatePose(poseId, 1000, itemId);

        logger.debug('Shortcut pose activated', { shortcutKey, itemId, pose, poseId });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'ItemService.handleShortcutActivation', { shortcutKey, itemId });
    }
  }

  /**
   * Activate a pose for a specific duration
   *
   * @param poseId Pose ID from ENTITY_POSES
   * @param durationMs Duration in milliseconds
   * @param modifierId Unique ID for this modifier
   */
  private activatePose(poseId: number, durationMs: number, modifierId: string): void {
    const poseStack = this.appContext.services.modifier?.getModifierStack<number>(StackName.PLAYER_POSE);
    if (!poseStack) {
      logger.warn('PLAYER_POSE stack not available');
      return;
    }

    // Clear existing timer for this modifier if any
    const existingTimer = this.poseTimers.get(modifierId);
    if (existingTimer) {
      clearTimeout(existingTimer);
      this.poseTimers.delete(modifierId);
    }

    // Add modifier with priority 10 (higher than default, overrides idle)
    const modifier = poseStack.addModifier(poseId, 10);

    // Set timer to remove modifier after duration
    const timer = window.setTimeout(() => {
      modifier.close();
      this.poseTimers.delete(modifierId);
      logger.debug('Pose duration expired', { modifierId, durationMs });
    }, durationMs);

    this.poseTimers.set(modifierId, timer);
  }

  /**
   * Extracts the shortcut number from a shortcut key.
   *
   * @param shortcutKey Shortcut key (e.g., 'key1', 'key10', 'click2', 'slot5')
   * @returns Shortcut number
   */
  private extractShortcutNumber(shortcutKey: string): number {
    if (shortcutKey.startsWith('key')) {
      // key0-key9 -> 0-9, key10 -> 10
      return parseInt(shortcutKey.replace('key', ''), 10);
    } else if (shortcutKey.startsWith('click')) {
      // click0-9 -> 0-9
      return parseInt(shortcutKey.replace('click', ''), 10);
    } else if (shortcutKey.startsWith('slot')) {
      // slot0-N -> 0-N
      return parseInt(shortcutKey.replace('slot', ''), 10);
    }

    // Fallback: try to extract any number from the key
    const match = shortcutKey.match(/\d+/);
    return match ? parseInt(match[0], 10) : 0;
  }

  /**
   * Dispose service
   */
  dispose(): void {
    // Clear all pose timers
    this.poseTimers.forEach((timer) => clearTimeout(timer));
    this.poseTimers.clear();

    this.itemCache.clear();
    this.pendingRequests.clear();
    logger.debug('ItemService disposed');
  }
}
