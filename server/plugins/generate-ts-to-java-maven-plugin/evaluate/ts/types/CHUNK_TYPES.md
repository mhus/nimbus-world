# Chunk Types: ChunkData vs ChunkDataTransferObject

Understanding the difference between internal chunk representation and network transfer format.

## Overview

```
ChunkData (Internal)  →  [Convert]  →  ChunkDataTransferObject (Network)
                                              ↓
                                        JSON Transfer
                                              ↓
ChunkData (Internal)  ←  [Convert]  ←  ChunkDataTransferObject (Received)
```

## ChunkData (shared/types/ChunkData.ts)

**Purpose:** Internal chunk storage and manipulation

**Characteristics:**
- ✅ Used on client and server internally
- ✅ Optimized for fast access (Uint16Array)
- ✅ Contains helper functions
- ✅ Includes status and metadata
- ❌ Not directly serializable to JSON
- ❌ Not sent over network

**Structure:**
```typescript
interface ChunkData {
  x: number;
  z: number;
  size: number;
  blocks: Uint16Array;              // Efficient flat array
  sparseBlocks?: Map<number, number>; // For sparse chunks
  heightData?: number[];
  status: ChunkStatus;
  lastModified?: number;
  isDirty?: boolean;
}
```

**Usage:**
```typescript
// Create chunk
const chunk = ChunkDataHelper.create(0, 0, 16, 256);

// Access blocks efficiently
const blockId = ChunkDataHelper.getBlock(chunk, 5, 64, 10);
ChunkDataHelper.setBlock(chunk, 5, 64, 10, 42);

// Index calculation: x + z * size + y * size * size
const index = 5 + 10 * 16 + 64 * 16 * 16;
```

## ChunkDataTransferObject (shared/network/messages/ChunkMessage.ts)

**Purpose:** Network transfer format

**Characteristics:**
- ✅ Transmitted over network (WebSocket)
- ✅ JSON-serializable
- ✅ Compact (shortened field names)
- ✅ Contains only necessary data
- ❌ No helper functions
- ❌ No internal state (status, dirty flags)

**Structure:**
```typescript
interface ChunkDataTransferObject {
  cx: number;          // Chunk X (shortened)
  cz: number;          // Chunk Z (shortened)
  b: Block[];          // Block data as array of Block objects
  h: HeightData[];     // Height data
  a?: AreaData[];      // Areas (optional)
  e?: EntityData[];    // Entities (optional)
}
```

**Usage:**
```typescript
// Server sends chunk
const transferObj: ChunkDataTransferObject = {
  cx: 0,
  cz: 0,
  b: [
    { position: {x: 0, y: 64, z: 0}, blockTypeId: 1 },
    { position: {x: 1, y: 64, z: 0}, blockTypeId: 1 },
    // ... only non-air blocks
  ],
  h: [
    [128, 0, 64, 62], // Height data
    // ...
  ]
};

ws.send(JSON.stringify({ t: 'c.u', d: [transferObj] }));
```

## Comparison

| Aspect | ChunkData | ChunkDataTransferObject |
|--------|-----------|------------------------|
| **Network Transfer** | ❌ No | ✅ Yes |
| **Storage Format** | Uint16Array | Block[] |
| **Serializable** | ❌ No | ✅ JSON |
| **Helper Functions** | ✅ Yes | ❌ No |
| **Metadata** | ✅ Status, dirty flags | ❌ None |
| **Size** | 🔹 Memory efficient | 🔸 Network efficient |
| **Access Speed** | ⚡ O(1) indexed | 🐌 Array iteration |
| **Use Case** | Internal processing | Network transfer |

## Data Flow

### 1. Server Generates Chunk

```typescript
// Create internal chunk
const chunk = ChunkDataHelper.create(0, 0, 16, 256);

// Generate terrain
for (let x = 0; x < 16; x++) {
  for (let z = 0; z < 16; z++) {
    const height = getTerrainHeight(x, z);
    for (let y = 0; y <= height; y++) {
      ChunkDataHelper.setBlock(chunk, x, y, z, getBlockType(y));
    }
  }
}

chunk.status = ChunkStatus.READY;
```

### 2. Convert to Transfer Object

```typescript
function chunkToTransferObject(chunk: ChunkData): ChunkDataTransferObject {
  const blocks: Block[] = [];
  const heightData: HeightData[] = [];

  // Extract only non-air blocks
  for (let y = 0; y < 256; y++) {
    for (let z = 0; z < chunk.size; z++) {
      for (let x = 0; x < chunk.size; x++) {
        const blockId = ChunkDataHelper.getBlock(chunk, x, y, z);
        if (blockId !== 0) {
          blocks.push({
            position: {
              x: chunk.x * chunk.size + x,
              y: y,
              z: chunk.z * chunk.size + z
            },
            blockTypeId: blockId
          });
        }
      }
    }
  }

  // Convert height data
  if (chunk.heightData) {
    for (let i = 0; i < chunk.heightData.length; i += 4) {
      heightData.push([
        chunk.heightData[i],
        chunk.heightData[i + 1],
        chunk.heightData[i + 2],
        chunk.heightData[i + 3]
      ]);
    }
  }

  return {
    cx: chunk.x,
    cz: chunk.z,
    b: blocks,
    h: heightData
  };
}
```

### 3. Send Over Network

```typescript
// Server sends
const transferObj = chunkToTransferObject(chunk);
sendMessage({
  t: 'c.u',
  d: [transferObj]
});
```

### 4. Client Receives and Converts

