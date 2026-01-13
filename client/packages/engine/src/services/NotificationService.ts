/**
 * NotificationService - Manages notification display across 4 areas
 *
 * Four areas:
 * - System (bottom-left): Info, errors, command results (auto-hide 5s, max 5)
 * - Chat (bottom-right): Chat messages (no auto-hide, max 5, clearable)
 * - Overlay (center): Big messages (auto-hide 2s, max 1)
 * - Quest (top-right): Quest info (no auto-hide, max 2)
 */

import { getLogger, ExceptionHandler, type TeamMember } from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import { StackName, type Modifier } from './ModifierService';
import type {
  Notification,
  NotificationType,
  NotificationAreaConfig,
} from '../types/Notification';
import {
  NotificationArea,
  NOTIFICATION_AREA_CONFIGS,
  NOTIFICATION_TYPE_TO_AREA,
  NOTIFICATION_TYPE_STYLES,
} from '../types/Notification';

const logger = getLogger('NotificationService');

/**
 * NotificationService - Manages notifications across different areas
 */
export class NotificationService {
  private appContext: AppContext;
  private notifications: Map<NotificationArea, Notification[]> = new Map();
  private nextNotificationId: number = 1;
  private hideTimers: Map<string, number> = new Map();
  private visible: boolean = true;

  // Container elements
  private containers: Map<NotificationArea, HTMLElement> = new Map();

  // Team table
  private teamTableContainer: HTMLElement | null = null;
  private teamTableVisible: boolean = false;

  constructor(appContext: AppContext) {
    this.appContext = appContext;

    // Initialize notification storage
    this.notifications.set(NotificationArea.SYSTEM, []);
    this.notifications.set(NotificationArea.CHAT, []);
    this.notifications.set(NotificationArea.OVERLAY, []);
    this.notifications.set(NotificationArea.QUEST, []);

    // Get container elements
    this.initializeContainers();

    logger.debug('NotificationService initialized');
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

    // Subscribe to PlayerInfo updates to refresh shortcut display
    playerService.on('playerInfo:updated', () => {
      // Refresh shortcut display if currently visible
      if (this.currentShortcutMode !== 'off') {
        this.updateShortcutDisplay();
      }
    });

    // Subscribe to shortcut highlight events
    playerService.on('shortcut:highlight', (shortcutKey: string) => {
      this.highlightShortcut(shortcutKey);
    });

    // Subscribe to status effects changes
    playerService.on('statusEffects:changed', (effects: any[]) => {
      this.updateStatusEffectsDisplay(effects);
    });

    // Subscribe to vitals changes
    playerService.on('vitals:changed', (vitals: any[]) => {
      this.updateVitalsDisplay(vitals);
    });

    logger.debug('NotificationService event subscriptions initialized');
  }

  /**
   * Initialize splash screen audio modifier (internal, called from NimbusClient)
   * Creates a modifier in the AMBIENT_AUDIO stack with priority 5
   */
  initializeSplashScreenAudioModifierInternal(): void {

    const modifierService = this.appContext.services.modifier;

    if (!modifierService) {
      return;
    }

    const stack = modifierService.getModifierStack<string>(StackName.AMBIENT_AUDIO);

    if (!stack) {
      logger.warn('Ambient audio stack not available');
      return;
    }

    // Create splash screen audio modifier if not already created
    if (!this.splashScreenAudioModifier) {
      this.splashScreenAudioModifier = stack.addModifier('', 5); // Priority 5 (higher than environment=50, lower than death=10)
      this.splashScreenAudioModifier.setEnabled(false); // Disabled by default
    }
  }

  /**
   * Activate splash screen audio (internal, called from NimbusClient)
   * @param audioPath Path to audio file
   */
  activateSplashScreenAudio(audioPath: string): void {
    if (!this.splashScreenAudioModifier) {
      logger.warn('Splash screen audio modifier not initialized');
      return;
    }

    this.splashScreenAudioModifier.setValue(audioPath);
    this.splashScreenAudioModifier.setEnabled(true);
  }

  /**
   * Highlight a shortcut slot
   *
   * Switches to the appropriate mode if needed and briefly highlights the slot.
   * If shortcuts were not visible before, they are hidden again after highlighting.
   * Example: highlightShortcut('click1') switches to 'clicks' mode and highlights click1
   *
   * @param shortcutKey Shortcut key (e.g., 'key1', 'click2', 'slot5')
   */
  private highlightShortcut(shortcutKey: string): void {
    try {
      // Remember if shortcuts were visible before
      const wasVisible = this.currentShortcutMode !== 'off';

      // Determine which mode this shortcut belongs to
      let targetMode: typeof this.shortcutModes[number] = 'off';

      if (shortcutKey.startsWith('key')) {
        targetMode = 'keys';
      } else if (shortcutKey.startsWith('click')) {
        targetMode = 'clicks';
      } else if (shortcutKey.startsWith('slot')) {
        const slotNum = parseInt(shortcutKey.replace('slot', ''), 10);
        if (!isNaN(slotNum)) {
          targetMode = slotNum >= 10 ? 'slots1' : 'slots0';
        }
      }

      // If not currently showing or showing different mode, switch mode
      if (this.currentShortcutMode !== targetMode) {
        this.currentShortcutMode = targetMode;
        this.updateShortcutDisplay().then(() => {
          this.highlightSlotElement(shortcutKey);

          // If was not visible, hide again after highlight duration (1.5s)
          if (!wasVisible) {
            setTimeout(() => {
              this.currentShortcutMode = 'off';
              this.updateShortcutDisplay();
            }, 1500);
          }
        });
      } else {
        // Already showing correct mode, just highlight
        this.highlightSlotElement(shortcutKey);
      }

      logger.debug('Shortcut highlighted', { shortcutKey, mode: targetMode, wasVisible });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.highlightShortcut', { shortcutKey });
    }
  }

  /**
   * Highlight a specific slot element with animation
   */
  private highlightSlotElement(shortcutKey: string): void {
    if (!this.shortcutContainer) return;

    // Find the slot element by data attribute
    const slots = this.shortcutContainer.querySelectorAll('[data-shortcut-key]');
    for (const slot of Array.from(slots)) {
      if ((slot as HTMLElement).dataset.shortcutKey === shortcutKey) {
        const element = slot as HTMLElement;

        // Store original border
        const originalBorder = element.style.border;

        // Highlight with yellow border
        element.style.border = '2px solid rgba(255, 255, 0, 1)';
        element.style.boxShadow = '0 0 10px rgba(255, 255, 0, 0.8)';

        // Reset after 1 second
        setTimeout(() => {
          element.style.border = originalBorder;
          element.style.boxShadow = '';
        }, 1000);

        break;
      }
    }
  }

