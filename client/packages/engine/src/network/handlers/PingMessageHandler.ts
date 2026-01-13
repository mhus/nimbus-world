/**
 * PingMessageHandler - Handles ping/pong keep-alive
 *
 * Sends regular ping messages to server and handles pong responses
 * to maintain connection and measure network latency.
 */

import {
  BaseMessage,
  RequestMessage,
  MessageType,
  PingData,
  PongData,
  getLogger,
  ExceptionHandler,
} from '@nimbus/shared';
import { MessageHandler } from '../MessageHandler';
import type { AppContext } from '../../AppContext';
import type { NetworkService } from '../../services/NetworkService';

const logger = getLogger('PingMessageHandler');

/**
 * Handles ping/pong keep-alive mechanism with RTT and server lag tracking
 */
export class PingMessageHandler extends MessageHandler {
  readonly messageType = MessageType.PING;

  private pingInterval?: NodeJS.Timeout;
  private lastPongAt: number = 0;
  private _pingIntervalMs: number = 5000; // Default 5 seconds (more frequent)

  // RTT (Round-Trip Time) tracking
  private _currentRTT: number = 0;
  private _averageRTT: number = 0;
  private rttHistory: number[] = [];
  private readonly rttHistorySize: number = 10; // Keep last 10 RTT measurements

  // Server timestamp lag tracking
  private _serverTimestampLag: number = 0;
  private _averageServerLag: number = 0;
  private lagHistory: number[] = [];
  private readonly lagHistorySize: number = 10; // Keep last 10 lag measurements

  // Ping tracking for RTT calculation
  private pendingPings: Map<string, number> = new Map(); // messageId -> clientTimestamp

  constructor(
    private networkService: NetworkService,
    _appContext: AppContext
  ) {
    super();
  }

  /**
   * Get current ping interval in milliseconds
   */
  get pingIntervalMs(): number {
    return this._pingIntervalMs;
  }

  /**
   * Set ping interval in milliseconds
   * Restarts the ping interval if it's currently running
   */
  set pingIntervalMs(value: number) {
    if (value < 100) {
      logger.debug('Ping interval too low, setting minimum to 100ms', { requestedValue: value });
      value = 100;
    }

    if (value > 60000) {
      logger.debug('Ping interval too high, setting maximum to 60000ms', { requestedValue: value });
      value = 60000;
    }

    const wasRunning = this.pingInterval !== undefined;
    this._pingIntervalMs = value;

    logger.debug('Ping interval changed', { intervalMs: value });

    // Restart interval if it was running
    if (wasRunning) {
      this.startPingInterval();
    }
  }

  /**
   * Get current RTT (Round-Trip Time) in milliseconds
   */
  get currentRTT(): number {
    return this._currentRTT;
  }

  /**
   * Get average RTT over last measurements
   */
  get averageRTT(): number {
    return this._averageRTT;
  }

  /**
   * Get current server timestamp lag in milliseconds
   * Positive value means server is ahead, negative means server is behind
   */
  get serverTimestampLag(): number {
    return this._serverTimestampLag;
  }

  /**
   * Get average server timestamp lag over last measurements
   */
  get averageServerLag(): number {
    return this._averageServerLag;
  }

  /**
   * Start ping interval
   */
  startPingInterval(): void {
    this.stopPingInterval();

    logger.debug('Starting ping interval', { intervalMs: this._pingIntervalMs });

    this.pingInterval = setInterval(() => {
      this.sendPing();
    }, this._pingIntervalMs);

    // Send initial ping
    this.sendPing();
  }

  /**
   * Start ping interval based on world settings (seconds)
   * @deprecated Use startPingInterval() and set pingIntervalMs property
   */
  startPingIntervalSeconds(intervalSeconds: number): void {
    this._pingIntervalMs = intervalSeconds * 1000;
    this.startPingInterval();
  }

