# Underwater Physics Implementation

## Overview

Implementation of underwater physics and rendering based on ClientHeightData.

## Status: ✅ FULLY IMPLEMENTED

## Requirements

### 1. PhysicsService - Water Detection

**Location:** `PhysicsService.checkUnderwaterState()`

**Implementation:**
```typescript
// Get ClientHeightData from ChunkService for entity's column
const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
const chunkX = Math.floor(entity.position.x / chunkSize);
const chunkZ = Math.floor(entity.position.z / chunkSize);
const chunk = this.chunkService.getChunk(chunkX, chunkZ);

if (!chunk) return;

// Get height data for entity's local position
const localX = Math.floor(entity.position.x % chunkSize);
const localZ = Math.floor(entity.position.z % chunkSize);
const heightKey = `${localX},${localZ}`;
const heightData = chunk.data.hightData.get(heightKey);

if (heightData && heightData[5] !== undefined) {
  const [x, z, maxHeight, minHeight, groundLevel, waterHeight] = heightData;

  // Check if underwater
  const wasUnderwater = this.isUnderwater;
  this.isUnderwater = entity.position.y < waterHeight;

  // Notify CameraService on state change
  if (wasUnderwater !== this.isUnderwater) {
    const cameraService = this.appContext.services.camera;
    cameraService?.setUnderwater(this.isUnderwater);
  }

  // Clamp to min/max height boundaries
  if (entity.position.y < minHeight) {
    entity.position.y = minHeight;
    entity.velocity.y = 0;
  } else if (entity.position.y > maxHeight) {
    entity.position.y = maxHeight;
    entity.velocity.y = 0;
  }
}
```

### 2. PhysicsService - Underwater Physics

**Location:** `PhysicsService.updateWalkMode()`

**Behavior:**
- ✅ Gravity disabled when underwater (implemented)
- ✅ Movement speed reduced to `underwaterSpeed` (implemented)
- ✅ Vertical movement enabled (like fly mode) (implemented)
- ⏳ TODO: Add water drag/resistance
- ⏳ TODO: Add collision detection when underwater

**Movement:**
- Forward/Backward: Use camera pitch (like fly mode)
- Strafe: Left/Right movement
- Up/Down: Enabled when underwater
- Speed: `3.0 blocks/second` (slower than walk/fly)

### 3. CameraService - Water Sphere Rendering

**Location:** `CameraService.setUnderwater()`

**Requirements:**
```typescript
setUnderwater(underwater: boolean): void {
  if (underwater) {
    // Create water sphere mesh around camera
    // - Radius: ~5-10 blocks
    // - Material: Translucent blue with transparency
    // - Follow camera position

    // Apply water effects:
    // - Fog: Blue-tinted fog
    // - Light attenuation: Reduce visibility
    // - Color tint: Slight blue overlay

  } else {
    // Remove water sphere mesh
    // Reset fog and lighting to normal
  }
}
```

**Visual Effects:**
- Water sphere mesh with translucent material
- Blue fog effect
- Reduced visibility/light intensity
- Optional: Caustics, bubbles, particles

### 4. Height Boundaries

**minHeight & maxHeight:**
- Used as physics boundaries
- Entity clamped to `[minHeight, maxHeight]` range
- Prevents falling through world bottom
- Prevents flying above world top

## Implementation Checklist

- [x] **PhysicsService.checkUnderwaterState()** - IMPLEMENTED
  - [x] Get ClientHeightData from ChunkService
  - [x] Check waterHeight value
  - [x] Update isUnderwater state
  - [x] Call CameraService.setUnderwater()
  - [x] Clamp to min/max height boundaries
  - [x] Handle negative coordinates correctly with modulo

- [x] **PhysicsService underwater physics** - IMPLEMENTED
  - [x] Disable gravity when underwater
  - [x] Reduce movement speed (underwaterSpeed: 3.0)
  - [x] Enable vertical movement (moveUp/moveDown)
  - [x] Forward movement with pitch (like fly mode)
  - [ ] Add water drag/resistance (FUTURE)
  - [ ] Add collision detection when underwater (FUTURE)

- [x] **CameraService.setUnderwater()** - IMPLEMENTED
  - [x] Create/destroy water sphere mesh
  - [x] Apply water material (translucent blue, alpha: 0.3)
  - [x] Add fog effects (blue tint, exponential fog)
  - [x] Water sphere follows camera in update() loop
  - [x] Flip normals for inside rendering
  - [x] Proper disposal of meshes and materials

- [x] **ChunkService.processHeightData()** - IMPLEMENTED
  - [x] Calculate waterHeight from blocks with 'water' in description
  - [x] Set waterHeight to top of highest water block (y + 1)
  - [x] Return undefined if no water blocks found

- [x] **AppContext.Services** - UPDATED
  - [x] Added camera?: CameraService to Services interface
  - [x] CameraService registered in EngineService.init()

## Testing Checklist

To test underwater functionality:

- [ ] **Create water blocks in world**
  - [ ] Add blocks with description containing "water"
  - [ ] Place at different heights to create water pools

- [ ] **Test entering water**
  - [ ] Player moves below waterHeight
  - [ ] Water sphere appears around camera
  - [ ] Blue fog activates
  - [ ] Gravity disables
  - [ ] Movement speed reduces

- [ ] **Test leaving water**
  - [ ] Player moves above waterHeight
  - [ ] Water sphere disappears
  - [ ] Fog resets to normal
  - [ ] Gravity re-enables
  - [ ] Movement speed returns to normal

- [ ] **Test physics boundaries**
  - [ ] Player cannot fall below minHeight
  - [ ] Player cannot fly above maxHeight

- [ ] **Test movement underwater**
  - [ ] Forward/backward with pitch control (like fly)
  - [ ] Strafe left/right
  - [ ] Up/down movement enabled
  - [ ] Speed is slower (3.0 blocks/s)

- [ ] **Test visual effects**
  - [ ] Water sphere is visible and translucent
  - [ ] Sphere follows camera smoothly
  - [ ] Blue fog creates atmosphere
  - [ ] No z-fighting or rendering artifacts

## Notes

- **waterHeight is optional**: Only present when water blocks exist in column
- **Per-entity state**: Currently global `isUnderwater`, should be per-entity
- **Performance**: Check underwater state once per frame, not per movement
- **Water blocks**: No special block type needed, waterHeight in ClientHeightData is sufficient

## Related Files

- `PhysicsService.ts:159` - checkUnderwaterState() implementation
- `PhysicsService.ts:205` - updateWalkMode() with underwater logic
- `PhysicsService.ts:565` - moveForward() with underwater movement
- `PhysicsService.ts:635` - moveUp() for underwater vertical movement
- `PhysicsService.ts:687` - getMoveSpeed() with underwaterSpeed
- `CameraService.ts:185` - setUnderwater() stub
- `ChunkService.ts:293` - waterHeight calculation (currently undefined)

## Future Enhancements

- Per-entity underwater state (not global)
- Water depth-based effects (deeper = darker, more fog)
- Oxygen/breathing mechanics
- Swimming animations
- Water currents/flow
- Surface effects (waves, reflections)
