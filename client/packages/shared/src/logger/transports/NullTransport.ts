/**
 * Null transport for logger
 * Discards all log entries (useful for disabling logging)
 */

import type { LogTransport } from '../LogEntry';

/**
 * Creates a null transport that discards all log entries
 * Useful for disabling logging in production or testing
 */
export function createNullTransport(): LogTransport {
  return (): void => {
    // Do nothing - discard log entry
  };
}
