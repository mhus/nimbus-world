# Validators Usage

Validation functions for critical data structures.

## Available Validators

- **BlockValidator** - Block, BlockType, BlockMetadata validation
- **ChunkValidator** - ChunkData, ChunkDataTransferObject validation
- **EntityValidator** - EntityData validation
- **MessageValidator** - Network message validation

## ValidationResult Interface

```typescript
interface ValidationResult {
  valid: boolean;
  errors: string[];
  warnings?: string[];
}
```

## BlockValidator

### Basic Validation

```typescript
import { BlockValidator } from '@nimbus/shared';

// Validate block type ID
BlockValidator.isValidBlockTypeId(42);     // true
BlockValidator.isValidBlockTypeId(70000);  // false (> 65535)
BlockValidator.isValidBlockTypeId(-1);     // false

// Validate status
BlockValidator.isValidStatus(0);    // true
BlockValidator.isValidStatus(10);   // true
BlockValidator.isValidStatus(256);  // false
```

### Full Block Validation

```typescript
const block: Block = {
  position: { x: 10, y: 64, z: 5 },
  blockTypeId: 42,
  status: 0
};

const result = BlockValidator.validateBlock(block);
if (!result.valid) {
  console.error('Block validation failed:', result.errors);
  result.warnings?.forEach(w => console.warn(w));
} else {
  // Block is valid, safe to use
  processBlock(block);
}
```

### BlockType Validation

```typescript
const blockType: BlockType = {
  id: 1,
  initialStatus: 0,
  modifiers: {
    0: { /* default modifier */ }
  }
};

const result = BlockValidator.validateBlockType(blockType);
if (!result.valid) {
  console.error('BlockType validation failed:', result.errors);
}

// Common errors detected:
// - Missing default modifier (status 0)
// - Invalid status keys
// - Initial status has no corresponding modifier
```

### Network Block Array

```typescript
// Validate blocks received from network
onBlockUpdate((blocks: Block[]) => {
  const result = BlockValidator.validateBlockArray(blocks, 10000);

  if (!result.valid) {
    console.error('Invalid blocks received:', result.errors);
    return; // Don't process invalid data
  }

  // Safe to process
  blocks.forEach(block => updateBlock(block));
});
```

### Quick Validation

```typescript
// Fast validation for critical path
if (BlockValidator.isValid(block)) {
  // Only checks: position, blockTypeId
  processBlock(block);
}
```

### Sanitization

```typescript
// Fix invalid block data
const dirtyBlock = {
  position: { x: NaN, y: 64, z: 5 },
  blockTypeId: -1,
  status: 300
};

const clean = BlockValidator.sanitize(dirtyBlock);
// {
//   position: { x: 0, y: 64, z: 5 },  // NaN → 0
//   blockTypeId: 0,                     // -1 → 0 (air)
//   // status removed (invalid)
// }
```

## ChunkValidator

### Chunk Coordinates

```typescript
import { ChunkValidator } from '@nimbus/shared';

// Validate coordinates
ChunkValidator.isValidChunkCoordinate(0, 0);        // true
ChunkValidator.isValidChunkCoordinate(1000, 500);   // true
ChunkValidator.isValidChunkCoordinate(1000000, 0);  // false (too large)
```

### Chunk Size

```typescript
// Must be power of 2
ChunkValidator.isValidChunkSize(16);   // true
ChunkValidator.isValidChunkSize(32);   // true
ChunkValidator.isValidChunkSize(20);   // false (not power of 2)
ChunkValidator.isValidChunkSize(128);  // true
ChunkValidator.isValidChunkSize(256);  // false (> 128)
```

### ChunkData Validation

```typescript
const chunk = ChunkDataHelper.create(0, 0, 16, 256);

const result = ChunkValidator.validateChunkData(chunk, 256);
if (!result.valid) {
  console.error('Invalid chunk:', result.errors);
}

// Checks:
// - Valid coordinates
// - Valid size (power of 2)
// - Blocks is Uint16Array
// - Correct array length
// - No invalid block IDs
// - Valid height data structure
```

### Network Transfer Object

