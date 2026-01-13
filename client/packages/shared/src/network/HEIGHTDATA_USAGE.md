# HeightData Usage

Named tuple for chunk height information with clear semantics.

## Type Definitions

### Network HeightData (ChunkData.ts)

For network transfer between server and client:

```typescript
type HeightData = readonly [
  x: number,           // Local X coordinate in chunk
  z: number,           // Local Z coordinate in chunk
  maxHeight: number,   // Highest block in this column
  groundLevel: number  // First solid block from bottom
];
```

### Client HeightData (ClientChunk.ts)

Extended version for client-side processing:

```typescript
type ClientHeightData = readonly [
  x: number,
  z: number,
  maxHeight: number,
  minHeight: number,
  groundLevel: number,
  waterHeight?: number  // Optional: highest water block (future feature)
];
```

**Note:** Water level is **not stored in WorldInfo** and water blocks are **not specially rendered**.

## Benefits of Named Tuple

✅ **Self-documenting** - Parameter names visible in IDE
✅ **Type-safe** - TypeScript enforces tuple length
✅ **Readonly** - Prevents accidental mutations
✅ **Autocomplete** - IDE shows parameter names

## Usage Examples

### 1. Creating HeightData (Network)

```typescript
// Create height data for a chunk position
const heightData: HeightData = [
  0,    // x (local coordinate)
  0,    // z (local coordinate)
  128,  // maxHeight
  64    // groundLevel
];

// With named parameters (visible in IDE)
const heightData: HeightData = [
  /*x*/ 0,
  /*z*/ 0,
  /*maxHeight*/ 128,
  /*groundLevel*/ 64
];
```

### 2. Destructuring

```typescript
// Destructure with clear names
const [x, z, maxHeight, groundLevel] = heightData;

console.log(`Position: (${x}, ${z})`);
console.log(`Max: ${maxHeight}, Ground: ${groundLevel}`);

// Partial destructuring (skip position)
const [, , maxHeight, groundLevel] = heightData;
console.log(`Height: ${maxHeight}, Ground: ${groundLevel}`);
```

### 3. Accessing by Index

```typescript
const x = heightData[0];
const z = heightData[1];
const maxHeight = heightData[2];
const groundLevel = heightData[3];
```

### 4. In ChunkDataTransferObject

```typescript
interface ChunkDataTransferObject {
  cx: number;  // Chunk X coordinate
  cz: number;  // Chunk Z coordinate
  b: Block[];
  h: HeightData[];  // Array of height data for each (x,z) position
  a?: AreaData[];
  e?: EntityData[];
}

// Create chunk with height data
const chunkData: ChunkDataTransferObject = {
  cx: 0,
  cz: 0,
  b: [],
  h: [
    [0, 0, 128, 64],  // Position (0, 0): maxHeight=128, groundLevel=64
    [1, 0, 130, 65],  // Position (1, 0): maxHeight=130, groundLevel=65
    [2, 0, 125, 63],  // Position (2, 0): maxHeight=125, groundLevel=63
    // ... more height data for each XZ position in chunk
  ]
};
```

### 5. Processing Height Data

```typescript
function analyzeChunk(chunk: ChunkDataTransferObject) {
  chunk.h?.forEach((heightData) => {
    const [x, z, maxHeight, groundLevel] = heightData;

    console.log(`Position (${x}, ${z}):`);
    console.log(`  Max height: ${maxHeight}`);
    console.log(`  Ground level: ${groundLevel}`);
    console.log(`  Height difference: ${maxHeight - groundLevel}`);
  });
}
```

### 6. Generating Height Data

```typescript
function generateHeightData(localX: number, localZ: number): HeightData {
  // Use noise function or other terrain generation
  const groundLevel = getTerrainHeight(localX, localZ);

  // Calculate max height (e.g., add trees, structures)
  const maxHeight = Math.min(255, groundLevel + 10);

  return [localX, localZ, maxHeight, groundLevel];
}

// Generate for entire chunk
function generateChunkHeightData(chunkX: number, chunkZ: number, chunkSize: number): HeightData[] {
  const heightData: HeightData[] = [];

  for (let z = 0; z < chunkSize; z++) {
    for (let x = 0; x < chunkSize; x++) {
      const worldX = chunkX * chunkSize + x;
      const worldZ = chunkZ * chunkSize + z;
      const groundLevel = getTerrainHeight(worldX, worldZ);
      const maxHeight = Math.min(255, groundLevel + 10);

      heightData.push([x, z, maxHeight, groundLevel]);
    }
  }

  return heightData;
}
```