```typescript
onChunkUpdate((transferObjects: ChunkDataTransferObject[]) => {
  transferObjects.forEach(transferObj => {
    // Convert to internal format
    const chunk = transferObjectToChunk(transferObj, worldHeight);

    // Store in chunk cache
    chunkCache.set(
      ChunkDataHelper.getKey(chunk.x, chunk.z),
      chunk
    );

    // Trigger rendering
    renderChunk(chunk);
  });
});

function transferObjectToChunk(
  obj: ChunkDataTransferObject,
  worldHeight: number
): ChunkData {
  const size = 16; // Or from world settings
  const chunk = ChunkDataHelper.create(obj.cx, obj.cz, size, worldHeight);

  // Fill blocks
  obj.b.forEach(block => {
    const localX = block.position.x % size;
    const localZ = block.position.z % size;
    ChunkDataHelper.setBlock(
      chunk,
      localX,
      block.position.y,
      localZ,
      block.blockTypeId
    );
  });

  // Fill height data
  if (obj.h) {
    chunk.heightData = obj.h.flat();
  }

  chunk.status = ChunkStatus.READY;
  chunk.isDirty = false;

  return chunk;
}
```

## Memory Efficiency

### ChunkData (Internal)
```
For 16×256×16 chunk:
Uint16Array: 131,072 bytes (2 bytes per block)
+ heightData: ~4KB
+ metadata: ~100 bytes
≈ 135 KB per chunk
```

### ChunkDataTransferObject (Network)
```
For same chunk (50% filled):
Block[] (32,768 blocks): ~1.3 MB uncompressed
Height data: ~4 KB
JSON overhead: ~20%
≈ 1.6 MB uncompressed

With compression (gzip):
≈ 200-400 KB (depends on block variety)
```

## Network Optimization

### Only Send Non-Air Blocks

```typescript
// Don't send air blocks
const blocks: Block[] = [];
for (let index = 0; index < chunk.blocks.length; index++) {
  const blockId = chunk.blocks[index];
  if (blockId !== 0) {
    // Convert index to XYZ
    const x = index % size;
    const z = Math.floor(index / size) % size;
    const y = Math.floor(index / (size * size));

    blocks.push({
      position: { x, y, z },
      blockTypeId: blockId
    });
  }
}
```

### Delta Updates

```typescript
// Only send changed blocks
function createDeltaUpdate(
  oldChunk: ChunkData,
  newChunk: ChunkData
): Block[] {
  const changedBlocks: Block[] = [];

  for (let i = 0; i < newChunk.blocks.length; i++) {
    if (oldChunk.blocks[i] !== newChunk.blocks[i]) {
      // Block changed, include in update
      const x = i % newChunk.size;
      const z = Math.floor(i / newChunk.size) % newChunk.size;
      const y = Math.floor(i / (newChunk.size * newChunk.size));

      changedBlocks.push({
        position: { x, y, z },
        blockTypeId: newChunk.blocks[i]
      });
    }
  }

  return changedBlocks;
}
```

## Helper Functions Usage

### Fast Block Access

```typescript
const chunk = chunkCache.get(ChunkDataHelper.getKey(0, 0));

// Get block (O(1))
const blockId = ChunkDataHelper.getBlock(chunk, 5, 64, 10);

// Set block (O(1))
ChunkDataHelper.setBlock(chunk, 5, 64, 10, 42);

// Check if empty
if (ChunkDataHelper.isEmpty(chunk)) {
  console.log('Chunk is empty, skip rendering');
}

// Count blocks
const count = ChunkDataHelper.countBlocks(chunk);
console.log(`Chunk has ${count} non-air blocks`);
```

### Sparse Chunk Optimization

```typescript
// Optimize storage for sparse chunks (e.g., sky chunks)
ChunkDataHelper.optimizeStorage(chunk, 0.1);

if (chunk.sparseBlocks) {
  console.log(`Sparse storage: ${chunk.sparseBlocks.size} blocks`);
  // Use sparse representation instead of full array
}
```

## Status Management

```typescript
enum ChunkStatus {
  EMPTY = 0,        // Not generated
  GENERATING = 1,   // Structure generation
  DECORATING = 2,   // Features (trees, etc.)
  READY = 3,        // Fully generated
  LOADING = 4,      // Loading from storage
  UNLOADING = 5,    // Saving/unloading
}

// Check chunk status before rendering
if (chunk.status === ChunkStatus.READY) {
  renderChunk(chunk);
} else {
  console.log('Chunk not ready yet');
}
```

## Best Practices

### ✅ DO
- Use `ChunkData` for internal storage
- Use `ChunkDataTransferObject` for network
- Convert between formats at network boundary
- Use helper functions for block access
- Check `isDirty` before saving
- Optimize sparse chunks with `optimizeStorage()`

### ❌ DON'T
- Don't send `ChunkData` over network (not serializable)
- Don't use `ChunkDataTransferObject` for storage (inefficient)
- Don't access `chunk.blocks` directly (use helpers)
- Don't send air blocks over network
- Don't forget to set `chunk.status`
- Don't mutate received transfer objects

## Summary

- **ChunkData**: Internal representation, fast access, Uint16Array
- **ChunkDataTransferObject**: Network format, JSON-serializable, Block[]
- **Separation**: Clean architecture, optimized for each use case
- **Conversion**: At network boundary only
- **Memory**: ChunkData ~135KB, Transfer ~1.6MB uncompressed
- **Speed**: ChunkData O(1) access, Transfer object iteration
