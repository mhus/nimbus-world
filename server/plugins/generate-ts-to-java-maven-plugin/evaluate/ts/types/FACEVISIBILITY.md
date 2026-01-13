# FaceVisibility Bitfield System

Efficient face visibility management using bitfield operations (1 byte storage).

## Bitfield Structure

```
Bit:  6     5     4     3     2     1     0
     FIXED BACK FRONT RIGHT LEFT BOTTOM TOP
```

- **Bits 0-5**: Face visibility flags (6 faces)
- **Bit 6**: Fixed/Auto mode flag

## Quick Start

```typescript
import { FaceVisibility, FaceFlag, FaceVisibilityHelper } from '@nimbus/shared';

// Create with all faces visible
const fv = FaceVisibilityHelper.createAllVisible();

// Check if top is visible
if (FaceVisibilityHelper.isVisible(fv, FaceFlag.TOP)) {
  console.log('Top is visible');
}

// Hide the bottom face
FaceVisibilityHelper.setInvisible(fv, FaceFlag.BOTTOM);
```

## FaceFlag Enum

```typescript
enum FaceFlag {
  TOP    = 1,   // 0b00000001
  BOTTOM = 2,   // 0b00000010
  LEFT   = 4,   // 0b00000100
  RIGHT  = 8,   // 0b00001000
  FRONT  = 16,  // 0b00010000
  BACK   = 32,  // 0b00100000
  FIXED  = 64,  // 0b01000000
}
```

## Helper Functions

### Creation

```typescript
// Empty (all invisible)
const fv1 = FaceVisibilityHelper.create();
// { value: 0 }

// All visible
const fv2 = FaceVisibilityHelper.createAllVisible();
// { value: 63 } = 0b00111111

// From face names
const fv3 = FaceVisibilityHelper.fromFaces(['top', 'bottom'], true);
// { value: 67 } = 0b01000011 (top + bottom + fixed)
```

### Checking Visibility

```typescript
const fv = FaceVisibilityHelper.createAllVisible();

// Check single face
FaceVisibilityHelper.isVisible(fv, FaceFlag.TOP);     // true
FaceVisibilityHelper.isVisible(fv, FaceFlag.BOTTOM);  // true

// Check mode
FaceVisibilityHelper.isFixed(fv);  // false (auto mode)

// Get all visible faces
FaceVisibilityHelper.getVisibleFaces(fv);
// ['top', 'bottom', 'left', 'right', 'front', 'back']

// Count visible faces
FaceVisibilityHelper.countVisible(fv);  // 6
```

### Modifying Visibility

```typescript
const fv = FaceVisibilityHelper.create();

// Set faces visible
FaceVisibilityHelper.setVisible(fv, FaceFlag.TOP);
FaceVisibilityHelper.setVisible(fv, FaceFlag.FRONT);

// Set faces invisible
FaceVisibilityHelper.setInvisible(fv, FaceFlag.BOTTOM);

// Toggle face
FaceVisibilityHelper.toggle(fv, FaceFlag.LEFT);
```

### Fixed vs Auto Mode

```typescript
const fv = FaceVisibilityHelper.createAllVisible();

// Enable fixed mode (manual control)
FaceVisibilityHelper.setFixed(fv);
FaceVisibilityHelper.isFixed(fv);  // true

// Enable auto mode (culling calculation)
FaceVisibilityHelper.setAuto(fv);
FaceVisibilityHelper.isFixed(fv);  // false
```

### Utility Functions

```typescript
const fv = FaceVisibilityHelper.createAllVisible();

// Clone
const copy = FaceVisibilityHelper.clone(fv);

// Debug string
FaceVisibilityHelper.toString(fv);
// "FaceVisibility(top,bottom,left,right,front,back, auto)"
```

## Usage Examples

### 1. Block Face Culling

```typescript
// Calculate which faces are visible based on neighbors
function calculateFaceVisibility(
  block: Block,
  neighbors: Block[]
): FaceVisibility {
  const fv = FaceVisibilityHelper.create();

  // Check each direction
  if (!neighbors[0] || !neighbors[0].isSolid) {
    FaceVisibilityHelper.setVisible(fv, FaceFlag.TOP);
  }
  if (!neighbors[1] || !neighbors[1].isSolid) {
    FaceVisibilityHelper.setVisible(fv, FaceFlag.BOTTOM);
  }
  // ... check other directions

  // Mark as auto-calculated
  FaceVisibilityHelper.setAuto(fv);

  return fv;
}
```

### 2. Manual Face Control

```typescript
// Block editor: user manually sets which faces to show
function setBlockFaces(block: Block, faces: string[]) {
  const fv = FaceVisibilityHelper.fromFaces(faces, true);
  block.faceVisibility = fv;
}

// Usage
setBlockFaces(block, ['top', 'front']);
// Only top and front faces will be rendered
```

### 3. Glass Block Rendering

```typescript
// Glass blocks show all faces
function createGlassBlock(): Block {
  return {
    position: { x: 0, y: 0, z: 0 },
    blockTypeId: GLASS_ID,
    faceVisibility: FaceVisibilityHelper.createAllVisible(),
  };
}
```

### 4. Partial Block (Slab)

```typescript
// Bottom slab: hide top face
function createBottomSlab(): Block {
  const fv = FaceVisibilityHelper.createAllVisible();
  FaceVisibilityHelper.setInvisible(fv, FaceFlag.TOP);
  FaceVisibilityHelper.setFixed(fv);  // Don't recalculate

  return {
    position: { x: 0, y: 0, z: 0 },
    blockTypeId: SLAB_ID,
    faceVisibility: fv,
  };
}
```