  /**
   * Stop ping interval
   */
  stopPingInterval(): void {
    if (this.pingInterval) {
      clearInterval(this.pingInterval);
      this.pingInterval = undefined;
      logger.debug('Ping interval stopped');
    }
  }

  /**
   * Send ping message to server
   */
  private sendPing(): void {
    try {
      if (!this.networkService.isConnected()) {
        logger.debug('Not connected, skipping ping');
        return;
      }

      const messageId = this.networkService.generateMessageId();
      const clientTimestamp = Date.now();

      const pingMsg: RequestMessage<PingData> = {
        i: messageId,
        t: MessageType.PING,
        d: {
          cTs: clientTimestamp,
        },
      };

      // Store client timestamp for RTT calculation
      this.pendingPings.set(messageId, clientTimestamp);

      this.networkService.send(pingMsg);

      logger.debug('Ping sent', { messageId, clientTimestamp });

      // Check for timeout (no pong received for 2x ping interval)
      if (this.lastPongAt > 0) {
        const timeSinceLastPong = Date.now() - this.lastPongAt;
        const timeoutThreshold = this._pingIntervalMs * 2;

        if (timeSinceLastPong > timeoutThreshold) {
          logger.warn('Ping timeout - no pong received', {
            timeSinceLastPong,
            timeoutThreshold,
          });
        }
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'PingMessageHandler.sendPing');
    }
  }

  /**
   * Handle pong response from server
   */
  handle(message: BaseMessage): void {
    try {
      this.lastPongAt = Date.now();

      // Extract pong data
      const pongData = message.d as PongData;
      const responseId = message.r;

      if (!responseId || !pongData) {
        logger.warn('Invalid pong message', { message });
        return;
      }

      // Get original ping timestamp
      const pingClientTimestamp = this.pendingPings.get(responseId);
      if (!pingClientTimestamp) {
        logger.debug('Received pong for unknown ping', { responseId });
        return;
      }

      // Clean up pending ping
      this.pendingPings.delete(responseId);

      const nowClientTimestamp = Date.now();

      // Calculate RTT (Round-Trip Time)
      const rtt = nowClientTimestamp - pingClientTimestamp;
      this._currentRTT = rtt;

      // Update RTT history and average
      this.rttHistory.push(rtt);
      if (this.rttHistory.length > this.rttHistorySize) {
        this.rttHistory.shift();
      }
      this._averageRTT = this.rttHistory.reduce((a, b) => a + b, 0) / this.rttHistory.length;

      // Calculate server timestamp lag
      // Estimate: server time = (client time at ping + RTT/2)
      const estimatedServerTime = pingClientTimestamp + rtt / 2;
      const serverTimestamp = pongData.sTs;
      const lag = serverTimestamp - estimatedServerTime;
      this._serverTimestampLag = lag;

      // Update lag history and average
      this.lagHistory.push(lag);
      if (this.lagHistory.length > this.lagHistorySize) {
        this.lagHistory.shift();
      }
      this._averageServerLag = this.lagHistory.reduce((a, b) => a + b, 0) / this.lagHistory.length;

      logger.debug('Pong received', {
        responseId,
        rtt,
        averageRTT: this._averageRTT,
        serverLag: lag,
        averageServerLag: this._averageServerLag,
        clientTime: nowClientTimestamp,
        serverTime: serverTimestamp,
      });
    } catch (error) {
      ExceptionHandler.handle(error, 'PingMessageHandler.handle', { message });
    }
  }

  /**
   * Get network statistics
   */
  getNetworkStats(): {
    currentRTT: number;
    averageRTT: number;
    serverTimestampLag: number;
    averageServerLag: number;
    pingIntervalMs: number;
  } {
    return {
      currentRTT: this._currentRTT,
      averageRTT: this._averageRTT,
      serverTimestampLag: this._serverTimestampLag,
      averageServerLag: this._averageServerLag,
      pingIntervalMs: this._pingIntervalMs,
    };
  }
}
