# Input System Architecture

Das Input System des Nimbus Clients folgt einer klaren Trennung zwischen Input-Empfang, Binding und Logik.

## Architektur-Überblick

```
AppContext
  └── InputService (Zentrale Verwaltung)
        ├── HandlerRegistry (Map<string, InputHandler>)
        │     ├── 'click' → ClickInputHandler
        │     ├── 'shortcut' → ShortcutInputHandler
        │     ├── 'moveForward' → MoveForwardHandler
        │     ├── 'moveBackward' → MoveBackwardHandler
        │     ├── 'moveLeft' → MoveLeftHandler
        │     ├── 'moveRight' → MoveRightHandler
        │     ├── 'moveUp' → MoveUpHandler
        │     ├── 'moveDown' → MoveDownHandler
        │     ├── 'jump' → JumpHandler
        │     ├── 'rotate' → RotateHandler
        │     ├── 'cycleMovementState' → CycleMovementStateHandler
        │     ├── 'toggleViewMode' → ToggleViewModeHandler
        │     ├── 'toggleShortcuts' → ToggleShortcutsHandler
        │     ├── 'editSelectionRotator' → EditSelectionRotatorHandler (__EDITOR__)
        │     ├── 'editorActivate' → EditorActivateHandler (__EDITOR__)
        │     ├── 'blockEditorActivate' → BlockEditorActivateHandler (__EDITOR__)
        │     ├── 'editConfigActivate' → EditConfigActivateHandler (__EDITOR__)
        │     └── ...
        └── Controller (Reine Binding-Schicht)
              ├── WebInputController (Keyboard + Mouse)
              ├── GamePadController (GamePad)
              └── TouchController (Touch + Virtual Joystick)
                    └── Bindet Input-Events → Handler.activate()
```

**Wichtig:** ALLE Handler werden zentral im InputService erstellt.
Controller sind reine Binding-Schichten ohne Business-Logik.

## Komponenten

### 1. InputService

**Verantwortlichkeiten:**
- Zentrale Verwaltung aller InputHandler
- Bereitstellung von Handlers für InputController
- Update-Loop für aktive Handler

**API:**
```typescript
class InputService {
  // Handler-Zugriff
  getHandler(key: string): InputHandler | undefined;

  // Controller-Management
  setController(controller: InputController): void;

  // Update-Loop (wird jedes Frame aufgerufen)
  update(deltaTime: number): void;
}
```

**Verwendung:**
```typescript
// In NimbusClient oder Bootstrap-Code
const inputService = new InputService(appContext, playerService);
appContext.services.input = inputService;

// Controller setzen
const webController = new WebInputController(canvas, playerService, appContext);
inputService.setController(webController);
```

### 2. InputController

**Verantwortlichkeiten:**
- Empfangen von Input-Events (Keyboard, Maus, GamePad, Touch)
- Binding: Welcher Input aktiviert welchen Handler
- Keine Business-Logik!

**Interface:**
```typescript
interface InputController {
  initialize(): void;
  dispose(): void;
  getHandlers(): InputHandler[];
}
```

**Implementierungen:**
- `WebInputController` - Browser-Input (Keyboard, Maus)
- `GamePadController` - GamePad/Controller-Input (zukünftig)
- `TouchController` - Touch-Screen-Input (zukünftig)

