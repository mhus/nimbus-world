# Block vs ClientBlock

Understanding the difference between `Block` and `ClientBlock` types.

## Overview

```
Block (Network/Server)  →  [Network Transfer]  →  ClientBlock (Client Only)
```

## Block (shared/types/Block.ts)

**Purpose:** Network transfer and server-side usage

**Characteristics:**
- ✅ Transmitted over network (WebSocket)
- ✅ Used on server-side
- ✅ Minimal data (only IDs, no references)
- ✅ Serializable to JSON
- ❌ No cached values
- ❌ No resolved references

**Structure:**
```typescript
interface Block {
  position: Vector3;
  blockTypeId: number;        // ID only, not resolved
  offsets?: Offsets;
  faceVisibility?: FaceVisibility;
  status?: number;
  metadata?: BlockMetadata;
  // NO cached references
}
```

**Usage:**
```typescript
// Server creates block
const block: Block = {
  position: { x: 10, y: 64, z: 10 },
  blockTypeId: 42,  // Just the ID
  status: 0
};

// Server sends via network
ws.send(JSON.stringify({
  t: 'b.u',
  d: [block]
}));

// Client receives
const receivedBlock: Block = JSON.parse(message).d[0];
```

## ClientBlock (shared/types/ClientBlock.ts)

**Purpose:** Client-side representation with caches

**Characteristics:**
- ❌ Never transmitted over network
- ✅ Client-side only
- ✅ Contains cached/resolved references
- ✅ Contains rendering optimization data
- ✅ Contains dirty flags and timestamps
- ❌ Not serializable (contains references)

**Structure:**
```typescript
interface ClientBlock {
  block: Block;                    // Original network data
  chunk: { cx: number; cz: number };

  // Cached references (resolved from IDs)
  blockType: BlockType;            // Resolved from registry
  metadata?: BlockMetadata;        // Resolved reference
  currentModifier: BlockModifier;  // Resolved from status

  // Rendering optimization
  clientBlockType: ClientBlockType;

  // Client-side state
  statusName?: string;
  isVisible?: boolean;
  lastUpdate?: number;
  isDirty?: boolean;
}
```

**Usage:**
```typescript
// Client receives Block from network
const networkBlock: Block = receiveFromNetwork();

// Client creates ClientBlock with resolved references
const clientBlock: ClientBlock = {
  block: networkBlock,
  chunk: { cx: 0, cz: 0 },

  // Resolve references
  blockType: registry.getBlockType(networkBlock.blockTypeId),
  metadata: networkBlock.metadata,
  currentModifier: resolveModifier(blockType, networkBlock.status),

  // Pre-process for rendering
  clientBlockType: createClientBlockType(blockType, networkBlock),

  // Client state
  statusName: 'DEFAULT',
  isVisible: true,
  lastUpdate: Date.now(),
  isDirty: false
};

// Use for rendering
renderer.renderBlock(clientBlock);
```

## Comparison

| Aspect | Block | ClientBlock |
|--------|-------|-------------|
| **Network Transfer** | ✅ Yes | ❌ No |
| **Server Usage** | ✅ Yes | ❌ No |
| **Client Usage** | ⚠️ Received only | ✅ Primary use |
| **Serializable** | ✅ Yes (JSON) | ❌ No (references) |
| **References** | ❌ IDs only | ✅ Resolved |
| **Caches** | ❌ None | ✅ Many |
| **Size** | 🔹 Small | 🔸 Large |
| **Mutability** | 🔒 Immutable | ✏️ Mutable (state) |

## Data Flow

### 1. Server → Client (Block Update)

```typescript
// Server side
const block: Block = {
  position: { x: 10, y: 64, z: 10 },
  blockTypeId: 42,
  status: 1
};

sendToClient({ t: 'b.u', d: [block] });
```

### 2. Client Receives and Creates ClientBlock

```typescript
// Client side - receive network message
onMessage((message) => {
  const blocks: Block[] = message.d;

  blocks.forEach(block => {
    // Create or update ClientBlock
    const clientBlock = createClientBlock(block);

    // Store in local cache
    blockCache.set(getBlockKey(block.position), clientBlock);

    // Mark for rendering
    clientBlock.isDirty = true;
  });
});

function worldToChunk(position: Vector3): { cx: number; cz: number } {
  const chunkSize = 16;
  return {
    cx: Math.floor(position.x / chunkSize),
    cz: Math.floor(position.z / chunkSize)
  };
}
```

### 3. Client Resolves References

