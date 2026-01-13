/**
 * Editor Input Handlers
 *
 * Handles editor-specific actions like selection mode rotation and editor activation.
 * These handlers are only available in Editor build mode.
 */

import { getLogger } from '@nimbus/shared';
import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';
import type { AppContext } from '../../AppContext';
import { SelectMode } from '../../services/SelectService';

const logger = getLogger('EditorHandlers');

/**
 * Selection Mode Order for rotation
 */
const SELECT_MODE_ORDER: SelectMode[] = [
  SelectMode.NONE,
  SelectMode.INTERACTIVE,
  SelectMode.BLOCK,
  SelectMode.AIR,
  SelectMode.ALL,
];

/**
 * EditSelectionRotator Handler (Key: '.')
 *
 * Rotates through selection modes:
 * NONE → INTERACTIVE → BLOCK → AIR → ALL → (back to NONE)
 */
export class EditSelectionRotatorHandler extends InputHandler {
  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  protected onActivate(value: number): void {
    const selectService = this.appContext?.services.select;
    const notificationService = this.appContext?.services.notification;

    if (!selectService) {
      logger.warn('SelectService not available');
      return;
    }

    // Get current mode
    const currentMode = selectService.autoSelectMode;
    const currentIndex = SELECT_MODE_ORDER.indexOf(currentMode);

    // Calculate next mode (wrap around)
    const nextIndex = (currentIndex + 1) % SELECT_MODE_ORDER.length;
    const nextMode = SELECT_MODE_ORDER[nextIndex];

    // Set new mode
    selectService.autoSelectMode = nextMode;

    logger.debug(`Selection mode changed: ${currentMode} → ${nextMode}`);

    // Show notification about mode change
    if (notificationService) {
      notificationService.newNotification(0, null, `Selection Mode: ${nextMode}`);
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Mode rotation doesn't need continuous updates
  }
}

/**
 * EditorActivate Handler (Key: '/')
 *
 * Sends setSelectedEditBlock command to server for currently selected block
 * The server will then execute the configured editAction (e.g., open editor, config dialog, etc.)
 */
export class EditorActivateHandler extends InputHandler {
  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  protected onActivate(value: number): void {
    const selectService = this.appContext?.services.select;
    const commandService = this.appContext?.services.command;
    const notificationService = this.appContext?.services.notification;
    const modalService = this.appContext?.services.modal;

    // Check service availability
    if (!selectService) {
      logger.warn('SelectService not available');
      // TODO send b.int to server
      return;
    }

    if (!commandService) {
      logger.warn('CommandService not available');
      return;
    }

    // Get currently selected block
    const selectedBlock = selectService.getCurrentSelectedBlock();

    if (!selectedBlock) {
      logger.warn('No block selected - aim at a block to edit');
      if (notificationService) {
        notificationService.newNotification(0, null, 'No block selected - aim at a block to edit');
      }
      return;
    }

    // Get block position
    const pos = selectedBlock.block.position;

    // Send setSelectedEditBlock command to server
    // Server will execute the configured editAction (OPEN_EDITOR, OPEN_CONFIG_DIALOG, etc.)
    logger.debug('Sending setSelectedEditBlock command to server', { position: pos });

    commandService
//      .sendCommandToServer('setSelectedEditBlock', [pos.x, pos.y, pos.z])
      .sendCommandToServer('control.EditBlockTrigger', [pos.x, pos.y, pos.z])
      .then((result) => {
        logger.debug('setSelectedEditBlock command result', { result });
        if (result.rc !== 0) {
          // Command failed - show notification and open edit configuration
          logger.warn('setSelectedEditBlock command failed', { message: result.message });

          if (notificationService) {
            notificationService.newNotification(
              0,
              null,
              `Edit command failed: ${result.message}. Opening configuration...`
            );
          }

          // Open EditConfiguration modal to let user fix the issue
          if (modalService) {
            setTimeout(() => {
              modalService.openEditConfiguration();
            }, 500); // Small delay so notification is visible first
          }
        }
      })
      .catch((error) => {
        // Network/timeout error - show notification and open edit configuration
        logger.error('Failed to send setSelectedEditBlock command', { error });

        const errorMessage = error instanceof Error ? error.message : 'Unknown error';

        if (notificationService) {
          notificationService.newNotification(
            0,
            null,
            `Failed to communicate with server: ${errorMessage}. Opening configuration...`
          );
        }

        // Open EditConfiguration modal
        if (modalService) {
          setTimeout(() => {
            modalService.openEditConfiguration();
          }, 500); // Small delay so notification is visible first
        }
      });
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Editor activation doesn't need continuous updates
  }
}

/**
 * BlockEditorActivate Handler (Key: F10)
 *
 * Opens block editor for currently selected block
 */
export class BlockEditorActivateHandler extends InputHandler {
  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  protected onActivate(value: number): void {
    const selectService = this.appContext?.services.select;
    const modalService = this.appContext?.services.modal;
    const notificationService = this.appContext?.services.notification;

    // Check service availability
    if (!selectService) {
      logger.warn('SelectService not available');
      return;
    }

    if (!modalService) {
      logger.warn('ModalService not available');
      return;
    }

    // Get currently selected block
    const selectedBlock = selectService.getCurrentSelectedBlock();

    if (!selectedBlock) {
      logger.warn('No block selected - aim at a block to edit');
      if (notificationService) {
        notificationService.newNotification(0, null, 'No block selected - aim at a block to edit');
      }
      return;
    }

    // Get block position
    const pos = selectedBlock.block.position;

    // Open block editor directly
    logger.debug('Opening block editor (triggered by F10 key)', { position: pos });

    try {
      modalService.openBlockEditor(pos.x, pos.y, pos.z);
    } catch (error) {
      logger.error('Failed to open block editor', { error });

      if (notificationService) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        notificationService.newNotification(0, null, `Failed to open block editor: ${errorMessage}`);
      }
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Editor activation doesn't need continuous updates
  }
}

/**
 * EditConfigActivate Handler (Key: F9)
 *
 * Opens widget selector dialog to choose between available widgets
 */
export class EditConfigActivateHandler extends InputHandler {
  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  protected onActivate(value: number): void {
    const modalService = this.appContext?.services.modal;

    // Check service availability
    if (!modalService) {
      logger.warn('ModalService not available');
      return;
    }

    // Open Widget Selector dialog
    logger.debug('Opening widget selector dialog (triggered by F9 key)');

    try {
      modalService.openWidgetSelector();
    } catch (error) {
      logger.error('Failed to open widget selector dialog', { error });

      const notificationService = this.appContext?.services.notification;
      if (notificationService) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        notificationService.newNotification(0, null, `Failed to open widget selector: ${errorMessage}`);
      }
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Config activation doesn't need continuous updates
  }
}

/**
 * PanelActivate Handler (Key: p)
 *
 * Opens panel navigation modal
 */
export class PanelActivateHandler extends InputHandler {
  constructor(playerService: PlayerService, appContext: AppContext) {
    super(playerService, appContext);
  }

  protected onActivate(value: number): void {
    const modalService = this.appContext?.services.modal;

    // Check service availability
    if (!modalService) {
      logger.warn('ModalService not available');
      return;
    }

    // Open Panel modal
    logger.debug('Opening panel navigation modal (triggered by p key)');

    try {
      modalService.openPanel();
    } catch (error) {
      logger.error('Failed to open panel modal', { error });

      const notificationService = this.appContext?.services.notification;
      if (notificationService) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        notificationService.newNotification(0, null, `Failed to open panel: ${errorMessage}`);
      }
    }
  }

  protected onDeactivate(): void {
    // No action needed on deactivation
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Panel activation doesn't need continuous updates
  }
}
