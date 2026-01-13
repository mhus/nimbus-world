# REST API DTOs

This directory contains Data Transfer Objects (DTOs) for REST API communication between the Nimbus client and server.

## Overview

These DTOs are designed to match the REST API endpoints defined in `client/instructions/client_2.0/server_rest_api.md`. They provide type-safe interfaces for HTTP requests and responses.

## DTO Files

### WorldDTO.ts

DTOs for world-related REST API endpoints.

**Endpoints:**
- `GET /api/worlds` - List available worlds
- `GET /api/worlds/{worldId}` - Get world details

**Types:**
- `WorldListItemDTO` - Minimal world info for world selection
- `WorldListResponseDTO` - Array of world list items
- `WorldDetailDTO` - Complete world metadata with boundaries and settings
- `UserDTO` - User/owner information
- `Position3D` - 3D position (x, y, z)
- `WorldSettingsDTO` - World configuration settings

### BlockTypeDTO.ts

DTOs for block type related REST API endpoints.

**Endpoints:**
- `GET /api/worlds/{worldId}/blocktypes/{id}` - Get single block type
- `GET /api/worlds/{worldId}/blocktypes/{from}/{to}` - Get range of block types

**Types:**
- `BlockTypeDTO` - Block type definition from REST API
- `BlockTypeOptionsDTO` - Block type options (solid, opaque, transparent, material)
- `BlockTypeSingleResponseDTO` - Single block type response
- `BlockTypeRangeResponseDTO` - Array of block types (may have gaps)

**Note:** This is a simplified DTO for REST API responses. The complete BlockType definition with modifiers is in `types/BlockType.ts`.

### BlockMetadataDTO.ts

DTOs for block metadata related REST API endpoints.

**Endpoints:**
- `GET /api/worlds/{worldId}/blocks/{x}/{y}/{z}/metadata` - Get block metadata

**Types:**
- `BlockMetadataDTO` - Block metadata not included in block type definition
- `BlockMetadataResponseDTO` - Block metadata response

**Purpose:** Returns metadata specific to block instances (groups, display name) that are not part of the block type definition.

## Usage Examples

### Fetching World List

```typescript
import type { WorldListResponseDTO } from '@nimbus/shared';

async function fetchWorlds(): Promise<WorldListResponseDTO> {
  const response = await fetch('/api/worlds', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch worlds: ${response.statusText}`);
  }

  return response.json();
}

// Usage
const worlds = await fetchWorlds();
worlds.forEach(world => {
  console.log(`${world.name}: ${world.description}`);
  console.log(`Owner: ${world.owner.displayName}`);
});
```

### Fetching World Details

```typescript
import type { WorldDetailDTO } from '@nimbus/shared';

async function fetchWorldDetails(worldId: string): Promise<WorldDetailDTO> {
  const response = await fetch(`/api/worlds/${worldId}`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch world: ${response.statusText}`);
  }

  return response.json();
}

// Usage
const world = await fetchWorldDetails('world123');
console.log(`World: ${world.name}`);
console.log(`Bounds: ${JSON.stringify(world.start)} to ${JSON.stringify(world.stop)}`);
console.log(`Chunk size: ${world.chunkSize}`);
console.log(`Max players: ${world.settings.maxPlayers}`);
```

### Fetching Block Types

```typescript
import type { BlockTypeDTO, BlockTypeRangeResponseDTO } from '@nimbus/shared';

async function fetchBlockType(worldId: string, typeId: number | string): Promise<BlockTypeDTO> {
  const response = await fetch(`/api/worlds/${worldId}/blocktypes/${typeId}`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch block type: ${response.statusText}`);
  }

  return response.json();
}

async function fetchBlockTypeRange(
  worldId: string,
  from: number,
  to: number
): Promise<BlockTypeRangeResponseDTO> {
  const response = await fetch(`/api/worlds/${worldId}/blocktypes/${from}/${to}`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch block types: ${response.statusText}`);
  }

  return response.json();
}

// Usage - fetch single block type by ID
const stoneBlock = await fetchBlockType('world123', 1);
console.log(`Block: ${stoneBlock.displayName} (${stoneBlock.shape})`);

// Usage - fetch single block type by name
const dirtBlock = await fetchBlockType('world123', 'dirt');

// Usage - fetch range of block types
const blockTypes = await fetchBlockTypeRange('world123', 1, 100);
console.log(`Loaded ${blockTypes.length} block types`);

// Note: Array may have gaps if some IDs are not defined
blockTypes.forEach((blockType, index) => {
  if (blockType) {
    console.log(`[${blockType.id}] ${blockType.name}: ${blockType.displayName}`);
  }
});
```

### Fetching Block Metadata

```typescript
import type { BlockMetadataDTO } from '@nimbus/shared';

async function fetchBlockMetadata(
  worldId: string,
  x: number,
  y: number,
  z: number
): Promise<BlockMetadataDTO> {
  const response = await fetch(`/api/worlds/${worldId}/blocks/${x}/${y}/${z}/metadata`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch block metadata: ${response.statusText}`);
  }

  return response.json();
}

