/**
 * Tests for Logger
 */

import { Logger } from './Logger';
import { LogLevel } from './LogLevel';
import type { LogEntry } from './LogEntry';
import { TransportManager } from './TransportManager';

describe('Logger', () => {
  let logger: Logger;
  let entries: LogEntry[];
  let transport: jest.Mock;

  beforeEach(() => {
    entries = [];
    transport = jest.fn((entry: LogEntry) => {
      entries.push(entry);
    });

    // Configure TransportManager with test transport
    TransportManager.setTransports([transport]);

    logger = new Logger('TestLogger', {
      minLevel: LogLevel.TRACE,
    });
  });

  afterEach(() => {
    // Reset TransportManager to default after each test
    TransportManager.reset();
  });

  describe('constructor', () => {
    it('should create logger with default config', () => {
      const defaultLogger = new Logger('DefaultLogger');
      expect(defaultLogger).toBeInstanceOf(Logger);
    });

    it('should create logger with custom config', () => {
      const customLogger = new Logger('CustomLogger', {
        minLevel: LogLevel.ERROR,
        includeStack: false,
      });
      expect(customLogger).toBeInstanceOf(Logger);
    });
  });

  describe('log levels', () => {
    it('should log FATAL messages', () => {
      logger.fatal('Fatal error', { code: 1 });

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.FATAL);
      expect(entries[0].message).toBe('Fatal error');
      expect(entries[0].data).toEqual({ code: 1 });
    });

    it('should log ERROR messages', () => {
      const error = new Error('Test error');
      logger.error('Error occurred', { userId: '123' }, error);

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.ERROR);
      expect(entries[0].message).toBe('Error occurred');
      expect(entries[0].data).toEqual({ userId: '123' });
      expect(entries[0].error).toBe(error);
      expect(entries[0].stack).toBeDefined();
    });

    it('should log WARN messages', () => {
      logger.warn('Warning message', { attempts: 3 });

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.WARN);
      expect(entries[0].message).toBe('Warning message');
      expect(entries[0].data).toEqual({ attempts: 3 });
    });

    it('should log INFO messages', () => {
      logger.info('Info message', { version: '1.0' });

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.INFO);
      expect(entries[0].message).toBe('Info message');
      expect(entries[0].data).toEqual({ version: '1.0' });
    });

    it('should log DEBUG messages', () => {
      logger.debug('Debug message', { state: 'active' });

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.DEBUG);
      expect(entries[0].message).toBe('Debug message');
      expect(entries[0].data).toEqual({ state: 'active' });
    });

    it('should log TRACE messages', () => {
      logger.trace('Trace message', { callstack: [] });

      expect(entries).toHaveLength(1);
      expect(entries[0].level).toBe(LogLevel.TRACE);
      expect(entries[0].message).toBe('Trace message');
      expect(entries[0].data).toEqual({ callstack: [] });
    });
  });

  describe('log level filtering', () => {
    beforeEach(() => {
      logger.setLevel(LogLevel.WARN);
      entries = [];
    });

    it('should log FATAL (above threshold)', () => {
      logger.fatal('Fatal');
      expect(entries).toHaveLength(1);
    });

    it('should log ERROR (above threshold)', () => {
      logger.error('Error');
      expect(entries).toHaveLength(1);
    });

    it('should log WARN (at threshold)', () => {
      logger.warn('Warning');
      expect(entries).toHaveLength(1);
    });

    it('should not log INFO (below threshold)', () => {
      logger.info('Info');
      expect(entries).toHaveLength(0);
    });

    it('should not log DEBUG (below threshold)', () => {
      logger.debug('Debug');
      expect(entries).toHaveLength(0);
    });

    it('should not log TRACE (below threshold)', () => {
      logger.trace('Trace');
      expect(entries).toHaveLength(0);
    });
  });

  describe('isLevelEnabled', () => {
    beforeEach(() => {
      logger.setLevel(LogLevel.INFO);
    });

    it('should return true for enabled levels', () => {
      expect(logger.isLevelEnabled(LogLevel.FATAL)).toBe(true);
      expect(logger.isLevelEnabled(LogLevel.ERROR)).toBe(true);
      expect(logger.isLevelEnabled(LogLevel.WARN)).toBe(true);
      expect(logger.isLevelEnabled(LogLevel.INFO)).toBe(true);
    });

    it('should return false for disabled levels', () => {
      expect(logger.isLevelEnabled(LogLevel.DEBUG)).toBe(false);
      expect(logger.isLevelEnabled(LogLevel.TRACE)).toBe(false);
    });
  });

  describe('setLevel', () => {
    it('should change log level', () => {
      logger.setLevel(LogLevel.ERROR);

      logger.error('Error');
      logger.warn('Warning');
      logger.info('Info');

      expect(entries).toHaveLength(1);
      expect(entries[0].message).toBe('Error');
    });

    it('should allow raising log level', () => {
      logger.setLevel(LogLevel.TRACE);
      logger.setLevel(LogLevel.FATAL);

      logger.fatal('Fatal');
      logger.error('Error');

      expect(entries).toHaveLength(1);
      expect(entries[0].message).toBe('Fatal');
    });
  });

  describe('multiple transports', () => {
    it('should call all transports', () => {
      const transport1 = jest.fn();
      const transport2 = jest.fn();
      const transport3 = jest.fn();

      // Configure TransportManager with multiple transports
      TransportManager.setTransports([transport1, transport2, transport3]);

      const multiLogger = new Logger('MultiLogger', {
        minLevel: LogLevel.INFO,
      });

      multiLogger.info('Test message');

      expect(transport1).toHaveBeenCalledTimes(1);
      expect(transport2).toHaveBeenCalledTimes(1);
      expect(transport3).toHaveBeenCalledTimes(1);
    });

    it('should handle transport errors', () => {
      const errorTransport = jest.fn(() => {
        throw new Error('Transport error');
      });
      const goodTransport = jest.fn();

      // Configure TransportManager with error and good transport
      TransportManager.setTransports([errorTransport, goodTransport]);

      const errorLogger = new Logger('ErrorLogger', {
        minLevel: LogLevel.INFO,
      });

      // Should not throw
      expect(() => {
        errorLogger.info('Test message');
      }).not.toThrow();

      expect(errorTransport).toHaveBeenCalledTimes(1);
      expect(goodTransport).toHaveBeenCalledTimes(1);
    });
  });

  describe('log entry structure', () => {
    it('should include logger name', () => {
      logger.info('Test');

      expect(entries[0].name).toBe('TestLogger');
    });

    it('should include timestamp', () => {
      const before = Date.now();
      logger.info('Test');
      const after = Date.now();

      expect(entries[0].timestamp).toBeGreaterThanOrEqual(before);
      expect(entries[0].timestamp).toBeLessThanOrEqual(after);
    });

    it('should include error and stack for error logs', () => {
      const error = new Error('Test error');
      logger.error('Error message', undefined, error);

      expect(entries[0].error).toBe(error);
      expect(entries[0].stack).toBe(error.stack);
    });

    it('should not include stack if includeStack is false', () => {
      // Clear previous entries
      entries = [];

      const noStackLogger = new Logger('NoStackLogger', {
        minLevel: LogLevel.ERROR,
        includeStack: false,
      });

      const error = new Error('Test error');
      noStackLogger.error('Error message', undefined, error);

      expect(entries[0].error).toBe(error);
      expect(entries[0].stack).toBeUndefined();
    });
  });

  describe('data parameter', () => {
    it('should accept undefined data', () => {
      logger.info('Message');
      expect(entries[0].data).toBeUndefined();
    });

    it('should accept object data', () => {
      const data = { userId: '123', action: 'login' };
      logger.info('Message', data);
      expect(entries[0].data).toEqual(data);
    });

    it('should accept primitive data', () => {
      logger.info('Message', 'string data');
      expect(entries[0].data).toBe('string data');

      logger.info('Message', 42);
      expect(entries[1].data).toBe(42);

      logger.info('Message', true);
      expect(entries[2].data).toBe(true);
    });

    it('should accept array data', () => {
      const data = [1, 2, 3];
      logger.info('Message', data);
      expect(entries[0].data).toEqual(data);
    });
  });

  describe('edge cases', () => {
    it('should handle empty message', () => {
      logger.info('');
      expect(entries[0].message).toBe('');
    });

    it('should handle very long messages', () => {
      const longMessage = 'x'.repeat(10000);
      logger.info(longMessage);
      expect(entries[0].message).toBe(longMessage);
    });

    it('should handle special characters in message', () => {
      const message = 'Test\n\t"quoted"\r\n\\backslash';
      logger.info(message);
      expect(entries[0].message).toBe(message);
    });

    it('should handle circular references in data', () => {
      const circularData: any = { name: 'test' };
      circularData.self = circularData;

      // Should not throw
      expect(() => {
        logger.info('Message', circularData);
      }).not.toThrow();
    });
  });
});
