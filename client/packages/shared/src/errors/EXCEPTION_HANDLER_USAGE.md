# Exception Handler Usage

Central exception handling for the Nimbus project.

## Overview

The `ExceptionHandler` provides centralized exception handling and logging. All caught exceptions should be passed through this handler to ensure they are properly logged and can be handled uniformly across the application.

## Key Features

- ✅ Automatic logging of all exceptions
- ✅ Normalized error objects (handles any thrown value)
- ✅ Context tracking (method/class names)
- ✅ Optional custom error handlers (for UI notifications)
- ✅ Function wrapping utilities
- ✅ Consistent error handling patterns

## When to Use Try-Catch Blocks

Add try-catch blocks in these scenarios:

1. **Top-level initialization code** - Main entry points, constructors
2. **Async operations** - Network calls, file I/O, database operations
3. **Event handlers** - User interactions, timers, callbacks
4. **Service boundaries** - Public API methods, message handlers
5. **Resource management** - File handles, connections, cleanup operations

## Basic Usage

### Pattern 1: Handle and Rethrow

Use this pattern for **critical operations** that must succeed (initialization, setup, etc.):

```typescript
import { ExceptionHandler } from '@nimbus/shared';

async initialize(): Promise<void> {
  try {
    // Critical operation
    await this.connectToServer();
    await this.loadResources();
  } catch (error) {
    // Log and rethrow
    throw ExceptionHandler.handleAndRethrow(
      error,
      'ServiceName.initialize',
      { serverUrl: this.serverUrl }
    );
  }
}
```

### Pattern 2: Handle Without Rethrowing

Use this pattern for **non-critical operations** where the application can continue:

```typescript
import { ExceptionHandler } from '@nimbus/shared';

async savePreferences(): Promise<void> {
  try {
    // Non-critical operation
    await this.storage.save(this.preferences);
  } catch (error) {
    // Log but don't rethrow
    ExceptionHandler.handle(error, 'PreferenceService.savePreferences');
    // Continue with default preferences
  }
}
```

### Pattern 3: Transport/Logging Operations

For logging transports and similar operations, **never rethrow** to avoid breaking the logging system:

```typescript
import { ExceptionHandler } from '@nimbus/shared';

transport = (entry: LogEntry): void => {
  try {
    // Transport logic
    this.writeToFile(entry);
  } catch (error) {
    ExceptionHandler.handle(error, 'FileTransport.transport', { entry });
    // Never rethrow - transport errors must not break logging
  }
};
```

## Function Wrapping

### Wrap Async Functions

```typescript
import { ExceptionHandler } from '@nimbus/shared';

// Wrap an async function
const fetchData = ExceptionHandler.wrapAsync(
  async (userId: string) => {
    const response = await fetch(`/api/users/${userId}`);
    return response.json();
  },
  'UserService.fetchData'
);

// Usage - exceptions are automatically handled
const data = await fetchData('user123');
```

### Wrap Sync Functions

```typescript
import { ExceptionHandler } from '@nimbus/shared';

// Wrap a sync function
const parseConfig = ExceptionHandler.wrap(
  (configText: string) => {
    return JSON.parse(configText);
  },
  'ConfigService.parseConfig'
);

// Usage - exceptions are automatically handled
const config = parseConfig(text);
```

## Context Naming Convention

Always use the format: `'ClassName.methodName'`

**Examples:**
- ✅ `'NetworkService.connect'`
- ✅ `'ChunkRenderer.generateMesh'`
- ✅ `'FileLogTransport.initialize'`
- ✅ `'NimbusClient.init'`

**Avoid:**
- ❌ `'error'` - Too generic
- ❌ `'exception'` - Not descriptive
- ❌ `'networkError'` - Missing method context
- ❌ `'connect'` - Missing class context

## Providing Context Data

Include relevant context data to help debug issues:

```typescript
try {
  await this.loadChunk(chunkX, chunkZ);
} catch (error) {
  throw ExceptionHandler.handleAndRethrow(
    error,
    'ChunkService.loadChunk',
    {
      chunkX,
      chunkZ,
      worldId: this.worldId,
      cacheSize: this.cache.size,
    }
  );
}
```

**Good context data:**
- IDs (userId, chunkId, etc.)
- Coordinates/positions
- State information
- Operation parameters

**Avoid:**
- Sensitive data (passwords, tokens)
- Large objects (use summaries instead)
- Circular references

## Custom Error Handlers

Register a custom handler for user-facing error notifications:

```typescript
import { ExceptionHandler } from '@nimbus/shared';

// Register handler (typically in main.ts or app initialization)
ExceptionHandler.registerHandler((error, context, data) => {
  // Show user-friendly error dialog
  showErrorDialog({
    title: 'An error occurred',
    message: error.message,
    details: `Error in ${context}`,
  });

  // Send error report to server
  errorReportingService.report({
    error,
    context,
    data,
    timestamp: Date.now(),
  });
});
```

**Important:**
- All exceptions are ALWAYS logged via the logger, regardless of custom handlers
- Custom handlers are for additional actions only
- Don't throw errors from custom handlers (they are caught and logged)

### Unregister Handler

```typescript
// Remove custom handler
ExceptionHandler.registerHandler(null);
```

## Decision Tree: When to Rethrow

