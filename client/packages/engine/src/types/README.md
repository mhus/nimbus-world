# Client Types - Nimbus Voxel Engine 2.0

Client-specific data types for rendering, caching, and client-side state management.

## Overview

These types extend the shared network types with client-side caching, rendering state, and optimizations. They are **NOT transmitted over the network** - they exist only on the client.

## Type Files

### Core Client Types

- **ClientBlockType.ts** - Optimized block type for client rendering
  - Pre-processed data for fast rendering access
  - Contains resolved assets, textures, transformations
  - Includes Facing enum for block orientation

- **ClientBlock.ts** - Client-side block instance
  - Wraps network Block with resolved references
  - Caches BlockType, BlockModifier, metadata
  - Tracks visibility, dirty state, update timestamps

- **ClientChunk.ts** - Client-side chunk representation
  - Wraps ChunkData with rendering state
  - Contains mesh and material references (Babylon.js)
  - Manages LOD, visibility, culling
  - Tracks rendering priority and performance metrics
  - Includes ChunkRenderState enum and ClientChunkHelper namespace

## Usage

```typescript
import { ClientBlock, ClientChunk, ClientBlockType } from './types';
import { Block, ChunkData, BlockType } from '@nimbus/shared';

// Create ClientBlock from network Block
const clientBlock: ClientBlock = {
  block: networkBlock,
  chunk: { cx: 0, cz: 0 },
  blockType: registry.getBlockType(networkBlock.blockTypeId),
  currentModifier: resolveModifier(blockType, networkBlock.status),
  clientBlockType: optimizeForRendering(blockType, networkBlock),
  isVisible: true,
  isDirty: false
};

// Create ClientChunk from ChunkData
const clientChunk = ClientChunkHelper.create(chunkData);

// Update rendering state
ClientChunkHelper.markDirty(clientChunk);
const needsRender = ClientChunkHelper.needsRendering(clientChunk);
```

## Type Hierarchy

```
Network Types (from @nimbus/shared)
  ↓
BlockType → Block → ChunkData
  ↓ client-side resolution
  ↓
ClientBlockType → ClientBlock → ClientChunk
```

## Separation of Concerns

### Shared Types (Network/Server)
- **Block**: Network data only
  - Position, blockTypeId, status, faceVisibility
  - No cached references
  - Minimal data for transmission

- **ChunkData**: Server chunk storage
  - Uint16Array of block IDs
  - Height data, biome, status
  - No rendering state

### Client Types (Client-Only)
- **ClientBlock**: Rendering data
  - Cached BlockType, BlockModifier references
  - Visibility and dirty flags
  - Update timestamps

- **ClientChunk**: Rendering and culling
  - Babylon.js mesh and material references
  - Rendering state (NOT_RENDERED, GENERATING_MESH, RENDERED, etc.)
  - LOD level, priority, distance from camera
  - Neighbor chunk references
  - Performance metrics (vertex/triangle counts)

## Design Principles

1. **Cache Resolved Data**: Pre-resolve all references for fast rendering
2. **Track State**: Visibility, dirty flags, update times
3. **Optimize Access**: Flatten nested data for hot paths
4. **Manage Resources**: Track Babylon.js objects for disposal
5. **Performance Metrics**: Vertex counts, render times, LOD levels

## ClientChunk Rendering States

```typescript
enum ChunkRenderState {
  NOT_RENDERED = 0,    // Not yet rendered
  GENERATING_MESH = 1, // Mesh generation in progress
  MESH_READY = 2,      // Mesh ready, not yet added to scene
  RENDERED = 3,        // Rendered and visible in scene
  DIRTY = 4,           // Needs re-rendering (data changed)
  UNLOADING = 5,       // Being unloaded
}
```

## Helper Functions

### ClientChunkHelper

- `create(chunkData)` - Create from ChunkData
- `markDirty(chunk)` - Mark as needing re-render
- `needsRendering(chunk)` - Check if needs rendering
- `calculatePriority(chunk, cameraX, cameraZ, chunkSize)` - Calculate priority
- `calculateLOD(chunk, maxDistance)` - Calculate LOD level
- `updateNeighbors(chunk, chunks)` - Update neighbor references
- `dispose(chunk)` - Dispose resources (mesh, material)
- `getKey(chunk)` - Get chunk key for mapping ("cx,cz")
- `isEmpty(chunk)` - Check if chunk is empty
- `clone(chunk)` - Shallow clone
- `getRenderStateName(state)` - Get state name for debugging
- `toString(chunk)` - Debug string

## Documentation

See `docs/` for additional documentation:
- **BLOCK_VS_CLIENTBLOCK.md** - Detailed comparison of Block vs ClientBlock
- **CLIENTCHUNK_USAGE.md** - ClientChunk usage patterns and examples

## Integration with Shared Package

Client types import from `@nimbus/shared`:
```typescript
import type {
  Block,
  BlockType,
  BlockMetadata,
  BlockModifier,
  ChunkData
} from '@nimbus/shared';
```

This maintains a clear dependency: `@nimbus/engine` depends on `@nimbus/shared`, but not vice versa.
