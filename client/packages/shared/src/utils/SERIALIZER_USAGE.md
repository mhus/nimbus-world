# Serializers Usage

Conversion utilities between internal types and network/JSON formats.

## Available Serializers

- **BlockSerializer** - Block ↔ JSON
- **BlockTypeSerializer** - BlockType ↔ JSON, Registry sync
- **ChunkSerializer** - ChunkData ↔ ChunkDataTransferObject
- **EntitySerializer** - EntityData ↔ JSON (type-optimized)
- **MessageSerializer** - Message ↔ JSON (with compression)

## BlockSerializer

### Block to/from JSON

```typescript
import { BlockSerializer } from '@nimbus/shared';

const block: Block = {
  position: { x: 10, y: 64, z: 5 },
  blockTypeId: 42,
  status: 1
};

// Serialize
const json = BlockSerializer.toJSON(block);
// '{"position":{"x":10,"y":64,"z":5},"blockTypeId":42,"status":1}'

// Deserialize
const restored = BlockSerializer.fromJSON(json);
if (restored) {
  console.log(restored.blockTypeId);  // 42
}
```

### Optimized Object Conversion

```typescript
// Convert to plain object (removes undefined fields)
const obj = BlockSerializer.toObject(block);
// Only includes present fields → smaller JSON

// From object
const block = BlockSerializer.fromObject(obj);
```

### Block Array

```typescript
const blocks: Block[] = [/* ... */];

// Serialize array
const json = BlockSerializer.arrayToJSON(blocks);

// Deserialize array
const restored = BlockSerializer.arrayFromJSON(json);
if (restored) {
  restored.forEach(block => processBlock(block));
}
```

## ChunkSerializer

### ChunkData ↔ ChunkDataTransferObject

```typescript
import { ChunkSerializer } from '@nimbus/shared';

// Internal chunk (Uint16Array)
const chunkData: ChunkData = ChunkDataHelper.create(0, 0, 16, 256);

// Convert to transfer object (for network)
const transferObj = ChunkSerializer.chunkToTransferObject(chunkData, 256);
// {
//   cx: 0,
//   cz: 0,
//   b: [Block, Block, ...],  // Only non-air blocks
//   h: [HeightData, ...]
// }

// Send over network
ws.send(JSON.stringify({ t: 'c.u', d: [transferObj] }));
```

### Transfer Object → ChunkData

```typescript
// Receive from network
onChunkUpdate((transferObjs: ChunkDataTransferObject[]) => {
  transferObjs.forEach(transferObj => {
    // Convert to internal format
    const chunkData = ChunkSerializer.transferObjectToChunk(
      transferObj,
      16,   // chunk size
      256   // world height
    );

    // Store in cache
    chunkCache.set(ChunkDataHelper.getKey(chunkData.cx, chunkData.cz), chunkData);
  });
});
```

### Delta Updates

```typescript
// Only send changed blocks
const oldChunk = chunkCache.get(key);
const newChunk = modifiedChunk;

const changedBlocks = ChunkSerializer.createDeltaUpdate(oldChunk, newChunk, 16);

// Send delta instead of full chunk
ws.send(JSON.stringify({ t: 'b.u', d: changedBlocks }));

// Savings: ~99% for small changes
// Example: 1 block changed = 1 block sent vs 65536 blocks
```

### Chunk Save/Load (JSON)

```typescript
// Save chunk to file
const chunk: ChunkData = chunkCache.get(key);
const json = ChunkSerializer.toJSON(chunk);
await fs.writeFile(`chunks/${chunk.cx}_${chunk.cz}.json`, json);

// Load chunk from file
const json = await fs.readFile(`chunks/0_0.json`, 'utf-8');
const chunk = ChunkSerializer.fromJSON(json, 256);
if (chunk) {
  chunkCache.set(ChunkDataHelper.getKey(chunk.cx, chunk.cz), chunk);
}
```

### Compression

```typescript
// Get compression ratio
const ratio = ChunkSerializer.getCompressionRatio(chunk);
console.log(`Chunk is ${(ratio * 100).toFixed(1)}% full`);
// 0.05 = 5% full (very sparse)
// 0.95 = 95% full (dense)

// Compress for storage
const compressed = ChunkSerializer.compress(chunk);
const json = JSON.stringify(compressed);
// Only stores non-air blocks

// Decompress
const restored = ChunkSerializer.decompress(compressed, 256);
```

## EntitySerializer

### Entity to/from JSON (Type-Optimized)

