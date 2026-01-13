# ModifierService

A priority-based value management system that allows multiple sources to override a base value. Higher priority modifiers win, and when priorities are equal, the most recently created modifier takes precedence.

## Overview

The ModifierService consists of three main components:

1. **Modifier**: A single value with a priority and creation timestamp
2. **ModifierStack**: A collection of modifiers for a specific value, sorted by priority
3. **ModifierService**: Central service that manages multiple stacks

## Core Concepts

### Priority System

- **Lower priority values = Higher priority**
- Priority 100 beats priority 1000
- When priorities are equal, the newest modifier wins
- Default modifier has `MAX_PRIORITY` and is always present as fallback

### Modifiers

Each modifier has:
- `value`: The actual value (can be any type: number, boolean, string, object, etc.)
- `prio`: Priority level (lower = higher priority)
- `created`: Creation timestamp (for tie-breaking)
- Reference to its owning stack

### ModifierStack

Each stack manages:
- A list of modifiers sorted by priority
- A default modifier (fallback)
- An action callback that executes when the value changes
- Automatic value calculation and update triggering

## Usage

### 1. Create a ModifierStack

```typescript
import { ModifierService } from './services/ModifierService';
import type { AppContext } from './AppContext';

const modifierService = appContext.services.modifier!;

// Create stack with default value and action
modifierService.createModifierStack<number>(
  'windForce',
  0, // Default value
  (newValue: number) => {
    // This action is called whenever the value changes
    environmentService.setWindStrength(newValue);
  }
);
```

### 2. Add Modifiers

```typescript
// Weather system adds wind (lower priority)
const windModifierByWeather = modifierService.addModifier<number>(
  'windForce',
  {
    value: 0.3,
    prio: 1000, // Lower priority
  }
);
// Action is called → windForce = 0.3

// Storm spell adds stronger wind (higher priority)
const windModifierByEffect = modifierService.addModifier<number>(
  'windForce',
  {
    value: 0.8,
    prio: 100, // Higher priority (lower value)
  }
);
// Action is called → windForce = 0.8 (overrides weather)
```

### 3. Update Modifier Values

```typescript
// Change weather wind strength
windModifierByWeather.setValue(0.5);
// Action is called → windForce still 0.8 (effect has higher priority)

// Change effect wind strength
windModifierByEffect.setValue(1.0);
// Action is called → windForce = 1.0
```

### 4. Remove Modifiers

```typescript
// Remove the effect modifier
windModifierByEffect.close();
// Action is called → windForce = 0.5 (falls back to weather)

// Remove weather modifier
windModifierByWeather.close();
// Action is called → windForce = 0 (falls back to default)
```

## Complete Example

```typescript
// Setup
const modifierService = appContext.services.modifier!;

modifierService.createModifierStack<number>(
  'windForce',
  0,
  (newValue: number) => {
    environmentService.setWindStrength(newValue);
  }
);

// Scenario: Normal weather
const weatherMod = modifierService.addModifier('windForce', {
  value: 0.3,
  prio: 1000,
});
// windForce = 0.3

// Scenario: Storm spell cast
const stormMod = modifierService.addModifier('windForce', {
  value: 0.9,
  prio: 100,
});
// windForce = 0.9 (spell overrides weather)

// Scenario: Weather changes
weatherMod.setValue(0.6);
// windForce = 0.9 (spell still active)

// Scenario: Spell expires
stormMod.close();
// windForce = 0.6 (back to weather)

// Scenario: Weather calms
weatherMod.close();
// windForce = 0 (default)
```

## Type Safety

ModifierService is fully generic and type-safe:

```typescript
// Number modifiers
modifierService.createModifierStack<number>('speed', 1.0, (v) => { ... });
modifierService.addModifier('speed', { value: 1.5, prio: 100 });

// Boolean modifiers
modifierService.createModifierStack<boolean>('canFly', false, (v) => { ... });
modifierService.addModifier('canFly', { value: true, prio: 100 });

// String modifiers
modifierService.createModifierStack<string>('message', 'default', (v) => { ... });
modifierService.addModifier('message', { value: 'custom', prio: 100 });

// Complex object modifiers
interface Settings { fov: number; distance: number; }
modifierService.createModifierStack<Settings>(
  'camera',
  { fov: 75, distance: 10 },
  (v) => { ... }
);
modifierService.addModifier('camera', {
  value: { fov: 30, distance: 50 },
  prio: 100
});
```

