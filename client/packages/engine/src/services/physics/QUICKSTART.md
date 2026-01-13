# Physics System - Quick Start Guide

Schnelle Einführung in das modulare Physics-System.

## Basics

### Was ist PhysicsEntity?

Jedes Objekt das sich bewegt (Spieler, NPCs, Fahrzeuge):

```typescript
const entity: PhysicsEntity = {
  entityId: 'player',
  position: new Vector3(10, 64, 20),
  velocity: Vector3.Zero(),
  rotation: Vector3.Zero(),
  movementMode: 'walk',
  wishMove: Vector3.Zero(),
  grounded: false,
  onSlope: false,
  inWater: false,
  autoJump: 0,
  lastBlockPos: new Vector3(10, 64, 20),
};

physicsService.registerEntity(entity);
```

### Bewegung auslösen

**Nicht so** (direkte Position-Manipulation):
```typescript
// ❌ FALSCH
entity.position.x += 1.0;
```

**Sondern so** (über wishMove):
```typescript
// ✅ RICHTIG
physicsService.moveForward(entity, 1.0, cameraYaw, cameraPitch);

// Oder direkt wishMove setzen:
entity.wishMove.x = 1.0;
entity.wishMove.z = 0.5;
```

Das System berechnet dann automatisch Velocity, Kollisionen, etc.

## Häufige Aufgaben

### 1. Spieler bewegen

```typescript
// In InputService / InputAction
class MoveForwardAction extends InputAction {
  execute(value: number) {
    const player = playerService.getPlayerEntity();
    const camera = cameraService.getCamera();

    physicsService.moveForward(
      player,
      value, // -1 bis +1 (rückwärts/vorwärts)
      camera.rotation.y, // Yaw
      camera.rotation.x  // Pitch
    );
  }
}
```

### 2. Springen

```typescript
class JumpAction extends InputAction {
  execute(value: number) {
    if (value > 0.5) { // Button gedrückt
      const player = playerService.getPlayerEntity();
      physicsService.jump(player);
    }
  }
}
```

### 3. Movement-Mode wechseln

```typescript
// Zu Sprint wechseln
physicsService.setMovementMode(entity, 'sprint');

// Zu Crouch (macht Entity 1 Block hoch)
physicsService.setMovementMode(entity, 'crouch');

// Zu Fly (nur Editor)
if (__EDITOR__) {
  physicsService.setMovementMode(entity, 'fly');
}

// Toggle Walk ↔ Fly
physicsService.toggleMovementMode(entity);
```

### 4. Entity-Info abfragen

```typescript
// Ist auf Boden?
if (entity.grounded) {
  console.log('On ground');
}

// Ist auf Slope?
if (entity.onSlope) {
  console.log('On slope, sliding');
}

// Ist unter Wasser?
if (entity.inWater) {
  console.log('Swimming');
}

// Aktuelle Geschwindigkeit
const speed = entity.velocity.length();
console.log(`Moving at ${speed} blocks/s`);
```

### 5. Teleportation

```typescript
// Entity teleportieren
const targetPosition = new Vector3(100, 70, 200);
const targetRotation = new Vector3(0, Math.PI / 2, 0); // 90° Yaw

physicsService.teleport(entity, targetPosition, targetRotation);

// System wartet automatisch auf Chunks und aktiviert dann Physics
```

## Block-Konfiguration

### One-Way Blöcke

```typescript
// Tür die nur von vorne begehbar ist
{
  solid: true,
  passableFrom: Direction.NORTH
}

// Von NORTH: ✅ Kann eintreten
// Von anderen Seiten: ❌ Blockiert
// Von innen: ✅ Kann austreten (wenn Nachbar nicht solid)
```

### Dünne Wände

```typescript
// Glaswand (nicht solid, aber blockiert seitlich)
{
  solid: false,
  passableFrom: Direction.NORTH | Direction.SOUTH
}

// Wand läuft Nord-Süd
// Kann in NS-Richtung passieren, aber nicht in EW-Richtung durchgehen
```

### Rampen / Slopes

```typescript
// Manuelle Corner Heights
{
  solid: true,
  cornerHeights: [0, 0.5, 0.5, 0] // NW, NE, SE, SW
}

// Auto-Corner-Heights (aus Mesh-Offsets)
{
  solid: true,
  autoCornerHeights: true
}
// + Block.offsets oder VisibilityModifier.offsets müssen gesetzt sein
```

### Convey or Belt

```typescript
{
  solid: true,
  autoMove: { x: 2.0, y: 0, z: 0 } // 2 blocks/s nach Osten
}
```

### Trampolin

```typescript
{
  solid: true,
  autoJump: true
}
```

### Drehscheibe

```typescript
{
  solid: true,
  autoOrientationY: 1.57 // 90° (PI/2 radians)
}
```

