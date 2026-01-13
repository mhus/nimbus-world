/**
 * Movement Input Handlers
 *
 * Handles player movement actions (forward, backward, left, right).
 */

import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';

/**
 * Move Forward Handler
 */
export class MoveForwardHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value, not pre-calculated distance
    // MovementResolver will handle speed and deltaTime
    this.playerService.moveForward(value);
  }
}

/**
 * Move Backward Handler
 */
export class MoveBackwardHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value
    this.playerService.moveForward(-value);
  }
}

/**
 * Move Left Handler
 */
export class MoveLeftHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value
    this.playerService.moveRight(-value);
  }
}

/**
 * Move Right Handler
 */
export class MoveRightHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value
    this.playerService.moveRight(value);
  }
}

/**
 * Move Up Handler (Fly mode only)
 */
export class MoveUpHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value
    this.playerService.moveUp(value);
  }
}

/**
 * Move Down Handler (Fly mode only)
 */
export class MoveDownHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // NEW PHYSICS: Pass normalized input value
    this.playerService.moveUp(-value);
  }
}
