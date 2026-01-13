# Underwater Debug Logging Guide

## Overview

Extensive logging has been added to debug underwater functionality. All water-related logs are prefixed with `💧` for easy filtering.

## Performance Optimization

**Important:** Underwater checks are now optimized to only run when entity **block coordinates** change!

- ✅ Block coordinate tracking (Math.floor(x), Math.floor(y), Math.floor(z))
- ✅ Chunk lookups only when entity enters new block
- ✅ Last checked block coords stored per entity
- ✅ Cleanup on entity unregister

**Why block coordinates instead of position?**
- waterHeight is per block column (x, z) - integer coordinates
- No need to check within same block (position 10.1 vs 10.9 = same block!)
- More checks than epsilon-position, but still optimized

**Frequency:**
- Walking 1 block/s → ~1 check/second
- Walking 5 blocks/s → ~5 checks/second
- Standing still → 0 checks/second ✅
- Within same block → 0 checks/second ✅

## Log Locations

### 1. ChunkService - Water Height Calculation

**When:** Chunk is loaded and processed

**Log Messages:**

```
💧 Processing server-provided height data
- Shows: chunk position, number of height entries from server

💧 Server height data entry (DEBUG)
- Shows: local position, maxHeight, groundLevel (waterHeight not yet calculated)

💧 WATER BLOCK FOUND (DEBUG)
- Shows: chunk position, local position, blockTypeId, blockY, description
- Appears for each water block found (description contains "water")

💧 WATER HEIGHT CALCULATED (INFO)
- Shows: chunk position, local position, water block count, maxWaterY, calculated waterHeight

💧 HEIGHT DATA PROCESSING COMPLETE (INFO)
- Shows: total columns, columns with water, all water heights found
```

**Location:** `ChunkService.ts:253-368`

### 2. PhysicsService - Underwater Detection

**When:** Only when entity position changes (optimized!)

**Log Messages:**

```
💧 WATER CHECK: No chunk loaded (DEBUG)
- Shows: entity position, chunk coordinates (chunk not loaded yet)

💧 WATER CHECK (DEBUG)
- Shows when position changed:
  - Entity ID
  - Chunk position (cx, cz)
  - Local position (x, z) within chunk
  - Height key used for lookup
  - Full height data (x, z, maxHeight, minHeight, groundLevel, waterHeight)
  - Current entity Y position
  - Current underwater state

💧 WATER HEIGHT FOUND (INFO on state change, DEBUG otherwise)
- Shows when waterHeight exists:
  - waterHeight value
  - entityY position
  - isUnderwater (true/false)
  - wasUnderwater (previous state)
  - stateChanged (true if state changed)

💧 UNDERWATER STATE CHANGED → CameraService notified (INFO)
- Shows when entering/leaving water:
  - Entity ID
  - New underwater state
  - Water height
  - Entity Y position

💧 CameraService not available! (WARN)
- Shows if CameraService is not registered in AppContext

💧 NO WATER HEIGHT DATA (INFO)
- Shows when no waterHeight data available:
  - Whether heightData exists at all
  - Value of waterHeight field
  - Current entity Y
```

**Location:** `PhysicsService.ts:166-245`

### 3. CameraService - Visual Effects

**When:** Underwater state changes

**Log Messages:**

```
💧 ENABLING UNDERWATER EFFECTS (INFO)
- Shows when entering water

💧 Water sphere created (INFO)
- Shows when sphere mesh is created first time:
  - Position
  - Diameter (8 blocks)
  - Material color and alpha

💧 Water sphere visible: true (INFO)
- Confirms sphere is made visible

💧 Underwater effects enabled (INFO)
- Shows final state:
  - Fog mode
  - Fog density
  - Fog color (RGB)
  - Sphere visibility

💧 DISABLING UNDERWATER EFFECTS (INFO)
- Shows when leaving water

💧 Water sphere hidden (INFO)
- Confirms sphere is hidden

💧 Underwater effects disabled (INFO)
- Shows fog restored to normal
```

