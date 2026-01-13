# Error Handling Usage

Structured error handling with error codes and severity levels.

## Error Types

### ErrorCode Enum

```typescript
enum ErrorCode {
  // Client errors (400-499)
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,

  // Server errors (500-599)
  INTERNAL_SERVER_ERROR = 500,

  // Network errors (1000-1099)
  CONNECTION_FAILED = 1000,
  CONNECTION_LOST = 1001,

  // Authentication (1100-1199)
  INVALID_CREDENTIALS = 1100,
  SESSION_EXPIRED = 1101,

  // World (1200-1299)
  WORLD_NOT_FOUND = 1200,

  // Chunk (1300-1399)
  CHUNK_NOT_FOUND = 1300,

  // Block (1400-1499)
  INVALID_BLOCK_TYPE = 1401,

  // Entity (1500-1599)
  ENTITY_NOT_FOUND = 1500,

  // Permissions (1600-1699)
  NO_PERMISSION = 1600,

  // Validation (1700-1799)
  VALIDATION_FAILED = 1700,

  // Resources (1800-1899)
  RESOURCE_NOT_FOUND = 1800,
}
```

### ErrorSeverity Enum

```typescript
enum ErrorSeverity {
  INFO = 0,      // Informational
  WARNING = 1,   // May cause issues
  ERROR = 2,     // Action failed
  CRITICAL = 3,  // System unstable
  FATAL = 4,     // System must stop
}
```

## GameError Interface

```typescript
interface GameError {
  code: ErrorCode;
  message: string;
  severity: ErrorSeverity;
  details?: any;
  timestamp?: number; // javaType: long
  stack?: string;
}
```

## Usage Examples

### 1. Create Error

```typescript
import { ErrorHelper, ErrorCode, ErrorSeverity } from '@nimbus/shared';

// Basic error
const error = ErrorHelper.create(
  ErrorCode.WORLD_NOT_FOUND,
  'World "example" not found',
  ErrorSeverity.ERROR
);

// With details
const error = ErrorHelper.create(
  ErrorCode.VALIDATION_FAILED,
  'Block validation failed',
  ErrorSeverity.WARNING,
  { blockId: 42, errors: ['Invalid position'] }
);
```

### 2. Common Errors (Factory Functions)

```typescript
import { CommonErrors } from '@nimbus/shared';

// Invalid credentials
const error = CommonErrors.invalidCredentials();

// World not found
const error = CommonErrors.worldNotFound('world_123');

// Chunk not found
const error = CommonErrors.chunkNotFound(0, 0);

// Invalid block type
const error = CommonErrors.invalidBlockType(99999);

// Connection lost
const error = CommonErrors.connectionLost();

// No permission
const error = CommonErrors.noPermission('edit blocks');

// Validation failed
const error = CommonErrors.validationFailed(['Missing position', 'Invalid ID']);
```

### 3. From Exception

```typescript
try {
  riskyOperation();
} catch (e) {
  const error = ErrorHelper.fromException(
    e as Error,
    ErrorCode.CHUNK_LOAD_FAILED
  );

  ErrorHelper.log(error);
  handleError(error);
}
```

### 4. Network Error Response

```typescript
// Server sends error response
const errorResponse = ErrorHelper.createResponse(
  ErrorCode.NO_PERMISSION,
  'You do not have permission to edit this block'
);

ws.send(JSON.stringify({
  r: requestId,
  t: 'int.rs',
  d: errorResponse
}));

// Client receives
if (!response.success) {
  const error = ErrorHelper.create(
    response.errorCode,
    response.errorMessage,
    ErrorSeverity.WARNING,
    response.details
  );

  showErrorToUser(error.message);
}
```

### 5. Error Logging

```typescript
import { ErrorHelper } from '@nimbus/shared';

const error = ErrorHelper.create(
  ErrorCode.CHUNK_LOAD_FAILED,
  'Failed to load chunk (0, 0)',
  ErrorSeverity.ERROR,
  { cx: 0, cz: 0, reason: 'Network timeout' }
);

// Auto-log with appropriate console level
ErrorHelper.log(error);
// Outputs:
// [ERROR] CHUNK_LOAD_FAILED (1301): Failed to load chunk (0, 0)
// Timestamp: 2025-10-28T17:00:00.000Z
// Details: {"cx":0,"cz":0,"reason":"Network timeout"}
```

### 6. Error Classification