```typescript
import { EntitySerializer } from '@nimbus/shared';

const player: EntityData = {
  id: 'player_123',
  type: EntityType.PLAYER,
  username: 'alice',
  position: { x: 0, y: 65, z: 0 },
  rotation: { y: 0, p: 0 },
  health: { current: 20, max: 20, alive: true }
};

// Serialize (only includes relevant fields for player type)
const json = EntitySerializer.toJSON(player);

// Deserialize
const restored = EntitySerializer.fromJSON(json);
```

### Type-Optimized Object

```typescript
// Convert to optimized object (only relevant fields)
const obj = EntitySerializer.toObject(player);
// Only includes: id, type, username, displayName, position, rotation, health, state
// Excludes: dialogId (NPC), itemTypeId (Item), aggression (Mob)

// Smaller network payload
const json = JSON.stringify(obj);
```

### Minimal vs Full Updates

```typescript
// Minimal update (only position/rotation)
const minimal = EntitySerializer.toMinimalUpdate(entity);
// { id, position, rotation, velocity }
// ~50 bytes

// Full snapshot (all fields)
const full = EntitySerializer.toFullSnapshot(entity);
// ~500 bytes

// Send minimal updates frequently
setInterval(() => {
  const update = EntitySerializer.toMinimalUpdate(player);
  ws.send(JSON.stringify({ t: 'u.m', d: update }));
}, 100);  // 10 Hz

// Send full snapshot occasionally
setInterval(() => {
  const snapshot = EntitySerializer.toFullSnapshot(player);
  ws.send(JSON.stringify({ t: 'e.u', d: [snapshot] }));
}, 5000);  // Every 5 seconds
```

### Entity Array

```typescript
const entities: EntityData[] = [player1, player2, npc1];

// Serialize array (type-optimized)
const json = EntitySerializer.arrayToJSON(entities);

// Deserialize array
const restored = EntitySerializer.arrayFromJSON(json);
```

## MessageSerializer

### Message to/from JSON

```typescript
import { MessageSerializer } from '@nimbus/shared';

const message: BaseMessage = {
  i: 'msg_123',
  t: MessageType.LOGIN,
  d: { username: 'alice', password: 'secret' }
};

// Serialize
const json = MessageSerializer.toJSON(message);

// Deserialize
const restored = MessageSerializer.fromJSON(json);
if (restored) {
  handleMessage(restored);
}
```

### Compact JSON (Remove undefined/null)

```typescript
const message: BaseMessage = {
  i: 'msg_123',
  t: MessageType.PING,
  d: undefined,  // Will be removed
  r: undefined   // Will be removed
};

// Regular JSON
const json = MessageSerializer.toJSON(message);
// '{"i":"msg_123","t":"p","d":null,"r":null}'  (70 bytes)

// Compact JSON
const compact = MessageSerializer.toCompactJSON(message);
// '{"i":"msg_123","t":"p"}'  (25 bytes)

// Savings: ~64%
```

### Message Size Calculation

```typescript
// Calculate message size
const size = MessageSerializer.getSize(message);
console.log(`Message size: ${size} bytes`);

const compactSize = MessageSerializer.getCompactSize(message);
console.log(`Compact size: ${compactSize} bytes`);
console.log(`Savings: ${((1 - compactSize / size) * 100).toFixed(1)}%`);
```

### Batch Messages

```typescript
// Send multiple messages in one WebSocket frame
const messages: BaseMessage[] = [msg1, msg2, msg3];

const json = MessageSerializer.batchToJSON(messages);
ws.send(json);

// Receive batch
const messages = MessageSerializer.batchFromJSON(receivedData);
if (messages) {
  messages.forEach(msg => handleMessage(msg));
}
```

## BlockTypeSerializer

### Registry Sync

```typescript
import { BlockTypeSerializer } from '@nimbus/shared';

// Server: Send registry to client
const registry = new Map<number, BlockType>();
registry.set(1, grassBlockType);
registry.set(2, stoneBlockType);
// ... more block types

const json = BlockTypeSerializer.registryToJSON(registry);
ws.send(json);

// Client: Receive registry
const registry = BlockTypeSerializer.registryFromJSON(receivedJson);
if (registry) {
  registry.forEach((blockType, id) => {
    clientRegistry.register(blockType);
  });
}
```

### Single BlockType

```typescript
// Serialize
const json = BlockTypeSerializer.toJSON(grassBlockType);

// Deserialize
const blockType = BlockTypeSerializer.fromJSON(json);
```

## Complete Workflows

### 1. Chunk Loading (Server → Client)