**Location:** `CameraService.ts:230-321`

## How to Debug

### Step 1: Check if Water Blocks are Found

Look for logs during chunk loading:

```
💧 WATER BLOCK FOUND
  chunkPos: { cx: 0, cz: 0 }
  localPos: { x: 5, z: 7 }
  blockTypeId: 123
  blockY: 64
  description: "water block"
```

**If you DON'T see this:**
- ❌ No water blocks in chunks
- ❌ BlockType description doesn't contain "water"
- ❌ BlockTypeService not available

**Solution:** Add blocks with `description: "water"` or similar

### Step 2: Check if Water Height is Calculated

Look for:

```
💧 WATER HEIGHT CALCULATED
  chunkPos: { cx: 0, cz: 0 }
  localPos: { x: 5, z: 7 }
  waterBlockCount: 3
  maxWaterY: 64
  waterHeight: 65
```

**If you DON'T see this:**
- ❌ No water blocks matched the filter

### Step 3: Check Summary

```
💧 HEIGHT DATA PROCESSING COMPLETE
  chunkPos: { cx: 0, cz: 0 }
  totalColumns: 256  (16x16 chunk)
  columnsWithWater: 5
  waterHeights: [
    { localPos: { x: 5, z: 7 }, waterHeight: 65 },
    ...
  ]
```

**Expected:**
- `columnsWithWater > 0` if water exists in chunk

### Step 4: Check Water Detection (When Position Changes)

```
💧 WATER CHECK
  entityId: "player"
  chunkPos: { cx: 0, cz: 0 }
  localPos: { x: 5, z: 7 }
  heightKey: "5,7"
  hasHeightData: true
  heightData: {
    x: 5,
    z: 7,
    maxHeight: 128,
    minHeight: 0,
    groundLevel: 64,
    waterHeight: 65
  }
  entityY: 63.5
  isUnderwater: false
```

**Key values:**
- `waterHeight: 65` - Water surface at Y=65
- `entityY: 63.5` - Player at Y=63.5
- `isUnderwater: false` - Player is below water (63.5 < 65 would be true!)

**If waterHeight is undefined:**
```
💧 NO WATER HEIGHT DATA
  hasHeightData: true
  waterHeightValue: undefined
  entityY: 64
```

### Step 5: Check State Change

```
💧 WATER HEIGHT FOUND
  waterHeight: 65
  entityY: 64.8
  isUnderwater: true
  wasUnderwater: false
  stateChanged: true

💧 UNDERWATER STATE CHANGED → CameraService notified
  entityId: "player"
  underwater: true
  waterHeight: 65
  entityY: 64.8
```

**Then in CameraService:**

```
💧 ENABLING UNDERWATER EFFECTS

💧 Water sphere created
  position: { x: 10, y: 64.8, z: 10 }
  diameter: 8
  material: {
    color: { r: 0.1, g: 0.3, b: 0.6 }
    alpha: 0.3
  }

💧 Water sphere visible: true

💧 Underwater effects enabled
  fogMode: 3  (EXP2)
  fogDensity: 0.05
  fogColor: { r: 0.1, g: 0.4, b: 0.7 }
  sphereVisible: true
```

## Troubleshooting

### Problem: No water height data

**Check:**
1. Are there blocks with "water" in description?
2. Is BlockTypeService loaded?
3. Are chunks being processed?

**Logs to look for:**
- `💧 WATER BLOCK FOUND` - Should appear if water blocks exist
- `💧 WATER HEIGHT CALCULATED` - Should appear after finding water

### Problem: Underwater state not changing

**Check:**
1. Is player actually below waterHeight?
2. Is chunk loaded where player is standing?

**Logs to look for:**
- `💧 WATER CHECK` - Shows entityY vs waterHeight comparison
- `💧 WATER HEIGHT FOUND` - Shows `isUnderwater` calculation

### Problem: Water sphere not visible

**Check:**
1. Is CameraService registered in AppContext?
2. Did `setUnderwater(true)` get called?