### Leiter

```typescript
{
  solid: false,
  climbable: 1.0 // Climb speed multiplier
}
```

### Langsame Oberfläche (Sumpf, Sand)

```typescript
{
  solid: true,
  resistance: 0.5 // 50% langsamer
}
```

## Erweiterte Nutzung

### Custom Movement Controller

```typescript
class CustomModeController {
  constructor(
    private appContext: AppContext,
    private chunkService: ChunkService
  ) {
    this.contextAnalyzer = new BlockContextAnalyzer(chunkService);
    this.collisionDetector = new CollisionDetector(...);
  }

  update(entity: PhysicsEntity, deltaTime: number) {
    // Custom physics logic
    const context = this.contextAnalyzer.getContext(entity, dimensions);

    // ... velocity berechnung
    // ... kollision
    // ... position update
  }
}

// In PhysicsService registrieren
if (entity.movementMode === 'custom') {
  customController.update(entity, deltaTime);
}
```

### Entity-Dimensionen anpassen

```typescript
// In PlayerInfo (für Spieler)
playerInfo.dimensions.walk = {
  height: 3.0,    // Größerer Spieler
  width: 1.0,     // Breiter
  footprint: 0.5  // Größerer Footprint
};

// Für NPCs/Entities (Default überschreiben)
function getEntityDimensions(entity: PhysicsEntity) {
  if (entity.entityId === 'giant_npc') {
    return { height: 4.0, width: 2.0, footprint: 1.0 };
  }
  // ... default logic
}
```

### Physics-Parameter tunen

```typescript
// In PhysicsService Constructor
const customConfig: PhysicsConfig = {
  gravity: -30.0,              // Stärkere Gravitation
  groundAcceleration: 150.0,   // Schnellere Reaktion
  groundFriction: 10.0,        // Mehr Reibung
  jumpSpeed: 12.0,             // Höhere Sprünge
  maxClimbHeight: 0.5,         // Höhere Auto-Climb-Stufen
  coyoteTime: 0.2,             // Mehr Jump-Toleranz
};

walkController = new WalkModeController(appContext, chunkService, customConfig);
```

## Debugging-Tipps

### Logging aktivieren

```typescript
import { LoggerFactory, LogLevel } from '@nimbus/shared';

// Alle Physics-Module auf DEBUG
LoggerFactory.setLoggerLevel('BlockContextAnalyzer', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('CollisionDetector', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('MovementResolver', LogLevel.DEBUG);
LoggerFactory.setLoggerLevel('WalkModeController', LogLevel.DEBUG);

// Sehr detailliert
LoggerFactory.setLoggerLevel('PhysicsService', LogLevel.TRACE);
```

### Häufige Probleme

**Entity fällt durch Boden**:
```typescript
// Check 1: Chunks geladen?
if (!chunkService.getChunk(cx, cz)) {
  console.log('Chunk not loaded!');
}

// Check 2: Ground blocks erkannt?
const context = contextAnalyzer.getContext(entity, dims);
console.log('Ground blocks:', context.groundBlocks.blocks.length);
console.log('Has ground:', context.groundBlocks.hasGround);
console.log('Ground Y:', context.groundBlocks.groundY);
```

**Entity kann nicht durch Tür**:
```typescript
// Check passableFrom
const block = chunkService.getBlockAt(x, y, z);
const passableFrom = block?.currentModifier.physics?.passableFrom;
const isSolid = block?.currentModifier.physics?.solid;

console.log('PassableFrom:', passableFrom);
console.log('Is solid:', isSolid);
console.log('Entry side:', direction);

// Test canEnterFrom
const canEnter = PhysicsUtils.canEnterFrom(passableFrom, direction, isSolid);
console.log('Can enter:', canEnter);
```

**Jump funktioniert nicht**:
```typescript
// Check grounded state
console.log('Grounded:', entity.grounded);

// Check jump speed
const jumpSpeed = movementResolver.getJumpSpeed(entity);
console.log('Jump speed:', jumpSpeed);

// Check coyote time
// (interner State im MovementResolver)
```

**Slope sliding zu stark**:
```typescript
// In MovementResolver.applySlopeForces()
const slideForce = 5.0; // Verringern für weniger Rutschigkeit

// Oder Resistance auf Slope-Blöcken erhöhen
{
  solid: true,
  cornerHeights: [...],
  resistance: 0.3 // 30% langsamer = weniger sliding
}
```

## Cheat Sheet

### Wichtigste Methoden

| Methode | Zweck |
|---------|-------|
| `physicsService.registerEntity()` | Entity für Physics anmelden |
| `physicsService.moveForward()` | Vorwärts/Rückwärts bewegen |
| `physicsService.moveRight()` | Seitwärts bewegen |
| `physicsService.moveUp()` | Hoch/Runter (fly/swim) |
| `physicsService.jump()` | Springen |
| `physicsService.setMovementMode()` | Modus wechseln |
| `physicsService.teleport()` | Teleportieren |

