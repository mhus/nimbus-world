# Rotation Type Usage

Centralized rotation type used throughout the codebase.

## Definition

```typescript
interface Rotation {
  y: number;  // Yaw - horizontal rotation (degrees)
  p: number;  // Pitch - vertical rotation (degrees)
  r?: number; // Roll - tilt rotation (degrees, optional)
}
```

## Used In

### 1. EntityData
```typescript
interface EntityData {
  rotation: Rotation;  // All entities have rotation
  // ...
}
```

### 2. UserMessage (Network)
```typescript
interface UserMovementData {
  r?: Rotation;  // Player rotation update
}

interface PlayerTeleportData {
  r: Rotation;  // Teleport rotation
}
```

### 3. Future Uses
- Camera controller
- Block placement rotation
- Model rotation in rendering
- Animation rotation keyframes

## Utility Functions

### Creation
```typescript
import { RotationUtils } from '@nimbus/shared';

// Create rotation
const rot = RotationUtils.create(90, 0);        // yaw=90°, pitch=0°
const rot2 = RotationUtils.create(180, 45, 0);  // with roll

// Zero rotation
const zero = RotationUtils.zero();              // (0, 0, 0)

// Clone
const copy = RotationUtils.clone(rot);
```

### Comparison
```typescript
// Check equality
if (RotationUtils.equals(rot1, rot2)) {
  console.log('Same rotation');
}

// With custom epsilon
if (RotationUtils.equals(rot1, rot2, 0.1)) {
  console.log('Almost same rotation');
}
```

### Normalization
```typescript
// Normalize to 0-360 range
const rot = { y: 450, p: -30 };  // Out of range
const normalized = RotationUtils.normalize(rot);
// { y: 90, p: 330 }

// Clamp pitch to prevent camera flip
const rot = { y: 0, p: 120 };  // Invalid pitch
const clamped = RotationUtils.clampPitch(rot);
// { y: 0, p: 89 }  // Max 89°
```

### Interpolation
```typescript
// Smooth rotation interpolation
const start = { y: 0, p: 0 };
const end = { y: 90, p: 45 };

// 50% between start and end
const mid = RotationUtils.lerp(start, end, 0.5);
// { y: 45, p: 22.5 }
```

### Conversion
```typescript
// Degrees to radians (for rendering)
const degrees = { y: 90, p: 45 };
const radians = RotationUtils.toRadians(degrees);
// { y: 1.5708, p: 0.7854 }

// Radians to degrees
const back = RotationUtils.toDegrees(radians);
// { y: 90, p: 45 }
```

### Direction Vectors
```typescript
// Get forward direction from rotation
const rotation = { y: 90, p: 0 };
const forward = RotationUtils.getForwardVector(rotation);
// { x: 1, y: 0, z: 0 }  // Facing east

// Get right direction
const right = RotationUtils.getRightVector(rotation);
// { x: 0, y: 0, z: -1 }  // Right is south
```

### Debugging
```typescript
const rotation = { y: 45, p: -30, r: 10 };
console.log(RotationUtils.toString(rotation));
// "Rotation(y:45.0°, p:-30.0°, r:10.0°)"

const simple = { y: 90, p: 0 };
console.log(RotationUtils.toString(simple));
// "Rotation(y:90.0°, p:0.0°)"
```

## Common Use Cases

### 1. Camera Controller

```typescript
class CameraController {
  private rotation: Rotation = RotationUtils.zero();

  update(mouseDeltaX: number, mouseDeltaY: number, sensitivity: number) {
    // Update rotation based on mouse movement
    this.rotation.y += mouseDeltaX * sensitivity;
    this.rotation.p += mouseDeltaY * sensitivity;

    // Normalize and clamp
    this.rotation = RotationUtils.normalize(this.rotation);
    this.rotation = RotationUtils.clampPitch(this.rotation);

    // Update camera
    const forward = RotationUtils.getForwardVector(this.rotation);
    camera.setTarget(Vector3Utils.add(camera.position, forward));
  }

  getRotation(): Rotation {
    return RotationUtils.clone(this.rotation);
  }
}
```

### 2. Player Movement

```typescript
function movePlayer(player: EntityData, direction: 'forward' | 'backward' | 'left' | 'right') {
  const forward = RotationUtils.getForwardVector(player.rotation);
  const right = RotationUtils.getRightVector(player.rotation);

  const speed = player.isSprinting ? 5.6 : 4.3;

  switch (direction) {
    case 'forward':
      player.velocity = Vector3Utils.multiply(forward, speed);
      break;
    case 'backward':
      player.velocity = Vector3Utils.multiply(forward, -speed);
      break;
    case 'left':
      player.velocity = Vector3Utils.multiply(right, -speed);
      break;
    case 'right':
      player.velocity = Vector3Utils.multiply(right, speed);
      break;
  }
}
```

