/**
 * Central Exception Handler
 *
 * Provides centralized exception handling and logging.
 * All caught exceptions should be passed through this handler.
 *
 * Usage:
 * ```typescript
 * try {
 *   // risky operation
 * } catch (error) {
 *   ExceptionHandler.handle(error, 'OperationName', { context: 'data' });
 *   // or rethrow:
 *   throw ExceptionHandler.handleAndRethrow(error, 'OperationName', { context: 'data' });
 * }
 * ```
 */

import { getLogger } from '../logger/LoggerFactory';

const logger = getLogger('ExceptionHandler');

/**
 * Custom error handler function type
 * Can be registered to react to exceptions (e.g., show error dialog)
 */
export type ErrorHandler = (
  error: Error,
  context: string,
  data?: any
) => void;

/**
 * Central exception handler
 */
export class ExceptionHandler {
  private static customHandler: ErrorHandler | null = null;

  /**
   * Handle an exception: log it and optionally call custom handler
   *
   * @param error - The error to handle
   * @param context - Context where the error occurred (e.g., 'FileLogTransport.initialize')
   * @param data - Additional context data
   */
  static handle(error: unknown, context: string, data?: any): void {
    const errorObj = this.normalizeError(error);
    console.error(error);

    // Log the error
    logger.error(`Exception in ${context}: ${errorObj.message}`, data, errorObj);

    // Call custom handler if registered
    if (this.customHandler) {
      try {
        this.customHandler(errorObj, context, data);
      } catch (handlerError) {
        // Don't let custom handler errors break exception handling
        logger.error(
          'Custom error handler threw exception',
          { context, originalError: errorObj.message },
          this.normalizeError(handlerError)
        );
      }
    }
  }

  /**
   * Handle an exception and rethrow it
   *
   * @param error - The error to handle
   * @param context - Context where the error occurred
   * @param data - Additional context data
   * @returns The normalized error (for throwing)
   */
  static handleAndRethrow(
    error: unknown,
    context: string,
    data?: any
  ): Error {
    this.handle(error, context, data);
    return this.normalizeError(error);
  }

  /**
   * Register a custom error handler
   * Can be used to show error dialogs, send error reports, etc.
   *
   * @param handler - Custom error handler function
   */
  static registerHandler(handler: ErrorHandler | null): void {
    this.customHandler = handler;
    logger.info(
      handler
        ? 'Custom error handler registered'
        : 'Custom error handler unregistered'
    );
  }

  /**
   * Get the currently registered custom handler
   */
  static getHandler(): ErrorHandler | null {
    return this.customHandler;
  }

  /**
   * Normalize any value to an Error object
   *
   * @param error - The error to normalize (can be anything)
   * @returns Normalized Error object
   */
  private static normalizeError(error: unknown): Error {
    if (error instanceof Error) {
      return error;
    }

    if (typeof error === 'string') {
      return new Error(error);
    }

    if (typeof error === 'object' && error !== null) {
      const message =
        'message' in error && typeof error.message === 'string'
          ? error.message
          : JSON.stringify(error);
      return new Error(message);
    }

    return new Error(String(error));
  }

  /**
   * Wrap an async function with exception handling
   *
   * @param fn - Async function to wrap
   * @param context - Context name for error logging
   * @returns Wrapped function that handles exceptions
   */
  static wrapAsync<T extends any[], R>(
    fn: (...args: T) => Promise<R>,
    context: string
  ): (...args: T) => Promise<R> {
    return async (...args: T): Promise<R> => {
      try {
        return await fn(...args);
      } catch (error) {
        throw this.handleAndRethrow(error, context, { args });
      }
    };
  }

  /**
   * Wrap a sync function with exception handling
   *
   * @param fn - Function to wrap
   * @param context - Context name for error logging
   * @returns Wrapped function that handles exceptions
   */
  static wrap<T extends any[], R>(
    fn: (...args: T) => R,
    context: string
  ): (...args: T) => R {
    return (...args: T): R => {
      try {
        return fn(...args);
      } catch (error) {
        throw this.handleAndRethrow(error, context, { args });
      }
    };
  }
}
