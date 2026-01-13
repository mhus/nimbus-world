/**
 * Message serialization utilities
 * Convert between message types and JSON
 */

import type { BaseMessage } from '../network/BaseMessage';

/**
 * Message serialization helpers
 */
export namespace MessageSerializer {
  /**
   * Serialize message to JSON string
   * @param message Message to serialize
   * @returns JSON string
   */
  export function toJSON(message: BaseMessage): string {
    return JSON.stringify(message);
  }

  /**
   * Deserialize message from JSON string
   * @param json JSON string
   * @returns Message or null if invalid
   */
  export function fromJSON(json: string): BaseMessage | null {
    try {
      const data = JSON.parse(json);

      if (!data || typeof data !== 'object' || !data.t) {
        return null;
      }

      return data as BaseMessage;
    } catch (e) {
      console.error('Failed to parse message JSON:', e);
      return null;
    }
  }

  /**
   * Serialize message with compression (remove undefined/null)
   * @param message Message to serialize
   * @returns Compact JSON string
   */
  export function toCompactJSON(message: BaseMessage): string {
    const compact = removeUndefined(message);
    return JSON.stringify(compact);
  }

  /**
   * Remove undefined and null values from object (recursive)
   * @param obj Object to clean
   * @returns Cleaned object
   */
  function removeUndefined(obj: any): any {
    if (obj === null || obj === undefined) {
      return undefined;
    }

    if (Array.isArray(obj)) {
      return obj.map(removeUndefined).filter((v) => v !== undefined);
    }

    if (typeof obj === 'object') {
      const cleaned: any = {};
      Object.keys(obj).forEach((key) => {
        const value = removeUndefined(obj[key]);
        if (value !== undefined) {
          cleaned[key] = value;
        }
      });
      return cleaned;
    }

    return obj;
  }

  /**
   * Calculate message size in bytes
   * @param message Message
   * @returns Size in bytes
   */
  export function getSize(message: BaseMessage): number {
    const json = toJSON(message);
    return new Blob([json]).size;
  }

  /**
   * Calculate compressed message size
   * @param message Message
   * @returns Size in bytes
   */
  export function getCompactSize(message: BaseMessage): number {
    const json = toCompactJSON(message);
    return new Blob([json]).size;
  }

  /**
   * Batch serialize messages
   * @param messages Array of messages
   * @returns JSON string
   */
  export function batchToJSON(messages: BaseMessage[]): string {
    return JSON.stringify(messages);
  }

  /**
   * Batch deserialize messages
   * @param json JSON string
   * @returns Array of messages or null
   */
  export function batchFromJSON(json: string): BaseMessage[] | null {
    try {
      const data = JSON.parse(json);

      if (!Array.isArray(data)) {
        return null;
      }

      return data.filter(
        (msg) => msg && typeof msg === 'object' && msg.t
      ) as BaseMessage[];
    } catch (e) {
      console.error('Failed to parse batch messages JSON:', e);
      return null;
    }
  }
}
