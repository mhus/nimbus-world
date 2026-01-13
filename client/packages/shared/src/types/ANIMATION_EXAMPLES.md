# Animation System - Examples

Timeline-based animation system with parallel and sequential effects.

## Architecture

```
AnimationTemplate (Client)
  ↓ fill placeholders
AnimationInstance (Client)
  ↓ send to server
Server broadcasts to all players
  ↓
All clients play animation
```

## Example 1: Arrow Shot (Complex Multi-Position)

Player shoots arrow at NPC with explosion and sky effect.

### Template Definition (Client-side)

```typescript
const arrowShotTemplate: AnimationTemplate = {
  id: 'arrow_shot',
  name: 'Arrow Shot',
  description: 'Player shoots arrow with explosion and sky effects',
  placeholders: ['shooter', 'target', 'impact'],
  effects: [
    // Effect 1: Projectile flies from shooter to target (parallel)
    {
      id: 'projectile',
      type: AnimationEffectType.PROJECTILE,
      positions: [
        AnimationHelper.placeholder('shooter'),
        AnimationHelper.placeholder('target')
      ],
      params: {
        projectileModel: '/models/arrow.babylon',
        speed: 50,
        trajectory: 'arc'
      },
      startTime: 0,
      duration: 1000,
      blocking: true  // Wait for arrow to arrive
    },

    // Effect 2: Sky darkens (parallel with projectile)
    {
      id: 'sky_darken',
      type: AnimationEffectType.SKY_CHANGE,
      positions: [],  // No position needed
      params: {
        color: '#333333',
        lightIntensity: 0.3,
        easing: EasingType.EASE_IN
      },
      startTime: 0,
      duration: 500
    },

    // Effect 3: Explosion at impact (sequential, after projectile)
    {
      id: 'explosion',
      type: AnimationEffectType.EXPLOSION,
      positions: [
        AnimationHelper.placeholder('impact')
      ],
      params: {
        radius: 5,
        explosionIntensity: 1.0
      },
      startTime: 1000,
      duration: 300
    },

    // Effect 4: Camera shake (with explosion)
    {
      id: 'camera_shake',
      type: AnimationEffectType.CAMERA_SHAKE,
      positions: [],
      params: {
        intensity: 0.5,
        frequency: 20
      },
      startTime: 1000,
      duration: 300
    },

    // Effect 5: Play explosion sound
    {
      id: 'explosion_sound',
      type: AnimationEffectType.PLAY_SOUND,
      positions: [
        AnimationHelper.placeholder('impact')
      ],
      params: {
        soundPath: '/sounds/explosion.ogg',
        volume: 0.8
      },
      startTime: 1000,
      duration: 0  // Sound plays instantly
    },

    // Effect 6: Sky brightens (sequential, after explosion)
    {
      id: 'sky_brighten',
      type: AnimationEffectType.SKY_CHANGE,
      positions: [],
      params: {
        color: '#87CEEB',
        lightIntensity: 1.0,
        easing: EasingType.EASE_OUT
      },
      startTime: 1300,
      duration: 700
    }
  ]
};
```

### Client Triggers Animation

```typescript
// Player shoots arrow
function shootArrow(shooterPos: Vector3, targetPos: Vector3) {
  // Calculate impact position (where arrow lands)
  const impactPos = targetPos; // Could be raycast result

  // Fill placeholders with actual positions
  const instance = AnimationHelper.createInstance(
    arrowShotTemplate,
    {
      shooter: shooterPos,
      target: targetPos,
      impact: impactPos
    },
    currentPlayerId
  );

  // Play animation locally
  animationPlayer.play(instance.animation);

  // Send to server for broadcast
  networkService.send({
    t: MessageType.ANIMATION_START,
    d: [{
      x: impactPos.x,
      y: impactPos.y,
      z: impactPos.z,
      animation: instance.animation
    }]
  });
}
```

### Server Receives and Broadcasts