### Wichtigste Properties

| Property | Typ | Bedeutung |
|----------|-----|-----------|
| `entity.wishMove` | Vector3 | Bewegungs-Absicht (Input) |
| `entity.velocity` | Vector3 | Tatsächliche Geschwindigkeit |
| `entity.grounded` | boolean | Auf Boden? |
| `entity.onSlope` | boolean | Auf Schräge? |
| `entity.inWater` | boolean | Unter Wasser? |
| `entity.movementMode` | MovementMode | Aktueller Modus |

### Block-Properties Kurzreferenz

```typescript
// Collision
solid: true | false

// One-Way / Dünne Wand
passableFrom: Direction.NORTH | Direction.EAST | ...

// Slopes
cornerHeights: [NW, NE, SE, SW]
autoCornerHeights: true

// Auto-Functions
autoMove: { x, y, z }
autoOrientationY: number (radians)
autoJump: true

// Special
climbable: number
resistance: number (0-1)
```

## Beispiel-Code

### Vollständiges Beispiel: Spieler-Bewegung

```typescript
import { PhysicsService } from './services/PhysicsService';
import { PlayerService } from './services/PlayerService';

// Setup
const physicsService = new PhysicsService(appContext);
const playerService = new PlayerService(appContext, cameraService);

physicsService.setChunkService(chunkService);
playerService.setPhysicsService(physicsService);

// Input handling
document.addEventListener('keydown', (e) => {
  const player = playerService.getPlayerEntity();
  const camera = cameraService.getCamera();

  switch (e.key) {
    case 'w': // Forward
      physicsService.moveForward(player, 1.0, camera.rotation.y, camera.rotation.x);
      break;

    case 's': // Backward
      physicsService.moveForward(player, -1.0, camera.rotation.y, camera.rotation.x);
      break;

    case 'a': // Left
      physicsService.moveRight(player, -1.0, camera.rotation.y);
      break;

    case 'd': // Right
      physicsService.moveRight(player, 1.0, camera.rotation.y);
      break;

    case ' ': // Jump
      physicsService.jump(player);
      break;

    case 'Shift': // Sprint
      physicsService.setMovementMode(player, 'sprint');
      break;

    case 'Control': // Crouch
      physicsService.setMovementMode(player, 'crouch');
      break;
  }
});

document.addEventListener('keyup', (e) => {
  const player = playerService.getPlayerEntity();

  // Reset wishMove when key released
  if (e.key === 'w' || e.key === 's') {
    player.wishMove.x = 0;
    player.wishMove.z = 0;
  }

  // Back to walk mode
  if (e.key === 'Shift' || e.key === 'Control') {
    physicsService.setMovementMode(player, 'walk');
  }
});

// Update loop
function gameLoop(deltaTime: number) {
  physicsService.update(deltaTime); // Bewegt alle Entities

  // Entity-Position ist jetzt aktualisiert
  camera.position = player.position.add(new Vector3(0, 1.6, 0)); // Eye height
}
```

### Beispiel: One-Way Tür erstellen

```typescript
// Block platzieren
const doorBlock: Block = {
  position: { x: 10, y: 64, z: 20 },
  blockTypeId: DOOR_TYPE_ID,
  modifiers: {
    0: { // Default status
      physics: {
        solid: true,
        passableFrom: Direction.NORTH // Nur von Norden begehbar
      }
    }
  }
};

// Spieler kann von Norden eintreten
// Von Osten/Westen/Süden blockiert
// Von innen kann er durch jeden nicht-soliden Nachbar-Block austreten
```

### Beispiel: Slope-Rampe erstellen

```typescript
// Rampe die nach Osten ansteigt
const rampBlock: Block = {
  position: { x: 15, y: 64, z: 25 },
  blockTypeId: RAMP_TYPE_ID,
  modifiers: {
    0: {
      physics: {
        solid: true,
        cornerHeights: [
          0.0, // NW (West-Seite, unten)
          1.0, // NE (Ost-Seite, oben)
          1.0, // SE (Ost-Seite, oben)
          0.0  // SW (West-Seite, unten)
        ]
      }
    }
  }
};

// Spieler:
// - Rutscht nach Westen wenn er steht (slope sliding)
// - Kann nach Osten laufen (mit Widerstand)
// - Wird an Surface geclampt (kein "Durch-Slope-Fallen")
```

### Beispiel: Conveyor Belt erstellen