  /**
   * Initialize container references
   */
  private initializeContainers(): void {
    try {
      const systemContainer = document.getElementById('notifications-system');
      const chatContainer = document.getElementById('notifications-chat');
      const overlayContainer = document.getElementById('notifications-overlay');
      const questContainer = document.getElementById('notifications-quest');

      if (!systemContainer || !chatContainer || !overlayContainer || !questContainer) {
        throw new Error('Notification containers not found in DOM');
      }

      this.containers.set(NotificationArea.SYSTEM, systemContainer);
      this.containers.set(NotificationArea.CHAT, chatContainer);
      this.containers.set(NotificationArea.OVERLAY, overlayContainer);
      this.containers.set(NotificationArea.QUEST, questContainer);

      logger.debug('Notification containers initialized');
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'NotificationService.initializeContainers'
      );
    }
  }

  /**
   * Create a new notification
   *
   * @param type Notification type (0-31)
   * @param from Sender name (null for system messages)
   * @param message Notification message
   * @param texturePath Optional asset path for icon (e.g., 'items/sword.png')
   */
  newNotification(type: NotificationType, from: string | null, message: string, texturePath?: string | null): void {
    try {
      // Don't show if notifications are suppressed
      if (!this.visible) {
        logger.debug('Notification suppressed', { type, from, message });
        return;
      }

      // Determine area from type
      const area = NOTIFICATION_TYPE_TO_AREA[type];
      if (!area) {
        logger.warn('Unknown notification type', { type });
        return;
      }

      // Don't show static notifications (SYSTEM area) if visibility is disabled
      if (area === NotificationArea.SYSTEM && !this.showStaticNotifications) {
        logger.debug('Static notification suppressed by visibility state', { type, from, message });
        return;
      }

      // Get area config
      const config = NOTIFICATION_AREA_CONFIGS[area];

      // Create notification
      const notification: Notification = {
        id: `notification-${this.nextNotificationId++}`,
        type,
        from,
        message,
        timestamp: Date.now(),
        area,
        texturePath: texturePath || null,
      };

      // Add to storage
      const notifications = this.notifications.get(area)!;
      notifications.push(notification);

      // Enforce max count
      while (notifications.length > config.maxCount) {
        const removed = notifications.shift()!;
        this.removeNotificationElement(removed);
      }

      // Render notification
      this.renderNotification(notification, config);

      // Setup auto-hide if configured
      if (config.autoHideMs !== null) {
        this.setupAutoHide(notification, config.autoHideMs);
      }

      logger.debug('Notification created', {
        id: notification.id,
        type,
        area,
        from,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.newNotification', {
        type,
        from,
        message,
      });
    }
  }

  /**
   * Control notification visibility
   *
   * @param visible If false, suppress all notifications
   */
  notificationsVisible(visible: boolean): void {
    try {
      this.visible = visible;

      if (!visible) {
        logger.debug('Notifications suppressed');
      } else {
        logger.debug('Notifications enabled');
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.notificationsVisible', {
        visible,
      });
    }
  }

  /**
   * Clear all chat notifications
   */
  clearChatNotifications(): void {
    try {
      const chatNotifications = this.notifications.get(NotificationArea.CHAT)!;

      // Remove all chat notification elements
      chatNotifications.forEach((notification) => {
        this.removeNotificationElement(notification);
      });

      // Clear storage
      chatNotifications.length = 0;

      logger.debug('Chat notifications cleared');
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.clearChatNotifications');
    }
  }

  /**
   * Render a notification to the DOM
   */
  private renderNotification(
    notification: Notification,
    config: NotificationAreaConfig
  ): void {
    try {
      const container = this.containers.get(notification.area);
      if (!container) {
        logger.warn('Container not found for area', { area: notification.area });
        return;
      }

      // Get style config
      const style = NOTIFICATION_TYPE_STYLES[notification.type];

      // Create notification element
      const element = document.createElement('div');
      element.className = `notification color-${style.color} size-${style.size}`;
      element.id = notification.id;

      // Add icon if texturePath is provided
      if (notification.texturePath) {
        const iconContainer = document.createElement('div');
        iconContainer.className = 'notification-icon';
        iconContainer.style.cssText = `
          width: 48px;
          height: 48px;
          background: rgba(50, 50, 50, 0.9);
          border: 2px solid rgba(255, 255, 255, 0.3);
          border-radius: 4px;
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
          margin-right: 12px;
        `;

        const networkService = this.appContext.services.network;
        if (networkService) {
          const textureUrl = networkService.getAssetUrl(notification.texturePath);
          const img = document.createElement('img');
          img.src = textureUrl;
          img.style.cssText = `
            width: 44px;
            height: 44px;
            object-fit: contain;
            image-rendering: pixelated;
          `;
          img.onerror = () => {
            logger.warn('Failed to load notification icon', { texturePath: notification.texturePath, textureUrl });
          };
          iconContainer.appendChild(img);
        }

        element.appendChild(iconContainer);
      }

      // Create text container for from and message
      const textContainer = document.createElement('div');
      textContainer.style.cssText = 'flex: 1; display: flex; flex-direction: column;';

      // Add from line if present
      if (notification.from) {
        const fromElement = document.createElement('div');
        fromElement.className = 'notification-from';
        fromElement.textContent = notification.from;
        textContainer.appendChild(fromElement);
      }

      // Add message
      const messageElement = document.createElement('div');
      messageElement.className = 'notification-message';
      messageElement.textContent = notification.message;
      textContainer.appendChild(messageElement);

      element.appendChild(textContainer);

      // Add to container
      container.appendChild(element);

      logger.debug('Notification rendered', { id: notification.id, hasIcon: !!notification.texturePath });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.renderNotification', {
        notification,
      });
    }
  }

  /**
   * Setup auto-hide timer for notification
   */
  private setupAutoHide(notification: Notification, delayMs: number): void {
    try {
      const timerId = window.setTimeout(() => {
        this.hideNotification(notification);
      }, delayMs);

      this.hideTimers.set(notification.id, timerId);

      logger.debug('Auto-hide timer set', { id: notification.id, delayMs });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.setupAutoHide', {
        notification,
        delayMs,
      });
    }
  }

  /**
   * Hide a notification with animation
   */
  private hideNotification(notification: Notification): void {
    try {
      const element = document.getElementById(notification.id);
      if (!element) {
        return;
      }

      // Add hiding class for animation
      element.classList.add('hiding');

      // Remove after animation completes
      setTimeout(() => {
        this.removeNotificationElement(notification);
      }, 300); // Match CSS animation duration

      logger.debug('Notification hidden', { id: notification.id });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.hideNotification', {
        notification,
      });
    }
  }

  /**
   * Remove notification element from DOM and storage
   */
  private removeNotificationElement(notification: Notification): void {
    try {
      // Clear timer if exists
      const timerId = this.hideTimers.get(notification.id);
      if (timerId !== undefined) {
        window.clearTimeout(timerId);
        this.hideTimers.delete(notification.id);
      }

      // Remove from DOM
      const element = document.getElementById(notification.id);
      if (element && element.parentNode) {
        element.parentNode.removeChild(element);
      }

      // Remove from storage
      const notifications = this.notifications.get(notification.area);
      if (notifications) {
        const index = notifications.findIndex((n) => n.id === notification.id);
        if (index !== -1) {
          notifications.splice(index, 1);
        }
      }

      logger.debug('Notification removed', { id: notification.id });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.removeNotificationElement', {
        notification,
      });
    }
  }

  // ============================================
  // Shortcut UI Display
  // ============================================

  /** Shortcut display modes */
  private shortcutModes = ['keys', 'clicks', 'slots0', 'slots1', 'off'] as const;
  private currentShortcutMode: typeof this.shortcutModes[number] = 'off';
  private shortcutContainer: HTMLElement | null = null;

  /** Status effects display */
  private statusEffectsContainer: HTMLElement | null = null;

  /** Vitals display */
  private vitalsContainer: HTMLElement | null = null;
  private showVitals: boolean = true; // Default: on

  /** Visibility toggle states */
  private visibilityStates = ['NONE_VISIBLE', 'ONLY_VITALS', 'ONLY_SHORTCUTS', 'ALL_VISIBLE'] as const;
  private currentVisibilityState: typeof this.visibilityStates[number] = 'ALL_VISIBLE'; // Default: all visible

  /** Static notifications visibility */
  private showStaticNotifications: boolean = true; // Default: on

  /** Status effects visibility */
  private showStatusEffects: boolean = true; // Default: on

  /** Center text display */
  private centerTextElement: HTMLElement | null = null;

  /** Splash screen display */
  private splashScreenElement: HTMLElement | null = null;

  /** Splash screen audio modifier (priority 5) */
  private splashScreenAudioModifier?: Modifier<string>;

  /**
   * Get current shortcut mode
   *
   * @returns Current shortcut display mode
   */
  getCurrentShortcutMode(): typeof this.shortcutModes[number] {
    return this.currentShortcutMode;
  }

  /**
   * Map keyboard number (1-9, 0) to shortcut key based on current display mode
   *
   * @param keyNumber Keyboard number (1-9, 0 maps to index 0-9)
   * @returns Shortcut key to execute (e.g., 'click0', 'slot5', 'key1')
   */
  mapKeyboardNumberToShortcut(keyNumber: number): string | null {
    // If shortcuts not visible, return null (use default key mapping)
    if (this.currentShortcutMode === 'off') {
      return null;
    }

    // Map 1-9, 0 to indices 0-9
    const index = keyNumber === 0 ? 9 : keyNumber - 1;

    switch (this.currentShortcutMode) {
      case 'keys':
        // Default behavior: key1-key9, key0
        return null;

      case 'clicks':
        // Map to click0-9
        return `click${index}`;

      case 'slots0':
        // Map to slot0-9
        return `slot${index}`;

      case 'slots1':
        // Map to slot10-19
        return `slot${10 + index}`;

      default:
        return null;
    }
  }

  /**
   * Toggle shortcuts display
   *
   * Cycles through: keys -> clicks -> slots0 -> slots1 (if available) -> off
   */
  toggleShowShortcuts(): void {
    try {
      const currentIndex = this.shortcutModes.indexOf(this.currentShortcutMode);
      let nextIndex = (currentIndex + 1) % this.shortcutModes.length;

      // Skip slots1 if no shortcuts in that range
      if (this.shortcutModes[nextIndex] === 'slots1') {
        if (!this.hasShortcutsInRange(10, 19)) {
          nextIndex = (nextIndex + 1) % this.shortcutModes.length;
        }
      }

      this.currentShortcutMode = this.shortcutModes[nextIndex];
      this.updateShortcutDisplay();

      logger.debug('Shortcut display toggled', { mode: this.currentShortcutMode });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.toggleShowShortcuts');
    }
  }

  /**
   * Toggle visibility state (F2 key)
   *
   * Cycles through:
   * - NONE_VISIBLE: All elements hidden
   * - ONLY_VITALS: Only vitals and team table visible
   * - ONLY_SHORTCUTS: Only shortcuts visible
   * - ALL_VISIBLE: All elements visible
   */
  toggleVisibilityState(): void {
    try {
      const currentIndex = this.visibilityStates.indexOf(this.currentVisibilityState);
      const nextIndex = (currentIndex + 1) % this.visibilityStates.length;
      const nextState = this.visibilityStates[nextIndex];

      // Show notification before switching to NONE_VISIBLE
      if (nextState === 'NONE_VISIBLE') {
        this.newNotification(0, null, 'All UI elements hidden. Press F2 to restore.');
      }

      this.currentVisibilityState = nextState;

      // Apply visibility settings based on state
      this.applyVisibilityState();

      logger.info('Visibility state toggled', { state: this.currentVisibilityState });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.toggleVisibilityState');
    }
  }

  /**
   * Apply visibility settings based on current state
   */
  private applyVisibilityState(): void {
    try {
      const playerService = this.appContext.services.player;

      switch (this.currentVisibilityState) {
        case 'NONE_VISIBLE':
          // Hide all elements
          this.showVitals = false;
          this.showTeamTable(false);
          this.currentShortcutMode = 'off';
          this.showStaticNotifications = false;
          this.showStatusEffects = false;
          break;

        case 'ONLY_VITALS':
          // Only vitals and team table visible
          this.showVitals = true;
          this.showTeamTable(true);
          this.currentShortcutMode = 'off';
          this.showStaticNotifications = false;
          this.showStatusEffects = false;
          break;

        case 'ONLY_SHORTCUTS':
          // Only shortcuts visible (restore last mode or default to keys)
          this.showVitals = false;
          this.showTeamTable(false);
          this.currentShortcutMode = this.currentShortcutMode === 'off' ? 'keys' : this.currentShortcutMode;
          this.showStaticNotifications = false;
          this.showStatusEffects = false;
          break;

        case 'ALL_VISIBLE':
          // All elements visible
          this.showVitals = true;
          this.showTeamTable(true);
          this.currentShortcutMode = this.currentShortcutMode === 'off' ? 'keys' : this.currentShortcutMode;
          this.showStaticNotifications = true;
          this.showStatusEffects = true;
          break;
      }

      // Update displays
      this.updateShortcutDisplay();
      if (playerService) {
        this.updateVitalsDisplay(playerService.getVitals());
        this.updateStatusEffectsDisplay(playerService.getStatusEffects());
      }

      logger.debug('Visibility state applied', {
        state: this.currentVisibilityState,
        showVitals: this.showVitals,
        showShortcuts: this.currentShortcutMode,
        showStatusEffects: this.showStatusEffects,
        showStaticNotifications: this.showStaticNotifications,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.applyVisibilityState');
    }
  }

  /**
   * Check if shortcuts exist in range
   */
  private hasShortcutsInRange(start: number, end: number): boolean {
    const playerInfo = this.appContext.playerInfo;
    if (!playerInfo?.shortcuts) return false;

    for (let i = start; i <= end; i++) {
      if (playerInfo.shortcuts[`slot${i}`]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Update shortcut display
   */
  private async updateShortcutDisplay(): Promise<void> {
    try {
      // Remove existing display
      if (this.shortcutContainer) {
        this.shortcutContainer.remove();
        this.shortcutContainer = null;
      }

      // If mode is 'off', don't create display
      if (this.currentShortcutMode === 'off') {
        return;
      }

      // Create shortcut container
      this.shortcutContainer = document.createElement('div');
      this.shortcutContainer.id = 'shortcuts-container';
      this.shortcutContainer.style.cssText = `
        position: fixed;
        bottom: 80px;
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        gap: 8px;
        padding: 8px;
        background: rgba(0, 0, 0, 0.7);
        border-radius: 8px;
        z-index: 900;
        font-family: 'Courier New', monospace;
        font-size: 12px;
        color: white;
      `;

      // Get shortcuts to display
      const shortcuts = this.getShortcutsForMode(this.currentShortcutMode);

      // Create slots
      for (const [key, shortcut] of Object.entries(shortcuts)) {
        const slot = await this.createShortcutSlot(key, shortcut);
        this.shortcutContainer.appendChild(slot);
      }

      document.body.appendChild(this.shortcutContainer);
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.updateShortcutDisplay');
    }
  }

  /**
   * Get shortcuts for current mode
   * Always returns exactly 10 slots (filled or empty)
   */
  private getShortcutsForMode(mode: typeof this.shortcutModes[number]): Record<string, any> {
    const playerInfo = this.appContext.playerInfo;
    const playerShortcuts = playerInfo?.shortcuts || {};

    const shortcuts: Record<string, any> = {};

    switch (mode) {
      case 'keys':
        // Keys 1-0 (always 10 slots)
        for (let i = 1; i <= 9; i++) {
          shortcuts[`key${i}`] = playerShortcuts[`key${i}`] || null;
        }
        shortcuts['key0'] = playerShortcuts['key0'] || null;
        break;

      case 'clicks':
        // Clicks 0-9 (always 10 slots, for mice with multiple buttons)
        for (let i = 0; i <= 9; i++) {
          shortcuts[`click${i}`] = playerShortcuts[`click${i}`] || null;
        }
        break;

      case 'slots0':
        // Slots 0-9 (always 10 slots)
        for (let i = 0; i <= 9; i++) {
          shortcuts[`slot${i}`] = playerShortcuts[`slot${i}`] || null;
        }
        break;

      case 'slots1':
        // Slots 10-19 (always 10 slots)
        for (let i = 10; i <= 19; i++) {
          shortcuts[`slot${i}`] = playerShortcuts[`slot${i}`] || null;
        }
        break;
    }

    return shortcuts;
  }

  /**
   * Create shortcut slot element
   */
  private async createShortcutSlot(key: string, shortcut: any): Promise<HTMLElement> {
    const slot = document.createElement('div');
    slot.dataset.shortcutKey = key; // Add data attribute for highlighting
    slot.style.cssText = `
      width: 32px;
      height: 32px;
      background: ${shortcut ? 'rgba(50, 50, 50, 0.9)' : 'rgba(0, 0, 0, 0.9)'};
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      cursor: ${shortcut ? 'pointer' : 'default'};
      flex-shrink: 0;
    `;

    // Add key label
    const label = document.createElement('div');
    label.style.cssText = `
      position: absolute;
      top: 1px;
      right: 2px;
      font-size: 9px;
      color: rgba(255, 255, 255, 0.7);
    `;
    label.textContent = this.formatKeyLabel(key);
    slot.appendChild(label);

    if (shortcut) {
      // Load item and display texture
      await this.loadShortcutItem(slot, shortcut);

      // Add hover tooltip
      this.addShortcutTooltip(slot, shortcut);
    }

    return slot;
  }

  /**
   * Format key label for display
   */
  private formatKeyLabel(key: string): string {
    if (key.startsWith('key')) {
      return key.replace('key', '');
    }
    if (key.startsWith('click')) {
      return 'C' + key.replace('click', '');
    }
    if (key.startsWith('slot')) {
      return 'S' + key.replace('slot', '');
    }
    if (key.startsWith('empty')) {
      return ''; // Empty slots have no label
    }
    return key;
  }

  /**
   * Load item for shortcut and display texture
   */
  private async loadShortcutItem(slot: HTMLElement, shortcut: any): Promise<void> {
    try {
      let textureUrl: string | null = null;

      // Check if shortcut has custom iconPath
      if (shortcut.iconPath) {
        // Use custom icon path directly
        const networkService = this.appContext.services.network;
        if (networkService) {
          textureUrl = networkService.getAssetUrl(shortcut.iconPath);
          logger.debug('Using custom iconPath for shortcut', { iconPath: shortcut.iconPath, textureUrl });
        }
      } else {
        // Fall back to itemId texture
        const itemService = this.appContext.services.item;
        if (!itemService) {
          logger.warn('ItemService not available');
          return;
        }

        // Get item ID from shortcut definition
        const itemId = shortcut.itemId || shortcut.id;
        if (!itemId) {
          logger.debug('No itemId in shortcut', { shortcut });
          return;
        }

        // Load item from server
        const item = await itemService.getItem(itemId);
        if (!item) {
          logger.debug('Item not found', { itemId });
          return;
        }

        // Get texture URL (now async)
        textureUrl = await itemService.getTextureUrl(item);
      }

      if (!textureUrl) {
        logger.debug('No texture available for shortcut', { shortcut });
        return;
      }

      // Create image element
      const img = document.createElement('img');
      img.src = textureUrl;
      img.style.cssText = `
        width: 28px;
        height: 28px;
        object-fit: contain;
        image-rendering: pixelated;
      `;
      img.onerror = () => {
        logger.warn('Failed to load shortcut texture', { textureUrl });
      };

      slot.appendChild(img);
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.loadShortcutItem', { shortcut });
    }
  }

  /**
   * Add tooltip to shortcut slot
   */
  private addShortcutTooltip(slot: HTMLElement, shortcut: any): void {
    let tooltip: HTMLElement | null = null;

    slot.addEventListener('mouseenter', () => {
      // Create tooltip
      tooltip = document.createElement('div');
      tooltip.style.cssText = `
        position: absolute;
        bottom: 100%;
        left: 50%;
        transform: translateX(-50%);
        margin-bottom: 8px;
        padding: 8px 12px;
        background: rgba(0, 0, 0, 0.95);
        border: 1px solid rgba(255, 255, 255, 0.3);
        border-radius: 4px;
        white-space: nowrap;
        pointer-events: none;
        z-index: 1000;
      `;

      // Add title
      const title = document.createElement('div');
      title.style.cssText = 'font-weight: bold; margin-bottom: 4px;';
      title.textContent = shortcut.name || shortcut.displayName || 'Unnamed';
      tooltip.appendChild(title);

      // Add description if available
      if (shortcut.description) {
        const desc = document.createElement('div');
        desc.style.cssText = 'font-size: 11px; color: rgba(255, 255, 255, 0.8);';
        desc.textContent = shortcut.description;
        tooltip.appendChild(desc);
      }

      slot.appendChild(tooltip);
    });

    slot.addEventListener('mouseleave', () => {
      if (tooltip) {
        tooltip.remove();
        tooltip = null;
      }
    });
  }

  // ============================================
  // Status Effects UI Display
  // ============================================

  /**
   * Update status effects display
   *
   * @param effects Array of active status effects
   */
  private async updateStatusEffectsDisplay(effects: any[]): Promise<void> {
    try {
      // Remove existing display
      if (this.statusEffectsContainer) {
        this.statusEffectsContainer.remove();
        this.statusEffectsContainer = null;
      }

      // If no effects or visibility disabled, don't create display
      if (effects.length === 0 || !this.showStatusEffects) {
        return;
      }

      // Create status effects container (above shortcuts)
      this.statusEffectsContainer = document.createElement('div');
      this.statusEffectsContainer.id = 'status-effects-container';
      this.statusEffectsContainer.style.cssText = `
        position: fixed;
        bottom: 130px;
        left: 50%;
        transform: translateX(-50%);
        display: flex;
        gap: 8px;
        padding: 8px;
        background: rgba(0, 0, 0, 0.7);
        border-radius: 8px;
        z-index: 900;
        font-family: 'Courier New', monospace;
        font-size: 12px;
        color: white;
      `;

      // Create effect slots
      for (const effect of effects) {
        const slot = await this.createStatusEffectSlot(effect);
        this.statusEffectsContainer.appendChild(slot);
      }

      document.body.appendChild(this.statusEffectsContainer);
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.updateStatusEffectsDisplay');
    }
  }

  /**
   * Create status effect slot element
   */
  private async createStatusEffectSlot(effect: any): Promise<HTMLElement> {
    const slot = document.createElement('div');
    slot.style.cssText = `
      width: 32px;
      height: 32px;
      background: rgba(139, 0, 0, 0.9);
      border: 2px solid rgba(255, 0, 0, 0.5);
      border-radius: 4px;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      cursor: pointer;
      flex-shrink: 0;
    `;

    // Load item and display texture
    await this.loadStatusEffectItem(slot, effect);

    // Add hover tooltip
    this.addStatusEffectTooltip(slot, effect);

    return slot;
  }

  /**
   * Load item for status effect and display texture
   */
  private async loadStatusEffectItem(slot: HTMLElement, effect: any): Promise<void> {
    try {
      const itemService = this.appContext.services.item;
      if (!itemService) {
        logger.warn('ItemService not available');
        return;
      }

      const itemId = effect.itemId;
      if (!itemId) {
        logger.debug('No itemId in effect', { effect });
        return;
      }

      // Load item from server
      const item = await itemService.getItem(itemId);
      if (!item) {
        logger.debug('Item not found', { itemId });
        return;
      }

      // Get texture URL (now async)
      const textureUrl = await itemService.getTextureUrl(item);
      if (!textureUrl) {
        logger.debug('No texture for item', { itemId });
        return;
      }

      // Create image element
      const img = document.createElement('img');
      img.src = textureUrl;
      img.style.cssText = `
        width: 28px;
        height: 28px;
        object-fit: contain;
        image-rendering: pixelated;
      `;
      img.onerror = () => {
        logger.warn('Failed to load effect texture', { textureUrl });
      };

      slot.appendChild(img);
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.loadStatusEffectItem', { effect });
    }
  }

  /**
   * Add tooltip to status effect slot
   */
  private async addStatusEffectTooltip(slot: HTMLElement, effect: any): Promise<void> {
    const itemService = this.appContext.services.item;
    if (!itemService) return;

    // Load Item for description
    const item = await itemService.getItem(effect.itemId);

    let tooltip: HTMLElement | null = null;

    slot.addEventListener('mouseenter', () => {
      // Create tooltip
      tooltip = document.createElement('div');
      tooltip.style.cssText = `
        position: absolute;
        bottom: 100%;
        left: 50%;
        transform: translateX(-50%);
        margin-bottom: 8px;
        padding: 8px 12px;
        background: rgba(0, 0, 0, 0.95);
        border: 1px solid rgba(255, 0, 0, 0.5);
        border-radius: 4px;
        white-space: nowrap;
        pointer-events: none;
        z-index: 1000;
      `;

      // Add title (from Item or effect)
      const title = document.createElement('div');
      title.style.cssText = 'font-weight: bold; margin-bottom: 4px; color: rgba(255, 100, 100, 1);';
      title.textContent = item?.name || effect.itemId;
      tooltip.appendChild(title);

      // Add description if available
      if (item?.description) {
        const desc = document.createElement('div');
        desc.style.cssText = 'font-size: 11px; color: rgba(255, 255, 255, 0.8); margin-bottom: 4px;';
        desc.textContent = item.description;
        tooltip.appendChild(desc);
      }

      // Add duration info if available
      if (effect.expiresAt) {
        const remaining = Math.max(0, effect.expiresAt - Date.now());
        const duration = document.createElement('div');
        duration.style.cssText = 'font-size: 10px; color: rgba(255, 200, 200, 0.8);';
        duration.textContent = `${(remaining / 1000).toFixed(1)}s remaining`;
        tooltip.appendChild(duration);
      }

      slot.appendChild(tooltip);
    });

    slot.addEventListener('mouseleave', () => {
      if (tooltip) {
        tooltip.remove();
        tooltip = null;
      }
    });
  }

  // ============================================
  // Vitals UI Display
  // ============================================

  /**
   * Toggle vitals display
   */
  toggleShowVitals(): void {
    this.showVitals = !this.showVitals;
    const playerService = this.appContext.services.player;
    if (playerService) {
      this.updateVitalsDisplay(playerService.getVitals());
    }
    logger.debug('Vitals display toggled', { showVitals: this.showVitals });
  }

  /**
   * Update vitals display
   *
   * @param vitals Array of vitals data
   */
  private async updateVitalsDisplay(vitals: any[]): Promise<void> {
    try {
      // Remove existing display
      if (this.vitalsContainer) {
        this.vitalsContainer.remove();
        this.vitalsContainer = null;
      }

      // If showVitals is off, don't create display
      if (!this.showVitals) {
        return;
      }

      // Filter vitals that should be displayed (not full)
      const visibleVitals = vitals.filter((vital) => {
        const maxValue = vital.max + (vital.extended || 0);
        return vital.current < maxValue;
      });

      // If no vitals to show, don't create container
      if (visibleVitals.length === 0) {
        return;
      }

      // Sort by order
      visibleVitals.sort((a, b) => a.order - b.order);

      // Create vitals container
      this.vitalsContainer = document.createElement('div');
      this.vitalsContainer.id = 'vitals-container';
      this.vitalsContainer.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        display: flex;
        flex-direction: column;
        gap: 4px;
        z-index: 900;
        font-family: 'Courier New', monospace;
        font-size: 11px;
        color: white;
      `;

      // Create vital bars
      for (const vital of visibleVitals) {
        const bar = this.createVitalBar(vital);
        this.vitalsContainer.appendChild(bar);
      }

      document.body.appendChild(this.vitalsContainer);
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.updateVitalsDisplay');
    }
  }

  /**
   * Create vital bar element
   */
  private createVitalBar(vital: any): HTMLElement {
    const container = document.createElement('div');
    container.style.cssText = `
      position: relative;
      cursor: pointer;
    `;

    // Calculate dimensions
    const baseHeight = 10;
    const extendedPercent = vital.extended ? (vital.extended / vital.max) : 0;
    const totalHeight = Math.min(50, baseHeight + extendedPercent * baseHeight * 5); // Max 50px
    const width = 200;

    // Background bar (max + extended)
    const bgBar = document.createElement('div');
    bgBar.style.cssText = `
      width: ${width}px;
      height: ${totalHeight}px;
      background: rgba(50, 50, 50, 0.9);
      border: 1px solid rgba(255, 255, 255, 0.3);
      border-radius: 3px;
      position: relative;
      overflow: hidden;
    `;

    // Foreground bar (current value)
    const maxValue = vital.max + (vital.extended || 0);
    const fillPercent = maxValue > 0 ? (vital.current / maxValue) * 100 : 0;

    const fgBar = document.createElement('div');
    fgBar.style.cssText = `
      width: ${fillPercent}%;
      height: 100%;
      background: ${vital.color || '#ffffff'};
      transition: width 0.3s ease-out;
    `;
    bgBar.appendChild(fgBar);

    // Extended indicator (if present)
    if (vital.extended && vital.extended > 0) {
      const extendedBar = document.createElement('div');
      const extendedStartPercent = (vital.max / maxValue) * 100;
      extendedBar.style.cssText = `
        position: absolute;
        left: ${extendedStartPercent}%;
        top: 0;
        width: ${100 - extendedStartPercent}%;
        height: 100%;
        background: rgba(255, 255, 255, 0.2);
        border-left: 1px dashed rgba(255, 255, 255, 0.5);
      `;
      bgBar.appendChild(extendedBar);
    }

    // Value text overlay
    const text = document.createElement('div');
    text.style.cssText = `
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: 10px;
      font-weight: bold;
      color: white;
      text-shadow: 1px 1px 2px rgba(0, 0, 0, 0.8);
      pointer-events: none;
    `;
    text.textContent = `${Math.floor(vital.current)}/${vital.max}${vital.extended ? `+${vital.extended}` : ''}`;
    bgBar.appendChild(text);

    container.appendChild(bgBar);

    // Add tooltip
    this.addVitalTooltip(container, vital);

    return container;
  }

  /**
   * Add tooltip to vital bar
   */
  private addVitalTooltip(container: HTMLElement, vital: any): void {
    let tooltip: HTMLElement | null = null;

    container.addEventListener('mouseenter', () => {
      // Create tooltip
      tooltip = document.createElement('div');
      tooltip.style.cssText = `
        position: absolute;
        top: 0;
        right: 100%;
        margin-right: 8px;
        padding: 8px 12px;
        background: rgba(0, 0, 0, 0.95);
        border: 1px solid rgba(255, 255, 255, 0.3);
        border-radius: 4px;
        white-space: nowrap;
        pointer-events: none;
        z-index: 1000;
      `;

      // Title
      const title = document.createElement('div');
      title.style.cssText = 'font-weight: bold; margin-bottom: 4px;';
      title.textContent = vital.name || vital.type;
      tooltip.appendChild(title);

      // Current/Max
      const values = document.createElement('div');
      values.style.cssText = 'font-size: 11px; margin-bottom: 2px;';
      values.textContent = `${Math.floor(vital.current)} / ${vital.max}${vital.extended ? ` (+${vital.extended})` : ''}`;
      tooltip.appendChild(values);

      // Regen/Degen
      if (vital.regenRate > 0) {
        const regen = document.createElement('div');
        regen.style.cssText = 'font-size: 10px; color: rgba(100, 255, 100, 0.8);';
        regen.textContent = `+${vital.regenRate.toFixed(1)}/s`;
        tooltip.appendChild(regen);
      }

      if (vital.degenRate > 0) {
        const degen = document.createElement('div');
        degen.style.cssText = 'font-size: 10px; color: rgba(255, 100, 100, 0.8);';
        degen.textContent = `-${vital.degenRate.toFixed(1)}/s`;
        tooltip.appendChild(degen);
      }

      // Extended expiry
      if (vital.extendExpiry) {
        const remaining = Math.max(0, vital.extendExpiry - Date.now());
        const expiry = document.createElement('div');
        expiry.style.cssText = 'font-size: 9px; color: rgba(255, 255, 100, 0.8); margin-top: 2px;';
        expiry.textContent = `Extend expires in ${(remaining / 1000).toFixed(0)}s`;
        tooltip.appendChild(expiry);
      }

      container.appendChild(tooltip);
    });

    container.addEventListener('mouseleave', () => {
      if (tooltip) {
        tooltip.remove();
        tooltip = null;
      }
    });
  }

  /**
   * Dispose service and clean up
   */
  dispose(): void {
    try {
      // Clear all timers
      this.hideTimers.forEach((timerId) => {
        window.clearTimeout(timerId);
      });
      this.hideTimers.clear();

      // Clear all notifications
      this.notifications.forEach((notifications, area) => {
        notifications.forEach((notification) => {
          this.removeNotificationElement(notification);
        });
        notifications.length = 0;
      });

      // Remove shortcut container
      if (this.shortcutContainer) {
        this.shortcutContainer.remove();
        this.shortcutContainer = null;
      }

      // Remove status effects container
      if (this.statusEffectsContainer) {
        this.statusEffectsContainer.remove();
        this.statusEffectsContainer = null;
      }

      // Remove vitals container
      if (this.vitalsContainer) {
        this.vitalsContainer.remove();
        this.vitalsContainer = null;
      }

      // Remove center text element
      if (this.centerTextElement) {
        this.centerTextElement.remove();
        this.centerTextElement = null;
      }

      // Remove splash screen element
      if (this.splashScreenElement) {
        this.splashScreenElement.remove();
        this.splashScreenElement = null;
      }

      logger.debug('NotificationService disposed');
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.dispose');
    }
  }

  /**
   * Flash an image on screen
   *
   * Displays an image in the center of the screen that:
   * - Starts small and scales to full screen height
   * - Animates over the specified duration
   * - Disappears after animation completes
   *
   * @param assetPath Path to PNG image (only .png files accepted)
   * @param duration Animation duration in milliseconds
   * @param opacity Image opacity (0.0-1.0)
   */
  flashImage(assetPath: string, duration: number, opacity: number): void {
    try {
      // Validate PNG file
      if (!assetPath.toLowerCase().endsWith('.png')) {
        logger.warn('flashImage: Only PNG files are allowed', { assetPath });
        return;
      }

      // Get full asset URL via NetworkService
      const networkService = this.appContext.services.network;
      if (!networkService) {
        logger.warn('NetworkService not available for flashImage');
        return;
      }

      const imageUrl = networkService.getAssetUrl(assetPath);

      logger.debug('Flashing image', { assetPath, imageUrl, duration, opacity });

      // Create container for flash image
      const flashContainer = document.createElement('div');
      flashContainer.style.position = 'fixed';
      flashContainer.style.top = '0';
      flashContainer.style.left = '0';
      flashContainer.style.width = '100vw';
      flashContainer.style.height = '100vh';
      flashContainer.style.display = 'flex';
      flashContainer.style.justifyContent = 'center';
      flashContainer.style.alignItems = 'center';
      flashContainer.style.pointerEvents = 'none';
      flashContainer.style.zIndex = '9999';

      // Create image element
      const img = document.createElement('img');
      img.src = imageUrl;
      img.style.height = '0%'; // Start small
      img.style.width = 'auto';
      img.style.maxWidth = '100vw';
      img.style.maxHeight = '100vh';
      img.style.objectFit = 'contain';
      img.style.opacity = opacity.toString();
      img.style.transition = `height ${duration}ms ease-out`;

      flashContainer.appendChild(img);
      document.body.appendChild(flashContainer);

      // Start animation after a brief delay (to ensure transition triggers)
      setTimeout(() => {
        img.style.height = '100vh'; // Scale to full screen height
      }, 50);

      // Remove after animation completes
      setTimeout(() => {
        flashContainer.remove();
        logger.debug('Flash image removed', { assetPath });
      }, duration + 100); // Small buffer after animation

      logger.debug('Flash image displayed', { assetPath, duration, opacity });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.flashImage', {
        assetPath,
        duration,
        opacity,
      });
    }
  }

  /**
   * Set center text (permanent display until cleared)
   *
   * Displays text in the center of the screen with white color and black shadow.
   * Text remains visible until cleared with clearCenterText() or setCenterText('').
   *
   * @param text Text to display (empty string to clear)
   */
  setCenterText(text: string): void {
    try {
      // If empty text, clear instead
      if (!text || text.trim() === '') {
        this.clearCenterText();
        return;
      }

      // Create center text element if not exists
      if (!this.centerTextElement) {
        this.centerTextElement = document.createElement('div');
        this.centerTextElement.style.position = 'fixed';
        this.centerTextElement.style.top = '50%';
        this.centerTextElement.style.left = '50%';
        this.centerTextElement.style.transform = 'translate(-50%, -50%)';
        this.centerTextElement.style.color = 'white';
        this.centerTextElement.style.fontSize = '32px';
        this.centerTextElement.style.fontWeight = 'bold';
        this.centerTextElement.style.textAlign = 'center';
        this.centerTextElement.style.textShadow = '2px 2px 4px black, -2px -2px 4px black, 2px -2px 4px black, -2px 2px 4px black';
        this.centerTextElement.style.pointerEvents = 'none';
        this.centerTextElement.style.zIndex = '10000';
        this.centerTextElement.style.padding = '20px';
        this.centerTextElement.style.maxWidth = '80vw';
        this.centerTextElement.style.wordWrap = 'break-word';

        document.body.appendChild(this.centerTextElement);

        logger.debug('Center text element created');
      }

      // Update text content
      this.centerTextElement.textContent = text;
      this.centerTextElement.style.display = 'block';

      logger.debug('Center text set', { text });
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.setCenterText', { text });
    }
  }

  /**
   * Clear center text
   */
  clearCenterText(): void {
    try {
      if (this.centerTextElement) {
        this.centerTextElement.style.display = 'none';
        this.centerTextElement.textContent = '';
        logger.debug('Center text cleared');
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.clearCenterText');
    }
  }

  /**
   * Show splash screen
   *
   * Displays a fullscreen image as splash screen.
   * Optionally plays audio via the AMBIENT_AUDIO modifier stack.
   * Call without assetPath (empty string) to remove the splash screen.
   *
   * @param assetPath Path to image asset (empty string to remove splash screen)
   * @param audioPath Optional path to audio file to play during splash screen
   */
  showSplashScreen(assetPath: string = '', audioPath?: string): void {
    try {
      logger.debug('showSplashScreen called', { assetPath, audioPath });

      // Remove existing splash screen if present
      if (this.splashScreenElement) {
        this.splashScreenElement.remove();
        this.splashScreenElement = null;
        logger.debug('Existing splash screen removed');
      }

      // Disable audio modifier when removing splash screen
      if (this.splashScreenAudioModifier) {
        this.splashScreenAudioModifier.setEnabled(false);
        logger.debug('Splash screen audio modifier disabled');
      }

      // If empty path, just remove (already done above)
      if (!assetPath || assetPath.trim() === '') {
        logger.debug('Empty assetPath, splash screen not shown');
        return;
      }

      // Get full asset URL via NetworkService
      const networkService = this.appContext.services.network;
      if (!networkService) {
        logger.warn('NetworkService not available for showSplashScreen');
        return;
      }

      const imageUrl = networkService.getAssetUrl(assetPath);

      logger.debug('Creating splash screen element', { assetPath, imageUrl });

      // Create splash screen container
      this.splashScreenElement = document.createElement('div');
      this.splashScreenElement.id = 'splash-screen-container';
      this.splashScreenElement.style.cssText = `
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background-color: black;
        display: flex;
        justify-content: center;
        align-items: center;
        z-index: 99999;
        pointer-events: auto;
      `;

      // Create image element
      const img = document.createElement('img');
      img.src = imageUrl;
      img.style.cssText = `
        width: 100%;
        height: 100%;
        object-fit: contain;
      `;

      img.onload = () => {
        logger.debug('Splash screen image loaded successfully', { imageUrl });
      };

      img.onerror = (error) => {
        logger.warn('Failed to load splash screen image', { assetPath, imageUrl, error });
      };

      this.splashScreenElement.appendChild(img);
      document.body.appendChild(this.splashScreenElement);

      logger.debug('Splash screen element appended to body', {
        elementId: this.splashScreenElement.id,
        parentElement: document.body.contains(this.splashScreenElement) ? 'YES' : 'NO'
      });

      // Handle audio if provided
      if (audioPath && audioPath.trim() !== '') {
        this.initializeSplashScreenAudioModifierInternal();
        this.activateSplashScreenAudio(audioPath);
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'NotificationService.showSplashScreen', {
        assetPath,
        audioPath,
      });
    }
  }

  /**
   * Show/hide team table
   * @param show Whether to show the team table
   */
  showTeamTable(show: boolean): void {
    this.teamTableVisible = show;

    if (this.teamTableContainer) {
      this.teamTableContainer.style.display = show ? 'block' : 'none';
    }

    logger.info('Team table visibility', { visible: show });
  }

  /**
   * Set complete team
   * @param teamName Team name
   * @param members Team members
   */
  setTeam(teamName: string, members: TeamMember[]): void {
    // Create container if not exists
    if (!this.teamTableContainer) {
      this.createTeamTableContainer();
    }

    // Clear existing content
    this.teamTableContainer!.innerHTML = '';

    // Team name header
    const header = document.createElement('div');
    header.style.cssText = 'font-weight: bold; margin-bottom: 8px; font-size: 14px;';
    header.textContent = teamName;
    this.teamTableContainer!.appendChild(header);

    // Members container
    const membersContainer = document.createElement('div');
    membersContainer.id = 'team-members-list';

    members.forEach(member => {
      const element = this.createTeamMemberElement(member);
      membersContainer.appendChild(element);
    });

    this.teamTableContainer!.appendChild(membersContainer);
    this.showTeamTable(true);

    logger.info('Team table updated', { teamName, memberCount: members.length });
  }

  /**
   * Update single team member
   * @param member Team member
   */
  updateTeamMember(member: TeamMember): void {
    const existingElement = document.getElementById(`team-member-${member.playerId}`);

    if (existingElement) {
      // Replace existing element
      const newElement = this.createTeamMemberElement(member);
      existingElement.replaceWith(newElement);
    }
  }

  /**
   * Create team table container
   */
  private createTeamTableContainer(): void {
    this.teamTableContainer = document.createElement('div');
    this.teamTableContainer.id = 'team-table';
    this.teamTableContainer.style.cssText = `
      position: absolute;
      left: 10px;
      top: 10px;
      background: rgba(0, 0, 0, 0.8);
      color: white;
      padding: 10px;
      border-radius: 5px;
      font-family: monospace;
      font-size: 12px;
      z-index: 1000;
      min-width: 200px;
    `;

    document.body.appendChild(this.teamTableContainer);
  }

  /**
   * Create team member element
   * @param member Team member
   * @returns HTML element
   */
  private createTeamMemberElement(member: TeamMember): HTMLElement {
    const container = document.createElement('div');
    container.id = `team-member-${member.playerId}`;
    container.style.cssText = 'display: flex; align-items: center; margin: 4px 0; gap: 8px;';

    // Status indicator (colored square)
    const statusColor = this.getTeamMemberStatusColor(member);
    const statusIndicator = document.createElement('div');
    statusIndicator.style.cssText = `
      width: 16px;
      height: 16px;
      background-color: ${statusColor};
      border: 1px solid rgba(255,255,255,0.3);
    `;
    container.appendChild(statusIndicator);

    // Icon (if available)
    if (member.icon) {
      const icon = document.createElement('img');
      icon.src = member.icon;
      icon.style.cssText = 'width: 20px; height: 20px; border-radius: 3px;';
      icon.onerror = () => {
        icon.style.display = 'none';
      }; // Hide if load fails
      container.appendChild(icon);
    }

    // Name
    const nameColor = member.status === 2 ? '#999999' : '#ffffff';
    const name = document.createElement('span');
    name.textContent = member.name;
    name.style.cssText = `color: ${nameColor}; flex: 1;`;
    container.appendChild(name);

    // Health (nur bei alive)
    if (member.status === 1 && member.health !== undefined) {
      const health = document.createElement('span');
      health.textContent = `${member.health}%`;
      health.style.cssText = 'font-size: 10px; color: #aaaaaa;';
      container.appendChild(health);
    }

    return container;
  }

  /**
   * Get status color for team member
   * @param member Team member
   * @returns Color hex string
   */
  private getTeamMemberStatusColor(member: TeamMember): string {
    if (member.status === 0) return '#000000'; // Disconnected: schwarz
    if (member.status === 2) return '#999999'; // Dead: grau
    if (member.status === 1) {
      // Alive: grün, aber rot wenn health < 10
      if (member.health !== undefined && member.health < 10) {
        return '#ff0000'; // Low health: rot
      }
      return '#00ff00'; // Alive: grün
    }
    return '#ffffff'; // Default
  }
}
