/**
 * Rotation Input Handlers
 *
 * Handles camera rotation (mouse look).
 */

import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';

/**
 * Rotate (Look) Handler
 *
 * Handles mouse look rotation
 */
export class RotateHandler extends InputHandler {
  private deltaYaw: number = 0;
  private deltaPitch: number = 0;
  private readonly sensitivity: number = 0.002; // Mouse sensitivity

  /**
   * Set rotation delta from mouse movement
   *
   * @param deltaX Mouse X movement in pixels
   * @param deltaY Mouse Y movement in pixels
   */
  setDelta(deltaX: number, deltaY: number): void {
    this.deltaYaw = deltaX * this.sensitivity;
    this.deltaPitch = deltaY * this.sensitivity;
  }

  protected onActivate(value: number): void {
    // Activation handled in update
  }

  protected onDeactivate(): void {
    // Deactivation handled in update
  }

  protected onUpdate(deltaTime: number, value: number): void {
    if (this.deltaYaw !== 0 || this.deltaPitch !== 0) {
      this.playerService.rotate(this.deltaPitch, this.deltaYaw);

      // Reset deltas after applying
      this.deltaYaw = 0;
      this.deltaPitch = 0;
    }
  }
}