```typescript
// Server receives animation from client
onAnimationStart((playerId, animationData) => {
  // Validate animation
  const validation = AnimationHelper.validate(animationData);
  if (!validation.valid) {
    console.error('Invalid animation:', validation.errors);
    return;
  }

  // Broadcast to all players in range
  broadcastToPlayersInRange({
    t: MessageType.ANIMATION_START,
    d: [animationData]
  }, animationData.positions);
});
```

### Other Clients Receive and Play

```typescript
// Client receives animation from server
onAnimationStart((animations) => {
  animations.forEach(animData => {
    animationPlayer.play(animData.animation);
  });
});
```

## Example 2: Door Opening (Simple, Server-Defined)

```typescript
// Server sends complete animation with fixed position
const doorOpenAnimation: AnimationData = {
  name: 'door_open',
  source: { type: 'server' },
  effects: [
    {
      type: AnimationEffectType.ROTATE,
      positions: [
        AnimationHelper.fixedPosition({ x: 10, y: 64, z: 5 })
      ],
      params: {
        from: { y: 0 },
        to: { y: 90 },
        easing: EasingType.EASE_OUT
      },
      startTime: 0,
      duration: 500
    },
    {
      type: AnimationEffectType.PLAY_SOUND,
      positions: [
        AnimationHelper.fixedPosition({ x: 10, y: 64, z: 5 })
      ],
      params: {
        soundPath: '/sounds/door_open.ogg',
        volume: 0.6
      },
      startTime: 0,
      duration: 0
    }
  ]
};

// Server sends to all players
sendToAll({
  t: MessageType.ANIMATION_START,
  d: [{
    x: 10,
    y: 64,
    z: 5,
    animation: doorOpenAnimation
  }]
});
```

## Example 3: Block Break with Particles

```typescript
const blockBreakTemplate: AnimationTemplate = {
  id: 'block_break',
  name: 'Block Break',
  placeholders: ['block'],
  effects: [
    // Crack animation
    {
      type: AnimationEffectType.BLOCK_BREAK,
      positions: [AnimationHelper.placeholder('block')],
      params: {
        stages: 5,
        stageInterval: 100
      },
      startTime: 0,
      duration: 500,
      blocking: true
    },

    // Particle burst
    {
      type: AnimationEffectType.PARTICLES,
      positions: [AnimationHelper.placeholder('block')],
      params: {
        particleCount: 20,
        spread: 1.5,
        velocity: 5
      },
      startTime: 500,
      duration: 1000
    },

    // Break sound
    {
      type: AnimationEffectType.PLAY_SOUND,
      positions: [AnimationHelper.placeholder('block')],
      params: {
        soundPath: '/sounds/stone_break.ogg',
        volume: 0.7
      },
      startTime: 500,
      duration: 0
    }
  ]
};

// Player breaks block
function breakBlock(blockPos: Vector3) {
  const instance = AnimationHelper.createInstance(
    blockBreakTemplate,
    { block: blockPos },
    currentPlayerId
  );

  animationPlayer.play(instance.animation);

  networkService.send({
    t: MessageType.ANIMATION_START,
    d: [{
      x: blockPos.x,
      y: blockPos.y,
      z: blockPos.z,
      animation: instance.animation
    }]
  });
}
```

## Example 4: Area Effect (Multiple Positions)

Lightning strikes multiple targets simultaneously.

