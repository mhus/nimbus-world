# Wind Shader - Vector2 Uniform Problem und Lösung

## Problem

Blocks mit Wind-Shader wurden unsichtbar, sobald `windDirection` (vec2) Uniform verwendet wurde.

## Root Cause

`setVector2()` erwartet ein `Vector2`-Objekt, nicht zwei separate Zahlen.

## Lösung

```typescript
import { Vector2 } from '@babylonjs/core';

// ❌ FALSCH: Zwei separate Zahlen
material.setVector2('windDirection', 1.0, 0.0);

// ❌ FALSCH: Array mit setFloats()
material.setFloats('windDirection', [1.0, 0.0]);  // Werte kommen nicht an

// ✅ RICHTIG: Vector2-Objekt
material.setVector2('windDirection', new Vector2(1.0, 0.0));
```

**Quelle:** [Babylon.js Effect.setVector2 API](https://doc.babylonjs.com/typedoc/classes/BABYLON.Effect#setvector2)

Signatur: `setVector2(uniformName: string, vector2: IVector2Like)`

## Implementierung

**WindShader.ts:**
```typescript
import { ShaderMaterial, Scene, Effect, Texture, Vector2 } from '@babylonjs/core';

// In createWindMaterial():
material.setVector2('windDirection', new Vector2(1.0, 0.0));

// In updateWindParameters():
material.setVector2('windDirection', new Vector2(windDirection.x, windDirection.z));
```

**Vertex Shader (GLSL):**
```glsl
uniform vec2 windDirection;

void main(void) {
  vec3 pos = position;

  if (windLever > 0.01) {
    float windWave = sin(time * windSwayFactor) * windStrength;

    // Apply wind in X and Z directions
    pos.x += windDirection.x * windWave * windLever;
    pos.z += windDirection.y * windWave * windLever;
  }

  gl_Position = worldViewProjection * vec4(pos, 1.0);
  vUV = uv;
  vColor = color;
  vNormal = normalize((world * vec4(normal, 0.0)).xyz);
}
```

## Anwendung auf andere Shader

Diese Regel gilt für ALLE Babylon.js Shader Uniforms:

- `setVector2()` → `new Vector2(x, y)`
- `setVector3()` → `new Vector3(x, y, z)`
- `setVector4()` → `new Vector4(x, y, z, w)`
- `setColor3()` → `new Color3(r, g, b)`
- `setColor4()` → `new Color4(r, g, b, a)`
- `setMatrix()` → `Matrix` object

**NIEMALS** primitive Werte oder Arrays direkt übergeben!

## Code-Referenzen

- **WindShader.ts**: `packages/client/src/rendering/WindShader.ts`
  - Line 5: Vector2 Import
  - Line 167: Vector2 Initialisierung
  - Line 204: Vector2 in updateWindParameters()
  - Lines 56-63: Vertex Shader mit windDirection

- **FluidWaveShader.ts**: `packages/client/src/rendering/FluidWaveShader.ts`
  - Weitere Beispiele für korrekte Uniform-Verwendung