```typescript
// Validate chunk received from network
onChunkUpdate((chunks: ChunkDataTransferObject[]) => {
  chunks.forEach(chunk => {
    const result = ChunkValidator.validateChunkTransferObject(chunk);

    if (!result.valid) {
      console.error(`Invalid chunk (${chunk.cx}, ${chunk.cz}):`, result.errors);
      return;
    }

    // Safe to process
    processChunk(chunk);
  });
});
```

### Chunk Registration

```typescript
// Validate chunk coordinates for registration
const coords: ChunkCoordinate[] = [
  { cx: 0, cz: 0 },
  { cx: 1, cz: 0 }
];

const result = ChunkValidator.validateChunkCoordinates(coords, 1000);
if (!result.valid) {
  console.error('Invalid coordinates:', result.errors);
}

// Detects:
// - Missing cx/cz
// - Invalid coordinate values
// - Too many coordinates
// - Duplicate coordinates
```

## EntityValidator

### Entity Validation

```typescript
import { EntityValidator } from '@nimbus/shared';

const player: EntityData = {
  id: 'player_123',
  type: EntityType.PLAYER,
  username: 'alice',
  position: { x: 0, y: 65, z: 0 },
  rotation: { y: 0, p: 0 }
};

const result = EntityValidator.validateEntity(player);
if (!result.valid) {
  console.error('Invalid entity:', result.errors);
  result.warnings?.forEach(w => console.warn(w));
}

// Type-specific validation:
// - Players: checks username, userId, health
// - NPCs: checks displayName, modelPath
// - Items: checks itemTypeId, stackCount
```

### Entity Array

```typescript
// Validate entities from network
onEntityUpdate((entities: EntityData[]) => {
  const result = EntityValidator.validateEntityArray(entities, 1000);

  if (!result.valid) {
    console.error('Invalid entities:', result.errors);
    return;
  }

  // Checks:
  // - Each entity is valid
  // - No duplicate IDs
  // - Not too many entities

  entities.forEach(entity => updateEntity(entity));
});
```

### Quick Validation

```typescript
// Fast validation for render loop
if (EntityValidator.isValid(entity)) {
  renderEntity(entity);
}
```

## MessageValidator

### Base Message Validation

```typescript
import { MessageValidator } from '@nimbus/shared';

const message: BaseMessage = {
  i: 'msg_123',
  t: MessageType.LOGIN,
  d: { /* ... */ }
};

const result = MessageValidator.validateBaseMessage(message);
if (!result.valid) {
  console.error('Invalid message:', result.errors);
}

// Checks:
// - Message type is valid
// - Message ID format (if present)
// - Response ID format (if present)
// - i and r mutual exclusivity
```

### JSON Message Validation

```typescript
// Validate received WebSocket message
ws.onmessage = (event) => {
  const result = MessageValidator.validateMessageJSON(event.data);

  if (!result.valid) {
    console.error('Invalid message JSON:', result.errors);
    return;
  }

  // Message was parsed and validated
  const message = result.message!;
  handleMessage(message);
};

// Checks:
// - Valid JSON format
// - Valid base message structure
// - Message size (warns if > 10 MB)
```

### Quick Validation

```typescript
// Fast check before processing
if (MessageValidator.isValid(message)) {
  routeMessage(message);
}
```

## Integration Examples

### 1. Server-Side Validation

```typescript
// Validate all incoming data
ws.on('message', (data) => {
  // Validate JSON
  const jsonValidation = MessageValidator.validateMessageJSON(data);
  if (!jsonValidation.valid) {
    console.error('Invalid message from client:', jsonValidation.errors);
    return;
  }

  const message = jsonValidation.message!;

  // Validate message content based on type
  if (message.t === MessageType.BLOCK_CLIENT_UPDATE) {
    const blocks = message.d as Block[];
    const blockValidation = BlockValidator.validateBlockArray(blocks);

    if (!blockValidation.valid) {
      console.error('Invalid block update:', blockValidation.errors);
      sendError(player, 'Invalid block data');
      return;
    }

    // Safe to process
    processBlockUpdate(player, blocks);
  }
});
```

### 2. Client-Side Validation