```
Is this a critical operation that must succeed?
├─ YES → Use handleAndRethrow()
│  Examples:
│  - Service initialization
│  - Database connection
│  - Required resource loading
│
└─ NO → Can the application continue without this?
   ├─ YES → Use handle() without rethrowing
   │  Examples:
   │  - Saving preferences
   │  - Cache operations
   │  - Optional feature loading
   │
   └─ NO → Is this a logging/transport operation?
      ├─ YES → Use handle() without rethrowing (never break logging!)
      │  Examples:
      │  - Log transport errors
      │  - Metrics collection
      │  - Analytics events
      │
      └─ NO → Use handleAndRethrow()
```

## Real-World Examples

### Example 1: Service Initialization (Critical)

```typescript
class NetworkService {
  async connect(url: string): Promise<void> {
    try {
      this.socket = await this.createWebSocket(url);
      await this.authenticate();
      await this.subscribeToChannels();
    } catch (error) {
      // Critical: connection must succeed
      throw ExceptionHandler.handleAndRethrow(
        error,
        'NetworkService.connect',
        { url, retryCount: this.retryCount }
      );
    }
  }
}
```

### Example 2: Optional Feature (Non-Critical)

```typescript
class AnalyticsService {
  async trackEvent(event: string): Promise<void> {
    try {
      await this.api.post('/events', { event });
    } catch (error) {
      // Non-critical: analytics failures shouldn't break the app
      ExceptionHandler.handle(error, 'AnalyticsService.trackEvent', { event });
      // Continue without analytics
    }
  }
}
```

### Example 3: Resource Cleanup

```typescript
class ResourceManager {
  async cleanup(): Promise<void> {
    try {
      await this.closeConnections();
      await this.flushBuffers();
      await this.releaseMemory();
    } catch (error) {
      // Log but don't rethrow - cleanup is best-effort
      ExceptionHandler.handle(error, 'ResourceManager.cleanup');
    }
  }
}
```

### Example 4: Event Handler

```typescript
class InputController {
  private handleKeyPress = (event: KeyboardEvent): void => {
    try {
      const action = this.keyBindings.get(event.key);
      if (action) {
        action.execute();
      }
    } catch (error) {
      // Event handlers shouldn't crash the app
      ExceptionHandler.handle(error, 'InputController.handleKeyPress', {
        key: event.key,
      });
    }
  };
}
```

### Example 5: Async Operation Chain

```typescript
class ChunkLoader {
  async loadChunk(x: number, z: number): Promise<Chunk> {
    try {
      // Try cache first
      const cached = await this.cache.get(x, z);
      if (cached) return cached;

      // Load from server
      const data = await this.network.fetchChunk(x, z);
      const chunk = this.parser.parse(data);

      // Store in cache (best-effort)
      try {
        await this.cache.set(x, z, chunk);
      } catch (cacheError) {
        // Cache errors shouldn't fail chunk loading
        ExceptionHandler.handle(cacheError, 'ChunkLoader.cacheChunk', { x, z });
      }

      return chunk;
    } catch (error) {
      // Loading failed - must rethrow
      throw ExceptionHandler.handleAndRethrow(
        error,
        'ChunkLoader.loadChunk',
        { x, z, cacheSize: this.cache.size }
      );
    }
  }
}
```

## Guidelines Summary

### ✅ DO

- Use try-catch at boundaries where errors cannot propagate further
- Always call `ExceptionHandler.handle()` or `ExceptionHandler.handleAndRethrow()`
- Provide meaningful context: `'ClassName.methodName'`
- Include relevant context data
- Decide whether to rethrow based on operation criticality
- Use custom handlers for user-facing notifications

### ❌ DON'T

- Use bare try-catch without ExceptionHandler
- Swallow exceptions silently
- Use generic context names like 'error'
- Catch exceptions you can't handle properly
- Throw errors from custom error handlers
- Rethrow from logging/transport operations

## Error Normalization

The ExceptionHandler automatically normalizes any thrown value to an Error object:

```typescript
// These are all handled correctly:

throw new Error('Something failed');
// → Error: Something failed

throw 'String error';
// → Error: String error

throw { message: 'Custom object' };
// → Error: Custom object

throw 42;
// → Error: 42

throw null;
// → Error: null
```

## Integration with Logger

All exceptions are automatically logged with the ERROR level:

```typescript
try {
  await riskyOperation();
} catch (error) {
  ExceptionHandler.handle(error, 'Service.method', { context });
}

// Produces log entry:
// [ERROR] [ExceptionHandler] Exception in Service.method: Error message
//   Data: { context: ... }
//   Stack: ...
```

## Testing

When writing tests, you can register a test handler to capture exceptions:

```typescript
import { ExceptionHandler } from '@nimbus/shared';

describe('MyService', () => {
  const errors: Array<{ error: Error; context: string; data?: any }> = [];

  beforeEach(() => {
    // Capture errors during tests
    ExceptionHandler.registerHandler((error, context, data) => {
      errors.push({ error, context, data });
    });
  });

  afterEach(() => {
    ExceptionHandler.registerHandler(null);
    errors.length = 0;
  });

  it('should handle errors correctly', async () => {
    await service.doSomething();
    expect(errors).toHaveLength(0); // No errors occurred
  });
});
```

## See Also

- [LOGGER_USAGE.md](../logger/LOGGER_USAGE.md) - Logger documentation
- [ExceptionHandler.ts](./ExceptionHandler.ts) - Implementation
- [CLAUDE.md](../../../../CLAUDE.md#exception-handling) - Exception handling guidelines
