# Entity and Player - Unified System

Player is an Entity with `type='player'`. All entity types share the same `EntityData` structure.

## Philosophy

Instead of separate `Player` and `Entity` types, we use a **unified entity system**:

- **Player** = Entity with `type: EntityType.PLAYER` + player-specific fields
- **NPC** = Entity with `type: EntityType.NPC` + NPC-specific fields
- **Mob** = Entity with `type: EntityType.MOB` + mob-specific fields
- **Item** = Entity with `type: EntityType.ITEM` + item-specific fields

All type-specific fields are **optional** - only used when relevant.

## Entity Types

```typescript
enum EntityType {
  PLAYER = 'player',      // Human player
  NPC = 'npc',            // Non-player character
  MOB = 'mob',            // Monster/creature
  ITEM = 'item',          // Dropped item
  PROJECTILE = 'projectile', // Arrow, fireball, etc.
  VEHICLE = 'vehicle',    // Minecart, boat, etc.
}
```

## Common Fields (All Entities)

```typescript
interface EntityData {
  id: string;              // Unique ID
  type: EntityType;        // Entity type
  position: Vector3;       // World position (x, y, z)
  rotation: Rotation;      // Yaw, pitch, roll
  visibility?: EntityVisibility;
  walkToPosition?: Vector3;
  velocity?: Vector3;
  metadata?: Record<string, any>;
  lastUpdate?: number;
}
```

## Rotation Type

```typescript
interface Rotation {
  y: number;  // Yaw (horizontal)
  p: number;  // Pitch (vertical)
  r?: number; // Roll (optional)
}
```

Now used consistently in:
- EntityData
- UserMessage (movement updates)
- PlayerTeleportMessage

## Player-Specific Fields

Only present when `type === EntityType.PLAYER`:

```typescript
// Player identity
username?: string;
displayName?: string;
userId?: string;
role?: string;

// Player state
health?: EntityHealth;
inventory?: any[];
heldItem?: number;
state?: string;          // 'idle', 'walking', 'running', etc.
isCrouching?: boolean;
isSprinting?: boolean;
team?: string;
```

## NPC-Specific Fields

Only present when `type === EntityType.NPC`:

```typescript
displayName?: string;
dialogId?: string;       // Dialog tree ID
aiState?: string;        // AI behavior state
targetId?: string;       // Current target
team?: string;
```

## Mob-Specific Fields

Only present when `type === EntityType.MOB`:

```typescript
health?: EntityHealth;
aggression?: number;     // Aggression level
aiState?: string;        // AI behavior
targetId?: string;       // Attack target
```

## Item-Specific Fields

Only present when `type === EntityType.ITEM`:

```typescript
itemTypeId?: number;     // What item is it
stackCount?: number;     // Stack size
canPickup?: boolean;     // Can be picked up
```

## Usage Examples

### 1. Create Player

```typescript
import { createPlayer, EntityType } from '@nimbus/shared';

// Using helper function
const player = createPlayer(
  'entity_player_123',
  'user_123',
  'alice',
  'Alice',
  { x: 0, y: 65, z: 0 }
);

// Or manually
const player: EntityData = {
  id: 'entity_player_123',
  type: EntityType.PLAYER,
  userId: 'user_123',
  username: 'alice',
  displayName: 'Alice',
  position: { x: 0, y: 65, z: 0 },
  rotation: { y: 0, p: 0 },
  health: { current: 20, max: 20, alive: true },
  state: 'idle',
  inventory: [],
  visibility: { visible: true }
};
```

### 2. Create NPC

```typescript
import { createNPC, EntityType } from '@nimbus/shared';

const npc = createNPC(
  'entity_npc_merchant',
  'Village Merchant',
  { x: 10, y: 64, z: 10 },
  '/models/merchant.babylon',
  'dialog_merchant_greeting'
);

// NPC has:
// - displayName, position, rotation, visibility
// - dialogId (NPC-specific)
// - NO username, userId, inventory (player-specific)
```

### 3. Create Mob

```typescript
const zombie: EntityData = {
  id: 'entity_mob_zombie_001',
  type: EntityType.MOB,
  displayName: 'Zombie',
  position: { x: 20, y: 64, z: 15 },
  rotation: { y: 180, p: 0 },
  health: { current: 10, max: 10, alive: true },
  aggression: 0.8,
  aiState: 'wandering',
  visibility: {
    modelPath: '/models/zombie.babylon',
    visible: true,
    animationState: 'walk'
  }
};
```

### 4. Create Dropped Item

```typescript
import { createItem } from '@nimbus/shared';

const droppedItem = createItem(
  'entity_item_001',
  42,  // Item type ID (e.g., diamond)
  { x: 5, y: 64, z: 5 },
  16   // Stack count
);
```

### 5. Type Guards

```typescript
import { isPlayer, isNPC, isMob, isItem } from '@nimbus/shared';

function handleEntity(entity: EntityData) {
  if (isPlayer(entity)) {
    // TypeScript knows: entity has username, inventory, etc.
    console.log(`Player: ${entity.username}`);
    updatePlayerUI(entity);
  } else if (isNPC(entity)) {
    // TypeScript knows: entity has dialogId
    if (entity.dialogId) {
      showDialogIndicator(entity);
    }
  } else if (isMob(entity)) {
    // TypeScript knows: entity has aggression
    if (entity.aggression > 0.5) {
      showHostileIndicator(entity);
    }
  } else if (isItem(entity)) {
    // TypeScript knows: entity has itemTypeId
    renderDroppedItem(entity);
  }
}
```

### 6. Player Update

