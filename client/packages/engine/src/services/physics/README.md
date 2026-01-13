# Physics System - Dokumentation

Modulares Physics-System für die Nimbus Voxel Engine mit Source-Engine-Style Bewegungs-Physik.

## Übersicht

Das Physics-System ist in spezialisierte Module aufgeteilt, die jeweils eine klare Verantwortlichkeit haben:

```
physics/
├── types/                      # Type-Definitionen
│   ├── PhysicsEntity.ts       # Entity-Interface, MovementMode
│   ├── BlockContext.ts        # Block-Kontext Strukturen
│   ├── SurfaceState.ts        # Surface-Analyse Ergebnisse
│   └── ForceState.ts          # Kräfte-Akkumulation
│
├── PhysicsUtils.ts            # Pure utility functions
├── SurfaceAnalyzer.ts         # Surface/Slope-Analyse
├── BlockContextAnalyzer.ts    # Block-Kontext-Analyse
├── MovementResolver.ts        # Velocity & Kräfte-Berechnung
├── CollisionDetector.ts       # Kollisions-Erkennung
├── WalkModeController.ts      # Walk/Ground Physics
└── FlyModeController.ts       # Fly/Creative Physics
```

## Architektur-Prinzipien

### 1. Trennung von Absicht und Zustand

**Absicht (wishMove)**: Was der Spieler tun möchte (Input)
```typescript
entity.wishMove = new Vector3(1, 0, 0); // Möchte nach rechts
```

**Zustand (velocity)**: Tatsächliche physikalische Geschwindigkeit
```typescript
entity.velocity = new Vector3(0.5, -2.0, 0); // Bewegt sich langsam rechts, fällt
```

Die Velocity wird vom System basierend auf wishMove, Gravitation, Friction, etc. berechnet.

### 2. Source-Engine-Style Movement

Basiert auf dem Bewegungs-System aus Quake/Half-Life:

- **Approach**: Smooth acceleration zu Ziel-Geschwindigkeit
- **Exponential Decay**: Realistische Friction/Widerstand
- **Ground/Air Trennung**: Unterschiedliche Physik auf Boden vs. in Luft
- **Coyote Time**: Sprung noch kurz nach Verlassen des Bodens möglich

### 3. Modulare Komponenten

Jede Klasse hat eine klare, fokussierte Aufgabe:
- **Analysen** → BlockContextAnalyzer, SurfaceAnalyzer
- **Berechnungen** → MovementResolver
- **Kollisionen** → CollisionDetector
- **Modi** → WalkModeController, FlyModeController

## Kern-Komponenten

### PhysicsEntity

Zentrale Entity-Struktur für alle Objekte mit Physik:

```typescript
interface PhysicsEntity {
  // Position & Rotation
  position: Vector3;          // Weltposition
  rotation: Vector3;          // Euler-Winkel (x: pitch, y: yaw, z: roll)

  // Bewegung
  velocity: Vector3;          // Physikalischer Zustand (m/s)
  wishMove: Vector3;          // Bewegungs-Absicht (Input)

  // Zustand
  movementMode: MovementMode; // walk, sprint, crouch, swim, climb, fly, teleport
  grounded: boolean;          // Auf Boden
  onSlope: boolean;           // Auf Schräge
  inWater: boolean;           // Unter Wasser
  autoJump: number;       // Auto-Jump verfügbar mit value

  // Optimierung
  lastBlockPos: Vector3;      // Für Cache-Invalidierung
  cachedContext?: PlayerBlockContext; // Gecachter Block-Kontext

  // Spezial
  climbState?: ClimbState;    // Für sanfte Climb-Animation
  entityId: string;           // Eindeutige ID
}
```

### MovementMode

7 verschiedene Bewegungs-Modi:

- **walk**: Normal gehen (Standard)
- **sprint**: Schnelles Laufen
- **crouch**: Kriechen (1 Block hoch)
- **swim**: Schwimmen/Unterwasser
- **climb**: Klettern (Leitern)
- **fly**: Kreativ-Modus (kein Gravity, kein Collision)
- **teleport**: Teleport-Modus (wartet auf Chunks)

Jeder Modus hat eigene Dimensionen in PlayerInfo.dimensions.

## Block-Kontext System

### PlayerBlockContext

8 Block-Kategorien die den Spieler umgeben:

```typescript
interface PlayerBlockContext {
  currentBlocks: {        // Blöcke die Player besetzt (Körper-Raum)
    blocks: BlockInfo[];
    allNonSolid: boolean;
    hasSolid: boolean;
    passableFrom: number | undefined;
  };

  enteringBlocks: {       // Blöcke die Player betritt (bei Grenzüberschreitung)
    blocks: BlockInfo[];
    allPassable: boolean;
    hasSolid: boolean;
  };

  frontBlocks: {          // Blöcke vor Player (Bewegungsrichtung)
    blocks: BlockInfo[];
    allPassable: boolean;
    hasSolid: boolean;
  };

  footBlocks: {           // Blöcke bei Füßen (für Auto-Funktionen)
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasAutoRotationY: boolean;
    hasAutoMove: boolean;
    autoJump: number;
    autoOrientationY: number | undefined;
    autoMove: { x, y, z };
  };

  footFrontBlocks: {      // Blöcke vor Füßen (für Klettern/Slopes)
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasClimbable: boolean;
    maxClimbHeight: number;
    cornerHeights?: [number, number, number, number];
  };

  groundBlocks: {         // Blöcke unter Player (für Gravitation)
    blocks: BlockInfo[];
    hasSolid: boolean;
    hasGround: boolean;
    groundY: number;
    resistance: number;
    hasAutoMove: boolean;
    hasAutoRotationY: boolean;
    autoJump: number;
    autoMove: { x, y, z };
    autoOrientationY: number | undefined;
  };

  groundFootBlocks: {     // Blöcke auf Fuß-Level (für Slope-Sliding)
    blocks: BlockInfo[];
    isSemiSolid: boolean;
    maxHeight: number;
    cornerHeights?: [number, number, number, number];
  };

  headBlocks: {           // Blöcke über Kopf (Decken-Kollision)
    blocks: BlockInfo[];
    hasSolid: boolean;
    maxY: number;
  };
}
```

### Block-Kategorien Illustration

```
      Y
      │
      │   [headBlocks]         ← Kopf-Höhe (ceiling collision)
      │   ╔═══════╗
      │   ║       ║
      │   ║  👤   ║            ← currentBlocks (Körper)
      │   ║ /│\  ║
      │   ╚═══════╝
      │   [footBlocks]         ← Fuß-Niveau (auto-functions)
  ────┼──────────────── ←──────  groundFootBlocks (slopes)
      │   [groundBlocks]       ← Boden (gravity, resistance)
      │
      └────────────────── X/Z

  [frontBlocks] →              Vor dem Spieler
  [footFrontBlocks] →          Vor den Füßen (climbing)
  [enteringBlocks]             Bei Grenzüberschreitung
```

## Bewegungs-Ablauf (doMovement Schema)

Das `WalkModeController.doMovement()` implementiert folgendes Schema:

### 1. Vorbereitung
```typescript
// Absicht speichern
entity.wishMove.copyFrom(movementVector);

// Chunks geladen?
if (!chunksLoaded) return;
```

### 2. Environment prüfen
```typescript
// Im solid Block stecken?
if (currentBlocks.hasSolid) {
  // PassableFrom prüfen
  // Ggf. Push-Up versuchen
}
```

### 3. Bodenprüfung / Auto-Funktionen
```typescript
// Grounded-State aktualisieren
collisionDetector.checkGroundCollision(entity);

// Auto-Functions anwenden (wenn grounded oder footBlocks solid)
if (grounded || footBlocks.hasSolid) {
  // Auto-Rotation
  if (footBlocks.hasAutoRotationY) {
    movementResolver.applyAutoOrientation(entity, targetYaw, deltaTime);
  }

  // Auto-Move (Conveyors)
  if (footBlocks.hasAutoMove) {
    movementResolver.applyAutoMove(entity, autoMove, deltaTime);
  }

  // Auto-Jump
  if (footBlocks.autoJump > 0) {
    entity.autoJump = footBlocks.autoJump;
    startJump = true;
  }
}
```

### 4. Semi-Solid & Slopes
```typescript
// Auf Slope?
if (onSlope && groundFootBlocks.cornerHeights) {
  // Slope-Kräfte anwenden
  const slope = surfaceAnalyzer.calculateSlope(cornerHeights);
  movementResolver.applySlopeForces(entity, slope, deltaTime);

  // An Surface clampen
  if (groundFootBlocks.maxHeight > 0) {
    entity.position.y = Math.floor(y) + 1.0 + maxHeight;
  }
}
```

### 5. Bewegung / Kollision
```typescript
// Velocity berechnen
movementResolver.updateVelocity(entity, wishMove, context, resistance, deltaTime);

// Sprung behandeln
movementResolver.handleJump(entity, startJump, deltaTime);

// Nächste Position berechnen
const wishPosition = position + velocity * deltaTime;

// Kollisionen auflösen (Swept-AABB: Y → X → Z)
const resolved = collisionDetector.resolveCollision(entity, wishPosition, dimensions);
```

### 6. Weltgrenzen
```typescript
// Position anwenden
entity.position = resolved;

// World Bounds
PhysicsUtils.clampToWorldBounds(entity, appContext);

// Chunk Bounds
PhysicsUtils.clampToLoadedChunks(entity, ...);
```

### 7. Post-Processing
```typescript
// Unterwasser-Check (nur wenn Block geändert)
if (hasBlockPositionChanged(entity)) {
  checkUnderwaterState(entity, ...);
  contextAnalyzer.invalidateCache(entityId);
}
```

## Velocity-Berechnung (Source-Style)

### Horizontale Bewegung (X, Z)

```typescript
// 1. Ziel-Velocity aus wishMove berechnen
const wishDir = normalize(wishMove.xz);
const vTarget = wishDir * maxSpeed * clamp(|wishMove|, 0, 1);

// 2. Approach zu Ziel (smooth acceleration)
const accel = grounded ? groundAcceleration : airAcceleration;
velocity.xz = approach(velocity.xz, vTarget, accel * dt);

// 3. Friction anwenden (exponential decay)
const k = grounded ? groundFriction : airFriction;
velocity.xz *= exp(-k * dt);

// 4. Resistance vom Boden
velocity.xz *= (1 - resistance);
```