```typescript
// Förderband nach Norden
const conveyorBlock: Block = {
  position: { x: 20, y: 64, z: 30 },
  blockTypeId: CONVEYOR_TYPE_ID,
  modifiers: {
    0: {
      physics: {
        solid: true,
        autoMove: { x: 0, y: 0, z: -3.0 } // 3 blocks/s nach Norden
      }
    }
  }
};

// Spieler wird automatisch mit 3 blocks/s nach Norden bewegt
// Additiv zu seiner eigenen Bewegung
```

### Beispiel: Custom Entity mit eigenen Dimensionen

```typescript
// Pferd (größer, rechteckiger Footprint)
const horse: PhysicsEntity = {
  entityId: 'horse_1',
  position: new Vector3(50, 64, 50),
  velocity: Vector3.Zero(),
  rotation: Vector3.Zero(),
  movementMode: 'walk',
  wishMove: Vector3.Zero(),
  grounded: false,
  onSlope: false,
  inWater: false,
  autoJump: 0,
  lastBlockPos: new Vector3(50, 64, 50),
};

// Custom dimensions (überschreibe getEntityDimensions)
// Pferd: 2.5 Blöcke hoch, 1.5 breit, 2-Block Footprint
function getEntityDimensions(entity: PhysicsEntity) {
  if (entity.entityId.startsWith('horse_')) {
    return { height: 2.5, width: 1.5, footprint: 0.75 };
  }
  // ... standard logic
}
```

## Performance-Tipps

### 1. Entities nur registrieren wenn nötig

```typescript
// ✅ Gut: Nur aktive Entities
if (entity.isActive && entity.needsPhysics) {
  physicsService.registerEntity(entity);
}

// ❌ Schlecht: Alle Entities (auch unsichtbare, weit entfernte)
physicsService.registerEntity(everyEntity);
```

### 2. Context-Cache nutzen

```typescript
// ✅ Gut: Cache wird automatisch verwendet
// Nur bei Block-Wechsel invalidiert

// ❌ Schlecht: Cache manuell clearen
contextAnalyzer.invalidateCache(entityId); // Nur wenn wirklich nötig!
```

### 3. Logging in Produktion reduzieren

```typescript
// Development
if (__DEV__) {
  LoggerFactory.setDefaultLevel(LogLevel.DEBUG);
}

// Production
else {
  LoggerFactory.setDefaultLevel(LogLevel.WARN);
}
```

### 4. Physics für weit entfernte Entities pausieren

```typescript
// Nur Entities in Sichtweite updaten
const PHYSICS_RANGE = 100; // blocks

for (const entity of entities.values()) {
  const distance = entity.position.subtract(player.position).length();

  if (distance < PHYSICS_RANGE) {
    physicsService.update(entity, deltaTime);
  } else {
    // Physics pausiert - Position bleibt fix
  }
}
```

## FAQ

**Q: Wann wird wishMove auf Zero gesetzt?**
A: Das System setzt wishMove NICHT automatisch zurück. Der Input-Handler muss das bei Key-Release tun.

**Q: Kann ich velocity direkt setzen?**
A: Nur für Sprung (velocity.y). Ansonsten wishMove nutzen und das System rechnen lassen.

**Q: Wie funktioniert Coyote Time?**
A: MovementResolver trackt Zeit seit letztem Boden-Kontakt. Jump ist ~100ms nach Verlassen noch möglich.

**Q: Was ist der Unterschied zwischen walk und sprint?**
A: Nur die Geschwindigkeit (effectiveWalkSpeed vs. effectiveRunSpeed). Gleiche Physics, gleiche Dimensionen.

**Q: Warum hat crouch height: 1.0?**
A: Damit Spieler unter 1-Block-Decken passen kann. Wichtig für Tunnel, Verstecke.

**Q: Kann ich neue Movement-Modi hinzufügen?**
A: Ja! MovementMode-Type erweitern, dimensions hinzufügen, Controller-Logic in updateEntity() einfügen.

**Q: Wie teste ich das System?**
A: Jedes Modul ist separat testbar. Siehe `README.md` → Testing-Sektion.

**Q: Was passiert wenn Chunk nicht geladen ist?**
A: Bewegung wird blockiert, Entity bleibt an Chunk-Grenze stehen. Velocity wird auf 0 gesetzt.

**Q: Werden alte Entities automatisch migriert?**
A: Ja, `registerEntity()` initialisiert alle neuen Felder mit Defaults.

## Nächste Schritte

1. Lies `README.md` für detaillierte Dokumentation
2. Lies `ARCHITECTURE.md` für System-Design
3. Schau dir die Module in `physics/` an
4. Experimentiere mit Block-Properties
5. Teste verschiedene Movement-Modi

## Support

Bei Fragen oder Problemen:
- Prüfe die Logs (DEBUG-Level)
- Schau in die Modul-Implementierung
- Referenziere die Requirements (`instructions/physics.md`)
