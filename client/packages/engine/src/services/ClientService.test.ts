/**
 * Tests for ClientService
 */

import { ClientService } from './ClientService';
import { TransportManager, ClientType } from '@nimbus/shared';
import { ClientConfig } from '../config/ClientConfig';

describe('ClientService', () => {
  let mockConfig: ClientConfig;
  let service: ClientService;

  beforeEach(() => {
    mockConfig = {
      username: 'testuser',
      password: 'testpass',
      websocketUrl: 'ws://localhost:3000',
      apiUrl: 'http://localhost:3000',
      worldId: 'test-world',
      logToConsole: true,
      exitUrl: 'http://localhost:3000/exit',
    };

    // Reset TransportManager
    TransportManager.reset();
  });

  afterEach(() => {
    // Reset TransportManager after each test
    TransportManager.reset();
  });

  describe('constructor', () => {
    it('should create ClientService with config', () => {
      service = new ClientService(mockConfig);

      expect(service).toBeInstanceOf(ClientService);
      expect(service.getConfig()).toEqual(mockConfig);
    });
  });

  describe('getClientType', () => {
    it('should return WEB by default', () => {
      service = new ClientService(mockConfig);

      expect(service.getClientType()).toBe(ClientType.WEB);
    });

    it('should detect mobile from user agent', () => {
      // Mock navigator
      Object.defineProperty(global, 'navigator', {
        value: {
          userAgent:
            'Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X) AppleWebKit/605.1.15',
        },
        writable: true,
        configurable: true,
      });

      service = new ClientService(mockConfig);

      expect(service.getClientType()).toBe(ClientType.MOBILE);
    });
  });

  describe('getUserAgent', () => {
    it('should return empty string if navigator not available', () => {
      // Remove navigator
      const originalNavigator = global.navigator;
      // @ts-ignore
      delete global.navigator;

      service = new ClientService(mockConfig);

      expect(service.getUserAgent()).toBe('');

      // Restore
      global.navigator = originalNavigator;
    });

    it('should return user agent if available', () => {
      const userAgent = 'Test User Agent';
      Object.defineProperty(global, 'navigator', {
        value: {
          userAgent,
        },
        writable: true,
        configurable: true,
      });

      service = new ClientService(mockConfig);

      expect(service.getUserAgent()).toBe(userAgent);
    });
  });

  describe('getLanguage', () => {
    it('should return empty string if navigator not available', () => {
      // Remove navigator
      const originalNavigator = global.navigator;
      // @ts-ignore
      delete global.navigator;

      service = new ClientService(mockConfig);

      expect(service.getLanguage()).toBe('');

      // Restore
      global.navigator = originalNavigator;
    });

    it('should return language if available', () => {
      const language = 'en-US';
      Object.defineProperty(global, 'navigator', {
        value: {
          language,
        },
        writable: true,
        configurable: true,
      });

      service = new ClientService(mockConfig);

      expect(service.getLanguage()).toBe(language);
    });
  });

  describe('isEditor', () => {
    it('should return false if __EDITOR__ not defined', () => {
      service = new ClientService(mockConfig);

      // In test environment, __EDITOR__ is not defined
      expect(service.isEditor()).toBe(false);
    });
  });

  describe('isDevMode', () => {
    it('should detect development mode from NODE_ENV', () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'development';

      service = new ClientService(mockConfig);

      expect(service.isDevMode()).toBe(true);

      process.env.NODE_ENV = originalEnv;
    });

    it('should detect production mode from NODE_ENV', () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';

      service = new ClientService(mockConfig);

      expect(service.isDevMode()).toBe(false);

      process.env.NODE_ENV = originalEnv;
    });
  });

  describe('isLogToConsole', () => {
    it('should return config value', () => {
      mockConfig.logToConsole = true;
      service = new ClientService(mockConfig);

      expect(service.isLogToConsole()).toBe(true);

      mockConfig.logToConsole = false;
      service = new ClientService(mockConfig);

      expect(service.isLogToConsole()).toBe(false);
    });
  });

  describe('setLogToConsole', () => {
    it('should update log to console setting', () => {
      service = new ClientService(mockConfig);

      expect(service.isLogToConsole()).toBe(true);

      service.setLogToConsole(false);

      expect(service.isLogToConsole()).toBe(false);

      service.setLogToConsole(true);

      expect(service.isLogToConsole()).toBe(true);
    });
  });

  describe('setupLogger', () => {
    it('should configure transports based on settings', () => {
      service = new ClientService(mockConfig);

      // setupLogger is called in constructor, verify it doesn't throw
      expect(() => service.setupLogger()).not.toThrow();

      // Verify transports are configured
      const transports = TransportManager.getTransports();
      expect(transports.length).toBeGreaterThan(0);
    });

    it('should add null transport if console logging disabled', () => {
      mockConfig.logToConsole = false;
      service = new ClientService(mockConfig);

      const transports = TransportManager.getTransports();

      // Should have at least null transport
      expect(transports.length).toBeGreaterThan(0);
    });
  });

  describe('getConfig', () => {
    it('should return client configuration', () => {
      service = new ClientService(mockConfig);

      const config = service.getConfig();

      expect(config).toEqual(mockConfig);
      expect(config.username).toBe('testuser');
      expect(config.websocketUrl).toBe('ws://localhost:3000');
    });
  });
});
