/**
 * Action Input Handlers
 *
 * Handles discrete actions like jumping and toggling movement mode.
 */

import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';
import { PlayerMovementState, getLogger } from '@nimbus/shared';

const logger = getLogger('ActionHandlers');

/**
 * Jump Handler
 */
export class JumpHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Jump is a discrete action, execute immediately
    this.playerService.jump();

    // Immediately deactivate after jumping (discrete action, not continuous)
    // This allows the handler to be activated again on next Space press
    this.deactivate();
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Jump doesn't need continuous updates
  }
}

/**
 * Cycle Movement State Handler (F key)
 * Cycles through movement states: FREE_FLY → FLY → SPRINT → CROUCH → WALK
 * FREE_FLY and FLY are only available in Editor mode (__EDITOR__ = true)
 * Respects world's allowedMovementModes restriction
 */
export class CycleMovementStateHandler extends InputHandler {
  protected onActivate(value: number): void {
    const current = this.playerService.getMovementState();

    // Get allowed movement modes from WorldInfo
    const worldInfo = this.appContext?.worldInfo;
    const allowedModes = worldInfo?.settings?.allowedMovementModes;

    // Build rotation list based on editor mode and allowed modes
    const allStates = __EDITOR__
      ? [
          PlayerMovementState.WALK,
          PlayerMovementState.FREE_FLY,
          PlayerMovementState.FLY,
          PlayerMovementState.SPRINT,
          PlayerMovementState.CROUCH,
        ]
      : [
          PlayerMovementState.WALK,
          PlayerMovementState.SPRINT,
          PlayerMovementState.CROUCH,
        ];

    // Filter by allowed modes if specified
    const allowedStates = allowedModes
      ? allStates.filter(state => allowedModes.includes(state))
      : allStates;

    // If no states allowed (shouldn't happen), fallback to WALK
    if (allowedStates.length === 0) {
      logger.warn('No allowed movement states, fallback to WALK');
      this.playerService.setMovementState(PlayerMovementState.WALK);
      return;
    }

    // Find current index
    const currentIndex = allowedStates.indexOf(current);

    // Calculate next index (wrap around)
    const nextIndex = (currentIndex + 1) % allowedStates.length;
    const nextState = allowedStates[nextIndex];

    this.playerService.setMovementState(nextState);
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Cycle doesn't need continuous updates
  }
}

/**
 * Toggle View Mode Handler
 * Toggles between Ego (first-person) and Third-Person view
 */
export class ToggleViewModeHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Toggle is a discrete action, execute immediately
    this.playerService.toggleViewMode();
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Toggle doesn't need continuous updates
  }
}

/**
 * Toggle Fullscreen Handler (F6)
 * Toggles browser fullscreen mode
 */
export class ToggleFullscreenHandler extends InputHandler {
  protected onActivate(value: number): void {
    try {
      if (!document.fullscreenElement) {
        // Enter fullscreen
        document.documentElement.requestFullscreen().catch(err => {
          logger.error('Failed to enter fullscreen:', err);
        });
      } else {
        // Exit fullscreen
        document.exitFullscreen().catch(err => {
          logger.error('Failed to exit fullscreen:', err);
        });
      }
    } catch (error) {
      logger.error('Fullscreen toggle error:', error);
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Toggle doesn't need continuous updates
  }
}

/**
 * Toggle Shortcuts Handler (T key)
 * Toggles shortcut display: keys -> clicks -> slots0 -> slots1 -> off
 */
export class ToggleShortcutsHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Toggle is a discrete action, execute immediately
    const notificationService = this.appContext?.services.notification;
    if (notificationService) {
      notificationService.toggleShowShortcuts();
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Toggle doesn't need continuous updates
  }
}

/**
 * Toggle Visibility State Handler (F2 key)
 * Cycles through visibility states: NONE_VISIBLE -> ONLY_VITALS -> ONLY_SHORTCUTS -> ALL_VISIBLE
 */
export class ToggleVisibilityStateHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Toggle is a discrete action, execute immediately
    const notificationService = this.appContext?.services.notification;
    if (notificationService) {
      notificationService.toggleVisibilityState();
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Toggle doesn't need continuous updates
  }
}

/**
 * Toggle Model Selector Handler (F8 key)
 * Toggles model selector visibility when enabled (Editor only)
 */
export class ToggleModelSelectorHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Only available in editor mode
    if (!__EDITOR__) {
      return;
    }

    const selectService = this.appContext?.services.select;
    if (!selectService) {
      logger.warn('SelectService not available');
      return;
    }

    // Check if model selector is enabled
    if (!selectService.isModelSelectorEnabled()) {
      logger.info('Model selector not enabled');
      return;
    }

    // Toggle visibility
    selectService.toggleModelSelectorVisibility();

    logger.info('Model selector visibility toggled', {
      visible: selectService.isModelSelectorVisible(),
    });
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Toggle doesn't need continuous updates
  }
}