**Parameter**:
- `groundAcceleration = 100.0` - Schnelle Reaktion auf Boden
- `airAcceleration = 10.0` - Limitierte Luft-Kontrolle
- `groundFriction = 6.0` - Starke Boden-Reibung
- `airFriction = 0.1` - Minimale Luft-Reibung

### Vertikale Bewegung (Y)

```typescript
// Gravitation (wenn nicht grounded und nicht climb/fly)
if (!grounded && mode != climb && mode != fly) {
  const g = inWater ? underwaterGravity : gravity;
  velocity.y += g * dt;
}

// Terminal Velocity
velocity.y = clamp(velocity.y, -50.0, 50.0);

// Climb Mode: Direkte Kontrolle
if (mode == climb) {
  velocity.y = wishMove.y * moveSpeed;
}

// Fly Mode: Direkte Kontrolle
if (mode == fly) {
  velocity.y = wishMove.y * moveSpeed;
}
```

### Sprung (Coyote Time)

```typescript
// Coyote Time Tracking
if (grounded) {
  timeSinceGrounded = 0;
} else {
  timeSinceGrounded += dt;
}

// Sprung erlauben wenn grounded ODER innerhalb Coyote Time
if (startJump && timeSinceGrounded <= coyoteTime) {
  velocity.y = jumpSpeed;
  grounded = false;
}
```

**Coyote Time = 0.1s**: Erlaubt Sprung kurz nach Verlassen des Bodens (besseres Game-Feel).

## Kollisions-System

### Swept-AABB (Y → X → Z)

Kollisionen werden in 3 Phasen aufgelöst:

```typescript
function resolveCollision(entity, wishPosition, dimensions) {
  let resolved = wishPosition.clone();

  // Phase 1: Vertikale Kollision (Y-Achse)
  resolved.y = resolveVerticalCollision(entity, resolved, dimensions);

  // Phase 2: Horizontale Kollision (X, Z)
  const horizontal = resolveHorizontalCollision(entity, resolved, dimensions);
  resolved.x = horizontal.x;
  resolved.z = horizontal.z;

  return resolved;
}
```

**Warum Y → X → Z?**
- Gravitation ist wichtigste Kraft (zuerst auflösen)
- Verhindert "Durch-Boden-Fallen"
- Erlaubt sauberes Sliding an Wänden

### PassableFrom Logic

PassableFrom hat zwei unterschiedliche Bedeutungen je nach Block-Typ:

#### Fall 1: Solid Block + passableFrom = **One-Way Gate**

```typescript
// Block: solid=true, passableFrom=NORTH|SOUTH
//
// Von NORTH/SOUTH: ✅ Kann eintreten
// Von EAST/WEST: ❌ Blockiert
// Von innen: ✅ Kann immer austreten (wenn Nachbar-Block passierbar)
```

**Beispiel**: Eingang nur von vorne/hinten, Seiten blockiert.

```
     NORTH
       ↓
    ┌─────┐
WEST│ ░░░ │EAST  ← passableFrom = NORTH | SOUTH
    └─────┘
     SOUTH
```

#### Fall 2: Non-Solid Block + passableFrom = **Dünne Wand**

```typescript
// Block: solid=false, passableFrom=NORTH|SOUTH
//
// passableFrom definiert ERLAUBTE Richtungen
// Spieler kann Grenze in nicht-erlaubten Richtungen NICHT überschreiten
// (weder von innen noch von außen)
```

**Beispiel**: Dünne Wand (< 1 Block dick), Glasscheibe, Barriere.

```typescript
// Wand läuft Nord-Süd (passableFrom = NORTH | SOUTH)
// Spieler kann Wand in X-Richtung (EAST/WEST) NICHT durchqueren

   NORTH
     ║  ← Dünne Wand (non-solid, passableFrom=N|S)
     ║
   SOUTH
```

**Implementierung**:
```typescript
// Eintritt prüfen
function canEnterFrom(passableFrom, entrySide, isSolid) {
  if (!passableFrom) return !isSolid; // Default
  return hasDirection(passableFrom, entrySide);
}

// Austritt prüfen
function canLeaveTo(passableFrom, exitDir, isSolid) {
  if (!passableFrom) return true; // Default
  if (isSolid) return true; // Solid: immer austreten erlaubt
  return hasDirection(passableFrom, exitDir); // Non-solid: Barrier
}
```

### Semi-Solid Blöcke (Slopes)

Ein Block ist **semi-solid** wenn:
- `solid = true` UND
- `cornerHeights` gesetzt ODER
- `autoCornerHeights = true` + `offsets` definiert

**Corner Heights**: `[NW, NE, SE, SW]`

```
Corner-Layout:
  NW(-X,-Z)  NE(+X,-Z)
      [0]────────[1]
       │          │
       │          │
      [3]────────[2]
  SW(-X,+Z)  SE(+X,+Z)
```

