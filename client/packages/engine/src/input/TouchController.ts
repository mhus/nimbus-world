/**
 * TouchController - Mobile touch input controller
 *
 * Reads joystick state from TouchOverlayService each frame and
 * activates the appropriate input handlers.
 *
 * Touch zones (handled by TouchOverlayService):
 * - Left joystick: Movement (WASD equivalent)
 * - Right joystick: Camera rotation (mouse look equivalent)
 * - Double-tap right: Jump
 * - Single tap right (no drag): Primary action (click)
 */

import { getLogger } from '@nimbus/shared';
import type { InputController } from '../services/InputService';
import type { PlayerService } from '../services/PlayerService';
import type { AppContext } from '../AppContext';
import type { InputHandler } from './InputHandler';
import { TouchOverlayService } from '../services/TouchOverlayService';
import { RotateHandler } from './handlers/RotationHandlers';

const logger = getLogger('TouchController');

export class TouchController implements InputController {
  private canvas: HTMLCanvasElement;
  private playerService: PlayerService;
  private appContext: AppContext;

  private handlers: InputHandler[] = [];
  private touchOverlay: TouchOverlayService;

  // Handlers from InputService
  private moveForwardHandler?: InputHandler;
  private moveBackwardHandler?: InputHandler;
  private moveLeftHandler?: InputHandler;
  private moveRightHandler?: InputHandler;
  private jumpHandler?: InputHandler;
  private rotateHandler?: RotateHandler;
  private clickHandler?: InputHandler;
  private cycleMovementStateHandler?: InputHandler;
  private shortcutHandler?: InputHandler;
  private toggleViewModeHandler?: InputHandler;
  private toggleFullscreenHandler?: InputHandler;
  private toggleShortcutsHandler?: InputHandler;
  private panelActivateHandler?: InputHandler;

  private readonly movementThreshold = 0.15;
  private readonly yawSpeed = 2.0; // radians per second at full deflection (horizontal)
  private readonly maxPitchDeg = 60; // max pitch in degrees at full stick deflection

  constructor(canvas: HTMLCanvasElement, playerService: PlayerService, appContext: AppContext) {
    this.canvas = canvas;
    this.playerService = playerService;
    this.appContext = appContext;
    this.touchOverlay = new TouchOverlayService();
    logger.debug('TouchController created');
  }

  initialize(): void {
    const inputService = this.appContext.services.input;
    if (!inputService) {
      logger.warn('InputService not available');
      return;
    }

    // Retrieve handlers from InputService
    this.moveForwardHandler = inputService.getHandler('moveForward');
    this.moveBackwardHandler = inputService.getHandler('moveBackward');
    this.moveLeftHandler = inputService.getHandler('moveLeft');
    this.moveRightHandler = inputService.getHandler('moveRight');
    this.jumpHandler = inputService.getHandler('jump');
    this.rotateHandler = inputService.getHandler('rotate') as RotateHandler | undefined;
    this.clickHandler = inputService.getHandler('click');
    this.cycleMovementStateHandler = inputService.getHandler('cycleMovementState');
    this.shortcutHandler = inputService.getHandler('shortcut');
    this.toggleViewModeHandler = inputService.getHandler('toggleViewMode');
    this.toggleFullscreenHandler = inputService.getHandler('toggleFullscreen');
    this.toggleShortcutsHandler = inputService.getHandler('toggleShortcuts');
    this.panelActivateHandler = inputService.getHandler('panelActivate');

    // Build handlers array for update loop
    const handlerList = [
      this.moveForwardHandler,
      this.moveBackwardHandler,
      this.moveLeftHandler,
      this.moveRightHandler,
      this.jumpHandler,
      this.rotateHandler as InputHandler | undefined,
      this.clickHandler,
      this.cycleMovementStateHandler,
      this.shortcutHandler,
    ];
    this.handlers = handlerList.filter((h): h is InputHandler => h !== undefined);

    // Activate rotation handler (always active for delta-based rotation)
    this.rotateHandler?.activate();

    // Initialize touch overlay UI
    this.touchOverlay.initialize();

    logger.debug('TouchController initialized', { handlerCount: this.handlers.length });
  }

  dispose(): void {
    this.touchOverlay.dispose();
    this.deactivateAllMovement();
    logger.debug('TouchController disposed');
  }