```typescript
const lightningStrikeTemplate: AnimationTemplate = {
  id: 'lightning_strike',
  name: 'Lightning Strike',
  placeholders: ['target1', 'target2', 'target3'],
  effects: [
    // Sky flash (global)
    {
      type: AnimationEffectType.FLASH,
      positions: [],
      params: {
        color: '#ffffff',
        lightIntensity: 2.0
      },
      startTime: 0,
      duration: 100
    },

    // Lightning bolt 1
    {
      type: AnimationEffectType.PARTICLES,
      positions: [AnimationHelper.placeholder('target1')],
      params: {
        particleType: 'lightning',
        height: 50
      },
      startTime: 0,
      duration: 200
    },

    // Lightning bolt 2 (slightly delayed)
    {
      type: AnimationEffectType.PARTICLES,
      positions: [AnimationHelper.placeholder('target2')],
      params: {
        particleType: 'lightning',
        height: 50
      },
      startTime: 100,
      duration: 200
    },

    // Lightning bolt 3 (more delayed)
    {
      type: AnimationEffectType.PARTICLES,
      positions: [AnimationHelper.placeholder('target3')],
      params: {
        particleType: 'lightning',
        height: 50
      },
      startTime: 200,
      duration: 200
    },

    // Thunder sound
    {
      type: AnimationEffectType.PLAY_SOUND,
      positions: [],
      params: {
        soundPath: '/sounds/thunder.ogg',
        volume: 1.0
      },
      startTime: 150,
      duration: 0
    }
  ]
};

// Trigger with three targets
const instance = AnimationHelper.createInstance(
  lightningStrikeTemplate,
  {
    target1: { x: 10, y: 70, z: 5 },
    target2: { x: 15, y: 70, z: 8 },
    target3: { x: 12, y: 70, z: 12 }
  },
  'server'
);
```

## Example 5: Parallel vs Sequential Effects

```typescript
// All effects start at same time (parallel)
const parallelAnimation: AnimationData = {
  name: 'parallel_demo',
  effects: [
    { type: AnimationEffectType.SCALE, startTime: 0, duration: 1000, params: {} },
    { type: AnimationEffectType.ROTATE, startTime: 0, duration: 1000, params: {} },
    { type: AnimationEffectType.FADE, startTime: 0, duration: 1000, params: {} }
  ]
};

// Effects execute in sequence (using blocking)
const sequentialAnimation: AnimationData = {
  name: 'sequential_demo',
  effects: [
    {
      type: AnimationEffectType.SCALE,
      startTime: 0,
      duration: 500,
      blocking: true,  // Wait for completion
      params: {}
    },
    {
      type: AnimationEffectType.ROTATE,
      startTime: 500,  // Starts after scale
      duration: 500,
      blocking: true,
      params: {}
    },
    {
      type: AnimationEffectType.FADE,
      startTime: 1000,  // Starts after rotate
      duration: 500,
      params: {}
    }
  ]
};
```

## Animation Timeline Visualization

### Arrow Shot Timeline

```
Time (ms)  0    100   200   300   400   500   600   700   800   900   1000  1100  1200  1300  1400  1500  1600  1700  1800  1900  2000
           |-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|-----|
Projectile [====================================================================================================]
Sky Darken [=======================]
Explosion                                                                                        [===========]
Shake                                                                                            [===========]
Sound                                                                                            *
Sky Bright                                                                                                  [================================]
```

## Helper Functions Usage

### Check Animation Progress

```typescript
const animation: AnimationData = { /* ... */ };
const currentTime = 750;

// Get currently active effects
const activeEffects = AnimationHelper.getActiveEffects(animation, currentTime);
console.log(`Active effects: ${activeEffects.length}`);

// Calculate total duration
const duration = AnimationHelper.calculateDuration(animation);
console.log(`Total duration: ${duration}ms`);
```

### Fill Placeholders

```typescript
// Template with placeholders
const template: AnimationTemplate = {
  id: 'fireball',
  name: 'Fireball',
  placeholders: ['caster', 'target'],
  effects: [
    {
      type: AnimationEffectType.PROJECTILE,
      positions: [
        AnimationHelper.placeholder('caster'),
        AnimationHelper.placeholder('target')
      ],
      startTime: 0,
      duration: 800,
      params: { projectileModel: '/models/fireball.babylon' }
    }
  ]
};

// Fill with actual positions
const filledAnimation = AnimationHelper.fillPlaceholders(template, {
  caster: { x: 0, y: 65, z: 0 },
  target: { x: 10, y: 65, z: 10 }
});

// Now has fixed positions
console.log(filledAnimation.effects[0].positions);
// [
//   { type: 'fixed', position: { x: 0, y: 65, z: 0 } },
//   { type: 'fixed', position: { x: 10, y: 65, z: 10 } }
// ]
```

