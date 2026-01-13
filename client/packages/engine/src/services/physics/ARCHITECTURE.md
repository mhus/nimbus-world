# Physics System - Architektur

## System-Übersicht

```
┌─────────────────────────────────────────────────────────────────┐
│                        PhysicsService                            │
│  (Koordinator, Entity-Management, Public API)                   │
└────────────┬────────────────────────────────────────────────────┘
             │
             │ update(deltaTime)
             │
             ├──────────────┬──────────────┐
             │              │              │
             ▼              ▼              ▼
    ┌─────────────┐  ┌─────────────┐  ┌──────────┐
    │   Walk      │  │    Fly      │  │ Teleport │
    │ Controller  │  │ Controller  │  │  Check   │
    └──────┬──────┘  └─────────────┘  └──────────┘
           │
           │ doMovement()
           │
           ├─────────────────┬─────────────────┬──────────────┐
           │                 │                 │              │
           ▼                 ▼                 ▼              ▼
    ┌────────────┐    ┌───────────┐    ┌──────────┐   ┌──────────┐
    │  Block     │    │ Movement  │    │Collision │   │ Surface  │
    │  Context   │    │ Resolver  │    │ Detector │   │ Analyzer │
    │ Analyzer   │    └───────────┘    └──────────┘   └──────────┘
    └─────┬──────┘
          │
          │ getContext()
          │
          ▼
    ┌──────────────────────────────────────────────┐
    │         PlayerBlockContext                   │
    │  (8 Block-Kategorien)                        │
    │                                              │
    │  • currentBlocks    • footFrontBlocks        │
    │  • enteringBlocks   • groundBlocks           │
    │  • frontBlocks      • groundFootBlocks       │
    │  • footBlocks       • headBlocks             │
    └──────────────────────────────────────────────┘
```

## Datenfluss

### 1. Input → WishMove

```
User Input
   │
   ├─ KeyW pressed
   ├─ KeyA pressed
   └─ Space pressed
   │
   ▼
InputService
   │
   ├─ moveForward(entity, distance, yaw, pitch)
   ├─ moveRight(entity, distance, yaw)
   └─ jump(entity)
   │
   ▼
PhysicsService
   │
   └─ entity.wishMove.x = sin(yaw) * distance
      entity.wishMove.z = cos(yaw) * distance
```

### 2. WishMove → Velocity

```
entity.wishMove (Absicht)
   │
   ▼
MovementResolver.updateVelocity()
   │
   ├─ Ziel-Velocity berechnen:  vTarget = normalize(wishMove) * speed
   ├─ Approach anwenden:         velocity = approach(velocity, vTarget, accel*dt)
   ├─ Friction anwenden:         velocity *= exp(-k*dt)
   ├─ Resistance anwenden:       velocity *= (1 - resistance)
   └─ Gravitation addieren:      velocity.y += gravity * dt
   │
   ▼
entity.velocity (Zustand)
```

### 3. Velocity → Position

```
entity.velocity
   │
   ▼
wishPosition = position + velocity * dt
   │
   ▼
CollisionDetector.resolveCollision()
   │
   ├─ Y-Kollision (vertical)
   │  ├─ Head collision (Decke)
   │  └─ Ground collision (Boden, Slopes)
   │
   ├─ X,Z-Kollision (horizontal)
   │  ├─ Front blocks check
   │  ├─ PassableFrom logic
   │  ├─ Auto-climbable detection
   │  └─ Wall sliding
   │
   ▼
resolvedPosition
   │
   ▼
entity.position = resolvedPosition
```

## Komponenten-Diagramm

### BlockContextAnalyzer

```
      Entity Position + Dimensions
                │
                ▼
        ┌───────────────────┐
        │  getContext()     │
        └────────┬──────────┘
                 │
      ┌──────────┼──────────┬─────────────┬──────────┐
      │          │           │             │          │
      ▼          ▼           ▼             ▼          ▼
  Footprint  Direction   Y-Levels   Block-Queries  Aggregation
  Positions   (Yaw)     Calculation  (ChunkService)  (OR, MAX)
      │          │           │             │          │
      └──────────┴───────────┴─────────────┴──────────┘
                           │
                           ▼
                  PlayerBlockContext
                  (8 Kategorien)
```

