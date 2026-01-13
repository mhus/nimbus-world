# File Log Transport Usage

File logging support for browser (dev mode) and Node.js (server).

## Browser (FileLogTransport)

Saves logs to local filesystem in development mode using:
1. **File System Access API** (Chrome 86+, Edge 86+) - User selects file location once
2. **Download API** (fallback) - Downloads log file periodically

### Features

- ✅ Modern File System Access API support
- ✅ Automatic fallback to downloads
- ✅ Auto-flush with configurable interval
- ✅ Buffer management
- ✅ Configurable file size limits
- ✅ Timestamp in filename
- ✅ Zero impact when not used

### Quick Start

```typescript
import { FileLogTransport, LoggerFactory } from '@nimbus/shared';

// Create file transport
const fileTransport = new FileLogTransport({
  filename: 'nimbus-client.log',
  maxSizeMB: 5,
  flushIntervalMs: 1000,
});

// Initialize (prompts user for file location)
await fileTransport.initialize();

// Configure logger to use file transport
LoggerFactory.configure({
  transports: [fileTransport.transport],
});

// Logs are now written to file
const logger = getLogger('MyService');
logger.info('This goes to the file');
```

### Configuration Options

```typescript
interface FileLogTransportOptions {
  /** Log filename (default: 'app.log') */
  filename?: string;

  /** Maximum file size in MB (default: 5 MB) */
  maxSizeMB?: number;

  /** Auto-flush interval in ms (default: 1000) */
  flushIntervalMs?: number;

  /** Include timestamp in filename (default: true) */
  includeTimestamp?: boolean;

  /** Use File System Access API if available (default: true) */
  useFileSystemAPI?: boolean;
}
```

### File System Access API

**Supported browsers:**
- Chrome 86+ ✅
- Edge 86+ ✅
- Opera 72+ ✅
- Safari - Not supported ❌
- Firefox - Not supported ❌

**How it works:**
1. User selects file location once on initialization
2. Logs are continuously appended to that file
3. No repeated downloads or prompts

**User experience:**
```typescript
await fileTransport.initialize();
// → Browser shows file picker
// → User selects "nimbus-client-2025-10-28T20-00-00.log"
// → File is created and opened for writing
// → All logs are appended to this file
```

### Download API Fallback

For browsers without File System Access API, logs are downloaded periodically.

**How it works:**
1. Logs are buffered in memory
2. Every `flushIntervalMs` (default: 1 second), buffer is flushed
3. A download is triggered with the log content
4. User sees multiple download notifications (can be annoying)

**Recommendation:** Use File System Access API browsers for development.

### Integration Example (Client)

```typescript
// src/NimbusClient.ts
import {
  getLogger,
  LoggerFactory,
  LogLevel,
  FileLogTransport,
} from '@nimbus/shared';

const logger = getLogger('NimbusClient');

// Development mode: enable file logging
if (import.meta.env.DEV) {
  // Check if File System Access API is available
  if (FileLogTransport.isFileSystemAPISupported()) {
    const fileTransport = new FileLogTransport({
      filename: 'nimbus-client.log',
      maxSizeMB: 5,
      flushIntervalMs: 1000,
    });

    // Initialize and configure
    fileTransport
      .initialize()
      .then(() => {
        LoggerFactory.configure({
          transports: [fileTransport.transport],
          defaultLevel: LogLevel.DEBUG,
        });
        logger.info('File logging enabled');
      })
      .catch((error) => {
        logger.warn('File logging not available', error);
      });
  } else {
    logger.info('File System Access API not supported');
  }
}

// Production mode: console only
if (import.meta.env.PROD) {
  LoggerFactory.setDefaultLevel(LogLevel.WARN);
}
```

### Cleanup

```typescript
// Close file transport when done
await fileTransport.close();
```

## Node.js (NodeFileLogTransport)

Saves logs to filesystem with automatic log rotation.

### Features

- ✅ Node.js fs module integration
- ✅ Automatic log rotation
- ✅ Configurable file size and retention
- ✅ Auto-flush with configurable interval
- ✅ Creates log directory if needed

### Quick Start

```typescript
import { NodeFileLogTransport, LoggerFactory } from '@nimbus/shared';

// Create file transport
const fileTransport = new NodeFileLogTransport({
  directory: './logs',
  filename: 'nimbus-server.log',
  maxSizeMB: 10,
  maxFiles: 5,
});

// Configure logger
LoggerFactory.configure({
  transports: [fileTransport.transport],
});

// Logs are written to ./logs/nimbus-server.log
const logger = getLogger('NimbusServer');
logger.info('Server started');
```

### Configuration Options

