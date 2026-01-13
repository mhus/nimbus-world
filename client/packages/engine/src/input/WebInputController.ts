/**
 * WebInputController - Browser input controller
 *
 * Handles keyboard and mouse input for web browsers.
 * Binds WASD + Space + Mouse for player control.
 */

import { getLogger } from '@nimbus/shared';
import type { InputController } from '../services/InputService';
import type { PlayerService } from '../services/PlayerService';
import type { AppContext } from '../AppContext';
import type { InputHandler } from './InputHandler';
import { SelectMode } from '../services/SelectService';

const logger = getLogger('WebInputController');

/**
 * Key binding for an action
 */
interface KeyBinding {
  key: string;
  handler: InputHandler;
}

/**
 * WebInputController - Browser input
 *
 * Key bindings:
 * - W: Move forward
 * - S: Move backward
 * - A: Move left
 * - D: Move right
 * - Space: Jump (Walk mode) / Move up (Fly mode)
 * - Shift: Move down (Fly mode only)
 * - F: Cycle movement state (WALK → SPRINT → CROUCH → WALK, includes FLY in Editor)
 * - F2: Toggle visibility state (NONE_VISIBLE → ONLY_VITALS → ONLY_SHORTCUTS → ALL_VISIBLE)
 * - F5: Toggle Ego/Third-Person view
 * - F6: Toggle fullscreen
 * - F8: Toggle model selector visibility (Editor only)
 * - T: Toggle shortcuts display
 * - . (Period): Rotate selection mode (Editor only)
 * - / (Slash): Activate selected block editor (Editor only)
 * - p: Open panel navigation (Editor only)
 * - F9: Open edit configuration (Editor only)
 * - F10: Open block editor for selected block (Editor only)
 * - Mouse: Look around (when pointer locked)
 */
export class WebInputController implements InputController {
  private canvas: HTMLCanvasElement;
  private playerService: PlayerService;
  private appContext: AppContext;

  private handlers: InputHandler[] = [];
  private keyBindings: Map<string, InputHandler> = new Map();

  // All handlers are retrieved from InputService
  private moveForwardHandler?: InputHandler;
  private moveBackwardHandler?: InputHandler;
  private moveLeftHandler?: InputHandler;
  private moveRightHandler?: InputHandler;
  private moveUpHandler?: InputHandler;
  private moveDownHandler?: InputHandler;
  private jumpHandler?: InputHandler;
  private cycleMovementStateHandler?: InputHandler;
  private toggleViewModeHandler?: InputHandler;
  private toggleShortcutsHandler?: InputHandler;
  private toggleFullscreenHandler?: InputHandler;
  private toggleVisibilityStateHandler?: InputHandler;
  private rotateHandler?: InputHandler;
  private clickHandler?: InputHandler;
  private shortcutHandler?: InputHandler;

  // Editor handlers (Editor only)
  private editSelectionRotatorHandler?: InputHandler;
  private editorActivateHandler?: InputHandler;
  private blockEditorActivateHandler?: InputHandler;
  private editConfigActivateHandler?: InputHandler;
  private panelActivateHandler?: InputHandler;
  private toggleModelSelectorHandler?: InputHandler;

  // Pointer lock state
  private pointerLocked: boolean = false;

  constructor(canvas: HTMLCanvasElement, playerService: PlayerService, appContext: AppContext) {
    this.canvas = canvas;
    this.playerService = playerService;
    this.appContext = appContext;

    // Note: All handlers will be retrieved from InputService in initialize()
    // This makes WebInputController a pure binding layer

    logger.debug('WebInputController created');
  }

