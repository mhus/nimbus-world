/**
 * NetworkService - WebSocket connection and message routing
 *
 * Manages WebSocket connection to server, handles automatic login,
 * routes messages to handlers, and manages reconnection.
 */

import {
  BaseMessage,
  RequestMessage,
  MessageType,
  ClientType,
  LoginRequestData,
  getLogger,
  ExceptionHandler, ChunkCoordinate, ChunkDataTransferObject,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { MessageHandler } from '../network/MessageHandler';
import { NotificationType } from '../types/Notification';

const logger = getLogger('NetworkService');

/**
 * Connection state
 */
export enum ConnectionState {
  DISCONNECTED = 'DISCONNECTED',
  CONNECTING = 'CONNECTING',
  CONNECTED = 'CONNECTED',
  RECONNECTING = 'RECONNECTING',
}

/**
 * Pending request for request/response correlation
 */
interface PendingRequest {
  resolve: (data: any) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

/**
 * Event listener
 */
type EventListener = (...args: any[]) => void;

/**
 * NetworkService - Manages WebSocket connection and message routing
 *
 * Features:
 * - WebSocket connection management
 * - Automatic login after connect
 * - Message ID generation
 * - Handler registration and message routing
 * - Request/response correlation
 * - Automatic reconnection with exponential backoff
 * - Ping/pong keep

alive handled by PingMessageHandler
 */
export class NetworkService {
  private ws?: WebSocket;
  private websocketUrl: string;
  private apiUrl: string;
  private connectionState: ConnectionState = ConnectionState.DISCONNECTED;

  private messageIdCounter: number = 0;
  private handlers: Map<MessageType, MessageHandler[]> = new Map();
  private pendingRequests: Map<string, PendingRequest> = new Map();
  private eventListeners: Map<string, EventListener[]> = new Map();

  private reconnectAttempt: number = 0;
  private maxReconnectAttempts: number = 5;
  private shouldReconnect: boolean = true;
  private reconnectIntervalMs: number = 5000; // 5 seconds between reconnect attempts

  constructor(private appContext: AppContext) {
    // websocketUrl will be set after server config is loaded
    this.websocketUrl = appContext.config.websocketUrl || '';
    this.apiUrl = appContext.config.apiUrl;

    logger.debug('NetworkService initialized', {
      websocketUrl: this.websocketUrl || '(will be set from server config)',
      apiUrl: this.apiUrl,
    });
  }

  /**
   * Get cache version string from worldInfo.version, falling back to Date.now().
   * Used as query parameter for cache-busting resource URLs.
   */
  private getCacheVersion(): number {
    return this.appContext.worldInfo?.version || Date.now();
  }

  /**
   * Update WebSocket URL after server config is loaded
   * Called by ConfigService after loading EngineConfiguration
   */
  updateWebSocketUrl(): void {
    if (this.appContext.config.websocketUrl) {
      this.websocketUrl = this.appContext.config.websocketUrl;
      logger.info('WebSocket URL updated from server config', {
        websocketUrl: this.websocketUrl,
      });
    }
  }

  /**
   * Connect to WebSocket server and automatically send login
   */
  async connect(): Promise<void> {
    if (this.connectionState !== ConnectionState.DISCONNECTED) {
      logger.warn('Already connected or connecting');
      return;
    }

    try {
      this.connectionState = ConnectionState.CONNECTING;
      logger.debug('Connecting to WebSocket server', { url: this.websocketUrl });

      this.ws = new WebSocket(this.websocketUrl);
      // Safari has issues with large ArrayBuffer WebSocket messages (truncation),
      // so use 'blob' on Safari and convert async. Chrome/Firefox use 'arraybuffer' directly.
      const isSafari = /^((?!chrome|android).)*safari/i.test(navigator.userAgent);
      this.ws.binaryType = isSafari ? 'blob' : 'arraybuffer';

      this.ws.onopen = () => this.onOpen();
      this.ws.onmessage = (event) => this.onMessage(event);
      this.ws.onerror = (error) => this.onError(error);
      this.ws.onclose = () => this.onClose();

      // Wait for connection
      await new Promise<void>((resolve, reject) => {
        const timeout = setTimeout(() => {
          reject(new Error('Connection timeout'));
        }, 10000);

        this.once('connected', () => {
          clearTimeout(timeout);
          resolve();
        });

        this.once('error', (error) => {
          clearTimeout(timeout);
          reject(error);
        });
      });
    } catch (error) {
      this.connectionState = ConnectionState.DISCONNECTED;
      throw ExceptionHandler.handleAndRethrow(error, 'NetworkService.connect');
    }
  }

  /**
   * Enable or disable automatic reconnection.
   * Used by DiedCommand to prevent reconnection after server-initiated death disconnect.
   */
  setReconnectEnabled(enabled: boolean): void {
    this.shouldReconnect = enabled;
  }

  /**
   * Disconnect from WebSocket server
   */
  async disconnect(): Promise<void> {
    this.shouldReconnect = false;

    if (this.ws) {
      this.ws.close();
      this.ws = undefined;
    }

    this.connectionState = ConnectionState.DISCONNECTED;
    logger.debug('Disconnected from WebSocket server');
  }

  /**
   * Send a message to the server
   */
  send<T>(message: BaseMessage<T>): void {
    if (!this.isConnected()) {
      throw new Error('Not connected to server');
    }

    try {
      const json = JSON.stringify(message);
      this.ws!.send(json);

      logger.debug('Sent message', { type: message.t, id: message.i });
    } catch (error) {
      throw ExceptionHandler.handleAndRethrow(error, 'NetworkService.send', { message });
    }
  }

  /**
   * Send a request and wait for response
   */
  async request<T, R>(message: RequestMessage<T>): Promise<R> {
    if (!message.i) {
      throw new Error('Request message must have an ID');
    }

    return new Promise<R>((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingRequests.delete(message.i!);
        reject(new Error(`Request timeout: ${message.t}`));
      }, 30000); // 30 second timeout

      this.pendingRequests.set(message.i, {
        resolve,
        reject,
        timeout,
      });

      this.send(message);
    });
  }

  /**
   * Register a message handler
   */
  registerHandler(handler: MessageHandler): void {
    const handlers = this.handlers.get(handler.messageType) || [];
    handlers.push(handler);
    this.handlers.set(handler.messageType, handlers);

    logger.debug('Registered handler', { messageType: handler.messageType });
  }

  /**
   * Check if connected to server
   */
  isConnected(): boolean {
    return this.connectionState === ConnectionState.CONNECTED && this.ws?.readyState === WebSocket.OPEN;
  }

  /**
   * Get current connection state
   */
  getConnectionState(): ConnectionState {
    return this.connectionState;
  }

  /**
   * Generate unique message ID
   */
  generateMessageId(): string {
    return `msg_${Date.now()}_${++this.messageIdCounter}`;
  }

  /**
   * Add event listener
   */
  on(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event) || [];
    listeners.push(listener);
    this.eventListeners.set(event, listeners);
  }

  /**
   * Add one-time event listener
   */
  once(event: string, listener: EventListener): void {
    const onceListener = (...args: any[]) => {
      this.off(event, onceListener);
      listener(...args);
    };
    this.on(event, onceListener);
  }

  /**
   * Remove event listener
   */
  off(event: string, listener: EventListener): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index !== -1) {
        listeners.splice(index, 1);
      }
    }
  }

  /**
   * Emit event
   */
  emit(event: string, ...args: any[]): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => {
        try {
          listener(...args);
        } catch (error) {
          ExceptionHandler.handle(error, 'NetworkService.emit', { event });
        }
      });
    }
  }

  /**
   * WebSocket opened
   */
  private onOpen(): void {
    const isReconnect = this.reconnectAttempt > 0;

    this.connectionState = ConnectionState.CONNECTED;
    this.reconnectAttempt = 0;

    if (isReconnect) {
      logger.debug('Reconnected to WebSocket server');
      this.emit('reconnected');

      // Clear center text notification
      const notificationService = this.appContext.services.notification;
      if (notificationService) {
        notificationService.clearCenterText();

        // Show reconnection success notification
        notificationService.newNotification(
          NotificationType.SYSTEM_INFO,
          null,
          'Verbindung wiederhergestellt'
        );
      }
    } else {
      logger.debug('Connected to WebSocket server');
      this.emit('connected');
    }

    if (this.appContext.sessionId) {
      logger.info('sending login with sessionId', {
        sessionId: this.appContext.sessionId,
      });
      this.sendLoginWithSession(this.appContext.sessionId);
    } else {
      logger.warn('No sessionId available, resending original login');
    }

  }

  /**
   * Handle binary WebSocket frame (compressed chunk)
   */
  private async handleBinaryChunkMessage(data: ArrayBuffer): Promise<void> {
    try {
      // 1. Read header length (first 4 bytes, big-endian int32)
      const view = new DataView(data);
      const headerLength = view.getInt32(0);

      // 2. Extract and parse header JSON
      const headerBytes = new Uint8Array(data, 4, headerLength);
      const headerText = new TextDecoder('utf-8').decode(headerBytes);
      const header = JSON.parse(headerText);

      // 3. Extract compressed data
      const compressedBytes = new Uint8Array(data, 4 + headerLength);

      logger.debug('Received binary chunk', {
        cx: header.cx,
        cz: header.cz,
        headerSize: headerLength,
        compressedSize: compressedBytes.length,
        totalSize: data.byteLength,
      });

      // 4. Build ChunkDataTransferObject with compressed data
      // Decompression will happen in ChunkService.onChunkUpdate
      const chunkData: ChunkDataTransferObject = {
        cx: header.cx,
        cz: header.cz,
        b: [],  // Will be filled after decompression
        i: header.i,
        s: header.s,
        c: compressedBytes,  // Pass compressed data to ChunkService
      };

      // 6. Process chunk normally via appContext
      const chunkService = this.appContext.services.chunk;
      if (!chunkService) {
        logger.error('ChunkService not available, cannot process binary chunk', {
          cx: header.cx,
          cz: header.cz,
        });
        return;
      }

      await chunkService.onChunkUpdate([chunkData]);

    } catch (error) {
      ExceptionHandler.handle(error, 'NetworkService.handleBinaryChunkMessage', {
        dataSize: data.byteLength,
      });
    }
  }

  /**
   * WebSocket message received
   */
  private onMessage(event: MessageEvent): void {
    try {
      // Handle binary frames (compressed chunks)
      if (event.data instanceof ArrayBuffer) {
        this.handleBinaryChunkMessage(event.data);
        return;
      }
      if (event.data instanceof Blob) {
        event.data.arrayBuffer()
          .then(ab => this.handleBinaryChunkMessage(ab))
          .catch(err => ExceptionHandler.handle(err, 'NetworkService.onMessage.blob'));
        return;
      }

      // Handle text frames (normal JSON messages)
      const raw = JSON.parse(event.data);

      // Handle base64-encoded binary chunk data sent as text
      // (workaround for Safari which truncates large binary WebSocket frames)
      if (raw.t === 'CHUNK_BINARY') {
        const base64 = raw.c as string;
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        const chunkData: ChunkDataTransferObject = {
          cx: raw.cx,
          cz: raw.cz,
          b: [],
          i: raw.i,
          s: raw.s,
          c: bytes,
        };
        const chunkService = this.appContext.services.chunk;
        if (chunkService) {
          chunkService.onChunkUpdate([chunkData]);
        }
        return;
      }

      const message: BaseMessage = raw;

      logger.debug('Received message', { type: message.t, responseId: message.r });

      // Handle response to pending request
      if (message.r) {
        const pending = this.pendingRequests.get(message.r);
        if (pending) {
          clearTimeout(pending.timeout);
          this.pendingRequests.delete(message.r);
          pending.resolve(message.d);
          return;
        }
      }

      // Route to handlers
      const handlers = this.handlers.get(message.t);
      if (handlers && handlers.length > 0) {
        handlers.forEach(handler => {
          try {
            // Support both sync and async handlers
            const result = handler.handle(message);
            if (result instanceof Promise) {
              result.catch(error => {
                ExceptionHandler.handle(error, 'NetworkService.onMessage.handler.async', {
                  messageType: message.t,
                });
              });
            }
          } catch (error) {
            ExceptionHandler.handle(error, 'NetworkService.onMessage.handler', {
              messageType: message.t,
            });
          }
        });
      } else {
        logger.warn('No handler registered for message type', {
          type: message.t,
          registeredTypes: Array.from(this.handlers.keys()),
        });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'NetworkService.onMessage', {
        data: event.data,
      });
    }
  }

  /**
   * WebSocket error
   */
  private onError(error: Event): void {
    logger.error('WebSocket error', {}, error as any);
    this.emit('error', error);
  }

  /**
   * WebSocket closed
   */
  private onClose(): void {
    const wasConnected = this.connectionState === ConnectionState.CONNECTED;

    this.connectionState = ConnectionState.DISCONNECTED;
    logger.debug('WebSocket connection closed');

    this.emit('disconnected');

    if (wasConnected) {
      const playerService = this.appContext.services.player;
      const playerAlreadyDead = playerService?.isPlayerDead() || false;

      if (!playerAlreadyDead) {
        // Set DEAD mode when disconnected (only if not already in death state from server)
        if (playerService) {
          playerService.setPlayerDeadState(true);
        }

        // Show center text notification
        const notificationService = this.appContext.services.notification;
        if (notificationService) {
          notificationService.setCenterText('Disconnected from server');
        }
      }
    }

    // Attempt reconnection or redirect
    if (wasConnected && !this.shouldReconnect) {
      // Death disconnect — redirect to exit URL
      const exitUrl = sessionStorage.getItem('nimbus_exitUrl') || this.appContext.config.exitUrl || '/login';
      logger.info('Death disconnect, redirecting to exit URL', { exitUrl });
      if (typeof window !== 'undefined' && window.location) {
        setTimeout(() => {
          window.location.href = exitUrl;
        }, 2000);
      }
    } else if (this.shouldReconnect && wasConnected) {
      this.attemptReconnect();
    }
  }

  /**
   * Attempt to reconnect every 5 seconds
   * After 5 failed attempts, redirect to exitUrl
   */
  private attemptReconnect(): void {
    if (this.reconnectAttempt >= this.maxReconnectAttempts) {
      logger.error('Max reconnection attempts reached, redirecting to exit URL');
      this.emit('reconnect_failed');

      // Show error notification
      const notificationService = this.appContext.services.notification;
      if (notificationService) {
        notificationService.newNotification(
          NotificationType.SYSTEM_ERROR,
          null,
          'Verbindung konnte nicht wiederhergestellt werden'
        );
      }

      // Redirect to exit URL
      const exitUrl = sessionStorage.getItem('nimbus_exitUrl') || this.appContext.config.exitUrl || '/login';
      logger.debug('Redirecting to exit URL', { exitUrl });

      // Use window.location for redirect
      if (typeof window !== 'undefined' && window.location) {
        setTimeout(() => {
          window.location.href = exitUrl;
        }, 2000); // Wait 2 seconds before redirect to show notification
      }

      return;
    }

    this.reconnectAttempt++;

    logger.debug('Attempting reconnect', {
      attempt: this.reconnectAttempt,
      maxAttempts: this.maxReconnectAttempts,
      delay: this.reconnectIntervalMs,
    });

    // Show notification about reconnecting
    const notificationService = this.appContext.services.notification;
    if (notificationService) {
      notificationService.newNotification(
        NotificationType.SYSTEM_ERROR,
        null,
        `Verbindung verloren, versuche wieder zu verbinden... (Versuch ${this.reconnectAttempt}/${this.maxReconnectAttempts})`
      );
    }

    this.emit('reconnecting', this.reconnectAttempt);

    setTimeout(() => {
      if (this.shouldReconnect && this.connectionState !== ConnectionState.CONNECTED) {
        // Reset state before attempting to connect
        this.connectionState = ConnectionState.DISCONNECTED;

        this.connect().catch(error => {
          ExceptionHandler.handle(error, 'NetworkService.attemptReconnect');
          // On error, try again only if still disconnected
          if (this.connectionState === ConnectionState.DISCONNECTED && this.shouldReconnect) {
            this.attemptReconnect();
          }
        });
      } else {
        logger.debug('Reconnect attempt cancelled - already connected or should not reconnect');
      }
    }, this.reconnectIntervalMs);
  }

  /**
   * Send login message with sessionId for session restoration
   */
  private sendLoginWithSession(sessionId: string): void {
    const loginMessage: RequestMessage<LoginRequestData> = {
      i: this.generateMessageId(),
      t: MessageType.LOGIN,
      d: {
        sessionId,
        worldId: this.appContext.config.worldId,
        clientType: ClientType.WEB,
      },
    };

    this.send(loginMessage);

    logger.debug('Sent login message with sessionId', { sessionId, worldId: loginMessage.d!.worldId });
  }

  /**
   * Send block interaction to server
   *
   * @param x Block X position
   * @param y Block Y position
   * @param z Block Z position
   * @param action Action type ('click', 'collision')
   * @param params Action parameters
   * @param id Block ID from metadata (optional)
   * @param gId Block group ID (optional)
   */
  sendBlockInteraction(
    x: number,
    y: number,
    z: number,
    action: string = 'click',
    params?: Record<string, any>,
    id?: string,
    gId?: string,
    shortcutKey?: string
  ): void {
    const data: any = {
      x,
      y,
      z,
      id,
      gId,
      ac: action,
      pa: params || {},
    };
    if (shortcutKey) {
      data.sc = shortcutKey;
    }
    const message: RequestMessage<any> = {
      i: this.generateMessageId(),
      t: MessageType.BLOCK_INTERACTION,
      d: data,
    };

    this.send(message);

    logger.debug('Sent block interaction', {
      position: { x, y, z },
      id,
      gId,
      action,
      params,
    });
  }

  /**
   * Send entity interaction to server
   *
   * @param entityId Entity ID
   * @param action Action type (e.g., 'click', 'use', 'talk', 'fireShortcut')
   * @param clickType Mouse button number (0 = left, 1 = middle, 2 = right, etc.) - only for 'click' action
   * @param additionalParams Additional parameters to merge into pa
   */
  sendEntityInteraction(
    entityId: string,
    action: string = 'click',
    clickType?: number,
    additionalParams?: Record<string, any>,
    shortcutKey?: string
  ): void {
    const params: any = { ...additionalParams };
    if (action === 'click' && clickType !== undefined) {
      params.clickType = clickType;
    }

    const data: any = {
      entityId,
      ts: Date.now(),
      ac: action,
      pa: params,
    };
    if (shortcutKey) {
      data.sc = shortcutKey;
    }
    const message: RequestMessage<any> = {
      i: this.generateMessageId(),
      t: MessageType.ENTITY_INTERACTION,
      d: data,
    };

    this.send(message);

    logger.debug('Sent entity interaction', {
      entityId,
      action,
      clickType,
      params: additionalParams,
    });
  }

  /**
   * Send a simple interaction without block/entity target
   * Used when a shortcut fires but nothing is selected
   * @param action Action type (e.g., 'click')
   * @param shortcutKey Shortcut key (e.g., 'key1', 'click1')
   * @param additionalData Optional additional data to include in the message
   */
  sendSimpleInteraction(action: string, shortcutKey: string, additionalData?: Record<string, any>): void {
    // Discrete fire-and-forget interaction: silently drop it while
    // disconnected instead of letting send() throw into an event/modifier
    // callback that has no error handling.
    if (!this.isConnected()) {
      logger.debug('Skipping simple interaction, not connected', { action, shortcutKey });
      return;
    }
    const message: BaseMessage<any> = {
      i: this.generateMessageId(),
      t: MessageType.SIMPLE_INTERACTION,
      d: { ac: action, sc: shortcutKey, ...additionalData },
    };
    this.send(message);
    logger.debug('Sent simple interaction', { action, shortcutKey, additionalData });
  }

  /**
   * Get API URL for REST calls
   * @private Use specific URL methods instead (getAssetUrl, getEntityModelUrl, etc.)
   */
  private getApiUrl(): string {
    return this.apiUrl;
  }

  /**
   * Get WebSocket URL
   */
  getWebSocketUrl(): string {
    return this.websocketUrl;
  }

  /**
   * Get asset URL for a given asset path
   *
   * Constructs full URL using apiUrl and worldInfo.assetPath
   *
   * @param assetPath - Normalized asset path (e.g., "textures/block/basic/stone.png")
   * @returns Full asset URL
   */
  getAssetUrl(assetPath: string): string {
    const worldId = this.appContext.worldInfo?.worldId;
    const worldAssetPath = `/player/worlds/${worldId}/assets`;

    return `${this.apiUrl}${worldAssetPath}/${assetPath}`;
  }

  /**
   * Get speech URL for streaming speech audio.
   *
   * The stream is authenticated via the session cookie (the consumer fetches
   * with credentials:'include'), so no auth token is placed in the URL. Only
   * the sessionId is passed as a query parameter for stream association.
   *
   * @param streamPath - Speech stream path (e.g., "welcome" or "tutorial/intro")
   * @returns Full speech URL
   */
  getSpeechUrl(streamPath: string): string {
    const worldId = this.appContext.worldInfo?.worldId;
    const sessionId = this.appContext.sessionId;

    // Build base URL
    const baseUrl = `${this.apiUrl}/player/world/${worldId}/speech/${streamPath}`;

    // Add query parameters
    const params = new URLSearchParams();
    if (sessionId) {
      params.append('sessionId', sessionId);
    }

    const queryString = params.toString();
    return queryString ? `${baseUrl}?${queryString}` : baseUrl;
  }

  /**
   * Get entity model URL
   *
   * @param entityTypeId - Entity type ID
   * @returns Full entity model URL with cache-busting timestamp
   */
  getEntityModelUrl(entityTypeId: string): string {
    return `${this.apiUrl}/player/world/entitymodel/${entityTypeId}?v=${this.getCacheVersion()}`;
  }

  /**
   * Get backdrop URL
   *
   * @param backdropTypeId - Backdrop type ID
   * @returns Full backdrop URL with cache-busting timestamp
   */
  getBackdropUrl(backdropTypeId: string): string {
    return `${this.apiUrl}/player/world/backdrop/${backdropTypeId}?v=${this.getCacheVersion()}`;
  }

  /**
   * Get entity URL
   *
   * @param entityId - Entity ID
   * @returns Full entity URL with cache-busting timestamp
   */
  getEntityUrl(entityId: string): string {
    return `${this.apiUrl}/player/world/entity/${entityId}?v=${this.getCacheVersion()}`;
  }

  /**
   * Get block types range URL
   * @deprecated Use getBlockTypesChunkUrl instead
   *
   * @param from - Start of range
   * @param to - End of range
   * @returns Full block types range URL with cache-busting timestamp
   */
  getBlockTypesRangeUrl(from: number, to: number): string {
    return `${this.apiUrl}/player/world/blocktypes/${from}/${to}?v=${this.getCacheVersion()}`;
  }

  /**
   * Get block types chunk URL for a specific group
   *
   * @param groupName - BlockType group name (e.g., 'core', 'w', 'custom')
   * @returns Full block types chunk URL with cache-busting timestamp
   */
  getBlockTypesChunkUrl(groupName: string): string {
    return `${this.apiUrl}/player/world/blocktypeschunk/${groupName}?v=${this.getCacheVersion()}`;
  }

  /**
   * Get item URL
   *
   * @param itemId - Item ID
   * @returns Full item URL
   */
  getItemUrl(itemId: string): string {
    const worldId = this.appContext.worldInfo?.worldId;
    return `${this.apiUrl}/player/worlds/${worldId}/item/${itemId}`;
  }

  /**
   * Get item data URL
   *
   * @param itemId - Item ID
   * @returns Full item data URL
   */
  getItemDataUrl(itemId: string): string {
    const worldId = this.appContext.worldInfo?.worldId;
    return `${this.apiUrl}/player/worlds/${worldId}/itemdata/${itemId}`;
  }

  /**
   * Get base editor URL from world configuration
   *
   * @returns Editor base URL or null if no editor URL configured
   */
  getComponentBaseUrl(): string | null {
    const url = this.appContext.worldInfo?.editorUrl;

    if (!url) {
      logger.warn('No editor URL configured for this world');
      return null;
    }

    return url;
  }

  /**
   * Send effect parameter update to server
   *
   * Used by ShortcutService to send position updates for running effects.
   *
   * @param effectId Effect ID
   * @param paramName Parameter name (e.g., 'targetPos')
   * @param value Parameter value (will be serialized)
   * @param targeting Optional targeting context for network synchronization
   */
  sendEffectParameterUpdate(
    effectId: string,
    affectedChunks: Array<ChunkCoordinate>,
    paramName: string,
    value: any,
    targeting?: import('@nimbus/shared').SerializableTargetingContext
  ): void {
    try {
      // Serialize value - extract only JSON-serializable data
      let serializedValue = value;
      if (value && typeof value === 'object') {
        // For Vector3 or position objects
        if (value.x !== undefined && value.y !== undefined && value.z !== undefined) {
          serializedValue = { x: value.x, y: value.y, z: value.z };
        }
      }

      const updateData: any = {
        effectId,
        paramName,
        value: serializedValue,
      };

      updateData.chunks = affectedChunks;

      // Add targeting context if provided
      if (targeting) {
        updateData.targeting = targeting;
      }

      this.send({
        t: MessageType.EFFECT_PARAMETER_UPDATE,
        d: updateData,
      });

      logger.debug('Effect parameter update sent to server', {
        effectId,
        paramName,
        hasTargeting: !!targeting,
        targetingMode: targeting?.mode,
      });
    } catch (error) {
      logger.warn('Failed to send effect parameter update', {
        error: (error as Error).message,
        effectId,
        paramName,
      });
    }
  }

}
