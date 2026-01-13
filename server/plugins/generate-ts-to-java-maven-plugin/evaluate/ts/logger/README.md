# Nimbus Logger

Lightweight, type-safe logging framework for Nimbus Voxel Engine.

## Features

- **6 log levels**: FATAL, ERROR, WARN, INFO, DEBUG, TRACE
- **Named loggers**: Per-module logger instances
- **Configurable**: Global and per-logger level control
- **Environment-based**: Configure via env variables
- **Structured logging**: Optional data/context objects
- **Error support**: Automatic stack traces
- **Custom transports**: Pluggable output destinations
- **Custom formatters**: Flexible message formatting
- **Zero dependencies**: Pure TypeScript
- **Tree-shakeable**: Unused code eliminated

## Quick Start

```typescript
import { getLogger } from '@nimbus/shared';

const logger = getLogger('MyService');

logger.info('Service started');
logger.debug('Debug info', { key: 'value' });
logger.error('Operation failed', { context }, error);
```

## Files

- **LogLevel.ts** - Log level enum and utilities
- **LogEntry.ts** - Log entry structure and types
- **Logger.ts** - Logger implementation
- **LoggerFactory.ts** - Logger factory and configuration
- **index.ts** - Public exports
- **LOGGER_USAGE.md** - Comprehensive usage guide

## Log Levels

| Level | Value | Usage |
|-------|-------|-------|
| FATAL | 0 | System must stop |
| ERROR | 1 | Operation failed |
| WARN | 2 | May cause issues |
| INFO | 3 | Informational (default) |
| DEBUG | 4 | Debug information |
| TRACE | 5 | Verbose logging |

Lower values = higher priority.

## Configuration

### Environment Variables

```bash
# Global log level
LOG_LEVEL=DEBUG
VITE_LOG_LEVEL=DEBUG

# Per-logger levels
LOG_LOGGERS="NetworkService=DEBUG,ChunkService=TRACE"
VITE_LOG_LOGGERS="NetworkService=DEBUG,ChunkService=TRACE"
```

### Programmatic

```typescript
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Apply environment config
LoggerFactory.configureFromEnv();

// Set default level
LoggerFactory.setDefaultLevel(LogLevel.DEBUG);

// Set level for specific logger
LoggerFactory.setLoggerLevel('NetworkService', LogLevel.TRACE);
```

## Usage Examples

### Basic Logging

```typescript
const logger = getLogger('ChunkService');

logger.info('Loading chunk', { cx: 0, cz: 0 });
logger.warn('Chunk load timeout', { cx: 0, cz: 0 });
logger.error('Load failed', { cx: 0, cz: 0 }, error);
```

### Service Integration

```typescript
class NetworkService {
  private logger = getLogger('NetworkService');

  async connect(url: string): Promise<void> {
    this.logger.info('Connecting', { url });

    try {
      await this.doConnect(url);
      this.logger.info('Connected', { url });
    } catch (error) {
      this.logger.error('Connection failed', { url }, error as Error);
      throw error;
    }
  }
}
```

### Performance Logging

```typescript
const logger = getLogger('Renderer');

const start = performance.now();
renderChunk(chunk);
const duration = performance.now() - start;

if (duration > 16.67) {
  logger.warn('Slow render', { cx, cz, duration });
}
```

### Conditional Logging

```typescript
// Avoid expensive operations if not logging
if (logger.isLevelEnabled(LogLevel.DEBUG)) {
  const data = computeExpensiveDebugInfo();
  logger.debug('Debug data', data);
}
```

## Bundle Impact

- **Viewer build**: +1.1 KB gzipped (12.47 KB → 13.57 KB)
- **Tree-shaking**: Disabled log levels have zero cost
- **Production**: Use WARN level to minimize overhead

## Integration

### Client (NimbusClient.ts)

```typescript
import { getLogger, LoggerFactory, LogLevel } from '@nimbus/shared';

// Initialize
const logger = getLogger('NimbusClient');
LoggerFactory.configureFromEnv();

// Production: only warnings
if (import.meta.env.PROD) {
  LoggerFactory.setDefaultLevel(LogLevel.WARN);
}

// Development: verbose
if (import.meta.env.DEV && __EDITOR__) {
  LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
}

logger.info('Client initialized');
```

### Server (NimbusServer.ts)

```typescript
import { getLogger, LoggerFactory } from '@nimbus/shared';

const logger = getLogger('NimbusServer');
LoggerFactory.configureFromEnv();

logger.info('Server started', { port: 3000 });
```

## Documentation

See [LOGGER_USAGE.md](./LOGGER_USAGE.md) for comprehensive documentation including:
- Configuration options
- Custom formatters and transports
- Best practices
- Performance tips
- Complete examples

## Architecture

```
getLogger('MyService')
  ↓
LoggerFactory
  ↓
Logger instance
  ↓ (if level enabled)
Formatter
  ↓
Transport(s)
  ↓
Console/File/Remote
```

## Best Practices

✅ **DO**:
- Use named loggers per service/class
- Include context data
- Use appropriate log levels
- Check level before expensive operations

❌ **DON'T**:
- Use console.log directly
- Log sensitive data
- Log in hot paths without level check
- Use wrong log levels

## See Also

- [LOGGER_USAGE.md](./LOGGER_USAGE.md) - Comprehensive usage guide
- [../../CLAUDE.md](../../../../CLAUDE.md) - Architecture overview
