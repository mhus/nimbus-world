/**
 * Log levels for the Nimbus logging system
 */

export enum LogLevel {
  /** Fatal errors - system must stop */
  FATAL = 0,

  /** Critical errors - system unstable */
  ERROR = 1,

  /** Warnings - may cause issues */
  WARN = 2,

  /** Informational messages */
  INFO = 3,

  /** Debug information */
  DEBUG = 4,

  /** Trace/verbose logging */
  TRACE = 5,
}

/**
 * Log level names for display
 */
export const LogLevelNames: Record<LogLevel, string> = {
  [LogLevel.FATAL]: 'FATAL',
  [LogLevel.ERROR]: 'ERROR',
  [LogLevel.WARN]: 'WARN',
  [LogLevel.INFO]: 'INFO',
  [LogLevel.DEBUG]: 'DEBUG',
  [LogLevel.TRACE]: 'TRACE',
};

/**
 * Parse log level from string
 */
export function parseLogLevel(level: string): LogLevel {
  const upper = level.trim().toUpperCase();
  switch (upper) {
    case 'FATAL':
      return LogLevel.FATAL;
    case 'ERROR':
      return LogLevel.ERROR;
    case 'WARN':
    case 'WARNING':
      return LogLevel.WARN;
    case 'INFO':
      return LogLevel.INFO;
    case 'DEBUG':
      return LogLevel.DEBUG;
    case 'TRACE':
      return LogLevel.TRACE;
    default:
      return LogLevel.INFO;
  }
}