**Beispiel: WebInputController**
```typescript
class WebInputController implements InputController {
  private handlers: InputHandler[] = [];

  // All handlers retrieved from InputService
  private clickHandler?: InputHandler;
  private shortcutHandler?: InputHandler;
  private moveForwardHandler?: InputHandler;
  // ... weitere Handler

  initialize(): void {
    // Alle Handler aus InputService holen
    const inputService = this.appContext.services.input;
    this.clickHandler = inputService.getHandler('click');
    this.shortcutHandler = inputService.getHandler('shortcut');
    this.moveForwardHandler = inputService.getHandler('moveForward');
    // ... retrieve all needed handlers

    // Build handlers array for update loop
    this.handlers = [
      this.clickHandler,
      this.shortcutHandler,
      this.moveForwardHandler,
      // ... weitere Handler
    ].filter((h): h is InputHandler => h !== undefined);

    // Event-Listener registrieren
    this.canvas.addEventListener('mousedown', this.onMouseDown);
    window.addEventListener('keydown', this.onKeyDown);
    window.addEventListener('keyup', this.onKeyUp);
  }

  private onMouseDown = (event: MouseEvent) => {
    // Binding: Mausklick → ClickHandler
    if (this.clickHandler) {
      this.clickHandler.activate(event.button); // 0=left, 1=middle, 2=right
    }
  };

  private onKeyDown = (event: KeyboardEvent) => {
    // Binding: Zahlentaste → ShortcutHandler
    if (event.key >= '0' && event.key <= '9') {
      const shortcutNr = event.key === '0' ? 10 : parseInt(event.key, 10);
      if (this.shortcutHandler) {
        this.shortcutHandler.activate(shortcutNr);
      }
    }

    // Binding: W-Taste → MoveForwardHandler
    if (event.key === 'w' || event.key === 'W') {
      if (this.moveForwardHandler && !this.moveForwardHandler.isActive()) {
        this.moveForwardHandler.activate();
      }
    }
  };

  private onKeyUp = (event: KeyboardEvent) => {
    // Release: W-Taste → MoveForwardHandler
    if (event.key === 'w' || event.key === 'W') {
      if (this.moveForwardHandler && this.moveForwardHandler.isActive()) {
        this.moveForwardHandler.deactivate();
      }
    }
  };

  getHandlers(): InputHandler[] {
    return this.handlers;
  }

  dispose(): void {
    // Event-Listener entfernen
    this.canvas.removeEventListener('mousedown', this.onMouseDown);
    window.removeEventListener('keydown', this.onKeyDown);
    window.removeEventListener('keyup', this.onKeyUp);
  }
}
```

### 3. InputHandler

**Verantwortlichkeiten:**
- Enthält die gesamte Business-Logik für eine Aktion
- Zugriff auf Services (PlayerService, NetworkService, SelectService, etc.)
- Unterstützt verschiedene Input-Typen (diskret/kontinuierlich)

**Basis-Klasse:**
```typescript
abstract class InputHandler {
  protected playerService: PlayerService;
  protected appContext?: AppContext;
  protected state: InputState = { active: false, value: 0 };

  // Lifecycle-Methoden (public)
  activate(value?: number): void;
  deactivate(): void;
  update(deltaTime: number): void;
  isActive(): boolean;

  // Template-Methoden (protected, von Subklassen implementiert)
  protected abstract onActivate(value: number): void;
  protected abstract onDeactivate(): void;
  protected abstract onUpdate(deltaTime: number, value: number): void;
}
```

**Handler-Typen:**

#### A. Diskrete Handler (Keys, Buttons)
Führen eine Aktion sofort bei Aktivierung aus.

```typescript
class JumpHandler extends InputHandler {
  protected onActivate(value: number): void {
    this.playerService.jump();
  }

  protected onDeactivate(): void { /* no-op */ }
  protected onUpdate(deltaTime: number, value: number): void { /* no-op */ }
}
```

#### B. Kontinuierliche Handler (Movement)
Werden jedes Frame aktualisiert während sie aktiv sind.

```typescript
class MoveForwardHandler extends InputHandler {
  protected onActivate(value: number): void {
    // Initial aktivieren
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Jedes Frame: Bewegung anwenden
    this.playerService.move('forward', value); // value = 0.0-1.0
  }

  protected onDeactivate(): void {
    // Bewegung stoppen
  }
}
```

#### C. Komplexe Handler (Click, Shortcut)
Interagieren mit mehreren Services und senden Daten an Server.

```typescript
class ClickInputHandler extends InputHandler {
  protected onActivate(buttonNumber: number): void {
    // 1. SelectService: Entity/Block unter Cursor finden
    // 2. PlayerService: Position, Rotation, MovementStatus holen
    // 3. PlayerInfo: Shortcut-Konfiguration lesen
    // 4. NetworkService: Interaktion an Server senden

    const selectService = this.appContext.services.select;
    const selectedEntity = selectService.getCurrentSelectedEntity();

    if (selectedEntity) {
      const movementStatus = this.playerService.getMovementState();
      const shortcut = playerInfo.shortcuts?.[`click${buttonNumber}`];

      this.appContext.services.network.sendEntityInteraction(
        selectedEntity.id,
        'click',
        buttonNumber,
        {
          movementStatus,
          shortcutType: shortcut?.type,
          shortcutItemId: shortcut?.itemId,
          // ... weitere Kontext-Daten
        }
      );
    }
  }
}
```

## Input-Typen und ihre Unterstützung

Handler sollten flexibel verschiedene Input-Wege unterstützen:

### 1. Diskrete Inputs (Binary: An/Aus)
- **Beispiele:** Tasten, Mausklicks, GamePad-Buttons
- **Handler:** `activate()` wird mit Standardwert (1.0) aufgerufen
- **Adaptierung:** Handler nutzt internen Standardwert (z.B. Turn-Speed)

```typescript
// Keyboard
handler.activate(); // value=1.0 (Standard)

// GamePad Button
handler.activate(triggerPressure); // value=0.0-1.0 (optional)
```

### 2. Kontinuierliche Inputs (Analog: 0.0-1.0)
- **Beispiele:** Maus-Movement, Analog-Stick, Trigger-Druck
- **Handler:** `activate(value)` wird mit normalisierten Wert aufgerufen
- **Adaptierung:** Handler nutzt den übergebenen Wert direkt

```typescript
// Mouse Movement
handler.activate(deltaX / sensitivity); // Normalisiert

// Analog Stick
handler.activate(stickValue); // -1.0 bis +1.0
```

### 3. Beispiel: RotateHandler
Unterstützt beide Wege:

```typescript
class RotateHandler extends InputHandler {
  private deltaX: number = 0;
  private deltaY: number = 0;
  private defaultTurnSpeed: number = 0.003; // Standardgeschwindigkeit

  // Für Maus: Direkter Delta-Wert
  setDelta(deltaX: number, deltaY: number): void {
    this.deltaX = deltaX;
    this.deltaY = deltaY;
  }

  // Für GamePad: Kontinuierlicher Wert
  protected onUpdate(deltaTime: number, value: number): void {
    if (this.deltaX !== 0 || this.deltaY !== 0) {
      // Maus: Nutze Delta direkt
      this.playerService.rotate(this.deltaX, this.deltaY);
      this.deltaX = 0;
      this.deltaY = 0;
    } else if (value !== 0) {
      // GamePad: Nutze value + Standardgeschwindigkeit
      const rotationAmount = value * this.defaultTurnSpeed * deltaTime;
      this.playerService.rotate(rotationAmount, 0);
    }
  }
}
```

## Handler registrieren

### Alle Handler sind zentral (im InputService)
**WICHTIG:** Alle Handler werden im InputService registriert und von allen Controllern geteilt.
Dies ermöglicht maximale Wiederverwendbarkeit und konsistentes Verhalten über alle Input-Typen hinweg.

```typescript
// In InputService.registerCentralHandlers()
// Click und Shortcut Handler
this.handlerRegistry.set('click', new ClickInputHandler(playerService, appContext));
this.handlerRegistry.set('shortcut', new ShortcutInputHandler(playerService, appContext));

// Movement Handler
this.handlerRegistry.set('moveForward', new MoveForwardHandler(playerService));
this.handlerRegistry.set('moveBackward', new MoveBackwardHandler(playerService));
this.handlerRegistry.set('moveLeft', new MoveLeftHandler(playerService));
this.handlerRegistry.set('moveRight', new MoveRightHandler(playerService));
this.handlerRegistry.set('moveUp', new MoveUpHandler(playerService));
this.handlerRegistry.set('moveDown', new MoveDownHandler(playerService));

// Action Handler
this.handlerRegistry.set('jump', new JumpHandler(playerService));
this.handlerRegistry.set('cycleMovementState', new CycleMovementStateHandler(playerService));
this.handlerRegistry.set('toggleViewMode', new ToggleViewModeHandler(playerService));
this.handlerRegistry.set('toggleShortcuts', new ToggleShortcutsHandler(playerService, appContext));

// Rotation Handler
this.handlerRegistry.set('rotate', new RotateHandler(playerService));

// Editor Handler (nur im Editor-Modus)
if (__EDITOR__) {
  this.handlerRegistry.set('editSelectionRotator', new EditSelectionRotatorHandler(playerService, appContext));
  this.handlerRegistry.set('editorActivate', new EditorActivateHandler(playerService, appContext));
  this.handlerRegistry.set('blockEditorActivate', new BlockEditorActivateHandler(playerService, appContext));
  this.handlerRegistry.set('editConfigActivate', new EditConfigActivateHandler(playerService, appContext));
}
```

## Neue Handler erstellen

### Beispiel: Sprint-Handler hinzufügen

#### 1. Handler-Klasse erstellen

