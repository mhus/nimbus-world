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

  private readonly movementThreshold = 0.15;
  private readonly rotationSpeed = 3.0; // radians per second at full deflection

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

    // Build handlers array for update loop
    // Note: we add a virtual handler for polling overlay state
    const handlerList = [
      this.moveForwardHandler,
      this.moveBackwardHandler,
      this.moveLeftHandler,
      this.moveRightHandler,
      this.jumpHandler,
      this.rotateHandler as InputHandler | undefined,
      this.clickHandler,
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

    // Continuous rotation: stick position × speed × deltaTime → pixels of "virtual mouse movement"
    // RotateHandler.setDelta expects pixel-like values (multiplied internally by sensitivity 0.002)
    const pixelsPerSecond = this.rotationSpeed / 0.002; // convert radians/s to pixels/s
    const deltaX = state.dx * pixelsPerSecond * deltaTime;
    const deltaY = state.dy * pixelsPerSecond * deltaTime;
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
  }

  private deactivateAllMovement(): void {
    this.moveForwardHandler?.deactivate();
    this.moveBackwardHandler?.deactivate();
    this.moveLeftHandler?.deactivate();
    this.moveRightHandler?.deactivate();
  }
}
