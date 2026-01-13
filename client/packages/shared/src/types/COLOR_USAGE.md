# Color System Usage

Colors are stored as hex strings, with ColorRGBA class for component conversion.

## Philosophy

- **Storage**: Hex strings (`'#RRGGBB'`) - simple, compact, human-readable
- **Processing**: ColorRGBA class - when you need R,G,B,A components
- **Network**: Hex strings - minimal data transfer

## ColorHex Type

```typescript
type ColorHex = string;  // '#RRGGBB' or '#RRGGBBAA'
```

Supported formats:
- `'#RRGGBB'` - 6 chars (e.g., `'#ff5500'`)
- `'#RRGGBBAA'` - 8 chars with alpha (e.g., `'#ff5500cc'`)
- `'#RGB'` - 3 chars shorthand (e.g., `'#f50'`)
- `'#RGBA'` - 4 chars shorthand (e.g., `'#f50c'`)

## ColorRGBA Class

```typescript
class ColorRGBA {
  readonly r: number;  // 0-255
  readonly g: number;  // 0-255
  readonly b: number;  // 0-255
  readonly a: number;  // 0-1
}
```

## Usage Examples

### 1. Hex to RGBA

```typescript
import { ColorRGBA } from '@nimbus/shared';

// From hex string
const color = ColorRGBA.fromHex('#ff5500');
console.log(color.r);  // 255
console.log(color.g);  // 85
console.log(color.b);  // 0
console.log(color.a);  // 1

// With alpha
const colorAlpha = ColorRGBA.fromHex('#ff5500cc');
console.log(colorAlpha.a);  // 0.8 (204/255)

// Shorthand
const short = ColorRGBA.fromHex('#f50');
// Same as #ff5500
```

### 2. RGBA to Hex

```typescript
// Create from components
const color = new ColorRGBA(255, 85, 0, 1);

// Convert to hex
const hex = color.toHex();
console.log(hex);  // '#ff5500'

// Include alpha
const hexAlpha = color.toHex(true);
console.log(hexAlpha);  // '#ff5500ff'
```

### 3. From RGB (no alpha)

```typescript
const color = ColorRGBA.fromRGB(255, 85, 0);
// Same as new ColorRGBA(255, 85, 0, 1)

const hex = color.toHex();  // '#ff5500'
```

### 4. From Normalized (0-1)

```typescript
// Babylon.js often uses 0-1 range
const color = ColorRGBA.fromNormalized(1.0, 0.33, 0.0, 1.0);
// Converts to (255, 84, 0, 1)

const hex = color.toHex();  // '#ff5400'
```

### 5. To Normalized (for rendering)

```typescript
const color = ColorRGBA.fromHex('#ff5500');

const normalized = color.toNormalized();
console.log(normalized);
// { r: 1.0, g: 0.333, b: 0.0, a: 1.0 }

// Use in Babylon.js
mesh.material.diffuseColor = new BABYLON.Color3(
  normalized.r,
  normalized.g,
  normalized.b
);
```

### 6. Color Operations

```typescript
const orange = ColorRGBA.fromHex('#ff5500');

// Multiply (brighten/darken)
const bright = orange.multiply(1.5);
const dark = orange.multiply(0.5);

// Add colors
const red = ColorRGBA.RED;
const yellow = orange.add(red);

// Interpolate
const midColor = orange.lerp(ColorRGBA.BLUE, 0.5);
const hex = midColor.toHex();  // Halfway between orange and blue

// Grayscale
const gray = orange.toGrayscale();

// Invert
const inverted = orange.invert();
```

### 7. Parse Multiple Formats

```typescript
import { ColorUtils } from '@nimbus/shared';

// Parse hex
const color1 = ColorUtils.parseColor('#ff5500');

// Parse rgb()
const color2 = ColorUtils.parseColor('rgb(255, 85, 0)');

// Parse rgba()
const color3 = ColorUtils.parseColor('rgba(255, 85, 0, 0.8)');

// Parse named color
const color4 = ColorUtils.parseColor('red');

// All return ColorRGBA or null
```

### 8. Color Conversion