## API Reference

### ModifierService

#### createModifierStack<T>(stackName, defaultValue, action)
Creates a new modifier stack.

**Parameters:**
- `stackName: string` - Unique name for the stack
- `defaultValue: T` - Default value (fallback)
- `action: (value: T) => void` - Callback executed when value changes

**Returns:** `ModifierStack<T>`

**Throws:** Error if stack with same name already exists

---

#### addModifier<T>(stackName, config)
Adds a modifier to an existing stack.

**Parameters:**
- `stackName: string` - Name of the stack
- `config: { value: T; prio: number }` - Modifier configuration

**Returns:** `Modifier<T>`

**Throws:** Error if stack doesn't exist

---

#### getModifierStack<T>(stackName)
Gets a modifier stack by name.

**Parameters:**
- `stackName: string` - Name of the stack

**Returns:** `ModifierStack<T> | undefined`

---

#### hasStack(stackName)
Checks if a stack exists.

**Parameters:**
- `stackName: string` - Name of the stack

**Returns:** `boolean`

---

#### removeStack(stackName)
Removes a stack and disposes all its modifiers.

**Parameters:**
- `stackName: string` - Name of the stack

---

#### dispose()
Disposes all stacks and their modifiers.

---

### ModifierStack<T>

#### Properties
- `stackName: string` - Name of the stack
- `currentValue: T` - Current active value
- `modifiers: readonly Modifier<T>[]` - All modifiers (excluding default)

#### Methods

##### addModifier(value, prio)
Adds a modifier to this stack.

**Parameters:**
- `value: T` - The value
- `prio: number` - Priority (lower = higher priority)

**Returns:** `Modifier<T>`

---

##### removeModifier(modifier)
Removes a modifier from this stack.

**Parameters:**
- `modifier: Modifier<T>` - The modifier to remove

---

##### update(force)
Recalculates and applies the current value.

**Parameters:**
- `force: boolean` - Force action execution even if value hasn't changed

---

##### getDefaultModifier()
Gets the default modifier (fallback).

**Returns:** `Modifier<T>`

---

### Modifier<T>

#### Properties
- `value: T` - Current value
- `prio: number` - Priority level
- `created: number` - Creation timestamp

#### Methods

##### setValue(value)
Updates the modifier's value.

**Parameters:**
- `value: T` - New value

---

##### close()
Removes this modifier from its stack.

---

## Use Cases

### 1. Environmental Effects
Manage environment parameters affected by multiple sources:
- Wind strength (weather, spells, effects)
- Light intensity (time of day, torches, darkness spell)
- Fog density (weather, biomes, special effects)

### 2. Player Stats
Handle player attributes modified by various factors:
- Movement speed (terrain, buffs, debuffs)
- Health regeneration (food, potions, status effects)
- Damage multiplier (weapons, enchantments, critical hits)

### 3. Gameplay Flags
Control boolean flags with priority:
- Can fly (creative mode, items, permissions)
- Invulnerable (admin mode, temporary shields)
- Can build (game mode, permissions, zones)

### 4. Camera Settings
Manage camera behavior based on context:
- Field of view (binoculars, scopes, cutscenes)
- Distance limits (vehicles, zoom modes)
- Movement speed (cinematic mode, debug mode)

### 5. Rendering Settings
Control render parameters dynamically:
- Render distance (performance mode, world boundaries)
- Quality settings (performance optimization, cutscenes)
- Post-processing effects (underwater, damage flash)

## Best Practices

### 1. Choose Meaningful Priorities
Use a consistent priority scheme across your application:
```typescript
// Example priority levels
const PRIORITY = {
  SYSTEM: 1,        // Highest - system overrides
  ADMIN: 100,       // Admin/debug features
  EFFECT: 500,      // Temporary effects (spells, powerups)
  ENVIRONMENT: 1000, // Environmental factors
  DEFAULT: MAX_PRIORITY // Default fallback
};
```