### MovementResolver

```
    wishMove + Context + Resistance
                │
                ▼
        ┌───────────────────┐
        │ updateVelocity()  │
        └────────┬──────────┘
                 │
         ┌───────┼───────┬────────┐
         │               │        │
         ▼               ▼        ▼
    Planar (X,Z)    Vertical   Special
         │            (Y)      Modes
         │                        │
    ┌────┴────┐          ┌────────┼────────┐
    │         │          │        │        │
    ▼         ▼          ▼        ▼        ▼
 Approach  Friction  Gravity  Climb-Y  Fly-Y
    │         │          │        │        │
    └─────────┴──────────┴────────┴────────┘
                     │
                     ▼
               entity.velocity
```

### CollisionDetector

```
    wishPosition + Dimensions
                │
                ▼
        ┌───────────────────┐
        │ resolveCollision()│
        └────────┬──────────┘
                 │
         ┌───────┴───────┐
         │               │
         ▼               ▼
    Vertical        Horizontal
    (Y-Axis)        (X,Z-Axis)
         │               │
    ┌────┴────┐     ┌────┴─────┐
    │         │     │          │
    ▼         ▼     ▼          ▼
  Head    Ground  Front    Current
 Blocks   Blocks  Blocks    Blocks
    │         │     │          │
    │         │     │          │
    ▼         ▼     ▼          ▼
 Ceiling  Ground  Wall  PassableFrom
  Clamp    Clamp   Stop   Barriers
    │         │     │          │
    └─────────┴─────┴──────────┘
                │
                ▼
         resolvedPosition
```

## Zeitlicher Ablauf (Single Frame)

```
Frame Start (t = 0)
│
├─ 1. INPUT PHASE
│  ├─ User drückt Tasten
│  ├─ InputService ruft moveForward(), moveRight(), jump()
│  └─ wishMove wird gesetzt
│
├─ 2. PHYSICS UPDATE (PhysicsService.update())
│  │
│  ├─ For each entity:
│  │  │
│  │  ├─ Dimensions laden
│  │  │  └─ getEntityDimensions(entity) → { height, width, footprint }
│  │  │
│  │  ├─ Controller wählen
│  │  │  ├─ Walk-Modi → WalkModeController
│  │  │  └─ Fly-Modi → FlyModeController
│  │  │
│  │  └─ doMovement(entity, wishMove, startJump, dimensions, dt)
│  │     │
│  │     ├─ A. VORBEREITUNG
│  │     │  ├─ wishMove speichern
│  │     │  └─ Chunks geladen prüfen
│  │     │
│  │     ├─ B. CONTEXT ANALYSE
│  │     │  └─ contextAnalyzer.getContext() → 8 Block-Kategorien
│  │     │
│  │     ├─ C. ENVIRONMENT CHECK
│  │     │  ├─ In solid block? → Push-Up versuchen
│  │     │  └─ PassableFrom prüfen
│  │     │
│  │     ├─ D. BODEN / AUTO-FUNCTIONS
│  │     │  ├─ checkGroundCollision() → grounded state
│  │     │  ├─ autoRotationY anwenden
│  │     │  ├─ autoMove addieren
│  │     │  └─ autoJump setzen
│  │     │
│  │     ├─ E. SEMI-SOLID / SLOPES
│  │     │  ├─ Slope-Kräfte anwenden
│  │     │  └─ Surface-Clamping
│  │     │
│  │     ├─ F. VELOCITY UPDATE
│  │     │  ├─ movementResolver.updateVelocity()
│  │     │  │  ├─ Planar: approach + friction
│  │     │  │  └─ Vertical: gravity
│  │     │  └─ handleJump() → coyote time
│  │     │
│  │     ├─ G. POSITION BERECHNEN
│  │     │  └─ wishPosition = position + velocity * dt
│  │     │
│  │     ├─ H. KOLLISION
│  │     │  └─ collisionDetector.resolveCollision()
│  │     │     ├─ Y-Kollision (head/ground)
│  │     │     └─ XZ-Kollision (walls)
│  │     │
│  │     ├─ I. POSITION ANWENDEN
│  │     │  └─ entity.position = resolvedPosition
│  │     │
│  │     ├─ J. WELTGRENZEN
│  │     │  ├─ clampToWorldBounds()
│  │     │  └─ clampToLoadedChunks()
│  │     │
│  │     └─ K. POST-PROCESSING
│  │        ├─ checkUnderwaterState() (wenn Block geändert)
│  │        └─ Cache invalidieren
│  │
│  └─ wishMove auf Zero zurücksetzen
│
├─ 3. RENDERING
│  └─ RenderService nutzt entity.position
│
└─ Frame End (t = dt)
```