**Logs to look for:**
- `💧 CameraService not available!` - Service not registered
- `💧 ENABLING UNDERWATER EFFECTS` - Should appear when entering water
- `💧 Water sphere visible: true` - Confirms visibility

## Log Filtering

To see only water-related logs:

```javascript
// In browser console (F12 → Console)
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Enable detailed logging for water-related services
LoggerFactory.setLoggerLevel('ChunkService', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('PhysicsService', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('CameraService', LogLevel.DEBUG);
```

**Or filter console output:**
- In Chrome DevTools: Filter by `💧`
- This shows only water-related logs

**Log Levels:**
- **INFO**: Important events (water found, state changes, effects enabled)
- **DEBUG**: Detailed checks (only with DEBUG level enabled)

**Default behavior (INFO level):**
- You'll see: Water found in chunks, state changes, effects
- You won't see: Per-frame checks (optimized anyway!)

**With DEBUG level:**
- You'll see everything, including each position check

## Expected Log Flow

**When entering water:**

1. Chunk loads → ChunkService processes blocks
   ```
   💧 WATER BLOCK FOUND (for each water block)
   💧 WATER HEIGHT CALCULATED
   💧 HEIGHT DATA PROCESSING COMPLETE
   ```

2. Player moves → PhysicsService detects position change
   ```
   💧 WATER CHECK (only when position changes - optimized!)
   ```

3. Player crosses waterHeight threshold
   ```
   💧 WATER HEIGHT FOUND (isUnderwater changes to true)
   💧 UNDERWATER STATE CHANGED
   ```

4. CameraService activates effects
   ```
   💧 ENABLING UNDERWATER EFFECTS
   💧 Water sphere created (first time only)
   💧 Water sphere visible: true
   💧 Underwater effects enabled
   ```

**When leaving water:**

Same flow but `isUnderwater: false` and:
```
💧 DISABLING UNDERWATER EFFECTS
💧 Water sphere hidden
💧 Underwater effects disabled
```

## Performance Notes

### Optimization: Block Coordinate Change Detection

**Implementation:** `PhysicsService.checkUnderwaterStateIfMoved()` (Line 164-194)

The underwater check is **expensive** (chunk lookups, heightData access), so it only runs when:
- **Block X coordinate changes:** `Math.floor(x)` is different
- **Block Y coordinate changes:** `Math.floor(y)` is different
- **Block Z coordinate changes:** `Math.floor(z)` is different
- OR first time (no lastCheckedBlockCoords exists)

**Why this approach?**
- waterHeight is defined per block column (integer x, z)
- Moving within same block (10.1 → 10.9) doesn't need re-check
- Moving to new block (10.9 → 11.1) triggers check

**Benefits:**
- ✅ No chunk lookups when standing still
- ✅ No checks when moving within same block
- ✅ Checks happen when entering new block (where waterHeight might differ)
- ✅ 60 FPS smooth even with many entities

**Memory:**
- Stores `lastCheckedBlockCoords` per entity (3 integers = 12 bytes)
- Cleaned up on entity unregister

**Check frequency examples:**
- Standing still → 0 checks/second ✅
- Moving within block (x: 10.1 → 10.8) → 0 checks ✅
- Walking 1 block/s → ~1 check/second
- Walking 5 blocks/s → ~5 checks/second
- Jumping (Y changes) → Check on each block height

**When you'll see logs:**
- ✅ When moving to new block
- ✅ When entering water (state change → INFO)
- ✅ When leaving water (state change → INFO)
- ❌ NOT when standing still
- ❌ NOT when moving within same block

### Log Frequency

**At INFO level (default):**
- Chunk load with water: 1 log per chunk
- State change: 2-3 logs (state change + camera notification)
- **Total when standing still: 0 logs/second** ✅
- **Total when moving in same block: 0 logs/second** ✅

**At DEBUG level:**
- Block coordinate change: 1 log + underwater check logs
- Still optimized: Only when block changes!
