/**
 * Ping/Pong messages for connection keepalive and latency measurement
 */

import type { BaseMessage } from '../BaseMessage';

/**
 * Ping data (Client -> Server)
 */
export interface PingData {
  /** Client timestamp when ping was sent */
  cTs: number; //javaType: long
}

/**
 * Pong data (Server -> Client)
 */
export interface PongData {
  /** Client timestamp (echoed back from ping) */
  cTs: number; //javaType: long

  /** Server timestamp when pong was sent */
  sTs: number; //javaType: long
}

/**
 * Ping message (Client -> Server)
 * Message type: "p"
 *
 * Client sends regularly to maintain connection and measure latency.
 * Server responds with same message ID in 'r' field.
 *
 * Timeout: pingInterval + 10 seconds buffer
 * deadline = lastPingAt + pingInterval*1000 + 10000
 */
export interface PingMessage extends BaseMessage<PingData> {
  i: string;
}

/**
 * Pong message (Server -> Client)
 * Message type: "p"
 *
 * Response to ping with same message ID in 'r' field.
 * Returns client timestamp and adds server timestamp for latency calculation.
 */
export interface PongMessage extends BaseMessage<PongData> {
  r: string;
}
