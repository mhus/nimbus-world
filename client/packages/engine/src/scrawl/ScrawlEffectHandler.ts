import { getLogger } from '@nimbus/shared';
import type { ScrawlExecContext } from './ScrawlExecContext';

const logger = getLogger('ScrawlEffectHandler');

/**
 * Dependencies injected into effect handlers
 */
export interface EffectDeps {
  /** Logging function */
  log?: (...args: any[]) => void;

  /** Current time provider (in seconds) */
  now?: () => number;

  /** Additional dependencies can be added here */
  [key: string]: any;
}

/**
 * Abstract base class for effect handlers.
 * Effect handlers are responsible for executing specific effects (VFX, SFX, Light, etc.)
 * in the 3D world.
 *
 * @template O Options type for this effect handler
 */
export abstract class ScrawlEffectHandler<O = unknown> {
  protected started = false;

  constructor(
    protected readonly deps: EffectDeps,
    protected readonly options: O
  ) {}

  /**
   * Execute the effect with the given context.
   * This is the main entry point for effect execution.
   *
   * @param ctx Execution context containing actor, patients, and variables
   */
  abstract execute(ctx: ScrawlExecContext): void | Promise<void>;

  /**
   * Start the effect (optional, for effects that need initialization).
   * Called automatically by execute() if not already started.
   */
  start?(): void | Promise<void>;

  /**
   * Stop/cleanup the effect (optional, for effects that need cleanup).
   */
  stop?(): void | Promise<void>;

  /**
   * Gives whether this effect runs continuously (steady) or completes immediately (one-shot).
   *
   * Steady Effects:
   * - execute() starts the effect which runs until stop() is called
   * - Examples: sound:loop, beam:follow, continuous particle emitters
   *
   * One-Shot Effects:
   * - execute() performs an action and returns immediately
   * - Examples: log, command, spawn single particle burst
   *
   * @returns true if steady effect, false if one-shot (default: false)
   */
  isSteadyEffect(): boolean {
    return false;
  }

  /**
   * Indicates whether the effect is currently running.
   * Only relevant for steady effects.
   *
   * @returns true if effect is still active, false otherwise (default: false)
   */
  isRunning(): boolean {
    return false;
  }

  /**
   * Called when a parameter changes during effect execution.
   * Effects can optionally implement this to react to dynamic parameter updates.
   *
   * Only active during StepUntil loops - StepWhile does not trigger this.
   *
   * @param paramName Name of the parameter (e.g. 'targetPos', 'volume')
   * @param value New value of the parameter
   * @param ctx Current execution context with updated values
   */
  onParameterChanged?(
    paramName: string,
    value: any,
    ctx: ScrawlExecContext
  ): void;

  /**
   * Helper to log messages
   */
  protected logInfo(message: string, ...args: any[]): void {
    if (this.deps.log) {
      this.deps.log(message, ...args);
    } else {
      logger.debug(message, ...args);
    }
  }

  /**
   * Helper to log errors
   */
  protected logError(message: string, error?: any): void {
    logger.error(message, { error });
  }

  /**
   * Get current time in seconds
   */
  protected now(): number {
    return this.deps.now ? this.deps.now() : performance.now() / 1000;
  }
}

/**
 * Constructor type for effect handler classes
 */
export interface EffectHandlerConstructor<
  O = unknown,
  H extends ScrawlEffectHandler<O> = ScrawlEffectHandler<O>
> {
  new (deps: EffectDeps, options: O): H;
}