## Modul-Abhängigkeiten

```
PhysicsService
    │
    ├──> WalkModeController
    │       │
    │       ├──> BlockContextAnalyzer
    │       │       │
    │       │       ├──> ChunkService (getBlockAt)
    │       │       └──> SurfaceAnalyzer (getCornerHeights)
    │       │
    │       ├──> CollisionDetector
    │       │       │
    │       │       ├──> BlockContextAnalyzer
    │       │       ├──> SurfaceAnalyzer
    │       │       ├──> ChunkService
    │       │       └──> PhysicsUtils
    │       │
    │       ├──> MovementResolver
    │       │       └──> (keine Dependencies)
    │       │
    │       └──> PhysicsUtils
    │
    ├──> FlyModeController
    │       └──> PhysicsUtils
    │
    └──> AppContext (WorldInfo, PlayerInfo)
```

## Konfigurations-Parameter

### PhysicsConfig

Zentrale Physics-Konstanten in `MovementResolver`:

```typescript
interface PhysicsConfig {
  // Gravitation
  gravity: -20.0,              // blocks/s² (normal)
  underwaterGravity: -2.0,     // blocks/s² (10% von normal)

  // Acceleration
  groundAcceleration: 100.0,   // blocks/s² (schnelle Reaktion)
  airAcceleration: 10.0,       // blocks/s² (limitierte Luft-Kontrolle)

  // Friction
  groundFriction: 6.0,         // Friction-Koeffizient (Boden)
  airFriction: 0.1,            // Friction-Koeffizient (Luft)

  // Jump & Climb
  jumpSpeed: 8.0,              // blocks/s (Jump-Geschwindigkeit)
  maxClimbHeight: 0.1,         // blocks (max Auto-Climb)
  coyoteTime: 0.1,             // seconds (Jump-Toleranz nach Boden-Verlassen)
}
```

### Default Entity-Dimensionen

Für Non-Player-Entities:

```typescript
{
  height: 1.8,      // blocks
  width: 0.6,       // blocks
  footprint: 0.3,   // blocks (radius)
}
```

### Default Player-Dimensionen

In `DefaultPlayerInfo.ts`:

```typescript
dimensions: {
  walk:    { height: 2.0, width: 0.6, footprint: 0.3 },
  sprint:  { height: 2.0, width: 0.6, footprint: 0.3 },
  crouch:  { height: 1.0, width: 0.6, footprint: 0.3 }, // ← Niedriger!
  swim:    { height: 1.8, width: 0.6, footprint: 0.3 },
  climb:   { height: 1.8, width: 0.6, footprint: 0.3 },
  fly:     { height: 1.8, width: 0.6, footprint: 0.3 },
  teleport:{ height: 1.8, width: 0.6, footprint: 0.3 },
}
```

## State Machine

### Movement Mode Transitions