```typescript
import { ColorUtils } from '@nimbus/shared';

const color = '#ff5500';

// Convert to different formats
ColorUtils.convert(color, 'hex');   // '#ff5500'
ColorUtils.convert(color, 'hexa');  // '#ff5500ff'
ColorUtils.convert(color, 'rgb');   // 'rgb(255, 85, 0)'
ColorUtils.convert(color, 'rgba');  // 'rgba(255, 85, 0, 1)'
```

### 9. Color Manipulation

```typescript
import { ColorUtils } from '@nimbus/shared';

const base = '#ff5500';

// Lighten by 20%
const lighter = ColorUtils.lighten(base, 0.2);
// Closer to white

// Darken by 30%
const darker = ColorUtils.darken(base, 0.3);
// Closer to black

// Set alpha
const transparent = ColorUtils.setAlpha(base, 0.5);
// '#ff550080'

// Interpolate
const blended = ColorUtils.lerp('#ff0000', '#0000ff', 0.5);
// Halfway between red and blue
```

### 10. Block Texture Tinting

```typescript
// Block modifier with color tint
const modifier: BlockModifier = {
  visibility: {
    shape: Shape.CUBE,
    textures: {
      [TextureKey.ALL]: {
        path: '/grass.png',
        color: '#88ff88'  // ✓ Stored as hex string
      }
    }
  }
};

// Apply tint in renderer
function applyTextureTint(texture: TextureDefinition) {
  if (texture.color) {
    const tint = ColorRGBA.fromHex(texture.color);
    const normalized = tint.toNormalized();

    // Apply to Babylon.js material
    material.diffuseColor = new BABYLON.Color3(
      normalized.r,
      normalized.g,
      normalized.b
    );
  }
}
```

### 11. Illumination Color

```typescript
// Block illumination
const modifier: BlockModifier = {
  illumination: {
    color: '#ffaa00',  // ✓ Stored as hex string
    strength: 0.8
  }
};

// Use in light source
function createLight(illumination: IlluminationModifier) {
  const color = ColorRGBA.fromHex(illumination.color!);
  const normalized = color.toNormalized();

  const light = new BABYLON.PointLight('light', position, scene);
  light.diffuse = new BABYLON.Color3(normalized.r, normalized.g, normalized.b);
  light.intensity = illumination.strength ?? 1.0;
}
```

### 12. Sky Color Animation

```typescript
// Animate sky color from day to night
const dayColor = '#87CEEB';    // Sky blue
const nightColor = '#0a0a2e';  // Dark blue

function updateSkyColor(timeOfDay: number) {
  // timeOfDay: 0 (midnight) - 1 (next midnight)
  let t = 0;

  if (timeOfDay < 0.25) {
    // Night to dawn
    t = timeOfDay / 0.25;
    const color = ColorUtils.lerp(nightColor, dayColor, t);
    setSkyColor(color);
  } else if (timeOfDay < 0.75) {
    // Day
    setSkyColor(dayColor);
  } else {
    // Dusk to night
    t = (timeOfDay - 0.75) / 0.25;
    const color = ColorUtils.lerp(dayColor, nightColor, t);
    setSkyColor(color);
  }
}
```

### 13. Particle Colors

```typescript
// Explosion particle colors
const explosionColors = [
  '#ff3300',  // Bright orange
  '#ff6600',  // Orange
  '#ff9900',  // Yellow-orange
  '#ffcc00',  // Yellow
].map(hex => ColorRGBA.fromHex(hex));

function createExplosionParticle(age: number): ColorRGBA {
  const maxAge = 1000; // ms
  const t = age / maxAge;

  // Fade through color gradient
  const index = Math.floor(t * (explosionColors.length - 1));
  const nextIndex = Math.min(index + 1, explosionColors.length - 1);
  const localT = (t * (explosionColors.length - 1)) % 1;

  return explosionColors[index].lerp(explosionColors[nextIndex], localT);
}
```

### 14. Validation

