/**
 * File Log Transport for Browser (Development Mode)
 *
 * Saves logs to local filesystem using:
 * 1. File System Access API (Chrome 86+, Edge 86+) - Preferred
 * 2. Fallback: Download API (all browsers)
 *
 * Usage:
 * ```typescript
 * import { FileLogTransport } from '@nimbus/shared';
 *
 * const fileTransport = new FileLogTransport({
 *   filename: 'nimbus-client.log',
 *   maxSizeMB: 5,
 * });
 *
 * await fileTransport.initialize();
 *
 * LoggerFactory.configure({
 *   transports: [fileTransport.transport],
 * });
 * ```
 */

import type { LogEntry } from '../LogEntry';
import { LogLevelNames } from '../LogLevel';
import { ExceptionHandler } from '../../errors/ExceptionHandler';

export interface FileLogTransportOptions {
  /** Log filename */
  filename?: string;

  /** Maximum file size in MB (default: 5 MB) */
  maxSizeMB?: number;

  /** Auto-flush interval in ms (default: 1000) */
  flushIntervalMs?: number;

  /** Include timestamp in filename (default: true) */
  includeTimestamp?: boolean;

  /** Use File System Access API if available (default: true) */
  useFileSystemAPI?: boolean;
}

/**
 * File log transport for browser
 */
export class FileLogTransport {
  private options: Required<FileLogTransportOptions>;
  private buffer: string[] = [];
  private bufferSize = 0;
  private fileHandle: FileSystemFileHandle | null = null;
  private writable: FileSystemWritableFileStream | null = null;
  private flushTimer: number | null = null;
  private initialized = false;
  private useFileSystemAPI = false;

  constructor(options: FileLogTransportOptions = {}) {
    const timestamp = new Date()
      .toISOString()
      .replace(/[:.]/g, '-')
      .slice(0, -5);

    this.options = {
      filename: options.filename || 'app.log',
      maxSizeMB: options.maxSizeMB || 5,
      flushIntervalMs: options.flushIntervalMs || 1000,
      includeTimestamp: options.includeTimestamp ?? true,
      useFileSystemAPI: options.useFileSystemAPI ?? true,
    };

    // Add timestamp to filename if enabled
    if (this.options.includeTimestamp) {
      const ext = this.options.filename.split('.').pop();
      const base = this.options.filename.replace(`.${ext}`, '');
      this.options.filename = `${base}-${timestamp}.${ext}`;
    }
  }

  /**
   * Initialize file transport
   * Must be called before logging
   */
  async initialize(): Promise<void> {
    try {
      if (this.initialized) {
        return;
      }

      // Check if File System Access API is available
      this.useFileSystemAPI =
        this.options.useFileSystemAPI &&
        typeof window !== 'undefined' &&
        'showSaveFilePicker' in window;

      if (this.useFileSystemAPI) {
        await this.initializeFileSystemAPI();
      } else {
        console.info(
          '[FileLogTransport] Using download fallback (File System Access API not available)'
        );
      }

      // Start auto-flush timer
      this.startAutoFlush();

      this.initialized = true;

      console.info(
        `[FileLogTransport] Initialized: ${this.options.filename} (${this.options.maxSizeMB} MB max, ${this.useFileSystemAPI ? 'File System API' : 'Download API'})`
      );
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'FileLogTransport.initialize',
        { options: this.options }
      );
    }
  }

  /**
   * Initialize File System Access API
   */
  private async initializeFileSystemAPI(): Promise<void> {
    try {
      // Request file handle
      this.fileHandle = await (window as any).showSaveFilePicker({
        suggestedName: this.options.filename,
        types: [
          {
            description: 'Log Files',
            accept: { 'text/plain': ['.log', '.txt'] },
          },
        ],
      });

      // Create writable stream
      if (this.fileHandle) {
        this.writable = await this.fileHandle.createWritable();
      }

      console.info(
        `[FileLogTransport] File System Access API initialized: ${this.options.filename}`
      );
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        console.warn('[FileLogTransport] User cancelled file selection');
      } else {
        console.error('[FileLogTransport] Failed to initialize:', error);
      }
      // Fallback to download API
      this.useFileSystemAPI = false;
      this.fileHandle = null;
      this.writable = null;
    }
  }

  /**
   * Transport function for LoggerFactory
   */
  transport = (entry: LogEntry): void => {
    try {
      if (!this.initialized) {
        console.warn('[FileLogTransport] Not initialized, call initialize() first');
        return;
      }

      const line = this.formatEntry(entry);
      this.buffer.push(line);
      this.bufferSize += line.length;

      // Flush if buffer is getting large (>100KB)
      if (this.bufferSize > 100 * 1024) {
        this.flush();
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'FileLogTransport.transport', { entry });
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
  async flush(): Promise<void> {
    try {
      if (this.buffer.length === 0) {
        return;
      }

      const content = this.buffer.join('');
      this.buffer = [];
      this.bufferSize = 0;

      if (this.useFileSystemAPI && this.writable) {
        try {
          await this.writable.write(content);
        } catch (error) {
          console.error('[FileLogTransport] Failed to write:', error);
          // Fallback to download
          this.downloadLog(content);
        }
      } else {
        // Fallback: append to download buffer
        this.downloadLog(content);
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'FileLogTransport.flush');
      // Don't rethrow - flush errors should not break the application
    }
  }

  /**
   * Download log content
   */
  private downloadLog(content: string): void {
    try {
      const blob = new Blob([content], { type: 'text/plain' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = this.options.filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      ExceptionHandler.handle(error, 'FileLogTransport.downloadLog');
      // Don't rethrow - download errors should not break the application
    }
  }

  /**
   * Start auto-flush timer
   */
  private startAutoFlush(): void {
    this.flushTimer = window.setInterval(() => {
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
  async close(): Promise<void> {
    try {
      this.stopAutoFlush();

      // Flush remaining buffer
      await this.flush();

      // Close writable stream
      if (this.writable) {
        try {
          await this.writable.close();
        } catch (error) {
          console.error('[FileLogTransport] Failed to close:', error);
        }
        this.writable = null;
      }

      this.fileHandle = null;
      this.initialized = false;

      console.info('[FileLogTransport] Closed');
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(
        error,
        'FileLogTransport.close'
      );
    }
  }

  /**
   * Check if File System Access API is supported
   */
  static isFileSystemAPISupported(): boolean {
    return (
      typeof window !== 'undefined' && 'showSaveFilePicker' in window
    );
  }

  /**
   * Get current buffer size
   */
  getBufferSize(): number {
    return this.bufferSize;
  }

  /**
   * Get buffer entry count
   */
  getBufferCount(): number {
    return this.buffer.length;
  }
}
