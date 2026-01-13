# ClientChunk Usage

Client-side chunk representation with rendering state and caches.

## Overview

```
ChunkData (Internal)  →  ClientChunk (Client + Rendering)
```

Similar to how `Block` → `ClientBlock`, we have `ChunkData` → `ClientChunk`.

## ChunkRenderState Enum

```typescript
enum ChunkRenderState {
  NOT_RENDERED = 0,     // Not yet rendered
  GENERATING_MESH = 1,  // Mesh generation in progress
  MESH_READY = 2,       // Mesh ready, not added to scene
  RENDERED = 3,         // Rendered and visible
  DIRTY = 4,            // Needs re-rendering
  UNLOADING = 5,        // Being unloaded
}
```

## Structure

```typescript
interface ClientChunk {
  // Original data
  chunkData: ChunkData;
  chunk: { cx: number; cz: number };

  // Rendering
  renderState: ChunkRenderState;
  mesh?: BABYLON.Mesh;
  material?: BABYLON.Material;
  isVisible: boolean;
  lod: number;

  // Caches
  blocks?: Map<number, ClientBlock>;
  neighbors?: { north?, south?, east?, west? };

  // Metadata
  priority: number;
  isDirty: boolean;
  vertexCount?: number;
  triangleCount?: number;
}
```

## Usage Examples

### 1. Create ClientChunk

```typescript
import { ClientChunkHelper } from '@nimbus/shared';

// Receive ChunkData from network
const chunkData = convertTransferObjectToChunkData(transferObj);

// Create ClientChunk
const clientChunk = ClientChunkHelper.create(chunkData);

// Store in cache
chunkCache.set(ClientChunkHelper.getKey(clientChunk), clientChunk);
```

### 2. Render Chunk

```typescript
function renderChunk(clientChunk: ClientChunk) {
  // Check if needs rendering
  if (!ClientChunkHelper.needsRendering(clientChunk)) {
    return;
  }

  // Update state
  clientChunk.renderState = ChunkRenderState.GENERATING_MESH;

  // Generate mesh
  const meshData = generateChunkMesh(clientChunk.chunkData);

  // Create Babylon.js mesh
  const mesh = new BABYLON.Mesh(`chunk_${clientChunk.chunk.cx}_${clientChunk.chunk.cz}`, scene);
  mesh.setVerticesData(BABYLON.VertexBuffer.PositionKind, meshData.positions);
  mesh.setVerticesData(BABYLON.VertexBuffer.NormalKind, meshData.normals);
  mesh.setVerticesData(BABYLON.VertexBuffer.UVKind, meshData.uvs);
  mesh.setIndices(meshData.indices);

  // Apply material
  mesh.material = getMaterial(clientChunk);

  // Update ClientChunk
  clientChunk.mesh = mesh;
  clientChunk.material = mesh.material;
  clientChunk.renderState = ChunkRenderState.RENDERED;
  clientChunk.isDirty = false;
  clientChunk.lastRendered = Date.now();
  clientChunk.meshGeneratedAt = Date.now();
  clientChunk.vertexCount = meshData.positions.length / 3;
  clientChunk.triangleCount = meshData.indices.length / 3;
}
```

### 3. Update Priority and LOD

```typescript
function updateChunkPriorities(
  chunks: Map<string, ClientChunk>,
  cameraPos: Vector3,
  chunkSize: number,
  maxDistance: number
) {
  chunks.forEach(chunk => {
    // Calculate priority based on distance
    ClientChunkHelper.calculatePriority(
      chunk,
      cameraPos.x,
      cameraPos.z,
      chunkSize
    );

    // Calculate LOD
    chunk.lod = ClientChunkHelper.calculateLOD(chunk, maxDistance);

    // Update visibility
    chunk.isVisible = (chunk.distanceFromCamera ?? Infinity) <= maxDistance;
  });
}
```

### 4. Chunk Loading Priority Queue

