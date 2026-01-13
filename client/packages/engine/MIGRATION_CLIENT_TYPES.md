# Migration: Client Types from Shared to Client Package

**Date**: 2025-10-28
**Task**: Move all client-specific types from `@nimbus/shared` to `@nimbus/engine`

## Overview

Client-specific types (ClientBlock, ClientChunk, ClientBlockType) have been moved from the shared package to the client package. These types are only used on the client side for rendering, caching, and client-side state management, and should not be in the shared package which is focused on network transmission and server logic.

## Changes

### Files Moved

**From `packages/shared/src/types/` to `packages/engine/src/types/`:**

1. **ClientBlockType.ts** (63 lines)
   - Optimized block type for client rendering
   - Contains Facing enum
   - Depends on: BlockType, Shape, Vector3 from @nimbus/shared

2. **ClientBlock.ts** (72 lines)
   - Client-side block instance with resolved references
   - Caches BlockType, BlockModifier, metadata
   - Tracks visibility, dirty state, timestamps
   - Depends on: Block, BlockType, BlockMetadata, BlockModifier from @nimbus/shared

3. **ClientChunk.ts** (374 lines)
   - Client-side chunk representation with rendering state
   - Contains ChunkRenderState enum
   - Contains ClientChunkHelper namespace (11 functions)
   - Depends on: ChunkData from @nimbus/shared

### Documentation Moved

**From `packages/shared/src/types/` to `packages/engine/src/types/docs/`:**

1. **BLOCK_VS_CLIENTBLOCK.md** - Detailed comparison of Block vs ClientBlock
2. **CLIENTCHUNK_USAGE.md** - ClientChunk usage patterns and examples

### New Files Created

1. **packages/engine/src/types/index.ts** - Export file for client types
2. **packages/engine/src/types/README.md** - Client types documentation

### Files Modified

1. **packages/shared/src/types/index.ts**
   - Removed exports for ClientBlockType, ClientBlock, ClientChunk

2. **packages/shared/src/types/README.md**
   - Updated to reflect removal of client types
   - Added note that client types are in @nimbus/engine package
   - Updated type hierarchy diagram
   - Updated design principles

3. **packages/engine/package.json**
   - Added main/types/exports fields for proper package exports
   - Exports: "." (main) and "./types" (client types)

4. **packages/engine/tsconfig.json**
   - Added `emitDeclarationOnly: true` to generate type declarations

5. **packages/engine/package.json** (build script)
   - Changed build order: `vite build && tsc` (was `tsc && vite build`)
   - This ensures vite doesn't overwrite TypeScript declarations

## Import Changes

### Before (when types were in shared)

```typescript
import { ClientBlock, ClientChunk, ClientBlockType } from '@nimbus/shared';
```

### After (types now in client)

```typescript
// Import from client package
import { ClientBlock, ClientChunk, ClientBlockType } from '@nimbus/engine/types';

// Shared types remain in @nimbus/shared
import { Block, BlockType, ChunkData } from '@nimbus/shared';
```

## Package Structure

### @nimbus/shared (Network/Server Types)

```
packages/shared/src/types/
├── Vector3.ts
├── Rotation.ts
├── Color.ts
├── Shape.ts
├── BlockType.ts
├── Block.ts
├── BlockModifier.ts
├── BlockMetadata.ts
├── ChunkData.ts
├── World.ts
├── AnimationData.ts
├── AreaData.ts
├── EffectData.ts
├── EntityData.ts
└── index.ts
```

**Focus**: Network transmission, server logic, minimal data

### @nimbus/engine (Client Types)

```
packages/engine/src/types/
├── ClientBlockType.ts
├── ClientBlock.ts
├── ClientChunk.ts
├── index.ts
├── README.md
└── docs/
    ├── BLOCK_VS_CLIENTBLOCK.md
    └── CLIENTCHUNK_USAGE.md
```

**Focus**: Rendering, caching, client-side state, Babylon.js integration

## Dependency Flow

```
@nimbus/shared (network types)
    ↓ (import)
@nimbus/engine (client types + engine)
```

Engine package depends on shared, but shared does NOT depend on engine.

## Build Verification

Both packages build successfully:

```bash
# Build shared package
pnpm --filter @nimbus/shared build
# ✓ Compiled successfully

# Build engine package
pnpm --filter @nimbus/engine build
# ✓ vite build: dist/index.html + dist/assets/main-*.js
# ✓ tsc: dist/NimbusClient.d.ts + dist/types/*.d.ts
```

## Type Exports

### @nimbus/shared exports

```typescript
export * from './types';
export * from './network';
export * from './validators';
export * from './utils';
export * from './constants';
export * from './errors';
```

**Types exported**:
- Vector3, Rotation, Color, Shape
- BlockType, Block, BlockModifier, BlockMetadata
- ChunkData
- World, EntityData
- AnimationData, AreaData, EffectData

### @nimbus/engine exports

```json
{
  "exports": {
    ".": {
      "types": "./dist/NimbusClient.d.ts",
      "default": "./dist/NimbusClient.js"
    },
    "./types": {
      "types": "./dist/types/index.d.ts",
      "default": "./dist/types/index.js"
    }
  }
}
```

**Types exported from "./types"**:
- ClientBlockType, Facing enum
- ClientBlock
- ClientChunk, ChunkRenderState enum, ClientChunkHelper namespace

## Migration Impact

### No Breaking Changes for External Consumers

If there are external packages consuming these types, they need to update imports:

```typescript
// Before
import { ClientBlock } from '@nimbus/shared';

// After
import { ClientBlock } from '@nimbus/engine/types';
```

### Benefits of Separation

1. **Clear Boundaries**: Shared package is purely network/server focused
2. **Dependency Direction**: Client depends on shared, not vice versa
3. **Smaller Shared Package**: Reduced size for server-side usage
4. **Better Organization**: Client types co-located with client code
5. **Type Safety**: No accidental use of client types on server

## Future Considerations

### Server Package

When implementing `@nimbus/test_server`, it should:
- Import from `@nimbus/shared` (network types)
- NOT import from `@nimbus/engine` (client types)

### Additional Client Types

Any future client-only types should be added to `@nimbus/engine/src/types/`:
- ClientEntity (if needed)
- ClientAnimation (if needed)
- Rendering-specific types
- UI-specific types

## Verification Checklist

- [x] Client types moved to client package
- [x] Documentation moved to client package
- [x] Shared package exports updated
- [x] Shared README updated
- [x] Client README created
- [x] Client package.json exports configured
- [x] Client tsconfig.json updated
- [x] Build scripts updated
- [x] Both packages build successfully
- [x] Type declarations generated correctly
- [x] Import paths updated in client types

## Notes

- All client types import shared types via `@nimbus/shared`
- No circular dependencies introduced
- TypeScript project references maintained (client → shared)
- Build order matters: vite build → tsc (to preserve type declarations)