### 5. Rendering Optimization

```typescript
function renderBlock(block: Block) {
  const fv = block.faceVisibility;

  if (!fv) {
    // No visibility data, render all faces
    renderAllFaces(block);
    return;
  }

  // Render only visible faces
  if (FaceVisibilityHelper.isVisible(fv, FaceFlag.TOP)) {
    renderTopFace(block);
  }
  if (FaceVisibilityHelper.isVisible(fv, FaceFlag.BOTTOM)) {
    renderBottomFace(block);
  }
  // ... render other visible faces
}
```

### 6. Network Optimization

```typescript
// Only send faceVisibility if not default (auto mode, all faces)
function serializeBlock(block: Block): any {
  const data: any = {
    position: block.position,
    blockTypeId: block.blockTypeId,
  };

  if (block.faceVisibility) {
    const fv = block.faceVisibility;
    // Only include if fixed mode or non-standard visibility
    if (
      FaceVisibilityHelper.isFixed(fv) ||
      FaceVisibilityHelper.countVisible(fv) !== 6
    ) {
      data.faceVisibility = fv.value;  // 1 byte!
    }
  }

  return data;
}
```

### 7. Debug Visualization

```typescript
// Show which faces are visible in debug UI
function debugBlockFaces(block: Block) {
  if (!block.faceVisibility) {
    console.log('All faces visible (auto)');
    return;
  }

  const fv = block.faceVisibility;
  const faces = FaceVisibilityHelper.getVisibleFaces(fv);
  const mode = FaceVisibilityHelper.isFixed(fv) ? 'fixed' : 'auto';

  console.log(`Visible faces (${mode}):`, faces.join(', '));
  console.log(`Count: ${FaceVisibilityHelper.countVisible(fv)}/6`);
  console.log(`Binary: 0b${fv.value.toString(2).padStart(8, '0')}`);
}

// Output:
// Visible faces (fixed): top, front, right
// Count: 3/6
// Binary: 0b01010001
```

## Bitwise Operations

### Manual Bitfield Operations (if needed)

```typescript
const fv: FaceVisibility = { value: 0 };

// Set multiple faces at once
fv.value = FaceFlag.TOP | FaceFlag.BOTTOM | FaceFlag.LEFT;
// value = 7 = 0b00000111

// Check if any face is visible
const hasVisibleFaces = (fv.value & 0b00111111) !== 0;

// Check if all faces are visible
const allVisible = (fv.value & 0b00111111) === 0b00111111;

// Combine with another FaceVisibility
const fv2: FaceVisibility = { value: FaceFlag.RIGHT | FaceFlag.FRONT };
fv.value |= fv2.value;  // Union
```

## Performance Considerations

### Memory
- **1 byte** per block (instead of 7 booleans = 7 bytes)
- **85% memory savings** for face visibility data
- Efficient network transmission

### Speed
- **O(1)** for all operations (bit operations)
- No array allocations
- Fast comparison: `fv.value === other.value`

### Cache Efficiency
- Small size fits in CPU cache
- Bitwise ops are CPU-native

## Common Patterns

### All Faces Visible
```typescript
{ value: 63 }  // 0b00111111
```

### No Faces Visible
```typescript
{ value: 0 }   // 0b00000000
```

### Only Top/Bottom (Column)
```typescript
{ value: 3 }   // 0b00000011
```

### Fixed Mode, All Visible
```typescript
{ value: 127 } // 0b01111111
```

### Custom Pattern
```typescript
// Top + Front + Right (fixed)
{ value: 89 }  // 0b01011001
```

## Migration from Boolean Flags

### Before (7 bytes)
```typescript
interface OldFaceVisibility {
  top: boolean;
  bottom: boolean;
  left: boolean;
  right: boolean;
  front: boolean;
  back: boolean;
  fixed: boolean;
}
```

### After (1 byte)
```typescript
interface FaceVisibility {
  value: number;  // 1 byte bitfield
}
```

### Conversion
```typescript
function convertOldToNew(old: OldFaceVisibility): FaceVisibility {
  const fv = FaceVisibilityHelper.create();

  if (old.top) FaceVisibilityHelper.setVisible(fv, FaceFlag.TOP);
  if (old.bottom) FaceVisibilityHelper.setVisible(fv, FaceFlag.BOTTOM);
  if (old.left) FaceVisibilityHelper.setVisible(fv, FaceFlag.LEFT);
  if (old.right) FaceVisibilityHelper.setVisible(fv, FaceFlag.RIGHT);
  if (old.front) FaceVisibilityHelper.setVisible(fv, FaceFlag.FRONT);
  if (old.back) FaceVisibilityHelper.setVisible(fv, FaceFlag.BACK);
  if (old.fixed) FaceVisibilityHelper.setFixed(fv);

  return fv;
}
```

## Best Practices

### ✅ DO
- Use helpers instead of manual bitwise operations
- Check `isFixed()` before recalculating visibility
- Use `countVisible()` for optimization decisions
- Clone before modifying if original is needed

### ❌ DON'T
- Don't mutate shared FaceVisibility objects
- Don't use magic numbers (`fv.value = 42`)
- Don't forget to set fixed mode for manual control
- Don't send default visibility over network

## Summary

- **Efficient**: 1 byte for 7 boolean flags
- **Fast**: O(1) bitwise operations
- **Type-Safe**: Helper functions prevent errors
- **Debuggable**: toString() for inspection
- **Network-Friendly**: Minimal data transfer