```typescript
interface NodeFileLogTransportOptions {
  /** Log filename (default: 'app.log') */
  filename?: string;

  /** Log directory (default: './logs') */
  directory?: string;

  /** Maximum file size in MB (default: 10 MB) */
  maxSizeMB?: number;

  /** Maximum number of rotated files (default: 5) */
  maxFiles?: number;

  /** Auto-flush interval in ms (default: 1000) */
  flushIntervalMs?: number;
}
```

### Log Rotation

When log file reaches `maxSizeMB`:

1. Current file is renamed: `app.log` → `app.log.1`
2. Previous rotations are shifted: `app.log.1` → `app.log.2`, etc.
3. Oldest file (`app.log.5`) is deleted
4. New `app.log` is created

**Example rotation sequence:**
```
app.log (10 MB) → reaches limit
  ↓ rotation
app.log.4 → app.log.5 (deleted)
app.log.3 → app.log.4
app.log.2 → app.log.3
app.log.1 → app.log.2
app.log   → app.log.1
app.log (new, empty)
```

### Integration Example (Server)

```typescript
// src/NimbusServer.ts
import {
  getLogger,
  LoggerFactory,
  LogLevel,
  NodeFileLogTransport,
} from '@nimbus/shared';

const logger = getLogger('NimbusServer');

// Production: file logging with rotation
if (process.env.NODE_ENV === 'production') {
  const fileTransport = new NodeFileLogTransport({
    directory: './logs',
    filename: 'nimbus-server.log',
    maxSizeMB: 10,
    maxFiles: 5,
  });

  LoggerFactory.configure({
    transports: [fileTransport.transport],
    defaultLevel: LogLevel.INFO,
  });

  logger.info('File logging enabled');
}

// Development: console only
if (process.env.NODE_ENV === 'development') {
  LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
  logger.info('Console logging enabled');
}
```

### Multiple Transports

Combine console and file logging:

```typescript
import type { LogTransport } from '@nimbus/shared';

// Console transport
const consoleTransport: LogTransport = (entry) => {
  console.log(`[${entry.name}] ${entry.message}`);
};

// File transport
const fileTransport = new NodeFileLogTransport({
  directory: './logs',
  filename: 'app.log',
});

// Use both
LoggerFactory.configure({
  transports: [consoleTransport, fileTransport.transport],
});
```

## Performance

### Browser

- **Buffer size**: 100 KB before auto-flush
- **Flush interval**: 1 second (configurable)
- **Memory overhead**: Minimal (<1 MB buffer)
- **Impact**: Zero when not initialized

### Node.js

- **Buffer size**: 100 KB before auto-flush
- **Flush interval**: 1 second (configurable)
- **Disk I/O**: Buffered writes (minimal impact)
- **Rotation overhead**: <10ms (infrequent)

## Best Practices

### ✅ DO

```typescript
// Use only in development
if (import.meta.env.DEV) {
  const transport = new FileLogTransport();
  await transport.initialize();
}

// Check browser support
if (FileLogTransport.isFileSystemAPISupported()) {
  // Use file transport
}

// Close when done
window.addEventListener('beforeunload', async () => {
  await fileTransport.close();
});

// Use appropriate log levels
LoggerFactory.setDefaultLevel(LogLevel.DEBUG); // Dev
LoggerFactory.setDefaultLevel(LogLevel.WARN);  // Prod
```

### ❌ DON'T

```typescript
// Don't use in production (browser)
if (import.meta.env.PROD) {
  const transport = new FileLogTransport(); // ❌ Annoying downloads
}

// Don't forget to initialize
const transport = new FileLogTransport();
LoggerFactory.configure({ transports: [transport.transport] }); // ❌ Not initialized

// Don't log sensitive data to files
logger.info('User password', { password: '...' }); // ❌ Security risk
```

## Troubleshooting

### "User cancelled file selection"

File System Access API requires user to select file location. If cancelled, transport falls back to download API.

**Solution:** Re-initialize or use console logging only.

### "Downloads are blocked"

Browser may block repeated downloads.

**Solution:** Use File System Access API compatible browser (Chrome/Edge).

### "Permission denied"

File System Access API requires write permission.

**Solution:** Ensure file location is writable by browser.

### Node.js: "ENOENT: no such file or directory"

Log directory doesn't exist.

**Solution:** Transport creates directory automatically, but check permissions.

## Examples

See:
- `packages/engine/src/NimbusClient.ts` - Browser integration
- `packages/test_server/src/NimbusServer.ts` - Node.js integration

## Summary

| Feature | Browser | Node.js |
|---------|---------|---------|
| **File logging** | ✅ | ✅ |
| **Auto-rotation** | ❌ | ✅ |
| **User prompt** | Once | Never |
| **Browser support** | Chrome 86+ | N/A |
| **Fallback** | Downloads | N/A |
| **Production use** | ❌ | ✅ |
| **Dev mode use** | ✅ | ✅ |