```typescript
import { ErrorHelper, ErrorCode } from '@nimbus/shared';

const error = { code: ErrorCode.UNAUTHORIZED };

// Check error type
if (ErrorHelper.isClientError(error.code)) {
  console.log('Client-side error (4xx)');
  handleClientError(error);
}

if (ErrorHelper.isServerError(error.code)) {
  console.log('Server-side error (5xx)');
  handleServerError(error);
}

// Check if retryable
if (ErrorHelper.isRetryable(error.code)) {
  console.log('Error is retryable');
  scheduleRetry();
}
```

### 7. Error Handling Pattern

```typescript
import { GameError, ErrorCode, ErrorSeverity } from '@nimbus/shared';

class ErrorHandler {
  private errors: GameError[] = [];

  handle(error: GameError) {
    this.errors.push(error);

    // Log error
    ErrorHelper.log(error);

    // Handle based on severity
    switch (error.severity) {
      case ErrorSeverity.INFO:
        this.showInfo(error.message);
        break;

      case ErrorSeverity.WARNING:
        this.showWarning(error.message);
        break;

      case ErrorSeverity.ERROR:
        this.showError(error.message);
        this.attemptRecovery(error);
        break;

      case ErrorSeverity.CRITICAL:
        this.showCriticalError(error.message);
        this.saveState();
        break;

      case ErrorSeverity.FATAL:
        this.showFatalError(error.message);
        this.emergencyShutdown();
        break;
    }
  }

  getRecentErrors(count: number = 10): GameError[] {
    return this.errors.slice(-count);
  }

  clearErrors() {
    this.errors = [];
  }
}
```

### 8. Network Error Handling

```typescript
ws.onerror = (event) => {
  const error = ErrorHelper.create(
    ErrorCode.CONNECTION_FAILED,
    'WebSocket connection failed',
    ErrorSeverity.CRITICAL,
    { event }
  );

  errorHandler.handle(error);

  // Retry if retryable
  if (ErrorHelper.isRetryable(error.code)) {
    scheduleReconnect();
  }
};

ws.onclose = (event) => {
  if (!event.wasClean) {
    const error = ErrorHelper.create(
      ErrorCode.CONNECTION_LOST,
      'Connection lost unexpectedly',
      ErrorSeverity.CRITICAL,
      { code: event.code, reason: event.reason }
    );

    errorHandler.handle(error);
  }
};
```

### 9. Validation Error Handling

```typescript
const validation = BlockValidator.validateBlock(block);

if (!validation.valid) {
  const error = CommonErrors.validationFailed(validation.errors);
  errorHandler.handle(error);
  return;
}

// Process valid block
processBlock(block);
```

### 10. User-Facing Errors

```typescript
function showErrorToUser(error: GameError) {
  const icon = {
    [ErrorSeverity.INFO]: 'ℹ️',
    [ErrorSeverity.WARNING]: '⚠️',
    [ErrorSeverity.ERROR]: '❌',
    [ErrorSeverity.CRITICAL]: '🔥',
    [ErrorSeverity.FATAL]: '💀',
  }[error.severity];

  const message = `${icon} ${error.message}`;

  // Show in UI
  const notification = document.createElement('div');
  notification.className = `error-notification severity-${error.severity}`;
  notification.textContent = message;
  document.body.appendChild(notification);

  // Auto-remove
  setTimeout(() => notification.remove(), 5000);
}
```

## Error Response Pattern

### Client Request → Server Error Response

```typescript
// Client sends request
ws.send(JSON.stringify({
  i: 'req_123',
  t: 'int.r',
  d: { x: 10, y: 64, z: 5 }
}));

// Server validates and sends error
if (!hasPermission(player, block)) {
  const errorResponse = ErrorHelper.createResponse(
    ErrorCode.NO_PERMISSION,
    'You do not have permission to interact with this block'
  );

  ws.send(JSON.stringify({
    r: 'req_123',
    t: 'int.rs',
    d: errorResponse
  }));
}

// Client handles error response
if (!response.success) {
  const error = ErrorHelper.create(
    response.errorCode,
    response.errorMessage,
    ErrorSeverity.WARNING
  );

  errorHandler.handle(error);
}
```

## Summary

### Constants
- **10 groups** of constants
- **60+ values** covering all subsystems
- **Type-safe** with `as const`
- **No magic numbers**

### Errors
- **50+ error codes** categorized by system
- **5 severity levels** for appropriate handling
- **GameError** structured error type
- **ErrorHelper** utilities for creation, logging, classification
- **CommonErrors** factory functions for frequent errors
- **ErrorResponse** for network error replies
