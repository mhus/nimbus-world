# Logger Transports

File logging support for browser (development) and Node.js (server).

## Overview

Two transport implementations:

1. **FileLogTransport** - Browser file logging (dev mode)
   - Uses File System Access API (Chrome 86+, Edge 86+)
   - Fallback to download API for other browsers
   - Perfect for development debugging

2. **NodeFileLogTransport** - Node.js file logging
   - Uses Node.js fs module
   - Automatic log rotation
   - Production-ready

## Browser (FileLogTransport)

### Quick Start

```typescript
import { FileLogTransport, LoggerFactory } from '@nimbus/shared';

// Create and initialize
const fileTransport = new FileLogTransport({
  filename: 'app.log',
  maxSizeMB: 5,
});

await fileTransport.initialize(); // Prompts user for file location

// Configure logger
LoggerFactory.configure({
  transports: [fileTransport.transport],
});
```

### Features

- ✅ File System Access API support (Chrome/Edge)
- ✅ Automatic fallback to downloads
- ✅ Auto-flush (configurable interval)
- ✅ Buffer management
- ✅ Timestamp in filename
- ✅ Check API support: `FileLogTransport.isFileSystemAPISupported()`

### Browser Support

| Browser | File System Access API | Download Fallback |
|---------|----------------------|-------------------|
| Chrome 86+ | ✅ Yes | ✅ Yes |
| Edge 86+ | ✅ Yes | ✅ Yes |
| Opera 72+ | ✅ Yes | ✅ Yes |
| Safari | ❌ No | ✅ Yes |
| Firefox | ❌ No | ✅ Yes |

### Usage Example (Client)

```typescript
// src/NimbusClient.ts
import { FileLogTransport, LoggerFactory } from '@nimbus/shared';

if (import.meta.env.DEV && FileLogTransport.isFileSystemAPISupported()) {
  const fileTransport = new FileLogTransport({
    filename: 'nimbus-client.log',
  });

  await fileTransport.initialize();

  LoggerFactory.configure({
    transports: [fileTransport.transport],
  });
}
```

## Node.js (NodeFileLogTransport)

### Quick Start

```typescript
import { NodeFileLogTransport, LoggerFactory } from '@nimbus/shared';

// Create transport (auto-initializes)
const fileTransport = new NodeFileLogTransport({
  directory: './logs',
  filename: 'app.log',
  maxSizeMB: 10,
  maxFiles: 5,
});

// Configure logger
LoggerFactory.configure({
  transports: [fileTransport.transport],
});
```

### Features

- ✅ Node.js fs module integration
- ✅ Automatic log rotation
- ✅ Configurable retention (file count)
- ✅ Creates directory automatically
- ✅ Auto-flush (configurable interval)

### Log Rotation

When file reaches max size:
```
app.log (10 MB) → rotates
  ↓
app.log.5 → deleted
app.log.4 → app.log.5
app.log.3 → app.log.4
app.log.2 → app.log.3
app.log.1 → app.log.2
app.log   → app.log.1
app.log (new)
```

### Usage Example (Server)

```typescript
// src/NimbusServer.ts
import { NodeFileLogTransport, LoggerFactory } from '@nimbus/shared';

const fileTransport = new NodeFileLogTransport({
  directory: './logs',
  filename: 'nimbus-server.log',
  maxSizeMB: 10,
  maxFiles: 5,
});

LoggerFactory.configure({
  transports: [fileTransport.transport],
});
```

## Configuration

### FileLogTransport Options

```typescript
{
  filename?: string;           // 'app.log'
  maxSizeMB?: number;          // 5
  flushIntervalMs?: number;    // 1000
  includeTimestamp?: boolean;  // true
  useFileSystemAPI?: boolean;  // true
}
```

### NodeFileLogTransport Options

```typescript
{
  filename?: string;        // 'app.log'
  directory?: string;       // './logs'
  maxSizeMB?: number;       // 10
  maxFiles?: number;        // 5
  flushIntervalMs?: number; // 1000
}
```

## Log Format

Both transports use the same format:

```
[2025-10-28T20:00:00.000Z] [INFO ] [NimbusClient        ] Application started
  Data: {"version";"2.0.0","mode";"viewer"}
[2025-10-28T20:00:01.123Z] [ERROR] [NetworkService      ] Connection failed
  Error: ECONNREFUSED
  Stack: Error: ECONNREFUSED at ...
```

## Performance

- **Buffer size**: 100 KB before auto-flush
- **Flush interval**: 1 second (configurable)
- **Memory overhead**: Minimal (<1 MB)
- **Disk I/O**: Buffered writes

## Multiple Transports

Combine console and file logging:

```typescript
import type { LogTransport } from '@nimbus/shared';

// Console transport
const consoleTransport: LogTransport = (entry) => {
  console.log(`[${entry.name}] ${entry.message}`);
};

// File transport
const fileTransport = new NodeFileLogTransport();

// Use both
LoggerFactory.configure({
  transports: [consoleTransport, fileTransport.transport],
});
```

## Best Practices

### ✅ DO

- Use only in development (browser)
- Check API support before use
- Close transport on app exit
- Use appropriate log levels

### ❌ DON'T

- Use in production (browser) - annoying downloads
- Log sensitive data to files
- Forget to initialize FileLogTransport

## Documentation

- [FILE_TRANSPORT_USAGE.md](./FILE_TRANSPORT_USAGE.md) - Comprehensive guide
- [../LOGGER_USAGE.md](../LOGGER_USAGE.md) - Logger documentation
- [../README.md](../README.md) - Logger overview

## Files

- **FileLogTransport.ts** - Browser implementation
- **NodeFileLogTransport.ts** - Node.js implementation
- **index.ts** - Exports
- **FILE_TRANSPORT_USAGE.md** - Detailed usage guide
- **README.md** - This file

## Integration

**Client** (optional, uncomment to enable):
```typescript
// src/NimbusClient.ts
if (import.meta.env.DEV && FileLogTransport.isFileSystemAPISupported()) {
  const fileTransport = new FileLogTransport();
  await fileTransport.initialize();
  LoggerFactory.configure({ transports: [fileTransport.transport] });
}
```

**Server** (recommended):
```typescript
// src/NimbusServer.ts
const fileTransport = new NodeFileLogTransport({
  directory: './logs',
  filename: 'nimbus-server.log',
});
LoggerFactory.configure({ transports: [fileTransport.transport] });
```
