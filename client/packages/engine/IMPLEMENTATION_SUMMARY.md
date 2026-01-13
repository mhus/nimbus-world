# BabylonJS 3D Engine Implementation - Zusammenfassung

Dieses Dokument fasst die Implementierung der 3D-Engine mit BabylonJS für den Nimbus Client 2.0 zusammen.

## Implementierte Services

### 1. BlockTypeService
**Datei:** `src/services/BlockTypeService.ts`

- Lädt BlockTypes vom Server (`/api/world/{worldId}/blocktypes`)
- Cached BlockTypes nach dem Laden
- Bietet Lookup-Funktion `getBlockType(id: number)`
- Validiert BlockType-Daten

### 2. ShaderService
**Datei:** `src/services/ShaderService.ts`

- Basis-Struktur für zukünftige Shader-Effekte
- Unterstützt Registration von Shader-Effekten
- Wird vom MaterialService verwendet
- Effekte werden später basierend auf `BlockModifier.visibility.effect` implementiert

### 3. TextureAtlas
**Datei:** `src/rendering/TextureAtlas.ts`

- Dynamisches Texture-Atlas-System (2048x2048)
- Lädt Texturen on-demand vom Asset-Server
- UV-Mapping für Block-Faces
- Image-Caching zur Performance-Optimierung
- Integration mit NetworkService für Asset-URLs

### 4. MaterialService
**Datei:** `src/services/MaterialService.ts`

- Verwaltet BabylonJS StandardMaterial
- Integration mit TextureAtlas
- Cached Materials
- Vorbereitet für zukünftige Shader-Integration

### 5. EngineService
**Datei:** `src/services/EngineService.ts`

- Haupt-Koordinator für alle 3D-Services
- Initialisiert BabylonJS Engine + Scene
- Verwaltet Render-Loop mit Delta-Time
- Koordiniert Camera, Environment, Player, Render und Input Services
- Window-Resize-Handling

### 6. CameraService
**Datei:** `src/services/CameraService.ts`

- BabylonJS FreeCamera für Ego-View (First-Person)
- Position- und Rotations-Steuerung
- Pitch-Clamping (verhindert Kamera-Flip)
- `egoView`-Eigenschaft (Third-Person für später vorbereitet)

### 7. EnvironmentService
**Datei:** `src/services/EnvironmentService.ts`

- HemisphericLight für Basis-Beleuchtung
- Hintergrundfarbe (Himmel)
- Vorbereitet für Time-of-Day und Wetter-Effekte

### 8. PlayerService
**Datei:** `src/services/PlayerService.ts`

- Spieler-Position und Velocity
- Bewegungs-Logik (moveForward, moveRight)
- Einfache Physik (Gravitation, Ground-Check)
- Jump-Mechanik
- Synchronisiert Kamera mit Spieler-Position (Eye-Height: 1.6 Blöcke)
- Event-System für Position-Updates

### 9. RenderService
**Datei:** `src/services/RenderService.ts`

- Rendert Chunks als BabylonJS Meshes
- Hört auf ChunkService Events (`chunk:loaded`, `chunk:unloaded`)
- Verwendet CubeRenderer für CUBE-Shapes
- Überspringt INVISIBLE Blocks
- Mesh-Lifecycle-Management
- Statistiken (Vertex/Face-Count)

### 10. CubeRenderer
**Datei:** `src/rendering/CubeRenderer.ts`

- Generiert Mesh-Geometrie für Würfel-Blöcke
- 6 Faces mit korrekten Normalen
- UV-Mapping aus TextureAtlas
- Unterstützt verschiedene Texturen pro Face (Top, Bottom, Sides)
- Face-Culling vorbereitet (TODO)

### 11. InputService
**Datei:** `src/services/InputService.ts`

- Verwaltet InputController und InputHandler
- Update-Loop für alle Handler
- Erweiterbar für verschiedene Controller-Typen

### 12. InputHandler-System
**Dateien:**
- `src/input/InputHandler.ts` (Basis-Klasse)
- `src/input/handlers/MovementHandlers.ts` (Move Forward/Back/Left/Right)
- `src/input/handlers/ActionHandlers.ts` (Jump)
- `src/input/handlers/RotationHandlers.ts` (Mouse Look)

- Abstraktes Handler-System für Input-Actions
- State-Management (active/inactive)
- Continuous vs. Discrete Actions
- Delta-Time-Integration

### 13. WebInputController
**Datei:** `src/input/WebInputController.ts`

- Browser-Input-Controller
- Key-Bindings: WASD + Space
- Mouse-Look mit Pointer Lock
- Event-Listener-Management
- Konfigurierbare Sensitivität

## Key-Bindings

- **W:** Move Forward
- **S:** Move Backward
- **A:** Move Left (Strafe)
- **D:** Move Right (Strafe)
- **Space:** Jump
- **Mouse:** Look Around (Pointer Lock erforderlich)

## NimbusClient Integration

**Datei:** `src/NimbusClient.ts`

Die Integration erfolgt in folgenden Schritten:

1. **AppContext initialisieren** (Config, ClientService)
2. **Core Services initialisieren:**
   - NetworkService (connect + login)
   - BlockTypeService (load BlockTypes)
   - ShaderService
   - ChunkService
3. **3D Engine initialisieren:**
   - EngineService erstellen
   - Engine initialisieren (Texturen laden, Scene erstellen)
   - Render-Loop starten
   - Chunks um Spieler registrieren

## Datei-Struktur