### 3. Smooth Camera Rotation

```typescript
class SmoothCamera {
  private currentRotation: Rotation = RotationUtils.zero();
  private targetRotation: Rotation = RotationUtils.zero();

  setTarget(rotation: Rotation) {
    this.targetRotation = rotation;
  }

  update(deltaTime: number) {
    const lerpFactor = Math.min(1, deltaTime * 5); // 5 = smoothness

    // Interpolate rotation
    this.currentRotation = RotationUtils.lerp(
      this.currentRotation,
      this.targetRotation,
      lerpFactor
    );

    // Apply to camera
    this.applyRotation(this.currentRotation);
  }
}
```

### 4. Network Send/Receive

```typescript
// Send player rotation to server
function sendPlayerRotation(rotation: Rotation) {
  const message: UserMovementMessage = {
    t: MessageType.USER_MOVEMENT,
    d: {
      r: rotation  // ✓ Uses Rotation type
    }
  };
  ws.send(JSON.stringify(message));
}

// Receive teleport from server
onPlayerTeleport((data: PlayerTeleportData) => {
  player.position = data.p;
  player.rotation = data.r;  // ✓ Uses Rotation type

  // Normalize and apply
  player.rotation = RotationUtils.normalize(player.rotation);
});
```

### 5. Entity Rendering

```typescript
function renderEntity(entity: EntityData) {
  const mesh = getMesh(entity.id);

  // Convert rotation to radians for Babylon.js
  const rotRad = RotationUtils.toRadians(entity.rotation);

  mesh.rotation.y = rotRad.y;
  mesh.rotation.x = rotRad.p;  // Pitch maps to X in Babylon
  if (rotRad.r !== undefined) {
    mesh.rotation.z = rotRad.r;
  }
}
```

### 6. Look At Target

```typescript
function lookAt(from: Vector3, to: Vector3): Rotation {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const dz = to.z - from.z;

  const distance = Math.sqrt(dx * dx + dz * dz);

  const yaw = Math.atan2(dx, dz) * 180 / Math.PI;
  const pitch = -Math.atan2(dy, distance) * 180 / Math.PI;

  return RotationUtils.create(yaw, pitch);
}

// Usage: NPC looks at player
npc.rotation = lookAt(npc.position, player.position);
```

### 7. Raycasting Direction

```typescript
function raycast(origin: Vector3, rotation: Rotation, maxDistance: number) {
  const direction = RotationUtils.getForwardVector(rotation);

  const ray = new BABYLON.Ray(
    new BABYLON.Vector3(origin.x, origin.y, origin.z),
    new BABYLON.Vector3(direction.x, direction.y, direction.z),
    maxDistance
  );

  return scene.pickWithRay(ray);
}

// Usage: Player looking at block
const hit = raycast(player.position, player.rotation, 10);
if (hit.pickedMesh) {
  console.log('Looking at:', hit.pickedMesh.name);
}
```

## Coordinate System

### Yaw (Y-axis rotation)
- 0° = North (+Z)
- 90° = East (+X)
- 180° = South (-Z)
- 270° = West (-X)

### Pitch (X-axis rotation)
- 0° = Horizontal
- +45° = Looking up
- -45° = Looking down
- Typically clamped to ±89° to prevent gimbal lock

### Roll (Z-axis rotation)
- 0° = Upright
- ±90° = Tilted
- Usually 0 for players (no tilt)

## Best Practices

### ✅ DO
- Use `Rotation` type everywhere (not separate y, p, r variables)
- Use `RotationUtils` for operations (don't implement manually)
- Normalize rotation after user input
- Clamp pitch to prevent camera flip
- Use radians for rendering (convert with `toRadians()`)
- Use degrees for storage and network (human-readable)

### ❌ DON'T
- Don't use separate variables for yaw/pitch/roll
- Don't forget to normalize after adding angles
- Don't allow pitch beyond ±89°
- Don't mix degrees and radians
- Don't mutate rotation without cloning first
- Don't use Vector3 for rotation (use Rotation)

## Summary

- **Centralized**: Single Rotation type in `types/Rotation.ts`
- **Used everywhere**: EntityData, UserMessage, future camera/rendering
- **Utilities**: 12+ helper functions for common operations
- **Type-safe**: No mixing of rotation representations
- **Degrees by default**: Storage/network in degrees, convert to radians for rendering
- **Optional roll**: Most entities don't need roll
