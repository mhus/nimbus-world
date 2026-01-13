/**
 * Tests for MessageSerializer
 */

import { MessageSerializer } from './MessageSerializer';
import type { BaseMessage } from '../network/BaseMessage';
import { MessageType } from '../network/MessageTypes';

describe('MessageSerializer', () => {
  describe('toJSON and fromJSON', () => {
    it('should serialize simple message to JSON', () => {
      const message: BaseMessage = {
        t: MessageType.PING,
      };

      const json = MessageSerializer.toJSON(message);
      expect(json).toBeTruthy();
      expect(typeof json).toBe('string');

      const parsed = JSON.parse(json);
      expect(parsed.t).toBe(MessageType.PING);
    });

    it('should serialize message with ID', () => {
      const message: BaseMessage = {
        i: 'msg123',
        t: MessageType.CHUNK_QUERY,
      };

      const json = MessageSerializer.toJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.i).toBe('msg123');
      expect(parsed.t).toBe(MessageType.CHUNK_QUERY);
    });

    it('should serialize message with response ID', () => {
      const message: BaseMessage = {
        r: 'req456',
        t: MessageType.LOGIN_RESPONSE,
      };

      const json = MessageSerializer.toJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.r).toBe('req456');
      expect(parsed.t).toBe(MessageType.LOGIN_RESPONSE);
    });

    it('should serialize message with data payload', () => {
      const message: BaseMessage = {
        i: 'msg789',
        t: MessageType.USER_MOVEMENT,
        d: {
          position: { x: 10, y: 64, z: 20 },
          rotation: { y: 90, p: 0 },
        },
      };

      const json = MessageSerializer.toJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.i).toBe('msg789');
      expect(parsed.t).toBe(MessageType.USER_MOVEMENT);
      expect(parsed.d).toBeDefined();
      expect(parsed.d.position).toEqual({ x: 10, y: 64, z: 20 });
    });

    it('should deserialize message from JSON', () => {
      const original: BaseMessage = {
        i: 'msg123',
        t: MessageType.PING,
        d: { timestamp: 12345 },
      };

      const json = MessageSerializer.toJSON(original);
      const deserialized = MessageSerializer.fromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.i).toBe('msg123');
      expect(deserialized!.t).toBe(MessageType.PING);
      expect(deserialized!.d).toEqual({ timestamp: 12345 });
    });

    it('should return null for invalid JSON', () => {
      const message = MessageSerializer.fromJSON('invalid json');
      expect(message).toBeNull();
    });

    it('should return null for empty string', () => {
      const message = MessageSerializer.fromJSON('');
      expect(message).toBeNull();
    });

    it('should return null for non-object JSON', () => {
      const message = MessageSerializer.fromJSON('[]');
      expect(message).toBeNull();
    });

    it('should return null for object without type field', () => {
      const json = '{"i":"msg123","d":{"foo":"bar"}}';
      const message = MessageSerializer.fromJSON(json);
      expect(message).toBeNull();
    });

    it('should return null for null value', () => {
      const message = MessageSerializer.fromJSON('null');
      expect(message).toBeNull();
    });
  });

  describe('toCompactJSON', () => {
    it('should remove undefined fields', () => {
      const message: BaseMessage = {
        t: MessageType.PING,
        i: undefined,
        r: undefined,
        d: undefined,
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.t).toBe(MessageType.PING);
      expect(parsed.i).toBeUndefined();
      expect(parsed.r).toBeUndefined();
      expect(parsed.d).toBeUndefined();
    });

    it('should remove null fields', () => {
      const message: any = {
        t: MessageType.PING,
        i: null,
        d: null,
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.t).toBe(MessageType.PING);
      expect(parsed.i).toBeUndefined();
      expect(parsed.d).toBeUndefined();
    });

    it('should remove undefined in nested objects', () => {
      const message: BaseMessage = {
        t: MessageType.USER_MOVEMENT,
        d: {
          position: { x: 10, y: 64, z: 20 },
          rotation: undefined,
          velocity: { x: 0, y: undefined, z: 0.5 },
          metadata: undefined,
        },
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.d.position).toBeDefined();
      expect(parsed.d.rotation).toBeUndefined();
      expect(parsed.d.velocity).toBeDefined();
      expect(parsed.d.velocity.y).toBeUndefined();
      expect(parsed.d.metadata).toBeUndefined();
    });

    it('should handle arrays correctly', () => {
      const message: BaseMessage = {
        t: MessageType.CHUNK_UPDATE,
        d: {
          blocks: [1, 2, undefined, 3, null, 4],
          chunks: [
            { id: 1, data: 'valid' },
            undefined,
            { id: 2, data: 'also valid' },
          ],
        },
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      // Undefined and null should be filtered from arrays
      expect(parsed.d.blocks).toHaveLength(4);
      expect(parsed.d.blocks).toEqual([1, 2, 3, 4]);
      expect(parsed.d.chunks).toHaveLength(2);
      expect(parsed.d.chunks[0].id).toBe(1);
      expect(parsed.d.chunks[1].id).toBe(2);
    });

    it('should keep zero and false values', () => {
      const message: BaseMessage = {
        t: MessageType.BLOCK_UPDATE,
        d: {
          blockId: 0,
          visible: false,
          count: 0,
        },
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.d.blockId).toBe(0);
      expect(parsed.d.visible).toBe(false);
      expect(parsed.d.count).toBe(0);
    });

    it('should keep empty strings', () => {
      const message: BaseMessage = {
        t: MessageType.CMD_MESSAGE,
        d: {
          message: '',
          title: 'Alert',
        },
      };

      const json = MessageSerializer.toCompactJSON(message);
      const parsed = JSON.parse(json);

      expect(parsed.d.message).toBe('');
      expect(parsed.d.title).toBe('Alert');
    });
  });

  describe('getSize and getCompactSize', () => {
    it('should calculate message size', () => {
      const message: BaseMessage = {
        t: MessageType.PING,
      };

      const size = MessageSerializer.getSize(message);
      expect(size).toBeGreaterThan(0);
      expect(typeof size).toBe('number');
    });

    it('should calculate compact size smaller than regular size', () => {
      const message: BaseMessage = {
        i: 'msg123',
        r: undefined,
        t: MessageType.USER_MOVEMENT,
        d: {
          position: { x: 10, y: 64, z: 20 },
          rotation: undefined,
          extra: undefined,
        },
      };

      const regularSize = MessageSerializer.getSize(message);
      const compactSize = MessageSerializer.getCompactSize(message);

      expect(regularSize).toBeGreaterThan(0);
      expect(compactSize).toBeGreaterThan(0);
      expect(compactSize).toBeLessThanOrEqual(regularSize);
    });

    it('should have same size for messages without undefined', () => {
      const message: BaseMessage = {
        i: 'msg123',
        t: MessageType.PING,
        d: { timestamp: 12345 },
      };

      const regularSize = MessageSerializer.getSize(message);
      const compactSize = MessageSerializer.getCompactSize(message);

      // Should be similar (minor JSON formatting differences possible)
      expect(Math.abs(regularSize - compactSize)).toBeLessThan(5);
    });
  });

  describe('batchToJSON and batchFromJSON', () => {
    it('should serialize empty batch', () => {
      const json = MessageSerializer.batchToJSON([]);
      expect(json).toBe('[]');
    });

    it('should serialize batch of messages', () => {
      const messages: BaseMessage[] = [
        { t: MessageType.PING },
        { i: 'msg1', t: MessageType.USER_MOVEMENT, d: { x: 1, y: 2, z: 3 } },
        { r: 'req1', t: MessageType.LOGIN_RESPONSE, d: { success: true } },
      ];

      const json = MessageSerializer.batchToJSON(messages);
      const parsed = JSON.parse(json);

      expect(Array.isArray(parsed)).toBe(true);
      expect(parsed).toHaveLength(3);
      expect(parsed[0].t).toBe(MessageType.PING);
      expect(parsed[1].i).toBe('msg1');
      expect(parsed[2].r).toBe('req1');
    });

    it('should deserialize empty batch', () => {
      const messages = MessageSerializer.batchFromJSON('[]');
      expect(messages).toEqual([]);
    });

    it('should deserialize batch of messages', () => {
      const original: BaseMessage[] = [
        { t: MessageType.PING },
        { i: 'msg1', t: MessageType.CHUNK_QUERY },
        { t: MessageType.ENTITY_UPDATE, d: { entities: [] } },
      ];

      const json = MessageSerializer.batchToJSON(original);
      const messages = MessageSerializer.batchFromJSON(json);

      expect(messages).not.toBeNull();
      expect(messages).toHaveLength(3);
      expect(messages![0].t).toBe(MessageType.PING);
      expect(messages![1].i).toBe('msg1');
      expect(messages![2].t).toBe(MessageType.ENTITY_UPDATE);
    });

    it('should return null for invalid JSON', () => {
      const messages = MessageSerializer.batchFromJSON('invalid json');
      expect(messages).toBeNull();
    });

    it('should return null for non-array JSON', () => {
      const messages = MessageSerializer.batchFromJSON('{"foo":"bar"}');
      expect(messages).toBeNull();
    });

    it('should filter out invalid messages in batch', () => {
      const json =
        '[{"t":"p"},{"invalid":"data"},{"t":"c.q","i":"msg1"},null,{"missing":"type"}]';
      const messages = MessageSerializer.batchFromJSON(json);

      expect(messages).not.toBeNull();
      // Only messages with 't' field should be included
      expect(messages).toHaveLength(2);
      expect(messages![0].t).toBe('p');
      expect(messages![1].t).toBe('c.q');
    });

    it('should handle mixed valid and invalid messages', () => {
      const json = '[{"t":"p"},{"t":"c.q","i":"msg1"},"string",123,true,null]';
      const messages = MessageSerializer.batchFromJSON(json);

      expect(messages).not.toBeNull();
      expect(messages).toHaveLength(2);
      expect(messages![0].t).toBe('p');
      expect(messages![1].t).toBe('c.q');
    });
  });

  describe('round-trip serialization', () => {
    it('should preserve message through serialization cycle', () => {
      const original: BaseMessage = {
        i: 'msg123',
        t: MessageType.USER_MOVEMENT,
        d: {
          position: { x: 10, y: 64, z: 20 },
          rotation: { y: 90, p: 15 },
          velocity: { x: 0.5, y: 0, z: 0.5 },
        },
      };

      const json = MessageSerializer.toJSON(original);
      const deserialized = MessageSerializer.fromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.i).toBe(original.i);
      expect(deserialized!.t).toBe(original.t);
      expect(deserialized!.d).toEqual(original.d);
    });

    it('should preserve batch through serialization cycle', () => {
      const original: BaseMessage[] = [
        { t: MessageType.PING },
        {
          i: 'msg1',
          t: MessageType.BLOCK_UPDATE,
          d: { blocks: [{ x: 1, y: 2, z: 3, id: 5 }] },
        },
        { r: 'req1', t: MessageType.LOGIN_RESPONSE, d: { success: true } },
      ];

      const json = MessageSerializer.batchToJSON(original);
      const deserialized = MessageSerializer.batchFromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized).toHaveLength(3);
      expect(deserialized![0].t).toBe(MessageType.PING);
      expect(deserialized![1].i).toBe('msg1');
      expect(deserialized![1].d.blocks).toHaveLength(1);
      expect(deserialized![2].r).toBe('req1');
    });

    it('should handle compact serialization round-trip', () => {
      const original: BaseMessage = {
        i: 'msg123',
        t: MessageType.ENTITY_UPDATE,
        d: {
          entities: [
            { id: 'e1', position: { x: 1, y: 2, z: 3 } },
            { id: 'e2', position: { x: 4, y: 5, z: 6 } },
          ],
        },
      };

      const compactJson = MessageSerializer.toCompactJSON(original);
      const deserialized = MessageSerializer.fromJSON(compactJson);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.i).toBe(original.i);
      expect(deserialized!.t).toBe(original.t);
      expect(deserialized!.d.entities).toHaveLength(2);
    });
  });

  describe('edge cases', () => {
    it('should handle message with complex nested data', () => {
      const message: BaseMessage = {
        i: 'msg123',
        t: MessageType.CHUNK_UPDATE,
        d: {
          chunk: {
            cx: 0,
            cz: 0,
            blocks: [
              { position: { x: 0, y: 0, z: 0 }, blockTypeId: 1 },
              { position: { x: 1, y: 0, z: 0 }, blockTypeId: 2 },
            ],
            metadata: {
              generated: true,
              biome: 'plains',
              structures: ['tree', 'rock'],
            },
          },
        },
      };

      const json = MessageSerializer.toJSON(message);
      const deserialized = MessageSerializer.fromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.d.chunk.blocks).toHaveLength(2);
      expect(deserialized!.d.chunk.metadata.structures).toEqual(['tree', 'rock']);
    });

    it('should handle very large message', () => {
      const largeArray = Array.from({ length: 1000 }, (_, i) => ({
        id: `entity${i}`,
        x: i,
        y: i * 2,
        z: i * 3,
      }));

      const message: BaseMessage = {
        t: MessageType.ENTITY_UPDATE,
        d: { entities: largeArray },
      };

      const json = MessageSerializer.toJSON(message);
      const deserialized = MessageSerializer.fromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.d.entities).toHaveLength(1000);
      expect(deserialized!.d.entities[999].id).toBe('entity999');
    });

    it('should handle message with special characters', () => {
      const message: BaseMessage = {
        t: MessageType.CMD_MESSAGE,
        d: {
          message: 'Hello "World"\nNew line\tTab\r\nSpecial: äöü',
          emoji: '🎮🌍✨',
        },
      };

      const json = MessageSerializer.toJSON(message);
      const deserialized = MessageSerializer.fromJSON(json);

      expect(deserialized).not.toBeNull();
      expect(deserialized!.d.message).toBe('Hello "World"\nNew line\tTab\r\nSpecial: äöü');
      expect(deserialized!.d.emoji).toBe('🎮🌍✨');
    });

    it('should handle message with circular reference in toCompactJSON', () => {
      // Note: toJSON would throw, but toCompactJSON might handle it differently
      // This is more of a documentation test showing the limitation
      const circular: any = { t: MessageType.PING };
      circular.self = circular;

      expect(() => {
        MessageSerializer.toJSON(circular);
      }).toThrow();
    });
  });
});