```typescript
// Validate data before sending
function sendBlockUpdate(blocks: Block[]) {
  // Validate before sending
  const result = BlockValidator.validateBlockArray(blocks);

  if (!result.valid) {
    console.error('Cannot send invalid blocks:', result.errors);
    showErrorToUser('Invalid block data');
    return;
  }

  if (result.warnings && result.warnings.length > 0) {
    console.warn('Block warnings:', result.warnings);
  }

  // Safe to send
  networkService.send({
    t: MessageType.BLOCK_CLIENT_UPDATE,
    d: blocks
  });
}
```

### 3. Editor Validation

```typescript
// Validate block before placing
function placeBlock(position: Vector3, blockTypeId: number) {
  const block: Block = {
    position,
    blockTypeId,
    status: 0
  };

  // Validate
  const result = BlockValidator.validateBlock(block);

  if (!result.valid) {
    showEditorError(result.errors.join('\n'));
    return;
  }

  // Place block
  chunkManager.setBlock(block);
}
```

### 4. Data Import Validation

```typescript
// Validate imported world data
function importWorldData(data: any) {
  const chunks = data.chunks as ChunkDataTransferObject[];

  let validChunks = 0;
  let invalidChunks = 0;

  chunks.forEach(chunk => {
    const result = ChunkValidator.validateChunkTransferObject(chunk);

    if (result.valid) {
      validChunks++;
      importChunk(chunk);
    } else {
      invalidChunks++;
      console.error(`Skipping invalid chunk (${chunk.cx}, ${chunk.cz}):`, result.errors);
    }
  });

  console.log(`Import complete: ${validChunks} valid, ${invalidChunks} invalid`);
}
```

### 5. Development/Debug Mode

```typescript
// Enable strict validation in development
const DEV_MODE = process.env.NODE_ENV === 'development';

function processBlock(block: Block) {
  if (DEV_MODE) {
    const result = BlockValidator.validateBlock(block);
    if (!result.valid) {
      console.error('Invalid block detected:', result.errors);
      debugger; // Break in debugger
    }
    if (result.warnings) {
      console.warn('Block warnings:', result.warnings);
    }
  }

  // Process block
  updateBlock(block);
}
```

### 6. Logging Invalid Data

```typescript
// Log validation failures for debugging
function logValidationFailure(
  dataType: string,
  data: any,
  result: ValidationResult
) {
  console.error(`[Validation] ${dataType} validation failed`);
  console.error('Errors:', result.errors);
  if (result.warnings) {
    console.warn('Warnings:', result.warnings);
  }
  console.debug('Data:', JSON.stringify(data, null, 2));
}

// Usage
const result = BlockValidator.validateBlock(block);
if (!result.valid) {
  logValidationFailure('Block', block, result);
}
```

## Best Practices

### ✅ DO
- Validate all data from network before processing
- Use full validation in development mode
- Use quick validation in production (performance)
- Log validation errors for debugging
- Sanitize data when possible
- Check warnings for potential issues

### ❌ DON'T
- Don't skip validation for untrusted data
- Don't ignore warnings in development
- Don't process invalid data
- Don't trust client-sent data without validation
- Don't use full validation in tight render loops
- Don't forget to check ValidationResult.valid

## Performance Considerations

### Quick vs Full Validation

```typescript
// Render loop (performance critical)
if (BlockValidator.isValid(block)) {
  // Only checks critical fields
  renderBlock(block);
}

// Network receive (security critical)
const result = BlockValidator.validateBlock(block);
if (result.valid) {
  // Full validation
  processBlock(block);
}
```

### Validation Levels

**Level 1: Quick (isValid)**
- Only critical fields
- ~0.001ms per item
- Use in: render loops, hot paths

**Level 2: Full (validate)**
- All fields with detailed errors
- ~0.01ms per item
- Use in: network receive, data import

**Level 3: Sanitize**
- Fix invalid data where possible
- ~0.02ms per item
- Use in: untrusted data sources

## Summary

- **4 Validators**: Block, Chunk, Entity, Message
- **ValidationResult**: Consistent result format
- **Quick checks**: isValid() for performance
- **Full validation**: validate() for security
- **Sanitization**: Fix invalid data when possible
- **Type-specific**: Player, NPC, Item field validation
- **Network safety**: Validate all incoming data
