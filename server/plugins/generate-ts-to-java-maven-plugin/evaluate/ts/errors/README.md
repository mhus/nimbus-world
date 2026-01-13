# Error Handling

Central error handling for the Nimbus project.

## Files

- **ErrorTypes.ts** - Common error type definitions
- **ExceptionHandler.ts** - Central exception handler
- **EXCEPTION_HANDLER_USAGE.md** - Comprehensive exception handler documentation

## Quick Start

```typescript
import { ExceptionHandler } from '@nimbus/shared';

// Pattern 1: Handle and rethrow (critical operations)
try {
  await initializeService();
} catch (error) {
  throw ExceptionHandler.handleAndRethrow(error, 'Service.initialize');
}

// Pattern 2: Handle without rethrowing (non-critical)
try {
  await savePreferences();
} catch (error) {
  ExceptionHandler.handle(error, 'Service.savePreferences');
  // Continue with defaults
}
```

## Key Principles

1. **All exceptions must be logged** - Use ExceptionHandler for all caught exceptions
2. **Provide context** - Always include class and method name: `'ClassName.methodName'`
3. **Decide on rethrowing** - Rethrow critical errors, handle non-critical ones
4. **Never break logging** - Transport/logging operations must never rethrow

## Documentation

See [EXCEPTION_HANDLER_USAGE.md](./EXCEPTION_HANDLER_USAGE.md) for detailed documentation.

## Integration

The ExceptionHandler is automatically available when importing from `@nimbus/shared`:

```typescript
import { ExceptionHandler } from '@nimbus/shared';
```

All exceptions are automatically logged via the logger system with ERROR level.
