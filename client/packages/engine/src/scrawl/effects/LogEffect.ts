/**
 * LogEffect - Simple logging effect for testing and debugging
 *
 * Outputs a message to the log with optional context data.
 * Useful for debugging scrawl scripts and understanding execution flow.
 */

import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';

const logger = getLogger('LogEffect');

/**
 * Options for LogEffect
 */
export interface LogEffectOptions {
  /** Message to log */
  message: string;

  /** Log level: 'debug' | 'info' | 'warn' | 'error' */
  level?: 'debug' | 'info' | 'warn' | 'error';

  /** Additional data to include in the log */
  data?: any;
}

/**
 * LogEffect - Outputs a message to the log
 *
 * Usage in scrawl script:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "log",
 *   "ctx": {
 *     "message": "Hello from scrawl script!",
 *     "level": "info",
 *     "data": { "foo": "bar" }
 *   }
 * }
 * ```
 */
export class LogEffect extends ScrawlEffectHandler<LogEffectOptions> {
  execute(ctx: ScrawlExecContext): void {
    const { message, level = 'info', data } = this.options;

    // Build log context
    const logContext: any = {
      scriptId: ctx.scriptId,
    };

    // Add source info if present (from vars)
    const source = ctx.vars?.source;
    if (source) {
      logContext.source = {
        entityId: source.entityId,
        position: source.position,
      };
    }

    // Add targets info if present (from vars)
    const targets = ctx.vars?.targets;
    if (targets && targets.length > 0) {
      logContext.targets = targets.map((p: any) => ({
        entityId: p.entityId,
        position: p.position,
      }));
    }

    // Add custom data if provided
    if (data) {
      logContext.data = data;
    }

    // Log with appropriate level
    switch (level) {
      case 'debug':
        logger.debug(message, logContext);
        break;
      case 'info':
        logger.debug(message, logContext);
        break;
      case 'warn':
        logger.warn(message, logContext);
        break;
      case 'error':
        logger.error(message, logContext);
        break;
      default:
        logger.debug(message, logContext);
    }
  }
}