```typescript
function updatePlayer(player: EntityData) {
  if (!isPlayer(player)) return;

  // Update health
  if (player.health) {
    updateHealthBar(player.health.current, player.health.max);
  }

  // Update inventory
  if (player.inventory) {
    updateInventoryUI(player.inventory);
  }

  // Update state animation
  if (player.state) {
    setPlayerAnimation(player.state); // 'idle', 'walking', etc.
  }

  // Update crouch/sprint
  if (player.isCrouching) {
    applyCrouchPose(player);
  }
  if (player.isSprinting) {
    applySprintAnimation(player);
  }
}
```

### 7. Entity Movement

```typescript
function updateEntityMovement(entity: EntityData, deltaTime: number) {
  // Common for all entities
  if (entity.velocity) {
    entity.position.x += entity.velocity.x * deltaTime;
    entity.position.y += entity.velocity.y * deltaTime;
    entity.position.z += entity.velocity.z * deltaTime;
  }

  // AI movement towards target
  if (entity.walkToPosition) {
    const direction = Vector3Utils.subtract(entity.walkToPosition, entity.position);
    const distance = Vector3Utils.length(direction);

    if (distance > 0.1) {
      const normalized = Vector3Utils.normalize(direction);
      const speed = isPlayer(entity) ? 4.3 : 3.0; // Different speeds

      entity.velocity = Vector3Utils.multiply(normalized, speed);
    } else {
      entity.walkToPosition = undefined;
      entity.velocity = undefined;
    }
  }
}
```

### 8. Network Optimization

```typescript
// Only send relevant fields for each entity type
function serializeEntity(entity: EntityData): any {
  const base = {
    id: entity.id,
    type: entity.type,
    position: entity.position,
    rotation: entity.rotation,
  };

  if (isPlayer(entity)) {
    return {
      ...base,
      username: entity.username,
      displayName: entity.displayName,
      health: entity.health,
      state: entity.state,
      // Don't send full inventory over network constantly
    };
  } else if (isNPC(entity)) {
    return {
      ...base,
      displayName: entity.displayName,
      dialogId: entity.dialogId,
      visibility: entity.visibility,
    };
  }
  // ... other types

  return base;
}
```

### 9. Render Different Entity Types

```typescript
function renderEntity(entity: EntityData, scene: BABYLON.Scene) {
  if (isPlayer(entity)) {
    renderPlayer(entity, scene);
  } else if (isNPC(entity)) {
    renderNPC(entity, scene);
  } else if (isMob(entity)) {
    renderMob(entity, scene);
  } else if (isItem(entity)) {
    renderDroppedItem(entity, scene);
  }
}

function renderPlayer(player: EntityData, scene: BABYLON.Scene) {
  // Load player model
  const mesh = BABYLON.MeshBuilder.CreateBox(
    `player_${player.id}`,
    { size: 1 },
    scene
  );

  mesh.position.set(player.position.x, player.position.y, player.position.z);
  mesh.rotation.y = player.rotation.y * Math.PI / 180;

  // Apply player skin if available
  if (player.visibility?.skinPath) {
    applySkin(mesh, player.visibility.skinPath);
  }

  // Show name tag
  if (player.displayName) {
    createNameTag(mesh, player.displayName);
  }

  // Apply animation
  if (player.state) {
    playAnimation(mesh, player.state);
  }
}
```

### 10. Player List UI

```typescript
function getOnlinePlayers(entities: Map<string, EntityData>): EntityData[] {
  return Array.from(entities.values()).filter(isPlayer);
}

function renderPlayerList() {
  const players = getOnlinePlayers(entityCache);

  players.forEach(player => {
    const listItem = document.createElement('div');
    listItem.innerHTML = `
      <span class="player-name">${player.displayName}</span>
      <span class="player-health">${player.health?.current ?? 0} HP</span>
      <span class="player-state">${player.state ?? 'idle'}</span>
    `;
    playerListElement.appendChild(listItem);
  });
}
```

## Benefits of Unified System

### ✅ Advantages

1. **Single Entity Update Message** - No separate player/npc/mob updates
2. **Consistent Handling** - Same position, rotation, velocity for all
3. **Network Efficiency** - One message type for all entities
4. **Type Safety** - Type guards ensure correct field access
5. **Extensible** - Easy to add new entity types
6. **Code Reuse** - Common movement, rendering, etc.

### Example: Entity Update Message

```typescript
// Single message type for all entities
{
  t: 'e.u',
  d: [
    { id: '1', type: 'player', username: 'alice', ... },
    { id: '2', type: 'npc', dialogId: 'merchant', ... },
    { id: '3', type: 'mob', aggression: 0.8, ... },
    { id: '4', type: 'item', itemTypeId: 42, ... }
  ]
}

// Client processes all entity types with same handler
onEntityUpdate((entities: EntityData[]) => {
  entities.forEach(entity => {
    updateEntity(entity); // Works for all types
  });
});
```

## Type Aliases (Optional)

For convenience, you can create type aliases:

```typescript
// Helper type for type-safe player access
export type PlayerEntity = EntityData & {
  type: EntityType.PLAYER;
  username: string;
  userId: string;
  displayName: string;
};

// Helper type for NPC
export type NPCEntity = EntityData & {
  type: EntityType.NPC;
  displayName: string;
};

// Usage with type assertion (after type guard)
function handlePlayer(entity: EntityData) {
  if (isPlayer(entity)) {
    const player = entity as PlayerEntity;
    // player.username is guaranteed to exist
  }
}
```

## Summary

- **Unified**: Player is Entity with type='player'
- **Optional Fields**: Player-specific fields only present for players
- **Type Guards**: isPlayer(), isNPC(), etc. for safe access
- **Helpers**: createPlayer(), createNPC(), etc. for easy creation
- **Rotation**: Centralized Rotation type (replaces duplicate)
- **Efficient**: Single entity update message for all types
- **Extensible**: Easy to add new entity types