```
                    ┌─────────┐
                    │   FLY   │◄─────────────────┐
                    └────┬────┘                  │
                         │                       │
                         │ Editor F-Key          │ Editor F-Key
                         │                       │
    ┌────────────────────┼───────────────────┐   │
    │                    ▼                   │   │
    │  ┌──────┐      ┌──────┐      ┌──────┐ │   │
    │  │CROUCH│◄────►│ WALK │◄────►│SPRINT│ │   │
    │  └──────┘ Ctrl └───┬──┘ Shift└──────┘ │   │
    │    Ground           │          Ground  │   │
    │    Modes            │          Modes   │   │
    │                     │                  │   │
    │                     │ In Water         │   │
    │                     ▼                  │   │
    │                 ┌──────┐               │   │
    │                 │ SWIM │               │   │
    │                 └───┬──┘               │   │
    │                     │                  │   │
    │                     │ On Ladder        │   │
    │                     ▼                  │   │
    │                 ┌──────┐               │   │
    │                 │CLIMB │               │   │
    │                 └──────┘               │   │
    └─────────────────────────────────────────┘   │
                                                  │
                    ┌──────────┐                  │
                    │ TELEPORT │──────────────────┘
                    └──────────┘
                   Chunk-Wait Mode
```

### Entity State Flags

```
┌─────────────────────────────────────────────────────┐
│                  PhysicsEntity                      │
│                                                     │
│  grounded: boolean     ◄─── checkGroundCollision() │
│  onSlope: boolean      ◄─── groundFootBlocks       │
│  inWater: boolean      ◄─── checkUnderwaterState() │
│  canAutoJump: boolean  ◄─── footBlocks/groundBlocks│
│                                                     │
└─────────────────────────────────────────────────────┘
```

## Block-Properties Mapping

Wie Block-Properties auf Physics-Verhalten mappen:

```
BlockModifier.physics
    │
    ├─ solid: boolean
    │    │
    │    ├─ true  → Kollision, nicht passierbar
    │    └─ false → Kein Kollision, passierbar
    │
    ├─ passableFrom: Direction (Bitfield)
    │    │
    │    ├─ + solid=true  → One-Way Gate
    │    │    └─ Eintritt nur von passableFrom-Seiten
    │    │
    │    └─ + solid=false → Dünne Wand / Barriere
    │         └─ Grenzen die nicht überschritten werden können
    │
    ├─ cornerHeights: [NW, NE, SE, SW]
    │    │
    │    └─ + solid=true → Semi-Solid (Slope)
    │         ├─ Surface-Interpolation
    │         ├─ Slope-Sliding
    │         └─ Auto-Climbable wenn <= maxClimbHeight
    │
    ├─ autoCornerHeights: boolean
    │    │
    │    └─ true → Derive cornerHeights from offsets
    │         └─ Block.offsets[19,22,16,13] (Y-Werte der oberen Ecken)
    │
    ├─ resistance: number (0-1)
    │    │
    │    └─ Geschwindigkeits-Reduktion auf Boden
    │         └─ velocity *= (1 - resistance)
    │
    ├─ climbable: number
    │    │
    │    └─ > 0 → Ladder-Funktionalität
    │         ├─ mode → climb
    │         └─ Vertical movement aktiv
    │
    ├─ autoClimbable: boolean
    │    │
    │    └─ true → Auto step-up (1 block max)
    │
    ├─ autoMove: Vector3
    │    │
    │    └─ Conveyor Belt / Strömung
    │         └─ velocity += autoMove * dt
    │
    ├─ autoOrientationY: number (radians)
    │    │
    │    └─ Erzwungene Rotation (Drehscheibe)
    │         └─ rotation.y → lerp → targetYaw
    │
    └─ autoJump: boolean
         │
         └─ true → Auto-Sprung beim Betreten
              └─ startJump = true
```

## Performance-Charakteristiken

### Complexity

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| getContext() | O(n) | n = footprint-corners × height × 8-categories |
| resolveCollision() | O(m) | m = blocks in movement path |
| updateVelocity() | O(1) | Konstante Zeit |
| checkUnderwaterState() | O(1) | Direct chunk lookup |

### Optimierungen

1. **Context Caching**: 100ms cache, invalidiert bei Block-Änderung
2. **Lazy Evaluation**: Underwater-Check nur bei Block-Wechsel
3. **Pre-Merged Modifiers**: Keine Registry-Lookups
4. **Single-Pass Aggregation**: Properties während Sammlung kombiniert
5. **Footprint Deduplication**: Eliminiert doppelte Block-Queries

### Memory Usage