```typescript
// Server side
const chunkData = worldGenerator.generateChunk(0, 0);

// Convert to transfer object
const transferObj = ChunkSerializer.chunkToTransferObject(chunkData, 256);

// Validate
const validation = ChunkValidator.validateChunkTransferObject(transferObj);
if (!validation.valid) {
  console.error('Invalid chunk:', validation.errors);
  return;
}

// Send
ws.send(JSON.stringify({ t: 'c.u', d: [transferObj] }));

// Client side
const transferObj = message.d[0];

// Convert to internal format
const chunkData = ChunkSerializer.transferObjectToChunk(transferObj, 16, 256);

// Store
chunkCache.set(ChunkDataHelper.getKey(chunkData.cx, chunkData.cz), chunkData);
```

### 2. Block Updates (Client → Server)

```typescript
// Client edits block
const block: Block = {
  position: { x: 10, y: 64, z: 5 },
  blockTypeId: 42
};

// Validate
if (!BlockValidator.isValid(block)) {
  console.error('Invalid block');
  return;
}

// Serialize and send
const json = BlockSerializer.toJSON(block);
ws.send(JSON.stringify({ t: 'b.cu', d: [block] }));

// Server receives
const block = BlockSerializer.fromJSON(receivedData);
if (block) {
  processBlockUpdate(block);
}
```

### 3. Entity Updates (Server → All Clients)

```typescript
// Server updates entity
const updatedEntity = updateEntityLogic(entity);

// Create optimized object
const obj = EntitySerializer.toObject(updatedEntity);

// Send to all players
broadcastToAll({ t: 'e.u', d: [obj] });

// Client receives
const entities = message.d as EntityData[];
entities.forEach(entity => {
  entityCache.set(entity.id, entity);
  renderEntity(entity);
});
```

### 4. World Save (Server)

```typescript
async function saveWorld() {
  const chunks: ChunkData[] = Array.from(chunkCache.values());

  for (const chunk of chunks) {
    // Compress chunk
    const compressed = ChunkSerializer.compress(chunk);
    const json = JSON.stringify(compressed);

    // Save to file
    await fs.writeFile(
      `world/chunks/${chunk.cx}_${chunk.cz}.json`,
      json
    );

    console.log(
      `Saved chunk (${chunk.cx}, ${chunk.cz}): ` +
      `${(ChunkSerializer.getCompressionRatio(chunk) * 100).toFixed(1)}% full`
    );
  }
}
```

### 5. World Load (Server)

```typescript
async function loadWorld() {
  const files = await fs.readdir('world/chunks');

  for (const file of files) {
    const json = await fs.readFile(`world/chunks/${file}`, 'utf-8');

    // Try compressed format first
    let chunk: ChunkData | null = null;

    try {
      const compressed = JSON.parse(json);
      chunk = ChunkSerializer.decompress(compressed, 256);
    } catch (e) {
      // Try regular format
      chunk = ChunkSerializer.fromJSON(json, 256);
    }

    if (chunk) {
      chunkCache.set(ChunkDataHelper.getKey(chunk.cx, chunk.cz), chunk);
      console.log(`Loaded chunk (${chunk.cx}, ${chunk.cz})`);
    }
  }
}
```

## Performance Comparison

### Block Updates

| Method | Size | Use Case |
|--------|------|----------|
| Full block | ~120 bytes | Complete block data |
| Optimized (toObject) | ~80 bytes | Remove undefined fields |
| Minimal | ~40 bytes | Only position + typeId |

### Chunk Transfer

| Method | Size (16×256×16, 50% full) | Use Case |
|--------|---------------------------|----------|
| Full Uint16Array | 131 KB | Never sent over network |
| TransferObject | ~800 KB | Full chunk sync |
| Delta update | ~200 bytes | 1 block changed |
| Compressed | ~400 KB | Sparse chunks |

### Entity Updates

| Method | Size | Frequency | Use Case |
|--------|------|-----------|----------|
| Minimal | ~50 bytes | 10 Hz | Movement only |
| Optimized | ~200 bytes | 1 Hz | State updates |
| Full snapshot | ~500 bytes | 0.2 Hz | Sync |

## Best Practices

### ✅ DO
- Use ChunkSerializer for chunk network transfer
- Use delta updates for block changes
- Use minimal updates for frequent entity movement
- Compress chunks before saving to disk
- Validate after deserialization
- Use type-optimized entity serialization

### ❌ DON'T
- Don't send ChunkData directly (use TransferObject)
- Don't send full chunk for small changes (use delta)
- Don't send full entity every frame (use minimal)
- Don't forget to handle null returns
- Don't skip validation after deserialization
- Don't send unnecessary fields

## Summary

- **4 Serializers**: Block, Chunk, Entity, Message
- **Bidirectional**: to/from JSON
- **Optimized**: Remove undefined, type-specific fields
- **Compression**: Chunk compression, compact JSON
- **Delta updates**: Only send changes
- **Validation**: Always validate after deserialize
- **Performance**: Minimal, optimized, and full modes