```typescript
// handlers/SprintHandler.ts
import { InputHandler } from '../InputHandler';
import type { PlayerService } from '../../services/PlayerService';

export class SprintHandler extends InputHandler {
  constructor(playerService: PlayerService) {
    super(playerService);
  }

  protected onActivate(value: number): void {
    // Setze Movement-Modus auf SPRINT
    this.playerService.setMovementMode('sprint');
  }

  protected onDeactivate(): void {
    // Zurück zum WALK-Modus
    this.playerService.setMovementMode('walk');
  }

  protected onUpdate(deltaTime: number, value: number): void {
    // Keine kontinuierlichen Updates nötig
  }
}
```

#### 2. Im InputService registrieren

```typescript
// InputService.ts - In registerCentralHandlers()
import { SprintHandler } from '../input/handlers/SprintHandler';

private registerCentralHandlers(): void {
  // ... existing handlers ...

  // Sprint Handler
  this.handlerRegistry.set('sprint', new SprintHandler(this.playerService));

  // ... rest of handlers ...
}
```

#### 3. Im Controller binden

```typescript
// WebInputController.ts
export class WebInputController implements InputController {
  private sprintHandler?: InputHandler;

  initialize(): void {
    // ... retrieve other handlers ...

    // Retrieve sprint handler
    this.sprintHandler = inputService.getHandler('sprint');

    // ... setup event listeners ...
  }

  private onKeyDown = (event: KeyboardEvent): void => {
    // Bind Left Shift to Sprint
    if (event.key === 'Shift' && event.location === KeyboardEvent.DOM_KEY_LOCATION_LEFT) {
      if (this.sprintHandler && !this.sprintHandler.isActive()) {
        this.sprintHandler.activate();
        event.preventDefault();
      }
      return;
    }

    // ... rest of key handling ...
  };

  private onKeyUp = (event: KeyboardEvent): void => {
    // Release sprint on Shift up
    if (event.key === 'Shift' && event.location === KeyboardEvent.DOM_KEY_LOCATION_LEFT) {
      if (this.sprintHandler && this.sprintHandler.isActive()) {
        this.sprintHandler.deactivate();
        event.preventDefault();
      }
      return;
    }

    // ... rest of key handling ...
  };
}
```

#### 4. Handler zur Update-Liste hinzufügen

```typescript
// WebInputController.ts - In initialize()
const handlerList = [
  this.clickHandler,
  this.shortcutHandler,
  this.moveForwardHandler,
  // ... other handlers ...
  this.sprintHandler,  // Add new handler here
];

// Filter out undefined handlers and build final list
this.handlers = handlerList.filter((h): h is InputHandler => h !== undefined);
```

## Neuen Controller erstellen (GamePad-Beispiel)

### GamePad Controller erstellen

Ein GamePad Controller ist eine reine Binding-Schicht, die dieselben zentralen Handler nutzt wie WebInputController.

