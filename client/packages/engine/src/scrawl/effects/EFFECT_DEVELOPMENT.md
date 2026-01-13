# Scrawl Effect Development Guide

This guide explains how to create new effects for the Scrawl Framework.

## Table of Contents

1. [Overview](#overview)
2. [Effect Types](#effect-types)
3. [Creating a New Effect](#creating-a-new-effect)
4. [Effect Lifecycle](#effect-lifecycle)
5. [Parameter Updates](#parameter-updates)
6. [Best Practices](#best-practices)
7. [Examples](#examples)
8. [Registration](#registration)

---

## Overview

Scrawl effects are reusable visual, audio, or gameplay elements that can be triggered from scripts. Effects extend the `ScrawlEffectHandler` base class and implement specific behavior.

**Key Concepts:**
- **One-Shot Effects**: Execute once and return immediately (e.g., log, command, spawn particle)
- **Steady Effects**: Run continuously until stopped (e.g., sound:loop, beam:follow, particle emitters)
- **Parameter Updates**: Steady effects can react to parameter changes during execution (e.g., target position updates)

---

## Effect Types

### One-Shot Effects

Execute immediately and complete in one call to `execute()`.

**Characteristics:**
- `isSteadyEffect()` returns `false` (default)
- `execute()` performs action and returns
- `isRunning()` returns `false` (default)
- No need for cleanup in `stop()`

**Examples:**
- `LogEffect` - Logs a message
- `CommandEffect` - Executes a command
- One-time particle burst

**Use Cases:**
- Discrete actions that complete immediately
- Effects used in loops (Repeat, While, Until with one-shot behavior)

### Steady Effects

Run continuously from `execute()` until `stop()` is called.

**Characteristics:**
- `isSteadyEffect()` returns `true`
- `execute()` starts the effect (may return immediately)
- Effect runs in background (animation loop, sound playback)
- `isRunning()` returns `true` while active
- **Must implement `stop()`** for cleanup

**Examples:**
- `LoopingSoundEffect` - Plays sound in loop
- `BeamFollowEffect` - Continuous beam rendering
- `ParticleBeamEffect` - Continuous particle emission

**Use Cases:**
- While/Until loops (runs once until condition met)
- Continuous effects that need cleanup
- Effects that react to parameter updates

---

## Creating a New Effect

### Step 1: Create Effect File

**Location:** `/Users/hummel/sources/mhus/nimbus/client/packages/engine/src/scrawl/effects/YourEffect.ts`

```typescript
import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';

const logger = getLogger('YourEffect');

/**
 * Options for YourEffect
 */
export interface YourEffectOptions {
  // Required parameters
  parameter1: string;

  // Optional parameters with defaults
  parameter2?: number;
  parameter3?: boolean;
}

/**
 * YourEffect - Brief description
 *
 * Detailed description of what this effect does.
 *
 * Usage:
 * ```json
 * {
 *   "kind": "Play",
 *   "effectId": "yourEffect",
 *   "ctx": {
 *     "parameter1": "value",
 *     "parameter2": 42
 *   }
 * }
 * ```
 */
export class YourEffect extends ScrawlEffectHandler<YourEffectOptions> {
  // Private state (if needed)
  private someResource: any = null;

  /**
   * Indicates if this is a steady effect (default: false for one-shot)
   */
  isSteadyEffect(): boolean {
    return false; // Change to true for steady effects
  }

  /**
   * Execute the effect
   */
  async execute(ctx: ScrawlExecContext): Promise<void> {
    // Implement your effect logic here
    logger.debug('Effect executed', this.options);
  }

  /**
   * Stop/cleanup the effect (required for steady effects)
   */
  stop(): void {
    // Clean up resources
    if (this.someResource) {
      this.someResource.dispose();
      this.someResource = null;
    }
  }

  /**
   * Check if effect is running (required for steady effects)
   */
  isRunning(): boolean {
    return this.someResource !== null;
  }

  /**
   * React to parameter changes (optional, for dynamic effects)
   */
  onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
    // React to parameter updates
    if (paramName === 'targetPos') {
      // Update internal state
    }
  }
}
```

### Step 2: Register Effect

**Location:** `/Users/hummel/sources/mhus/nimbus/client/packages/engine/src/scrawl/ScrawlService.ts`

```typescript
// Import your effect
import { YourEffect } from './effects/YourEffect';

// In registerBuiltInEffects()
private registerBuiltInEffects(): void {
  // ... existing effects

  // Register your effect
  this.effectRegistry.register('yourEffect', YourEffect);

  // Or with namespace
  this.effectRegistry.register('category:yourEffect', YourEffect);
}
```

### Step 3: Export Effect

**Location:** `/Users/hummel/sources/mhus/nimbus/client/packages/engine/src/scrawl/effects/index.ts`

```typescript
export { YourEffect } from './YourEffect';
export type { YourEffectOptions } from './YourEffect';
```

---

## Effect Lifecycle

### One-Shot Effect Lifecycle

```
ScrawlExecutor.execStepPlay()
  ↓
effectFactory.create('yourEffect', options)
  ↓
effect.execute(ctx)
  ↓
[Effect performs action]
  ↓
execute() returns
  ↓
[Effect complete]
```

### Steady Effect Lifecycle

```
ScrawlExecutor.execStepWhile/Until()
  ↓
effectFactory.create('yourEffect', options)
  ↓
effect.isSteadyEffect() → true
  ↓
effect.execute(ctx)
  ↓
[Effect starts background process]
  ↓
execute() returns (but effect keeps running)
  ↓
effect.isRunning() → true
  ↓
[Loop continues, calling onUpdate/onParameterChanged]
  ↓
[Termination condition met]
  ↓
effect.stop()
  ↓
[Cleanup resources]
  ↓
effect.isRunning() → false
```

---

## Parameter Updates

### When Are Parameters Updated?

Parameters are updated in two scenarios:

1. **StepUntil loops** - Automatically via `InputService.updateActiveExecutors()`
2. **External calls** - Via `ScrawlService.updateExecutorParameter()`

### Implementing onParameterChanged()

```typescript
onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
  logger.debug('Parameter updated', { paramName, value });

  // React to specific parameters
  switch (paramName) {
    case 'targetPos':
      if (ctx.patients?.[0]) {
        this.targetPosition = new Vector3(
          ctx.patients[0].position.x,
          ctx.patients[0].position.y,
          ctx.patients[0].position.z
        );
      }
      break;

    case 'volume':
      if (this.sound) {
        this.sound.setVolume(value);
      }
      break;

    case 'color':
      if (this.material) {
        this.material.emissiveColor = Color3.FromHexString(value);
      }
      break;
  }
}
```

### Accessing Updated Context

The `ctx` parameter contains the latest execution context with updated variables:

```typescript
onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
  // Access updated context variables
  const playerPos = ctx.vars?.['sourcePos'];
  const targetPos = ctx.vars?.['targetPos'];

  // Access context subjects
  const actor = ctx.actor;
  const patients = ctx.patients;
}
```

---

## Best Practices

### 1. Resource Management

**Always dispose resources in `stop()`:**

```typescript
stop(): void {
  // Cancel animation frames
  if (this.animationHandle !== null) {
    cancelAnimationFrame(this.animationHandle);
    this.animationHandle = null;
  }

  // Dispose Babylon.js resources
  if (this.mesh) {
    this.mesh.dispose();
    this.mesh = null;
  }

  if (this.material) {
    this.material.dispose();
    this.material = null;
  }

  if (this.particleSystem) {
    this.particleSystem.stop();
    this.particleSystem.dispose();
    this.particleSystem = null;
  }

  // Dispose textures
  if (this.texture) {
    this.texture.dispose();
    this.texture = null;
  }
}
```

### 2. Error Handling

**Use try-catch for external dependencies:**

```typescript
async execute(ctx: ScrawlExecContext): Promise<void> {
  const scene = ctx.appContext.services.engine?.getScene();
  if (!scene) {
    logger.warn('Scene not available');
    return;
  }

  try {
    // Your effect logic
    this.mesh = MeshBuilder.CreateBox('myMesh', {}, scene);
  } catch (error) {
    logger.error('Failed to create effect', { error });
    this.cleanup();
    throw error;
  }
}
```

### 3. Null Safety

**Always check for null/undefined:**

```typescript
private animate = (): void => {
  // Check if effect is still running
  if (!this.isRunning()) {
    return;
  }

  // Check resources exist
  if (!this.mesh || !this.targetPos) {
    return;
  }

  // Safe to use resources
  this.mesh.position = this.targetPos;
  this.animationHandle = requestAnimationFrame(this.animate);
};
```

### 4. Logging

**Use appropriate log levels:**

```typescript
// Debug: Detailed execution info (only visible at DEBUG level)
logger.debug('Effect started', { options: this.options });

// Info: Important milestones
logger.debug('Effect completed successfully');

// Warn: Non-critical issues (effect can continue)
logger.warn('AudioService not available, skipping sound');

// Error: Critical failures
logger.error('Failed to create mesh', { error });
```

### 5. Default Parameter Values

**Use nullish coalescing for defaults:**

```typescript
async execute(ctx: ScrawlExecContext): Promise<void> {
  const duration = this.options.duration ?? 1.0;
  const color = this.options.color ?? '#ffffff';
  const alpha = this.options.alpha ?? 1.0;
  const thickness = this.options.thickness ?? 0.1;
}
```

### 6. Scene Access

**Always check scene availability:**

```typescript
async execute(ctx: ScrawlExecContext): Promise<void> {
  const scene = ctx.appContext.services.engine?.getScene();
  if (!scene) {
    logger.warn('Scene not available');
    return;
  }
  this.scene = scene; // Store for later use
}
```

---

## Examples

### Example 1: One-Shot Effect (Simple)

```typescript
import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';

const logger = getLogger('SpawnParticleEffect');

export interface SpawnParticleOptions {
  position: { x: number; y: number; z: number };
  color: string;
  count?: number;
}

/**
 * Spawns a one-time particle burst
 */
export class SpawnParticleEffect extends ScrawlEffectHandler<SpawnParticleOptions> {
  isSteadyEffect(): boolean {
    return false; // One-shot
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) return;

    const count = this.options.count ?? 10;
    const position = new Vector3(
      this.options.position.x,
      this.options.position.y,
      this.options.position.z
    );

    // Create and configure particle system
    const ps = new ParticleSystem('burst', count, scene);
    ps.emitter = position;
    ps.minLifeTime = 0.5;
    ps.maxLifeTime = 1.0;
    ps.emitRate = count * 10;

    // Start and auto-stop
    ps.start();
    setTimeout(() => {
      ps.stop();
      ps.dispose();
    }, 1000);

    logger.debug('Particle burst spawned', { count, position });
  }
}
```

### Example 2: Steady Effect (Continuous)

```typescript
import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import { MeshBuilder, StandardMaterial, Color3, Mesh, Scene } from '@babylonjs/core';

const logger = getLogger('GlowingSphereEffect');

export interface GlowingSphereOptions {
  position: { x: number; y: number; z: number };
  color: string;
  radius?: number;
  alpha?: number;
  pulseSpeed?: number;
}

/**
 * Creates a glowing sphere that pulses until stopped
 */
export class GlowingSphereEffect extends ScrawlEffectHandler<GlowingSphereOptions> {
  private mesh: Mesh | null = null;
  private material: StandardMaterial | null = null;
  private scene: Scene | null = null;
  private animationHandle: number | null = null;
  private startTime: number = 0;

  isSteadyEffect(): boolean {
    return true; // Steady effect
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    const position = new Vector3(
      this.options.position.x,
      this.options.position.y,
      this.options.position.z
    );

    // Create sphere
    this.mesh = MeshBuilder.CreateSphere(
      'glowingSphere',
      { diameter: (this.options.radius ?? 0.5) * 2 },
      this.scene
    );
    this.mesh.position = position;

    // Create glowing material
    this.material = new StandardMaterial('glowMat', this.scene);
    this.material.emissiveColor = Color3.FromHexString(this.options.color);
    this.material.alpha = this.options.alpha ?? 1.0;
    this.mesh.material = this.material;

    // Start animation
    this.startTime = performance.now() / 1000;
    this.animate();

    logger.debug('Glowing sphere effect started', { position });
  }

  private animate = (): void => {
    if (!this.isRunning() || !this.material) {
      return;
    }

    // Pulse effect
    const elapsed = performance.now() / 1000 - this.startTime;
    const pulseSpeed = this.options.pulseSpeed ?? 2.0;
    const pulse = Math.sin(elapsed * pulseSpeed) * 0.5 + 0.5;

    this.material.alpha = (this.options.alpha ?? 1.0) * (0.5 + pulse * 0.5);

    this.animationHandle = requestAnimationFrame(this.animate);
  };

  stop(): void {
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    if (this.mesh) {
      this.mesh.dispose();
      this.mesh = null;
    }

    if (this.material) {
      this.material.dispose();
      this.material = null;
    }

    logger.debug('Glowing sphere effect stopped');
  }

  isRunning(): boolean {
    return this.mesh !== null;
  }
}
```

### Example 3: Steady Effect with Parameter Updates

```typescript
import { getLogger } from '@nimbus/shared';
import { ScrawlEffectHandler } from '../ScrawlEffectHandler';
import type { ScrawlExecContext } from '../ScrawlExecContext';
import { Vector3, MeshBuilder, LinesMesh, Scene } from '@babylonjs/core';

const logger = getLogger('LineFollowEffect');

export interface LineFollowOptions {
  color: string;
  thickness?: number;
  alpha?: number;
}

/**
 * Draws a line that follows dynamic source/target positions
 */
export class LineFollowEffect extends ScrawlEffectHandler<LineFollowOptions> {
  private line: LinesMesh | null = null;
  private scene: Scene | null = null;
  private sourcePos: Vector3 | null = null;
  private targetPos: Vector3 | null = null;
  private animationHandle: number | null = null;

  isSteadyEffect(): boolean {
    return true;
  }

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) {
      logger.warn('Scene not available');
      return;
    }
    this.scene = scene;

    // Get initial positions from context
    if (ctx.actor) {
      this.sourcePos = new Vector3(
        ctx.actor.position.x,
        ctx.actor.position.y,
        ctx.actor.position.z
      );
    }

    if (ctx.patients?.[0]) {
      this.targetPos = new Vector3(
        ctx.patients[0].position.x,
        ctx.patients[0].position.y,
        ctx.patients[0].position.z
      );
    }

    if (!this.sourcePos || !this.targetPos) {
      logger.warn('Source or target position missing');
      return;
    }

    // Create line
    this.createLine();

    // Start animation
    this.animate();

    logger.debug('Line follow effect started');
  }

  onParameterChanged(paramName: string, value: any, ctx: ScrawlExecContext): void {
    // Update positions from context when they change
    if (ctx.actor) {
      this.sourcePos = new Vector3(
        ctx.actor.position.x,
        ctx.actor.position.y,
        ctx.actor.position.z
      );
    }

    if (ctx.patients?.[0]) {
      this.targetPos = new Vector3(
        ctx.patients[0].position.x,
        ctx.patients[0].position.y,
        ctx.patients[0].position.z
      );
    }

    logger.debug('Line positions updated', { paramName });
  }

  private createLine(): void {
    if (!this.scene || !this.sourcePos || !this.targetPos) return;

    this.line = MeshBuilder.CreateLines(
      'line',
      {
        points: [this.sourcePos, this.targetPos],
        updatable: true,
      },
      this.scene
    );

    this.line.color = Color3.FromHexString(this.options.color);
    this.line.alpha = this.options.alpha ?? 1.0;
  }

  private animate = (): void => {
    if (!this.isRunning() || !this.line || !this.sourcePos || !this.targetPos) {
      return;
    }

    // Update line points
    this.line = MeshBuilder.CreateLines(
      'line',
      {
        points: [this.sourcePos, this.targetPos],
        instance: this.line,
      }
    );

    this.animationHandle = requestAnimationFrame(this.animate);
  };

  stop(): void {
    if (this.animationHandle !== null) {
      cancelAnimationFrame(this.animationHandle);
      this.animationHandle = null;
    }

    if (this.line) {
      this.line.dispose();
      this.line = null;
    }

    logger.debug('Line follow effect stopped');
  }

  isRunning(): boolean {
    return this.line !== null;
  }
}
```

---

## Effect Context

### ScrawlExecContext Properties

The execution context provides access to the application state:

```typescript
interface ScrawlExecContext {
  // Application context (services, config)
  appContext: AppContext;

  // Script executor instance
  executor: ScrawlExecutor;

  // Current script ID
  scriptId: string;

  // Source subject (actor)
  actor?: ScrawlSubject;

  // Target subjects (patients)
  patients?: ScrawlSubject[];

  // Variables (accessible via $varName)
  vars?: Record<string, any>;
}
```

### Default Variables (from Items/Shortcuts)

When a script is executed from an item shortcut, these variables are automatically available:

| Variable | Type | Description |
|----------|------|-------------|
| `$source` | ScrawlSubject | Player position (actor) |
| `$target` | ScrawlSubject | First target (patient[0]) |
| `$targets` | ScrawlSubject[] | All targets (patients) |
| `$item` | Block | Item Block object |
| `$itemId` | string | Item ID |
| `$itemName` | string | Item display name |
| `$itemTexture` | string | Item texture filename |

**Accessing in Effects:**

```typescript
async execute(ctx: ScrawlExecContext): Promise<void> {
  // Access via vars
  const itemId = ctx.vars?.['itemId'];
  const itemName = ctx.vars?.['itemName'];

  // Access subjects
  const playerPos = ctx.actor?.position;
  const targetPos = ctx.patients?.[0]?.position;
}
```

### ScrawlSubject Structure

```typescript
interface ScrawlSubject {
  position: Vector3;     // World position
  entityId?: string;     // Entity ID if target is entity
  blockId?: string;      // Block ID if target is block
}
```

---

## Registration

### Effect Naming Conventions

**Use namespaces for organization:**

- `log` - Simple effects (built-in)
- `command` - Command effects
- `sound:loop` - Audio effects (category:name)
- `beam:follow` - VFX effects
- `animation:cast` - Animation effects
- `move:towards` - Movement effects

**Benefits:**
- Clear categorization
- Avoid naming conflicts
- Easy to find related effects

### Registration Example

```typescript
// In ScrawlService.ts
private registerBuiltInEffects(): void {
  // Simple effects
  this.effectRegistry.register('log', LogEffect);
  this.effectRegistry.register('command', CommandEffect);

  // VFX effects
  this.effectRegistry.register('circleMarker', CircleMarkerEffect);
  this.effectRegistry.register('projectile', ProjectileEffect);
  this.effectRegistry.register('particleBeam', ParticleBeamEffect);

  // Looping/Steady effects
  this.effectRegistry.register('sound:loop', LoopingSoundEffect);
  this.effectRegistry.register('beam:follow', BeamFollowEffect);

  logger.debug('Built-in effects registered', {
    effects: ['log', 'command', 'circleMarker', ...],
  });
}
```

---

## Testing

### Unit Test Template

```typescript
import { describe, it, expect, beforeEach, afterEach, jest } from '@jest/globals';
import { YourEffect } from '../effects/YourEffect';
import type { ScrawlExecContext } from '../ScrawlExecContext';

describe('YourEffect', () => {
  let effect: YourEffect;
  let mockContext: ScrawlExecContext;

  beforeEach(() => {
    // Setup mock context
    mockContext = {
      appContext: {
        services: {
          engine: {
            getScene: jest.fn(() => mockScene),
          },
        },
      },
      executor: {} as any,
      scriptId: 'test',
      vars: {},
    } as any;

    // Create effect instance
    effect = new YourEffect(
      { log: console.log, now: () => Date.now() / 1000 },
      { parameter1: 'value' }
    );
  });

  afterEach(() => {
    // Cleanup
    if (effect.stop) {
      effect.stop();
    }
  });

  it('should be a one-shot effect', () => {
    expect(effect.isSteadyEffect()).toBe(false);
  });

  it('should execute successfully', async () => {
    await effect.execute(mockContext);
    // Add assertions
  });

  // For steady effects:
  it('should clean up resources on stop', () => {
    effect.execute(mockContext);
    expect(effect.isRunning()).toBe(true);

    effect.stop();
    expect(effect.isRunning()).toBe(false);
  });

  // For parameter updates:
  it('should react to parameter changes', () => {
    effect.execute(mockContext);

    effect.onParameterChanged?.('targetPos', newPosition, mockContext);

    // Assert effect updated internal state
  });
});
```

---

## Common Patterns

### Pattern 1: Timed Effect with Phases

```typescript
/**
 * Effect with setup, active, and fade phases
 */
export class TimedEffect extends ScrawlEffectHandler<TimedEffectOptions> {
  private startTime: number = 0;

  async execute(ctx: ScrawlExecContext): Promise<void> {
    this.startTime = this.now();
    this.animate();
  }

  private animate = (): void => {
    const elapsed = this.now() - this.startTime;
    const setupDuration = this.options.setupDuration ?? 0.2;
    const activeDuration = this.options.duration ?? 1.0;
    const fadeDuration = this.options.fadeDuration ?? 0.2;
    const totalDuration = setupDuration + activeDuration + fadeDuration;

    if (elapsed >= totalDuration) {
      this.cleanup();
      return;
    }

    // Calculate phase
    let alpha = 1.0;
    if (elapsed < setupDuration) {
      // Setup phase
      alpha = elapsed / setupDuration;
    } else if (elapsed > setupDuration + activeDuration) {
      // Fade phase
      const fadeElapsed = elapsed - setupDuration - activeDuration;
      alpha = 1.0 - (fadeElapsed / fadeDuration);
    }

    // Update effect with current alpha
    this.updateAlpha(alpha);

    this.animationHandle = requestAnimationFrame(this.animate);
  };
}
```

### Pattern 2: Pooled Resources

```typescript
/**
 * Effect that reuses resources from a pool
 */
export class PooledEffect extends ScrawlEffectHandler<PooledEffectOptions> {
  private static texturePool = new Map<string, Texture>();

  async execute(ctx: ScrawlExecContext): Promise<void> {
    const scene = ctx.appContext.services.engine?.getScene();
    if (!scene) return;

    // Get or create texture from pool
    const textureKey = this.options.textureName;
    let texture = PooledEffect.texturePool.get(textureKey);

    if (!texture) {
      texture = new Texture(`/textures/${textureKey}`, scene);
      PooledEffect.texturePool.set(textureKey, texture);
    }

    // Use texture (don't dispose in stop(), it's shared)
    this.material.diffuseTexture = texture;
  }

  stop(): void {
    // DON'T dispose pooled resources
    // Only dispose instance-specific resources
    if (this.mesh) {
      this.mesh.dispose();
      this.mesh = null;
    }
  }
}
```

### Pattern 3: Service Integration

```typescript
/**
 * Effect that uses multiple services
 */
export class ComplexEffect extends ScrawlEffectHandler<ComplexEffectOptions> {
  async execute(ctx: ScrawlExecContext): Promise<void> {
    const services = ctx.appContext.services;

    // Audio service
    const audioService = services.audio;
    if (audioService) {
      const sound = audioService.createSound('impact.mp3', {});
      sound.play();
    }

    // Entity service
    const entityService = services.entity;
    if (entityService && ctx.actor?.entityId) {
      const entity = entityService.getEntity(ctx.actor.entityId);
      // Modify entity state
    }

    // Modifier service
    const modifierService = services.modifier;
    if (modifierService && ctx.actor?.entityId) {
      modifierService.addModifier(ctx.actor.entityId, 'CUSTOM', 5000, {
        // Modifier definition
      });
    }
  }
}
```

---

## Troubleshooting

### Common Issues

#### Issue 1: Effect not stopping

**Problem:** `stop()` is called but resources remain active.

**Solution:** Ensure all cleanup is in `stop()` method:
- Cancel `requestAnimationFrame` handles
- Dispose all Babylon.js resources (meshes, materials, textures, particle systems)
- Clear timers and intervals
- Set all resource references to `null`

#### Issue 2: Effect not receiving parameter updates

**Problem:** `onParameterChanged()` is not called.

**Solution:**
- Only works in `StepUntil` loops (not StepWhile)
- Effect must be steady (`isSteadyEffect() = true`)
- Check if `updateExecutorParameter()` is being called
- Verify executorId is correct

#### Issue 3: Memory leaks

**Problem:** Resources accumulate in memory.

**Solution:**
- Always implement `stop()` for steady effects
- Dispose ALL Babylon.js resources
- Use `finally` blocks in executor for guaranteed cleanup
- Test with Chrome DevTools memory profiler

#### Issue 4: Effect starts multiple times in While loop

**Problem:** Effect creates new instances each iteration.

**Solution:**
- Return `true` from `isSteadyEffect()` to run only once
- Executor will track and reuse the instance
- Only one-shot effects should create new instances per iteration

---

## Performance Tips

### 1. Minimize requestAnimationFrame Calls

```typescript
// Bad: Multiple RAF callbacks
effect1.animate = () => requestAnimationFrame(effect1.animate);
effect2.animate = () => requestAnimationFrame(effect2.animate);

// Good: Single RAF callback for multiple effects
private animate = (): void => {
  this.updateEffect1();
  this.updateEffect2();
  this.updateEffect3();
  this.animationHandle = requestAnimationFrame(this.animate);
};
```

### 2. Reuse Geometries

```typescript
// Bad: Create new geometry each time
this.mesh = MeshBuilder.CreateBox('box', {}, scene);

// Good: Use instances for repeated geometries
const baseMesh = scene.getMeshByName('boxTemplate') ||
  MeshBuilder.CreateBox('boxTemplate', {}, scene);
this.mesh = baseMesh.createInstance('box_instance');
```

### 3. Texture Management

```typescript
// Bad: Load texture multiple times
const texture = new Texture('same.png', scene);

// Good: Cache and reuse textures
private static textureCache = new Map<string, Texture>();

getTexture(name: string, scene: Scene): Texture {
  let texture = PooledEffect.textureCache.get(name);
  if (!texture) {
    texture = new Texture(`/textures/${name}`, scene);
    PooledEffect.textureCache.set(name, texture);
  }
  return texture;
}
```

### 4. Particle System Optimization

```typescript
// Set appropriate particle count based on distance/importance
const distance = Vector3.Distance(playerPos, effectPos);
const particleCount = distance < 10 ? 2000 : distance < 50 ? 500 : 100;

const ps = new ParticleSystem('particles', particleCount, scene);
```

---

## Advanced Topics

### Custom Effect Dependencies

Effects receive dependencies via constructor:

```typescript
export interface EffectDeps {
  log?: (...args: any[]) => void;
  now?: () => number;
  [key: string]: any;
}

// Access in effect
protected logInfo(message: string): void {
  if (this.deps.log) {
    this.deps.log(message);
  } else {
    logger.debug(message);
  }
}

protected now(): number {
  return this.deps.now ? this.deps.now() : performance.now() / 1000;
}
```

### Multi-Phase Effects

```typescript
/**
 * Effect with distinct phases (charge, release, impact)
 */
export class MultiPhaseEffect extends ScrawlEffectHandler<MultiPhaseOptions> {
  private phase: 'charge' | 'release' | 'impact' = 'charge';

  private animate = (): void => {
    const elapsed = this.now() - this.startTime;

    switch (this.phase) {
      case 'charge':
        this.animateCharge(elapsed);
        if (elapsed > this.options.chargeDuration) {
          this.phase = 'release';
          this.startTime = this.now();
        }
        break;

      case 'release':
        this.animateRelease(elapsed);
        if (elapsed > this.options.releaseDuration) {
          this.phase = 'impact';
          this.startTime = this.now();
        }
        break;

      case 'impact':
        this.animateImpact(elapsed);
        if (elapsed > this.options.impactDuration) {
          this.cleanup();
          return;
        }
        break;
    }

    this.animationHandle = requestAnimationFrame(this.animate);
  };
}
```

---

## Checklist for New Effects

Before submitting a new effect, ensure:

- [ ] Effect extends `ScrawlEffectHandler<OptionsType>`
- [ ] Options interface is exported
- [ ] `isSteadyEffect()` correctly returns true/false
- [ ] `execute()` method implemented
- [ ] `stop()` implemented for steady effects
- [ ] `isRunning()` implemented for steady effects
- [ ] All resources disposed in `stop()`
- [ ] Null checks for scene and resources
- [ ] Error handling with try-catch
- [ ] Logging at appropriate levels
- [ ] JSDoc comments with usage example
- [ ] Registered in ScrawlService
- [ ] Exported from effects/index.ts
- [ ] Unit tests written
- [ ] Tested in actual scripts

---

## Reference Effects

Study these effects as templates:

| Effect | Type | Features | File |
|--------|------|----------|------|
| **LogEffect** | One-Shot | Simple, minimal | `LogEffect.ts` |
| **CommandEffect** | One-Shot | Service integration | `CommandEffect.ts` |
| **CircleMarkerEffect** | One-Shot | Shaders, timed phases | `CircleMarkerEffect.ts` |
| **ProjectileEffect** | One-Shot | Movement, orientation | `ProjectileEffect.ts` |
| **ParticleBeamEffect** | One-Shot | Particles, complex shader | `ParticleBeamEffect.ts` |
| **LoopingSoundEffect** | Steady | Audio, parameter updates | `LoopingSoundEffect.ts` |
| **BeamFollowEffect** | Steady | Geometry, dynamic updates | `BeamFollowEffect.ts` |

---

## Questions?

For questions or issues with effect development:
- Check existing effects in `effects/` directory
- Review `ScrawlEffectHandler.ts` base class
- See `ScrawlExecutor.ts` for how effects are executed
- Check `LOGGER_USAGE.md` for logging guidelines

Happy effect coding! 🎨✨
