# Shared Types - Nimbus Voxel Engine 2.0

Shared data types for Nimbus client and server, based on the object model 2.0.

## Organization Principle

**One data structure per file.** Related enums, status types, and helper types can be in the same file if they are only relevant to that specific type.

## Type Files

### Core Types
- **Vector3.ts** - 3D position/offset type
- **Shape.ts** - Block shape enumeration (cube, cross, model, etc.)

### Block Types
- **BlockType.ts** - Block type definition (registry entry) with BlockStatus enum
- **Block.ts** - Block instance in the world
- **BlockModifier.ts** - Visual and behavioral properties with related enums:
  - `TextureKey`, `TextureRotation`, `SamplingMode`, `TransparencyMode`, `Direction`
  - Modifier interfaces: Visibility, Wind, Illumination, Physics, Effects, Sound
- **BlockMetadata.ts** - Additional metadata for block instances

### Chunk Types
- **ChunkData.ts** - Internal chunk representation with Uint16Array storage

### World Types
- **World.ts** - World information and settings

### Animation & Effects
- **AnimationData.ts** - Animation sequences with AnimationEffectType and EasingType enums
- **AreaData.ts** - Area definition with effects
- **EffectData.ts** - Environmental effect definition

### Entity Types
- **EntityData.ts** - Entity (NPC, Player, etc.) with EntityType enum

## Usage

```typescript
import {
  BlockType,
  Block,
  Shape,
  ChunkData
} from '@nimbus/shared';

// All shared types are available through the main package export
// Client-specific types (ClientBlock, ClientChunk, ClientBlockType)
// are in @nimbus/engine package
```

## Type Hierarchy

```
BlockType (Registry)
  ↓ references (by ID)
Block (Instance in world)
  ↓ network transmission
  ↓ client-side resolution
ClientBlock (in @nimbus/engine)
  ↓ uses
ClientBlockType (in @nimbus/engine)
```

## Design Principles

1. **Minimal Data**: All optional fields minimize transmission/storage
2. **Type Safety**: Full TypeScript strict mode compliance
3. **Network/Server Focus**: Shared types are for network transmission and server logic
4. **Metadata Merging**: Priority system for block properties
5. **Extensibility**: Custom status values (100+) for world-specific states

**Note**: Client-specific types (ClientBlock, ClientChunk, ClientBlockType) with caching
and rendering optimizations are located in the `@nimbus/engine` package.

## Metadata Merging Priority

When resolving block properties (first match wins):

1. Block-BlockType-status-Metadata (instance status)
2. Block-BlockType-ID status-Metadata (instance status = world status)
3. Block-BlockType-ID status-Metadata (base status)
4. Block-BlockType-ID status-Metadata (base status = world status)
5. Default values (e.g., shape = 0)

## Rendering Order

For block transformations: **Offsets → Scale → Rotation**

## Status Values

- `0-9`: Standard states (default, open, closed, locked, destroyed)
- `10-17`: Seasonal states (winter, spring, summer, autumn + transitions)
- `100+`: Custom world-specific states

## Notes

- **AnimationData** is not yet finalized (marked as TODO)
- **EntityData** is not yet fully defined (marked as TODO)
- Parameter names may be shortened for network transmission
- See `object-model-2.0.md` for complete specification