```typescript
import { ColorUtils } from '@nimbus/shared';

function validateColor(colorStr: string): boolean {
  // Check if valid hex
  if (ColorUtils.isValidHex(colorStr)) {
    return true;
  }

  // Try to parse
  const parsed = ColorUtils.parseColor(colorStr);
  return parsed !== null;
}

// Usage
console.log(validateColor('#ff5500'));      // true
console.log(validateColor('#f50'));         // true
console.log(validateColor('rgb(255,85,0)')); // true
console.log(validateColor('red'));          // true
console.log(validateColor('invalid'));      // false
```

## Predefined Colors

```typescript
ColorRGBA.WHITE       // (255, 255, 255, 1)
ColorRGBA.BLACK       // (0, 0, 0, 1)
ColorRGBA.RED         // (255, 0, 0, 1)
ColorRGBA.GREEN       // (0, 255, 0, 1)
ColorRGBA.BLUE        // (0, 0, 255, 1)
ColorRGBA.YELLOW      // (255, 255, 0, 1)
ColorRGBA.CYAN        // (0, 255, 255, 1)
ColorRGBA.MAGENTA     // (255, 0, 255, 1)
ColorRGBA.TRANSPARENT // (0, 0, 0, 0)

// Usage
const white = ColorRGBA.WHITE.toHex();  // '#ffffff'
```

## Network Storage

Colors are stored and transmitted as hex strings:

```json
{
  "visibility": {
    "textures": {
      "0": {
        "path": "/grass.png",
        "color": "#88ff88"
      }
    }
  },
  "illumination": {
    "color": "#ffaa00",
    "strength": 0.8
  }
}
```

**Benefits:**
- Compact (7 chars with #)
- Human-readable
- No parsing needed for storage
- Easy to edit in JSON

## Conversion Examples

### Hex ↔ RGBA

```typescript
// Hex → RGBA
const rgba = ColorRGBA.fromHex('#ff5500');
console.log(rgba.r, rgba.g, rgba.b, rgba.a);
// 255, 85, 0, 1

// RGBA → Hex
const hex = rgba.toHex();
console.log(hex);  // '#ff5500'
```

### Normalized ↔ RGBA

```typescript
// Normalized → RGBA
const rgba = ColorRGBA.fromNormalized(1.0, 0.33, 0.0);
console.log(rgba.r, rgba.g, rgba.b);
// 255, 84, 0

// RGBA → Normalized
const norm = rgba.toNormalized();
console.log(norm.r, norm.g, norm.b);
// 1.0, 0.329, 0.0
```

### Array Format

```typescript
const rgba = ColorRGBA.fromHex('#ff5500');

// To array (0-255)
const arr255 = rgba.toArray(false);
console.log(arr255);  // [255, 85, 0, 1]

// To array (0-1)
const arr1 = rgba.toArray(true);
console.log(arr1);  // [1.0, 0.333, 0.0, 1.0]
```

## Best Practices

### ✅ DO
- Store colors as hex strings in types
- Use ColorRGBA when you need component access
- Use ColorUtils for string-based operations
- Validate color strings from user input
- Use predefined colors when possible
- Include # in hex strings

### ❌ DON'T
- Don't store as separate r,g,b,a fields (use string)
- Don't mutate ColorRGBA (it's readonly)
- Don't forget alpha when needed
- Don't mix up 0-255 and 0-1 ranges
- Don't hardcode colors (use hex strings)

## Performance

### Memory
- **Hex string**: ~8 bytes (JS string overhead + 7 chars)
- **Separate RGBA fields**: ~32 bytes (4 × 8 bytes for numbers)
- **Savings**: 75% memory reduction

### Speed
- **String storage**: Fast, no conversion needed
- **Component access**: Create ColorRGBA when needed (lazy)
- **Rendering**: Convert to normalized once per render

## Integration Examples

### With BlockModifier

```typescript
const modifier: BlockModifier = {
  visibility: {
    textures: {
      [TextureKey.ALL]: {
        path: '/stone.png',
        color: '#888888'  // ✓ Hex string
      }
    }
  },
  illumination: {
    color: '#ffaa00',    // ✓ Hex string
    strength: 0.8
  }
};

// Use in renderer
const tintColor = ColorRGBA.fromHex(modifier.visibility!.textures![0].color!);
const lightColor = ColorRGBA.fromHex(modifier.illumination!.color!);
```

