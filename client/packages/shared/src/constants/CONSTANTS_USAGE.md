# Nimbus Constants Usage

Central constants to avoid magic numbers throughout the codebase.

## Import

```typescript
import {
  Constants,
  ChunkConstants,
  BlockConstants,
  NetworkConstants,
  // ... individual imports
} from '@nimbus/shared';

// Or use grouped constants
import { Constants } from '@nimbus/shared';
console.log(Constants.Chunk.SIZE_DEFAULT);
```

## Available Constants

### ChunkConstants
```typescript
ChunkConstants.SIZE_DEFAULT           // 16
ChunkConstants.SUPPORTED_SIZES        // [8, 16, 32, 64]
ChunkConstants.SIZE_MAX               // 128
ChunkConstants.SIZE_MIN               // 8
```

### WorldConstants
```typescript
WorldConstants.HEIGHT_DEFAULT         // 256
WorldConstants.HEIGHT_MAX             // 512
WorldConstants.HEIGHT_MIN             // 64
WorldConstants.SEA_LEVEL_DEFAULT      // 62
WorldConstants.GROUND_LEVEL_DEFAULT   // 64
```

### BlockConstants
```typescript
BlockConstants.AIR_BLOCK_ID           // 0
BlockConstants.MAX_BLOCK_TYPE_ID      // 65535
BlockConstants.MIN_BLOCK_TYPE_ID      // 0
BlockConstants.DEFAULT_STATUS         // 0
BlockConstants.MAX_STATUS             // 255
BlockConstants.MAX_OFFSET             // 127
BlockConstants.MIN_OFFSET             // -127
BlockConstants.MAX_FACE_VISIBILITY    // 127
```

### NetworkConstants
```typescript
NetworkConstants.PING_TIMEOUT_BUFFER_MS      // 10000
NetworkConstants.PING_INTERVAL_DEFAULT       // 30
NetworkConstants.MAX_MESSAGE_SIZE            // 10 MB
NetworkConstants.RECONNECT_DELAY_MS          // 5000
NetworkConstants.MAX_RECONNECT_ATTEMPTS      // 5
NetworkConstants.MESSAGE_ID_MAX_LENGTH       // 100
```

### EntityConstants
```typescript
EntityConstants.PLAYER_HEALTH_DEFAULT        // 20
EntityConstants.PLAYER_MAX_HEALTH_DEFAULT    // 20
EntityConstants.PLAYER_WALK_SPEED            // 4.3
EntityConstants.PLAYER_SPRINT_SPEED          // 5.6
EntityConstants.PLAYER_CROUCH_SPEED          // 1.3
EntityConstants.PLAYER_JUMP_HEIGHT           // 1.25
EntityConstants.ENTITY_ID_MAX_LENGTH         // 100
EntityConstants.DISPLAY_NAME_MAX_LENGTH      // 100
EntityConstants.USERNAME_MAX_LENGTH          // 50
```

### RenderConstants
```typescript
RenderConstants.RENDER_DISTANCE_DEFAULT      // 8
RenderConstants.RENDER_DISTANCE_MAX          // 32
RenderConstants.TARGET_FPS                   // 60
RenderConstants.MAX_CHUNKS_PER_FRAME         // 3
RenderConstants.LOD_THRESHOLDS               // [0.25, 0.5, 0.75]
RenderConstants.MAX_VERTICES_PER_CHUNK       // 100000
```

### AnimationConstants
```typescript
AnimationConstants.MAX_DURATION              // 60000
AnimationConstants.MAX_EFFECTS               // 50
AnimationConstants.MAX_PLACEHOLDERS          // 10
AnimationConstants.DEFAULT_EASING            // 'easeInOut'
```

### PhysicsConstants
```typescript
PhysicsConstants.GRAVITY                     // 20
PhysicsConstants.TERMINAL_VELOCITY           // 50
PhysicsConstants.AIR_DRAG                    // 0.98
PhysicsConstants.GROUND_FRICTION             // 0.6
PhysicsConstants.WATER_DRAG                  // 0.8
```

### CameraConstants
```typescript
CameraConstants.FOV_DEFAULT                  // 70
CameraConstants.FOV_MIN                      // 30
CameraConstants.FOV_MAX                      // 110
CameraConstants.SENSITIVITY_DEFAULT          // 0.1
CameraConstants.PITCH_MAX                    // 89
CameraConstants.PITCH_MIN                    // -89
CameraConstants.NEAR_PLANE                   // 0.1
CameraConstants.FAR_PLANE                    // 1000
```

### LimitConstants
```typescript
LimitConstants.MAX_BLOCKS_PER_MESSAGE        // 10000
LimitConstants.MAX_ENTITIES_PER_MESSAGE      // 1000
LimitConstants.MAX_CHUNK_COORDINATES         // 1000
LimitConstants.MAX_NOTIFICATION_QUEUE        // 100
LimitConstants.MAX_ANIMATION_QUEUE           // 50
```

## Usage Examples

### 1. Chunk Creation

```typescript
import { ChunkConstants, WorldConstants } from '@nimbus/shared';

// ❌ Don't use magic numbers
const chunk = ChunkDataHelper.create(0, 0, 16, 256);

// ✅ Use constants
const chunk = ChunkDataHelper.create(
  0,
  0,
  ChunkConstants.SIZE_DEFAULT,
  WorldConstants.HEIGHT_DEFAULT
);
```

### 2. Validation with Constants

```typescript
import { BlockConstants } from '@nimbus/shared';

function isValidBlockId(id: number): boolean {
  return (
    id >= BlockConstants.MIN_BLOCK_TYPE_ID &&
    id <= BlockConstants.MAX_BLOCK_TYPE_ID
  );
}

function isAir(id: number): boolean {
  return id === BlockConstants.AIR_BLOCK_ID;
}
```

