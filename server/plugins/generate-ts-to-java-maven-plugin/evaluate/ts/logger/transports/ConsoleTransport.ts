/**
 * Console transport for logger
 * Outputs log entries to browser/node console with colored output
 */

import type { LogEntry, LogTransport } from '../LogEntry';
import { LogLevel, LogLevelNames } from '../LogLevel';

/**
 * Console transport options
 */
export interface ConsoleTransportOptions {
  /** Include timestamp in output */
  includeTimestamp?: boolean;

  /** Include stack traces for errors */
  includeStack?: boolean;

}

/**
 * Creates a console transport
 */
export function createConsoleTransport(
  options: ConsoleTransportOptions = {}
): LogTransport {
  const { includeTimestamp = true, includeStack = true } = options;

  return (entry: LogEntry): void => {
    const formatted = formatLogEntry(entry, includeTimestamp, includeStack);

    // Use appropriate console method based on log level
    switch (entry.level) {
      case LogLevel.FATAL:
      case LogLevel.ERROR:
        console.error(formatted);
        break;
      case LogLevel.WARN:
        console.warn(formatted);
        break;
      case LogLevel.INFO:
        console.info(formatted);
        break;
      case LogLevel.DEBUG:
      case LogLevel.TRACE:
        console.debug(formatted);
        break;
      default:
        console.log(formatted);
    }
  };
}

/**
 * Format log entry for console output
 */
function formatLogEntry(
  entry: LogEntry,
  includeTimestamp: boolean,
  includeStack: boolean
): string {
  const level = LogLevelNames[entry.level].padEnd(5);
  const timestamp = includeTimestamp
    ? `[${new Date(entry.timestamp).toISOString()}] `
    : '';

  let message = `${timestamp}[${level}] [${entry.name}] ${entry.message}`;

  if (entry.data !== undefined) {
    try {
      message += `\n  Data: ${JSON.stringify(entry.data, null, 2)}`;
    } catch (error) {
      // Handle circular references
      message += `\n  Data: [Circular or non-serializable]`;
    }
  }

  if (entry.error) {
    message += `\n  Error: ${entry.error.message}`;
    if (entry.stack && includeStack) {
      message += `\n  Stack: ${entry.stack}`;
    }
  }

  return message;
}
