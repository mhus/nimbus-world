/**
 * File Log Transport for Node.js (Server)
 *
 * Saves logs to filesystem using Node.js fs module.
 * Includes log rotation support.
 *
 * Usage:
 * ```typescript
 * import { NodeFileLogTransport } from '@nimbus/shared';
 *
 * const fileTransport = new NodeFileLogTransport({
 *   filename: 'nimbus-server.log',
 *   maxSizeMB: 10,
 *   maxFiles: 5,
 * });
 *
 * LoggerFactory.configure({
 *   transports: [fileTransport.transport],
 * });
 * ```
 */

import type { LogEntry } from '../LogEntry';
import { LogLevelNames } from '../LogLevel';
import { ExceptionHandler } from '../../errors/ExceptionHandler';

export interface NodeFileLogTransportOptions {
  /** Log filename */
  filename?: string;

  /** Log directory (default: './logs') */
  directory?: string;

  /** Maximum file size in MB (default: 10 MB) */
  maxSizeMB?: number;

  /** Maximum number of rotated files (default: 5) */
  maxFiles?: number;

  /** Auto-flush interval in ms (default: 1000) */
  flushIntervalMs?: number;
}

/**
 * File log transport for Node.js
 */
export class NodeFileLogTransport {
  private options: Required<NodeFileLogTransportOptions>;
  private buffer: string[] = [];
  private bufferSize = 0;
  private currentSize = 0;
  private flushTimer: NodeJS.Timeout | null = null;
  private fs: any;
  private path: any;
  private fullPath: string;

  constructor(options: NodeFileLogTransportOptions = {}) {
    try {
      this.options = {
        filename: options.filename || 'app.log',
        directory: options.directory || './logs',
        maxSizeMB: options.maxSizeMB || 10,
        maxFiles: options.maxFiles || 5,
        flushIntervalMs: options.flushIntervalMs || 1000,
      };

      // Lazy load Node.js modules (won't exist in browser)
      try {
        this.fs = require('fs');
        this.path = require('path');
      } catch {
        throw new Error(
          'NodeFileLogTransport requires Node.js environment'
        );
      }

      this.fullPath = this.path.join(
        this.options.directory,
        this.options.filename
      );

      this.initialize();
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'NodeFileLogTransport.constructor',
        { options }
      );
    }
  }

  /**
   * Initialize file transport
   */
  private initialize(): void {
    try {
      // Create directory if it doesn't exist
      if (!this.fs.existsSync(this.options.directory)) {
        this.fs.mkdirSync(this.options.directory, { recursive: true });
      }

      // Get current file size
      if (this.fs.existsSync(this.fullPath)) {
        const stats = this.fs.statSync(this.fullPath);
        this.currentSize = stats.size;
      }

      // Start auto-flush timer
      this.startAutoFlush();

      console.info(
        `[NodeFileLogTransport] Initialized: ${this.fullPath} (${this.options.maxSizeMB} MB max, ${this.options.maxFiles} files)`
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'NodeFileLogTransport.initialize',
        { options: this.options }
      );
    }
  }

  /**
   * Transport function for LoggerFactory
   */
  transport = (entry: LogEntry): void => {
    try {
      const line = this.formatEntry(entry);
      this.buffer.push(line);
      this.bufferSize += line.length;

      // Flush if buffer is getting large (>100KB)
      if (this.bufferSize > 100 * 1024) {
        this.flush();
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'NodeFileLogTransport.transport', { entry });
      // Don't rethrow - transport errors should not break logging
    }
  };

  /**
   * Format log entry
   */
  private formatEntry(entry: LogEntry): string {
    const timestamp = new Date(entry.timestamp).toISOString();
    const level = LogLevelNames[entry.level].padEnd(5);
    const name = entry.name.padEnd(20);

    let line = `[${timestamp}] [${level}] [${name}] ${entry.message}`;

    if (entry.data) {
      line += `\n  Data: ${JSON.stringify(entry.data)}`;
    }

    if (entry.error) {
      line += `\n  Error: ${entry.error.message}`;
      if (entry.stack) {
        line += `\n  Stack: ${entry.stack}`;
      }
    }

    return line + '\n';
  }

  /**
   * Flush buffer to file
   */
  flush(): void {
    try {
      if (this.buffer.length === 0) {
        return;
      }

      const content = this.buffer.join('');
      const contentSize = Buffer.byteLength(content);

      // Check if rotation is needed
      if (
        this.currentSize + contentSize >
        this.options.maxSizeMB * 1024 * 1024
      ) {
        this.rotate();
      }

      // Append to file
      try {
        this.fs.appendFileSync(this.fullPath, content, 'utf8');
        this.currentSize += contentSize;
      } catch (error) {
        console.error('[NodeFileLogTransport] Failed to write:', error);
      }

      this.buffer = [];
      this.bufferSize = 0;
    } catch (error) {
      ExceptionHandler.handle(error, 'NodeFileLogTransport.flush');
      // Don't rethrow - flush errors should not break the application
    }
  }

  /**
   * Rotate log files
   */
  private rotate(): void {
    try {
      // Delete oldest file if max files reached
      const oldestFile = `${this.fullPath}.${this.options.maxFiles}`;
      if (this.fs.existsSync(oldestFile)) {
        this.fs.unlinkSync(oldestFile);
      }

      // Rotate existing files
      for (let i = this.options.maxFiles - 1; i >= 1; i--) {
        const oldFile = `${this.fullPath}.${i}`;
        const newFile = `${this.fullPath}.${i + 1}`;

        if (this.fs.existsSync(oldFile)) {
          this.fs.renameSync(oldFile, newFile);
        }
      }

      // Rotate current file
      if (this.fs.existsSync(this.fullPath)) {
        this.fs.renameSync(this.fullPath, `${this.fullPath}.1`);
      }

      this.currentSize = 0;

      console.info(`[NodeFileLogTransport] Rotated log files`);
    } catch (error) {
      ExceptionHandler.handle(error, 'NodeFileLogTransport.rotate');
      // Don't rethrow - rotation errors should not break the application
    }
  }

  /**
   * Start auto-flush timer
   */
  private startAutoFlush(): void {
    this.flushTimer = setInterval(() => {
      this.flush();
    }, this.options.flushIntervalMs);
  }

  /**
   * Stop auto-flush timer
   */
  private stopAutoFlush(): void {
    if (this.flushTimer !== null) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }
  }

  /**
   * Close file transport
   */
  close(): void {
    try {
      this.stopAutoFlush();
      this.flush();
      console.info('[NodeFileLogTransport] Closed');
    } catch (error) {
      ExceptionHandler.handle(error, 'NodeFileLogTransport.close');
      // Don't rethrow - close errors should not break the application
    }
  }

  /**
   * Get current file size
   */
  getCurrentSize(): number {
    return this.currentSize;
  }

  /**
   * Get buffer size
   */
  getBufferSize(): number {
    return this.bufferSize;
  }
}
