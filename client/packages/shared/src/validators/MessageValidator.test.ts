/**
 * Tests for MessageValidator
 */

import { MessageValidator } from './MessageValidator';
import { MessageType } from '../network/MessageTypes';
import type { BaseMessage } from '../network/BaseMessage';

describe('MessageValidator', () => {
  describe('isValidMessageId', () => {
    it('should accept valid message IDs', () => {
      expect(MessageValidator.isValidMessageId('msg123')).toBe(true);
      expect(MessageValidator.isValidMessageId('m')).toBe(true);
      expect(MessageValidator.isValidMessageId('a'.repeat(100))).toBe(true);
    });

    it('should reject invalid message IDs', () => {
      expect(MessageValidator.isValidMessageId('')).toBe(false);
      expect(MessageValidator.isValidMessageId('a'.repeat(101))).toBe(false);
      expect(MessageValidator.isValidMessageId(123 as any)).toBe(false);
    });
  });

  describe('isValidMessageType', () => {
    it('should accept valid message types', () => {
      expect(MessageValidator.isValidMessageType(MessageType.PING)).toBe(true);
      expect(MessageValidator.isValidMessageType(MessageType.LOGIN)).toBe(true);
      expect(MessageValidator.isValidMessageType(MessageType.CHUNK_QUERY)).toBe(true);
    });

    it('should reject invalid message types', () => {
      expect(MessageValidator.isValidMessageType('invalid')).toBe(false);
      expect(MessageValidator.isValidMessageType('')).toBe(false);
    });
  });

  describe('validateBaseMessage', () => {
    it('should validate correct message', () => {
      const message: BaseMessage = {
        t: MessageType.PING,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate message with ID', () => {
      const message: BaseMessage = {
        i: 'msg123',
        t: MessageType.CHUNK_QUERY,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(true);
    });

    it('should validate message with response ID', () => {
      const message: BaseMessage = {
        r: 'req456',
        t: MessageType.LOGIN_RESPONSE,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(true);
    });

    it('should reject message without type', () => {
      const message: any = {
        i: 'msg123',
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Message type (t) is required');
    });

    it('should reject invalid message type', () => {
      const message: any = {
        t: 'invalid',
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Invalid message type'))).toBe(true);
    });

    it('should reject invalid message ID', () => {
      const message: BaseMessage = {
        i: '',
        t: MessageType.PING,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Invalid message ID'))).toBe(true);
    });

    it('should reject invalid response ID', () => {
      const message: BaseMessage = {
        r: '',
        t: MessageType.PING,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Invalid response ID'))).toBe(true);
    });

    it('should warn about message with both i and r', () => {
      const message: BaseMessage = {
        i: 'msg123',
        r: 'req456',
        t: MessageType.PING,
      };
      const result = MessageValidator.validateBaseMessage(message);
      expect(result.warnings).toBeDefined();
      expect(result.warnings!.some((w) => w.includes('both i and r'))).toBe(true);
    });
  });

  describe('validateMessageJSON', () => {
    it('should validate correct JSON message', () => {
      const json = '{"t":"p"}';
      const result = MessageValidator.validateMessageJSON(json);
      expect(result.valid).toBe(true);
      expect(result.message).toBeDefined();
      expect(result.message!.t).toBe('p');
    });

    it('should validate JSON with data payload', () => {
      const json = '{"i":"msg123","t":"c.q","d":{"cx":0,"cz":0}}';
      const result = MessageValidator.validateMessageJSON(json);
      expect(result.valid).toBe(true);
      expect(result.message).toBeDefined();
    });

    it('should reject invalid JSON', () => {
      const json = 'invalid json';
      const result = MessageValidator.validateMessageJSON(json);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes('Invalid JSON'))).toBe(true);
    });

    it('should reject JSON without type field', () => {
      const json = '{"i":"msg123"}';
      const result = MessageValidator.validateMessageJSON(json);
      expect(result.valid).toBe(false);
    });

    it('should warn about very large messages', () => {
      const largeData = 'x'.repeat(11 * 1024 * 1024); // 11 MB
      const json = `{"t":"p","d":"${largeData}"}`;
      const result = MessageValidator.validateMessageJSON(json);
      expect(result.warnings).toBeDefined();
      expect(result.warnings!.some((w) => w.includes('very large'))).toBe(true);
    });
  });

  describe('isValid', () => {
    it('should return true for valid message', () => {
      const message: BaseMessage = {
        t: MessageType.PING,
      };
      expect(MessageValidator.isValid(message)).toBe(true);
    });

    it('should return false for invalid message', () => {
      const message: any = {
        t: 'invalid',
      };
      expect(MessageValidator.isValid(message)).toBe(false);
    });

    it('should return false for null', () => {
      expect(MessageValidator.isValid(null as any)).toBeFalsy();
    });

    it('should return false for non-object', () => {
      expect(MessageValidator.isValid('string' as any)).toBe(false);
    });
  });
});