**Slope Sliding**:
```typescript
// Slope-Vektor berechnen
const westHeight = (cornerHeights[0] + cornerHeights[3]) / 2;
const eastHeight = (cornerHeights[1] + cornerHeights[2]) / 2;
const slopeX = eastHeight - westHeight;

const northHeight = (cornerHeights[0] + cornerHeights[1]) / 2;
const southHeight = (cornerHeights[3] + cornerHeights[2]) / 2;
const slopeZ = southHeight - northHeight;

// Slope-Kraft anwenden
const slideForce = 5.0; // blocks/s² pro Einheit Slope
velocity.x += slopeX * slideForce * dt;
velocity.z += slopeZ * slideForce * dt;
```

**Surface Clamping**:
```typescript
// Spieler auf Slope-Surface clampen
const surfaceY = blockY + 1.0 + interpolateHeight(cornerHeights, localX, localZ);
if (entity.position.y < surfaceY) {
  entity.position.y = surfaceY;
}
```

**Auto-Climbable**:
```typescript
// Wenn Höhen-Unterschied <= maxClimbHeight (default: 0.1 blocks)
if (maxHeight <= 0.1) {
  // Automatisch "drüber steigen"
  // Keine Blockierung, Y-Anpassung erfolgt automatisch
}
```

## Auto-Funktionen

Blöcke können spezielle Verhaltensweisen auslösen:

### autoMove (Conveyors, Strömungen)

```typescript
// Block-Property
physics: {
  autoMove: { x: 2.0, y: 0, z: 0 }  // Bewegung nach Osten
}

// Anwendung (additiv zu velocity)
entity.velocity.x += autoMove.x * deltaTime;
entity.velocity.y += autoMove.y * deltaTime;
entity.velocity.z += autoMove.z * deltaTime;
```

### autoOrientationY (Drehscheiben)

```typescript
// Block-Property
physics: {
  autoOrientationY: 1.57  // 90 Grad in Radians
}

// Anwendung (smooth rotation)
const diff = targetYaw - entity.rotation.y;
const normalized = atan2(sin(diff), cos(diff));
const maxRotation = turnSpeed * deltaTime;

if (abs(normalized) < maxRotation) {
  entity.rotation.y = targetYaw;
} else {
  entity.rotation.y += sign(normalized) * maxRotation;
}
```

### autoJump (Trampoline)

```typescript
// Block-Property
physics: {
  autoJump: true
}

// Wenn Spieler auf/in autoJump-Block
if (footBlocks.autoJump > 0 || groundBlocks.autoJump > 0) {
  entity.autoJump = true;
  startJump = true; // Automatischer Sprung
}
```

## Module-Details

### BlockContextAnalyzer

**Verantwortung**: Analysiert alle Blöcke um eine Entity herum.

**Hauptmethode**:
```typescript
getContext(
  entity: PhysicsEntity,
  dimensions: { height, width, footprint }
): PlayerBlockContext
```

**Prozess**:
1. Footprint-Positionen berechnen (4 Ecken basierend auf footprint-Radius)
2. Front-Direction aus Yaw ermitteln
3. Y-Levels berechnen (feet, ground, head)
4. Alle 8 Block-Kategorien sammeln
5. Properties aggregieren (OR für passableFrom, MAX für resistance, etc.)

**Caching**:
- Context wird gecached (100ms timeout)
- Invalidiert wenn `entity.lastBlockPos` sich ändert
- Spart Block-Queries

### SurfaceAnalyzer

**Verantwortung**: Analysiert Oberflächen und Slopes.

**Methoden**:
```typescript
getCornerHeights(block: ClientBlock): [number, number, number, number] | undefined
```
Prioritäten-Kaskade:
1. Block.cornerHeights (höchste Priorität)
2. PhysicsModifier.cornerHeights
3. Auto-derived from Block.offsets (wenn autoCornerHeights=true)
4. Auto-derived from VisibilityModifier.offsets
5. undefined (kein Slope)

```typescript
getBlockSurfaceHeight(block: ClientBlock, worldX, worldZ): number
```
Bilineare Interpolation zwischen 4 Ecken:
```
heightNorth = heightNW + (heightNE - heightNW) * localX
heightSouth = heightSW + (heightSE - heightSW) * localX
surfaceHeight = heightNorth + (heightSouth - heightNorth) * localZ
```

```typescript
calculateSlope(cornerHeights): { x, z }
```
Slope-Vektor:
- X: Durchschnitt West-Seite vs. Ost-Seite
- Z: Durchschnitt Nord-Seite vs. Süd-Seite

```typescript
isSemiSolid(block): boolean
```
Prüft ob Block corner heights hat.

### MovementResolver

**Verantwortung**: Velocity-Berechnung und Kräfte.

**Velocity Update (Source-Style)**:
```typescript
updateVelocity(entity, wishMove, context, resistance, deltaTime) {
  // Planar (X, Z)
  const maxSpeed = getMoveSpeed(entity);
  const effectiveSpeed = maxSpeed * (1 - resistance);

  const vTarget = normalize(wishMove.xz) * effectiveSpeed * |wishMove|;
  const accel = grounded ? groundAccel : airAccel;
  velocity.xz = approach(velocity.xz, vTarget, accel * dt);

  const friction = grounded ? groundFriction : airFriction;
  velocity.xz *= exp(-friction * dt);

  // Vertical (Y)
  if (!grounded && mode != climb && mode != fly) {
    velocity.y += (inWater ? underwaterGravity : gravity) * dt;
  }

  // Terminal velocity
  velocity.y = clamp(velocity.y, -50, 50);
}
```