```
client/packages/engine/src/
├── NimbusClient.ts              # Haupt-Entry-Point
├── AppContext.ts                # Service-Registry
├── services/
│   ├── BlockTypeService.ts
│   ├── ShaderService.ts
│   ├── MaterialService.ts
│   ├── EngineService.ts
│   ├── CameraService.ts
│   ├── EnvironmentService.ts
│   ├── PlayerService.ts
│   ├── RenderService.ts
│   └── InputService.ts
├── rendering/
│   ├── TextureAtlas.ts
│   └── CubeRenderer.ts
└── input/
    ├── InputHandler.ts
    ├── WebInputController.ts
    └── handlers/
        ├── MovementHandlers.ts
        ├── ActionHandlers.ts
        └── RotationHandlers.ts
```

## Architektur-Diagramm

```
AppContext
├── NetworkService
├── BlockTypeService
├── ShaderService
├── ChunkService
└── EngineService
    ├── TextureAtlas
    ├── MaterialService (→ TextureAtlas, ShaderService)
    ├── CameraService
    ├── EnvironmentService
    ├── PlayerService (→ CameraService)
    ├── RenderService (→ MaterialService, BlockTypeService, TextureAtlas)
    │   └── CubeRenderer (→ TextureAtlas)
    └── InputService
        └── WebInputController
            └── InputHandlers (→ PlayerService)
```

## Event-Flow

### Chunk-Rendering:
```
ChunkService.emit('chunk:loaded')
  → RenderService.onChunksLoaded()
  → RenderService.renderChunk()
  → CubeRenderer.renderCube()
  → TextureAtlas.getTextureUV()
  → Mesh erstellen und zur Scene hinzufügen
```

### Player-Movement:
```
Keyboard-Event (W)
  → WebInputController.onKeyDown()
  → MoveForwardHandler.activate()
  → InputService.update() (jedes Frame)
  → MoveForwardHandler.onUpdate()
  → PlayerService.moveForward()
  → CameraService.setPosition()
```

## Minimale Implementierung

Die aktuelle Implementierung ist bewusst minimal gehalten:

### Was implementiert ist:
- ✅ CUBE-Shape Rendering
- ✅ INVISIBLE-Shape (wird übersprungen)
- ✅ Standard-Material (ohne Shader-Effekte)
- ✅ Ego-View (First-Person) Kamera
- ✅ WASD + Maus-Steuerung
- ✅ Einfache Physik (Gravitation, Jump)
- ✅ Texture-Atlas-System

### Was noch NICHT implementiert ist:
- ❌ Weitere Shapes (CROSS, GLASS, FLAT, SPHERE, etc.)
- ❌ Shader-Effekte (Water, Lava, Wind, Fog)
- ❌ Face-Culling (alle Faces werden gerendert)
- ❌ Kollisions-Erkennung mit Blöcken
- ❌ Third-Person-View
- ❌ Player-Rendering (in Third-Person)
- ❌ Block-Modifiers (Rotation, Scale, Color, Offsets)
- ❌ Optimierungen (Chunk-Batching, LOD, etc.)

## Nächste Schritte

1. **Testen:**
   - Server mit Chunks starten
   - Client bauen: `pnpm build` oder `pnpm dev`
   - Im Browser öffnen und testen

2. **Debugging:**
   - Browser DevTools → Console für Logs
   - F12 → Network für Asset-Loading
   - Performance-Tab für Render-Performance

3. **Erweiterungen:**
   - Weitere Shapes implementieren (CROSS für Pflanzen)
   - Face-Culling implementieren
   - Kollisions-Erkennung mit Terrain
   - Shader-Effekte für Wasser/Lava

## Technische Details

### Koordinaten-System:
- **World Coordinates:** x, y, z (absolute Position)
- **Chunk Coordinates:** cx, cz (Chunk-Position)
- BabylonJS verwendet Rechts-Hand-System (Y-Up)

### Performance-Optimierungen:
- Texture-Atlas reduziert Draw-Calls
- Ein Mesh pro Chunk (statt pro Block)
- Image-Caching vermeidet doppelte Downloads
- UV-Caching für häufig verwendete Texturen

### Bekannte Einschränkungen:
- Alle Block-Faces werden gerendert (keine Culling)
- Keine Chunk-Mesh-Updates (nur neu erstellen)
- Einfache Physik ohne echte Kollision
- Fixed Chunk-Loading-Radius

## Abhängigkeiten

- **@babylonjs/core** ^7.0.0
- **@babylonjs/loaders** ^7.0.0
- **@babylonjs/materials** ^7.0.0

## Entwickler-Hinweise

### Logging:
Alle Services verwenden `getLogger('ServiceName')`. Log-Level kann in `.env` gesetzt werden:
```
LOG_LEVEL=DEBUG
```

### Exception-Handling:
Alle Fehler werden durch `ExceptionHandler` geleitet:
```typescript
try {
  // ...
} catch (error) {
  throw ExceptionHandler.handleAndRethrow(error, 'ServiceName.methodName');
}
```

### Event-System:
Services kommunizieren via Events:
```typescript
service.on('event:name', (data) => { ... });
service.emit('event:name', data);
```

## Referenzen

- **BabylonJS Docs:** https://doc.babylonjs.com/
- **Client 2.0 Doku:** `client/CLAUDE.md`
- **Migration Plan:** `client/instructions/client_2.0/migration.md`
- **Playground Referenz:** `client_playground/packages/client/src/rendering/` (legacy)
