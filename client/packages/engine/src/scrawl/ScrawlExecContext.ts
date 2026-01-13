import type { ScrawlExecContext as BaseScrawlExecContext } from '@nimbus/shared';
import type { AppContext } from '../AppContext';

/**
 * Extended execution context for scrawl scripts in the engine.
 * Extends the base context from shared with engine-specific references.
 */
export interface ScrawlExecContext extends BaseScrawlExecContext {
  /**
   * Reference to the AppContext.
   * Provides access to all services (network, chunk, render, etc.)
   */
  appContext: AppContext;

  /**
   * Reference to the ScrawlExecutor executing this script.
   * Allows access to script control methods (pause, cancel, emit events, etc.)
   */
  executor: ScrawlExecutor;

  /**
   * Current LOD level for this execution.
   * Used by StepLodSwitch to select appropriate effects.
   */
  lodLevel?: 'high' | 'medium' | 'low';

  /**
   * Script ID being executed (for debugging/logging)
   */
  scriptId?: string;

  /**
   * Whether this script execution is local (originated on this client)
   * or remote (received from server via multiplayer sync).
   *
   * - Local (true): Script was triggered by this client (player action, item use, etc.)
   * - Remote (false): Script was received from server (other player's action)
   *
   * Remote scripts should NOT activate client-specific features like:
   * - Player direction broadcast (receivePlayerDirection)
   * - Local input handling
   * - Client-only UI effects
   */
  isLocal?: boolean;
}

// Forward declaration to avoid circular dependency
export interface ScrawlExecutor {
  /** Cancel the script execution */
  cancel(): void;

  /** Pause the script execution */
  pause(): void;

  /** Resume the script execution */
  resume(): void;

  /** Emit an event */
  emit(eventName: string, payload?: any): void;

  /** Wait for an event */
  waitEvent(eventName: string, timeoutSec?: number): Promise<boolean>;

  /** Get a variable from the context */
  getVar(name: string): any;

  /** Set a variable in the context */
  setVar(name: string, value: any): void;

  /** Check if the script is cancelled */
  isCancelled(): boolean;

  /** Check if the script is paused */
  isPaused(): boolean;
}