### Validate Before Sending

```typescript
const animation: AnimationData = { /* ... */ };

const validation = AnimationHelper.validate(animation);
if (!validation.valid) {
  console.error('Animation errors:', validation.errors);
  // Don't send invalid animation
  return;
}

// Safe to send
networkService.send({ t: 'a.s', d: [animation] });
```

### Merge Animations

```typescript
// Combine multiple animations into one timeline
const explosion = createExplosionAnimation();
const smoke = createSmokeAnimation();

// Start smoke 500ms after explosion
const combined = AnimationHelper.merge(
  [explosion, smoke],
  [0, 500]  // Offsets
);

// Play combined animation
animationPlayer.play(combined);
```

## Client-Side Animation Workflow

### 1. Register Templates

```typescript
class AnimationRegistry {
  private templates = new Map<string, AnimationTemplate>();

  register(template: AnimationTemplate) {
    this.templates.set(template.id, template);
  }

  get(id: string): AnimationTemplate | undefined {
    return this.templates.get(id);
  }
}

// Register arrow shot template
animationRegistry.register(arrowShotTemplate);
```

### 2. Trigger Animation

```typescript
function onPlayerShootArrow(targetNPC: Entity) {
  const template = animationRegistry.get('arrow_shot');
  if (!template) return;

  // Get positions
  const shooterPos = player.position;
  const targetPos = targetNPC.position;
  const impactPos = calculateImpactPosition(shooterPos, targetPos);

  // Create instance
  const instance = AnimationHelper.createInstance(template, {
    shooter: shooterPos,
    target: targetPos,
    impact: impactPos
  }, player.id);

  // Play locally immediately
  animationPlayer.play(instance.animation);

  // Send to server for broadcast
  networkService.send({
    t: MessageType.ANIMATION_START,
    d: [{
      x: impactPos.x,
      y: impactPos.y,
      z: impactPos.z,
      animation: instance.animation
    }]
  });
}
```

### 3. Play Animation

```typescript
class AnimationPlayer {
  private activeAnimations = new Map<string, {
    animation: AnimationData;
    startTime: number;
  }>();

  play(animation: AnimationData) {
    const id = `anim_${Date.now()}_${Math.random()}`;

    this.activeAnimations.set(id, {
      animation,
      startTime: Date.now()
    });

    // Calculate duration
    const duration = AnimationHelper.calculateDuration(animation);

    // Remove after completion
    setTimeout(() => {
      this.activeAnimations.delete(id);
    }, duration);
  }

  update(deltaTime: number) {
    const now = Date.now();

    this.activeAnimations.forEach(({ animation, startTime }) => {
      const elapsed = now - startTime;

      // Get currently active effects
      const activeEffects = AnimationHelper.getActiveEffects(animation, elapsed);

      // Execute each effect
      activeEffects.forEach(effect => {
        this.executeEffect(effect, elapsed - effect.startTime);
      });
    });
  }

  private executeEffect(effect: AnimationEffect, localTime: number) {
    switch (effect.type) {
      case AnimationEffectType.PROJECTILE:
        this.executeProjectile(effect, localTime);
        break;
      case AnimationEffectType.EXPLOSION:
        this.executeExplosion(effect, localTime);
        break;
      case AnimationEffectType.SKY_CHANGE:
        this.executeSkyChange(effect, localTime);
        break;
      // ... other effect types
    }
  }

  private executeProjectile(effect: AnimationEffect, localTime: number) {
    if (!effect.positions || effect.positions.length < 2) return;

    const start = (effect.positions[0] as any).position;
    const end = (effect.positions[1] as any).position;
    const duration = effect.duration ?? 1000;

    // Calculate current position along path
    const t = Math.min(1, localTime / duration);
    const currentPos = Vector3Utils.lerp(start, end, t);

    // Update projectile mesh
    projectileMesh.position.set(currentPos.x, currentPos.y, currentPos.z);
  }
}
```

