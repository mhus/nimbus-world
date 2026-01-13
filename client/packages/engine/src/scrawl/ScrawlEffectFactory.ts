import { getLogger, ExceptionHandler } from '@nimbus/shared';
import type {
  ScrawlEffectHandler,
  EffectHandlerConstructor,
  EffectDeps,
} from './ScrawlEffectHandler';
import type { ScrawlExecContext } from './ScrawlExecContext';

const logger = getLogger('ScrawlEffectFactory');

/**
 * Registry and factory for effect handlers.
 * Manages effect handler registration and creates new instances on demand.
 */
export class ScrawlEffectRegistry {
  private map = new Map<string, EffectHandlerConstructor<any>>();

  /**
   * Register an effect handler class
   * @param key Effect key/ID
   * @param ctor Effect handler constructor
   * @throws Error if key is already registered
   */
  register<O>(key: string, ctor: EffectHandlerConstructor<O>): this {
    if (this.map.has(key)) {
      throw new Error(`Effect key already registered: ${key}`);
    }
    this.map.set(key, ctor);
    logger.debug(`Registered effect handler: ${key}`);
    return this;
  }

  /**
   * Get an effect handler constructor by key
   * @param key Effect key/ID
   * @throws Error if key is not found
   */
  get<O>(key: string): EffectHandlerConstructor<O> {
    const ctor = this.map.get(key);
    if (!ctor) {
      throw new Error(`Effect key not found: ${key}`);
    }
    return ctor;
  }

  /**
   * Check if an effect handler is registered
   * @param key Effect key/ID
   */
  has(key: string): boolean {
    return this.map.has(key);
  }

  /**
   * Get all registered effect keys
   */
  getKeys(): string[] {
    return Array.from(this.map.keys());
  }
}

/**
 * Factory for creating effect handler instances.
 * Always creates new instances (no caching/pooling).
 */
export class ScrawlEffectFactory {
  constructor(
    private readonly registry: ScrawlEffectRegistry,
    private readonly deps: EffectDeps
  ) {}

  /**
   * Create a new effect handler instance
   * @param key Effect key/ID
   * @param options Effect options
   * @returns New effect handler instance
   */
  create<O, H extends ScrawlEffectHandler<O> = ScrawlEffectHandler<O>>(
    key: string,
    options: O
  ): H {
    try {
      const Ctor = this.registry.get<O>(key) as EffectHandlerConstructor<O, H>;
      return new Ctor(this.deps, options);
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlEffectFactory.create',
        { key, options }
      );
    }
  }

  /**
   * Create and execute an effect handler
   * @param key Effect key/ID
   * @param options Effect options
   * @param ctx Execution context
   * @returns Effect handler instance
   */
  async play<O>(
    key: string,
    options: O,
    ctx: ScrawlExecContext
  ): Promise<ScrawlEffectHandler<O>> {
    try {
      const handler = this.create<O>(key, options);
      logger.debug(`Playing effect: ${key}`, { options });

      const result = handler.execute(ctx);
      if (result instanceof Promise) {
        await result;
      }

      return handler;
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ScrawlEffectFactory.play',
        { key, options, scriptId: ctx.scriptId }
      );
    }
  }

  /**
   * Check if an effect is registered
   * @param key Effect key/ID
   */
  has(key: string): boolean {
    return this.registry.has(key);
  }

  /**
   * Get all registered effect keys
   */
  getEffectKeys(): string[] {
    return this.registry.getKeys();
  }
}