### 2. Keep References to Modifiers
Always store modifier references if you need to update or remove them:
```typescript
class WeatherSystem {
  private windModifier?: Modifier<number>;

  setWindStrength(strength: number) {
    if (this.windModifier) {
      this.windModifier.setValue(strength);
    } else {
      this.windModifier = modifierService.addModifier('windForce', {
        value: strength,
        prio: PRIORITY.ENVIRONMENT
      });
    }
  }

  dispose() {
    this.windModifier?.close();
  }
}
```

### 3. Clean Up Modifiers
Always close modifiers when they're no longer needed:
```typescript
class TempEffect {
  private modifier: Modifier<number>;

  constructor() {
    this.modifier = modifierService.addModifier('speed', {
      value: 1.5,
      prio: PRIORITY.EFFECT
    });

    // Auto-cleanup after 10 seconds
    setTimeout(() => this.dispose(), 10000);
  }

  dispose() {
    this.modifier.close();
  }
}
```

### 4. Use Type-Safe Stacks
Define interfaces for complex modifier values:
```typescript
interface WindConfig {
  strength: number;
  direction: { x: number; z: number };
  gustiness: number;
}

modifierService.createModifierStack<WindConfig>(
  'wind',
  { strength: 0, direction: { x: 1, z: 0 }, gustiness: 0 },
  (config) => {
    environmentService.setWindConfig(config);
  }
);
```

### 5. Handle Edge Cases
Consider what happens when modifiers conflict:
```typescript
// Multiple sources may set the same value
// The priority system automatically handles conflicts
const mod1 = modifierService.addModifier('canFly', {
  value: true,
  prio: 100
});
const mod2 = modifierService.addModifier('canFly', {
  value: false, // Conflict!
  prio: 50 // Higher priority - this wins
});
// Result: canFly = false
```

## Debugging

### Inspect Stack State
```typescript
const stack = modifierService.getModifierStack('windForce');
if (stack) {
  console.log('Current value:', stack.currentValue);
  console.log('Active modifiers:', stack.modifiers.length);
  console.log('Default:', stack.getDefaultModifier().value);

  // List all modifiers
  for (const mod of stack.modifiers) {
    console.log(`Priority ${mod.prio}: ${mod.value}`);
  }
}
```

### List All Stacks
```typescript
console.log('Active stacks:', modifierService.stackNames);
```

### Force Update
```typescript
// Force action execution even if value hasn't changed
const stack = modifierService.getModifierStack('windForce');
stack?.update(true);
```

## Integration

### AppContext Setup
The ModifierService is automatically registered in AppContext during initialization:

```typescript
// In NimbusClient.ts
const modifierService = new ModifierService();
appContext.services.modifier = modifierService;
```

### Usage in Services
```typescript
export class EnvironmentService {
  constructor(scene: Scene, appContext: AppContext) {
    const modifierService = appContext.services.modifier!;

    // Setup wind force modifier stack
    modifierService.createModifierStack<number>(
      'windForce',
      0,
      (value) => this.setWindStrength(value)
    );
  }
}
```

## Testing

See `ModifierService.test.ts` for comprehensive test examples.

Key test scenarios:
- Basic stack creation and modifier addition
- Priority ordering
- Value updates
- Modifier removal and cleanup
- Type safety with different types
- Edge cases (same priority, empty stack)

## Performance Considerations

- Modifier sorting is done on every add/remove/update
- For stacks with many modifiers, consider batching updates
- Use force update sparingly (only when necessary)
- Clean up unused modifiers to avoid memory leaks
- The value comparison uses `!==`, so complex objects will always trigger updates

## Future Improvements

Potential enhancements:
- Custom comparator for value equality checks
- Batch update mode to reduce action calls
- Modifier groups (enable/disable multiple modifiers at once)
- Conditional modifiers (only active under certain conditions)
- Modifier expiration (auto-cleanup after timeout)
- Modifier stacking modes (multiply, add, override)
