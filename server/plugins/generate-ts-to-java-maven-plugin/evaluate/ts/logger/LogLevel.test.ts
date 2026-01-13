/**
 * Tests for LogLevel utilities
 */

import { LogLevel, LogLevelNames, parseLogLevel } from './LogLevel';

describe('LogLevel', () => {
  describe('enum values', () => {
    it('should have correct numeric values', () => {
      expect(LogLevel.FATAL).toBe(0);
      expect(LogLevel.ERROR).toBe(1);
      expect(LogLevel.WARN).toBe(2);
      expect(LogLevel.INFO).toBe(3);
      expect(LogLevel.DEBUG).toBe(4);
      expect(LogLevel.TRACE).toBe(5);
    });

    it('should be ordered from highest to lowest priority', () => {
      expect(LogLevel.FATAL).toBeLessThan(LogLevel.ERROR);
      expect(LogLevel.ERROR).toBeLessThan(LogLevel.WARN);
      expect(LogLevel.WARN).toBeLessThan(LogLevel.INFO);
      expect(LogLevel.INFO).toBeLessThan(LogLevel.DEBUG);
      expect(LogLevel.DEBUG).toBeLessThan(LogLevel.TRACE);
    });
  });

  describe('LogLevelNames', () => {
    it('should map FATAL level', () => {
      expect(LogLevelNames[LogLevel.FATAL]).toBe('FATAL');
    });

    it('should map ERROR level', () => {
      expect(LogLevelNames[LogLevel.ERROR]).toBe('ERROR');
    });

    it('should map WARN level', () => {
      expect(LogLevelNames[LogLevel.WARN]).toBe('WARN');
    });

    it('should map INFO level', () => {
      expect(LogLevelNames[LogLevel.INFO]).toBe('INFO');
    });

    it('should map DEBUG level', () => {
      expect(LogLevelNames[LogLevel.DEBUG]).toBe('DEBUG');
    });

    it('should map TRACE level', () => {
      expect(LogLevelNames[LogLevel.TRACE]).toBe('TRACE');
    });

    it('should have entries for all log levels', () => {
      const levels = Object.keys(LogLevelNames).map((k) => parseInt(k, 10));
      expect(levels).toContain(LogLevel.FATAL);
      expect(levels).toContain(LogLevel.ERROR);
      expect(levels).toContain(LogLevel.WARN);
      expect(levels).toContain(LogLevel.INFO);
      expect(levels).toContain(LogLevel.DEBUG);
      expect(levels).toContain(LogLevel.TRACE);
    });
  });

  describe('parseLogLevel', () => {
    describe('valid inputs', () => {
      it('should parse FATAL', () => {
        expect(parseLogLevel('FATAL')).toBe(LogLevel.FATAL);
        expect(parseLogLevel('fatal')).toBe(LogLevel.FATAL);
        expect(parseLogLevel('Fatal')).toBe(LogLevel.FATAL);
      });

      it('should parse ERROR', () => {
        expect(parseLogLevel('ERROR')).toBe(LogLevel.ERROR);
        expect(parseLogLevel('error')).toBe(LogLevel.ERROR);
        expect(parseLogLevel('Error')).toBe(LogLevel.ERROR);
      });

      it('should parse WARN', () => {
        expect(parseLogLevel('WARN')).toBe(LogLevel.WARN);
        expect(parseLogLevel('warn')).toBe(LogLevel.WARN);
        expect(parseLogLevel('Warn')).toBe(LogLevel.WARN);
      });

      it('should parse INFO', () => {
        expect(parseLogLevel('INFO')).toBe(LogLevel.INFO);
        expect(parseLogLevel('info')).toBe(LogLevel.INFO);
        expect(parseLogLevel('Info')).toBe(LogLevel.INFO);
      });

      it('should parse DEBUG', () => {
        expect(parseLogLevel('DEBUG')).toBe(LogLevel.DEBUG);
        expect(parseLogLevel('debug')).toBe(LogLevel.DEBUG);
        expect(parseLogLevel('Debug')).toBe(LogLevel.DEBUG);
      });

      it('should parse TRACE', () => {
        expect(parseLogLevel('TRACE')).toBe(LogLevel.TRACE);
        expect(parseLogLevel('trace')).toBe(LogLevel.TRACE);
        expect(parseLogLevel('Trace')).toBe(LogLevel.TRACE);
      });

      it('should handle whitespace', () => {
        expect(parseLogLevel('  INFO  ')).toBe(LogLevel.INFO);
        expect(parseLogLevel('\tDEBUG\n')).toBe(LogLevel.DEBUG);
      });
    });

    describe('invalid inputs', () => {
      it('should return INFO for invalid strings', () => {
        expect(parseLogLevel('INVALID')).toBe(LogLevel.INFO);
        expect(parseLogLevel('log')).toBe(LogLevel.INFO);
        expect(parseLogLevel('verbose')).toBe(LogLevel.INFO);
      });

      it('should return INFO for empty string', () => {
        expect(parseLogLevel('')).toBe(LogLevel.INFO);
      });

      it('should return INFO for whitespace only', () => {
        expect(parseLogLevel('   ')).toBe(LogLevel.INFO);
        expect(parseLogLevel('\t\n')).toBe(LogLevel.INFO);
      });

      it('should return INFO for partial matches', () => {
        expect(parseLogLevel('INF')).toBe(LogLevel.INFO);
        expect(parseLogLevel('INFORMATION')).toBe(LogLevel.INFO);
        expect(parseLogLevel('ERRO')).toBe(LogLevel.INFO);
      });
    });

    describe('edge cases', () => {
      it('should handle mixed case consistently', () => {
        expect(parseLogLevel('ErRoR')).toBe(LogLevel.ERROR);
        expect(parseLogLevel('wArN')).toBe(LogLevel.WARN);
        expect(parseLogLevel('DeBuG')).toBe(LogLevel.DEBUG);
      });

      it('should handle numeric strings as invalid', () => {
        expect(parseLogLevel('0')).toBe(LogLevel.INFO);
        expect(parseLogLevel('1')).toBe(LogLevel.INFO);
        expect(parseLogLevel('5')).toBe(LogLevel.INFO);
      });
    });
  });

  describe('integration', () => {
    it('should support round-trip conversion', () => {
      // Level -> Name -> Level
      const level = LogLevel.ERROR;
      const name = LogLevelNames[level];
      const parsed = parseLogLevel(name);

      expect(parsed).toBe(level);
    });

    it('should support all levels round-trip', () => {
      const levels = [
        LogLevel.FATAL,
        LogLevel.ERROR,
        LogLevel.WARN,
        LogLevel.INFO,
        LogLevel.DEBUG,
        LogLevel.TRACE,
      ];

      levels.forEach((level) => {
        const name = LogLevelNames[level];
        const parsed = parseLogLevel(name);
        expect(parsed).toBe(level);
      });
    });
  });
});