## Example 6: Chain Reaction

Multiple explosions triggering each other.

```typescript
const chainExplosion: AnimationData = {
  name: 'chain_explosion',
  effects: [
    // First explosion
    {
      type: AnimationEffectType.EXPLOSION,
      positions: [AnimationHelper.fixedPosition({ x: 0, y: 64, z: 0 })],
      params: { radius: 3, explosionIntensity: 1.0 },
      startTime: 0,
      duration: 300,
      blocking: true
    },

    // Second explosion (triggered by first)
    {
      type: AnimationEffectType.EXPLOSION,
      positions: [AnimationHelper.fixedPosition({ x: 5, y: 64, z: 5 })],
      params: { radius: 3, explosionIntensity: 1.0 },
      startTime: 300,
      duration: 300,
      blocking: true
    },

    // Third explosion (triggered by second)
    {
      type: AnimationEffectType.EXPLOSION,
      positions: [AnimationHelper.fixedPosition({ x: 10, y: 64, z: 10 })],
      params: { radius: 3, explosionIntensity: 1.0 },
      startTime: 600,
      duration: 300
    }
  ]
};
```

## Position Reference Types

### Fixed Position (Server-defined)

```typescript
const fixedPos: PositionRef = {
  type: 'fixed',
  position: { x: 10, y: 64, z: 5 }
};
```

### Placeholder (Client fills)

```typescript
const placeholderPos: PositionRef = {
  type: 'placeholder',
  name: 'player'
};

// Later filled by client:
const filled = AnimationHelper.fillPlaceholders(template, {
  player: currentPlayerPosition
});
```

## Timeline Calculation

Effects can be defined with:
- **startTime + duration**: `startTime: 0, duration: 500` → ends at 500ms
- **startTime + endTime**: `startTime: 0, endTime: 500` → ends at 500ms
- **blocking**: Wait for effect to complete before next sequential effect

```typescript
const timeline = [
  { startTime: 0, duration: 500, blocking: true },    // 0-500ms, blocks
  { startTime: 500, duration: 300, blocking: false }, // 500-800ms, doesn't block
  { startTime: 500, duration: 1000 },                 // 500-1500ms (parallel with above)
  { startTime: 800, duration: 200 }                   // 800-1000ms
];

// Total duration: 1500ms
```

## Best Practices

### ✅ DO
- Define reusable templates with placeholders
- Use descriptive placeholder names ('player', 'target', 'impact')
- Validate animations before sending to server
- Use blocking for sequential effects
- Set appropriate startTime for parallel effects
- Include source information (server vs client)

### ❌ DON'T
- Don't send animations with unfilled placeholders (unless server-side)
- Don't create overly complex animations (performance)
- Don't forget to broadcast to other players
- Don't mutate animation templates (clone first)
- Don't skip validation

## Network Flow

```
1. Client has template: arrowShotTemplate (with placeholders)
   ↓
2. Player triggers action: shootArrow(targetPos)
   ↓
3. Client fills placeholders with actual positions
   ↓
4. Client plays animation locally (instant feedback)
   ↓
5. Client sends filled animation to server
   ↓
6. Server validates and broadcasts to other players
   ↓
7. Other clients receive and play animation
```

## Summary

- **Timeline-based**: Effects on timeline with parallel/sequential execution
- **Multi-position**: Support for animations involving multiple locations
- **Templates**: Reusable with placeholders
- **Client-fill**: Client fills positions and sends to server
- **Server-broadcast**: Server distributes to all players
- **Type-safe**: Full TypeScript support
- **Flexible**: Extensible effect parameters
