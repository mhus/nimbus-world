# Logger Usage - Nimbus Voxel Engine

Lightweight, type-safe logging framework for client and server.

## Features

- ✅ **6 log levels**: FATAL, ERROR, WARN, INFO, DEBUG, TRACE
- ✅ **Named loggers**: Per-module logger instances
- ✅ **Configurable**: Global and per-logger level control
- ✅ **Environment-based**: Configure via env variables
- ✅ **Structured logging**: Optional data/context objects
- ✅ **Error support**: Automatic stack traces
- ✅ **Custom transports**: Pluggable output destinations
- ✅ **Custom formatters**: Flexible message formatting
- ✅ **Zero dependencies**: Pure TypeScript implementation

## Quick Start

```typescript
import { getLogger } from '@nimbus/shared';

const logger = getLogger('MyService');

logger.info('Service initialized');
logger.debug('Debug information', { key: 'value' });
logger.error('Operation failed', { userId: 123 }, error);
```

## Log Levels

| Level | Value | Usage |
|-------|-------|-------|
| FATAL | 0 | System must stop |
| ERROR | 1 | Operation failed |
| WARN | 2 | May cause issues |
| INFO | 3 | Informational (default) |
| DEBUG | 4 | Debug information |
| TRACE | 5 | Verbose/trace logging |

**Lower values = higher priority**

## Basic Usage

### Creating Loggers

```typescript
import { getLogger } from '@nimbus/shared';

// Get named logger (recommended)
const logger = getLogger('NetworkService');

// Get logger for class
class ChunkService {
  private logger = getLogger('ChunkService');

  loadChunk(cx: number, cz: number) {
    this.logger.info('Loading chunk', { cx, cz });
  }
}
```

### Logging Methods

```typescript
// Fatal - system must stop
logger.fatal('Database connection lost');

// Error - operation failed
logger.error('Failed to load chunk', { cx: 0, cz: 0 }, error);

// Warn - may cause issues
logger.warn('Chunk load timeout', { cx: 0, cz: 0 });

// Info - informational (default level)
logger.info('Server started', { port: 3000 });

// Debug - debug information
logger.debug('Processing block update', { blockId: 42 });

// Trace - verbose logging
logger.trace('WebSocket message received', { type: 'c.u' });
```

### With Context Data

```typescript
// Simple data
logger.info('User connected', { userId: 123 });

// Complex data
logger.debug('Chunk rendered', {
  cx: 0,
  cz: 0,
  vertexCount: 1234,
  triangleCount: 5678,
  renderTime: 16.7,
});

// With error
try {
  await loadChunk(cx, cz);
} catch (error) {
  logger.error('Chunk load failed', { cx, cz }, error as Error);
}
```

## Configuration

### Environment Variables

**Global log level:**
```bash
# .env
LOG_LEVEL=DEBUG
# or for Vite
VITE_LOG_LEVEL=DEBUG
```

**Per-logger levels:**
```bash
# .env
LOG_LOGGERS="NetworkService=DEBUG,ChunkService=TRACE,RenderService=WARN"
# or for Vite
VITE_LOG_LOGGERS="NetworkService=DEBUG,ChunkService=TRACE"
```

**Apply environment config:**
```typescript
import { LoggerFactory } from '@nimbus/shared';

// Apply environment configuration
LoggerFactory.configureFromEnv();
```

### Programmatic Configuration

**Set default level:**
```typescript
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Set default level for all loggers
LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
```

**Set level for specific logger:**
```typescript
// Set level before getting logger
LoggerFactory.setLoggerLevel('NetworkService', LogLevel.TRACE);

const logger = getLogger('NetworkService');
// logger now logs at TRACE level
```

**Global configuration:**
```typescript
import { LoggerFactory, LogLevel } from '@nimbus/shared';

LoggerFactory.configure({
  defaultLevel: LogLevel.INFO,
  includeTimestamp: true,
  includeStack: true,
  loggerLevels: {
    'NetworkService': LogLevel.DEBUG,
    'ChunkService': LogLevel.TRACE,
    'RenderService': LogLevel.WARN,
  },
});
```

### Check if Level Enabled

```typescript
// Avoid expensive operations if not logging
if (logger.isLevelEnabled(LogLevel.DEBUG)) {
  const expensiveData = computeExpensiveDebugInfo();
  logger.debug('Debug info', expensiveData);
}
```

## Advanced Usage

### Custom Formatter

```typescript
import { LoggerFactory, LogLevel, LogLevelNames } from '@nimbus/shared';
import type { LogEntry } from '@nimbus/shared';

// Custom JSON formatter
const jsonFormatter = (entry: LogEntry): string => {
  return JSON.stringify({
    level: LogLevelNames[entry.level],
    logger: entry.name,
    message: entry.message,
    timestamp: entry.timestamp,
    data: entry.data,
    error: entry.error?.message,
  });
};

LoggerFactory.configure({
  formatter: jsonFormatter,
});
```

### Custom Transport

```typescript
import { LoggerFactory } from '@nimbus/shared';
import type { LogEntry, LogTransport } from '@nimbus/shared';

// File transport (Node.js)
const fileTransport: LogTransport = (entry) => {
  const line = `${entry.timestamp},${entry.level},${entry.name},${entry.message}\n`;
  fs.appendFileSync('app.log', line);
};

// Remote transport
const remoteTransport: LogTransport = async (entry) => {
  await fetch('/api/logs', {
    method: 'POST',
    body: JSON.stringify(entry),
  });
};

LoggerFactory.configure({
  transports: [fileTransport, remoteTransport],
});
```

### Multiple Transports