**Jump Handling**:
```typescript
handleJump(entity, startJump, deltaTime) {
  // Coyote time tracking
  if (grounded) {
    timeSinceGrounded = 0;
  } else {
    timeSinceGrounded += deltaTime;
  }

  // Jump wenn grounded ODER innerhalb coyote time
  if (startJump && timeSinceGrounded <= coyoteTime) {
    entity.velocity.y = jumpSpeed;
    entity.grounded = false;
  }
}
```

**Slope Forces**:
```typescript
applySlopeForces(entity, slopeVector, deltaTime) {
  const slideForce = 5.0; // blocks/s² per unit slope
  entity.velocity.x += slopeVector.x * slideForce * deltaTime;
  entity.velocity.z += slopeVector.z * slideForce * deltaTime;
}
```

**Auto-Move**:
```typescript
applyAutoMove(entity, autoMove, deltaTime) {
  entity.velocity.x += autoMove.x * deltaTime;
  entity.velocity.y += autoMove.y * deltaTime;
  entity.velocity.z += autoMove.z * deltaTime;
}
```

**Auto-Orientation**:
```typescript
applyAutoOrientation(entity, targetYaw, deltaTime) {
  const turnSpeed = entity.playerInfo.effectiveTurnSpeed;
  const diff = targetYaw - entity.rotation.y;
  const normalized = atan2(sin(diff), cos(diff)); // -PI to PI
  const maxRotation = turnSpeed * deltaTime;

  if (abs(normalized) < maxRotation) {
    entity.rotation.y = targetYaw;
  } else {
    entity.rotation.y += sign(normalized) * maxRotation;
  }
}
```

### CollisionDetector

**Verantwortung**: Kollisions-Erkennung und Auflösung.

**Swept-AABB Collision**:
```typescript
resolveCollision(entity, wishPosition, dimensions): Vector3 {
  let resolved = wishPosition.clone();

  // Y-Achse (vertical)
  resolved.y = resolveVerticalCollision(entity, resolved, dimensions);

  // X, Z Achsen (horizontal)
  const horizontal = resolveHorizontalCollision(entity, resolved, dimensions);
  resolved.x = horizontal.x;
  resolved.z = horizontal.z;

  return resolved;
}
```

**Vertical Collision**:
```typescript
resolveVerticalCollision(entity, wishPosition, dimensions) {
  if (movingUp) {
    // Head Collision (Decke)
    if (headBlocks.hasSolid) {
      const ceilingY = headBlocks.maxY;
      if (wishY + height > ceilingY) {
        entity.velocity.y = 0;
        return ceilingY - height;
      }
    }
  } else {
    // Ground Collision
    if (groundBlocks.hasGround) {
      const groundY = groundBlocks.groundY + 1.0;
      if (wishY < groundY) {
        entity.velocity.y = 0;
        entity.grounded = true;
        return groundY;
      }
    }

    // Semi-Solid (Slopes)
    if (groundFootBlocks.isSemiSolid) {
      const surfaceY = floor(y) + 1.0 + maxHeight;
      if (wishY < surfaceY) {
        entity.velocity.y = 0;
        entity.grounded = true;
        entity.onSlope = true;
        return surfaceY;
      }
    }
  }

  return wishY;
}
```

**Horizontal Collision**:
```typescript
resolveHorizontalCollision(entity, wishPosition, dimensions) {
  const frontBlocks = getFrontBlocks(entity, dimensions, dx, dz);

  for (const block of frontBlocks) {
    if (!block.solid) continue;

    const dir = getMovementDirection(dx, dz);

    // PassableFrom Check (One-Way Gate)
    if (passableFrom && !canEnterFrom(passableFrom, dir, true)) {
      // Blockiert - Bewegung stoppen
      stopMovement(dominantAxis);
      break;
    }

    // Auto-Climbable Check
    if (cornerHeights && max(cornerHeights) <= maxClimbHeight) {
      // Kann drüber - erlauben
      continue;
    }

    // Regular Collision
    stopMovement(dominantAxis);
    break;
  }

  // Current Block PassableFrom (Thin Wall)
  if (currentBlocks.passableFrom) {
    if (!canLeaveTo(passableFrom, exitDir, isSolid)) {
      // Wand-Barriere - Bewegung stoppen
      stopMovement(dominantAxis);
    }
  }

  return { x, z };
}
```

**Push-Up (Stuck Prevention)**:
```typescript
checkAndPushUp(entity, dimensions) {
  if (currentBlocks.hasSolid && !currentBlocks.allNonSolid) {
    // Im solid Block - Push up wenn Platz
    if (!headBlocks.hasSolid) {
      entity.position.y += 1.0;
      entity.velocity.y = 0;
      return true;
    }
  }
  return false;
}
```

### PhysicsUtils

**Verantwortung**: Stateless Helper-Funktionen.

**Wichtigste Funktionen**:

