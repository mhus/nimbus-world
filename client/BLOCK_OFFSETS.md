# Block Offsets Documentation

Block offsets allow precise manipulation of block shapes by defining vertex positions and shape-specific properties. All offset values support floating-point numbers for sub-block precision.

## Overview

Offsets are stored as flat arrays of numbers, where each group of 3 consecutive values typically represents X, Y, Z coordinates. The interpretation depends on the block's shape type.

## Shape-Specific Offset Structures

### INVISIBLE (Shape 0)
- **No offsets**: Invisible blocks do not support offset manipulation.

---

### CUBE, HASH, CROSS (Shapes 1, 2, 3)
- **Total values**: 24 (8 corners × XYZ)
- **Structure**: Each corner is defined by 3 consecutive values (X, Y, Z)

**Corner order**:
```
Index | Corner Description          | Offset Indices
------|----------------------------|---------------
0     | Bottom Front Left (SW)     | 0, 1, 2
1     | Bottom Front Right (SE)    | 3, 4, 5
2     | Bottom Back Left (NW)      | 6, 7, 8
3     | Bottom Back Right (NE)     | 9, 10, 11
4     | Top Front Left (SW)        | 12, 13, 14
5     | Top Front Right (SE)       | 15, 16, 17
6     | Top Back Left (NW)         | 18, 19, 20
7     | Top Back Right (NE)        | 21, 22, 23
```

**Example**:
```javascript
// Create a tapered cube (narrower at top)
offsets = [
  // Bottom corners (full width)
  0, 0, 0,     // Bottom Front Left
  0, 0, 0,     // Bottom Front Right
  0, 0, 0,     // Bottom Back Left
  0, 0, 0,     // Bottom Back Right
  // Top corners (pulled inward by 0.2 on X and Z)
  0.2, 0, 0.2, // Top Front Left
  -0.2, 0, 0.2, // Top Front Right
  0.2, 0, -0.2, // Top Back Left
  -0.2, 0, -0.2, // Top Back Right
]
```

---

### FLIPBOX (Shape 11)
- **Total values**: 24 (same structure as CUBE)
- **Special behavior**: Only the **top 4 corners** (indices 4-7) are used for rendering
- **Corner indices used**: 12-23

---

### FLAT, GLASS_FLAT (Shapes 7, 6)
- **Total values**: 12 (4 corners × XYZ)
- **Structure**: Flat surface with 4 corners

**Corner order**:
```
Index | Corner Description   | Offset Indices
------|---------------------|---------------
0     | Front Left (SW)     | 0, 1, 2
1     | Front Right (SE)    | 3, 4, 5
2     | Back Left (NW)      | 6, 7, 8
3     | Back Right (NE)     | 9, 10, 11
```

**Example**:
```javascript
// Create a sloped surface (higher at back)
offsets = [
  0, 0, 0,     // Front Left (low)
  0, 0, 0,     // Front Right (low)
  0, 0.5, 0,   // Back Left (raised)
  0, 0.5, 0,   // Back Right (raised)
]
```

---

### SPHERE (Shape 8)
- **Total values**: 6 (2 points × XYZ)
- **Structure**:
  - **Point 1 (indices 0-2)**: Radius offset (XYZ)
  - **Point 2 (indices 3-5)**: Displacement (XYZ)

**Interpretation**:
```
Index | Property              | Description
------|-----------------------|----------------------------------
0-2   | Radius Offset (X,Y,Z) | Adjusts sphere radius per axis
3-5   | Displacement (X,Y,Z)  | Moves sphere center position
```

**Example**:
```javascript
// Create an ellipsoid (stretched vertically) moved up
offsets = [
  0, 0.3, 0,   // Radius: stretched Y by 0.3
  0, 0.2, 0,   // Displacement: moved up by 0.2
]
```

---

### CYLINDER (Shape 9)
- **Total values**: 12 (4 points × 3 values)
- **Structure**: Special meaning for each point

**Point structure**:
```
Point | Indices | Property              | Used Axes | Description
------|---------|----------------------|-----------|---------------------------
1     | 0-2     | Radius Top           | X, Z      | Top radius offset (Y unused)
2     | 3-5     | Radius Bottom        | X, Z      | Bottom radius offset (Y unused)
3     | 6-8     | Displacement Top     | X, Y, Z   | Top center position offset
4     | 9-11    | Displacement Bottom  | X, Y, Z   | Bottom center position offset
```

