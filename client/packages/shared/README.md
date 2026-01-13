# @nimbus/shared

Shared types, protocols and utilities for Nimbus client and server.

## Structure

```
src/
├── types/          # Shared data types (Block, Chunk, Entity, etc.)
├── network/        # Network protocol messages and DTOs
│   ├── messages/   # Message types
│   └── dto/        # Data Transfer Objects
└── utils/          # Shared utility functions
```

## Usage

```typescript
import { BlockType, ChunkData } from '@nimbus/shared';
```

## Development

```bash
# Build
pnpm build

# Watch mode
pnpm dev

# Tests
pnpm test
```