```typescript
import type { LogTransport } from '@nimbus/shared';

const consoleTransport: LogTransport = (entry) => {
  console.log(`[${entry.name}] ${entry.message}`);
};

const fileTransport: LogTransport = (entry) => {
  fs.appendFileSync('app.log', JSON.stringify(entry) + '\n');
};

LoggerFactory.configure({
  transports: [consoleTransport, fileTransport],
});
```

## Usage Patterns

### Service Initialization

```typescript
class NetworkService {
  private logger = getLogger('NetworkService');

  async connect(url: string): Promise<void> {
    this.logger.info('Connecting to server', { url });

    try {
      await this.doConnect(url);
      this.logger.info('Connected successfully', { url });
    } catch (error) {
      this.logger.error('Connection failed', { url }, error as Error);
      throw error;
    }
  }
}
```

### Request/Response Logging

```typescript
class WebSocketClient {
  private logger = getLogger('WebSocketClient');

  send(message: Message): void {
    this.logger.debug('Sending message', {
      id: message.i,
      type: message.t,
      size: JSON.stringify(message).length,
    });

    this.ws.send(JSON.stringify(message));
  }

  onMessage(data: string): void {
    this.logger.trace('Received message', { size: data.length });

    try {
      const message = JSON.parse(data);
      this.logger.debug('Parsed message', {
        id: message.i,
        type: message.t,
      });

      this.handleMessage(message);
    } catch (error) {
      this.logger.error('Failed to parse message', { data }, error as Error);
    }
  }
}
```

### Performance Logging

```typescript
class ChunkRenderer {
  private logger = getLogger('ChunkRenderer');

  renderChunk(chunk: ClientChunk): void {
    const startTime = performance.now();

    this.logger.debug('Rendering chunk', {
      cx: chunk.chunk.cx,
      cz: chunk.chunk.cz,
    });

    // Render chunk...

    const duration = performance.now() - startTime;

    if (duration > 16.67) {
      this.logger.warn('Slow chunk render', {
        cx: chunk.chunk.cx,
        cz: chunk.chunk.cz,
        duration,
        targetFps: 60,
      });
    } else {
      this.logger.trace('Chunk rendered', {
        cx: chunk.chunk.cx,
        cz: chunk.chunk.cz,
        duration,
      });
    }
  }
}
```

### Error Handling

```typescript
class ChunkService {
  private logger = getLogger('ChunkService');

  async loadChunk(cx: number, cz: number): Promise<ClientChunk> {
    this.logger.debug('Loading chunk', { cx, cz });

    try {
      const data = await this.fetchChunkData(cx, cz);
      const chunk = this.createClientChunk(data);

      this.logger.info('Chunk loaded', {
        cx,
        cz,
        blockCount: chunk.blocks?.size ?? 0,
      });

      return chunk;
    } catch (error) {
      if (error instanceof NetworkError) {
        this.logger.warn('Network error loading chunk', { cx, cz }, error);
        // Retry logic...
      } else {
        this.logger.error('Failed to load chunk', { cx, cz }, error as Error);
        throw error;
      }
    }
  }
}
```

## Production Configuration

### Client (Browser)

```typescript
// src/main.ts
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Apply environment config
LoggerFactory.configureFromEnv();

// Production: only errors and warnings
if (import.meta.env.PROD) {
  LoggerFactory.setDefaultLevel(LogLevel.WARN);
}

// Development: verbose logging
if (import.meta.env.DEV) {
  LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
}

// Editor build: even more verbose
if (__EDITOR__ && import.meta.env.DEV) {
  LoggerFactory.setDefaultLevel(LogLevel.TRACE);
}
```

### Server (Node.js)

```typescript
// src/main.ts
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Apply environment config
LoggerFactory.configureFromEnv();

// Production: structured JSON logs
if (process.env.NODE_ENV === 'production') {
  LoggerFactory.configure({
    defaultLevel: LogLevel.INFO,
    formatter: (entry) => JSON.stringify(entry),
  });
}

// Development: readable console logs
if (process.env.NODE_ENV === 'development') {
  LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
}
```

## Best Practices

### ✅ DO

```typescript
// Use named loggers
const logger = getLogger('MyService');

// Log context data
logger.info('User action', { userId: 123, action: 'block_place' });

// Include errors
logger.error('Operation failed', { context }, error);

// Check level before expensive operations
if (logger.isLevelEnabled(LogLevel.DEBUG)) {
  logger.debug('Expensive debug info', computeExpensiveData());
}

// Use appropriate levels
logger.error('Fatal database error'); // ERROR
logger.warn('Deprecated API used'); // WARN
logger.info('Server started'); // INFO
logger.debug('Processing chunk'); // DEBUG
```

### ❌ DON'T

```typescript
// Don't use console.log directly
console.log('User connected'); // ❌

// Don't log sensitive data
logger.info('User logged in', { password: '...' }); // ❌

// Don't log in hot paths without level check
for (let i = 0; i < 1000000; i++) {
  logger.trace('Processing item', { i }); // ❌ Too expensive
}

// Don't use wrong levels
logger.error('User clicked button'); // ❌ Should be DEBUG
logger.info('Database connection failed'); // ❌ Should be ERROR
```

## Performance

- **Level checking**: O(1) - no overhead if disabled
- **Formatter**: Only called if level enabled
- **Transports**: Only invoked if level enabled
- **Zero cost**: Disabled log levels have zero runtime cost

## Examples

See `/packages/shared/src/logger/__tests__/` for comprehensive examples.

## Summary

```typescript
// 1. Import
import { getLogger, LoggerFactory, LogLevel } from '@nimbus/shared';

// 2. Configure (once at startup)
LoggerFactory.configureFromEnv();

// 3. Get logger
const logger = getLogger('MyService');

// 4. Log
logger.info('Message', { data });
logger.error('Error', { context }, error);
```
