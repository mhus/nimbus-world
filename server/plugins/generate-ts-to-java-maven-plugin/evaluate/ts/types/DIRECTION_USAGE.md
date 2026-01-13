# Direction Bitfield System

Efficient direction flags using bitfield operations (1 byte storage).

## Direction Enum

```typescript
enum Direction {
  NORTH = 1,   // 0b000001
  SOUTH = 2,   // 0b000010
  EAST = 4,    // 0b000100
  WEST = 8,    // 0b001000
  UP = 16,     // 0b010000
  DOWN = 32,   // 0b100000
}
```

## Bitfield Structure

```
Bit:  5    4   3    2    1     0
     DOWN  UP WEST EAST SOUTH NORTH
```

## Usage in Physics

The main use case is in `PhysicsModifier.gateFromDirection`:

```typescript
interface PhysicsModifier {
  gateFromDirection?: Direction;  // Bitfield of allowed directions
}
```

This allows a block to be passable only from specific directions (e.g., one-way gates).

## Quick Start

```typescript
import { Direction, DirectionHelper } from '@nimbus/shared';

// Create gate that allows passage from north and south
let gate = Direction.NORTH | Direction.SOUTH;
// gate = 3 (0b000011)

// Check if direction is allowed
if (DirectionHelper.hasDirection(gate, Direction.NORTH)) {
  console.log('Can pass from north');
}

// Add east direction
gate = DirectionHelper.addDirection(gate, Direction.EAST);
// gate = 7 (0b000111)
```

## Helper Functions

### Checking Directions

```typescript
const gate = Direction.NORTH | Direction.SOUTH;

// Check single direction
DirectionHelper.hasDirection(gate, Direction.NORTH);  // true
DirectionHelper.hasDirection(gate, Direction.EAST);   // false

// Get all directions
DirectionHelper.getDirections(gate);
// ['north', 'south']

// Count directions
DirectionHelper.countDirections(gate);  // 2
```

### Modifying Directions

```typescript
let gate = 0;  // No directions

// Add directions
gate = DirectionHelper.addDirection(gate, Direction.NORTH);
gate = DirectionHelper.addDirection(gate, Direction.SOUTH);
// gate = 3

// Remove direction
gate = DirectionHelper.removeDirection(gate, Direction.SOUTH);
// gate = 1

// Toggle direction
gate = DirectionHelper.toggleDirection(gate, Direction.SOUTH);
// gate = 3 (added)
gate = DirectionHelper.toggleDirection(gate, Direction.SOUTH);
// gate = 1 (removed)
```

### Creating from Names

```typescript
// Create from string array
const gate = DirectionHelper.fromDirections(['north', 'south', 'east']);
// gate = 7

// Check what was set
console.log(DirectionHelper.toString(gate));
// "Direction(north | south | east)"
```

### Horizontal/Vertical Checks

```typescript
const horizontalGate = Direction.NORTH | Direction.SOUTH;
const verticalGate = Direction.UP | Direction.DOWN;

DirectionHelper.hasHorizontalDirection(horizontalGate);  // true
DirectionHelper.hasVerticalDirection(horizontalGate);    // false

DirectionHelper.hasHorizontalDirection(verticalGate);    // false
DirectionHelper.hasVerticalDirection(verticalGate);      // true
```

### Opposite Direction

```typescript
const opposite = DirectionHelper.getOpposite(Direction.NORTH);
// Direction.SOUTH

const opposite2 = DirectionHelper.getOpposite(Direction.UP);
// Direction.DOWN
```

## Use Cases

### 1. One-Way Gate

```typescript
// Block allows passage only from north
const oneWayGate: PhysicsModifier = {
  solid: true,
  gateFromDirection: Direction.NORTH
};

// Check if player can pass
function canPassGate(
  gate: PhysicsModifier,
  approachDirection: Direction
): boolean {
  if (!gate.gateFromDirection) {
    return false;  // No gate directions set = solid
  }

  return DirectionHelper.hasDirection(
    gate.gateFromDirection,
    approachDirection
  );
}

// Usage
const fromNorth = canPassGate(oneWayGate, Direction.NORTH);  // true
const fromSouth = canPassGate(oneWayGate, Direction.SOUTH);  // false
```

### 2. Two-Way Door

```typescript
// Door allows passage from north and south
const door: PhysicsModifier = {
  solid: true,
  gateFromDirection: Direction.NORTH | Direction.SOUTH
};

// Player approaching from any horizontal direction
const playerDirection = getPlayerFacingDirection();
const canPass = DirectionHelper.hasDirection(
  door.gateFromDirection!,
  playerDirection
);
```

### 3. Ladder (Vertical Movement)

```typescript
// Ladder allows movement up and down
const ladder: PhysicsModifier = {
  solid: false,
  climbable: 1,
  gateFromDirection: Direction.UP | Direction.DOWN
};

if (DirectionHelper.hasVerticalDirection(ladder.gateFromDirection!)) {
  console.log('This is a vertical passage');
}
```

### 4. Teleporter Portal

```typescript
// Portal allows entry from any horizontal direction
const portal: PhysicsModifier = {
  solid: false,
  gateFromDirection:
    Direction.NORTH | Direction.SOUTH | Direction.EAST | Direction.WEST
};

if (DirectionHelper.hasHorizontalDirection(portal.gateFromDirection!)) {
  console.log('Can enter portal from sides');
}
```

