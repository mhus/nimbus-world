/**
 * Log entry structure
 */

import type { LogLevel } from './LogLevel';

/**
 * Single log entry
 */
export interface LogEntry {
  /** Log level */
  level: LogLevel;

  /** Logger name/category */
  name: string;

  /** Log message */
  message: string;

  /** Timestamp (milliseconds since epoch) */
  timestamp: number;

  /** Optional data/context */
  data?: any;

  /** Optional error object */
  error?: Error;

  /** Optional stack trace */
  stack?: string;
}

/**
 * Log entry formatter function type
 */
export type LogFormatter = (entry: LogEntry) => string;

/**
 * Log transport function type
 */
export type LogTransport = (entry: LogEntry) => void;
