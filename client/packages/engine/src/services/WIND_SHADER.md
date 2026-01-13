# Wind Shader Documentation

## Overview

The wind shader provides realistic wind animation for blocks in the Nimbus engine. It simulates physical displacement of vegetation (grass, leaves, plants) based on wind parameters and block-specific properties.

## Activation

To enable wind effects for a block, set the `effect` property in the `VisibilityModifier`:

```json
{
  "visibility": {
    "effect": 2,  // BlockEffect.WIND
    "textures": { ... }
  },
  "wind": {
    "leverUp": 4.0,
    "leverDown": 3.0,
    "leafiness": 1.0,
    "stability": 0.0
  }
}
```

## Wind Parameters

### Global Parameters (from EnvironmentService)

These parameters affect all wind-animated blocks in the world:

| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `windDirection` | Vector2 | Any | (1.0, 0.0) | Wind direction in XZ plane (East by default) |
| `windStrength` | float | 0.0 - 2.0 | 0.3 | Base wind strength (30% by default) |
| `windGustStrength` | float | 0.0 - 2.0 | 0.15 | Gust intensity (15% by default) |
| `windSwayFactor` | float | 0.0 - 5.0 | 1.0 | Animation speed multiplier |

**How to modify:**
```typescript
environmentService.setWindDirection(1.0, 0.5); // East-North
environmentService.setWindStrength(0.8);        // Strong wind
environmentService.setWindGustStrength(0.4);    // Strong gusts
environmentService.setWindSwayFactor(2.0);      // Fast animation
```

### Block-Specific Parameters (from WindModifier)

These parameters are set per-block and control individual block behavior:

| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `leafiness` | float | 0.0 - 1.0 | 0.5 | Amplitude of vertical "leaf flutter" movement |
| `stability` | float | 0.0 - 1.0 | 0.5 | Resistance to wind (1.0 = no movement, 0.0 = full movement) |
| `leverUp` | float | 0.0 - 20.0 | 0.0 | Blocks above current block that act as lever extension |
| `leverDown` | float | 0.0 - 20.0 | 0.0 | Blocks below current block that act as pivot point |

**Example configurations:**

```typescript
// Tall grass (flexible, strong movement)
{
  "wind": {
    "leverUp": 2.0,      // Short lever
    "leverDown": 1.0,    // Low pivot
    "leafiness": 0.9,    // High flutter
    "stability": 0.1     // Very flexible
  }
}

// Small tree (moderate movement)
{
  "wind": {
    "leverUp": 6.0,      // Tall lever
    "leverDown": 4.0,    // Higher pivot
    "leafiness": 0.6,    // Moderate flutter
    "stability": 0.4     // Moderate stiffness
  }
}

// Large tree (subtle movement)
{
  "wind": {
    "leverUp": 10.0,     // Very tall lever
    "leverDown": 8.0,    // High pivot
    "leafiness": 0.3,    // Low flutter
    "stability": 0.7     // Stiff trunk
  }
}
```

## Physics Model

The wind shader implements a physically-based animation model:

### 1. Horizontal Shearing (Primary Movement)

Blocks bend horizontally like a lever anchored at the pivot point:

- **Pivot Point**: Determined by `leverDown` (blocks below)
- **Lever Length**: Interpolated between `leverDown` (bottom) and `leverUp` (top)
- **Displacement**: Higher vertices move more (quadratic relationship with height)
- **Direction**: Follows `windDirection` in XZ plane

**Formula:**
```glsl
float h = clamp((position.y - pivotY) / (pivotY + blockHeight + leverUp), 0.0, 1.0);
float leverAtThisHeight = mix(leverDown, leverUp, h);
float horizontalDisp = totalWave * leverAtThisHeight * stabilityFactor * 0.05;
pos += shearDir * horizontalDisp * h;
```

### 2. Vertical Compression (Secondary Movement)

When bent horizontally, the block compresses vertically (physics: bent rod becomes shorter):

**Formula:**
```glsl
float compressionFactor = (horizontalDisp^2) / (2 * heightFromPivot);
pos.y -= compressionFactor;
```

### 3. Vertical Leafiness (Tertiary Movement)

Additional up/down oscillation for organic "leaf flutter" effect:

- Uses separate wave frequencies (2.1x, 1.3x base speed)
- Amplitude controlled by `leafiness` parameter
- Only affects upper vertices (scaled by height factor)
- Small amplitude (0.02 blocks)

**Formula:**
```glsl
float leafiness = max(windLeafiness, 0.5);
float verticalLeafWave = sin(time * swayFactor * 2.1 + worldPos.x * 0.015) * leafiness;
verticalLeafWave += cos(time * swayFactor * 1.3 + worldPos.z * 0.02) * leafiness * 0.5;
pos.y += verticalLeafWave * 0.02 * h;
```

### 4. Wave Composition

Three wave types are combined:

1. **Base Wave**: Smooth sinusoidal sway
2. **Gust Wave**: Faster irregular pulses
3. **Leaf Wave**: Organic secondary movement

**Formula:**
```glsl
float totalWave = baseWave + gustWave * 0.5 + leafWave * 0.3;
```

## Technical Implementation

### Shader Architecture

**Vertex Shader** (`windVertexShader`):
- Input: Standard attributes (position, normal, uv, color) + wind attributes
- Processing: Physical displacement calculations
- Output: Transformed position, passed-through UVs and normals