### With Animation Effects

```typescript
const skyEffect: AnimationEffect = {
  type: AnimationEffectType.SKY_CHANGE,
  params: {
    color: '#87CEEB',  // ✓ Hex string
    lightIntensity: 1.0
  },
  startTime: 0,
  duration: 1000
};

// Animate sky color
function animateSky(effect: AnimationEffect, t: number) {
  const startColor = '#0a0a2e';  // Night
  const endColor = effect.params.color!;  // Day

  const currentColor = ColorUtils.lerp(startColor, endColor, t);
  setSkyColor(currentColor);  // Sets as hex string
}
```

### With Notifications

```typescript
// Color-coded notifications
function getNotificationColor(type: NotificationType): ColorHex {
  switch (type) {
    case NotificationType.SYSTEM:
      return '#888888';
    case NotificationType.CHAT:
      return '#ffffff';
    case NotificationType.WARNING:
      return '#ffaa00';
    case NotificationType.ERROR:
      return '#ff3333';
    case NotificationType.INFO:
      return '#3399ff';
    default:
      return '#ffffff';
  }
}

// Apply to UI
const color = getNotificationColor(notification.t);
element.style.color = color;  // ✓ Direct use as CSS
```

## Color Utilities

### Lighten/Darken

```typescript
const base = '#ff5500';

const lighter = ColorUtils.lighten(base, 0.2);  // 20% lighter
const darker = ColorUtils.darken(base, 0.3);    // 30% darker

// Use for hover effects
button.style.backgroundColor = base;
button.addEventListener('mouseenter', () => {
  button.style.backgroundColor = lighter;
});
```

### Alpha Manipulation

```typescript
const solid = '#ff5500';
const transparent = ColorUtils.setAlpha(solid, 0.5);
// '#ff550080'

// Use for fade effects
function fadeOut(element: HTMLElement, duration: number) {
  const startColor = '#ff5500ff';
  const endColor = '#ff550000';

  animate(duration, (t) => {
    const color = ColorUtils.lerp(startColor, endColor, t);
    element.style.backgroundColor = color;
  });
}
```

### Color Interpolation

```typescript
// Health bar color: red → yellow → green
function getHealthColor(health: number, maxHealth: number): ColorHex {
  const percent = health / maxHealth;

  if (percent > 0.5) {
    // Green to yellow (100% → 50%)
    const t = (1 - percent) * 2;  // 0 → 1
    return ColorUtils.lerp('#00ff00', '#ffff00', t);
  } else {
    // Yellow to red (50% → 0%)
    const t = (0.5 - percent) * 2;  // 0 → 1
    return ColorUtils.lerp('#ffff00', '#ff0000', t);
  }
}

// Usage
const healthColor = getHealthColor(player.health.current, player.health.max);
healthBar.style.backgroundColor = healthColor;
```

## Comparison with Other Approaches

### Approach 1: Separate Fields (❌ Not Used)
```typescript
// Don't do this
interface BadColor {
  r: number;
  g: number;
  b: number;
  a: number;
}
```
**Problems:** 32 bytes, verbose, not CSS-compatible

### Approach 2: Hex String + ColorRGBA (✅ Our Approach)
```typescript
// Store as string
color: '#ff5500'

// Convert when needed
const rgba = ColorRGBA.fromHex(color);
```
**Benefits:** 8 bytes, CSS-compatible, convert only when needed

### Approach 3: Always RGB Object (❌ Inefficient)
```typescript
// Don't do this
color: { r: 255, g: 85, b: 0 }
```
**Problems:** 24+ bytes, not CSS-compatible, verbose JSON

## Summary

- **Storage**: Hex strings (`ColorHex`)
- **Processing**: ColorRGBA class
- **Readonly**: Components are immutable
- **Bidirectional**: Easy conversion hex ↔ RGBA
- **Utilities**: ColorUtils for string operations
- **Predefined**: Common colors as static properties
- **Type-safe**: Full TypeScript support
- **Efficient**: ~8 bytes vs ~32 bytes for separate fields