```typescript
// Bewegungs-Richtung ermitteln
getMovementDirection(dx, dz): Direction

// PassableFrom Checks
canEnterFrom(passableFrom, entrySide, isSolid): boolean
canLeaveTo(passableFrom, exitDir, isSolid): boolean

// Chunk-Validierung
isChunkLoaded(x, z, chunkService, chunkSize): boolean
clampToLoadedChunks(entity, oldX, oldZ, ...): void

// World-Grenzen
clampToWorldBounds(entity, appContext): void

// Block-Checks
isBlockSolid(x, y, z, chunkService): boolean

// Unterwasser
checkUnderwaterState(entity, chunkService, appContext, eyeHeight): boolean
hasBlockPositionChanged(entity): boolean
```

## Integration mit PhysicsService

### Update-Loop

```typescript
class PhysicsService {
  update(deltaTime: number) {
    if (!physicsEnabled || teleportationPending) return;

    for (const entity of entities.values()) {
      this.updateEntity(entity, deltaTime);
    }
  }

  private updateEntity(entity: PhysicsEntity, deltaTime: number) {
    const dimensions = getEntityDimensions(entity);

    // Walk-Modi (walk, sprint, crouch, swim, climb)
    if (isWalkMode(entity.movementMode)) {
      walkController.doMovement(
        entity,
        entity.wishMove,
        false, // startJump
        dimensions,
        deltaTime
      );
    }
    // Fly-Modi (fly, teleport)
    else if (isFlyMode(entity.movementMode)) {
      flyController.update(entity, entity.wishMove, deltaTime);
    }
  }
}
```

### Input-System

Input-Methoden setzen `wishMove` statt direkte Position-Manipulation:

```typescript
// Vorwärts/Rückwärts
moveForward(entity, distance, cameraYaw, cameraPitch) {
  if (usePitch) {
    entity.wishMove.x = sin(yaw) * cos(pitch) * distance;
    entity.wishMove.y = -sin(pitch) * distance;
    entity.wishMove.z = cos(yaw) * cos(pitch) * distance;
  } else {
    entity.wishMove.x = sin(yaw) * distance;
    entity.wishMove.z = cos(yaw) * distance;
  }
}

// Seitwärts
moveRight(entity, distance, cameraYaw) {
  entity.wishMove.x += sin(yaw + PI/2) * distance;
  entity.wishMove.z += cos(yaw + PI/2) * distance;
}

// Hoch/Runter (nur fly/teleport/swim)
moveUp(entity, distance) {
  if (mode == fly || mode == teleport || mode == swim) {
    entity.wishMove.y = distance;
  }
}

// Sprung
jump(entity) {
  if (mode == walk && grounded) {
    entity.velocity.y = jumpSpeed;
    entity.grounded = false;
  }
}
```

## Entity-Dimensionen

Dimensionen sind **mode-spezifisch** in PlayerInfo:

```typescript
dimensions: {
  walk:    { height: 2.0, width: 0.6, footprint: 0.3 },  // Normal
  sprint:  { height: 2.0, width: 0.6, footprint: 0.3 },  // Normal
  crouch:  { height: 1.0, width: 0.6, footprint: 0.3 },  // Halb so hoch!
  swim:    { height: 1.8, width: 0.6, footprint: 0.3 },  // Leicht kleiner
  climb:   { height: 1.8, width: 0.6, footprint: 0.3 },  // Normal
  fly:     { height: 1.8, width: 0.6, footprint: 0.3 },  // Normal
  teleport:{ height: 1.8, width: 0.6, footprint: 0.3 },  // Normal
}
```

**Verwendung**:
```typescript
function getEntityDimensions(entity: PhysicsEntity) {
  if (isPlayerEntity(entity) && entity.playerInfo.dimensions) {
    return entity.playerInfo.dimensions[entity.movementMode];
  }
  return { height: 1.8, width: 0.6, footprint: 0.3 }; // Default
}
```

**Footprint**: Radius für Corner-Sampling
- Player Position ± footprint = 4 Eckpunkte
- Ermöglicht präzise Multi-Block-Kollision

## Performance-Optimierungen

### 1. Block-Context Caching

```typescript
// Cache nur invalidieren wenn Block-Position sich ändert
if (hasBlockPositionChanged(entity)) {
  contextAnalyzer.invalidateCache(entity.entityId);
}

// Cache-Timeout: 100ms
// Spart hunderte Block-Queries pro Sekunde
```

### 2. Pre-Merged Block Modifiers

```typescript
// ChunkService merged bereits beim Laden
clientBlock.currentModifier = mergeModifiers(blockType, block.modifiers);

// Physics-Code: Direkter Zugriff ohne Registry-Lookup
const physics = clientBlock.currentModifier.physics;
const isSolid = physics?.solid === true;
```

### 3. Underwater Check Optimization

```typescript
// Nur prüfen wenn Block-Koordinaten sich ändern
if (floor(position) != lastBlockPos) {
  checkUnderwaterState(entity);
  lastBlockPos = floor(position);
}
```

### 4. Single-Pass Block Collection

BlockContextAnalyzer sammelt ALLE benötigten Blöcke in einem Durchgang:
- Keine redundanten Chunk-Queries
- Aggregation während Sammlung (nicht danach)
- Properties werden on-the-fly kombiniert

## Bewegungs-Modi im Detail

### Walk Mode
- Horizontale Bewegung (XZ)
- Gravitation aktiv
- Ground-Friction: 6.0
- Collision Detection
- Auto-Functions aktiv