  /**
   * Setup key bindings
   */
  private setupKeyBindings(): void {
    // Movement bindings
    if (this.moveForwardHandler) {
      this.keyBindings.set('w', this.moveForwardHandler);
      this.keyBindings.set('W', this.moveForwardHandler);
    }
    if (this.moveBackwardHandler) {
      this.keyBindings.set('s', this.moveBackwardHandler);
      this.keyBindings.set('S', this.moveBackwardHandler);
    }
    if (this.moveLeftHandler) {
      this.keyBindings.set('a', this.moveLeftHandler);
      this.keyBindings.set('A', this.moveLeftHandler);
    }
    if (this.moveRightHandler) {
      this.keyBindings.set('d', this.moveRightHandler);
      this.keyBindings.set('D', this.moveRightHandler);
    }

    // Action bindings
    if (this.cycleMovementStateHandler) {
      this.keyBindings.set('f', this.cycleMovementStateHandler);
      this.keyBindings.set('F', this.cycleMovementStateHandler);
    }
    if (this.toggleViewModeHandler) {
      this.keyBindings.set('F5', this.toggleViewModeHandler);
    }
    if (this.toggleShortcutsHandler) {
      this.keyBindings.set('t', this.toggleShortcutsHandler);
      this.keyBindings.set('T', this.toggleShortcutsHandler);
    }
    if (this.toggleFullscreenHandler) {
      this.keyBindings.set('F6', this.toggleFullscreenHandler);
    }
    if (this.toggleVisibilityStateHandler) {
      this.keyBindings.set('F2', this.toggleVisibilityStateHandler);
    }

    // Editor-only key bindings
    if (this.editSelectionRotatorHandler) {
      this.keyBindings.set('.', this.editSelectionRotatorHandler);
    }
    if (this.editorActivateHandler) {
      this.keyBindings.set('/', this.editorActivateHandler);
    }
    if (this.panelActivateHandler) {
      this.keyBindings.set('p', this.panelActivateHandler);
      this.keyBindings.set('P', this.panelActivateHandler);
    }
    if (this.editConfigActivateHandler) {
      this.keyBindings.set('F9', this.editConfigActivateHandler);
    }
    if (this.blockEditorActivateHandler) {
      this.keyBindings.set('F10', this.blockEditorActivateHandler);
    }
    if (this.toggleModelSelectorHandler) {
      this.keyBindings.set('F8', this.toggleModelSelectorHandler);
    }

    // Space: Jump in Walk mode, Move up in Fly mode (handled dynamically)
    // Shift: Move down in Fly mode (handled dynamically)
    // . : Rotate selection mode (Editor only)
    // / : Activate selected block editor (Editor only)
    // p : Open panel navigation (Editor only)
    // F9: Open edit configuration (Editor only)
    // F10: Open block editor for selected block (Editor only)
  }

  /**
   * Initialize controller
   */
  initialize(): void {
    // Get all handlers from InputService
    const inputService = this.appContext.services.input;
    if (!inputService) {
      logger.warn('InputService not available');
      return;
    }

    // Retrieve all handlers from InputService
    this.clickHandler = inputService.getHandler('click');
    this.shortcutHandler = inputService.getHandler('shortcut');
    this.moveForwardHandler = inputService.getHandler('moveForward');
    this.moveBackwardHandler = inputService.getHandler('moveBackward');
    this.moveLeftHandler = inputService.getHandler('moveLeft');
    this.moveRightHandler = inputService.getHandler('moveRight');
    this.moveUpHandler = inputService.getHandler('moveUp');
    this.moveDownHandler = inputService.getHandler('moveDown');
    this.jumpHandler = inputService.getHandler('jump');
    this.cycleMovementStateHandler = inputService.getHandler('cycleMovementState');
    this.toggleViewModeHandler = inputService.getHandler('toggleViewMode');
    this.toggleShortcutsHandler = inputService.getHandler('toggleShortcuts');
    this.toggleFullscreenHandler = inputService.getHandler('toggleFullscreen');
    this.toggleVisibilityStateHandler = inputService.getHandler('toggleVisibilityState');
    this.rotateHandler = inputService.getHandler('rotate');

    // Editor handlers (only available in editor mode)
    if (__EDITOR__) {
      this.editSelectionRotatorHandler = inputService.getHandler('editSelectionRotator');
      this.editorActivateHandler = inputService.getHandler('editorActivate');
      this.blockEditorActivateHandler = inputService.getHandler('blockEditorActivate');
      this.editConfigActivateHandler = inputService.getHandler('editConfigActivate');
      this.panelActivateHandler = inputService.getHandler('panelActivate');
      this.toggleModelSelectorHandler = inputService.getHandler('toggleModelSelector');
    }

    // Build handlers array for update loop
    const handlerList = [
      this.clickHandler,
      this.shortcutHandler,
      this.moveForwardHandler,
      this.moveBackwardHandler,
      this.moveLeftHandler,
      this.moveRightHandler,
      this.moveUpHandler,
      this.moveDownHandler,
      this.jumpHandler,
      this.cycleMovementStateHandler,
      this.toggleViewModeHandler,
      this.toggleShortcutsHandler,
      this.toggleVisibilityStateHandler,
      this.rotateHandler,
    ];

    // Add editor handlers if available
    if (this.editSelectionRotatorHandler) {
      handlerList.push(this.editSelectionRotatorHandler);
    }
    if (this.editorActivateHandler) {
      handlerList.push(this.editorActivateHandler);
    }
    if (this.blockEditorActivateHandler) {
      handlerList.push(this.blockEditorActivateHandler);
    }
    if (this.editConfigActivateHandler) {
      handlerList.push(this.editConfigActivateHandler);
    }
    if (this.panelActivateHandler) {
      handlerList.push(this.panelActivateHandler);
    }

    // Filter out undefined handlers and build final list
    this.handlers = handlerList.filter((h): h is InputHandler => h !== undefined);

    // Setup key bindings (now that handlers are available)
    this.setupKeyBindings();

    // Add event listeners
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('keyup', this.onKeyUp);
    this.canvas.addEventListener('click', this.onCanvasClick);
    this.canvas.addEventListener('mousedown', this.onMouseDown);
    window.addEventListener('mouseup', this.onMouseUp); // On window to catch all mouse up events
    document.addEventListener('pointerlockchange', this.onPointerLockChange);
    document.addEventListener('mousemove', this.onMouseMove);

    // Activate rotation handler (always active for mouse look)
    if (this.rotateHandler) {
      this.rotateHandler.activate();
    }

    logger.debug('WebInputController initialized', {
      handlerCount: this.handlers.length,
    });
  }