### 5. Block Editor - Gate Configuration

```typescript
function setGateDirections(block: Block, directions: string[]) {
  const gateValue = DirectionHelper.fromDirections(directions);

  // Apply to block modifier
  const modifier = getBlockModifier(block);
  if (!modifier.physics) {
    modifier.physics = {};
  }

  modifier.physics.gateFromDirection = gateValue;
}

// Usage
setGateDirections(block, ['north', 'south']);
// Block is now a two-way gate
```

### 6. Collision Detection

```typescript
function checkBlockCollision(
  block: Block,
  playerPos: Vector3,
  playerVelocity: Vector3
): boolean {
  const modifier = getBlockModifier(block);

  if (!modifier.physics?.solid) {
    return false;  // Not solid, no collision
  }

  if (!modifier.physics.gateFromDirection) {
    return true;  // Solid with no gate = always collide
  }

  // Determine approach direction from velocity
  const approachDir = getDirectionFromVelocity(playerVelocity);

  // Check if can pass from this direction
  return !DirectionHelper.hasDirection(
    modifier.physics.gateFromDirection,
    approachDir
  );
}

function getDirectionFromVelocity(velocity: Vector3): Direction {
  // Determine primary direction from velocity
  const absX = Math.abs(velocity.x);
  const absY = Math.abs(velocity.y);
  const absZ = Math.abs(velocity.z);

  if (absY > absX && absY > absZ) {
    return velocity.y > 0 ? Direction.UP : Direction.DOWN;
  } else if (absX > absZ) {
    return velocity.x > 0 ? Direction.EAST : Direction.WEST;
  } else {
    return velocity.z > 0 ? Direction.SOUTH : Direction.NORTH;
  }
}
```

### 7. Multi-Direction Gates

```typescript
// Complex gate: can enter from north or east, but not others
const complexGate = DirectionHelper.fromDirections(['north', 'east']);

console.log(DirectionHelper.toString(complexGate));
// "Direction(north | east)"

// Check each direction
console.log(DirectionHelper.hasDirection(complexGate, Direction.NORTH)); // true
console.log(DirectionHelper.hasDirection(complexGate, Direction.SOUTH)); // false
console.log(DirectionHelper.hasDirection(complexGate, Direction.EAST));  // true
console.log(DirectionHelper.hasDirection(complexGate, Direction.WEST));  // false
```

## Network Optimization

### Efficient Storage

```typescript
// 6 directions in 1 byte (6 bits)
const allDirections =
  Direction.NORTH |
  Direction.SOUTH |
  Direction.EAST |
  Direction.WEST |
  Direction.UP |
  Direction.DOWN;

// Value: 63 (0b00111111) = 1 byte!
```

### JSON Serialization

```typescript
// Send as number
{
  "physics": {
    "gateFromDirection": 3  // North + South
  }
}

// Receive and use
const gate = physics.gateFromDirection ?? 0;
if (DirectionHelper.hasDirection(gate, Direction.NORTH)) {
  // Can pass from north
}
```

## Common Patterns

### All Horizontal Directions
```typescript
const horizontal =
  Direction.NORTH | Direction.SOUTH | Direction.EAST | Direction.WEST;
// 15 (0b001111)
```

### All Vertical Directions
```typescript
const vertical = Direction.UP | Direction.DOWN;
// 48 (0b110000)
```

### All Directions
```typescript
const all =
  Direction.NORTH |
  Direction.SOUTH |
  Direction.EAST |
  Direction.WEST |
  Direction.UP |
  Direction.DOWN;
// 63 (0b111111)
```

### No Directions (Solid)
```typescript
const solid = 0;  // No directions allowed
```

## Best Practices

### ✅ DO
- Use DirectionHelper functions (not manual bitwise ops)
- Use Direction enum values (not magic numbers)
- Store as single number in physics modifier
- Use `hasDirection()` to check permissions
- Use `fromDirections()` for string-based config

### ❌ DON'T
- Don't use magic numbers (`gateFromDirection: 3`)
- Don't mutate direction values directly
- Don't forget to check for undefined
- Don't use multiple boolean flags instead of bitfield
- Don't mix up directions (north vs south, etc.)

## Debugging

```typescript
function debugGate(physics: PhysicsModifier) {
  if (!physics.gateFromDirection) {
    console.log('Gate: None (solid block)');
    return;
  }

  const directions = DirectionHelper.getDirections(physics.gateFromDirection);
  const count = DirectionHelper.countDirections(physics.gateFromDirection);

  console.log(`Gate directions (${count}): ${directions.join(', ')}`);
  console.log(`Binary: 0b${physics.gateFromDirection.toString(2).padStart(8, '0')}`);

  if (DirectionHelper.hasHorizontalDirection(physics.gateFromDirection)) {
    console.log('Has horizontal passage');
  }
  if (DirectionHelper.hasVerticalDirection(physics.gateFromDirection)) {
    console.log('Has vertical passage');
  }
}

// Output:
// Gate directions (2): north, south
// Binary: 0b00000011
// Has horizontal passage
```

## Summary

- **Efficient**: 6 directions in 1 byte
- **Type-safe**: Helper functions prevent errors
- **Flexible**: Support for multi-direction gates
- **Debuggable**: toString() for inspection
- **Similar to FaceVisibility**: Same bitfield pattern