### Sprint Mode
- Wie Walk
- Höhere Geschwindigkeit (effectiveRunSpeed)
- Gleiche Dimensionen

### Crouch Mode
- Wie Walk
- Langsamer (effectiveCrawlSpeed)
- **Höhe = 1.0 Block** (kann unter 1-Block-Decken)

### Swim Mode
- Wie Walk aber im Wasser
- Reduzierte Gravitation (underwaterGravity)
- Vertical wishMove aktiv (moveUp/Down)
- effectiveUnderwaterSpeed

### Climb Mode
- Auf climbable Blöcken (Leitern)
- Keine Gravitation
- Vertikale Bewegung aktiv
- Langsamer (50% von walk speed)

### Fly Mode
- Kreativ/Editor-Modus
- Keine Gravitation
- Keine Kollision
- 3D-Bewegung mit Pitch
- Schneller (2x walk speed)

### Teleport Mode
- Wartet auf Chunk-Loading
- Wie Fly aber physics disabled
- Auto-aktiviert bei Teleportation
- Wechselt zu walk wenn Chunks ready

## Debugging

### Logging

Jedes Modul hat eigenen Logger:
```typescript
const logger = getLogger('ModuleName');

logger.debug('Context built', { blocks: context.currentBlocks.blocks.length });
```

**Log-Levels**:
- ERROR: Kritische Fehler
- WARN: Warnungen
- INFO: Wichtige Events
- DEBUG: Detaillierte Infos
- TRACE: Sehr detailliert

**Konfiguration**:
```typescript
LoggerFactory.setLoggerLevel('BlockContextAnalyzer', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('CollisionDetector', LogLevel.TRACE);
```

### Visualisierung

Für Debugging können Block-Kategorien visualisiert werden:
```typescript
// Im RenderService
if (__EDITOR__ && debugMode) {
  const context = contextAnalyzer.getContext(player, dimensions);

  // Grüne Boxen: groundBlocks
  for (const block of context.groundBlocks.blocks) {
    drawDebugBox(block, 'green');
  }

  // Rote Boxen: frontBlocks
  for (const block of context.frontBlocks.blocks) {
    drawDebugBox(block, 'red');
  }
}
```

## Erweiterung

### Neuen Movement-Mode hinzufügen

```typescript
// 1. MovementMode erweitern
export type MovementMode = '... | newmode';

// 2. Dimensions in PlayerInfo hinzufügen
dimensions: {
  // ...
  newmode: { height: 1.5, width: 0.5, footprint: 0.25 }
}

// 3. Speed in MovementResolver.getMoveSpeed()
case 'newmode':
  return playerInfo.effectiveNewModeSpeed;

// 4. Controller-Logik in updateEntity()
if (entity.movementMode === 'newmode') {
  customController.update(entity, deltaTime);
}
```

### Neue Block-Physics-Property hinzufügen

```typescript
// 1. In BlockModifier (shared/types/BlockModifier.ts)
interface PhysicsModifier {
  // ...
  newProperty?: number;
}

// 2. In BlockContextAnalyzer aggregieren
for (const block of blocks) {
  if (block.currentModifier.physics?.newProperty) {
    // Aggregieren (MAX, OR, ADD, etc.)
    aggregated.newProperty = Math.max(aggregated.newProperty, ...);
  }
}

// 3. In PlayerBlockContext hinzufügen
groundBlocks: {
  // ...
  newProperty: number;
}

// 4. In Controller nutzen
if (context.groundBlocks.newProperty > 0) {
  // Spezielle Logik
}
```

## Best Practices

### ✅ DO:
- Nutze die Controller für alle Bewegungs-Logik
- Setze wishMove, nie direkt position
- Nutze getEntityDimensions() für Entity-Größe
- Invalidiere Context-Cache bei Block-Wechsel
- Logge wichtige Events (DEBUG-Level)

### ❌ DON'T:
- Nicht direkt `entity.position` manipulieren (außer in Controllern)
- Nicht `entity.velocity` direkt setzen (außer Jump)
- Nicht alte `updateWalkMode()` Methode nutzen
- Nicht Block-Queries ohne Chunk-Check
- Nicht Context manuell bauen (nutze BlockContextAnalyzer)

## Beispiel-Nutzung

### Entity erstellen und registrieren

```typescript
const entity: PhysicsEntity = {
  entityId: 'player',
  position: new Vector3(0, 64, 0),
  velocity: Vector3.Zero(),
  rotation: Vector3.Zero(),
  movementMode: 'walk',
  wishMove: Vector3.Zero(),
  grounded: false,
  onSlope: false,
  inWater: false,
  autoJump: 0,
  lastBlockPos: new Vector3(0, 64, 0),
};

physicsService.registerEntity(entity);
```

### Input verarbeiten

```typescript
// Im InputService / InputAction
if (keyW.pressed) {
  physicsService.moveForward(entity, 1.0, cameraYaw, cameraPitch);
}

if (keySpace.pressed) {
  physicsService.jump(entity);
}

// wishMove wird automatisch im nächsten Frame verarbeitet
```

### Movement-Mode wechseln