// Usage
const metadata = await fetchBlockMetadata('world123', 10, 64, 20);
console.log(`Block at (${metadata.x}, ${metadata.y}, ${metadata.z})`);
console.log(`ID: ${metadata.id}`);
console.log(`Groups: ${metadata.groupNames.join(', ')}`);
if (metadata.displayName) {
  console.log(`Custom name: ${metadata.displayName}`);
}
```

### Fetching Assets

```typescript
async function fetchAsset(worldId: string, assetPath: string): Promise<Blob> {
  const response = await fetch(`/api/worlds/${worldId}/assets/${assetPath}`, {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch asset: ${response.statusText}`);
  }

  return response.blob();
}

// Usage - fetch texture
const stoneTexture = await fetchAsset('world123', 'textures/block/stone.png');
const imageUrl = URL.createObjectURL(stoneTexture);

// Usage - fetch model
const treeModel = await fetchAsset('world123', 'models/tree.glb');
```

## Authentication

All REST API endpoints require authentication. The client must send an Authorization header with either:

- **Basic Auth**: `Authorization: Basic <base64(username:password)>`
- **Bearer Token**: `Authorization: Bearer <token>`

```typescript
// Basic Auth
const credentials = btoa(`${username}:${password}`);
const headers = {
  'Authorization': `Basic ${credentials}`
};

// Bearer Token
const headers = {
  'Authorization': `Bearer ${token}`
};
```

## Error Handling

All REST API calls should include proper error handling:

```typescript
async function safeApiCall<T>(url: string, options?: RequestInit): Promise<T> {
  try {
    const response = await fetch(url, options);

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`HTTP ${response.status}: ${errorText}`);
    }

    return response.json();
  } catch (error) {
    if (error instanceof TypeError) {
      throw new Error('Network error: Unable to reach server');
    }
    throw error;
  }
}
```

## Type Safety

These DTOs provide full TypeScript type safety:

```typescript
import type { WorldDetailDTO } from '@nimbus/shared';

function processWorld(world: WorldDetailDTO) {
  // TypeScript knows all properties and their types
  const chunkSize: number = world.chunkSize;
  const owner: UserDTO = world.owner;
  const maxPlayers: number = world.settings.maxPlayers;

  // Compile-time error if property doesn't exist
  // const invalid = world.nonExistent; // ❌ Error
}
```

## Differences Between DTOs and Internal Types

### BlockType

- **BlockTypeDTO** (REST API): Simplified view with basic properties (texture, shape, hardness, etc.)
- **BlockType** (Internal): Complete definition with modifiers map for different block states

The client will typically:
1. Fetch `BlockTypeDTO` from REST API
2. Convert to internal `BlockType` format with proper modifiers
3. Store in block type registry

### BlockMetadata

- **BlockMetadataDTO** (REST API): Block instance metadata from server (groups, display name)
- **BlockMetadata** (Internal): May include additional client-side metadata and modifiers

### World

- **WorldListItemDTO**: Minimal info for world selection (name, description, owner)
- **WorldDetailDTO**: Complete metadata with boundaries, settings, configuration
- **WorldInfo** (Internal): May include additional runtime state and client-specific data

## Best Practices

1. **Use Type Guards**: Validate API responses match expected types
2. **Handle Missing Data**: Not all optional fields will be present
3. **Cache Responses**: Cache world lists and block types to reduce API calls
4. **Batch Requests**: Use range endpoints when fetching multiple block types
5. **Error Recovery**: Implement retry logic for transient network errors
6. **Authentication**: Always include authentication headers
7. **CORS**: Ensure server is configured to allow cross-origin requests if needed

## Related Documentation

- **REST API Specification**: `client/instructions/client_2.0/server_rest_api.md`
- **Internal Types**: `packages/shared/src/types/`
- **Network Messages**: `packages/shared/src/network/messages/`
- **Object Model**: `client/instructions/client_2.0/object-model-2.0.md`

## Future Enhancements

Possible future additions:

1. **Pagination**: Support for paginated world lists
2. **Filtering**: Query parameters for filtering worlds/block types
3. **Caching Headers**: ETags and cache control for efficient caching
4. **Partial Updates**: PATCH endpoints for updating world settings
5. **Bulk Operations**: Batch endpoints for multiple operations
6. **WebSocket Fallback**: Real-time updates via WebSocket for asset changes
