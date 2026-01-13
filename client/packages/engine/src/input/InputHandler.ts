/**
 * InputHandler - Base class for input actions
 *
 * Handles specific player actions like movement, rotation, jumping, etc.
 */

import { getLogger } from '@nimbus/shared';
import type { PlayerService } from '../services/PlayerService';
import type { AppContext } from '../AppContext';

const logger = getLogger('InputHandler');

/**
 * Input action state
 */
export interface InputState {
  /** Is this action currently active? */
  active: boolean;

  /** Value for continuous inputs (0-1 for analog, -1 to 1 for axes) */
  value?: number;
}

/**
 * Base InputHandler class
 *
 * Subclasses implement specific actions (movement, rotation, etc.)
 */
export abstract class InputHandler {
  protected playerService: PlayerService;
  protected appContext?: AppContext;
  protected state: InputState = { active: false, value: 0 };

  constructor(playerService: PlayerService, appContext?: AppContext) {
    this.playerService = playerService;
    this.appContext = appContext;
  }

  /**
   * Handle input activation
   *
   * @param value Optional value for continuous inputs
   */
  activate(value?: number): void {
    this.state.active = true;
    this.state.value = value ?? 1.0;
    this.onActivate(this.state.value);
  }

  /**
   * Handle input deactivation
   */
  deactivate(): void {
    this.state.active = false;
    this.state.value = 0;
    this.onDeactivate();
  }

  /**
   * Update handler (called each frame)
   *
   * @param deltaTime Time since last frame in seconds
   */
  update(deltaTime: number): void {
    if (this.state.active) {
      this.onUpdate(deltaTime, this.state.value ?? 1.0);
    }
  }

  /**
   * Check if handler is active
   */
  isActive(): boolean {
    return this.state.active;
  }

  /**
   * Called when input is activated
   */
  protected abstract onActivate(value: number): void;

  /**
   * Called when input is deactivated
   */
  protected abstract onDeactivate(): void;

  /**
   * Called each frame while input is active
   */
  protected abstract onUpdate(deltaTime: number, value: number): void;
}