### 3. Player Movement

```typescript
import { EntityConstants, PhysicsConstants } from '@nimbus/shared';

function updatePlayer(player: EntityData, deltaTime: number) {
  const speed = player.isSprinting
    ? EntityConstants.PLAYER_SPRINT_SPEED
    : player.isCrouching
    ? EntityConstants.PLAYER_CROUCH_SPEED
    : EntityConstants.PLAYER_WALK_SPEED;

  // Apply gravity
  player.velocity.y -= PhysicsConstants.GRAVITY * deltaTime;

  // Terminal velocity
  if (player.velocity.y < -PhysicsConstants.TERMINAL_VELOCITY) {
    player.velocity.y = -PhysicsConstants.TERMINAL_VELOCITY;
  }

  // Apply drag
  player.velocity = Vector3Utils.multiply(
    player.velocity,
    PhysicsConstants.AIR_DRAG
  );
}
```

### 4. Network Timeouts

```typescript
import { NetworkConstants } from '@nimbus/shared';

// Calculate ping deadline
const deadline =
  lastPingAt +
  worldInfo.settings.pingInterval * 1000 +
  NetworkConstants.PING_TIMEOUT_BUFFER_MS;

// Check timeout
if (Date.now() > deadline) {
  disconnect('Ping timeout');
}

// Reconnect logic
let reconnectAttempts = 0;

function reconnect() {
  if (reconnectAttempts >= NetworkConstants.MAX_RECONNECT_ATTEMPTS) {
    showError('Failed to reconnect');
    return;
  }

  reconnectAttempts++;
  setTimeout(() => {
    attemptConnection();
  }, NetworkConstants.RECONNECT_DELAY_MS);
}
```

### 5. Camera Setup

```typescript
import { CameraConstants } from '@nimbus/shared';

function setupCamera(scene: BABYLON.Scene) {
  const camera = new BABYLON.UniversalCamera('camera', position, scene);

  camera.fov = (CameraConstants.FOV_DEFAULT * Math.PI) / 180;
  camera.minZ = CameraConstants.NEAR_PLANE;
  camera.maxZ = CameraConstants.FAR_PLANE;

  return camera;
}

function updateCameraRotation(camera: Camera, pitch: number) {
  // Clamp pitch
  const clampedPitch = Math.max(
    CameraConstants.PITCH_MIN,
    Math.min(CameraConstants.PITCH_MAX, pitch)
  );

  camera.rotation.x = (clampedPitch * Math.PI) / 180;
}
```

### 6. Render Distance

```typescript
import { RenderConstants } from '@nimbus/shared';

function updateVisibleChunks(cameraPos: Vector3, renderDistance?: number) {
  const distance = renderDistance ?? RenderConstants.RENDER_DISTANCE_DEFAULT;
  const maxDistance = Math.min(distance, RenderConstants.RENDER_DISTANCE_MAX);

  chunks.forEach(chunk => {
    const dist = calculateDistance(cameraPos, chunk);
    chunk.isVisible = dist <= maxDistance * chunkSize;

    // Calculate LOD
    const normalizedDist = dist / (maxDistance * chunkSize);
    if (normalizedDist < RenderConstants.LOD_THRESHOLDS[0]) {
      chunk.lod = 0;  // High detail
    } else if (normalizedDist < RenderConstants.LOD_THRESHOLDS[1]) {
      chunk.lod = 1;  // Medium
    } else if (normalizedDist < RenderConstants.LOD_THRESHOLDS[2]) {
      chunk.lod = 2;  // Low
    } else {
      chunk.lod = 3;  // Very low
    }
  });
}
```

### 7. Array Size Limits

```typescript
import { LimitConstants } from '@nimbus/shared';

// Validate before sending
function sendBlockUpdate(blocks: Block[]) {
  if (blocks.length > LimitConstants.MAX_BLOCKS_PER_MESSAGE) {
    console.error(
      `Too many blocks: ${blocks.length} (max: ${LimitConstants.MAX_BLOCKS_PER_MESSAGE})`
    );
    return;
  }

  ws.send(JSON.stringify({ t: 'b.cu', d: blocks }));
}
```

### 8. Default Values

```typescript
import { EntityConstants, BlockConstants } from '@nimbus/shared';

// Create player with defaults
function createPlayer(id: string, username: string): EntityData {
  return {
    id,
    type: EntityType.PLAYER,
    username,
    position: { x: 0, y: WorldConstants.GROUND_LEVEL_DEFAULT, z: 0 },
    rotation: { y: 0, p: 0 },
    health: {
      current: EntityConstants.PLAYER_HEALTH_DEFAULT,
      max: EntityConstants.PLAYER_MAX_HEALTH_DEFAULT,
      alive: true,
    },
    state: 'idle',
  };
}

// Create block with defaults
function createBlock(position: Vector3, blockTypeId: number): Block {
  return {
    position,
    blockTypeId,
    status: BlockConstants.DEFAULT_STATUS,
  };
}
```

## Benefits

✅ **No magic numbers** - All values named and documented
✅ **Central location** - Easy to find and modify
✅ **Type-safe** - `as const` for literal types
✅ **Grouped** - Related constants together
✅ **Discoverable** - IDE autocomplete

## Best Practices

### ✅ DO
- Use constants instead of magic numbers
- Import specific constant groups you need
- Use Constants.Group.VALUE for grouped access
- Update constants instead of hardcoded values

### ❌ DON'T
- Don't use magic numbers (16, 256, 65535, etc.)
- Don't duplicate constants in different files
- Don't modify constants (they're readonly)
- Don't create new constants without adding to this file