  getHandlers(): InputHandler[] {
    return this.handlers;
  }

  /**
   * Called each frame by InputService.update().
   * We hook into the rotateHandler's update cycle to poll the overlay state.
   */
  update(deltaTime: number): void {
    this.pollMovement();
    this.pollRotation(deltaTime);
    this.pollActions();
  }

  private pollMovement(): void {
    const state = this.touchOverlay.getMovementState();

    if (!state.active || state.strength < this.movementThreshold) {
      this.deactivateAllMovement();
      return;
    }

    // dy negative = forward (screen up = forward)
    const forward = Math.max(0, -state.dy);
    const backward = Math.max(0, state.dy);
    const right = Math.max(0, state.dx);
    const left = Math.max(0, -state.dx);

    if (forward > this.movementThreshold) {
      this.moveForwardHandler?.activate(forward);
    } else {
      this.moveForwardHandler?.deactivate();
    }
    if (backward > this.movementThreshold) {
      this.moveBackwardHandler?.activate(backward);
    } else {
      this.moveBackwardHandler?.deactivate();
    }
    if (right > this.movementThreshold) {
      this.moveRightHandler?.activate(right);
    } else {
      this.moveRightHandler?.deactivate();
    }
    if (left > this.movementThreshold) {
      this.moveLeftHandler?.activate(left);
    } else {
      this.moveLeftHandler?.deactivate();
    }
  }

  private pollRotation(deltaTime: number): void {
    const state = this.touchOverlay.getRotationState();
    if (!state.active || state.strength < this.movementThreshold) return;

    // Yaw (horizontal): continuous rotation - stick held right = keep turning right
    const yawPixelsPerSecond = this.yawSpeed / 0.002; // convert radians/s to pixels/s
    const deltaX = state.dx * yawPixelsPerSecond * deltaTime;

    // Pitch (vertical): absolute position - stick position maps to target pitch angle
    // dy: -1 = up (look up = negative pitch), +1 = down (look down = positive pitch)
    const targetPitchDeg = state.dy * this.maxPitchDeg;
    const cameraService = this.appContext.services.camera;
    const currentPitchDeg = cameraService?.getCameraPitch() ?? 0;
    const pitchDiffDeg = targetPitchDeg - currentPitchDeg;
    // Convert degree diff to the "pixel-like" value that RotateHandler expects
    const pitchDiffRad = pitchDiffDeg * (Math.PI / 180);
    const deltaY = pitchDiffRad / 0.002;

    this.rotateHandler?.setDelta(deltaX, deltaY);
  }

  private pollActions(): void {
    if (this.touchOverlay.consumeJumpRequest()) {
      this.jumpHandler?.activate();
    }
    if (this.touchOverlay.consumeTapRequest()) {
      this.clickHandler?.activate(0);
      setTimeout(() => this.clickHandler?.deactivate(), 50);
    }
    if (this.touchOverlay.consumeMovementToggleRequest()) {
      this.cycleMovementStateHandler?.activate();
    }

    // Shortcut keys 1-9
    const shortcutNr = this.touchOverlay.consumeShortcutRequest();
    if (shortcutNr !== null) {
      this.shortcutHandler?.activate(shortcutNr);
      setTimeout(() => this.shortcutHandler?.deactivate(), 50);
    }

    // Shortcut display toggle (T key equivalent)
    if (this.touchOverlay.consumeShortcutToggleRequest()) {
      this.toggleShortcutsHandler?.activate();
    }

    // Menu actions
    const menuAction = this.touchOverlay.consumeMenuAction();
    if (menuAction) {
      switch (menuAction) {
        case 'message':
          this.appContext.services.notification?.toggleInputPanel();
          break;
        case 'panel':
          this.panelActivateHandler?.activate();
          break;
        case 'viewToggle':
          this.toggleViewModeHandler?.activate();
          break;
        case 'fullscreen':
          this.toggleFullscreenHandler?.activate();
          break;
      }
    }
  }

  private deactivateAllMovement(): void {
    this.moveForwardHandler?.deactivate();
    this.moveBackwardHandler?.deactivate();
    this.moveLeftHandler?.deactivate();
    this.moveRightHandler?.deactivate();
  }
}