**Example**:
```javascript
// Create a cone (narrow top, wide bottom) tilted to the right
offsets = [
  -0.3, 0, -0.3,  // Top radius: smaller by 0.3
  0.2, 0, 0.2,    // Bottom radius: larger by 0.2
  0.1, 0, 0,      // Top displacement: shifted right
  -0.1, 0, 0,     // Bottom displacement: shifted left
]
```

---

### WATER (Shape 22)
- **Total values**: 28 (8 corners × XYZ + 4 water properties)
- **Structure**:
  - **Indices 0-23**: 8 corners (same as CUBE) - defines water surface
  - **Indices 24-27**: Water properties (color + transparency)

**Water properties**:
```
Index | Property     | Range | Description
------|-------------|-------|--------------------------------
24    | Red         | 0-1   | Water color red component
25    | Green       | 0-1   | Water color green component
26    | Blue        | 0-1   | Water color blue component
27    | Alpha       | 0-1   | Transparency (0=transparent, 1=opaque)
```

**Water presets**:
```javascript
// Clear water (light blue, semi-transparent)
[...corners, 0.7, 0.85, 1.0, 0.3]

// Ocean blue (deep blue, moderate transparency)
[...corners, 0.1, 0.3, 0.8, 0.5]

// Swamp green (murky green, less transparent)
[...corners, 0.2, 0.5, 0.2, 0.6]

// Murky brown (brownish, mostly opaque)
[...corners, 0.4, 0.3, 0.2, 0.7]
```

---

### THIN_INSTANCES (Shape 25)
- **Total values**: 3 (single XYZ offset)
- **Structure**: Simple position offset

```
Index | Property      | Description
------|--------------|---------------------------
0     | X Offset     | Horizontal offset (East-West)
1     | Y Offset     | Vertical offset (Up-Down)
2     | Z Offset     | Horizontal offset (North-South)
```

**Example**:
```javascript
// Move instance 0.5 blocks up and 0.2 blocks east
offsets = [0.2, 0.5, 0]
```

---

### Generic/Unknown Shapes
- **Total values**: 24 (8 points × XYZ)
- **Structure**: Generic numbered points 0-7, each with XYZ coordinates

```
Point | Offset Indices
------|---------------
0     | 0, 1, 2
1     | 3, 4, 5
2     | 6, 7, 8
3     | 9, 10, 11
4     | 12, 13, 14
5     | 15, 16, 17
6     | 18, 19, 20
7     | 21, 22, 23
```

---

## Coordinate System

The Nimbus world uses the following coordinate system:

- **X-axis**: East (+) / West (-)
- **Y-axis**: Up (+) / Down (-)
- **Z-axis**: South (+) / North (-)

**Cardinal directions**:
- **SW (Southwest)**: Low X, Low Z
- **SE (Southeast)**: High X, Low Z
- **NW (Northwest)**: Low X, High Z
- **NE (Northeast)**: High X, High Z

---

## Offset Normalization

The offset editor automatically normalizes offsets:

1. **Null/undefined conversion**: All `null` or `undefined` values are converted to `0`
2. **Trailing zero trimming**: Trailing zeros are removed from the array
3. **Empty array handling**: If all values are zero, the offsets array becomes `undefined`

This ensures minimal data storage and clean serialization.

---

## Usage in Code

```typescript
// Example: Create a tapered pillar
const blockType = {
  shape: 1, // CUBE
  offsets: [
    0, 0, 0,      // Bottom corners
    0, 0, 0,
    0, 0, 0,
    0, 0, 0,
    0.2, 0, 0.2,  // Top corners (pulled inward)
    -0.2, 0, 0.2,
    0.2, 0, -0.2,
    -0.2, 0, -0.2,
  ]
};

// Example: Create colored water block
const waterBlock = {
  shape: 22, // WATER
  offsets: [
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // Bottom 4 corners
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // Top 4 corners
    0.1, 0.3, 0.8, 0.5 // Ocean blue color with 50% transparency
  ]
};
```

---

## Implementation Reference

The offset editor is implemented in:
- **Editor UI**: `packages/controls/src/editors/OffsetsEditor.vue`
- **Type definitions**: See BlockType interfaces in `@nimbus/shared`

For more details on block shapes and rendering, see the BlockType documentation.
