/**
 * Client Service
 * Provides client platform detection, configuration, and logger setup
 */

import { getLogger, TransportManager, ClientType } from '@nimbus/shared';
import {
  createConsoleTransport,
  createNullTransport,
  FileLogTransport,
} from '@nimbus/shared';
import type { ClientConfig } from '../config/ClientConfig';

const logger = getLogger('ClientService');

/**
 * Client Service
 * Singleton service for client-specific functionality
 */
export class ClientService {
  private config: ClientConfig;
  private logToConsole: boolean;

  constructor(config: ClientConfig) {
    this.config = config;
    this.logToConsole = config.logToConsole;

    logger.debug('ClientService initialized', {
      clientType: this.getClientType(),
      isEditor: this.isEditor(),
      isDevMode: this.isDevMode(),
    });
  }

  /**
   * Get client type based on user agent
   * @returns Client type
   */
  getClientType(): ClientType {
    if (typeof navigator === 'undefined' || !navigator.userAgent) {
      // Server-side or test environment
      return ClientType.WEB;
    }

    const userAgent = navigator.userAgent.toLowerCase();

    // Check for Xbox
    if (userAgent.includes('xbox')) {
      return ClientType.XBOX;
    }

    // Check for mobile devices
    if (
      userAgent.includes('mobile') ||
      userAgent.includes('android') ||
      userAgent.includes('iphone') ||
      userAgent.includes('ipad')
    ) {
      return ClientType.MOBILE;
    }

    // Default to web
    return ClientType.WEB;
  }

  /**
   * Get user agent string
   * @returns User agent or empty string if not available
   */
  getUserAgent(): string {
    if (typeof navigator === 'undefined') {
      return '';
    }
    return navigator.userAgent;
  }

  /**
   * Get browser/client language
   * @returns Language code (e.g., 'en-US') or empty string if not available
   */
  getLanguage(): string {
    if (typeof navigator === 'undefined') {
      return '';
    }
    return navigator.language || '';
  }

  /**
   * Check if running in editor mode
   * @returns True if editor build
   */
  isEditor(): boolean {
    // @ts-ignore - __EDITOR__ is defined by Vite
    return typeof __EDITOR__ !== 'undefined' && __EDITOR__;
  }

  /**
   * Check if running in development mode
   * @returns True if development mode
   */
  isDevMode(): boolean {
    // Check Vite environment
    try {
      // Use eval to defer import.meta parsing (not available in Jest/Node)
      // @ts-ignore
      const importMeta = eval('import.meta');
      if (importMeta && importMeta.env) {
        return !importMeta.env.PROD;
      }
    } catch (e) {
      // import.meta not available, fall through to Node.js check
    }

    // Check Node.js environment
    if (typeof process !== 'undefined' && process.env) {
      return process.env.NODE_ENV !== 'production';
    }

    // Default to false
    return false;
  }

  /**
   * Check if console logging is enabled
   * @returns True if console logging enabled
   */
  isLogToConsole(): boolean {
    return this.logToConsole;
  }

  /**
   * Enable or disable console logging
   * @param enabled True to enable console logging
   */
  setLogToConsole(enabled: boolean): void {
    this.logToConsole = enabled;
    logger.debug(`Console logging ${enabled ? 'enabled' : 'disabled'}`);

    // Reconfigure logger
    this.setupLogger();
  }

  /**
   * Setup logger with appropriate transports based on environment
   */
  setupLogger(): void {
    logger.debug('Setting up logger', {
      isDevMode: this.isDevMode(),
      isEditor: this.isEditor(),
      logToConsole: this.logToConsole,
    });

    const transports = [];

    // Development mode: Add file transport (optional)
    if (this.isDevMode() && FileLogTransport.isFileSystemAPISupported()) {
      try {
        const fileTransport = new FileLogTransport({
          maxSizeMB: 1,
        });
        // Initialize async - don't wait for it
        fileTransport
          .initialize()
          .then(() => {
            transports.push(fileTransport.transport);
            TransportManager.addTransport(fileTransport.transport);
            logger.debug('File log transport added');
          })
          .catch((error) => {
            logger.warn('Failed to initialize file log transport', {
              error: error instanceof Error ? error.message : String(error),
            });
          });
      } catch (error) {
        logger.warn('Failed to create file log transport', {
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }

    // Console transport
    if (this.logToConsole) {
      const consoleTransport = createConsoleTransport({
        includeTimestamp: true,
        includeStack: this.isDevMode(),
      });
      transports.push(consoleTransport);
      logger.debug('Console transport added');
    }

    // If no transports, use null transport
    if (transports.length === 0) {
      transports.push(createNullTransport());
      logger.debug('Null transport added (logging disabled)');
    }

    // Configure transport manager
    TransportManager.setTransports(transports);

    // Set log level based on environment
    if (this.isDevMode()) {
      TransportManager.configure({
        includeTimestamp: true,
        includeStack: true,
      });
    } else {
      TransportManager.configure({
        includeTimestamp: false,
        includeStack: false,
      });
    }

    logger.debug('Logger setup complete', {
      transportCount: transports.length,
    });
  }

  /**
   * Get client configuration
   * @returns Client configuration
   */
  getConfig(): ClientConfig {
    return this.config;
  }
}