### 7. Validation

```typescript
function isValidHeightData(data: HeightData): boolean {
  const [x, z, maxHeight, groundLevel] = data;

  // Validate position within chunk
  if (x < 0 || x >= 16 || z < 0 || z >= 16) return false;

  // Validate height bounds
  if (maxHeight < -512 || maxHeight > 512) return false;
  if (groundLevel < -512 || groundLevel > 512) return false;

  // Validate ground level is not above max height
  if (groundLevel > maxHeight) return false;

  return true;
}
```

### 8. Network Optimization

```typescript
function serializeHeightData(data: HeightData): number[] {
  // Convert to plain array for JSON serialization
  return [...data];
}

function deserializeHeightData(data: number[]): HeightData | null {
  if (data.length !== 4) return null;

  const heightData: HeightData = [
    data[0], // x
    data[1], // z
    data[2], // maxHeight
    data[3]  // groundLevel
  ];

  return isValidHeightData(heightData) ? heightData : null;
}
```

### 9. Client-Side Processing

```typescript
// Calculate optimal LOD based on height data
function calculateLOD(heightData: HeightData, cameraY: number): number {
  const [, , maxHeight, groundLevel] = heightData;

  const distanceToGround = Math.abs(cameraY - groundLevel);
  const terrainHeight = maxHeight - groundLevel;

  if (distanceToGround > 100 || terrainHeight < 5) {
    return 3; // Low detail
  } else if (distanceToGround > 50) {
    return 2; // Medium detail
  } else {
    return 1; // High detail
  }
}

// Determine chunk rendering priority based on height variation
function getChunkPriority(chunk: ChunkDataTransferObject): number {
  if (!chunk.h || chunk.h.length === 0) return 0;

  let priority = 0;

  chunk.h.forEach(([, , maxHeight, groundLevel]) => {
    // Higher priority for chunks with more variation
    priority += maxHeight - groundLevel;
  });

  return priority / chunk.h.length;
}
```

### 10. Utility Functions

```typescript
// Get average ground level for chunk
function getAverageGroundLevel(heightData: HeightData[]): number {
  const sum = heightData.reduce(
    (acc, [, , , groundLevel]) => acc + groundLevel,
    0
  );
  return sum / heightData.length;
}

// Get height range for chunk
function getHeightRange(heightData: HeightData[]): { min: number; max: number } {
  let min = Infinity;
  let max = -Infinity;

  heightData.forEach(([, , maxHeight, groundLevel]) => {
    min = Math.min(min, groundLevel);
    max = Math.max(max, maxHeight);
  });

  return { min, max };
}

// Check if chunk is flat
function isFlat(heightData: HeightData[], threshold = 5): boolean {
  const { min, max } = getHeightRange(heightData);
  return max - min <= threshold;
}

// Find position with highest terrain
function findHighestPoint(heightData: HeightData[]): { x: number; z: number; height: number } | null {
  if (heightData.length === 0) return null;

  let highest = heightData[0];
  for (const data of heightData) {
    if (data[2] > highest[2]) { // Compare maxHeight
      highest = data;
    }
  }

  return { x: highest[0], z: highest[1], height: highest[2] };
}
```

## IDE Support

When you type `heightData[` in your IDE, you'll see:

```
heightData[0] - x: number
heightData[1] - z: number
heightData[2] - maxHeight: number
heightData[3] - groundLevel: number
```

This makes the code self-documenting and reduces errors.

## Comparison: Before vs After

### Before (unnamed tuple)
```typescript
type HeightData = [number, number, number, number];

// What does each value mean? 🤔
const height = heightData[2];
```

### After (named tuple)
```typescript
type HeightData = readonly [
  x: number,
  z: number,
  maxHeight: number,
  groundLevel: number
];

// Clear meaning! ✅
const [x, z, maxHeight, groundLevel] = heightData;
// or
const maxHeight = heightData[2]; // IDE shows: maxHeight: number
```

## Best Practices

### ✅ DO
- Use destructuring with clear names
- Validate height data bounds
- Use readonly to prevent mutations
- Document units (meters, blocks, etc.)

### ❌ DON'T
- Don't mutate HeightData (it's readonly)
- Don't access by magic indices without comments
- Don't skip validation when deserializing
- Don't mix up parameter order

## Summary

- **Named parameters** improve code readability
- **Readonly** prevents accidental mutations
- **Type-safe** ensures correct tuple length
- **IDE-friendly** with autocomplete support
- **Self-documenting** code
