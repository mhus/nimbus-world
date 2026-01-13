/**
 * LogLevelCommand - Set log level for a logger or all loggers
 */

import { CommandHandler } from './CommandHandler';
import { LoggerFactory, LogLevel, getLogger } from '@nimbus/shared';

const logger = getLogger('LogLevelCommand');

/**
 * LogLevel command - Controls logging verbosity
 *
 * Usage:
 *   loglevel                    - Show current default level and all logger levels
 *   loglevel debug              - Set default level to DEBUG
 *   loglevel PhysicsService 0   - Set PhysicsService to DEBUG (0)
 *   loglevel PhysicsService debug - Set PhysicsService to DEBUG (by name)
 */
export class LogLevelCommand extends CommandHandler {
  name(): string {
    return 'loglevel';
  }

  description(): string {
    return 'Set log level (trace|debug|info|warn|error|fatal) for specific logger or default';
  }

  execute(parameters: any[]): any {
    // No parameters - show current levels
    if (parameters.length === 0) {
      return {
        hint: 'Usage: loglevel <logger-name> <level> OR loglevel <level>',
        examples: [
          'loglevel debug          - Set default to DEBUG',
          'loglevel PhysicsService 0  - Set PhysicsService to DEBUG (0)',
          'loglevel PhysicsService debug - Set PhysicsService to DEBUG',
        ],
        levels: {
          TRACE: 0,
          DEBUG: 1,
          INFO: 2,
          WARN: 3,
          ERROR: 4,
          FATAL: 5,
        },
      };
    }

    // One parameter - set default level
    if (parameters.length === 1) {
      const level = this.parseLevel(parameters[0]);
      if (level === null) {
        return {
          error: `Invalid log level: ${parameters[0]}`,
          valid: 'trace, debug, info, warn, error, fatal (or 0-5)',
        };
      }

      LoggerFactory.setDefaultLevel(level);
      return `Default log level set to ${this.getLevelName(level)} (${level})`;
    }

    // Two parameters - set specific logger level
    if (parameters.length === 2) {
      const loggerName = parameters[0];
      const level = this.parseLevel(parameters[1]);

      if (level === null) {
        return {
          error: `Invalid log level: ${parameters[1]}`,
          valid: 'trace, debug, info, warn, error, fatal (or 0-5)',
        };
      }

      logger.debug(`[LogLevelCommand] Setting '${loggerName}' to level ${level}`);
      LoggerFactory.setLoggerLevel(loggerName, level);

      // Verify it was set
      const loggerNames = LoggerFactory.getLoggerNames();
      logger.debug(`[LogLevelCommand] Existing loggers:`, loggerNames);

      return `Logger '${loggerName}' set to ${this.getLevelName(level)} (${level})`;
    }

    return {
      error: 'Invalid number of parameters',
      usage: 'loglevel [logger-name] <level>',
    };
  }

  /**
   * Parse log level from string or number
   */
  private parseLevel(value: any): LogLevel | null {
    // Try as number first
    if (typeof value === 'number') {
      if (value >= 0 && value <= 5) {
        return value as LogLevel;
      }
      return null;
    }

    // Try as string
    const str = String(value).toUpperCase();
    switch (str) {
      case 'TRACE':
      case '0':
        return LogLevel.TRACE;
      case 'DEBUG':
      case '1':
        return LogLevel.DEBUG;
      case 'INFO':
      case '2':
        return LogLevel.INFO;
      case 'WARN':
      case 'WARNING':
      case '3':
        return LogLevel.WARN;
      case 'ERROR':
      case '4':
        return LogLevel.ERROR;
      case 'FATAL':
      case '5':
        return LogLevel.FATAL;
      default:
        return null;
    }
  }

  /**
   * Get level name from LogLevel enum
   */
  private getLevelName(level: LogLevel): string {
    switch (level) {
      case LogLevel.TRACE:
        return 'TRACE';
      case LogLevel.DEBUG:
        return 'DEBUG';
      case LogLevel.INFO:
        return 'INFO';
      case LogLevel.WARN:
        return 'WARN';
      case LogLevel.ERROR:
        return 'ERROR';
      case LogLevel.FATAL:
        return 'FATAL';
      default:
        return 'UNKNOWN';
    }
  }
}
