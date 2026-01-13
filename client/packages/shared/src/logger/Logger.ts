/**
 * Logger implementation
 */

import { LogLevel } from './LogLevel';
import type { LogEntry } from './LogEntry';
import { TransportManager } from './TransportManager';

/**
 * Logger configuration
 */
export interface LoggerConfig {
  /** Minimum log level to process */
  minLevel: LogLevel;

  /** Enable stack traces for errors */
  includeStack?: boolean;
}

/**
 * Logger instance
 */
export class Logger {
  private name: string;
  private config: LoggerConfig;

  constructor(name: string, config: Partial<LoggerConfig> = {}) {
    this.name = name;
    this.config = {
      minLevel: LogLevel.INFO,
      includeStack: true,
      ...config,
    };
  }

  /**
   * Set minimum log level
   */
  setLevel(level: LogLevel): void {
    this.config.minLevel = level;
  }

  /**
   * Get current log level
   */
  getLevel(): LogLevel {
    return this.config.minLevel;
  }

  /**
   * Check if level is enabled
   */
  isLevelEnabled(level: LogLevel): boolean {
    return level <= this.config.minLevel;
  }

  /**
   * Log at specified level
   */
  log(level: LogLevel, message: string, data?: any, error?: Error): void {
    if (!this.isLevelEnabled(level)) {
      return;
    }

    const entry: LogEntry = {
      level,
      name: this.name,
      message,
      timestamp: Date.now(),
      data,
      error,
    };

    // Add stack trace for errors
    if (error && this.config.includeStack) {
      entry.stack = error.stack;
    }

    // Use central transport manager to log entry
    TransportManager.log(entry);
  }

  /**
   * Fatal error (system must stop)
   */
  fatal(message: string, data?: any, error?: Error): void {
    this.log(LogLevel.FATAL, message, data, error);
  }

  /**
   * Error message
   */
  error(message: string, data?: any, error?: Error): void {
    this.log(LogLevel.ERROR, message, data, error);
  }

  /**
   * Warning message
   */
  warn(message: string, data?: any): void {
    this.log(LogLevel.WARN, message, data);
  }

  /**
   * Info message
   */
  info(message: string, data?: any): void {
    this.log(LogLevel.INFO, message, data);
  }

  /**
   * Debug message
   */
  debug(message: string, data?: any): void {
    this.log(LogLevel.DEBUG, message, data);
  }

  /**
   * Trace message
   */
  trace(message: string, data?: any): void {
    this.log(LogLevel.TRACE, message, data);
  }
}
