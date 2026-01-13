/**
 * Central transport manager for logger
 * Manages transports that can be swapped at runtime
 */

import type { LogEntry, LogTransport } from './LogEntry';
import { createConsoleTransport } from './transports/ConsoleTransport';

/**
 * Transport manager configuration
 */
export interface TransportManagerConfig {
  /** Include timestamp in logs */
  includeTimestamp?: boolean;

  /** Include stack traces for errors */
  includeStack?: boolean;
}

/**
 * Central transport manager (singleton)
 * Holds transports that are shared across all loggers
 * Allows runtime transport configuration changes
 */
class TransportManagerImpl {
  private transports: LogTransport[] = [];
  private config: TransportManagerConfig = {
    includeTimestamp: true,
    includeStack: true,
  };

  constructor() {
    // Initialize with default console transport
    this.transports = [
      createConsoleTransport({
        includeTimestamp: this.config.includeTimestamp,
        includeStack: this.config.includeStack,
      }),
    ];
  }

  /**
   * Log entry to all configured transports
   */
  log(entry: LogEntry): void {
    if (this.transports.length === 0) {
      // No transports configured - silently discard
      return;
    }

    this.transports.forEach((transport) => {
      try {
        transport(entry);
      } catch (error) {
        // Transport errors should not break logging
        // Use console directly as fallback since transport might be broken
        console.error('[TransportManager] Transport error:', error);
      }
    });
  }

  /**
   * Set transports (replaces all existing transports)
   * Changes apply immediately to all loggers
   */
  setTransports(transports: LogTransport[]): void {
    this.transports = [...transports];
  }

  /**
   * Add a transport to the existing list
   */
  addTransport(transport: LogTransport): void {
    this.transports.push(transport);
  }

  /**
   * Remove all transports
   */
  clearTransports(): void {
    this.transports = [];
  }

  /**
   * Get current transports (copy for inspection)
   */
  getTransports(): LogTransport[] {
    return [...this.transports];
  }

  /**
   * Update configuration
   * Note: This does not recreate existing transports
   */
  configure(config: TransportManagerConfig): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): TransportManagerConfig {
    return { ...this.config };
  }

  /**
   * Reset to default configuration (console transport)
   */
  reset(): void {
    this.config = {
      includeTimestamp: true,
      includeStack: true,
    };
    this.transports = [
      createConsoleTransport({
        includeTimestamp: this.config.includeTimestamp,
        includeStack: this.config.includeStack,
      }),
    ];
  }
}

/**
 * Global transport manager instance
 */
export const TransportManager = new TransportManagerImpl();