```typescript
class ChunkLoadQueue {
  private queue: ClientChunk[] = [];

  add(chunk: ClientChunk) {
    this.queue.push(chunk);
    this.sort();
  }

  private sort() {
    // Sort by priority (highest first)
    this.queue.sort((a, b) => b.priority - a.priority);
  }

  getNext(): ClientChunk | undefined {
    return this.queue.shift();
  }

  isEmpty(): boolean {
    return this.queue.length === 0;
  }

  updatePriorities(cameraPos: Vector3, chunkSize: number) {
    this.queue.forEach(chunk => {
      ClientChunkHelper.calculatePriority(
        chunk,
        cameraPos.x,
        cameraPos.z,
        chunkSize
      );
    });
    this.sort();
  }
}

// Usage
const loadQueue = new ChunkLoadQueue();

// Add chunks to load
chunks.forEach(chunk => {
  if (ClientChunkHelper.needsRendering(chunk)) {
    loadQueue.add(chunk);
  }
});

// Load chunks in priority order
function loadNextChunk() {
  const chunk = loadQueue.getNext();
  if (chunk) {
    renderChunk(chunk);
  }
}
```

### 5. Neighbor Updates for Face Culling

```typescript
function updateAllNeighbors(chunks: Map<string, ClientChunk>) {
  chunks.forEach(chunk => {
    ClientChunkHelper.updateNeighbors(chunk, chunks);
  });
}

function shouldRenderFace(
  chunk: ClientChunk,
  direction: 'north' | 'south' | 'east' | 'west'
): boolean {
  const neighbor = chunk.neighbors?.[direction];

  // Render face if no neighbor or neighbor not rendered
  if (!neighbor) return true;
  if (neighbor.renderState !== ChunkRenderState.RENDERED) return true;

  // Don't render if neighbor is solid
  return false; // Simplified
}
```

### 6. Chunk Unloading

```typescript
function unloadChunk(clientChunk: ClientChunk) {
  // Dispose resources
  ClientChunkHelper.dispose(clientChunk);

  // Remove from cache
  chunkCache.delete(ClientChunkHelper.getKey(clientChunk));
}

function unloadDistantChunks(
  chunks: Map<string, ClientChunk>,
  maxDistance: number
) {
  chunks.forEach(chunk => {
    if ((chunk.distanceFromCamera ?? 0) > maxDistance) {
      unloadChunk(chunk);
    }
  });
}
```

### 7. Dirty Chunk Detection

```typescript
// Block changed in chunk
function onBlockChanged(chunk: ClientChunk, blockPos: Vector3) {
  // Mark chunk dirty
  ClientChunkHelper.markDirty(chunk);

  // Also mark neighbors dirty (for face culling update)
  if (chunk.neighbors) {
    const localX = blockPos.x % chunk.chunkData.size;
    const localZ = blockPos.z % chunk.chunkData.size;

    // If block is on chunk edge, mark neighbor dirty
    if (localX === 0 && chunk.neighbors.west) {
      ClientChunkHelper.markDirty(chunk.neighbors.west);
    }
    if (localX === chunk.chunkData.size - 1 && chunk.neighbors.east) {
      ClientChunkHelper.markDirty(chunk.neighbors.east);
    }
    if (localZ === 0 && chunk.neighbors.north) {
      ClientChunkHelper.markDirty(chunk.neighbors.north);
    }
    if (localZ === chunk.chunkData.size - 1 && chunk.neighbors.south) {
      ClientChunkHelper.markDirty(chunk.neighbors.south);
    }
  }
}
```

### 8. Performance Monitoring

```typescript
function getChunkStats(chunks: Map<string, ClientChunk>) {
  let totalVertices = 0;
  let totalTriangles = 0;
  let renderedCount = 0;
  let dirtyCount = 0;

  chunks.forEach(chunk => {
    totalVertices += chunk.vertexCount ?? 0;
    totalTriangles += chunk.triangleCount ?? 0;

    if (chunk.renderState === ChunkRenderState.RENDERED) {
      renderedCount++;
    }
    if (chunk.isDirty) {
      dirtyCount++;
    }
  });

  return {
    totalChunks: chunks.size,
    renderedChunks: renderedCount,
    dirtyChunks: dirtyCount,
    totalVertices,
    totalTriangles,
    avgVerticesPerChunk: totalVertices / chunks.size,
    avgTrianglesPerChunk: totalTriangles / chunks.size,
  };
}

// Log stats
const stats = getChunkStats(chunkCache);
console.log(`Chunks: ${stats.renderedChunks}/${stats.totalChunks} rendered`);
console.log(`Vertices: ${stats.totalVertices.toLocaleString()}`);
console.log(`Triangles: ${stats.totalTriangles.toLocaleString()}`);
```

### 9. Water and Transparency Detection