```typescript
// Zu Sprint
physicsService.setMovementMode(entity, 'sprint');

// Zu Crouch (1 Block hoch)
physicsService.setMovementMode(entity, 'crouch');

// Zu Fly (Editor)
if (__EDITOR__) {
  physicsService.setMovementMode(entity, 'fly');
}
```

### Block-Physics konfigurieren

```typescript
// One-Way Gate (von Nord/Süd begehbar)
{
  solid: true,
  passableFrom: Direction.NORTH | Direction.SOUTH
}

// Dünne Wand (läuft Nord-Süd)
{
  solid: false,
  passableFrom: Direction.NORTH | Direction.SOUTH
}

// Slope/Rampe
{
  solid: true,
  cornerHeights: [0, 0.5, 0.5, 0], // NW, NE, SE, SW
}

// Auto-Corner-Heights (aus offsets)
{
  solid: true,
  autoCornerHeights: true
}
// + Block.offsets oder VisibilityModifier.offsets

// Conveyor Belt
{
  solid: true,
  autoMove: { x: 2.0, y: 0, z: 0 } // 2 blocks/s nach Osten
}

// Trampolin
{
  solid: true,
  autoJump: true
}

// Drehscheibe
{
  solid: true,
  autoOrientationY: 1.57 // 90° in Radians
}

// Leiter
{
  solid: false,
  climbable: 1.0 // Climb speed multiplier
}

// Resistance (Sumpf, Sand)
{
  solid: true,
  resistance: 0.5 // 50% Geschwindigkeits-Reduktion
}
```

## Testing

### Unit-Tests

Jedes Modul kann isoliert getestet werden:

```typescript
// SurfaceAnalyzer Test
describe('SurfaceAnalyzer', () => {
  it('should calculate slope correctly', () => {
    const analyzer = new SurfaceAnalyzer(mockChunkService);
    const slope = analyzer.calculateSlope([0, 1, 1, 0]);
    expect(slope.x).toBe(1.0); // Rising to East
    expect(slope.z).toBe(0.0); // Flat in Z
  });
});

// CollisionDetector Test
describe('CollisionDetector', () => {
  it('should detect collision with solid block', () => {
    const detector = new CollisionDetector(mockChunkService, mockAnalyzer);
    const resolved = detector.resolveCollision(entity, wishPos, dims);
    expect(resolved.x).toBe(entity.position.x); // Stopped by wall
  });
});
```

### Integration-Tests

```typescript
describe('Physics Integration', () => {
  it('should handle walk → sprint → crouch transitions', () => {
    physicsService.setMovementMode(entity, 'walk');
    expect(getEntityDimensions(entity).height).toBe(2.0);

    physicsService.setMovementMode(entity, 'crouch');
    expect(getEntityDimensions(entity).height).toBe(1.0);
  });

  it('should apply slope forces correctly', () => {
    // Setup slope block
    // Move entity onto slope
    // Check velocity has slope component
  });
});
```

## Troubleshooting

### Problem: Entity fällt durch Boden

**Ursache**: Chunks nicht geladen oder groundBlocks nicht erkannt

**Lösung**:
```typescript
// Check ob Chunks geladen
if (!isChunkLoaded(x, z, chunkService, chunkSize)) {
  logger.warn('Chunk not loaded at position');
}

// Check groundBlocks
const context = contextAnalyzer.getContext(entity, dimensions);
logger.debug('Ground blocks', {
  count: context.groundBlocks.blocks.length,
  hasGround: context.groundBlocks.hasGround,
  groundY: context.groundBlocks.groundY
});
```

### Problem: Entity kann nicht durch One-Way Block

**Ursache**: passableFrom falsch konfiguriert

**Lösung**:
```typescript
// Für One-Way Gate (solid block):
passableFrom = entrySide; // z.B. NORTH

// Spieler kann von NORTH eintreten
// Von anderen Seiten blockiert
```

### Problem: Slope sliding zu stark/schwach

**Ursache**: slideForce oder friction nicht optimal

**Lösung**:
```typescript
// In MovementResolver.applySlopeForces()
const slideForce = 5.0; // Anpassen (höher = rutschiger)

// In PhysicsConfig
groundFriction: 6.0; // Anpassen (höher = mehr Reibung)
```

### Problem: Jump fühlt sich träge an

**Ursache**: Acceleration oder jumpSpeed zu niedrig

**Lösung**:
```typescript
// In PhysicsConfig
groundAcceleration: 100.0; // Erhöhen für schnellere Reaktion
jumpSpeed: 8.0; // Erhöhen für höhere Sprünge
```

## Referenzen

### Externe Dokumentation
- [Source Engine Movement](https://adrianb.io/2015/02/14/bunnyhop.html) - Velocity-System
- [Swept AABB Collision](https://www.gamedev.net/tutorials/programming/general-and-gameplay-programming/swept-aabb-collision-detection-and-response-r3084/) - Kollisions-Algorithmus

### Interne Dateien
- `instructions/physics.md` - Requirements-Dokument
- `shared/src/types/BlockModifier.ts` - Block-Physics-Properties
- `shared/src/types/PlayerInfo.ts` - Player-Konfiguration

## Autoren

Implementiert von Claude Code basierend auf detailliertem Requirements-Dokument.

Version: 2.0 (Komplette Neustrukturierung)
Datum: 2025-11-11
