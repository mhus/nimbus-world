/**
 * Tests for TransportManager
 */

import { TransportManager } from './TransportManager';
import { LogLevel } from './LogLevel';
import type { LogEntry } from './LogEntry';
import { createNullTransport } from './transports/NullTransport';

describe('TransportManager', () => {
  let entries: LogEntry[];
  let mockTransport: jest.Mock;

  beforeEach(() => {
    entries = [];
    mockTransport = jest.fn((entry: LogEntry) => {
      entries.push(entry);
    });
  });

  afterEach(() => {
    // Reset to default state after each test
    TransportManager.reset();
  });

  describe('setTransports', () => {
    it('should set transports at runtime', () => {
      // Set custom transport
      TransportManager.setTransports([mockTransport]);

      // Create test entry
      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Test message',
        timestamp: Date.now(),
      };

      TransportManager.log(entry);

      expect(mockTransport).toHaveBeenCalledTimes(1);
      expect(mockTransport).toHaveBeenCalledWith(entry);
    });

    it('should replace existing transports', () => {
      const transport1 = jest.fn();
      const transport2 = jest.fn();

      // Set first transport
      TransportManager.setTransports([transport1]);

      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Message 1',
        timestamp: Date.now(),
      };

      TransportManager.log(entry);

      expect(transport1).toHaveBeenCalledTimes(1);
      expect(transport2).not.toHaveBeenCalled();

      // Replace with second transport
      TransportManager.setTransports([transport2]);

      const entry2: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Message 2',
        timestamp: Date.now(),
      };

      TransportManager.log(entry2);

      expect(transport1).toHaveBeenCalledTimes(1); // Still 1
      expect(transport2).toHaveBeenCalledTimes(1); // Now called
    });
  });

  describe('addTransport', () => {
    it('should add transport to existing list', () => {
      const transport1 = jest.fn();
      const transport2 = jest.fn();

      TransportManager.setTransports([transport1]);
      TransportManager.addTransport(transport2);

      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Test',
        timestamp: Date.now(),
      };

      TransportManager.log(entry);

      expect(transport1).toHaveBeenCalledTimes(1);
      expect(transport2).toHaveBeenCalledTimes(1);
    });
  });

  describe('clearTransports', () => {
    it('should remove all transports', () => {
      TransportManager.setTransports([mockTransport]);
      TransportManager.clearTransports();

      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Test',
        timestamp: Date.now(),
      };

      // Should not throw, but nothing should be logged
      expect(() => {
        TransportManager.log(entry);
      }).not.toThrow();

      expect(mockTransport).not.toHaveBeenCalled();
    });
  });

  describe('reset', () => {
    it('should reset to default console transport', () => {
      // Set custom transport
      TransportManager.setTransports([mockTransport]);

      // Reset
      TransportManager.reset();

      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Test',
        timestamp: Date.now(),
      };

      TransportManager.log(entry);

      // Custom transport should not be called
      expect(mockTransport).not.toHaveBeenCalled();
      // Default console transport would be called (but we can't easily test that)
    });
  });

  describe('runtime transport switching', () => {
    it('should allow switching transports while logging', () => {
      const transport1 = jest.fn();
      const transport2 = jest.fn();
      const transport3 = jest.fn();

      // Start with transport1
      TransportManager.setTransports([transport1]);

      TransportManager.log({
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Message 1',
        timestamp: Date.now(),
      });

      expect(transport1).toHaveBeenCalledTimes(1);

      // Switch to transport2
      TransportManager.setTransports([transport2]);

      TransportManager.log({
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Message 2',
        timestamp: Date.now(),
      });

      expect(transport1).toHaveBeenCalledTimes(1);
      expect(transport2).toHaveBeenCalledTimes(1);

      // Switch to transport3
      TransportManager.setTransports([transport3]);

      TransportManager.log({
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Message 3',
        timestamp: Date.now(),
      });

      expect(transport1).toHaveBeenCalledTimes(1);
      expect(transport2).toHaveBeenCalledTimes(1);
      expect(transport3).toHaveBeenCalledTimes(1);
    });

    it('should switch from console to null transport', () => {
      // Start with default console transport (already set)
      TransportManager.reset();

      // Switch to null transport to disable logging
      TransportManager.setTransports([createNullTransport()]);

      // Logging should still work but be discarded
      expect(() => {
        TransportManager.log({
          level: LogLevel.INFO,
          name: 'Test',
          message: 'This should be discarded',
          timestamp: Date.now(),
        });
      }).not.toThrow();
    });
  });

  describe('transport error handling', () => {
    it('should continue with other transports on error', () => {
      const errorTransport = jest.fn(() => {
        throw new Error('Transport error');
      });
      const goodTransport = jest.fn();

      TransportManager.setTransports([errorTransport, goodTransport]);

      const entry: LogEntry = {
        level: LogLevel.INFO,
        name: 'Test',
        message: 'Test',
        timestamp: Date.now(),
      };

      // Should not throw
      expect(() => {
        TransportManager.log(entry);
      }).not.toThrow();

      expect(errorTransport).toHaveBeenCalledTimes(1);
      expect(goodTransport).toHaveBeenCalledTimes(1);
    });
  });

  describe('getTransports', () => {
    it('should return copy of transports', () => {
      const transport1 = jest.fn();
      const transport2 = jest.fn();

      TransportManager.setTransports([transport1, transport2]);

      const transports = TransportManager.getTransports();

      expect(transports).toHaveLength(2);

      // Modifying returned array should not affect internal state
      transports.push(jest.fn());

      expect(TransportManager.getTransports()).toHaveLength(2);
    });
  });

  describe('configure', () => {
    it('should update configuration', () => {
      TransportManager.configure({
        includeTimestamp: false,
        includeStack: false,
      });

      const config = TransportManager.getConfig();

      expect(config.includeTimestamp).toBe(false);
      expect(config.includeStack).toBe(false);
    });
  });
});