```typescript
// input/GamePadController.ts
import { getLogger } from '@nimbus/shared';
import type { InputController } from '../services/InputService';
import type { PlayerService } from '../services/PlayerService';
import type { AppContext } from '../AppContext';
import type { InputHandler } from './InputHandler';

const logger = getLogger('GamePadController');

export class GamePadController implements InputController {
  private playerService: PlayerService;
  private appContext: AppContext;
  private handlers: InputHandler[] = [];

  // All handlers retrieved from InputService
  private clickHandler?: InputHandler;
  private shortcutHandler?: InputHandler;
  private moveForwardHandler?: InputHandler;
  private moveBackwardHandler?: InputHandler;
  private moveLeftHandler?: InputHandler;
  private moveRightHandler?: InputHandler;
  private rotateHandler?: InputHandler;
  private jumpHandler?: InputHandler;

  private gamepadIndex: number = -1;
  private updateInterval?: number;

  constructor(playerService: PlayerService, appContext: AppContext) {
    this.playerService = playerService;
    this.appContext = appContext;
  }

  initialize(): void {
    // Get all handlers from InputService
    const inputService = this.appContext.services.input;
    if (!inputService) {
      logger.warn('InputService not available');
      return;
    }

    // Retrieve handlers
    this.clickHandler = inputService.getHandler('click');
    this.shortcutHandler = inputService.getHandler('shortcut');
    this.moveForwardHandler = inputService.getHandler('moveForward');
    this.moveBackwardHandler = inputService.getHandler('moveBackward');
    this.moveLeftHandler = inputService.getHandler('moveLeft');
    this.moveRightHandler = inputService.getHandler('moveRight');
    this.rotateHandler = inputService.getHandler('rotate');
    this.jumpHandler = inputService.getHandler('jump');

    // Build handlers array for update loop
    this.handlers = [
      this.clickHandler,
      this.shortcutHandler,
      this.moveForwardHandler,
      this.moveBackwardHandler,
      this.moveLeftHandler,
      this.moveRightHandler,
      this.rotateHandler,
      this.jumpHandler,
    ].filter((h): h is InputHandler => h !== undefined);

    // Listen for gamepad connection
    window.addEventListener('gamepadconnected', this.onGamePadConnected);
    window.addEventListener('gamepaddisconnected', this.onGamePadDisconnected);

    logger.debug('GamePadController initialized');
  }

  private onGamePadConnected = (event: GamepadEvent): void => {
    this.gamepadIndex = event.gamepad.index;
    logger.debug('Gamepad connected', { index: this.gamepadIndex });

    // Start update loop
    this.updateInterval = window.setInterval(() => this.updateGamePad(), 16); // ~60 FPS
  };

  private onGamePadDisconnected = (event: GamepadEvent): void => {
    if (event.gamepad.index === this.gamepadIndex) {
      this.gamepadIndex = -1;
      logger.debug('Gamepad disconnected');

      // Stop update loop
      if (this.updateInterval) {
        clearInterval(this.updateInterval);
        this.updateInterval = undefined;
      }
    }
  };

  private updateGamePad(): void {
    const gamepads = navigator.getGamepads();
    const pad = gamepads[this.gamepadIndex];

    if (!pad) return;

    // Right Trigger (RT) → Click (Button 0)
    if (pad.buttons[7]?.pressed && this.clickHandler && !this.clickHandler.isActive()) {
      this.clickHandler.activate(0);
    } else if (!pad.buttons[7]?.pressed && this.clickHandler?.isActive()) {
      this.clickHandler.deactivate();
    }

    // A Button → Jump
    if (pad.buttons[0]?.pressed && this.jumpHandler && !this.jumpHandler.isActive()) {
      this.jumpHandler.activate();
    } else if (!pad.buttons[0]?.pressed && this.jumpHandler?.isActive()) {
      this.jumpHandler.deactivate();
    }

    // D-Pad → Shortcuts (Buttons 12-15)
    if (pad.buttons[12]?.pressed && this.shortcutHandler && !this.shortcutHandler.isActive()) {
      this.shortcutHandler.activate(1); // D-Pad Up = Shortcut 1
    } else if (pad.buttons[13]?.pressed && this.shortcutHandler && !this.shortcutHandler.isActive()) {
      this.shortcutHandler.activate(2); // D-Pad Down = Shortcut 2
    } else if (pad.buttons[14]?.pressed && this.shortcutHandler && !this.shortcutHandler.isActive()) {
      this.shortcutHandler.activate(3); // D-Pad Left = Shortcut 3
    } else if (pad.buttons[15]?.pressed && this.shortcutHandler && !this.shortcutHandler.isActive()) {
      this.shortcutHandler.activate(4); // D-Pad Right = Shortcut 4
    } else if (
      !pad.buttons[12]?.pressed &&
      !pad.buttons[13]?.pressed &&
      !pad.buttons[14]?.pressed &&
      !pad.buttons[15]?.pressed &&
      this.shortcutHandler?.isActive()
    ) {
      this.shortcutHandler.deactivate();
    }

    // Left Stick → Movement (Axes 0=X, 1=Y)
    const leftStickX = pad.axes[0] || 0;
    const leftStickY = pad.axes[1] || 0;
    const deadzone = 0.15;

    // Forward/Backward
    if (Math.abs(leftStickY) > deadzone) {
      if (leftStickY < 0) {
        // Forward (stick up = negative Y)
        if (this.moveForwardHandler && !this.moveForwardHandler.isActive()) {
          this.moveForwardHandler.activate(Math.abs(leftStickY));
        }
      } else {
        // Backward (stick down = positive Y)
        if (this.moveBackwardHandler && !this.moveBackwardHandler.isActive()) {
          this.moveBackwardHandler.activate(Math.abs(leftStickY));
        }
      }
    } else {
      // Release movement handlers when stick returns to center
      if (this.moveForwardHandler?.isActive()) {
        this.moveForwardHandler.deactivate();
      }
      if (this.moveBackwardHandler?.isActive()) {
        this.moveBackwardHandler.deactivate();
      }
    }

    // Left/Right
    if (Math.abs(leftStickX) > deadzone) {
      if (leftStickX < 0) {
        // Left (stick left = negative X)
        if (this.moveLeftHandler && !this.moveLeftHandler.isActive()) {
          this.moveLeftHandler.activate(Math.abs(leftStickX));
        }
      } else {
        // Right (stick right = positive X)
        if (this.moveRightHandler && !this.moveRightHandler.isActive()) {
          this.moveRightHandler.activate(Math.abs(leftStickX));
        }
      }
    } else {
      // Release movement handlers when stick returns to center
      if (this.moveLeftHandler?.isActive()) {
        this.moveLeftHandler.deactivate();
      }
      if (this.moveRightHandler?.isActive()) {
        this.moveRightHandler.deactivate();
      }
    }

    // Right Stick → Camera Rotation (Axes 2=X, 3=Y)
    const rightStickX = pad.axes[2] || 0;
    const rightStickY = pad.axes[3] || 0;

    if (this.rotateHandler && (Math.abs(rightStickX) > deadzone || Math.abs(rightStickY) > deadzone)) {
      // Convert stick input to rotation delta
      const sensitivity = 5.0;
      const deltaX = rightStickX * sensitivity;
      const deltaY = rightStickY * sensitivity;

      // Activate rotation handler with delta values
      // Note: RotateHandler might need a setDelta() method for this
      if (!this.rotateHandler.isActive()) {
        this.rotateHandler.activate();
      }
      // Call setDelta if RotateHandler supports it
      // (this.rotateHandler as RotateHandler).setDelta?.(deltaX, deltaY);
    }
  }

  getHandlers(): InputHandler[] {
    return this.handlers;
  }

  dispose(): void {
    // Remove event listeners
    window.removeEventListener('gamepadconnected', this.onGamePadConnected);
    window.removeEventListener('gamepaddisconnected', this.onGamePadDisconnected);

    // Stop update loop
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = undefined;
    }

    // Deactivate all handlers
    for (const handler of this.handlers) {
      if (handler.isActive()) {
        handler.deactivate();
      }
    }

    logger.debug('GamePadController disposed');
  }
}
```