Pro Entity:
- `PhysicsEntity`: ~200 bytes
- `PlayerBlockContext` (cached): ~2-5 KB (abhängig von Block-Anzahl)
- `Coyote Time Tracking`: ~16 bytes

Für 100 Entities: ~500 KB total

## Erweiterbarkeit

### Neue Physics-Property hinzufügen

1. **Type erweitern** (`BlockModifier.ts`):
```typescript
interface PhysicsModifier {
  newProperty?: T;
}
```

2. **Context aggregieren** (`BlockContextAnalyzer.ts`):
```typescript
let newProperty = defaultValue;
for (const block of blocks) {
  if (block.physics?.newProperty) {
    newProperty = aggregate(newProperty, block.physics.newProperty);
  }
}
```

3. **Context-Type erweitern** (`BlockContext.ts`):
```typescript
groundBlocks: {
  // ...
  newProperty: T;
}
```

4. **Nutzen** (`WalkModeController.ts`):
```typescript
if (context.groundBlocks.newProperty) {
  // Custom logic
}
```

### Neue Block-Kategorie hinzufügen

1. **Context erweitern** (`BlockContext.ts`):
```typescript
interface PlayerBlockContext {
  // ...
  newCategory: {
    blocks: BlockInfo[];
    // ... properties
  };
}
```

2. **Analyse implementieren** (`BlockContextAnalyzer.ts`):
```typescript
private analyzeNewCategory(footprint, y) {
  const blocks = [];
  // ... sammeln und aggregieren
  return { blocks, ... };
}
```

3. **In buildContext() einbinden**:
```typescript
const newCategory = this.analyzeNewCategory(footprint, y);
return { ..., newCategory };
```

4. **Nutzen** (in Controller):
```typescript
if (context.newCategory.someProperty) {
  // Logic
}
```

## Migration Notes

### Von Alt zu Neu

**Alte API** (deprecated):
```typescript
// NICHT mehr nutzen:
physicsService.updateWalkMode(entity, dt);
physicsService.analyzeSurface(entity);
physicsService.checkGroundCollision(entity);

// Position direkt setzen (BAD)
entity.position.x += dx;
```

**Neue API**:
```typescript
// Nutzen:
physicsService.moveForward(entity, distance, yaw, pitch);
physicsService.jump(entity);

// wishMove wird automatisch im update() verarbeitet
// Position wird von Controller gesetzt
```

### Breaking Changes

1. **isOnGround → grounded**: Alle Referenzen aktualisiert
2. **MovementMode erweitert**: 'walk' | 'fly' → 7 Modi
3. **PlayerInfo.dimensions**: Neues Required-Field
4. **PhysicsEntity erweitert**: wishMove, lastBlockPos, etc. hinzugefügt

### Backwards Compatibility

- PhysicsEntity und MovementMode werden re-exported
- Public API bleibt stabil (moveForward, jump, etc.)
- Fly-Mode funktioniert weiterhin
- Teleport-System kompatibel

## Future Work

### Geplante Features

- [ ] **Swimming Physics**: Richtige Wasser-Physik (Buoyancy, Drag)
- [ ] **Riding System**: Mount/Vehicle Physics
- [ ] **Multiplayer Prediction**: Client-Side Prediction + Reconciliation
- [ ] **Physics Materials**: Unterschiedliche Friction pro Block-Material
- [ ] **Dynamic Slopes**: Slopes die sich ändern können
- [ ] **Advanced Climbing**: Wand-Klettern, Hängen
- [ ] **Crawl Mode**: Unter Hindernissen kriechen
- [ ] **Ragdoll Physics**: Bei Tod/Knockback

### Mögliche Optimierungen

- [ ] Spatial Hashing für Block-Queries
- [ ] Parallel Physics-Update (Worker Threads)
- [ ] Frustum Culling für Entity-Updates
- [ ] Physics LOD (weit entfernte Entities vereinfachen)
- [ ] Incremental Context-Update (nur geänderte Kategorien)

## Credits

**System Design**: Basierend auf Source-Engine Movement
**Implementation**: Modulares Design mit klarer Separation of Concerns
**Date**: 2025-11-11
**Version**: 2.0
