# Block Modifiers System

Block modifiers define the visual and behavioral properties of blocks for different status values.

## Architecture

```
BlockType (Registry)
  └─ modifiers: Record<number, BlockModifier>
       ├─ 0: BlockModifier (DEFAULT)
       ├─ 1: BlockModifier (OPEN)
       └─ 2: BlockModifier (CLOSED)

Block (Instance)
  ├─ blockTypeId: number
  ├─ status: number
  ├─ modifiers?: Record<number, BlockModifier> (instance overrides)
  └─ metadata?: BlockMetadata (organizational data)
```

## Status → Modifier Resolution

When rendering a block, modifiers are resolved in this priority order:

1. **Block.modifiers[status]** - Instance-specific modifier override
2. **BlockType.modifiers[status]** - Type-defined modifier for status
3. **BlockType.modifiers[0]** - Fallback to DEFAULT status

## BlockType Modifiers

Every BlockType defines modifiers for different status values:

```typescript
const grassBlockType: BlockType = {
  id: 1,
  initialStatus: 0,
  modifiers: {
    // Default status (always required)
    0: {
      visibility: {
        shape: Shape.CUBE,
        textures: {
          [TextureKey.TOP]: { path: '/grass_top.png' },
          [TextureKey.SIDE]: { path: '/grass_side.png' },
          [TextureKey.BOTTOM]: { path: '/dirt.png' }
        }
      },
      physics: {
        solid: true
      }
    },
    // Winter status (seasonal)
    10: {
      visibility: {
        shape: Shape.CUBE,
        textures: {
          [TextureKey.TOP]: { path: '/grass_snow.png' },
          [TextureKey.SIDE]: { path: '/grass_side_snow.png' },
          [TextureKey.BOTTOM]: { path: '/dirt.png' }
        }
      },
      physics: {
        solid: true
      }
    }
  }
};
```

## Block Instance Status

Each block instance can have a different status:

```typescript
const block: Block = {
  position: { x: 10, y: 64, z: 10 },
  blockTypeId: 1,  // grass block
  status: 10       // winter status
};

// Renderer resolves: BlockType[1].modifiers[10] → winter grass
```

## Instance Modifier Overrides

Individual block instances can override modifiers via metadata:

```typescript
const specialDoor: Block = {
  position: { x: 5, y: 64, z: 5 },
  blockTypeId: 42,  // wooden door
  status: 1,        // open
  modifiers: {
    // Override open state for this specific door
    1: {
      visibility: {
        shape: Shape.MODEL,
        path: '/models/ancient_door_open.babylon',
        textures: {
          [TextureKey.DIFFUSE]: { path: '/ancient_wood.png' }
        }
      },
      effects: {
        sky: {
          intensity: 0.8,
          color: '#ffaa00'
        }
      }
    }
  },
  metadata: {
    groupId: 1  // Organizational data
  }
};

// Renderer resolves: Block.modifiers[1] → ancient door (override)
```

## Status Values

### Standard Status (0-9)
- `0` - DEFAULT (required for all BlockTypes)
- `1` - OPEN
- `2` - CLOSED
- `3` - LOCKED
- `5` - DESTROYED

### Seasonal Status (10-17)
- `10` - WINTER
- `11` - WINTER_SPRING (transition)
- `12` - SPRING
- `13` - SPRING_SUMMER (transition)
- `14` - SUMMER
- `15` - SUMMER_AUTUMN (transition)
- `16` - AUTUMN
- `17` - AUTUMN_WINTER (transition)

### Custom Status (100+)
World-specific custom states, e.g.:
- `100` - ENCHANTED
- `666` - APOCALYPSE
- `1000` - SPECIAL_EVENT

## World Status Changes

The server can trigger global status changes that affect all blocks:

```typescript
// Server sends world status update
{
  t: 'w.su',
  d: { s: 10 }  // Switch to WINTER
}

// All blocks with modifiers[10] will be re-rendered
// Blocks without modifiers[10] keep their current appearance
```

## Modifier Merging

When resolving block properties, modifiers are merged with this priority (first match wins):

1. Block.modifiers[status] (instance-specific override)
2. BlockType.modifiers[status] (type-defined modifier for status)
3. BlockType.modifiers[0] (DEFAULT fallback)

## Use Cases

### 1. Seasonal Changes
```typescript
// All grass blocks automatically show snow in winter
worldStatusUpdate({ s: BlockStatus.WINTER });
```

### 2. Interactive Blocks
```typescript
// Door switches between open/closed
block.status = block.status === BlockStatus.CLOSED
  ? BlockStatus.OPEN
  : BlockStatus.CLOSED;
```

### 3. Unique Instances
```typescript
// Special boss door with custom appearance
block.modifiers = {
  [BlockStatus.CLOSED]: { /* epic closed appearance */ },
  [BlockStatus.OPEN]: { /* epic open appearance */ }
};
block.metadata = {
  groupId: 100  // Boss room group
};
```

### 4. Damage States
```typescript
// Building takes damage, shows destroyed state
block.status = BlockStatus.DESTROYED;
```

## Performance Considerations

- **BlockType.modifiers** are shared across all instances (memory efficient)
- **Block.modifiers** are per-instance (use sparingly)
- Status changes trigger re-rendering only if modifier exists
- Missing status values fall back to DEFAULT (no error)

## Network Transmission

Modifiers are transmitted differently:

- **BlockType.modifiers**: Sent once during registry sync
- **Block.modifiers**: Only sent for blocks with overrides
- **Block.status**: Always sent with block data (small integer)

## Example: Door Block

```typescript
const doorBlockType: BlockType = {
  id: 42,
  initialStatus: BlockStatus.CLOSED,
  modifiers: {
    // Closed state
    [BlockStatus.CLOSED]: {
      visibility: {
        shape: Shape.FLAT,
        textures: {
          [TextureKey.FRONT]: { path: '/door_closed.png' }
        }
      },
      physics: {
        solid: true
      },
      audio: [
        {
          type: AudioType.STEPS,
          path: '/sounds/door_close.ogg',
          volume: 1.0,
          enabled: true
        }
      ]
    },
    // Open state
    [BlockStatus.OPEN]: {
      visibility: {
        shape: Shape.FLAT,
        textures: {
          [TextureKey.FRONT]: { path: '/door_open.png' }
        },
        rotationY: 90
      },
      physics: {
        solid: false
      },
      audio: [
        {
          type: AudioType.STEPS,
          path: '/sounds/door_open.ogg',
          volume: 1.0,
          enabled: true
        }
      ]
    }
  }
};

// In game
const door: Block = {
  position: { x: 10, y: 64, z: 10 },
  blockTypeId: 42,
  status: BlockStatus.CLOSED
};

// Player interacts
door.status = BlockStatus.OPEN;
// → Renderer switches to open modifier
// → Audio plays: door_open.ogg
// → Physics updates: no longer solid
```