### Controller aktivieren

```typescript
// In NimbusClient oder Bootstrap-Code
const inputService = appContext.services.input;

// WebInputController für Keyboard/Mouse
const webController = new WebInputController(canvas, playerService, appContext);
inputService.setController(webController);

// GamePadController kann parallel verwendet werden
// (Implementierung mit Multi-Controller-Support erforderlich)
const gamepadController = new GamePadController(playerService, appContext);
// gamepadController würde dann automatisch bei Gamepad-Verbindung aktiviert
```

## Best Practices

### 1. Keine Logik im Controller
❌ **Falsch:**
```typescript
private onMouseDown = (event: MouseEvent) => {
  const selectedEntity = this.appContext.services.select.getCurrentSelectedEntity();
  if (selectedEntity) {
    this.appContext.services.network.sendEntityInteraction(...);
  }
};
```

✅ **Richtig:**
```typescript
private onMouseDown = (event: MouseEvent) => {
  this.clickHandler?.activate(event.button);
};
```

### 2. Handler sind zustandslos zwischen Aktivierungen
Handler sollten ihren Zustand zwischen `deactivate()` und `activate()` nicht beibehalten (außer Konfiguration).

### 3. Handler-Keys sind eindeutig
Zentrale Handler verwenden eindeutige Keys:
- `'click'` - Mausklicks/Trigger
- `'shortcut'` - Tastatur-Shortcuts/GamePad-Buttons
- `'use'` - Interact/Use-Action
- usw.

### 4. Performance
- Handler-Lookups nur bei `initialize()`, nicht bei jedem Event
- Handler-Referenzen cachen
- Keine schweren Operations in `onUpdate()` (wird jedes Frame aufgerufen)

## Zusammenfassung

**Input-Flow:**
```
User Input
  ↓
InputController (Binding)
  ↓
InputHandler.activate()
  ↓
InputHandler.onActivate() [Business-Logik]
  ↓
Services (PlayerService, NetworkService, etc.)
  ↓
Server / Game State
```

**Vorteile dieser Architektur:**
- ✅ **Wiederverwendbarkeit:** Handler funktionieren mit allen Controllern
- ✅ **Erweiterbarkeit:** Neue Controller einfach hinzufügen (GamePad, Touch)
- ✅ **Testbarkeit:** Handler können isoliert getestet werden
- ✅ **Wartbarkeit:** Klare Trennung von Input-Empfang und Logik
- ✅ **Flexibilität:** Handler unterstützen verschiedene Input-Typen