  /**
   * Handle keydown event
   */
  private onKeyDown = (event: KeyboardEvent): void => {
    // Handle Space key dynamically based on movement mode
    if (event.key === ' ') {
      const mode = this.playerService.getMovementMode();
      logger.info('Space key pressed, movement mode:', { mode });

      // Check if SelectionService is in INTERACTIVE mode and has a target
      const selectService = this.appContext.services.select;
      if (selectService) {
        const selectMode = selectService.getAutoSelectMode();
        const selectedBlock = selectService.getCurrentSelectedBlock();
        const selectedEntity = selectService.getCurrentSelectedEntity();

        // If in INTERACTIVE mode and has a target, send interaction instead of jump
        if (selectMode === SelectMode.INTERACTIVE && (selectedBlock || selectedEntity)) {
          logger.info('Space key triggers interaction in INTERACTIVE mode', {
            hasBlock: !!selectedBlock,
            hasEntity: !!selectedEntity
          });

          const networkService = this.appContext.services.network;
          if (networkService) {
            if (selectedEntity) {
              // Send entity interaction (e.int.r)
              networkService.sendEntityInteraction(
                selectedEntity.id,
                'interact',
                undefined, // no clickType for 'interact' action
                {}
              );
              logger.info('Sent entity interaction for space key', { entityId: selectedEntity.id });
            } else if (selectedBlock) {
              // Check if block requires confirmation
              const confirmText = selectedBlock.block.metadata?.client?.confirm;
              if (confirmText) {
                logger.info('Block requires confirmation', { confirmText });
                const confirmed = window.confirm(confirmText);
                if (!confirmed) {
                  logger.info('User cancelled block interaction');
                  event.preventDefault();
                  return;
                }
                logger.info('User confirmed block interaction');
              }

              // Send block interaction (b.int)
              const pos = selectedBlock.block.position;
              networkService.sendBlockInteraction(
                pos.x,
                pos.y,
                pos.z,
                'interact',
                {}, // no additional params for 'interact' action
                selectedBlock.block.metadata?.id,
                selectedBlock.block.metadata?.groupId
              );
              logger.info('Sent block interaction for space key', { position: pos });
            }
          } else {
            logger.warn('NetworkService not available for space interaction');
          }

          event.preventDefault();
          return;
        }
      }

      // Only WALK and SPRINT modes: Jump
      if (mode === 'walk' || mode === 'sprint') {
        if (this.jumpHandler && !this.jumpHandler.isActive()) {
          logger.info('Activating jump handler');
          this.jumpHandler.activate();
          event.preventDefault();
        } else {
          logger.info('Jump handler not available or already active', {
            hasHandler: !!this.jumpHandler,
            isActive: this.jumpHandler?.isActive()
          });
        }
      } else {
        logger.info('Space key ignored - wrong movement mode', { mode });
      }
      return;
    }

    // Handle F key for cycling movement state
    if (event.key === 'f' || event.key === 'F') {
      if (this.cycleMovementStateHandler && !this.cycleMovementStateHandler.isActive()) {
        this.cycleMovementStateHandler.activate();
        event.preventDefault();
      }
      return;
    }

    // Handle number keys (1-9, 0) for shortcuts
    if (event.key >= '0' && event.key <= '9') {
      const shortcutNr = event.key === '0' ? 10 : parseInt(event.key, 10);
      this.handleShortcut(shortcutNr);
      event.preventDefault();
      return;
    }

    // Handle other keys via bindings
    const handler = this.keyBindings.get(event.key);
    if (handler && !handler.isActive()) {
      handler.activate();
      event.preventDefault();
    }
  };

  /**
   * Handle shortcut key press (1-9, 0)
   */
  private handleShortcut(shortcutNr: number): void {
    if (this.shortcutHandler) {
      this.shortcutHandler.activate(shortcutNr);
    } else {
      logger.warn('Shortcut handler not available');
    }
  };