```typescript
function analyzeChunkContents(clientChunk: ClientChunk) {
  const chunkData = clientChunk.chunkData;
  let hasWater = false;
  let hasTransparency = false;

  // Scan all blocks
  for (let i = 0; i < chunkData.blocks.length; i++) {
    const blockTypeId = chunkData.blocks[i];
    if (blockTypeId === 0) continue; // Skip air

    const blockType = registry.getBlockType(blockTypeId);
    const modifier = blockType.modifiers[0]; // Default status

    // Check for water
    if (modifier.visibility?.shape === Shape.WATER) {
      hasWater = true;
    }
  }

  // Cache results
  clientChunk.hasWater = hasWater;
  clientChunk.hasTransparency = hasTransparency;
}
```

### 10. Render Loop Integration

```typescript
class ChunkRenderer {
  private chunks = new Map<string, ClientChunk>();

  update(deltaTime: number, cameraPos: Vector3) {
    const chunkSize = 16;
    const maxDistance = 256;

    // Update priorities and LOD
    this.chunks.forEach(chunk => {
      ClientChunkHelper.calculatePriority(
        chunk,
        cameraPos.x,
        cameraPos.z,
        chunkSize
      );

      chunk.lod = ClientChunkHelper.calculateLOD(chunk, maxDistance);
      chunk.isVisible = (chunk.distanceFromCamera ?? Infinity) <= maxDistance;
    });

    // Render dirty chunks (highest priority first)
    const dirtyChunks = Array.from(this.chunks.values())
      .filter(chunk => ClientChunkHelper.needsRendering(chunk) && chunk.isVisible)
      .sort((a, b) => b.priority - a.priority);

    // Limit chunks rendered per frame
    const maxChunksPerFrame = 3;
    for (let i = 0; i < Math.min(maxChunksPerFrame, dirtyChunks.length); i++) {
      renderChunk(dirtyChunks[i]);
    }

    // Hide invisible chunks
    this.chunks.forEach(chunk => {
      if (chunk.mesh) {
        chunk.mesh.isVisible = chunk.isVisible;
      }
    });

    // Unload distant chunks
    this.chunks.forEach(chunk => {
      if ((chunk.distanceFromCamera ?? 0) > maxDistance * 1.5) {
        this.unloadChunk(chunk);
      }
    });
  }

  private unloadChunk(chunk: ClientChunk) {
    ClientChunkHelper.dispose(chunk);
    this.chunks.delete(ClientChunkHelper.getKey(chunk));
  }
}
```

## Comparison: ChunkData vs ClientChunk

| Aspect | ChunkData | ClientChunk |
|--------|-----------|-------------|
| **Network** | ❌ Not transmitted | ❌ Not transmitted |
| **Storage** | Uint16Array | + Mesh, Material |
| **State** | ChunkStatus | + ChunkRenderState |
| **Caches** | None | Blocks, Neighbors |
| **Rendering** | No mesh data | Mesh, Material refs |
| **Usage** | Internal storage | Rendering engine |

## Memory Implications

### ChunkData
```
Uint16Array: ~135 KB
Metadata: ~1 KB
Total: ~136 KB
```

### ClientChunk
```
ChunkData: ~136 KB
+ Mesh reference: 8 bytes
+ Material reference: 8 bytes
+ Neighbors: 32 bytes
+ Blocks Map: variable (sparse)
+ Metadata: ~100 bytes
Total: ~136+ KB (+ mesh geometry in GPU)
```

## Best Practices

### ✅ DO
- Use ClientChunk for all rendering operations
- Update priority every frame based on camera
- Use LOD for distant chunks
- Cache neighbors for face culling
- Dispose chunks when unloading
- Monitor vertex/triangle counts
- Mark dirty when data changes

### ❌ DON'T
- Don't send ClientChunk over network (use ChunkDataTransferObject)
- Don't keep all chunks in memory (unload distant)
- Don't render invisible chunks
- Don't regenerate mesh if not dirty
- Don't forget to update neighbors
- Don't skip LOD calculations

## Summary

- **ClientChunk**: Client-side chunk with rendering state
- **Caches**: Mesh, material, neighbors, blocks
- **Priority**: Dynamic based on distance and state
- **LOD**: 4 levels (0-3) based on distance
- **Helpers**: Full set of utility functions
- **Analog to ClientBlock**: Same pattern, chunk level