```typescript
function createClientBlock(block: Block): ClientBlock {
  // Resolve BlockType from registry
  const blockType = registry.getBlockType(block.blockTypeId);

  // Resolve current modifier based on status
  const status = block.status ?? 0;
  const currentModifier =
    block.metadata?.modifiers?.[status] ??
    blockType.modifiers[status] ??
    blockType.modifiers[0];

  // Create optimized client type
  const clientBlockType = createClientBlockType(blockType, currentModifier);

  return {
    block,
    chunk: worldToChunk(block.position),
    blockType,
    metadata: block.metadata,
    currentModifier,
    clientBlockType,
    statusName: getStatusName(status),
    isVisible: true,
    lastUpdate: Date.now(),
    isDirty: true
  };
}
```

### 4. Renderer Uses ClientBlock

```typescript
function renderBlock(clientBlock: ClientBlock) {
  if (!clientBlock.isVisible || !clientBlock.isDirty) {
    return; // Skip rendering
  }

  const { currentModifier, clientBlockType, block } = clientBlock;

  // Use cached data for fast rendering
  const mesh = createMeshFromModifier(currentModifier);
  mesh.position = block.position;

  // Apply textures from clientBlockType
  mesh.material = clientBlockType.material;

  scene.addMesh(mesh);

  // Clear dirty flag
  clientBlock.isDirty = false;
  clientBlock.lastUpdate = Date.now();
}
```

## Status Updates

### Network Update

```typescript
// Server sends status change
{
  t: 'b.s.u',
  d: [{
    x: 10, y: 64, z: 10,
    s: 1  // New status
  }]
}
```

### Client Updates ClientBlock

```typescript
onBlockStatusUpdate((update) => {
  const clientBlock = blockCache.get(getKey(update));

  if (clientBlock) {
    // Update original block
    clientBlock.block.status = update.s;

    // Re-resolve modifier
    clientBlock.currentModifier = resolveModifier(
      clientBlock.blockType,
      update.s
    );

    // Update status name
    clientBlock.statusName = getStatusName(update.s);

    // Mark dirty for re-render
    clientBlock.isDirty = true;
    clientBlock.lastUpdate = Date.now();
  }
});
```

## Memory Implications

### Block (Network)
```
Position (12 bytes) + BlockTypeId (2 bytes) + Status (1 byte) = ~15 bytes
+ Optional: offsets, faceVisibility, metadata
```

### ClientBlock (Client Memory)
```
Block (~15 bytes)
+ BlockType reference (8 bytes pointer)
+ BlockModifier reference (8 bytes pointer)
+ ClientBlockType reference (8 bytes pointer)
+ Flags and timestamps (~16 bytes)
= ~55 bytes + referenced objects
```

**Trade-off:** ClientBlock uses more memory but provides O(1) access to all rendering data.

## Best Practices

### ✅ DO

- Use `Block` for network messages
- Use `Block` on server-side logic
- Create `ClientBlock` from received `Block` data
- Keep `Block` data immutable
- Update `ClientBlock` caches when needed
- Use `ClientBlock.isDirty` for rendering optimization

### ❌ DON'T

- Don't serialize `ClientBlock` to JSON
- Don't send `ClientBlock` over network
- Don't store references in `Block`
- Don't mutate `Block` properties (create new)
- Don't access registry on every render (use cache)

## Example: Complete Workflow

```typescript
// 1. Server creates block
const block: Block = {
  position: { x: 10, y: 64, z: 10 },
  blockTypeId: 42,
  status: 0
};

// 2. Server sends to client
sendMessage({ t: 'b.u', d: [block] });

// 3. Client receives and processes
const clientBlock: ClientBlock = {
  block: block,
  chunk: { cx: 0, cz: 0 },
  blockType: registry.getBlockType(42),
  currentModifier: registry.getBlockType(42).modifiers[0],
  clientBlockType: createClientBlockType(...),
  statusName: 'DEFAULT',
  isVisible: true,
  lastUpdate: Date.now(),
  isDirty: true
};

// 4. Client renders
renderer.render(clientBlock);

// 5. Player interacts, status changes
clientBlock.block.status = 1;
clientBlock.currentModifier = blockType.modifiers[1];
clientBlock.statusName = 'OPEN';
clientBlock.isDirty = true;

// 6. Client sends update to server
sendMessage({
  t: 'b.cu',
  d: [clientBlock.block]  // Send only Block, not ClientBlock!
});
```

## Summary

- **Block**: Lean, serializable, network-ready
- **ClientBlock**: Rich, cached, render-optimized
- **Separation**: Clean architecture, clear responsibilities
- **Performance**: Network efficiency + rendering speed