  /**
   * Handle keyup event
   */
  private onKeyUp = (event: KeyboardEvent): void => {
    // Handle shortcut key release (number keys 0-9)
    if (event.key >= '0' && event.key <= '9') {
      if (this.shortcutHandler && this.shortcutHandler.isActive()) {
        this.shortcutHandler.deactivate();
        event.preventDefault();
      }
      return;
    }

    // Handle Space key dynamically based on movement mode
    if (event.key === ' ') {
      const mode = this.playerService.getMovementMode();
      if (mode === 'walk') {
        // Walk mode: Jump
        if (this.jumpHandler && this.jumpHandler.isActive()) {
          this.jumpHandler.deactivate();
          event.preventDefault();
        }
      } else if (mode === 'fly') {
        // Fly mode: Move up
        if (this.moveUpHandler && this.moveUpHandler.isActive()) {
          this.moveUpHandler.deactivate();
          event.preventDefault();
        }
      }
      return;
    }

    // Handle Shift key for Fly mode down movement
    if (event.key === 'Shift') {
      const mode = this.playerService.getMovementMode();
      if (mode === 'fly') {
        if (this.moveDownHandler && this.moveDownHandler.isActive()) {
          this.moveDownHandler.deactivate();
          event.preventDefault();
        }
      }
      return;
    }

    // Handle F key for cycling movement state
    if (event.key === 'f' || event.key === 'F') {
      if (this.cycleMovementStateHandler && this.cycleMovementStateHandler.isActive()) {
        this.cycleMovementStateHandler.deactivate();
        event.preventDefault();
      }
      return;
    }

    // Handle other keys via bindings
    const handler = this.keyBindings.get(event.key);
    if (handler && handler.isActive()) {
      handler.deactivate();
      event.preventDefault();
    }
  };

  /**
   * Handle canvas click (request pointer lock)
   */
  private onCanvasClick = (): void => {
    if (!this.pointerLocked) {
      this.canvas.requestPointerLock();
    }
  };

  /**
   * Handle mouse button down (for block and entity interactions)
   */
  private onMouseDown = (event: MouseEvent): void => {
    if (!this.pointerLocked) {
      return;
    }

    // Use ClickInputHandler from InputService
    if (this.clickHandler) {
      this.clickHandler.activate(event.button);
      event.preventDefault();
    } else {
      logger.warn('Click handler not available');
    }
  };

  /**
   * Handle mouse button up (for ending click shortcuts)
   */
  private onMouseUp = (event: MouseEvent): void => {
    logger.debug('Mouse up event received', {
      button: event.button,
      pointerLocked: this.pointerLocked,
      hasClickHandler: !!this.clickHandler
    });

    if (!this.pointerLocked) {
      logger.debug('Mouse up ignored - pointer not locked');
      return;
    }

    // Use ClickInputHandler from InputService
    if (this.clickHandler) {
      logger.debug('Calling clickHandler.deactivate()');
      this.clickHandler.deactivate();
      event.preventDefault();
    } else {
      logger.warn('Click handler not available for mouse up');
    }
  };

  /**
   * Handle pointer lock change
   */
  private onPointerLockChange = (): void => {
    this.pointerLocked = document.pointerLockElement === this.canvas;

    if (this.pointerLocked) {
      logger.debug('Pointer locked');
    } else {
      logger.debug('Pointer unlocked');
    }
  };

  /**
   * Handle mouse move (rotation)
   */
  private onMouseMove = (event: MouseEvent): void => {
    if (!this.pointerLocked) {
      return;
    }

    // Get mouse movement
    const deltaX = event.movementX || 0;
    const deltaY = event.movementY || 0;

    // Update rotation handler
    if (this.rotateHandler) {
      (this.rotateHandler as any).setDelta(deltaX, deltaY);
    }
  };

  /**
   * Get all handlers
   */
  getHandlers(): InputHandler[] {
    return this.handlers;
  }

  /**
   * Dispose controller
   */
  dispose(): void {
    // Remove event listeners
    window.removeEventListener('keydown', this.onKeyDown);
    window.removeEventListener('keyup', this.onKeyUp);
    this.canvas.removeEventListener('click', this.onCanvasClick);
    this.canvas.removeEventListener('mousedown', this.onMouseDown);
    window.removeEventListener('mouseup', this.onMouseUp);
    document.removeEventListener('pointerlockchange', this.onPointerLockChange);
    document.removeEventListener('mousemove', this.onMouseMove);

    // Exit pointer lock
    if (this.pointerLocked) {
      document.exitPointerLock();
    }

    // Deactivate all handlers
    for (const handler of this.handlers) {
      if (handler.isActive()) {
        handler.deactivate();
      }
    }

    logger.debug('WebInputController disposed');
  }
}