**Fragment Shader** (`windFragmentShader`):
- Input: Texture sampler, UVs
- Processing: Texture sampling, alpha test
- Output: Textured surface without lighting modifications

### Vertex Attributes

Wind-specific per-vertex attributes must be provided:

| Attribute | Type | Size | Description |
|-----------|------|------|-------------|
| `windLeafiness` | float | 1 component | Leaf flutter intensity |
| `windStability` | float | 1 component | Movement damping factor |
| `windLeverUp` | float | 1 component | Upper lever extension |
| `windLeverDown` | float | 1 component | Lower pivot point |
| `color` | vec4 | 4 components | Vertex color (required, set to white) |

**Important**: All renderers must provide these attributes via `BlockRenderer.addWindAttributesAndColors()`.

### Material Creation

Wind materials are created automatically by `MaterialService` when `effect === BlockEffect.WIND`:

```typescript
// MaterialService.ts
if (props.effect === BlockEffect.WIND) {
  const atlasTexture = this.textureAtlas?.getTexture();
  material = this.shaderService.createMaterial('wind', {
    texture: atlasTexture,
    name: cacheKey,
  });
}
```

### Supported Shapes

Wind effects are supported on all batched mesh shapes:

- ✅ **CUBE** (shape: 1)
- ✅ **CROSS** (shape: 2)
- ✅ **HASH** (shape: 3)
- ✅ **SPHERE** (shape: 5)
- ✅ **CYLINDER** (shape: 9)
- ✅ **STAIR** (shape: 10)
- ✅ **STEPS** (shape: 11)

## Usage in Editor

### Via VisibilityEditor

1. Open block editor
2. In Visibility section, set:
   - **Global Effect**: Select "WIND"
   - **(Optional) Effect Parameters**: Not used for wind
3. In Wind Modifier section (if available), set:
   - **Lever Up**: 0-20 (default: 0)
   - **Lever Down**: 0-20 (default: 0)
   - **Leafiness**: 0-1 (default: 0.5)
   - **Stability**: 0-1 (default: 0.5)

### Via JSON

```json
{
  "modifiers": {
    "0": {
      "visibility": {
        "shape": 9,
        "effect": 2,
        "textures": {
          "0": "textures/block/grass_side.png"
        }
      },
      "wind": {
        "leverUp": 4.0,
        "leverDown": 3.0,
        "leafiness": 1.0,
        "stability": 0.0
      }
    }
  }
}
```

## Performance Considerations

### Batching

Wind-animated blocks are **batched by material** like normal blocks:
- Blocks with same wind effect settings share the same mesh
- Efficient GPU rendering via single draw call per material group
- Wind attributes are stored as vertex data (no per-block overhead)

### Shader Uniforms

Wind parameters are updated **once per frame** for all materials:
- `time`: Updated every frame via `onBeforeRenderObservable`
- `windDirection`, `windStrength`, etc.: Updated when EnvironmentService changes
- No per-block uniform updates (all data is in vertex attributes)

## Troubleshooting

### Block appears black
- **Cause**: Missing `color` vertex attribute or vertex color is (0,0,0,0)
- **Solution**: Ensure renderer calls `addWindAttributesAndColors()`

### No wind movement
- **Cause 1**: Wind parameters too low (windStrength, leverUp/Down)
- **Solution**: Increase wind strength or lever values

- **Cause 2**: Stability too high (blocks "frozen")
- **Solution**: Reduce stability value (closer to 0.0)

- **Cause 3**: Wind attributes not set
- **Solution**: Check that faceData.windLeafiness arrays are initialized (only for effect=WIND materials)

### GL_INVALID_OPERATION: Vertex buffer is not big enough
- **Cause**: Renderer doesn't set wind attributes for all vertices
- **Solution**: Ensure renderer calls `addWindAttributesAndColors()` for every vertex

### Wind moves wrong direction
- **Cause**: windDirection not set or incorrect
- **Solution**: Check EnvironmentService.setWindDirection(x, z)

## Future Improvements

Potential enhancements for the wind shader:

1. **Per-vertex lever values**: Allow different lever lengths per vertex (for curved plants)
2. **Turbulence noise**: Use 3D noise for more organic wind patterns
3. **Wind zones**: Different wind parameters for different world regions
4. **Interaction**: Player movement affects nearby plants
5. **LOD**: Simplified wind calculations for distant blocks
6. **Lighting integration**: Add proper directional lighting back (currently disabled for simplicity)

## Related Files

- **ShaderService.ts** (lines 318-543): Wind shader definition and material creation
- **BlockRenderer.ts** (lines 42-79): Helper method for wind attributes
- **MaterialService.ts** (lines 159, 310-327, 332-340): Material selection and property application
- **RenderService.ts** (lines 275-302, 620-689): FaceData initialization and vertex buffer creation
- **EnvironmentService.ts**: Wind parameter management
- **BlockModifier.ts** (lines 466-482, 520-534): Type definitions

## See Also

- `instructions/shape_rendern.md` - General shape rendering documentation
- `FLIPBOX_SHADER.md` - Similar shader effect documentation (if exists)
- Babylon.js ShaderMaterial documentation: https://doc.babylonjs.com/features/featuresDeepDive/materials/shaders/shaderMaterial
